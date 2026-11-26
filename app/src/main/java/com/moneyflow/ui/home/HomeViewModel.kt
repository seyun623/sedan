package com.moneyflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.MonthBudget
import com.moneyflow.data.repository.SampleDataSeeder
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.CategorySpend
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.domain.MonthlyTrendItem
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.moneyflow.domain.todayFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class HomeUiState(
  val isLoading: Boolean = true,
  val accounts: List<AccountEntity> = emptyList(),
  val totalAssets: Long = 0,
  val totalDebts: Long = 0,
  val netWorth: Long = 0,
  /** Always the real current month (home does not follow the ledger's month picker). */
  val month: YearMonth = YearMonth.now(),
  val monthLabel: String = "",
  val monthlyIncome: Long = 0,
  val monthlyExpense: Long = 0,
  val budget: MonthBudget? = null,
  val budgetExpense: Long = 0,
  /** 0..1, 0 when no budget is set. */
  val budgetSpentRatio: Float = 0f,
  val remainingBudget: Long = 0,
  val categoryBreakdown: List<CategorySpend> = emptyList(),
  /** Last 6 months of real spending, oldest first. */
  val monthlyTrends: List<MonthlyTrendItem> = emptyList(),
  /** Spending change vs last month in %, null if last month had no spending. */
  val expenseChangeVsLastMonthPct: Float? = null,
  val recentTransactions: List<TransactionEntity> = emptyList(),
  val goals: List<SavingGoalEntity> = emptyList(),
  val isLockEnabled: Boolean = false,
  /** 실적 status of cards that have 실적 settings. */
  val cardStatuses: List<com.moneyflow.domain.card.CardStatus> = emptyList(),
  /** "어디서 어떤 카드?" rows from registered card benefits. */
  val cardGuide: List<com.moneyflow.domain.card.CardGuideRow> = emptyList(),
  /** 포인트 모아보기 (2.20): saved point balances of existing card accounts. */
  val cardPoints: Map<Long, com.moneyflow.domain.card.CardPoint> = emptyMap()
) {
  val hasNoAccounts: Boolean get() = !isLoading && accounts.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
  private val ledger: LedgerRepository,
  private val budgets: BudgetRepository,
  private val settings: SettingsRepository,
  private val cards: com.moneyflow.data.repository.CardRepository,
  private val seeder: SampleDataSeeder,
  private val stats: MonthlyStatsCalculator,
  private val messages: UserMessageBus,
  private val clock: AppClock
) : ViewModel() {


  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<HomeUiState> = clock.todayFlow()
    .map { java.time.YearMonth.from(it) }
    .distinctUntilChanged()
    .flatMapLatest { month -> state(month) }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

  private fun state(month: java.time.YearMonth) = combine(
    ledger.accounts,
    ledger.transactions,
    ledger.goals,
    combine(budgets.observeBudget(month), settings.settings) { b, st -> budgets.withCarry(b, st.budgetCarryOver, clock.zone) },
    combine(settings.settings, cards.statuses, cards.guide) { s, c, g -> Triple(s, c, g) }
  ) { accounts, txs, goals, budget, (s, cardStatuses, cardGuide) ->
    val assets = accounts.filter { !it.isDebt }.sumOf { it.balance }
    val debts = accounts.filter { it.isDebt }.sumOf { it.balance }
    val monthTxs = stats.inMonth(txs, month)
    val budgetExpense = stats.budgetExpense(monthTxs)
    val trends = stats.monthlyTrends(txs, month, month)
    HomeUiState(
      isLoading = false,
      accounts = accounts,
      totalAssets = assets,
      totalDebts = debts,
      netWorth = assets - debts,
      month = month,
      monthLabel = Formatters.formatMonth(month),
      monthlyIncome = stats.totalIncome(monthTxs),
      monthlyExpense = stats.totalExpense(monthTxs),
      budget = budget,
      budgetExpense = budgetExpense,
      budgetSpentRatio = if (budget.total > 0) (budgetExpense.toFloat() / budget.total).coerceIn(0f, 1f) else 0f,
      remainingBudget = budget.total - budgetExpense,
      categoryBreakdown = stats.categoryBreakdown(monthTxs),
      monthlyTrends = trends,
      expenseChangeVsLastMonthPct = trends.lastOrNull()?.changeVsPrevPct,
      // Future installment parts / scheduled items are not "recent".
      recentTransactions = clock.nowMillis().let { now -> txs.filter { it.timestamp <= now }.take(RECENT_COUNT) },
      goals = goals,
      isLockEnabled = s.isLockEnabled,
      cardStatuses = cardStatuses.filter { it.target > 0 },
      cardGuide = cardGuide,
      cardPoints = s.cardPoints.filterKeys { id -> accounts.any { it.id == id } }.filterValues { it.amount > 0 }
    )
  }

  fun addAccount(
    name: String, bankName: String, type: AccountType, accountNumber: String,
    balance: Long, colorHex: Long, isDebt: Boolean, paymentDay: Int = 0
  ) = viewModelScope.launch {
    ledger.addAccount(
      AccountEntity(
        name = name.trim(), bankName = bankName.trim(), accountType = type,
        accountNumber = accountNumber.trim(), balance = balance, colorHex = colorHex, isDebt = isDebt, paymentDay = paymentDay
      )
    )
    messages.show("'${name.trim()}' 계좌를 추가했어요.")
  }

  fun transfer(from: AccountEntity, to: AccountEntity, amount: Long, memo: String) = viewModelScope.launch {
    runCatching { ledger.transfer(from, to, amount, memo) }
      .onSuccess { messages.show("${to.name}(으)로 ${Formatters.formatCurrency(amount)} 이체를 기록했어요.") }
      .onFailure { messages.show(it.message ?: "이체를 기록하지 못했어요.") }
  }

  fun loadSampleData() = viewModelScope.launch {
    if (seeder.canSeed()) {
      seeder.seed()
      messages.show("샘플 데이터를 불러왔어요. 설정에서 언제든 직접 수정할 수 있어요.")
    }
  }

  private companion object {
    const val RECENT_COUNT = 10
  }
}
