package com.moneyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.ai.AiProvider
import com.moneyflow.ai.AiService
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
  private val ai: AiService,
  private val messages: UserMessageBus
) : ViewModel() {

  val settings: StateFlow<AppSettings> = settingsRepo.settings
    .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

  private val _check = MutableStateFlow(AiKeyCheck())
  val check: StateFlow<AiKeyCheck> = _check.asStateFlow()

  fun selectProvider(provider: AiProvider) = viewModelScope.launch {
    settingsRepo.setAiProvider(provider)
    _check.value = AiKeyCheck()
  }

  /** Verifies a newly entered key (by listing its models) and stores it encrypted if valid. */
  fun saveKey(key: String, customBaseUrl: String) = viewModelScope.launch {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return@launch
    val provider = settingsRepo.current().aiProvider
    if (provider == AiProvider.CUSTOM) {
      if (!customBaseUrl.trim().startsWith("https://")) {
        _check.value = AiKeyCheck(error = "주소는 https:// 로 시작해야 해요."); return@launch
      }
      settingsRepo.setAiCustomBaseUrl(customBaseUrl)
    }
    _check.value = AiKeyCheck(busy = true)
    runCatching { ai.listModels(provider, trimmed, customBaseUrl.trim()) }
      .onSuccess { models ->
        settingsRepo.setAiApiKeyEncrypted(secrets.encrypt(trimmed))
        val current = settingsRepo.current().aiModel
        if (models.isNotEmpty() && current !in models) settingsRepo.setAiModel(pickDefaultModel(provider, models))
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
    val s = settingsRepo.current()
    runCatching { ai.listModels(s.aiProvider, key, s.aiCustomBaseUrl) }
      .onSuccess { _check.value = AiKeyCheck(models = it) }
      .onFailure { _check.value = AiKeyCheck(error = it.message ?: "연결에 실패했어요.") }
  }

  fun removeKey() = viewModelScope.launch {
    settingsRepo.setAiApiKeyEncrypted(null)
    _check.value = AiKeyCheck()
    messages.show("API 키를 삭제하고 AI 추천을 껐어요.")
  }

  // ---- 예비 AI (3.1) ----
  private val _backupCheck = MutableStateFlow(AiKeyCheck())
  val backupCheck: StateFlow<AiKeyCheck> = _backupCheck.asStateFlow()

  /** A backup key pasted: which service it is comes from its shape; checked, then saved and switched on. */
  fun addBackupKey(raw: String) = viewModelScope.launch {
    val key = raw.trim()
    val provider = com.moneyflow.ai.AiModelPicker.providerOfKey(key)
    if (provider == null) { _backupCheck.value = AiKeyCheck(error = "Groq(gsk_…)·OpenRouter(sk-or-…)·Gemini(AIza…) 키가 아니에요. 키 전체를 복사했는지 봐 주세요."); return@launch }
    if (provider == settingsRepo.current().aiProvider) { _backupCheck.value = AiKeyCheck(error = "${provider.label}는 지금 기본으로 쓰는 AI예요. 예비로는 다른 서비스 키를 넣어 주세요."); return@launch }
    _backupCheck.value = AiKeyCheck(busy = true)
    runCatching { ai.listModels(provider, key, "") }
      .onSuccess { models ->
        val model = if (models.isEmpty()) provider.defaultModel else pickDefaultModel(provider, models)
        settingsRepo.setAiKeyFor(provider, secrets.encrypt(key), model)
        settingsRepo.setAiBackupEnabled(true)
        _backupCheck.value = AiKeyCheck()
        messages.show("${provider.label} 키를 확인했어요. 이제 기본 AI 한도가 차면 ${provider.label}(으)로 넘어가요.")
      }
      .onFailure { _backupCheck.value = AiKeyCheck(error = it.message ?: "키를 확인하지 못했어요.") }
  }

  fun removeBackupKey(provider: AiProvider) = viewModelScope.launch {
    settingsRepo.setAiKeyFor(provider, null)
    messages.show("${provider.label} 예비 키를 지웠어요.")
  }

  fun setBackupEnabled(on: Boolean) = viewModelScope.launch { settingsRepo.setAiBackupEnabled(on) }

  fun setEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepo.setAiEnabled(enabled) }
  fun setModel(model: String) = viewModelScope.launch { settingsRepo.setAiModel(model) }

  companion object {
    fun pickDefaultModel(provider: AiProvider, models: List<String>): String = when {
      provider.defaultModel in models -> provider.defaultModel
      provider == AiProvider.GEMINI -> pickDefaultModel(models)
      else -> models.firstOrNull { it.endsWith(":free") } ?: models.first()
    }

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
