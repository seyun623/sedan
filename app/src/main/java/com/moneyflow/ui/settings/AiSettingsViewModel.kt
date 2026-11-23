package com.moneyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.ai.CategorySuggester
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.security.SecretStore
import com.moneyflow.ui.common.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiKeyCheck(val busy: Boolean = false, val error: String? = null, val models: List<String> = emptyList())

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
  private val settingsRepo: SettingsRepository,
  private val secrets: SecretStore,
  private val suggester: CategorySuggester,
  private val messages: UserMessageBus
) : ViewModel() {

  val settings: StateFlow<AppSettings> = settingsRepo.settings
    .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

  private val _check = MutableStateFlow(AiKeyCheck())
  val check: StateFlow<AiKeyCheck> = _check.asStateFlow()

  /** Verifies a newly entered key (by listing its models) and stores it encrypted if valid. */
  fun saveKey(key: String) = viewModelScope.launch {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return@launch
    _check.value = AiKeyCheck(busy = true)
    runCatching { suggester.availableModels(trimmed) }
      .onSuccess { models ->
        settingsRepo.setAiApiKeyEncrypted(secrets.encrypt(trimmed))
        val current = settingsRepo.current().aiModel
        if (models.isNotEmpty() && current !in models) settingsRepo.setAiModel(pickDefaultModel(models))
        _check.value = AiKeyCheck(models = models)
        messages.show("API 키를 확인하고 저장했어요.")
      }
      .onFailure { _check.value = AiKeyCheck(error = it.message ?: "키를 확인하지 못했어요.") }
  }

  /** Re-checks the stored key and refreshes the model list. */
  fun refreshModels() = viewModelScope.launch {
    val key = settingsRepo.current().aiApiKeyEncrypted?.let(secrets::decrypt)
    if (key == null) { _check.value = AiKeyCheck(error = "저장된 API 키를 읽을 수 없어요. 다시 등록해 주세요."); return@launch }
    _check.value = _check.value.copy(busy = true, error = null)
    runCatching { suggester.availableModels(key) }
      .onSuccess { _check.value = AiKeyCheck(models = it) }
      .onFailure { _check.value = AiKeyCheck(error = it.message ?: "연결에 실패했어요.") }
  }

  fun removeKey() = viewModelScope.launch {
    settingsRepo.setAiApiKeyEncrypted(null)
    _check.value = AiKeyCheck()
    messages.show("API 키를 삭제하고 AI 추천을 껐어요.")
  }

  fun setEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepo.setAiEnabled(enabled) }
  fun setModel(model: String) = viewModelScope.launch { settingsRepo.setAiModel(model) }

  companion object {
    /** Prefer a general "flash" text model; avoid lite / preview / image / audio variants when possible. */
    fun pickDefaultModel(models: List<String>): String {
      val special = Regex("image|audio|tts|live|embedding|vision|thinking|exp|preview", RegexOption.IGNORE_CASE)
      val general = models.filterNot { special.containsMatchIn(it) }
      return general.filter { it.contains("flash") && !it.contains("lite") }.maxOrNull()
        ?: general.maxOrNull()
        ?: models.first()
    }
  }
}
