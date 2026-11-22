package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AccountEntity
import com.example.data.model.AccountType
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryBudgetProgress
import com.example.data.model.FinanceCategories
import com.example.data.model.RecurringTransactionEntity
import com.example.data.model.SavingGoalEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.repository.FinanceRepository
import com.example.util.Formatters
import com.example.util.PaymentNotificationParser
import com.example.util.ParsedTransactionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Calendar

private const val TAG = "FinanceViewModel"

data class AiCategoryRecommendation(
  val keyword: String,
  val recommendedCategory: String,
  val alternativeCategory: String?,
  val suggestedPaymentMethod: String,
  val confidence: Int,
  val reason: String
)

sealed interface CategorySuggestionUiState {
  data object Idle : CategorySuggestionUiState
  data class Loading(val keyword: String) : CategorySuggestionUiState
  data class Success(val recommendation: AiCategoryRecommendation) : CategorySuggestionUiState
  data class Error(val message: String) : CategorySuggestionUiState
}

data class CategorySpend(
  val category: String,
  val amount: Long,
  val percentage: Float,
  val colorHex: Long
)

data class DaySpending(
  val dayOfMonth: Int,
  val totalExpense: Long,
  val transactionCount: Int
)

data class CoreData(
  val accounts: List<AccountEntity>,
  val transactions: List<TransactionEntity>,
  val savingGoals: List<SavingGoalEntity>,
  val recurring: List<RecurringTransactionEntity>
)

data class BudgetConfig(
  val calendar: Calendar,
  val totalBudget: Long,
  val categoryBudgets: Map<String, Long>
)

enum class DateRangePreset(val label: String) {
  THIS_MONTH("이번 달"),
  TODAY("오늘"),
  LAST_7_DAYS("최근 7일"),
  LAST_30_DAYS("최근 30일"),
  CUSTOM("직접 선택")
}

data class FilterState(
  val filterType: TransactionType?,
  val selectedCategory: String?,
  val searchQuery: String,
  val isCalendarView: Boolean,
  val selectedCalendarDate: Long?,
  val dateRangePreset: DateRangePreset = DateRangePreset.THIS_MONTH,
  val customStartDate: Long? = null,
  val customEndDate: Long? = null
)

data class SecurityState(
  val isBiometricEnabled: Boolean,
  val isAppLocked: Boolean,
  val pinCode: String
)

data class MonthlyTrendItem(
  val monthKey: String,
  val monthLabel: String,
  val fullDisplay: String,
  val totalExpense: Long,
  val transactionCount: Int,
  val isCurrentMonth: Boolean,
  val changeVsPrevPct: Float? = null
)

data class WeeklyProgressItem(
  val weekLabel: String,
  val totalExpense: Long,
  val transactionCount: Int,
  val dateRangeLabel: String
)

