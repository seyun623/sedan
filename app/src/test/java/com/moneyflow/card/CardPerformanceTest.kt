package com.moneyflow.card

import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.card.CardPerformanceCalculator
import com.moneyflow.work.CardAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CardPerformanceTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private val calc = CardPerformanceCalculator(zone)
  private val card = 7L

  private fun tx(day: LocalDate, amount: Long, category: String = "식비", memo: String = "", account: Long = card) =
    TransactionEntity(type = TransactionType.EXPENSE, amount = amount, category = category, accountId = account,
      timestamp = LocalDateTime.of(day, java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), memo = memo)

  @Test fun periodsForCalendarAndBillingCycles() {
    val d = LocalDate.of(2026, 11, 23)
    with(calc.periodContaining(d, 1)) { assertEquals(LocalDate.of(2026, 11, 1), start); assertEquals(LocalDate.of(2026, 11, 30), end) }
    with(calc.periodContaining(d, 13)) { assertEquals(LocalDate.of(2026, 11, 13), start); assertEquals(LocalDate.of(2026, 12, 12), end) }
    with(calc.periodContaining(LocalDate.of(2026, 11, 5), 13)) { assertEquals(LocalDate.of(2026, 10, 13), start) }
    assertEquals(LocalDate.of(2026, 10, 1), calc.previous(calc.periodContaining(d, 1)).start)
  }

  @Test fun performanceExcludesCategoriesKeywordsAndOtherCards() {
    val profile = CardProfileEntity(card, 300_000, 1, "주거/통신", "관리비,상품권")
    val today = LocalDate.of(2026, 11, 27)
    val txs = listOf(
      tx(LocalDate.of(2026, 11, 3), 100_000),
      tx(LocalDate.of(2026, 11, 4), 50_000, "주거/통신"),
      tx(LocalDate.of(2026, 11, 5), 200_000, "생활/마트", "아파트 관리비"),
      tx(LocalDate.of(2026, 11, 6), 30_000, account = 99),
      tx(LocalDate.of(2026, 10, 20), 320_000)
    )
    val s = calc.status(profile, emptyList(), txs, today)
    assertEquals(100_000, s.performance)
    assertEquals(320_000, s.previousPerformance)
    assertEquals(200_000, s.shortage)
    assertEquals(4, s.daysLeft)
    assertTrue(s.needsAttention)
    assertFalse(s.achieved)
  }

  @Test fun benefitsRespectRateCapMinimumAndPreviousPerformance() {
    val profile = CardProfileEntity(card, 300_000)
    val today = LocalDate.of(2026, 11, 20)
    val cafe = CardBenefitEntity(1, card, "카페 10%", "카페/간식", "", BenefitKind.PERCENT, 1000, monthlyCap = 1_000, minPerformance = 300_000)
    val coffeeShop = CardBenefitEntity(2, card, "스타벅스 건당 500원", null, "스타벅스", BenefitKind.FIXED, 500, minAmount = 5_000)
    val txs = listOf(
      tx(LocalDate.of(2026, 10, 10), 350_000),                                   // last period: 350,000 ≥ 300,000
      tx(LocalDate.of(2026, 11, 2), 6_000, "카페/간식", "스타벅스 역삼"),           // 600 + 500
      tx(LocalDate.of(2026, 11, 3), 8_000, "카페/간식", "투썸"),                   // 800 → capped at 1,000 total
      tx(LocalDate.of(2026, 11, 4), 4_000, "카페/간식", "스타벅스")                 // below 5,000 min for fixed; 400 for %
    )
    val s = calc.status(profile, listOf(cafe, coffeeShop), txs, today)
    val pct = s.benefits.first { it.benefit.id == 1L }
    assertTrue(pct.eligible)
    assertEquals(1_000, pct.earned)
    assertEquals(0L, pct.remainingCap)
    assertTrue(pct.capReached)
    val fixed = s.benefits.first { it.benefit.id == 2L }
    assertEquals(1, fixed.matchingCount)
    assertEquals(500, fixed.earned)
    assertEquals(1_500, s.totalBenefit)
  }

  @Test fun benefitIsZeroWhenPreviousPerformanceIsShort() {
    val profile = CardProfileEntity(card, 300_000)
    val cafe = CardBenefitEntity(1, card, "카페 10%", "카페/간식", "", BenefitKind.PERCENT, 1000, minPerformance = 300_000)
    val s = calc.status(profile, listOf(cafe), listOf(tx(LocalDate.of(2026, 11, 2), 6_000, "카페/간식")), LocalDate.of(2026, 11, 20))
    assertFalse(s.benefits.single().eligible)
    assertEquals(0, s.benefits.single().earned)
  }

  @Test fun alertsAreSentOncePerPeriod() {
    val profile = CardProfileEntity(card, 300_000)
    val cafe = CardBenefitEntity(1, card, "카페 10%", "카페/간식", "", BenefitKind.PERCENT, 1000, monthlyCap = 500)
    val s = calc.status(profile, listOf(cafe), listOf(tx(LocalDate.of(2026, 11, 2), 9_000, "카페/간식")), LocalDate.of(2026, 11, 28))
    val due = CardAlerts.due(listOf(s), mapOf(card to "현대카드"), emptySet())
    assertEquals(2, due.size)
    assertTrue(due[0].title.contains("현대카드") && due[0].title.contains("부족"))
    assertTrue(due[1].title.contains("한도"))
    assertTrue(CardAlerts.due(listOf(s), mapOf(card to "현대카드"), due.map { it.key }.toSet()).isEmpty())
    // Not near the end of the period -> no shortage alert
    val early = calc.status(profile, emptyList(), emptyList(), LocalDate.of(2026, 11, 10))
    assertTrue(CardAlerts.due(listOf(early), emptyMap(), emptySet()).isEmpty())
  }
}
