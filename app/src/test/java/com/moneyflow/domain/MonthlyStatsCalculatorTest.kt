package com.moneyflow.domain

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthlyStatsCalculatorTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private val calc = MonthlyStatsCalculator(zone)

  private fun tx(type: TransactionType, amount: Long, at: LocalDateTime, category: String = "식비", excluded: Boolean = false) =
    TransactionEntity(type = type, amount = amount, category = category, accountId = 1, timestamp = at.atZone(zone).toInstant().toEpochMilli(), memo = "", isExcludedFromBudget = excluded)

  @Test fun monthBoundariesAreInclusiveAndZoned() {
    val nov = YearMonth.of(2026, 11)
    val txs = listOf(
      tx(TransactionType.EXPENSE, 1, LocalDateTime.of(2026, 10, 31, 23, 59, 59)),
      tx(TransactionType.EXPENSE, 2, LocalDateTime.of(2026, 11, 1, 0, 0)),
      tx(TransactionType.EXPENSE, 3, LocalDateTime.of(2026, 11, 30, 23, 59, 59)),
      tx(TransactionType.EXPENSE, 4, LocalDateTime.of(2026, 12, 1, 0, 0))
    )
    assertEquals(listOf(2L, 3L), calc.inMonth(txs, nov).map { it.amount })
  }

  @Test fun transfersAreNeitherIncomeNorExpense() {
    val at = LocalDateTime.of(2026, 11, 5, 10, 0)
    val txs = listOf(tx(TransactionType.EXPENSE, 100, at), tx(TransactionType.INCOME, 50, at), tx(TransactionType.TRANSFER, 1000, at))
    assertEquals(100L, calc.totalExpense(txs))
    assertEquals(50L, calc.totalIncome(txs))
  }

  @Test fun excludedTransactionsDoNotCountTowardBudgetOrCategoryLimits() {
    val at = LocalDateTime.of(2026, 11, 5, 10, 0)
    val txs = listOf(tx(TransactionType.EXPENSE, 100, at), tx(TransactionType.EXPENSE, 900, at, excluded = true))
    assertEquals(100L, calc.budgetExpense(txs))
    val food = calc.categoryBudgetProgress(txs, mapOf("식비" to 500L)).first { it.category == "식비" }
    assertEquals(100L, food.spentAmount)
  }

  @Test fun trendsUseRealDataAndNullChangeWhenPreviousIsZero() {
    val txs = listOf(tx(TransactionType.EXPENSE, 1000, LocalDateTime.of(2026, 11, 3, 10, 0)))
    val trends = calc.monthlyTrends(txs, YearMonth.of(2026, 11), YearMonth.of(2026, 11))
    assertEquals(6, trends.size)
    assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 1000L), trends.map { it.totalExpense })
    assertNull(trends.last().changeVsPrevPct)
    assertEquals(1000L, calc.averageMonthlyExpense(trends))
  }

  @Test fun noSpendDaysCountsOnlyElapsedDaysInCurrentMonth() {
    val txs = listOf(tx(TransactionType.EXPENSE, 10, LocalDateTime.of(2026, 11, 2, 10, 0)))
    assertEquals(4, calc.noSpendDays(txs, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 5)))
    assertEquals(0, calc.noSpendDays(txs, YearMonth.of(2026, 12), LocalDate.of(2026, 11, 5)))
  }

  @Test fun weekdaySpendStartsMonday() {
    // 2026-11-23 is a Monday
    val txs = listOf(tx(TransactionType.EXPENSE, 7, LocalDateTime.of(2026, 11, 23, 10, 0)))
    assertEquals("월" to 7L, calc.weekdaySpend(txs).first())
  }
}
