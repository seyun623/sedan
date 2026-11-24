package com.moneyflow.domain

import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurringDetectorTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private val detector = RecurringDetector(zone)
  private val today = LocalDate.of(2026, 11, 24)
  private var id = 0L

  private fun tx(date: LocalDate, memo: String, amount: Long, category: String = "문화/여가", source: TransactionSource = TransactionSource.MANUAL,
                 type: TransactionType = TransactionType.EXPENSE) = TransactionEntity(
    id = ++id, type = type, amount = amount, category = category, accountId = 1,
    timestamp = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), memo = memo, source = source
  )

  private fun monthly(memo: String, day: Int, amounts: List<Long>, lastMonth: Int = 11, category: String = "문화/여가",
                      source: TransactionSource = TransactionSource.MANUAL, type: TransactionType = TransactionType.EXPENSE) =
    amounts.mapIndexed { i, a -> tx(LocalDate.of(2026, lastMonth - (amounts.size - 1 - i), day), memo, a, category, source, type) }

  @Test fun findsMonthlyHandTypedPayment() {
    val txs = monthly("넷플릭스", 17, listOf(17_000, 17_000, 17_000, 17_000)) +
      monthly("월급", 25, listOf(3_000_000, 3_000_000, 3_000_000), lastMonth = 10, category = "급여", type = TransactionType.INCOME)
    val s = detector.detect(txs, emptyList(), emptySet(), today)
    assertEquals(listOf("넷플릭스", "월급"), s.map { it.title })
    with(s[0]) { assertEquals(17, dayOfMonth); assertEquals(17_000, amount); assertEquals(4, months); assertTrue(paidThisMonth) }
    assertEquals(false, s[1].paidThisMonth)
  }

  @Test fun skipsAutoRecordedFrequentChangingStoppedCoveredAndDismissed() {
    val auto = monthly("유튜브 프리미엄", 5, listOf(14_900, 14_900, 14_900), source = TransactionSource.SMS)
    val weekly = (1..12).map { tx(LocalDate.of(2026, 9, 1).plusWeeks(it.toLong()), "스타벅스", 5_000, "카페/간식") }
    val changing = monthly("전기요금", 20, listOf(30_000, 52_000, 41_000), category = "주거/통신")
    val stopped = monthly("헬스장", 3, listOf(50_000, 50_000, 50_000), lastMonth = 9)
    val drifting = listOf(tx(LocalDate.of(2026, 9, 1), "학원비", 200_000), tx(LocalDate.of(2026, 10, 15), "학원비", 200_000), tx(LocalDate.of(2026, 11, 28).minusDays(10), "학원비", 200_000))
    val covered = monthly("월세", 1, listOf(600_000, 600_000, 600_000), category = "주거/통신")
    val categoryOnly = monthly("식비", 10, listOf(10_000, 10_000, 10_000), category = "식비")
    val dismissedOne = monthly("멜론", 9, listOf(10_900, 10_900, 10_900))
    val existing = listOf(RecurringTransactionEntity(title = "월세", type = TransactionType.EXPENSE, amount = 600_000, category = "주거/통신", dayOfMonth = 1, accountId = 1, accountName = "통장"))
    val s = detector.detect(auto + weekly + changing + stopped + drifting + covered + categoryOnly + dismissedOne, existing, setOf("EXPENSE|멜론"), today)
    assertTrue(s.map { it.title }.toString(), s.isEmpty())
  }
}
