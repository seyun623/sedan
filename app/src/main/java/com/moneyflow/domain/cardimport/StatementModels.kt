package com.moneyflow.domain.cardimport

import java.time.LocalDate
import java.time.LocalTime

/** One line of a card statement read from the card company's screen. */
data class StatementItem(
  val date: LocalDate,
  val time: LocalTime?,
  val merchant: String,
  val amount: Long,
  val isCancel: Boolean = false,
  val installmentMonths: Int? = null,
  /** A deposit (bank statement files); recorded as income. */
  val isIncome: Boolean = false
)

/** A spending-goal ("실적") figure shown on the card company's screen. */
data class PerformanceInfo(val label: String, val amount: Long)

data class StatementExtraction(
  val items: List<StatementItem>,
  val performance: List<PerformanceInfo>,
  /** "기기에서 읽기" or the AI provider name. */
  val readBy: String,
  /** Benefits found on a benefit screen (AI reading only); accountId is set when saving. */
  val benefits: List<com.moneyflow.data.model.CardBenefitEntity> = emptyList()
) {
  val isEmpty: Boolean get() = items.isEmpty() && performance.isEmpty() && benefits.isEmpty()
}
