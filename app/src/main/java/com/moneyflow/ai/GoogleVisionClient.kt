package com.moneyflow.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Cloud Vision text reading (영수증 읽기, 3.2) with the user's own API key: the photo's text,
 * read by Google's OCR (not an AI model). The key is sent in a header, nothing is logged.
 */
@Singleton
class GoogleVisionClient @Inject constructor() {
  internal var transport: HttpTransport = UrlConnectionTransport

  /** The text in a JPEG photo (base64), lines separated by newlines; "" when there is none. */
  suspend fun readText(apiKey: String, jpegBase64: String): String = withContext(Dispatchers.IO) {
    val request = JSONObject().put("requests", JSONArray().put(
      JSONObject()
        .put("image", JSONObject().put("content", jpegBase64))
        .put("features", JSONArray().put(JSONObject().put("type", "DOCUMENT_TEXT_DETECTION")))
        .put("imageContext", JSONObject().put("languageHints", JSONArray(listOf("ko", "en"))))
    ))
    val (code, body) = transport.execute(
      "POST", URL, mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json"), request.toString()
    )
    if (code !in 200..299) throw AiRequestException(errorMessage(code, body))
    parseText(body)
  }

  /**
   * Checks a key with a tiny request: an answer (even "bad image") means the key, the API and
   * billing are fine; an HTTP error tells what is missing.
   */
  suspend fun verify(apiKey: String) = withContext(Dispatchers.IO) {
    val request = JSONObject().put("requests", JSONArray().put(
      JSONObject().put("image", JSONObject().put("content", TINY_JPEG)).put("features", JSONArray().put(JSONObject().put("type", "TEXT_DETECTION")))
    ))
    val (code, body) = transport.execute("POST", URL, mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json"), request.toString())
    if (code !in 200..299) throw AiRequestException(errorMessage(code, body))
  }

  companion object {
    private const val URL = "https://vision.googleapis.com/v1/images:annotate"
    /** 1×1 white JPEG. */
    private const val TINY_JPEG = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="

    internal fun parseText(body: String): String {
      val r = JSONObject(body).optJSONArray("responses")?.optJSONObject(0) ?: return ""
      r.optJSONObject("error")?.let { throw AiRequestException("구글 Vision 오류: ${it.optString("message").take(120)}") }
      return r.optJSONObject("fullTextAnnotation")?.optString("text").orEmpty().trim()
    }

    internal fun errorMessage(code: Int, body: String): String {
      val msg = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrDefault("")
      return when {
        code == 403 && msg.contains("billing", true) -> "구글 Vision: 결제 계정(카드)이 연결되지 않았어요. 구글 클라우드 콘솔에서 결제를 연결해 주세요 (월 1,000건은 무료예요)."
        code == 403 && (msg.contains("disabled", true) || msg.contains("not been used", true)) -> "구글 Vision: 'Cloud Vision API'가 꺼져 있어요. 콘솔에서 사용 설정을 눌러 주세요."
        code == 400 && msg.contains("API key", true) || code == 401 || code == 403 -> "구글 Vision 키가 맞지 않아요. 키를 다시 복사해 주세요."
        code == 429 -> "구글 Vision 사용 한도를 넘었어요."
        else -> "구글 Vision 오류 ($code) ${msg.take(80)}"
      }
    }
  }
}
