package com.moneyflow.ai

import com.moneyflow.data.model.TransactionType
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.testSettingsRepository
import com.moneyflow.ui.settings.AiSettingsViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiTest {

  private class FakeTransport(val responses: Map<String, Pair<Int, String>>) : HttpTransport {
    val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
    var lastBody: String? = null
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
      calls += Triple(method, url, headers); lastBody = body
      return responses.entries.first { url.contains(it.key) }.value
    }
  }

  private val modelsJson = """{"models":[
    {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent","countTokens"]},
    {"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]},
    {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},
    {"name":"models/gemini-2.5-flash-image-preview","supportedGenerationMethods":["generateContent"]}]}"""

  private fun answer(json: String) = JSONObject().put("candidates",
    org.json.JSONArray().put(JSONObject().put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", json)))))).toString()

  @Test fun modelListKeepsOnlyGeminiGenerateContentModels() {
    assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-flash-image-preview"), GeminiClient.parseModelList(modelsJson))
    assertEquals("gemini-2.5-flash", AiSettingsViewModel.pickDefaultModel(GeminiClient.parseModelList(modelsJson)))
  }

  @Test fun errorsAreTranslated() {
    assertTrue(GeminiClient.errorMessage(400, """{"error":{"message":"API key not valid"}}""").contains("API 키"))
    assertTrue(GeminiClient.errorMessage(429, "").contains("한도"))
    assertNull(GeminiClient.parseGeneratedText("""{"candidates":[]}"""))
  }

  @Test fun keyIsSentInHeaderNotUrl() = runBlocking {
    val t = FakeTransport(mapOf("models?" to (200 to modelsJson)))
    val client = GeminiClient().apply { transport = t }
    client.listModels("SECRET-KEY-123")
    val (_, url, headers) = t.calls.single()
    assertFalse(url.contains("SECRET-KEY-123"))
    assertEquals("SECRET-KEY-123", headers["x-goog-api-key"])
  }

  @Test fun suggestionUsesStoredKeyModelAndAllowedCategories() = runBlocking {
    val settings = testSettingsRepository()
    val t = FakeTransport(mapOf(":generateContent" to (200 to answer(
      """{"recommendedCategory":"카페/간식","suggestedPaymentMethod":"체크카드","confidence":93,"reason":"커피 전문점"}"""))))
    val suggester = CategorySuggester(settings, PlainSecretStore, GeminiClient().apply { transport = t })

    // Not configured yet
    assertTrue(runCatching { suggester.suggest("스타벅스", TransactionType.EXPENSE) }.exceptionOrNull() is AiNotConfiguredException)

    settings.setAiApiKeyEncrypted(PlainSecretStore.encrypt("MY-KEY"))
    settings.setAiModel("gemini-2.5-flash")
    settings.setAiEnabled(true)
    val rec = suggester.suggest("스타벅스", TransactionType.EXPENSE)!!
    assertEquals("카페/간식", rec.recommendedCategory)
    assertEquals(93, rec.confidence)
    val (_, url, headers) = t.calls.single()
    assertTrue(url.endsWith("/models/gemini-2.5-flash:generateContent"))
    assertEquals("MY-KEY", headers["x-goog-api-key"])
    val sent = JSONObject(t.lastBody!!)
    assertEquals("application/json", sent.getJSONObject("generationConfig").getString("responseMimeType"))
    assertTrue(sent.toString().contains("스타벅스"))
  }

  @Test fun removingKeyTurnsAiOff() = runBlocking {
    val settings = testSettingsRepository()
    settings.setAiApiKeyEncrypted("enc:k"); settings.setAiEnabled(true)
    assertTrue(settings.current().isAiReady)
    settings.setAiApiKeyEncrypted(null)
    assertFalse(settings.current().isAiReady)
    assertFalse(settings.current().aiEnabled)
  }
}
