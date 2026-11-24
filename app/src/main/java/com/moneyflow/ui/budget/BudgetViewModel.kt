package com.moneyflow.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.CategoryBudgetProgress
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.MonthBudget
import com.moneyflow.data.repository.RecurringRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.ui.common.SelectedMonthStore
import com.moneyflow.ui.common.UserMessage
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class BudgetUiState(
  val month: YearMonth = YearMonth.now(),
  val monthLabel: String = "",
  val isCurrentMonth: Boolean = true,
  val budget: MonthBudget? = null,
  /** Expenses counted against the budget (excludes "예산 제외" transactions). */
  val budgetExpense: Long = 0,
  val budgetSpentRatio: Float = 0f,
  val remainingBudget: Long = 0,
  /** Remaining budget / remaining days (current month only, else null). */
  val dailyAllowance: Long? = null,
  val daysLeft: Int = 0,
  val categoryProgress: List<CategoryBudgetProgress> = emptyList(),
  val noSpendDays: Int = 0,
  val elapsedDays: Int = 0,
  val recurring: List<RecurringTransactionEntity> = emptyList(),
  val goals: List<SavingGoalEntity> = emptyList(),
  val accounts: List<AccountEntity> = emptyList(),
  val monthTransactions: List<TransactionEntity> = emptyList(),
  /** Hand-typed payments that repeat every month (suggested as 고정 거래). */
  val recurringSuggestions: List<com.moneyflow.domain.RecurringSuggestion> = emptyList(),
  val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
  private val ledger: LedgerRepository,
  private val budgets: BudgetRepository,
  private val recurringRepo: RecurringRepository,
  private val monthStore: SelectedMonthStore,
  private val stats: MonthlyStatsCalculator,
  private val messages: UserMessageBus,
  private val clock: AppClock,
  private val settings: com.moneyflow.data.repository.SettingsRepository
) : ViewModel() {
  private val detector = com.moneyflow.domain.RecurringDetector(clock.zone)

  val uiState: StateFlow<BudgetUiState> = combine(
    monthStore.month.flatMapLatest { m -> budgets.observeBudget(m) },
    ledger.transactions,
    ledger.goals,
    ledger.accounts,
    combine(recurringRepo.items, settings.settings) { r, s -> r to s.dismissedRecurringSuggestions }
  ) { budget, all, goals, accounts, (recurring, dismissed) ->
    val month = budget.month
    val today = clock.today()
    val monthTxs = stats.inMonth(all, month)
    val spent = stats.budgetExpense(monthTxs)
    val remaining = budget.total - spent
    val isCurrent = month == clock.currentMonth()
    val daysLeft = if (isCurrent) month.lengthOfMonth() - today.dayOfMonth + 1 else 0
    BudgetUiState(
      month = month,
      monthLabel = Formatters.formatMonth(month),
      isCurrentMonth = isCurrent,
      budget = budget,
      budgetExpense = spent,
      budgetSpentRatio = if (budget.total > 0) (spent.toFloat() / budget.total).coerceIn(0f, 1f) else 0f,
      remainingBudget = remaining,
      dailyAllowance = if (isCurrent && budget.isSet && daysLeft > 0) (remaining.coerceAtLeast(0) / daysLeft) else null,
      daysLeft = daysLeft,
      categoryProgress = stats.categoryBudgetProgress(monthTxs, budget.categoryLimits),
      noSpendDays = stats.noSpendDays(monthTxs, month, today),
      elapsedDays = stats.elapsedDays(month, today),
      recurring = recurring,
      goals = goals,
      accounts = accounts,
      monthTransactions = monthTxs,
      recurringSuggestions = detector.detect(all, recurring, dismissed, today).filter { s -> accounts.any { it.id == s.accountId } },
      isLoading = false
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

  fun previousMonth() = monthStore.previous()
  fun nextMonth() = monthStore.next()
  fun goToCurrentMonth() = monthStore.resetToCurrent()

  // ---- Budget ----
  fun saveBudget(total: Long, categoryLimits: Map<String, Long>) = viewModelScope.launch {
    budgets.saveBudget(uiState.value.month, total, categoryLimits)
    messages.show("${Formatters.formatMonth(uiState.value.month)} 예산을 저장했어요.")
  }

  // ---- Recurring ----
  /** Registers a detected repeating payment as a 고정 거래 (from next month if this month is paid). */
  fun acceptSuggestion(s: com.moneyflow.domain.RecurringSuggestion) = viewModelScope.launch {
    val account = uiState.value.accounts.firstOrNull { it.id == s.accountId } ?: return@launch
    val start = if (s.paidThisMonth) clock.currentMonth().plusMonths(1) else clock.currentMonth()
    recurringRepo.add(
      RecurringTransactionEntity(
        title = s.title, type = s.type, amount = s.amount, category = s.category, dayOfMonth = s.dayOfMonth,
        accountId = account.id, accountName = account.name, memo = s.title, paymentMethod = s.paymentMethod,
        startMonth = com.moneyflow.domain.MonthKeys.of(start)
      )
    )
    messages.show("'${s.title}'을(를) 매달 ${s.dayOfMonth}일 고정 거래로 등록했어요." + if (s.paidThisMonth) " 다음 달부터 기록돼요." else "")
  }

  fun dismissSuggestion(s: com.moneyflow.domain.RecurringSuggestion) = viewModelScope.launch {
    settings.dismissRecurringSuggestion(s.key)
  }

  fun addRecurring(item: RecurringTransactionEntity) = viewModelScope.launch {
    recurringRepo.add(item)
    messages.show("'${item.title}' 매월 ${item.dayOfMonth}일 고정 거래를 등록했어요.")
  }

  fun updateRecurring(item: RecurringTransactionEntity) = viewModelScope.launch {
    recurringRepo.update(item)
    messages.show("'${item.title}' 고정 거래를 수정했어요.")
  }

  fun setRecurringActive(item: RecurringTransactionEntity, active: Boolean) = viewModelScope.launch {
    recurringRepo.setActive(item.id, active)
    messages.show(if (active) "'${item.title}'을(를) 다시 시작했어요." else "'${item.title}'을(를) 일시 중지했어요.")
  }

  fun executeRecurringNow(item: RecurringTransactionEntity) = viewModelScope.launch {
    val done = recurringRepo.executeNow(item)
    messages.show(if (done) "'${item.title}' ${Formatters.formatCurrency(item.amount)}을(를) 기록했어요." else "이번 달 '${item.title}'은(는) 이미 기록되어 있어요.")
  }

  fun deleteRecurring(item: RecurringTransactionEntity) = viewModelScope.launch {
    recurringRepo.delete(item)
    messages.show("'${item.title}' 고정 거래를 삭제했어요. 이미 기록된 거래는 그대로 남아 있어요.")
  }

  // ---- Goals ----
  fun addGoal(goal: SavingGoalEntity) = viewModelScope.launch {
    ledger.addGoal(goal)
    messages.show("'${goal.title}' 목표를 만들었어요.")
  }

  fun updateGoal(goal: SavingGoalEntity) = viewModelScope.launch {
    ledger.updateGoal(goal)
    messages.show("'${goal.title}' 목표를 수정했어요.")
  }

  fun deleteGoal(goal: SavingGoalEntity) = viewModelScope.launch {
    ledger.deleteGoal(goal)
    messages.show("'${goal.title}' 목표를 삭제했어요. 저축 이체 내역은 가계부에 남아 있어요.")
  }

  fun depositToGoal(goal: SavingGoalEntity, fromAccountId: Long, amount: Long) = viewModelScope.launch {
    runCatching { ledger.depositToGoal(goal, fromAccountId, amount) }
      .onSuccess { id ->
        messages.show(
          UserMessage(
            text = "'${goal.title}'에 ${Formatters.formatCurrency(amount)}을 저축했어요.",
            actionLabel = "실행 취소",
            onAction = { ledger.getTransaction(id)?.let { ledger.deleteTransaction(it) } }
          )
        )
      }
      .onFailure { messages.show(it.message ?: "저축하지 못했어요.") }
  }
}
