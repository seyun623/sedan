package com.moneyflow

import com.moneyflow.ai.AiModelPicker
import com.moneyflow.ai.AiProvider
import com.moneyflow.domain.StartGuide
import com.moneyflow.domain.card.BenefitReader
import com.moneyflow.domain.cardimport.SavedPages
import com.moneyflow.domain.cardimport.StatementExtraction
import com.moneyflow.domain.cardimport.StatementItem
import com.moneyflow.domain.rules.RuleKeywords
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 3.0.1: fixes from the 3.0.0 phone check. */
@RunWith(RobolectricTestRunner::class)
class V301Test {
  @Test fun ruleKeywordsAreShort() {
    assertEquals("후라토식당", RuleKeywords.suggest("후라토식당 성수점"))
    assertEquals("예스이십사", RuleKeywords.suggest("예스이십사(주)"))
    assertEquals("비케이알", RuleKeywords.suggest("주식회사 비케이알"))
    assertEquals("가온에프앤에스", RuleKeywords.suggest("(주)가온에프앤에스 프리미엄직원식당 성수"))
    assertEquals("구글페이먼트코리아유한회사".replace("유한회사", ""), RuleKeywords.suggest("구글페이먼트코리아유한회사 누적"))
    assertEquals("키보우", RuleKeywords.suggest("키보우"))
  }

  @Test fun conditionsFromTheBenefitAnswer() {
    val json = JSONObject().put("performanceTarget", 300_000).put("excludedFromPerformance", JSONArray(listOf("관리비 납부", "세금", "상품권 구매", "x", "세금")))
    val c = BenefitReader.parseConditions(json)!!
    assertEquals(300_000L, c.performanceTarget)
    assertEquals(listOf("관리비", "세금", "상품권 구매"), c.excluded)
    assertNull(BenefitReader.parseConditions(JSONObject().put("performanceTarget", 0)))
  }

  @Test fun quotaFallbackTriesOtherGeminiModels() {
    val models = listOf("gemini-3.1-flash-lite", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "text-embedding-004", "gemini-3-flash-preview")
    val alt = AiModelPicker.quotaAlternatives(AiProvider.GEMINI, models, exclude = "gemini-3.1-flash-lite")
    assertEquals(listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-3-flash-preview"), alt)
    assertTrue(AiModelPicker.quotaAlternatives(AiProvider.GROQ, models, "x").isEmpty())
  }

  @Test fun neededOnlyKeepsTheLastMonth() {
    val today = LocalDate.of(2026, 11, 27)
    val e = StatementExtraction(listOf(
      StatementItem(LocalDate.of(2026, 11, 20), null, "a", 1_000),
      StatementItem(LocalDate.of(2026, 10, 27), null, "b", 2_000),
      StatementItem(LocalDate.of(2026, 9, 1), null, "c", 3_000)
    ), emptyList(), "기기에서 읽기")
    assertEquals(listOf("a", "b"), SavedPages.lastMonth(e, today).items.map { it.merchant })
  }

  @Test fun startGuideCanBeShownAgain() {
    val done = StartGuide.steps(true, false, false, budgetSet = true, familyOn = true, backupOn = true)
    assertTrue(!StartGuide.visible(done, dismissed = false))
    assertTrue(StartGuide.visible(done, dismissed = false, forced = true))
  }

  @Test fun pointLinksAreFound() {
    assertEquals(4, com.moneyflow.domain.cardimport.CardBrowsing.linkRank("포인트 조회"))
    assertTrue(com.moneyflow.domain.cardimport.CardBrowsing.LIFT_BOTTOM_SHEETS_JS.contains("max-height"))
  }
}
