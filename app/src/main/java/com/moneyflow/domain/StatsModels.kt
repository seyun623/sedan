package com.moneyflow.domain

data class CategorySpend(
  val category: String,
  val amount: Long,
  val percentage: Float,
  val colorHex: Long
)

data class MonthlyTrendItem(
  val monthKey: String,
  val monthLabel: String,
  val fullDisplay: String,
  val totalExpense: Long,
  val transactionCount: Int,
  val isCurrentMonth: Boolean,
  /** null when there is no previous month or the previous month had no spending. */
  val changeVsPrevPct: Float? = null
)

data class WeeklyProgressItem(
  val weekLabel: String,
  val totalExpense: Long,
  val transactionCount: Int,
  val dateRangeLabel: String
)