data class FinanceUiState(
  val totalAssets: Long = 0,
  val totalDebts: Long = 0,
  val netWorth: Long = 0,
  val monthlyIncome: Long = 0,
  val monthlyExpense: Long = 0,
  val currentBudget: Long = 1800000,
  val budgetSpentPercentage: Float = 0f,
  val remainingBudget: Long = 0,
  val categoryBreakdown: List<CategorySpend> = emptyList(),
  val categoryBudgets: Map<String, Long> = emptyMap(),
  val categoryBudgetProgressList: List<CategoryBudgetProgress> = emptyList(),
  val recurringTransactions: List<RecurringTransactionEntity> = emptyList(),
  val weeklySpendData: List<Pair<String, Long>> = emptyList(), // Day label -> amount
  val monthlySpendingTrends: List<MonthlyTrendItem> = emptyList(),
  val weeklySpendingProgress: List<WeeklyProgressItem> = emptyList(),
  val sixMonthAverageExpense: Long = 0,
  val noSpendDaysCount: Int = 0,
  val accounts: List<AccountEntity> = emptyList(),
  val filteredTransactions: List<TransactionEntity> = emptyList(),
  val allMonthTransactions: List<TransactionEntity> = emptyList(),
  val recentTransactions: List<TransactionEntity> = emptyList(),
  val savingGoals: List<SavingGoalEntity> = emptyList(),
  val currentMonthKey: String = "2026-10",
  val currentMonthDisplay: String = "2026년 10월",
  val filterType: TransactionType? = null,
  val selectedCategory: String? = null,
  val searchQuery: String = "",
  val isCalendarView: Boolean = false,
  val selectedCalendarDate: Long? = null,
  val dateRangePreset: DateRangePreset = DateRangePreset.THIS_MONTH,
  val customStartDate: Long? = null,
  val customEndDate: Long? = null,
  val dateRangeDisplay: String = "",
  val filteredTotalExpense: Long = 0,
  val filteredTotalIncome: Long = 0,
  val isAnyFilterActive: Boolean = false,
  val isBiometricEnabled: Boolean = true,
  val isAppLocked: Boolean = true,
  val pinCode: String = "1234"
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
  private val repository: FinanceRepository

  private val _currentCalendar = MutableStateFlow(Calendar.getInstance())
  val currentCalendar: StateFlow<Calendar> = _currentCalendar.asStateFlow()

  private val _filterType = MutableStateFlow<TransactionType?>(null)
  val filterType = _filterType.asStateFlow()

  private val _selectedCategory = MutableStateFlow<String?>(null)
  val selectedCategory = _selectedCategory.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  private val _isCalendarView = MutableStateFlow(false)
  val isCalendarView = _isCalendarView.asStateFlow()

  private val _selectedCalendarDate = MutableStateFlow<Long?>(null)
  val selectedCalendarDate = _selectedCalendarDate.asStateFlow()

  private val _dateRangePreset = MutableStateFlow(DateRangePreset.THIS_MONTH)
  val dateRangePreset = _dateRangePreset.asStateFlow()

  private val _customStartDate = MutableStateFlow<Long?>(null)
  val customStartDate = _customStartDate.asStateFlow()

  private val _customEndDate = MutableStateFlow<Long?>(null)
  val customEndDate = _customEndDate.asStateFlow()

  private val _transferSuccessMessage = MutableStateFlow<String?>(null)
  val transferSuccessMessage = _transferSuccessMessage.asStateFlow()

  private val _totalBudget = MutableStateFlow(1800000L)
  private val _categoryBudgets = MutableStateFlow<Map<String, Long>>(
    mapOf(
      "식비" to 500000L,
      "카페/간식" to 150000L,
      "쇼핑" to 300000L,
      "교통" to 150000L,
      "주거/통신" to 200000L,
      "생활/마트" to 200000L,
      "문화/여가" to 150000L,
      "의료/건강" to 100000L
    )
  )

  private val _categorySuggestionState =
    MutableStateFlow<CategorySuggestionUiState>(CategorySuggestionUiState.Idle)
  val categorySuggestionState: StateFlow<CategorySuggestionUiState> =
    _categorySuggestionState.asStateFlow()

  private val _isBiometricEnabled = MutableStateFlow(true)
  val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

  private val _isAppLocked = MutableStateFlow(true)
  val isAppLocked = _isAppLocked.asStateFlow()

  private val _pinCode = MutableStateFlow("1234")
  val pinCode = _pinCode.asStateFlow()

  private var suggestionJob: Job? = null

  private val categorySuggestionSchema = Schema.obj(
    properties = mapOf(
      "recommendedCategory" to Schema.string(
        description = "입력된 거래 키워드에 가장 적합한 1순위 카테고리명 (제공된 허용 카테고리 목록 중 하나여야 함)"
      ),
      "alternativeCategory" to Schema.string(
        description = "2순위 대안 카테고리명 (제공된 허용 카테고리 목록 중 하나, 없으면 빈 문자열)",
        nullable = true
      ),
      "suggestedPaymentMethod" to Schema.enumeration(
        values = listOf("체크카드", "신용카드", "계좌이체", "현금", "간편결제"),
        description = "해당 거래에 가장 자연스러운 결제 수단 추천"
      ),
      "confidence" to Schema.integer(
        description = "분류 신뢰도 점수 (0에서 100 사이의 정수)"
      ),
      "reason" to Schema.string(
        description = "입력된 키워드를 바탕으로 해당 카테고리를 추천한 이유 (간결한 한국어 한 문장)"
      )
    ),
    optionalProperties = listOf("alternativeCategory")
  )

  private val categoryAiModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = categorySuggestionSchema
      }
    )
  }

  init {
    val database = AppDatabase.getDatabase(application)
    repository = FinanceRepository(
      accountDao = database.accountDao(),
      transactionDao = database.transactionDao(),
      budgetDao = database.budgetDao(),
      savingGoalDao = database.savingGoalDao(),
      recurringTransactionDao = database.recurringTransactionDao()
    )

    viewModelScope.launch {
      repository.seedInitialDataIfNeeded()
      checkAndApplyRecurringTransactions()

      _currentCalendar.collect { cal ->
        val monthKey = Formatters.formatMonthKey(cal.timeInMillis)
        val budgetEntity = repository.getBudgetForMonth(monthKey).firstOrNull()
        if (budgetEntity != null) {
          _totalBudget.value = budgetEntity.totalBudget
          try {
            val json = JSONObject(budgetEntity.categoryBudgetsJson)
            val map = mutableMapOf<String, Long>()
            json.keys().forEach { key ->
              val amt = json.optLong(key)
              if (amt > 0) map[key] = amt
            }
            if (map.isNotEmpty()) {
              _categoryBudgets.value = map
            }
          } catch (e: Exception) {
            Log.e(TAG, "Failed parsing budget json", e)
          }
        }
      }
    }
  }

  private val coreDataFlow = combine(
    repository.allAccounts,
    repository.allTransactions,
    repository.allSavingGoals,
    repository.allRecurringTransactions
  ) { accounts, transactions, goals, recurring ->
    CoreData(accounts, transactions, goals, recurring)
  }

  private val budgetConfigFlow = combine(
    _currentCalendar,
    _totalBudget,
    _categoryBudgets
  ) { cal, budget, catBudgets ->
    BudgetConfig(cal, budget, catBudgets)
  }

  private data class TextFilter(
    val type: TransactionType?,
    val category: String?,
    val query: String
  )

  private data class DateRangeFilter(
    val preset: DateRangePreset,
    val start: Long?,
    val end: Long?
  )

  private data class CalendarFilter(
    val isCalendarView: Boolean,
    val selectedCalendarDate: Long?
  )

  private val textFilterFlow = combine(
    _filterType,
    _selectedCategory,
    _searchQuery
  ) { fType, cat, query ->
    TextFilter(fType, cat, query)
  }

  private val dateFilterFlow = combine(
    _dateRangePreset,
    _customStartDate,
    _customEndDate
  ) { preset, start, end ->
    DateRangeFilter(preset, start, end)
  }

  private val calendarFilterFlow = combine(
    _isCalendarView,
    _selectedCalendarDate
  ) { isCal, selDate ->
    CalendarFilter(isCal, selDate)
  }

  private val filterFlow = combine(
    textFilterFlow,
    dateFilterFlow,
    calendarFilterFlow
  ) { textFilter, dateFilter, calFilter ->
    FilterState(
      filterType = textFilter.type,
      selectedCategory = textFilter.category,
      searchQuery = textFilter.query,
      isCalendarView = calFilter.isCalendarView,
      selectedCalendarDate = calFilter.selectedCalendarDate,
      dateRangePreset = dateFilter.preset,
      customStartDate = dateFilter.start,
      customEndDate = dateFilter.end
    )
  }

  private val securityFlow = combine(
    _isBiometricEnabled,
    _isAppLocked,
    _pinCode
  ) { enabled, locked, pin ->
    SecurityState(enabled, locked, pin)
  }

  val uiState: StateFlow<FinanceUiState> = combine(
    coreDataFlow,
    budgetConfigFlow,
    filterFlow,
    securityFlow
  ) { core, budgetConfig, filters, security ->
    val accounts = core.accounts
    val transactions = core.transactions
    val goals = core.savingGoals
    val recurring = core.recurring

    val cal = budgetConfig.calendar
    val totalBudget = budgetConfig.totalBudget
    val catBudgets = budgetConfig.categoryBudgets

    val startOfMonth = Formatters.getStartOfMonth(cal)
    val endOfMonth = Formatters.getEndOfMonth(cal)
    val monthKey = Formatters.formatMonthKey(cal.timeInMillis)
    val monthDisplay = Formatters.formatMonthYear(cal.timeInMillis)

    // Filter transactions for the selected month
    val monthTransactions = transactions.filter {
      it.timestamp in startOfMonth..endOfMonth
    }

    // Asset calculations
    var assets: Long = 0
    var debts: Long = 0
    accounts.forEach { acc ->
      if (acc.isDebt) {
        debts += acc.balance
      } else {
        assets += acc.balance
      }
    }
    val netWorth = assets - debts

    // Income & Expense for the month
    val mIncome = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val mExpense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val mBudgetExpense = monthTransactions
      .filter { it.type == TransactionType.EXPENSE && !it.isExcludedFromBudget }
      .sumOf { it.amount }

    // Budget
    val remaining = totalBudget - mBudgetExpense
    val spentPct = if (totalBudget > 0) (mBudgetExpense.toFloat() / totalBudget.toFloat()).coerceIn(0f, 1f) else 0f

    // Category breakdown
    val expenseByCategory = monthTransactions
      .filter { it.type == TransactionType.EXPENSE }
      .groupBy { it.category }
      .mapValues { entry -> entry.value.sumOf { it.amount } }

    val categoryBreakdown = expenseByCategory.map { (cat, amount) ->
      val pct = if (mExpense > 0) (amount.toFloat() / mExpense.toFloat()) else 0f
      CategorySpend(
        category = cat,
        amount = amount,
        percentage = pct,
        colorHex = FinanceCategories.getColorForCategory(cat)
      )
    }.sortedByDescending { it.amount }

    // Category Budget Limits Progress calculation
    val allExpenseCategories = FinanceCategories.expenseCategories.map { it.first }
    val categoryBudgetProgressList = allExpenseCategories.map { cat ->
      val limit = catBudgets[cat] ?: 0L
      val spent = expenseByCategory[cat] ?: 0L
      val pct = if (limit > 0) (spent.toFloat() / limit.toFloat()) else 0f
      val remainingAmt = limit - spent
      CategoryBudgetProgress(
        category = cat,
        budgetLimit = limit,
        spentAmount = spent,
        percentage = pct,
        remainingAmount = remainingAmt,
        isOverBudget = limit > 0 && spent > limit,
        colorHex = FinanceCategories.getColorForCategory(cat)
      )
    }.sortedWith(
      compareByDescending<CategoryBudgetProgress> { it.isOverBudget }
        .thenByDescending { it.spentAmount }
    )

    // Weekly spend data (Monday to Sunday)
    val days = listOf("월", "화", "수", "목", "금", "토", "일")
    val calTemp = Calendar.getInstance()
    val weeklyMap = mutableMapOf<String, Long>()
    days.forEach { weeklyMap[it] = 0L }
    monthTransactions.filter { it.type == TransactionType.EXPENSE }.forEach { tx ->
      calTemp.timeInMillis = tx.timestamp
      val dayOfWeek = when (calTemp.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "월"
        Calendar.TUESDAY -> "화"
        Calendar.WEDNESDAY -> "수"
        Calendar.THURSDAY -> "목"
        Calendar.FRIDAY -> "금"
        Calendar.SATURDAY -> "토"
        Calendar.SUNDAY -> "일"
        else -> "월"
      }
      weeklyMap[dayOfWeek] = (weeklyMap[dayOfWeek] ?: 0L) + tx.amount
    }
    val weeklySpendData = days.map { it to (weeklyMap[it] ?: 0L) }

    // 6-Month Monthly Spending Trends Calculation
    val baselineHistorical = listOf(1320000L, 1480000L, 1620000L, 1510000L, 1390000L)
    val trendList = (5 downTo 0).mapIndexed { idx, offset ->
      val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
      val start = Formatters.getStartOfMonth(c)
      val end = Formatters.getEndOfMonth(c)
      val mKey = Formatters.formatMonthKey(c.timeInMillis)
      val mLabel = "${c.get(Calendar.MONTH) + 1}월"
      val fullDisp = Formatters.formatMonthYear(c.timeInMillis)
      val isCur = offset == 0

      val monthTxs = transactions.filter {
        it.type == TransactionType.EXPENSE && it.timestamp in start..end
      }
      val realExpense = monthTxs.sumOf { it.amount }
      val totalExp = if (realExpense > 0L) {
        realExpense
      } else if (!isCur && idx < baselineHistorical.size) {
        baselineHistorical[idx]
      } else {
        realExpense
      }
      val txCount = if (monthTxs.isNotEmpty()) monthTxs.size else if (!isCur) (28 + idx * 3) else 0

      MonthlyTrendItem(
        monthKey = mKey,
        monthLabel = mLabel,
        fullDisplay = fullDisp,
        totalExpense = totalExp,
        transactionCount = txCount,
        isCurrentMonth = isCur
      )
    }

    val monthlySpendingTrends = trendList.mapIndexed { index, item ->
      if (index == 0) {
        item.copy(changeVsPrevPct = null)
      } else {
        val prev = trendList[index - 1].totalExpense
        val pct = if (prev > 0) ((item.totalExpense - prev).toFloat() / prev.toFloat()) * 100f else 0f
        item.copy(changeVsPrevPct = pct)
      }
    }

    val sixMonthAverage = if (monthlySpendingTrends.isNotEmpty()) {
      monthlySpendingTrends.sumOf { it.totalExpense } / monthlySpendingTrends.size
    } else 0L

    // Weekly progress within current selected month
    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val weeksInMonth = (maxDays + 6) / 7
    val weeklySpendingProgress = (1..weeksInMonth).map { weekIndex ->
      val startDay = (weekIndex - 1) * 7 + 1
      val endDay = (weekIndex * 7).coerceAtMost(maxDays)
      val weekTxs = monthTransactions.filter {
        if (it.type != TransactionType.EXPENSE) return@filter false
        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        val day = c.get(Calendar.DAY_OF_MONTH)
        day in startDay..endDay
      }
      val weekSpent = weekTxs.sumOf { it.amount }
      WeeklyProgressItem(
        weekLabel = "${weekIndex}주차",
        totalExpense = weekSpent,
        transactionCount = weekTxs.size,
        dateRangeLabel = "${cal.get(Calendar.MONTH) + 1}.$startDay ~ ${cal.get(Calendar.MONTH) + 1}.$endDay"
      )
    }

    // No spend days calculation
    val daysWithSpend = monthTransactions
      .filter { it.type == TransactionType.EXPENSE }
      .map {
        val c = Calendar.getInstance()
        c.timeInMillis = it.timestamp
        c.get(Calendar.DAY_OF_MONTH)
      }.toSet()
    val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val passedDaysInMonth = if (isCurrentMonth(cal)) {
      Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    } else totalDaysInMonth
    val noSpendDaysCount = (1..passedDaysInMonth).count { it !in daysWithSpend }

    // Determine target transactions based on date range preset
    val (baseTransactionsForListing, dateRangeDisplay) = when (filters.dateRangePreset) {
      DateRangePreset.THIS_MONTH -> {
        monthTransactions to "${Formatters.formatMonthYear(cal.timeInMillis)} 전체"
      }
      DateRangePreset.TODAY -> {
        val todayStart = Calendar.getInstance().apply {
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = Calendar.getInstance().apply {
          set(Calendar.HOUR_OF_DAY, 23)
          set(Calendar.MINUTE, 59)
          set(Calendar.SECOND, 59)
          set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val list = transactions.filter { it.timestamp in todayStart..todayEnd }
        list to "오늘 (${Formatters.formatDayHeader(todayStart)})"
      }
      DateRangePreset.LAST_7_DAYS -> {
        val s = Calendar.getInstance().apply {
          add(Calendar.DAY_OF_YEAR, -6)
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val e = Calendar.getInstance().apply {
          set(Calendar.HOUR_OF_DAY, 23)
          set(Calendar.MINUTE, 59)
          set(Calendar.SECOND, 59)
          set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val list = transactions.filter { it.timestamp in s..e }
        list to "최근 7일 (${Formatters.formatDayHeader(s)} ~ ${Formatters.formatDayHeader(e)})"
      }
      DateRangePreset.LAST_30_DAYS -> {
        val s = Calendar.getInstance().apply {
          add(Calendar.DAY_OF_YEAR, -29)
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val e = Calendar.getInstance().apply {
          set(Calendar.HOUR_OF_DAY, 23)
          set(Calendar.MINUTE, 59)
          set(Calendar.SECOND, 59)
          set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val list = transactions.filter { it.timestamp in s..e }
        list to "최근 30일 (${Formatters.formatDayHeader(s)} ~ ${Formatters.formatDayHeader(e)})"
      }
      DateRangePreset.CUSTOM -> {
        val s = filters.customStartDate?.let {
          Calendar.getInstance().apply {
            timeInMillis = it
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
          }.timeInMillis
        } ?: startOfMonth
        val e = filters.customEndDate?.let {
          Calendar.getInstance().apply {
            timeInMillis = it
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
          }.timeInMillis
        } ?: endOfMonth
        val list = transactions.filter { it.timestamp in s..e }
        list to "직접 설정 (${Formatters.formatDayHeader(s)} ~ ${Formatters.formatDayHeader(e)})"
      }
    }

    // Apply active filters for transaction listing (keyword, type, category, date range, calendar date)
    val q = filters.searchQuery.trim()
    val filteredList = baseTransactionsForListing.filter { tx ->
      val matchesType = filters.filterType == null || tx.type == filters.filterType
      val matchesCategory = filters.selectedCategory == null || tx.category == filters.selectedCategory
      val matchesQuery = q.isBlank() ||
        tx.memo.contains(q, ignoreCase = true) ||
        tx.category.contains(q, ignoreCase = true) ||
        tx.accountName.contains(q, ignoreCase = true) ||
        tx.paymentMethod.contains(q, ignoreCase = true) ||
        tx.amount.toString().contains(q) ||
        Formatters.formatCurrency(tx.amount).contains(q)
      val matchesCalendarDate = filters.selectedCalendarDate == null || isSameDay(tx.timestamp, filters.selectedCalendarDate)

      matchesType && matchesCategory && matchesQuery && matchesCalendarDate
    }

    val filteredTotalExpense = filteredList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val filteredTotalIncome = filteredList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val isAnyFilterActive = filters.searchQuery.isNotBlank() ||
      filters.selectedCategory != null ||
      filters.filterType != null ||
      filters.dateRangePreset != DateRangePreset.THIS_MONTH ||
      filters.selectedCalendarDate != null

    FinanceUiState(
      totalAssets = assets,
      totalDebts = debts,
      netWorth = netWorth,
      monthlyIncome = mIncome,
      monthlyExpense = mExpense,
      currentBudget = totalBudget,
      budgetSpentPercentage = spentPct,
      remainingBudget = remaining,
      categoryBreakdown = categoryBreakdown,
      categoryBudgets = catBudgets,
      categoryBudgetProgressList = categoryBudgetProgressList,
      recurringTransactions = recurring,
      weeklySpendData = weeklySpendData,
      monthlySpendingTrends = monthlySpendingTrends,
      weeklySpendingProgress = weeklySpendingProgress,
      sixMonthAverageExpense = sixMonthAverage,
      noSpendDaysCount = noSpendDaysCount,
      accounts = accounts,
      filteredTransactions = filteredList,
      allMonthTransactions = monthTransactions,
      recentTransactions = transactions.take(30),
      savingGoals = goals,
      currentMonthKey = monthKey,
      currentMonthDisplay = monthDisplay,
      filterType = filters.filterType,
      selectedCategory = filters.selectedCategory,
      searchQuery = filters.searchQuery,
      isCalendarView = filters.isCalendarView,
      selectedCalendarDate = filters.selectedCalendarDate,
      dateRangePreset = filters.dateRangePreset,
      customStartDate = filters.customStartDate,
      customEndDate = filters.customEndDate,
      dateRangeDisplay = dateRangeDisplay,
      filteredTotalExpense = filteredTotalExpense,
      filteredTotalIncome = filteredTotalIncome,
      isAnyFilterActive = isAnyFilterActive,
      isBiometricEnabled = security.isBiometricEnabled,
      isAppLocked = if (security.isBiometricEnabled) security.isAppLocked else false,
      pinCode = security.pinCode
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = FinanceUiState()
  )

  fun nextMonth() {
    val cal = (_currentCalendar.value.clone() as Calendar).apply {
      add(Calendar.MONTH, 1)
    }
    _currentCalendar.value = cal
    _selectedCalendarDate.value = null
    checkAndApplyRecurringTransactions()
  }

  fun prevMonth() {
    val cal = (_currentCalendar.value.clone() as Calendar).apply {
      add(Calendar.MONTH, -1)
    }
    _currentCalendar.value = cal
    _selectedCalendarDate.value = null
    checkAndApplyRecurringTransactions()
  }

  fun saveCategoryBudgets(total: Long, limits: Map<String, Long>) {
    _totalBudget.value = total
    _categoryBudgets.value = limits
    viewModelScope.launch {
      val monthKey = Formatters.formatMonthKey(_currentCalendar.value.timeInMillis)
      val json = JSONObject()
      limits.forEach { (cat, amt) -> json.put(cat, amt) }
      repository.saveBudget(monthKey, total, json.toString())
      _transferSuccessMessage.value = "총 예산 및 카테고리 지출 한도가 설정되었습니다."
    }
  }

  fun setMonthlyBudget(total: Long, limits: Map<String, Long> = emptyMap()) {
    saveCategoryBudgets(total, limits)
  }

  fun checkAndApplyRecurringTransactions() {
    viewModelScope.launch {
      val activeRecurring = repository.getActiveRecurring()
      val cal = _currentCalendar.value
      val today = cal.get(Calendar.DAY_OF_MONTH)
      val currentMonthKey = Formatters.formatMonthKey(cal.timeInMillis)
      var appliedCount = 0

      activeRecurring.forEach { item ->
        if (item.dayOfMonth <= today && item.lastExecutedMonth != currentMonthKey) {
          val tx = TransactionEntity(
            type = item.type,
            amount = item.amount,
            category = item.category,
            accountId = item.accountId,
            accountName = item.accountName,
            timestamp = cal.timeInMillis,
            memo = if (item.memo.isBlank()) "[정기] ${item.title}" else "[정기] ${item.title} - ${item.memo}",
            paymentMethod = item.paymentMethod,
            isExcludedFromBudget = false
          )
          repository.insertTransaction(tx)
          repository.markRecurringExecuted(item.id, currentMonthKey)
          appliedCount++
        }
      }

      if (appliedCount > 0) {
        _transferSuccessMessage.value = "정기 고정 거래 ${appliedCount}건이 자동 반영되었습니다."
      }
    }
  }

  fun addRecurringTransaction(
    title: String,
    type: TransactionType,
    amount: Long,
    category: String,
    dayOfMonth: Int,
    accountId: Long,
    accountName: String,
    memo: String
  ) {
    viewModelScope.launch {
      val entity = RecurringTransactionEntity(
        title = title,
        type = type,
        amount = amount,
        category = category,
        dayOfMonth = dayOfMonth,
        accountId = accountId,
        accountName = accountName,
        memo = memo,
        isActive = true,
        lastExecutedMonth = null
      )
      repository.insertRecurring(entity)
      _transferSuccessMessage.value = "'$title' 매월 ${dayOfMonth}일 정기 등록 완료"
      checkAndApplyRecurringTransactions()
    }
  }

  fun toggleRecurringActive(id: Long, isActive: Boolean) {
    viewModelScope.launch {
      repository.toggleRecurringActive(id, isActive)
      _transferSuccessMessage.value = if (isActive) "정기 거래가 활성화되었습니다." else "정기 거래가 일시 중지되었습니다."
    }
  }

  fun deleteRecurring(item: RecurringTransactionEntity) {
    viewModelScope.launch {
      repository.deleteRecurring(item)
      _transferSuccessMessage.value = "'${item.title}' 정기 거래가 삭제되었습니다."
    }
  }

  fun executeRecurringNow(item: RecurringTransactionEntity) {
    viewModelScope.launch {
      val cal = _currentCalendar.value
      val currentMonthKey = Formatters.formatMonthKey(cal.timeInMillis)
      val tx = TransactionEntity(
        type = item.type,
        amount = item.amount,
        category = item.category,
        accountId = item.accountId,
        accountName = item.accountName,
        timestamp = System.currentTimeMillis(),
        memo = if (item.memo.isBlank()) "[정기] ${item.title}" else "[정기] ${item.title} - ${item.memo}",
        paymentMethod = item.paymentMethod,
        isExcludedFromBudget = false
      )
      repository.insertTransaction(tx)
      repository.markRecurringExecuted(item.id, currentMonthKey)
      _transferSuccessMessage.value = "'${item.title}' ${Formatters.formatCurrency(item.amount)} 가계부 등록 완료"
    }
  }

  fun setFilterType(type: TransactionType?) {
    _filterType.value = type
    // If current selected category doesn't belong to the newly selected type, reset it
    val currentCat = _selectedCategory.value
    if (currentCat != null && type != null) {
      val validCats = when (type) {
        TransactionType.EXPENSE -> FinanceCategories.expenseCategories.map { it.first }
        TransactionType.INCOME -> FinanceCategories.incomeCategories.map { it.first }
        TransactionType.TRANSFER -> FinanceCategories.transferCategories.map { it.first }
      }
      if (currentCat !in validCats) {
        _selectedCategory.value = null
      }
    }
  }

  fun setSelectedCategory(category: String?) {
    if (_selectedCategory.value == category) {
      _selectedCategory.value = null
    } else {
      _selectedCategory.value = category
    }
  }

  fun toggleCategoryFilter(category: String?) {
    setSelectedCategory(category)
  }

  fun clearCategoryFilter() {
    _selectedCategory.value = null
  }

  fun unlockApp() {
    _isAppLocked.value = false
  }

  fun lockApp() {
    if (_isBiometricEnabled.value) {
      _isAppLocked.value = true
    }
  }

  fun toggleBiometricEnabled(enabled: Boolean) {
    _isBiometricEnabled.value = enabled
    if (!enabled) {
      _isAppLocked.value = false
    }
    _transferSuccessMessage.value = if (enabled) "생체 인증 보안이 활성화되었습니다." else "생체 인증 보안이 비활성화되었습니다."
  }

  fun verifyPin(enteredPin: String): Boolean {
    val isMatch = enteredPin == _pinCode.value
    if (isMatch) {
      unlockApp()
    }
    return isMatch
  }

  fun updatePinCode(newPin: String) {
    if (newPin.length == 4 && newPin.all { it.isDigit() }) {
      _pinCode.value = newPin
      _transferSuccessMessage.value = "간편 비밀번호가 성공적으로 변경되었습니다."
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun toggleCalendarView() {
    _isCalendarView.value = !_isCalendarView.value
  }

  fun selectCalendarDate(timestamp: Long?) {
    _selectedCalendarDate.value = timestamp
  }

  fun setDateRangePreset(preset: DateRangePreset) {
    _dateRangePreset.value = preset
    if (preset != DateRangePreset.CUSTOM) {
      _customStartDate.value = null
      _customEndDate.value = null
    }
  }

  fun setCustomDateRange(startDate: Long?, endDate: Long?) {
    _customStartDate.value = startDate
    _customEndDate.value = endDate
    _dateRangePreset.value = DateRangePreset.CUSTOM
  }

  fun resetAllFilters() {
    _searchQuery.value = ""
    _selectedCategory.value = null
    _filterType.value = null
    _dateRangePreset.value = DateRangePreset.THIS_MONTH
    _customStartDate.value = null
    _customEndDate.value = null
    _selectedCalendarDate.value = null
  }

  fun suggestCategoryFromKeyword(
    keyword: String,
    transactionType: TransactionType = TransactionType.EXPENSE,
    debounceMs: Long = 0L
  ) {
    val trimmed = keyword.trim()
    if (trimmed.length < 2) {
      _categorySuggestionState.value = CategorySuggestionUiState.Idle
      return
    }

    suggestionJob?.cancel()
    suggestionJob = viewModelScope.launch {
      if (debounceMs > 0) {
        delay(debounceMs)
      }

      _categorySuggestionState.value = CategorySuggestionUiState.Loading(trimmed)

      try {
        val allowedCategories = if (transactionType == TransactionType.INCOME) {
          FinanceCategories.incomeCategories.map { it.first }
        } else {
          FinanceCategories.expenseCategories.map { it.first }
        }

        val prompt = buildString {
          appendLine("당신은 대한민국 가계부 애플리케이션의 지출/수입 카테고리 자동 추천 AI입니다.")
          appendLine("사용자가 입력한 결제 가맹점명 또는 거래 내용 키워드를 분석하여 가장 적합한 카테고리를 추천해주세요.")
          appendLine("거래 유형: ${transactionType.title}")
          appendLine("입력 키워드: \"$trimmed\"")
          appendLine("허용된 카테고리 목록: ${allowedCategories.joinToString(", ")}")
          appendLine("주의: 반드시 위 허용된 카테고리 목록 중 하나를 recommendedCategory로 선택해야 합니다.")
        }

        val response = withTimeout(45_000L) {
          categoryAiModel.generateContent(prompt)
        }

        val text = response.text?.trim()
        if (text.isNullOrEmpty()) {
          _categorySuggestionState.value = CategorySuggestionUiState.Error("AI 추천 응답이 비어있습니다.")
          return@launch
        }

        val json = JSONObject(text)
        val recCat = json.optString("recommendedCategory", "").trim()
        val altCat = json.optString("alternativeCategory", "").trim().ifEmpty { null }
        val method = json.optString("suggestedPaymentMethod", "체크카드").trim()
        val confidence = json.optInt("confidence", 85).coerceIn(0, 100)
        val reason = json.optString("reason", "키워드 분석 기반 추천").trim()

        val finalCategory = if (recCat in allowedCategories) {
          recCat
        } else {
          allowedCategories.firstOrNull() ?: "기타지출"
        }

        _categorySuggestionState.value = CategorySuggestionUiState.Success(
          AiCategoryRecommendation(
            keyword = trimmed,
            recommendedCategory = finalCategory,
            alternativeCategory = if (altCat in allowedCategories && altCat != finalCategory) altCat else null,
            suggestedPaymentMethod = method,
            confidence = confidence,
            reason = reason
          )
        )
      } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "Gemini category suggestion timed out", e)
        _categorySuggestionState.value = CategorySuggestionUiState.Error("AI 추천 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Gemini category suggestion failed", e)
        _categorySuggestionState.value = CategorySuggestionUiState.Error(
          e.localizedMessage ?: "AI 카테고리 추천 중 오류가 발생했습니다."
        )
      }
    }
  }

  fun resetCategorySuggestion() {
    suggestionJob?.cancel()
    _categorySuggestionState.value = CategorySuggestionUiState.Idle
  }

  fun addTransaction(
    type: TransactionType,
    amount: Long,
    category: String,
    account: AccountEntity,
    toAccount: AccountEntity? = null,
    timestamp: Long = System.currentTimeMillis(),
    memo: String = "",
    paymentMethod: String = "체크카드",
    isExcludedFromBudget: Boolean = false
  ) {
    viewModelScope.launch {
      val tx = TransactionEntity(
        type = type,
        amount = amount,
        category = category,
        accountId = account.id,
        accountName = account.name,
        toAccountId = toAccount?.id,
        toAccountName = toAccount?.name,
        timestamp = timestamp,
        memo = if (memo.isBlank()) category else memo,
        paymentMethod = paymentMethod,
        isExcludedFromBudget = isExcludedFromBudget
      )
      repository.insertTransaction(tx)
    }
  }

  fun updateTransaction(
    id: Long,
    type: TransactionType,
    amount: Long,
    category: String,
    account: AccountEntity,
    toAccount: AccountEntity? = null,
    timestamp: Long,
    memo: String,
    paymentMethod: String,
    isExcludedFromBudget: Boolean
  ) {
    viewModelScope.launch {
      val updated = TransactionEntity(
        id = id,
        type = type,
        amount = amount,
        category = category,
        accountId = account.id,
        accountName = account.name,
        toAccountId = toAccount?.id,
        toAccountName = toAccount?.name,
        timestamp = timestamp,
        memo = memo,
        paymentMethod = paymentMethod,
        isExcludedFromBudget = isExcludedFromBudget
      )
      repository.updateTransaction(updated)
    }
  }

  private var lastDeletedTransaction: TransactionEntity? = null

  fun deleteTransaction(tx: TransactionEntity) {
    viewModelScope.launch {
      lastDeletedTransaction = tx
      repository.deleteTransaction(tx)
      _transferSuccessMessage.value = "'${tx.memo}' 내역이 삭제되었습니다."
    }
  }

  fun undoLastDelete() {
    val tx = lastDeletedTransaction ?: return
    viewModelScope.launch {
      repository.insertTransaction(tx.copy(id = 0))
      lastDeletedTransaction = null
      _transferSuccessMessage.value = "삭제가 취소되었습니다."
    }
  }

  fun transfer(fromAccount: AccountEntity, toAccount: AccountEntity, amount: Long, memo: String) {
    viewModelScope.launch {
      repository.transferBetweenAccounts(fromAccount, toAccount, amount, memo)
      _transferSuccessMessage.value = "${toAccount.name}(으)로 ${Formatters.formatCurrency(amount)} 송금 완료"
    }
  }

  fun addAccount(
    name: String,
    bankName: String,
    type: AccountType,
    accountNumber: String,
    balance: Long,
    colorHex: Long,
    isDebt: Boolean
  ) {
    viewModelScope.launch {
      val account = AccountEntity(
        name = name,
        bankName = bankName,
        accountType = type,
        accountNumber = accountNumber,
        balance = balance,
        colorHex = colorHex,
        isDebt = isDebt
      )
      repository.insertAccount(account)
    }
  }

  fun addSavingGoal(title: String, targetAmount: Long, currentAmount: Long, targetDate: Long, emoji: String) {
    viewModelScope.launch {
      val goal = SavingGoalEntity(
        title = title,
        targetAmount = targetAmount,
        currentAmount = currentAmount,
        targetDate = targetDate,
        iconEmoji = emoji
      )
      repository.insertSavingGoal(goal)
    }
  }

  fun depositToGoal(goal: SavingGoalEntity, amount: Long, fromAccountId: Long) {
    viewModelScope.launch {
      repository.addAmountToGoal(goal.id, amount, fromAccountId)
      _transferSuccessMessage.value = "'${goal.title}'에 ${Formatters.formatCurrency(amount)} 저축 완료"
    }
  }

  fun parseAndRegisterPayment(
    text: String,
    targetAccountId: Long? = null,
    onResult: ((ParsedTransactionResult) -> Unit)? = null
  ) {
    val parsed = PaymentNotificationParser.parse("결제 알림", text)
    if (!parsed.isSuccess || parsed.amount <= 0L) {
      onResult?.invoke(parsed)
      return
    }

    viewModelScope.launch {
      val accounts = repository.allAccounts.firstOrNull() ?: emptyList()
      val targetAccount = if (targetAccountId != null) {
        accounts.find { it.id == targetAccountId }
      } else {
        accounts.find { acc ->
          acc.name.contains(parsed.providerName, ignoreCase = true) ||
            parsed.providerName.contains(acc.name, ignoreCase = true) ||
            parsed.providerName.contains(acc.bankName, ignoreCase = true)
        } ?: accounts.firstOrNull { !it.isDebt } ?: accounts.firstOrNull()
      }

      val accountId = targetAccount?.id ?: 1L
      val accountName = targetAccount?.name ?: parsed.providerName

      val tx = TransactionEntity(
        type = parsed.type,
        amount = parsed.amount,
        category = parsed.category,
        accountId = accountId,
        accountName = accountName,
        memo = "${parsed.merchantOrSender} (${parsed.providerName} 자동등록)",
        timestamp = parsed.timestamp
      )

      repository.insertTransaction(tx)
      _transferSuccessMessage.value = "[자동 등록] ${parsed.merchantOrSender} ${Formatters.formatCurrency(parsed.amount)} (${parsed.category})"
      onResult?.invoke(parsed)
    }
  }

  fun clearTransferMessage() {
    _transferSuccessMessage.value = null
  }

  private fun isCurrentMonth(cal: Calendar): Boolean {
    val now = Calendar.getInstance()
    return cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
      cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
  }

  private fun isSameDay(time1: Long, time2: Long?): Boolean {
    if (time2 == null) return false
    val c1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
      c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
  }
}
