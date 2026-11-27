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
  suspend fun completeJson(prompt: String, geminiSchema: JSONObject, shapeHint: String, image: Pair<String, String>? = null): String {
    val s = settings.current()
    if (!s.isAiReady) throw AiNotConfiguredException()
    val key = s.aiApiKeyEncrypted?.let(secrets::decrypt) ?: throw AiNotConfiguredException()
    if (image != null && s.aiProvider != AiProvider.GEMINI) throw AiRequestException("사진은 Google Gemini로만 읽을 수 있어요. 설정 → AI 설정에서 Gemini를 골라 주세요.")
    suspend fun call(model: String): String = when (s.aiProvider) {
      AiProvider.GEMINI -> gemini.generateJson(key, model, prompt, geminiSchema, image)
      else -> compat.completeJson(
        s.aiBaseUrl ?: throw AiNotConfiguredException(), key, model,
        "$prompt\n\n반드시 다음 형식의 JSON 객체 하나로만 답하세요:\n$shapeHint"
      )
    }
    return try {
      withQuotaFallback(s.aiProvider, key, s.aiCustomBaseUrl, s.aiModel) { m -> withBusyRetry { call(m) } }
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
    return withQuotaFallback(s.aiProvider, key, s.aiCustomBaseUrl, s.aiModel) { m -> withBusyRetry { gemini.generateGrounded(key, m, prompt) } }
  }

  /**
   * The chosen model is over its free limit (still 429 after the short retries): try other models,
   * which have their own limits. Only for this request; the chosen model stays as it is.
   */
  private suspend fun <T> withQuotaFallback(provider: AiProvider, key: String, customUrl: String, model: String, block: suspend (String) -> T): T {
    try {
      return block(model)
    } catch (e: AiBusyException) {
      val models = runCatching { listModels(provider, key, customUrl) }.getOrDefault(emptyList())
      val alternatives = AiModelPicker.quotaAlternatives(provider, models, exclude = model)
      for (m in alternatives) {
        try { return block(m) } catch (_: AiBusyException) {} catch (_: AiModelNotFoundException) {}
      }
      throw AiBusyException("AI 무료 사용 한도를 넘었어요 (다른 모델 ${alternatives.size}개도 시도했어요). 한도는 보통 하루 단위로 풀려요.")
    }
  }

  /** Whether [searchWeb] can be used with the chosen provider. */
  suspend fun canSearchWeb(): Boolean = settings.current().let { it.isAiReady && it.aiProvider == AiProvider.GEMINI }

  /** Free AI tiers are often briefly overloaded (503) or rate limited (429): wait and try again twice. */
  private suspend fun <T> withBusyRetry(block: suspend () -> T): T {
    var lastError: AiBusyException? = null
    for (wait in BUSY_WAITS_MS) {
      if (wait > 0) kotlinx.coroutines.delay(wait)
      try { return block() } catch (e: AiBusyException) { lastError = e }
    }
    throw lastError!!
  }

  /** Lists models for a key (also verifies the key). */
  suspend fun listModels(provider: AiProvider, apiKey: String, customBaseUrl: String): List<String> = when (provider) {
    AiProvider.GEMINI -> gemini.listModels(apiKey)
    AiProvider.CUSTOM -> compat.listModels(customBaseUrl, apiKey)
    else -> compat.listModels(provider.defaultBaseUrl!!, apiKey)
  }

  companion object {
    /** Before each try: now, after 2 s, after 6 s. */
    internal var BUSY_WAITS_MS = listOf(0L, 2_000L, 6_000L)
  }
}
