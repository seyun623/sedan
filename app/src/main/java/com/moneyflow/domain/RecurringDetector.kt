package com.moneyflow.domain

import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** "넷플릭스 · 매달 17일쯤 · 17,000원 · 4개월" — a payment typed in every month that could be a 고정 거래. */
data class RecurringSuggestion(
  val key: String,
  val title: String,
  val type: TransactionType,
  val amount: Long,
  val dayOfMonth: Int,
  val category: String,
  val accountId: Long,
  val paymentMethod: String,
  val months: Int,
  /** This month's payment is already recorded (registering should start next month). */
  val paidThisMonth: Boolean
)

/**
 * Finds payments the user records by hand about once a month with a similar amount on a similar
 * day (pure). Auto-recorded payments (SMS, notifications, card import) are left out: they are
 * already recorded every month, and a 고정 거래 would record them twice.
 */
class RecurringDetector(private val zone: ZoneId) {

  fun detect(
    txs: List<TransactionEntity>,
    existing: List<RecurringTransactionEntity>,
    dismissed: Set<String>,
    today: LocalDate
  ): List<RecurringSuggestion> {
    val current = YearMonth.from(today)
    val from = MonthKeys.startMillis(current.minusMonths(LOOKBACK_MONTHS), zone)
    return txs.asSequence()
      .filter { it.timestamp >= from && it.source == TransactionSource.MANUAL && it.type != TransactionType.TRANSFER }
      .filter { tx -> name(tx).let { it.length >= 2 && it != norm(tx.category) } }
      .groupBy { it.type to name(it) }
      .mapNotNull { (key, list) -> suggestion(key.first, key.second, list, existing, today, current) }
      .filter { it.key !in dismissed }
      .sortedByDescending { it.months }
      .toList()
  }

  private fun suggestion(
    type: TransactionType,
    name: String,
    list: List<TransactionEntity>,
    existing: List<RecurringTransactionEntity>,
    today: LocalDate,
    current: YearMonth
  ): RecurringSuggestion? {
    val sorted = list.sortedBy { it.timestamp }
    val dates = sorted.map { MonthKeys.toLocalDate(it.timestamp, zone) }
    val months = dates.map { YearMonth.from(it) }.distinct()
    if (months.size < MIN_MONTHS) return null
    // About once a month, not a place visited every week.
    if (sorted.size > months.size + 1) return null
    // Still going: seen within the last ~40 days.
    if (ChronoUnit.DAYS.between(dates.last(), today) > 40) return null
    // Similar amount.
    val amounts = sorted.map { it.amount }.sorted()
    val median = amounts[amounts.size / 2]
    if (amounts.last() - amounts.first() > maxOf(1_000L, median / 10)) return null
    // Similar day of the month.
    val days = dates.map { it.dayOfMonth }.sorted()
    if (days.last() - days.first() > MAX_DAY_SPREAD) return null
    val day = days[days.size / 2]
    val latest = sorted.last()
    val covered = existing.any { r ->
      r.type == type && (norm(r.title) == name || norm(r.memo) == name ||
        (r.category == latest.category && kotlin.math.abs(r.amount - latest.amount) <= latest.amount / 10 &&
          kotlin.math.abs(r.dayOfMonth - day) <= 3))
    }
    if (covered) return null
    return RecurringSuggestion(
      key = "${type.name}|$name",
      title = latest.memo.substringBefore(" (").trim().take(30),
      type = type,
      amount = latest.amount,
      dayOfMonth = day,
      category = latest.category,
      accountId = latest.accountId,
      paymentMethod = latest.paymentMethod,
      months = months.size,
      paidThisMonth = YearMonth.from(dates.last()) == current
    )
  }

  private fun name(tx: TransactionEntity) = norm(tx.memo.substringBefore(" (").replace(Regex("#\\S+"), ""))
  private fun norm(s: String) = s.replace(" ", "").lowercase()

  companion object {
    const val LOOKBACK_MONTHS = 6L
    const val MIN_MONTHS = 3
    const val MAX_DAY_SPREAD = 6
  }
}
