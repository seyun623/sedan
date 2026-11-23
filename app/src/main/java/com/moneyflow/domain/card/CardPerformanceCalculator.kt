package com.moneyflow.domain.card

import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.MonthKeys
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A 실적 period, inclusive. */
data class CardPeriod(val start: LocalDate, val end: LocalDate) {
  /** Stable key used to send each alert once per period. */
  val key: String get() = start.toString()
  val label: String get() = "${start.monthValue}.${start.dayOfMonth} ~ ${end.monthValue}.${end.dayOfMonth}"
}

data class BenefitUsage(
  val benefit: CardBenefitEntity,
  /** Benefit earned this period (capped). */
  val earned: Long,
  /** null when there is no cap. */
  val remainingCap: Long?,
  val matchingCount: Int,
  /** false when last period's 실적 was below the benefit's requirement. */
  val eligible: Boolean
) {
  val capReached: Boolean get() = remainingCap == 0L && eligible
}

data class CardStatus(
  val accountId: Long,
  val profile: CardProfileEntity,
  val period: CardPeriod,
  val performance: Long,
  val previousPerformance: Long,
  val daysLeft: Int,
  val benefits: List<BenefitUsage>
) {
  val target: Long get() = profile.performanceTarget
  val shortage: Long get() = (target - performance).coerceAtLeast(0)
  val progress: Float get() = if (target > 0) (performance.toFloat() / target).coerceIn(0f, 1f) else 0f
  val achieved: Boolean get() = target > 0 && performance >= target
  val totalBenefit: Long get() = benefits.sumOf { it.earned }
  /** Short of the goal with little time left. */
  val needsAttention: Boolean get() = target > 0 && !achieved && daysLeft <= ALERT_DAYS

  companion object { const val ALERT_DAYS = 5 }
}

/** Pure 실적 / benefit calculations for card accounts. */
class CardPerformanceCalculator(private val zone: ZoneId) {

  fun periodContaining(date: LocalDate, cycleStartDay: Int): CardPeriod {
    val day = cycleStartDay.coerceIn(1, 28)
    val start = if (date.dayOfMonth >= day) date.withDayOfMonth(day) else date.minusMonths(1).withDayOfMonth(day)
    return CardPeriod(start, start.plusMonths(1).minusDays(1))
  }

  fun previous(period: CardPeriod): CardPeriod {
    val start = period.start.minusMonths(1)
    return CardPeriod(start, period.start.minusDays(1))
  }

  /** Expenses of the card in the period that count toward 실적. */
  fun countedExpenses(txs: List<TransactionEntity>, profile: CardProfileEntity, period: CardPeriod): List<TransactionEntity> {
    val from = MonthKeys.dayStartMillis(period.start, zone)
    val to = MonthKeys.dayEndMillis(period.end, zone)
    return txs.filter { tx ->
      tx.accountId == profile.accountId && tx.type == TransactionType.EXPENSE && tx.timestamp in from..to &&
        countsToward(profile, tx.category, tx.memo)
    }
  }

  /** Whether a payment with this category / memo counts toward the card's 실적. */
  fun countsToward(profile: CardProfileEntity, category: String, memo: String): Boolean =
    category !in profile.excludedCategories.splitList() &&
      profile.excludedKeywords.splitList().none { w -> memo.contains(w, ignoreCase = true) }

  fun status(
    profile: CardProfileEntity,
    benefits: List<CardBenefitEntity>,
    txs: List<TransactionEntity>,
    today: LocalDate
  ): CardStatus {
    val period = periodContaining(today, profile.cycleStartDay)
    val counted = countedExpenses(txs, profile, period)
    val previousPerformance = countedExpenses(txs, profile, previous(period)).sumOf { it.amount }
    val from = MonthKeys.dayStartMillis(period.start, zone)
    val to = MonthKeys.dayEndMillis(period.end, zone)
    val periodExpenses = txs.filter { it.accountId == profile.accountId && it.type == TransactionType.EXPENSE && it.timestamp in from..to }
    return CardStatus(
      accountId = profile.accountId,
      profile = profile,
      period = period,
      performance = counted.sumOf { it.amount },
      previousPerformance = previousPerformance,
      daysLeft = ChronoUnit.DAYS.between(today, period.end).toInt() + 1,
      benefits = benefits.filter { it.isActive && it.accountId == profile.accountId }
        .map { usage(it, periodExpenses, previousPerformance) }
    )
  }

  fun usage(benefit: CardBenefitEntity, periodExpenses: List<TransactionEntity>, previousPerformance: Long): BenefitUsage {
    val eligible = benefit.minPerformance <= 0 || previousPerformance >= benefit.minPerformance
    val matching = periodExpenses.filter { matches(benefit, it) }
    val raw = matching.sumOf { tx -> benefitFor(benefit, tx.amount) }
    val earned = if (!eligible) 0L else if (benefit.monthlyCap > 0) minOf(raw, benefit.monthlyCap) else raw
    return BenefitUsage(
      benefit = benefit,
      earned = earned,
      remainingCap = if (benefit.monthlyCap > 0) (benefit.monthlyCap - earned).coerceAtLeast(0) else null,
      matchingCount = matching.size,
      eligible = eligible
    )
  }

  fun matches(benefit: CardBenefitEntity, tx: TransactionEntity): Boolean = matches(benefit, tx.category, tx.memo, tx.amount)

  fun matches(benefit: CardBenefitEntity, category: String, memo: String, amount: Long): Boolean {
    if (amount < benefit.minAmount) return false
    val words = benefit.merchantKeywords.splitList()
    val categoryOk = benefit.category == null || category == benefit.category
    val keywordOk = words.isEmpty() || words.any { memo.contains(it, ignoreCase = true) }
    return categoryOk && keywordOk
  }

  /** Benefit for one payment of [amount], before the monthly cap. */
  fun benefitFor(benefit: CardBenefitEntity, amount: Long): Long = when (benefit.kind) {
    BenefitKind.PERCENT -> amount * benefit.value / 10_000
    BenefitKind.FIXED -> benefit.value
  }

  private fun String.splitList(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
