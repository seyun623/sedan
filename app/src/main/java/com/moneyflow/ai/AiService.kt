package com.moneyflow.ai

import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.security.SecretStore
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Sends JSON-answer prompts to whichever AI provider the user selected (Settings → AI 설정). */
@Singleton
class AiService @Inject constructor(
  private val settings: SettingsRepository,
  private val secrets: SecretStore,
  private val gemini: GeminiClient,
  private val compat: OpenAiCompatClient
) {
  suspend fun isReady(): Boolean = settings.current().isAiReady

  /** Name shown in consent texts, e.g. "Google Gemini". */
  suspend fun providerLabel(): String = settings.current().aiProvider.label

  /**
   * @param geminiSchema response schema (Gemini enforces it natively)
   * @param shapeHint the same shape described as JSON for OpenAI-compatible providers
   */
  suspend fun completeJson(prompt: String, geminiSchema: JSONObject, shapeHint: String): String {
    val s = settings.current()
    if (!s.isAiReady) throw AiNotConfiguredException()
    val key = s.aiApiKeyEncrypted?.let(secrets::decrypt) ?: throw AiNotConfiguredException()
    suspend fun call(model: String): String = when (s.aiProvider) {
      AiProvider.GEMINI -> gemini.generateJson(key, model, prompt, geminiSchema)
      else -> compat.completeJson(
        s.aiBaseUrl ?: throw AiNotConfiguredException(), key, model,
        "$prompt\n\n반드시 다음 형식의 JSON 객체 하나로만 답하세요:\n$shapeHint"
      )
    }
    return try {
      call(s.aiModel)
    } catch (e: AiModelNotFoundException) {
      // Providers retire models (and free model lists change): switch to an available one and retry once.
      val available = runCatching { listModels(s.aiProvider, key, s.aiCustomBaseUrl) }.getOrDefault(emptyList())
      val replacement = AiModelPicker.pick(s.aiProvider, available, exclude = s.aiModel) ?: throw AiRequestException(
        "선택한 모델(${s.aiModel})을 찾을 수 없어요. 설정 → AI 설정에서 모델 목록을 다시 불러와 다른 모델을 골라 주세요."
      )
      settings.setAiModel(replacement)
      call(replacement)
    }
  }

  /** Web search with the answer (Gemini's Google Search grounding); null when the provider can't search. */
  suspend fun searchWeb(prompt: String): Pair<String, List<Pair<String, String>>>? {
    val s = settings.current()
    if (!s.isAiReady) throw AiNotConfiguredException()
    if (s.aiProvider != AiProvider.GEMINI) return null
    val key = s.aiApiKeyEncrypted?.let(secrets::decrypt) ?: throw AiNotConfiguredException()
    return gemini.generateGrounded(key, s.aiModel, prompt)
  }

  /** Whether [searchWeb] can be used with the chosen provider. */
  suspend fun canSearchWeb(): Boolean = settings.current().let { it.isAiReady && it.aiProvider == AiProvider.GEMINI }

  /** Lists models for a key (also verifies the key). */
  suspend fun listModels(provider: AiProvider, apiKey: String, customBaseUrl: String): List<String> = when (provider) {
    AiProvider.GEMINI -> gemini.listModels(apiKey)
    AiProvider.CUSTOM -> compat.listModels(customBaseUrl, apiKey)
    else -> compat.listModels(provider.defaultBaseUrl!!, apiKey)
  }
}
