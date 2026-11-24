package com.moneyflow.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.ui.common.SelectedMonthStore
import com.moneyflow.ui.common.UserMessage
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class DateRangePreset(val label: String) {
  THIS_MONTH("선택한 달"),
  TODAY("오늘"),
  LAST_7_DAYS("최근 7일"),
  LAST_30_DAYS("최근 30일"),
  CUSTOM("직접 설정")
}

data class LedgerFilters(
  val type: TransactionType? = null,
  val category: String? = null,
  val query: String = "",
  val preset: DateRangePreset = DateRangePreset.THIS_MONTH,
  val customStart: LocalDate? = null,
  val customEnd: LocalDate? = null,
  val isCalendarView: Boolean = false,
  /** Selected day in calendar view (filters the list to that day). */
  val selectedDate: LocalDate? = null,
  val minAmount: Long? = null,
  val maxAmount: Long? = null,
  /** Transactions of any of these accounts (empty = all). */
  val accountIds: Set<Long> = emptySet(),
  /** Transactions having all these #tags (empty = any). */
  val tags: Set<String> = emptySet()
) {
  /** Amount / account / tag conditions (set in the detail filter). */
  val detailCount: Int
    get() = (if (minAmount != null || maxAmount != null) 1 else 0) + (if (accountIds.isNotEmpty()) 1 else 0) + (if (tags.isNotEmpty()) 1 else 0)

  val isAnyActive: Boolean
    get() = type != null || category != null || query.isNotBlank() || preset != DateRangePreset.THIS_MONTH || selectedDate != null || detailCount > 0
}

