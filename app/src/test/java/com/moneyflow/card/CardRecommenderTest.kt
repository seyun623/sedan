package com.moneyflow.card

import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.card.CardPerformanceCalculator
import com.moneyflow.domain.card.CardRecommender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CardRecommenderTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private val calc = CardPerformanceCalculator(zone)
  private val rec = CardRecommender(calc)
  private val today = LocalDate.of(2026, 11, 20)
  private val shinhan = 1L
  private val hyundai = 2L
  private val samsung = 3L

  private fun tx(account: Long, day: LocalDate, amount: Long, category: String = "식비", memo: String = "") =
    TransactionEntity(type = TransactionType.EXPENSE, amount = amount, category = category, accountId = account,
      timestamp = LocalDateTime.of(day, LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), memo = memo)

  private fun benefit(id: Long, account: Long, title: String, category: String?, keywords: String = "", kind: BenefitKind = BenefitKind.PERCENT,
                      value: Long, cap: Long = 0, minPerf: Long = 0) =
    CardBenefitEntity(id, account, title, category, keywords, kind, value, monthlyCap = cap, minPerformance = minPerf)

  private val benefits = listOf(
    benefit(1, shinhan, "카페 10%", "카페/간식", value = 1000, cap = 5_000),
    benefit(2, hyundai, "카페 5%", "카페/간식", value = 500),
    benefit(3, samsung, "카페 20% (실적 50만)", "카페/간식", value = 2000, minPerf = 500_000),
    benefit(4, hyundai, "스타벅스 건당 1,000원", null, "스타벅스", BenefitKind.FIXED, 1_000)
  )

  private fun statuses(txs: List<TransactionEntity>) = listOf(
    calc.status(CardProfileEntity(shinhan, 300_000), benefits, txs, today),
    calc.status(CardProfileEntity(hyundai, 0), benefits, txs, today),
    calc.status(CardProfileEntity(samsung, 300_000, excludedCategories = "주거/통신"), benefits, txs, today)
  )

  @Test fun bestCardByExpectedBenefitSkippingIneligible() {
    // 삼성 had only 100,000 last month → its 20% needs 500,000 → not eligible.
    val txs = listOf(tx(samsung, LocalDate.of(2026, 10, 5), 100_000))
    val picks = rec.recommend(statuses(txs), "카페/간식", "동네카페", 6_000)
    assertEquals(listOf(shinhan, hyundai), picks.map { it.accountId })
    assertEquals(600, picks[0].expected)
    assertEquals("카페 10%", picks[0].benefit?.title)
  }

  @Test fun remainingCapLimitsAndUsedUpCapDropsTheCard() {
    // 신한: 48,000 of cafe spending this month → 4,800 of the 5,000 cap used.
    val txs = listOf(tx(shinhan, LocalDate.of(2026, 11, 3), 48_000, "카페/간식"))
    assertEquals(200, rec.recommend(statuses(txs), "카페/간식", "", 6_000).first { it.accountId == shinhan }.expected)
    val full = listOf(tx(shinhan, LocalDate.of(2026, 11, 3), 60_000, "카페/간식"))
    val picks = rec.recommend(statuses(full), "카페/간식", "", 6_000)
    assertEquals(hyundai, picks.first().accountId)
    assertTrue(picks.none { it.accountId == shinhan })
  }

  @Test fun merchantKeywordBeatsCategoryRate() {
    val picks = rec.recommend(statuses(emptyList()), "카페/간식", "스타벅스 강남", 5_000)
    assertEquals(hyundai, picks.first().accountId)
    assertEquals(1_000, picks.first().expected)
  }

  @Test fun withoutBenefitsSuggestsCardStillShortOfPerformance() {
    val picks = rec.recommend(statuses(emptyList()), "생활/마트", "이마트", 50_000)
    assertEquals(listOf(shinhan, samsung), picks.map { it.accountId })
    assertTrue(picks.all { it.forPerformance && it.expected == 0L })
    assertEquals(300_000, picks.first().shortage)
    // 삼성 doesn't count 주거/통신 toward 실적.
    assertEquals(listOf(shinhan), rec.recommend(statuses(emptyList()), "주거/통신", "", 50_000).map { it.accountId })
    assertTrue(rec.recommend(statuses(emptyList()), "식비", "", 0).isEmpty())
  }

  @Test fun guideHasOneRowPerCategoryAndMerchant() {
    val txs = listOf(
      tx(9, LocalDate.of(2026, 11, 1), 4_000, "카페/간식"),
      tx(9, LocalDate.of(2026, 11, 2), 6_000, "카페/간식"),
      tx(9, LocalDate.of(2026, 11, 3), 9_000, "카페/간식")
    )
    val rows = rec.guide(statuses(txs), { c, k -> CardRecommender.typicalAmount(txs, c, k) }, { "카페/간식" })
    assertEquals(setOf("카페/간식", "스타벅스"), rows.map { it.label }.toSet())
    val cafe = rows.first { it.label == "카페/간식" }
    assertEquals(6_000, cafe.typicalAmount)
    assertEquals(shinhan, cafe.best.accountId)
    assertEquals(hyundai, cafe.runnerUp?.accountId)
    val sb = rows.first { it.label == "스타벅스" }
    assertEquals(hyundai, sb.best.accountId)
    assertEquals(1_000, sb.best.expected)
  }

  @Test fun typicalAmountIsMedianAndFallsBackToCategory() {
    val txs = listOf(
      tx(9, today, 3_000, "카페/간식", "스타벅스"),
      tx(9, today, 7_000, "카페/간식", "스타벅스"),
      tx(9, today, 5_000, "카페/간식", "스타벅스"),
      tx(9, today, 20_000, "카페/간식", "빵집")
    )
    assertEquals(5_000, CardRecommender.typicalAmount(txs, "카페/간식", "스타벅스"))
    assertEquals(7_000, CardRecommender.typicalAmount(txs, "카페/간식", "투썸"))
    assertEquals(0, CardRecommender.typicalAmount(txs, "교통", null))
    assertNull(rec.guide(emptyList(), { _, _ -> 0 }, { it }).firstOrNull())
  }
}
