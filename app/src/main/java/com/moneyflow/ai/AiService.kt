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
    return when (s.aiProvider) {
      AiProvider.GEMINI -> gemini.generateJson(key, s.aiModel, prompt, geminiSchema)
      else -> compat.completeJson(
        s.aiBaseUrl ?: throw AiNotConfiguredException(), key, s.aiModel,
        "$prompt\n\n반드시 다음 형식의 JSON 객체 하나로만 답하세요:\n$shapeHint"
      )
    }
  }

  /** Lists models for a key (also verifies the key). */
  suspend fun listModels(provider: AiProvider, apiKey: String, customBaseUrl: String): List<String> = when (provider) {
    AiProvider.GEMINI -> gemini.listModels(apiKey)
    AiProvider.CUSTOM -> compat.listModels(customBaseUrl, apiKey)
    else -> compat.listModels(provider.defaultBaseUrl!!, apiKey)
  }
}
