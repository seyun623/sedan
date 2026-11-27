package com.moneyflow.domain

import com.moneyflow.data.model.CategoryBudgetProgress
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.model.spending
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Pure monthly statistics. Transfers never count as income/expense.
 * "Budget" figures exclude transactions flagged [TransactionEntity.isExcludedFromBudget].
 */
class MonthlyStatsCalculator(private val zone: ZoneId) {

  fun inMonth(all: List<TransactionEntity>, month: YearMonth): List<TransactionEntity> {
    val start = MonthKeys.startMillis(month, zone)
    val end = MonthKeys.endMillis(month, zone)
    return all.filter { it.timestamp in start..end }
  }

  fun totalIncome(txs: List<TransactionEntity>): Long =
    txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

  /** Spending: purchases minus refunds. */
  fun totalExpense(txs: List<TransactionEntity>): Long = txs.sumOf { it.spending }.coerceAtLeast(0)

  fun budgetExpense(txs: List<TransactionEntity>): Long =
    txs.filter { !it.isExcludedFromBudget }.sumOf { it.spending }.coerceAtLeast(0)

  fun categoryBreakdown(txs: List<TransactionEntity>): List<CategorySpend> {
    // Refunds lower their category; a category with nothing left is not shown.
    val byCategory = txs.filter { it.type.isSpending }.groupBy { it.category }
      .mapValues { (_, l) -> l.sumOf { it.spending } }.filterValues { it > 0 }
    val total = byCategory.values.sum()
    return byCategory
      .map { (cat, amount) ->
        CategorySpend(
          category = cat,
          amount = amount,
          percentage = if (total > 0) amount.toFloat() / total else 0f,
          colorHex = FinanceCategories.getColorForCategory(cat)
        )
      }
      .sortedByDescending { it.amount }
  }

  fun categoryBudgetProgress(
    txs: List<TransactionEntity>,
    limits: Map<String, Long>
  ): List<CategoryBudgetProgress> {
    val spentByCategory = txs
      .filter { it.type.isSpending && !it.isExcludedFromBudget }
      .groupBy { it.category }
      .mapValues { (_, list) -> list.sumOf { it.spending }.coerceAtLeast(0) }
    return FinanceCategories.expenseCategories.map { (cat, color) ->
      val limit = limits[cat] ?: 0L
      val spent = spentByCategory[cat] ?: 0L
      CategoryBudgetProgress(
        category = cat,
        budgetLimit = limit,
        spentAmount = spent,
        percentage = if (limit > 0) spent.toFloat() / limit else 0f,
        remainingAmount = limit - spent,
        isOverBudget = limit > 0 && spent > limit,
        colorHex = color
      )
    }.sortedWith(
      compareByDescending<CategoryBudgetProgress> { it.isOverBudget }.thenByDescending { it.spentAmount }
    )
  }

  /** Expense totals by weekday, Monday first. */
  fun weekdaySpend(txs: List<TransactionEntity>): List<Pair<String, Long>> {
    val labels = listOf("월", "화", "수", "목", "금", "토", "일")
    val sums = LongArray(7)
    txs.filter { it.type.isSpending }.forEach {
      val dow = MonthKeys.toLocalDate(it.timestamp, zone).dayOfWeek
      sums[dow.value - DayOfWeek.MONDAY.value] += it.spending
    }
    return labels.mapIndexed { i, l -> l to sums[i].coerceAtLeast(0) }
  }

  /** Real expense totals for the [count] months ending at [endMonth]. No placeholder values. */
  fun monthlyTrends(
    all: List<TransactionEntity>,
    endMonth: YearMonth,
    currentMonth: YearMonth,
    count: Int = 6
  ): List<MonthlyTrendItem> {
    val items = (count - 1 downTo 0).map { offset ->
      val m = endMonth.minusMonths(offset.toLong())
      val monthTxs = inMonth(all, m)
      val expenses = monthTxs.filter { it.type == TransactionType.EXPENSE }
      MonthlyTrendItem(
        monthKey = MonthKeys.of(m),
        monthLabel = "${m.monthValue}월",
        fullDisplay = "${m.year}년 ${m.monthValue}월",
        totalExpense = totalExpense(monthTxs),
        transactionCount = expenses.size,
        isCurrentMonth = m == currentMonth
      )
    }
    return items.mapIndexed { i, item ->
      val prev = items.getOrNull(i - 1)?.totalExpense
      item.copy(
        changeVsPrevPct = if (prev != null && prev > 0) (item.totalExpense - prev).toFloat() / prev * 100f else null
      )
    }
  }

  /** Average over months that actually have spending (0 if none). */
  fun averageMonthlyExpense(trends: List<MonthlyTrendItem>): Long {
    val withData = trends.filter { it.transactionCount > 0 }
    return if (withData.isEmpty()) 0L else withData.sumOf { it.totalExpense } / withData.size
  }

  fun weeklyProgress(txs: List<TransactionEntity>, month: YearMonth): List<WeeklyProgressItem> {
    val maxDays = month.lengthOfMonth()
    val expenses = txs.filter { it.type.isSpending }
      .map { MonthKeys.toLocalDate(it.timestamp, zone).dayOfMonth to it.spending }
    return (1..(maxDays + 6) / 7).map { week ->
      val startDay = (week - 1) * 7 + 1
      val endDay = (week * 7).coerceAtMost(maxDays)
      val inWeek = expenses.filter { it.first in startDay..endDay }
      WeeklyProgressItem(
        weekLabel = "${week}주차",
        totalExpense = inWeek.sumOf { it.second }.coerceAtLeast(0),
        transactionCount = inWeek.count { it.second > 0 },
        dateRangeLabel = "${month.monthValue}.$startDay ~ ${month.monthValue}.$endDay"
      )
    }
  }

  /** Number of days without any expense, up to today for the current month. */
  fun noSpendDays(txs: List<TransactionEntity>, month: YearMonth, today: LocalDate): Int {
    val lastDay = when {
      month.isAfter(YearMonth.from(today)) -> 0
      month == YearMonth.from(today) -> today.dayOfMonth
      else -> month.lengthOfMonth()
    }
    val spendDays = txs.filter { it.type == TransactionType.EXPENSE }
      .map { MonthKeys.toLocalDate(it.timestamp, zone).dayOfMonth }.toSet()
    return (1..lastDay).count { it !in spendDays }
  }

  /** Expense per day-of-month (for the calendar view). */
  fun dailyExpense(txs: List<TransactionEntity>): Map<Int, Long> =
    txs.filter { it.type.isSpending }
      .groupBy { MonthKeys.toLocalDate(it.timestamp, zone).dayOfMonth }
      .mapValues { (_, l) -> l.sumOf { it.spending } }
      .filterValues { it > 0 }

  /** Days in the month used for daily averages: elapsed days for the current month. */
  fun elapsedDays(month: YearMonth, today: LocalDate): Int = when {
    month == YearMonth.from(today) -> today.dayOfMonth
    month.isAfter(YearMonth.from(today)) -> 1
    else -> month.lengthOfMonth()
  }
}
