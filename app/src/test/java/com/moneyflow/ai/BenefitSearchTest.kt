package com.moneyflow.ai

import com.moneyflow.domain.card.BenefitReader
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 2.15.1: card benefits found by web search (Gemini's Google Search). */
@RunWith(RobolectricTestRunner::class)
class BenefitSearchTest {
  private fun answer(text: String, sources: List<Pair<String, String>> = emptyList()): String {
    val candidate = JSONObject().put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))))
    if (sources.isNotEmpty()) {
      candidate.put("groundingMetadata", JSONObject().put("groundingChunks", JSONArray().apply {
        sources.forEach { (t, u) -> put(JSONObject().put("web", JSONObject().put("title", t).put("uri", u))) }
      }))
    }
    return JSONObject().put("candidates", JSONArray().put(candidate)).toString()
  }

  @Test fun searchThenReadWithSources() = runBlocking {
    val settings = testSettingsRepository()
    settings.setAiApiKeyEncrypted(PlainSecretStore.encrypt("KEY"))
    settings.setAiModel("gemini-2.5-flash")
    settings.setAiEnabled(true)
    val bodies = mutableListOf<String>()
    val t = HttpTransport { _, _, _, body ->
      bodies += body.orEmpty()
      if (bodies.size == 1) 200 to answer("신한카드 Deep Dream\n- 모든 가맹점 0.7% 적립 (전월 실적 없음)\n- 스타벅스 5% 추가 적립 월 5천원", listOf("신한카드" to "https://www.shinhancard.com/x", "카드고릴라" to "https://www.card-gorilla.com/card/detail/1"))
      else 200 to answer("""{"benefits":[{"title":"스타벅스 5% 적립","category":"카페/간식","merchants":"스타벅스","kind":"PERCENT","value":5,"monthlyCap":5000,"perUseCap":0,"minPerformance":0}]}""")
    }
    val ai = AiService(settings, PlainSecretStore, GeminiClient().apply { transport = t }, OpenAiCompatClient())
    val found = BenefitReader(ai).search("신한카드 Deep Dream")!!
    // The first request searches the web; the second turns the answer into benefits.
    assertTrue(JSONObject(bodies[0]).getJSONArray("tools").getJSONObject(0).has("google_search"))
    assertTrue(bodies[0].contains("신한카드 Deep Dream"))
    assertEquals(listOf("신한카드", "카드고릴라"), found.sources.map { it.first })
    assertEquals("스타벅스 5% 적립", found.drafts.single().title)
  }

  @Test fun otherProvidersAnswerFromMemory() = runBlocking {
    val settings = testSettingsRepository()
    settings.setAiProvider(AiProvider.OPENROUTER)
    settings.setAiApiKeyEncrypted(PlainSecretStore.encrypt("KEY"))
    settings.setAiModel("x:free")
    settings.setAiEnabled(true)
    val content = JSONObject().put("benefits", JSONArray().put(JSONObject().put("title", "대중교통 10% 할인").put("category", "교통")
      .put("merchants", "버스, 지하철").put("kind", "PERCENT").put("value", 10).put("monthlyCap", 5000).put("perUseCap", 0).put("minPerformance", 300000))).toString()
    val reply = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()
    val t = HttpTransport { _, _, _, _ -> 200 to reply }
    val ai = AiService(settings, PlainSecretStore, GeminiClient(), OpenAiCompatClient().apply { transport = t })
    val found = BenefitReader(ai).search("삼성카드 taptap O")!!
    assertTrue(found.fromMemory)
    assertTrue(found.sources.isEmpty())
    assertEquals("대중교통 10% 할인", found.drafts.single().title)
  }
}
