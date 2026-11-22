package com.moneyflow.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.CategorySpend
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.domain.MonthlyTrendItem
import com.moneyflow.domain.WeeklyProgressItem
import com.moneyflow.ui.common.SelectedMonthStore
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class AnalyticsUiState(
  val month: YearMonth = YearMonth.now(),
  val monthLabel: String = "",
  val isCurrentMonth: Boolean = true,
  val monthlyIncome: Long = 0,
  val monthlyExpense: Long = 0,
  /** Expense / elapsed days (days so far for the current month, whole month otherwise). */
  val dailyAverageExpense: Long = 0,
  val elapsedDays: Int = 1,
  val categoryBreakdown: List<CategorySpend> = emptyList(),
  /** Monday..Sunday expense totals. */
  val weekdaySpend: List<Pair<String, Long>> = emptyList(),
  /** 6 months ending at the selected month, oldest first; real data only. */
  val monthlyTrends: List<MonthlyTrendItem> = emptyList(),
  /** Average over months in [monthlyTrends] that have any spending. */
  val averageMonthlyExpense: Long = 0,
  val weeklyProgress: List<WeeklyProgressItem> = emptyList(),
  /** Unfiltered expenses of the month, largest first (for "top spending"). */
  val topExpenses: List<TransactionEntity> = emptyList(),
  /** Unfiltered transactions of the month. */
  val monthTransactions: List<TransactionEntity> = emptyList(),
  val isLoading: Boolean = true
)

/** Analytics never uses the ledger's search/filter state. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
  ledger: LedgerRepository,
  private val monthStore: SelectedMonthStore,
  private val stats: MonthlyStatsCalculator,
  private val clock: AppClock
) : ViewModel() {

  val uiState: StateFlow<AnalyticsUiState> = combine(ledger.transactions, monthStore.month) { all, month ->
    val monthTxs = stats.inMonth(all, month)
    val expense = stats.totalExpense(monthTxs)
    val elapsed = stats.elapsedDays(month, clock.today())
    val trends = stats.monthlyTrends(all, month, clock.currentMonth())
    AnalyticsUiState(
      month = month,
      monthLabel = Formatters.formatMonth(month),
      isCurrentMonth = month == clock.currentMonth(),
      monthlyIncome = stats.totalIncome(monthTxs),
      monthlyExpense = expense,
      dailyAverageExpense = expense / elapsed.coerceAtLeast(1),
      elapsedDays = elapsed,
      categoryBreakdown = stats.categoryBreakdown(monthTxs),
      weekdaySpend = stats.weekdaySpend(monthTxs),
      monthlyTrends = trends,
      averageMonthlyExpense = stats.averageMonthlyExpense(trends),
      weeklyProgress = stats.weeklyProgress(monthTxs, month),
      topExpenses = monthTxs.filter { it.type == TransactionType.EXPENSE }.sortedByDescending { it.amount },
      monthTransactions = monthTxs,
      isLoading = false
    )
  }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

  fun previousMonth() = monthStore.previous()
  fun nextMonth() = monthStore.next()
  fun goToCurrentMonth() = monthStore.resetToCurrent()
}
