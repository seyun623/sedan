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
    assertEquals("b:free", AiSettingsViewModel.pickDefaultModel(AiProvider.OPENROUTER, listOf("a", "b:free")))
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

  private fun service(settings: com.moneyflow.data.repository.SettingsRepository, gemini: FakeTransport? = null, compat: FakeTransport? = null) =
    AiService(settings, PlainSecretStore, GeminiClient().apply { gemini?.let { transport = it } }, OpenAiCompatClient().apply { compat?.let { transport = it } })

  @Test fun suggestionUsesStoredKeyModelAndAllowedCategories() = runBlocking {
    val db = com.moneyflow.testing.inMemoryDb()
    val settings = testSettingsRepository()
    val t = FakeTransport(mapOf(":generateContent" to (200 to answer(
      """{"recommendedCategory":"문화/여가","suggestedPaymentMethod":"체크카드","confidence":93,"reason":"공연"}"""))))
    val suggester = CategorySuggester(service(settings, gemini = t), db, com.moneyflow.testing.FakeClock())

    // Not configured yet (and not answerable locally)
    assertTrue(runCatching { suggester.suggest("뮤지컬 티켓", TransactionType.EXPENSE) }.exceptionOrNull() is AiNotConfiguredException)

    settings.setAiApiKeyEncrypted(PlainSecretStore.encrypt("MY-KEY"))
    settings.setAiModel("gemini-2.5-flash")
    settings.setAiEnabled(true)
    val rec = suggester.suggest("뮤지컬 티켓", TransactionType.EXPENSE)!!
    assertEquals("문화/여가", rec.recommendedCategory)
    val (_, url, headers) = t.calls.single()
    assertTrue(url.endsWith("/models/gemini-2.5-flash:generateContent"))
    assertEquals("MY-KEY", headers["x-goog-api-key"])
    assertTrue(JSONObject(t.lastBody!!).toString().contains("뮤지컬"))
    // cached: no second request
    suggester.suggest("뮤지컬 티켓", TransactionType.EXPENSE)
    assertEquals(1, t.calls.size)
    db.close()
  }

  @Test fun knownMerchantsAreAnsweredLocallyWithoutCallingAi() = runBlocking {
    val db = com.moneyflow.testing.inMemoryDb()
    val settings = testSettingsRepository()
    val t = FakeTransport(mapOf("" to (500 to "")))
    val suggester = CategorySuggester(service(settings, gemini = t), db, com.moneyflow.testing.FakeClock())
    assertEquals("카페/간식", suggester.suggest("스타벅스", TransactionType.EXPENSE)!!.recommendedCategory)
    assertTrue(t.calls.isEmpty())
    db.close()
  }

  @Test fun openRouterProviderUsesBearerKeyAndChatCompletions() = runBlocking {
    val settings = testSettingsRepository()
    val t = FakeTransport(mapOf("/chat/completions" to (200 to """{"choices":[{"message":{"content":"```json\n{\"recommendedCategory\":\"쇼핑\",\"suggestedPaymentMethod\":\"신용카드\",\"confidence\":80,\"reason\":\"x\"}\n```"}}]}""")))
    settings.setAiProvider(AiProvider.OPENROUTER)
    settings.setAiApiKeyEncrypted(PlainSecretStore.encrypt("OR-KEY"))
    settings.setAiEnabled(true)
    val out = service(settings, compat = t).completeJson("p", JSONObject(), "{}")
    assertEquals("쇼핑", JSONObject(out).getString("recommendedCategory"))
    val (method, url, headers) = t.calls.single()
    assertEquals("POST", method)
    assertEquals("https://openrouter.ai/api/v1/chat/completions", url)
    assertEquals("Bearer OR-KEY", headers["Authorization"])
    assertEquals(AiProvider.OPENROUTER.defaultModel, JSONObject(t.lastBody!!).getString("model"))
  }

  @Test fun keysAndModelsAreKeptPerProvider() = runBlocking {
    val settings = testSettingsRepository()
    settings.setAiApiKeyEncrypted("enc:g"); settings.setAiModel("gemini-x")
    settings.setAiProvider(AiProvider.GROQ)
    assertFalse(settings.current().hasAiKey)
    settings.setAiApiKeyEncrypted("enc:q")
    settings.setAiProvider(AiProvider.GEMINI)
    assertEquals("enc:g", settings.current().aiApiKeyEncrypted)
    assertEquals("gemini-x", settings.current().aiModel)
  }

  @Test fun customProviderRequiresHttps() {
    assertTrue(runCatching { OpenAiCompatClient.base("http://192.168.0.2:20128/v1") }.isFailure)
    assertEquals("https://x.example/v1", OpenAiCompatClient.base("https://x.example/v1/"))
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
