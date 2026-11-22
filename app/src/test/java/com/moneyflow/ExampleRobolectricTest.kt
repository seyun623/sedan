package com.moneyflow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.FinanceRepository
import com.moneyflow.util.Formatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var database: AppDatabase
  private lateinit var repository: FinanceRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = FinanceRepository(
      accountDao = database.accountDao(),
      transactionDao = database.transactionDao(),
      budgetDao = database.budgetDao(),
      savingGoalDao = database.savingGoalDao(),
      recurringTransactionDao = database.recurringTransactionDao()
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("머니플로우", appName)
  }

  @Test
  fun `test currency formatting`() {
    val formatted = Formatters.formatCurrency(1250000L)
    assertEquals("1,250,000원", formatted)
    assertEquals("125만원", Formatters.formatKoreanUnitCurrency(1250000L))
  }

  @Test
  fun `test transaction Room CRUD and account balance reconciliation`() = runBlocking {
    // 1. Setup test bank account with 1,000,000 KRW
    val accountId = repository.insertAccount(
      AccountEntity(
        name = "테스트 주계좌",
        bankName = "토스뱅크",
        accountType = AccountType.BANK,
        accountNumber = "1000-01",
        balance = 1_000_000L
      )
    )

    // 2. CREATE (Insert expense of 15,000 KRW)
    val txId = repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 15_000L,
        category = "식비",
        accountId = accountId,
        accountName = "테스트 주계좌",
        timestamp = System.currentTimeMillis(),
        memo = "점심 식사"
      )
    )

    // Verify READ and balance deduction (1,000,000 - 15,000 = 985,000)
    val insertedTx = repository.getTransactionById(txId)
    assertNotNull(insertedTx)
    assertEquals("점심 식사", insertedTx?.memo)
    val accAfterInsert = database.accountDao().getAccountById(accountId)
    assertEquals(985_000L, accAfterInsert?.balance)

    // 3. UPDATE (Change expense from 15,000 KRW to 25,000 KRW and update memo)
    val updatedTx = insertedTx!!.copy(
      amount = 25_000L,
      memo = "저녁 회식"
    )
    repository.updateTransaction(updatedTx)

    val fetchedAfterUpdate = repository.getTransactionById(txId)
    assertEquals(25_000L, fetchedAfterUpdate?.amount)
    assertEquals("저녁 회식", fetchedAfterUpdate?.memo)
    // Verify balance reconciled: 985,000 + 15,000 (revert) - 25,000 (apply) = 975,000
    val accAfterUpdate = database.accountDao().getAccountById(accountId)
    assertEquals(975_000L, accAfterUpdate?.balance)

    // 4. DELETE (Delete the transaction and verify balance restored to 1,000,000)
    repository.deleteTransaction(fetchedAfterUpdate!!)
    val fetchedAfterDelete = repository.getTransactionById(txId)
    assertNull(fetchedAfterDelete)
    val accAfterDelete = database.accountDao().getAccountById(accountId)
    assertEquals(1_000_000L, accAfterDelete?.balance)
  }

  @Test
  fun `test category breakdown aggregation for pie chart`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(
        name = "현금",
        bankName = "현금",
        accountType = AccountType.CASH,
        accountNumber = "CASH-01",
        balance = 500_000L
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 30_000L,
        category = "식비",
        accountId = accId,
        accountName = "현금",
        memo = "점심",
        timestamp = System.currentTimeMillis()
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 20_000L,
        category = "식비",
        accountId = accId,
        accountName = "현금",
        memo = "저녁",
        timestamp = System.currentTimeMillis()
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 50_000L,
        category = "교통",
        accountId = accId,
        accountName = "현금",
        memo = "주유",
        timestamp = System.currentTimeMillis()
      )
    )

    val allTx = repository.allTransactions.first()
    val expenses = allTx.filter { it.type == TransactionType.EXPENSE }
    val totalExpense = expenses.sumOf { it.amount }
    assertEquals(100_000L, totalExpense)

    val grouped = expenses.groupBy { it.category }
    assertEquals(50_000L, grouped["식비"]?.sumOf { it.amount })
    assertEquals(50_000L, grouped["교통"]?.sumOf { it.amount })
  }

  @Test
  fun `test transaction filtering by category chip and toggle`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(
        name = "토스통장",
        bankName = "토스뱅크",
        accountType = AccountType.BANK,
        accountNumber = "1000-88",
        balance = 2_000_000L
      )
    )

    // Insert transactions across Food, Transport, and Salary
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 12_000L,
        category = "식비",
        accountId = accId,
        accountName = "토스통장",
        memo = "점심 김치찌개",
        timestamp = System.currentTimeMillis()
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 8_000L,
        category = "식비",
        accountId = accId,
        accountName = "토스통장",
        memo = "커피",
        timestamp = System.currentTimeMillis()
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 55_000L,
        category = "교통",
        accountId = accId,
        accountName = "토스통장",
        memo = "지하철 정기권 및 택시",
        timestamp = System.currentTimeMillis()
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.INCOME,
        amount = 3_000_000L,
        category = "급여",
        accountId = accId,
        accountName = "토스통장",
        memo = "10월 급여",
        timestamp = System.currentTimeMillis()
      )
    )

    val allTransactions = repository.allTransactions.first()
    assertEquals(4, allTransactions.size)

    // 1. Filter by "식비" (Food)
    val foodFilter = "식비"
    val foodTransactions = allTransactions.filter { it.category == foodFilter }
    assertEquals(2, foodTransactions.size)
    assertEquals(20_000L, foodTransactions.sumOf { it.amount })

    // 2. Filter by "교통" (Transport)
    val transportFilter = "교통"
    val transportTransactions = allTransactions.filter { it.category == transportFilter }
    assertEquals(1, transportTransactions.size)
    assertEquals(55_000L, transportTransactions.first().amount)

    // 3. Filter by "급여" (Salary)
    val salaryFilter = "급여"
    val salaryTransactions = allTransactions.filter { it.category == salaryFilter }
    assertEquals(1, salaryTransactions.size)
    assertEquals(3_000_000L, salaryTransactions.first().amount)

    // 4. Toggle / Reset back to "전체" (null)
    var selectedCategory: String? = "식비"
    // Clicking selected category toggles to null
    selectedCategory = if (selectedCategory == "식비") null else "식비"
    assertEquals(null, selectedCategory)

    val resetTransactions = allTransactions.filter { selectedCategory == null || it.category == selectedCategory }
    assertEquals(4, resetTransactions.size)
  }

  @Test
  fun `test monthly budget setting and category spending limit progress calculation`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(
        name = "생활비통장",
        bankName = "토스뱅크",
        accountType = AccountType.BANK,
        accountNumber = "1000-99",
        balance = 1_500_000L
      )
    )

    // 1. Save Budget into Room via repository: Total 1,000,000 KRW with Category limits
    val monthKey = "2026-10"
    val limitsJson = org.json.JSONObject().apply {
      put("식비", 300_000L)
      put("카페/간식", 100_000L)
      put("교통", 150_000L)
    }.toString()

    repository.saveBudget(monthKey, 1_000_000L, limitsJson)

    val savedBudget = database.budgetDao().getBudgetForMonth(monthKey).first()
    assertNotNull(savedBudget)
    assertEquals(1_000_000L, savedBudget?.totalBudget)

    // 2. Insert Expenses:
    // Food (식비): 320,000 KRW (limit 300,000 -> OVER BUDGET by 20,000)
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 320_000L,
        category = "식비",
        accountId = accId,
        accountName = "생활비통장",
        memo = "식료품 및 외식",
        timestamp = System.currentTimeMillis()
      )
    )

    // Cafe (카페/간식): 50,000 KRW (limit 100,000 -> UNDER BUDGET, 50% used)
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 50_000L,
        category = "카페/간식",
        accountId = accId,
        accountName = "생활비통장",
        memo = "카페 음료",
        timestamp = System.currentTimeMillis()
      )
    )

    val allTx = repository.allTransactions.first()
    val totalExpense = allTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    assertEquals(370_000L, totalExpense)

    val totalBudget = savedBudget!!.totalBudget
    val remainingBudget = totalBudget - totalExpense
    assertEquals(630_000L, remainingBudget)

    val budgetUsagePct = totalExpense.toFloat() / totalBudget.toFloat()
    assertEquals(0.37f, budgetUsagePct, 0.001f)

    // Check category limit calculations
    val catLimitsMap = mapOf(
      "식비" to 300_000L,
      "카페/간식" to 100_000L,
      "교통" to 150_000L
    )
    val foodSpent = allTx.filter { it.category == "식비" }.sumOf { it.amount }
    val foodLimit = catLimitsMap["식비"] ?: 0L
    val isFoodOver = foodSpent > foodLimit
    assertTrue(isFoodOver)
    assertEquals(20_000L, foodSpent - foodLimit)

    val cafeSpent = allTx.filter { it.category == "카페/간식" }.sumOf { it.amount }
    val cafeLimit = catLimitsMap["카페/간식"] ?: 0L
    val isCafeOver = cafeSpent > cafeLimit
    assertFalse(isCafeOver)
    assertEquals(50_000L, cafeLimit - cafeSpent)
  }

  @Test
  fun `test biometric authentication security state and pin verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
    val viewModel = com.moneyflow.ui.viewmodel.FinanceViewModel(context)

    // 1. Initial State: Biometric security is enabled and app is locked to protect sensitive financial data
    assertTrue(viewModel.isBiometricEnabled.value)
    assertTrue(viewModel.isAppLocked.value)

    // 2. Biometric authentication success unlocks app
    viewModel.unlockApp()
    assertFalse(viewModel.isAppLocked.value)

    // 3. User locks app again
    viewModel.lockApp()
    assertTrue(viewModel.isAppLocked.value)

    // 4. Test Fallback PIN verification
    assertFalse(viewModel.verifyPin("0000")) // Wrong PIN fails
    assertTrue(viewModel.isAppLocked.value)

    assertTrue(viewModel.verifyPin("1234")) // Correct PIN unlocks app
    assertFalse(viewModel.isAppLocked.value)

    // 5. Test Toggling Biometric security OFF
    viewModel.toggleBiometricEnabled(false)
    assertFalse(viewModel.isBiometricEnabled.value)
    assertFalse(viewModel.isAppLocked.value)

    // When disabled, lockApp does not lock
    viewModel.lockApp()
    assertFalse(viewModel.isAppLocked.value)

    // 6. Test Toggling Biometric security back ON
    viewModel.toggleBiometricEnabled(true)
    assertTrue(viewModel.isBiometricEnabled.value)
  }

  @Test
  fun `test monthly spending trend calculation and percentage comparison`() {
    // Test 6-month trend calculation with baseline values and percentage deltas
    val trendItems = listOf(
      com.moneyflow.ui.viewmodel.MonthlyTrendItem(
        monthKey = "2026-05",
        monthLabel = "5월",
        fullDisplay = "2026년 5월",
        totalExpense = 1_320_000L,
        transactionCount = 28,
        isCurrentMonth = false,
        changeVsPrevPct = null
      ),
      com.moneyflow.ui.viewmodel.MonthlyTrendItem(
        monthKey = "2026-06",
        monthLabel = "6월",
        fullDisplay = "2026년 6월",
        totalExpense = 1_480_000L,
        transactionCount = 31,
        isCurrentMonth = false,
        changeVsPrevPct = ((1_480_000L - 1_320_000L).toFloat() / 1_320_000L.toFloat()) * 100f
      ),
      com.moneyflow.ui.viewmodel.MonthlyTrendItem(
        monthKey = "2026-07",
        monthLabel = "7월",
        fullDisplay = "2026년 7월",
        totalExpense = 1_620_000L,
        transactionCount = 34,
        isCurrentMonth = false,
        changeVsPrevPct = ((1_620_000L - 1_480_000L).toFloat() / 1_480_000L.toFloat()) * 100f
      ),
      com.moneyflow.ui.viewmodel.MonthlyTrendItem(
        monthKey = "2026-08",
        monthLabel = "8월",
        fullDisplay = "2026년 8월",
        totalExpense = 1_510_000L,
        transactionCount = 37,
        isCurrentMonth = false,
        changeVsPrevPct = ((1_510_000L - 1_620_000L).toFloat() / 1_620_000L.toFloat()) * 100f
      ),
      com.moneyflow.ui.viewmodel.MonthlyTrendItem(
        monthKey = "2026-09",
        monthLabel = "9월",
        fullDisplay = "2026년 9월",
        totalExpense = 1_390_000L,
        transactionCount = 40,
        isCurrentMonth = false,
        changeVsPrevPct = ((1_390_000L - 1_510_000L).toFloat() / 1_510_000L.toFloat()) * 100f
      ),
      com.moneyflow.ui.viewmodel.MonthlyTrendItem(
        monthKey = "2026-10",
        monthLabel = "10월",
        fullDisplay = "2026년 10월",
        totalExpense = 1_420_000L,
        transactionCount = 35,
        isCurrentMonth = true,
        changeVsPrevPct = ((1_420_000L - 1_390_000L).toFloat() / 1_390_000L.toFloat()) * 100f
      )
    )

    assertEquals(6, trendItems.size)

    // Verify 6-month average calculation
    val averageExpense = trendItems.sumOf { it.totalExpense } / trendItems.size
    assertEquals(1_456_666L, averageExpense)

    // Verify current month vs previous month delta
    val currentMonth = trendItems.last()
    assertTrue(currentMonth.isCurrentMonth)
    assertEquals("10월", currentMonth.monthLabel)
    assertNotNull(currentMonth.changeVsPrevPct)
    val pct = currentMonth.changeVsPrevPct!!
    assertEquals(2.158f, pct, 0.01f) // +2.16% increase vs September

    // Verify August vs July (decrease)
    val august = trendItems[3]
    assertNotNull(august.changeVsPrevPct)
    assertTrue(august.changeVsPrevPct!! < 0f) // -6.79% decrease

    // Verify comparison against average
    val diffFromAverage = currentMonth.totalExpense - averageExpense
    assertEquals(-36_666L, diffFromAverage) // 36,666 KRW less than 6-month average (saving money)
  }

  @Test
  fun `test CategoryIconHelper returns designated icons and colors for expense categories`() {
    // Verify expense categories map to distinct icons
    val foodIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("식비")
    val cafeIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("카페/간식")
    val shopIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("쇼핑")
    val transportIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("교통")
    val houseIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("주거/통신")
    val healthIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("의료/건강")
    val leisureIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("문화/여가")
    val martIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("생활/마트")
    val salaryIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("급여")
    val transferIcon = com.moneyflow.ui.components.CategoryIconHelper.getIconForCategory("계좌이체")

    assertNotNull(foodIcon)
    assertNotNull(cafeIcon)
    assertNotNull(shopIcon)
    assertNotNull(transportIcon)
    assertNotNull(houseIcon)
    assertNotNull(healthIcon)
    assertNotNull(leisureIcon)
    assertNotNull(martIcon)
    assertNotNull(salaryIcon)
    assertNotNull(transferIcon)

    // Verify distinct colors
    val foodColor = com.moneyflow.ui.components.CategoryIconHelper.getColorForCategory("식비")
    val salaryColor = com.moneyflow.ui.components.CategoryIconHelper.getColorForCategory("급여")
    assertEquals(0xFFF04452, foodColor)
    assertEquals(0xFF00B074, salaryColor)
  }

  @Test
  fun `test recent transactions fetched from Room database with category classification`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(
        name = "토스통장",
        bankName = "토스뱅크",
        accountType = AccountType.BANK,
        accountNumber = "1000-01",
        balance = 500_000L
      )
    )

    val now = System.currentTimeMillis()

    // Insert multiple transactions with different categories
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 15_000L,
        category = "식비",
        accountId = accId,
        accountName = "토스통장",
        memo = "점심 식사 (국밥)",
        timestamp = now - 1000
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 6_500L,
        category = "카페/간식",
        accountId = accId,
        accountName = "토스통장",
        memo = "스타벅스 아메리카노",
        timestamp = now - 500
      )
    )
    repository.insertTransaction(
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 1_400L,
        category = "교통",
        accountId = accId,
        accountName = "토스통장",
        memo = "지하철 교통비",
        timestamp = now
      )
    )

    // Fetch all transactions from Room database (ordered by timestamp DESC)
    val fetched = repository.allTransactions.first()
    assertEquals(3, fetched.size)

    // Verify latest is the transportation expense
    assertEquals("교통", fetched[0].category)
    assertEquals(1_400L, fetched[0].amount)

    // Verify second is cafe
    assertEquals("카페/간식", fetched[1].category)
    assertEquals(6_500L, fetched[1].amount)

    // Verify third is food
    assertEquals("식비", fetched[2].category)
    assertEquals(15_000L, fetched[2].amount)

    // Total expense sum
    val totalExpense = fetched.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    assertEquals(22_900L, totalExpense)
  }

  @Test
  fun `test manual income and expense transaction entry into Room database with balance update`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(
        name = "주거래통장",
        bankName = "KB국민은행",
        accountType = AccountType.BANK,
        accountNumber = "123-456",
        balance = 1_000_000L
      )
    )

    // 1. Manually add income (e.g. 500,000 KRW bonus)
    val incomeTx = TransactionEntity(
      type = TransactionType.INCOME,
      amount = 500_000L,
      category = "상여금",
      accountId = accId,
      accountName = "주거래통장",
      memo = "프로젝트 보너스",
      paymentMethod = "계좌이체",
      timestamp = System.currentTimeMillis()
    )
    val incomeId = repository.insertTransaction(incomeTx)
    assertTrue(incomeId > 0)

    // Check account balance increased
    val updatedAccAfterIncome = database.accountDao().getAccountById(accId)
    assertEquals(1_500_000L, updatedAccAfterIncome?.balance)

    // 2. Manually add expense (e.g. 80,000 KRW groceries)
    val expenseTx = TransactionEntity(
      type = TransactionType.EXPENSE,
      amount = 80_000L,
      category = "생활/마트",
      accountId = accId,
      accountName = "주거래통장",
      memo = "이마트 장보기",
      paymentMethod = "체크카드",
      timestamp = System.currentTimeMillis()
    )
    val expenseId = repository.insertTransaction(expenseTx)
    assertTrue(expenseId > 0)

    // Check account balance decreased
    val updatedAccAfterExpense = database.accountDao().getAccountById(accId)
    assertEquals(1_420_000L, updatedAccAfterExpense?.balance)

    // Verify transactions in Room
    val all = repository.allTransactions.first()
    assertTrue(all.any { it.memo == "프로젝트 보너스" && it.type == TransactionType.INCOME })
    assertTrue(all.any { it.memo == "이마트 장보기" && it.type == TransactionType.EXPENSE })
  }

  @Test
  fun `test monthly spending limit budget setting in Room DB and progress calculation`() = runBlocking {
    val monthKey = "2026-11"
    val monthlyLimit = 2_000_000L
    val categoryBudgetsJson = """{"식비":600000,"카페/간식":200000,"쇼핑":300000}"""

    // 1. Save monthly budget limit
    repository.saveBudget(monthKey, monthlyLimit, categoryBudgetsJson)

    // 2. Fetch from Room database
    val savedBudget = repository.getBudgetForMonth(monthKey).first()
    assertNotNull(savedBudget)
    assertEquals(monthKey, savedBudget?.monthKey)
    assertEquals(monthlyLimit, savedBudget?.totalBudget)
    assertEquals(categoryBudgetsJson, savedBudget?.categoryBudgetsJson)

    // 3. Test progress calculation logic
    val currentSpent = 1_500_000L
    val progress = currentSpent.toFloat() / monthlyLimit.toFloat()
    val remaining = monthlyLimit - currentSpent
    val isOverBudget = currentSpent > monthlyLimit

    assertEquals(0.75f, progress, 0.001f) // 75% spent
    assertEquals(500_000L, remaining)
    assertFalse(isOverBudget)

    // 4. Test daily allowance calculation based on remaining days
    val daysRemaining = 20
    val dailyAllowance = remaining / daysRemaining
    assertEquals(25_000L, dailyAllowance)
  }

  @Test
  fun `test over budget scenario progress indicator`() = runBlocking {
    val monthlyLimit = 1_800_000L
    val currentSpent = 2_100_000L
    val progress = currentSpent.toFloat() / monthlyLimit.toFloat()
    val remaining = monthlyLimit - currentSpent
    val isOverBudget = currentSpent > monthlyLimit

    assertTrue(isOverBudget)
    assertTrue(progress > 1.0f)
    assertEquals(-300_000L, remaining) // 300,000 KRW over budget
  }

  @Test
  fun `test SpendingCategoryMapper correctly maps food transport housing and others`() {
    assertEquals("식비 (Food)", com.moneyflow.ui.components.SpendingCategoryMapper.getBilingualLabel("식비"))
    assertEquals("교통 (Transport)", com.moneyflow.ui.components.SpendingCategoryMapper.getBilingualLabel("교통"))
    assertEquals("주거·통신 (Housing)", com.moneyflow.ui.components.SpendingCategoryMapper.getBilingualLabel("주거/통신"))
    assertEquals("쇼핑 (Shopping)", com.moneyflow.ui.components.SpendingCategoryMapper.getBilingualLabel("쇼핑"))
    assertEquals("카페·간식 (Cafe)", com.moneyflow.ui.components.SpendingCategoryMapper.getBilingualLabel("카페/간식"))

    assertEquals("Food", com.moneyflow.ui.components.SpendingCategoryMapper.getShortType("식비"))
    assertEquals("Transport", com.moneyflow.ui.components.SpendingCategoryMapper.getShortType("교통"))
    assertEquals("Housing", com.moneyflow.ui.components.SpendingCategoryMapper.getShortType("주거/통신"))
  }

  @Test
  fun `test spending breakdown distribution calculation for monthly expense`() {
    val items = listOf(
      com.moneyflow.ui.viewmodel.CategorySpend(
        category = "식비",
        amount = 500_000L,
        percentage = 0.5f,
        colorHex = 0xFFF04452
      ),
      com.moneyflow.ui.viewmodel.CategorySpend(
        category = "주거/통신",
        amount = 300_000L,
        percentage = 0.3f,
        colorHex = 0xFF00A389
      ),
      com.moneyflow.ui.viewmodel.CategorySpend(
        category = "교통",
        amount = 200_000L,
        percentage = 0.2f,
        colorHex = 0xFF0064FF
      )
    )

    val totalExpense = items.sumOf { it.amount }
    assertEquals(1_000_000L, totalExpense)

    val top = items.maxByOrNull { it.amount }
    assertEquals("식비", top?.category)
    assertEquals(500_000L, top?.amount)
    assertEquals(0.5f, top?.percentage ?: 0f, 0.001f)
  }

  @Test
  fun `test manual capture of unlinked transaction with custom picked date and category persistence`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(
        name = "현금지갑",
        bankName = "지갑",
        accountType = AccountType.CASH,
        accountNumber = "CASH-001",
        balance = 100_000L
      )
    )

    // Simulate custom picked date (e.g. 3 days ago from DatePicker)
    val customPickedCalendar = java.util.Calendar.getInstance().apply {
      add(java.util.Calendar.DAY_OF_YEAR, -3)
      set(java.util.Calendar.HOUR_OF_DAY, 14)
      set(java.util.Calendar.MINUTE, 30)
    }
    val pickedTimestamp = customPickedCalendar.timeInMillis

    // Manually capture cash transaction supplementing automated bank data
    val manualTx = TransactionEntity(
      accountId = accId,
      type = TransactionType.EXPENSE,
      amount = 12_000L,
      category = "식비",
      memo = "전통시장 분식 현금 결제 (자동연동 보완)",
      timestamp = pickedTimestamp,
      paymentMethod = "현금",
      isExcludedFromBudget = false
    )

    val txId = repository.insertTransaction(manualTx)
    val savedTx = repository.getTransactionById(txId)

    assertNotNull(savedTx)
    assertEquals(12_000L, savedTx?.amount)
    assertEquals("식비", savedTx?.category)
    assertEquals("현금", savedTx?.paymentMethod)
    assertEquals("전통시장 분식 현금 결제 (자동연동 보완)", savedTx?.memo)
    assertEquals(pickedTimestamp, savedTx?.timestamp)
    assertFalse(savedTx?.isExcludedFromBudget ?: true)
  }

  @Test
  fun `test manual capture supports split bill dutch pay and second hand market income`() = runBlocking {
    val bankAccId = repository.insertAccount(
      AccountEntity(
        name = "토스뱅크 통장",
        bankName = "토스뱅크",
        accountType = AccountType.BANK,
        accountNumber = "1000-02",
        balance = 1_000_000L
      )
    )

    val todayMillis = System.currentTimeMillis()

    // 1. Manual expense: Dutch pay split payment
    val dutchPayExpense = TransactionEntity(
      accountId = bankAccId,
      type = TransactionType.EXPENSE,
      amount = 15_000L,
      category = "식비",
      memo = "모임 N빵 송금 정산",
      timestamp = todayMillis,
      paymentMethod = "계좌이체"
    )
    repository.insertTransaction(dutchPayExpense)

    // 2. Manual income: Second-hand market direct trade (cash income)
    val secondHandIncome = TransactionEntity(
      accountId = bankAccId,
      type = TransactionType.INCOME,
      amount = 35_000L,
      category = "부수입/중고거래",
      memo = "당근마켓 현금/직거래 입금",
      timestamp = todayMillis,
      paymentMethod = "현금"
    )
    repository.insertTransaction(secondHandIncome)

    val allTransactions = repository.allTransactions.first()
    val dutchPay = allTransactions.find { it.memo.contains("N빵") }
    val carrotTrade = allTransactions.find { it.memo.contains("당근마켓") }

    assertNotNull(dutchPay)
    assertEquals(15_000L, dutchPay?.amount)
    assertEquals(TransactionType.EXPENSE, dutchPay?.type)

    assertNotNull(carrotTrade)
    assertEquals(35_000L, carrotTrade?.amount)
    assertEquals(TransactionType.INCOME, carrotTrade?.type)
    assertEquals("부수입/중고거래", carrotTrade?.category)
  }

  @Test
  fun `test search transactions by keyword across memo, category, and amount`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(name = "국민카드", bankName = "국민은행", accountType = AccountType.CREDIT_CARD, accountNumber = "9999", balance = 0L)
    )
    val now = System.currentTimeMillis()

    repository.insertTransaction(
      TransactionEntity(accountId = accId, type = TransactionType.EXPENSE, amount = 4500L, category = "카페/간식", memo = "스타벅스 아이스 아메리카노", timestamp = now)
    )
    repository.insertTransaction(
      TransactionEntity(accountId = accId, type = TransactionType.EXPENSE, amount = 14000L, category = "교통", memo = "카카오택시 야간할증", timestamp = now)
    )
    repository.insertTransaction(
      TransactionEntity(accountId = accId, type = TransactionType.EXPENSE, amount = 8500L, category = "식비", memo = "맥도날드 빅맥세트", timestamp = now)
    )

    val all = repository.allTransactions.first()

    // 1. Keyword search by memo "스타벅스"
    val queryMemo = "스타벅스"
    val resultsMemo = all.filter { it.memo.contains(queryMemo, ignoreCase = true) }
    assertEquals(1, resultsMemo.size)
    assertEquals("스타벅스 아이스 아메리카노", resultsMemo[0].memo)

    // 2. Keyword search by category "교통"
    val queryCategory = "교통"
    val resultsCat = all.filter { it.category.contains(queryCategory, ignoreCase = true) || it.memo.contains(queryCategory, ignoreCase = true) }
    assertEquals(1, resultsCat.size)
    assertEquals("카카오택시 야간할증", resultsCat[0].memo)

    // 3. Keyword search by amount "14000"
    val queryAmount = "14000"
    val resultsAmt = all.filter { it.amount.toString().contains(queryAmount) }
    assertEquals(1, resultsAmt.size)
    assertEquals("카카오택시 야간할증", resultsAmt[0].memo)
  }

  @Test
  fun `test filter expenses by date range and category`() = runBlocking {
    val accId = repository.insertAccount(
      AccountEntity(name = "토스통장", bankName = "토스뱅크", accountType = AccountType.BANK, accountNumber = "1111", balance = 200_000L)
    )

    val cal = java.util.Calendar.getInstance()

    // Transaction 1: Today
    val todayMillis = cal.timeInMillis
    repository.insertTransaction(
      TransactionEntity(accountId = accId, type = TransactionType.EXPENSE, amount = 10000L, category = "식비", memo = "오늘 점심", timestamp = todayMillis)
    )

    // Transaction 2: 5 days ago (within 7 days)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -5)
    val fiveDaysAgoMillis = cal.timeInMillis
    repository.insertTransaction(
      TransactionEntity(accountId = accId, type = TransactionType.EXPENSE, amount = 20000L, category = "식비", memo = "5일 전 저녁", timestamp = fiveDaysAgoMillis)
    )

    // Transaction 3: 20 days ago (outside 7 days, within 30 days)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -15)
    val twentyDaysAgoMillis = cal.timeInMillis
    repository.insertTransaction(
      TransactionEntity(accountId = accId, type = TransactionType.EXPENSE, amount = 30000L, category = "교통", memo = "20일 전 KTX", timestamp = twentyDaysAgoMillis)
    )

    // Transaction 4: 45 days ago (outside 30 days)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -25)
    val fortyFiveDaysAgoMillis = cal.timeInMillis
    repository.insertTransaction(
      TransactionEntity(accountId = accId, type = TransactionType.EXPENSE, amount = 50000L, category = "쇼핑", memo = "지난달 쇼핑", timestamp = fortyFiveDaysAgoMillis)
    )

    val all = repository.allTransactions.first()

    // Filter by Last 7 Days
    val sevenDaysAgoLimit = java.util.Calendar.getInstance().apply {
      add(java.util.Calendar.DAY_OF_YEAR, -6)
      set(java.util.Calendar.HOUR_OF_DAY, 0)
      set(java.util.Calendar.MINUTE, 0)
      set(java.util.Calendar.SECOND, 0)
    }.timeInMillis
    val last7Days = all.filter { it.timestamp >= sevenDaysAgoLimit }
    assertEquals(2, last7Days.size) // Today and 5 days ago

    // Filter by Category "식비" within Last 7 Days
    val foodInLast7Days = last7Days.filter { it.category == "식비" }
    assertEquals(2, foodInLast7Days.size)
    assertEquals(30000L, foodInLast7Days.sumOf { it.amount })

    // Filter by Custom Date Range: 5 days ago to 25 days ago
    val customStart = twentyDaysAgoMillis - 1000
    val customEnd = fiveDaysAgoMillis + 1000
    val customRangeTxs = all.filter { it.timestamp in customStart..customEnd }
    assertEquals(2, customRangeTxs.size) // 5 days ago and 20 days ago
    assertTrue(customRangeTxs.any { it.memo == "5일 전 저녁" })
    assertTrue(customRangeTxs.any { it.memo == "20일 전 KTX" })
  }
}

