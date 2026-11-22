package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.CategoryBudgetEntity
import com.moneyflow.domain.MonthKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
  val inheritedFrom: YearMonth?
) {
  val isSet: Boolean get() = total > 0
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