data class LedgerUiState(
  val month: YearMonth = YearMonth.now(),
  val monthLabel: String = "",
  val isCurrentMonth: Boolean = true,
  val filters: LedgerFilters = LedgerFilters(),
  val dateRangeLabel: String = "",
  /** Month totals (unfiltered). */
  val monthlyIncome: Long = 0,
  val monthlyExpense: Long = 0,
  /** All transactions of the selected month (unfiltered) — for the calendar and CSV export. */
  val monthTransactions: List<TransactionEntity> = emptyList(),
  /** Filtered list, newest first. */
  val transactions: List<TransactionEntity> = emptyList(),
  val filteredIncome: Long = 0,
  val filteredExpense: Long = 0,
  /** Expense / income per day of the selected month (calendar view). */
  val dailyExpense: Map<Int, Long> = emptyMap(),
  val dailyIncome: Map<Int, Long> = emptyMap(),
  val accounts: List<AccountEntity> = emptyList(),
  /** Every #tag used in the ledger, most used first. */
  val allTags: List<String> = emptyList(),
  val savedFilters: List<SavedFilter> = emptyList(),
  /** Today per the app clock (recomputed whenever the list updates). */
  val today: LocalDate = LocalDate.now(),
  val isLoading: Boolean = true
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
  private val ledger: LedgerRepository,
  private val monthStore: SelectedMonthStore,
  private val stats: MonthlyStatsCalculator,
  private val messages: UserMessageBus,
  private val clock: AppClock,
  private val settings: com.moneyflow.data.repository.SettingsRepository
) : ViewModel() {

  private val filters = MutableStateFlow(LedgerFilters())
  /** The search box text updates immediately; filtering uses a debounced copy. */
  private val _queryInput = MutableStateFlow("")
  val queryInput: StateFlow<String> = _queryInput

  init {
    viewModelScope.launch {
      _queryInput.debounce(250).collect { q -> filters.update { it.copy(query = q) } }
    }
  }

  /** Tags and saved filters change rarely: worked out apart from every filter tap / keystroke. */
  private val extras = combine(
    ledger.transactions.map { all -> com.moneyflow.util.Tags.summarize(all).sortedByDescending { it.transactions.size }.map { it.tag } },
    settings.settings.map { it.ledgerSavedFilters }.distinctUntilChanged().map { SavedFilters.decode(it) }
  ) { tags, saved -> tags to saved }

  val uiState: StateFlow<LedgerUiState> = combine(
    ledger.transactions, ledger.accounts, monthStore.month, filters, extras
  ) { all, accounts, month, f, (tags, saved) -> build(all, accounts, month, f).copy(allTags = tags, savedFilters = saved) }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

  private fun build(all: List<TransactionEntity>, accounts: List<AccountEntity>, month: YearMonth, f: LedgerFilters): LedgerUiState {
    val zone = clock.zone
    val today = clock.today()
    val monthTxs = stats.inMonth(all, month)
    val (base, label) = when (f.preset) {
      DateRangePreset.THIS_MONTH -> monthTxs to "${Formatters.formatMonth(month)} 전체"
      DateRangePreset.TODAY -> range(all, today, today) to "오늘"
      DateRangePreset.LAST_7_DAYS -> range(all, today.minusDays(6), today) to "최근 7일"
      DateRangePreset.LAST_30_DAYS -> range(all, today.minusDays(29), today) to "최근 30일"
      DateRangePreset.CUSTOM -> {
        val s = f.customStart ?: month.atDay(1)
        val e = f.customEnd ?: month.atEndOfMonth()
        range(all, minOf(s, e), maxOf(s, e)) to "${s.monthValue}.${s.dayOfMonth} ~ ${e.monthValue}.${e.dayOfMonth}"
      }
    }
    val filtered = base.filter { tx -> LedgerFilterMatch.matches(tx, f, zone) }
    return LedgerUiState(
      month = month,
      monthLabel = Formatters.formatMonth(month),
      isCurrentMonth = month == clock.currentMonth(),
      filters = f,
      dateRangeLabel = label,
      monthlyIncome = stats.totalIncome(monthTxs),
      monthlyExpense = stats.totalExpense(monthTxs),
      monthTransactions = monthTxs,
      transactions = filtered,
      filteredIncome = stats.totalIncome(filtered),
      filteredExpense = stats.totalExpense(filtered),
      dailyExpense = stats.dailyExpense(monthTxs),
      dailyIncome = monthTxs.filter { it.type == TransactionType.INCOME }
        .groupBy { MonthKeys.toLocalDate(it.timestamp, zone).dayOfMonth }
        .mapValues { (_, l) -> l.sumOf { it.amount } },
      accounts = accounts,
      today = today,
      isLoading = false
    )
  }

  private fun range(all: List<TransactionEntity>, from: LocalDate, to: LocalDate): List<TransactionEntity> {
    val s = MonthKeys.dayStartMillis(from, clock.zone)
    val e = MonthKeys.dayEndMillis(to, clock.zone)
    return all.filter { it.timestamp in s..e }
  }

  fun previousMonth() { monthStore.previous(); filters.update { it.copy(selectedDate = null) } }
  fun nextMonth() { monthStore.next(); filters.update { it.copy(selectedDate = null) } }
  fun goToCurrentMonth() { monthStore.resetToCurrent(); filters.update { it.copy(selectedDate = null) } }

  fun setQuery(q: String) { _queryInput.value = q }

  fun setType(type: TransactionType?) = filters.update {
    // A category belongs to one type: clear it when switching to another type.
    it.copy(type = type, category = if (type != null && it.type != type) null else it.category)
  }

  fun setCategory(category: String?) = filters.update { it.copy(category = category) }
  fun setPreset(preset: DateRangePreset) = filters.update {
    it.copy(preset = preset, customStart = if (preset == DateRangePreset.CUSTOM) it.customStart else null,
      customEnd = if (preset == DateRangePreset.CUSTOM) it.customEnd else null)
  }
  fun setCustomRange(start: LocalDate?, end: LocalDate?) =
    filters.update { it.copy(preset = DateRangePreset.CUSTOM, customStart = start, customEnd = end) }
  fun toggleCalendarView() = filters.update { it.copy(isCalendarView = !it.isCalendarView, selectedDate = null) }
  fun selectDate(date: LocalDate?) = filters.update { it.copy(selectedDate = if (it.selectedDate == date) null else date) }

  fun setDetail(minAmount: Long?, maxAmount: Long?, accountIds: Set<Long>, tags: Set<String>) = filters.update {
    it.copy(minAmount = minAmount, maxAmount = maxAmount, accountIds = accountIds, tags = tags)
  }

  fun applySaved(f: SavedFilter) {
    _queryInput.value = f.query
    filters.update { f.toFilters(it.isCalendarView) }
  }

  fun saveCurrent(name: String) = viewModelScope.launch {
    val list = SavedFilters.upsert(uiState.value.savedFilters, SavedFilter.from(name, filters.value.copy(query = _queryInput.value)))
    settings.setLedgerSavedFilters(SavedFilters.encode(list))
    messages.show("'${name.trim()}' 필터를 저장했어요.")
  }

  fun deleteSaved(f: SavedFilter) = viewModelScope.launch {
    settings.setLedgerSavedFilters(SavedFilters.encode(uiState.value.savedFilters.filter { it.name != f.name }))
  }

  fun resetFilters() {
    _queryInput.value = ""
    filters.update { LedgerFilters(isCalendarView = it.isCalendarView) }
  }

  /** Deletes with an undo snackbar. Completes with true if the row was deleted. */
  fun delete(tx: TransactionEntity): Deferred<Boolean> = viewModelScope.async {
    val removed = ledger.deleteTransaction(tx) ?: return@async false
    messages.show(
      UserMessage(
        text = "'${removed.memo.ifBlank { removed.category }}' 내역을 삭제했어요.",
        actionLabel = "실행 취소",
        onAction = { ledger.restoreTransaction(removed) }
      )
    )
    true
  }
}
