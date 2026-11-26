package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.CategoryBudgetEntity
import com.moneyflow.domain.MonthKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A month's budget. If the month has no budget of its own, the most recent earlier budget
 * is carried forward ([inheritedFrom] names that month). [total] == 0 means "not set".
 */
data class MonthBudget(
  val month: YearMonth,
  val total: Long,
  val categoryLimits: Map<String, Long>,
  val inheritedFrom: YearMonth?,
  /** 예산 이월 already included in [total] (see [BudgetRepository.withCarry]). */
  val carry: Long = 0
) {
  val isSet: Boolean get() = total > 0
}

/**
 * 예산 이월 (2.18): what last month left over is added to this month's budget; overspending is
 * taken off. Only one month back (no running total), and only when both months have a budget.
 */
object BudgetCarry {
  fun amount(previous: MonthBudget, previousSpent: Long, current: MonthBudget): Long =
    if (!previous.isSet || !current.isSet) 0L else previous.total - previousSpent
}

@Singleton
class BudgetRepository @Inject constructor(private val db: AppDatabase) {
  private val dao = db.budgetDao()

  fun observeBudget(month: YearMonth): Flow<MonthBudget> =
    combine(dao.observeAllBudgets(), dao.observeAllCategoryBudgets()) { budgets, categories ->
      val key = MonthKeys.of(month)
      val source = budgets.filter { it.monthKey <= key }.maxByOrNull { it.monthKey }
      if (source == null) {
        MonthBudget(month, 0L, emptyMap(), null)
      } else {
        MonthBudget(
          month = month,
          total = source.totalBudget,
          categoryLimits = categories.filter { it.monthKey == source.monthKey && it.limitAmount > 0 }
            .associate { it.category to it.limitAmount },
          inheritedFrom = if (source.monthKey == key) null else MonthKeys.parse(source.monthKey)
        )
      }
    }

  /**
   * The budget with last month's leftover added when 예산 이월 is on (home, widget, alerts).
   * The budget screen does the same from its own flows.
   */
  suspend fun withCarry(budget: MonthBudget, carryOn: Boolean, zone: java.time.ZoneId): MonthBudget {
    if (!carryOn || !budget.isSet) return budget
    val prevMonth = budget.month.minusMonths(1)
    val previous = observeBudget(prevMonth).first()
    if (!previous.isSet) return budget
    val txs = db.transactionDao().getBetween(MonthKeys.startMillis(prevMonth, zone), MonthKeys.endMillis(prevMonth, zone))
    val carry = BudgetCarry.amount(previous, com.moneyflow.domain.MonthlyStatsCalculator(zone).budgetExpense(txs), budget)
    return budget.copy(total = (budget.total + carry).coerceAtLeast(0), carry = carry)
  }

  suspend fun saveBudget(month: YearMonth, total: Long, categoryLimits: Map<String, Long>) {
    val key = MonthKeys.of(month)
    db.withTransaction {
      dao.upsertBudget(key, total.coerceAtLeast(0))
      dao.clearCategoryBudgets(key)
      dao.upsertCategoryBudgets(
        categoryLimits.filterValues { it > 0 }.map { (cat, limit) -> CategoryBudgetEntity(key, cat, limit) }
      )
    }
  }
}
