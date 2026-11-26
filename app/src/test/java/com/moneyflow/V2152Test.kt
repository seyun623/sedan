package com.moneyflow

import com.moneyflow.ai.AiBusyException
import com.moneyflow.ai.AiService
import com.moneyflow.ai.GeminiClient
import com.moneyflow.ai.HttpTransport
import com.moneyflow.ai.OpenAiCompatClient
import com.moneyflow.domain.cardimport.CardBrowsing
import com.moneyflow.domain.cardimport.StatementTextParser
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 2.15.2: fixes from the user's card-site test on a phone. */
@RunWith(RobolectricTestRunner::class)
class V2152Test {
  @Test fun weekdayUnderTheDateIsNotTheMerchant() {
    val screen = "11.25\n수요일\n쿠팡\n10,240원\n11.21 (금)\n스타벅스 5,600원"
    val items = StatementTextParser(LocalDate.of(2026, 11, 26)).parse(screen).items
    assertEquals(listOf("쿠팡", "스타벅스"), items.map { it.merchant })
  }

  @Test fun menuFinderSkipsRevolvingAndAds() {
    assertNull(CardBrowsing.linkRank("일부결제금액이월약정(리볼빙)"))
    assertNull(CardBrowsing.linkRank("카드의정석2 전월 실적 없는 실속 카드 3종 비교"))
    assertEquals(0, CardBrowsing.linkRank("이용내역조회 · 개인고객 바로가기"))
  }

  @Test fun busyAiIsTriedAgain() = runBlocking {
    AiService.BUSY_WAITS_MS = listOf(0L, 0L, 0L)
    val settings = testSettingsRepository()
    settings.setAiApiKeyEncrypted(PlainSecretStore.encrypt("KEY"))
    settings.setAiModel("gemini-2.5-flash")
    settings.setAiEnabled(true)
    var calls = 0
    val ok = JSONObject().put("candidates", JSONArray().put(JSONObject().put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "{\"ok\":true}")))))).toString()
    val t = HttpTransport { _, _, _, _ -> calls++; if (calls < 3) 503 to """{"error":{"message":"The model is overloaded"}}""" else 200 to ok }
    val ai = AiService(settings, PlainSecretStore, GeminiClient().apply { transport = t }, OpenAiCompatClient())
    assertEquals("{\"ok\":true}", ai.completeJson("p", JSONObject(), "{}"))
    assertEquals(3, calls)
    // Always busy: the user gets the message after three tries.
    calls = 0
    val busy = HttpTransport { _, _, _, _ -> calls++; 503 to "{}" }
    val ai2 = AiService(settings, PlainSecretStore, GeminiClient().apply { transport = busy }, OpenAiCompatClient())
    assertTrue(runCatching { ai2.completeJson("p", JSONObject(), "{}") }.exceptionOrNull() is AiBusyException)
    assertEquals(3, calls)
  }
}
