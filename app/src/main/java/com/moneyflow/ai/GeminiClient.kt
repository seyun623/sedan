package com.moneyflow.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

open class AiRequestException(message: String) : IOException(message)

/** The chosen model doesn't exist (any more) at the provider (HTTP 404). */
class AiModelNotFoundException(message: String) : AiRequestException(message)

/** The provider is overloaded or briefly failing (HTTP 5xx / 429): worth trying again shortly. */
class AiBusyException(message: String) : AiRequestException(message)

/** Throws the right exception for an unsuccessful AI response. */
internal fun aiFailure(code: Int, body: String): AiRequestException = when {
  code == 404 && !body.contains("policy", ignoreCase = true) -> AiModelNotFoundException(GeminiClient.errorMessage(code, body))
  code in 500..599 || code == 429 -> AiBusyException(GeminiClient.errorMessage(code, body))
  else -> AiRequestException(GeminiClient.errorMessage(code, body))
}

/** Transport so the client can be tested without the network. */
fun interface HttpTransport {
  /** Returns (status code, body). */
  fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String>
}

object UrlConnectionTransport : HttpTransport {
  override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      conn.requestMethod = method
      conn.connectTimeout = 15_000
      conn.readTimeout = 45_000
      headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
      if (body != null) {
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      }
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
      return code to text
    } finally {
      conn.disconnect()
    }
  }
}

/**
 * Minimal Google Gemini API (generativelanguage.googleapis.com) client using the user's own API key.
 * The key is sent in a header (never in the URL), and nothing is logged.
 */
@Singleton
class GeminiClient @Inject constructor() {
  internal var transport: HttpTransport = UrlConnectionTransport

  /** Model ids that support generateContent, e.g. "gemini-2.5-flash". */
  suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
    val (code, body) = transport.execute("GET", "$BASE/models?pageSize=200", headers(apiKey), null)
    if (code !in 200..299) throw AiRequestException(errorMessage(code, body))
    parseModelList(body)
  }

  /** Sends [prompt] and returns the model's text output, constrained to JSON by [responseSchema]. */
  suspend fun generateJson(
    apiKey: String, model: String, prompt: String, responseSchema: JSONObject,
    /** (mime type, base64) of a photo to read with the prompt (영수증). */
    image: Pair<String, String>? = null
  ): String =
    withContext(Dispatchers.IO) {
      val parts = JSONArray().put(JSONObject().put("text", prompt))
      image?.let { (mime, data) -> parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", data))) }
      val request = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("responseSchema", responseSchema))
      val url = "$BASE/models/${URLEncoder.encode(model.removePrefix("models/"), "UTF-8")}:generateContent"
      val (code, body) = transport.execute("POST", url, headers(apiKey) + ("Content-Type" to "application/json"), request.toString())
      if (code !in 200..299) throw aiFailure(code, body)
      parseGeneratedText(body) ?: throw AiRequestException("AI 응답이 비어 있어요.")
    }

  /**
   * Answers [prompt] with Google Search grounding (the model searches the web first). Returns the
   * text and the web pages it used (title, address).
   */
  suspend fun generateGrounded(apiKey: String, model: String, prompt: String): Pair<String, List<Pair<String, String>>> =
    withContext(Dispatchers.IO) {
      val request = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
      val url = "$BASE/models/${URLEncoder.encode(model.removePrefix("models/"), "UTF-8")}:generateContent"
      val (code, body) = transport.execute("POST", url, headers(apiKey) + ("Content-Type" to "application/json"), request.toString())
      if (code !in 200..299) throw aiFailure(code, body)
      val text = parseGeneratedText(body) ?: throw AiRequestException("AI 응답이 비어 있어요.")
      text to parseSources(body)
    }

  private fun headers(apiKey: String) = mapOf("x-goog-api-key" to apiKey)

  companion object {
    const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    internal fun parseModelList(body: String): List<String> {
      val models = JSONObject(body).optJSONArray("models") ?: return emptyList()
      return (0 until models.length()).map { models.getJSONObject(it) }
        .filter { m ->
          val methods = m.optJSONArray("supportedGenerationMethods")
          methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
        }
        .map { it.getString("name").removePrefix("models/") }
        .filter { it.startsWith("gemini") }
    }

    /** Web pages a grounded answer used: candidates[0].groundingMetadata.groundingChunks[].web. */
    internal fun parseSources(body: String): List<Pair<String, String>> {
      val chunks = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
        ?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: return emptyList()
      return (0 until chunks.length()).mapNotNull { i ->
        val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: return@mapNotNull null
        web.optString("title").ifBlank { "웹 페이지" } to web.optString("uri")
      }.distinctBy { it.first }
    }

    internal fun parseGeneratedText(body: String): String? {
      val candidates = JSONObject(body).optJSONArray("candidates") ?: return null
      if (candidates.length() == 0) return null
      val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
      return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim().ifEmpty { null }
    }

    internal fun errorMessage(code: Int, body: String): String {
      val apiMessage = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
      return when (code) {
        400 -> if (apiMessage.contains("API key", true)) "API 키가 올바르지 않아요." else "요청이 거부됐어요. 모델을 다시 골라 보세요."
        401, 403 -> "API 키가 올바르지 않거나 권한이 없어요."
        404 -> if (apiMessage.contains("policy", true))
          "OpenRouter 계정의 개인정보 설정 때문에 무료 모델을 쓸 수 없어요. openrouter.ai → Settings → Privacy에서 무료 모델 사용(Free model publication)을 허용해 주세요."
          else "선택한 모델을 찾을 수 없어요. 설정 → AI 설정에서 모델 목록을 다시 불러와 주세요."
        429 -> "사용 한도를 넘었어요. 잠시 후 다시 시도해 주세요."
        in 500..599 -> "AI 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요."
        else -> "AI 요청에 실패했어요 ($code)."
      }
    }
  }
}
