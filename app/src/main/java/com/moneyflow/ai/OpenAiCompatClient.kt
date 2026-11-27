package com.moneyflow.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Client for OpenAI-compatible chat APIs (OpenRouter, Groq, self-hosted gateways). */
@Singleton
class OpenAiCompatClient @Inject constructor() {
  internal var transport: HttpTransport = UrlConnectionTransport

  suspend fun listModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
    val (code, body) = transport.execute("GET", "${base(baseUrl)}/models", headers(apiKey), null)
    if (code !in 200..299) throw AiRequestException(GeminiClient.errorMessage(code, body))
    parseModelList(body)
  }

  /** Asks for a JSON object answer; the expected shape is described in the prompt. */
  suspend fun completeJson(baseUrl: String, apiKey: String, model: String, prompt: String): String = withContext(Dispatchers.IO) {
    val request = JSONObject()
      .put("model", model)
      .put("temperature", 0.2)
      .put("response_format", JSONObject().put("type", "json_object"))
      .put("messages", JSONArray()
        .put(JSONObject().put("role", "system").put("content", "Answer with a single JSON object only. No markdown."))
        .put(JSONObject().put("role", "user").put("content", prompt)))
    val (code, body) = transport.execute(
      "POST", "${base(baseUrl)}/chat/completions", headers(apiKey) + ("Content-Type" to "application/json"), request.toString()
    )
    if (code !in 200..299) throw aiFailure(code, body)
    parseContent(body) ?: throw AiRequestException("AI 응답이 비어 있어요.")
  }

  private fun headers(apiKey: String) = mapOf(
    "Authorization" to "Bearer $apiKey",
    // OpenRouter attribution headers (ignored by other providers)
    "X-Title" to "MoneyFlow"
  )

  companion object {
    fun base(url: String): String {
      val u = url.trim().trimEnd('/')
      require(u.startsWith("https://")) { "https 주소만 사용할 수 있어요." }
      return u
    }

    internal fun parseModelList(body: String): List<String> {
      val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
      return (0 until data.length()).map { data.getJSONObject(it).getString("id") }
        .sortedWith(compareByDescending<String> { it.endsWith(":free") }.thenBy { it })
    }

    /** Extracts the assistant text and strips ```json fences some models add anyway. */
    internal fun parseContent(body: String): String? {
      val choices = JSONObject(body).optJSONArray("choices") ?: return null
      if (choices.length() == 0) return null
      val text = choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty().trim()
      return extractJsonObject(text)
    }

    internal fun extractJsonObject(text: String): String? {
      val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
      val start = cleaned.indexOf('{')
      val end = cleaned.lastIndexOf('}')
      return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }
  }
}
