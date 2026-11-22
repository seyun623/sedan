package com.example.data.repository

import com.example.data.local.AccountDao
import com.example.data.local.BudgetDao
import com.example.data.local.RecurringTransactionDao
import com.example.data.local.SavingGoalDao
import com.example.data.local.TransactionDao
import com.example.data.model.AccountEntity
import com.example.data.model.AccountType
import com.example.data.model.BudgetEntity
import com.example.data.model.RecurringTransactionEntity
import com.example.data.model.SavingGoalEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class FinanceRepository(
  private val accountDao: AccountDao,
  private val transactionDao: TransactionDao,
  private val budgetDao: BudgetDao,
  private val savingGoalDao: SavingGoalDao,
  private val recurringTransactionDao: RecurringTransactionDao
) {
  val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
  val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
  val allSavingGoals: Flow<List<SavingGoalEntity>> = savingGoalDao.getAllGoals()
  val allRecurringTransactions: Flow<List<RecurringTransactionEntity>> = recurringTransactionDao.getAllRecurring()

  fun getBudgetForMonth(monthKey: String): Flow<BudgetEntity?> = budgetDao.getBudgetForMonth(monthKey)

  fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> =
    transactionDao.getTransactionsBetween(startTime, endTime)

  suspend fun getTransactionById(id: Long): TransactionEntity? =
    transactionDao.getTransactionById(id)

  fun observeTransactionById(id: Long): Flow<TransactionEntity?> =
    transactionDao.observeTransactionById(id)

  fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
    transactionDao.searchTransactions(query)

  private suspend fun applyTransactionBalanceEffect(tx: TransactionEntity) {
    val account = accountDao.getAccountById(tx.accountId)
    val isFromDebt = account?.isDebt == true

    when (tx.type) {
      TransactionType.EXPENSE -> {
        val delta = if (isFromDebt) tx.amount else -tx.amount
        accountDao.updateBalance(tx.accountId, delta)
      }
      TransactionType.INCOME -> {
        val delta = if (isFromDebt) -tx.amount else tx.amount
        accountDao.updateBalance(tx.accountId, delta)
      }
      TransactionType.TRANSFER -> {
        val fromDelta = if (isFromDebt) tx.amount else -tx.amount
        accountDao.updateBalance(tx.accountId, fromDelta)
        tx.toAccountId?.let { toId ->
          val toAccount = accountDao.getAccountById(toId)
          val toDelta = if (toAccount?.isDebt == true) -tx.amount else tx.amount
          accountDao.updateBalance(toId, toDelta)
        }
      }
    }
  }

  private suspend fun revertTransactionBalanceEffect(tx: TransactionEntity) {
    val account = accountDao.getAccountById(tx.accountId)
    val isFromDebt = account?.isDebt == true

    when (tx.type) {
      TransactionType.EXPENSE -> {
        val delta = if (isFromDebt) -tx.amount else tx.amount
        accountDao.updateBalance(tx.accountId, delta)
      }
      TransactionType.INCOME -> {
        val delta = if (isFromDebt) tx.amount else -tx.amount
        accountDao.updateBalance(tx.accountId, delta)
      }
      TransactionType.TRANSFER -> {
        val fromDelta = if (isFromDebt) -tx.amount else tx.amount
        accountDao.updateBalance(tx.accountId, fromDelta)
        tx.toAccountId?.let { toId ->
          val toAccount = accountDao.getAccountById(toId)
          val toDelta = if (toAccount?.isDebt == true) tx.amount else -tx.amount
          accountDao.updateBalance(toId, toDelta)
        }
      }
    }
  }

  // CREATE
  suspend fun insertTransaction(tx: TransactionEntity): Long {
    val id = transactionDao.insertTransaction(tx)
    applyTransactionBalanceEffect(tx)
    return id
  }

  // UPDATE
  suspend fun updateTransaction(updatedTx: TransactionEntity) {
    val oldTx = transactionDao.getTransactionById(updatedTx.id)
    if (oldTx != null) {
      revertTransactionBalanceEffect(oldTx)
    }
    transactionDao.updateTransaction(updatedTx)
    applyTransactionBalanceEffect(updatedTx)
  }

  // DELETE
  suspend fun deleteTransaction(tx: TransactionEntity) {
    transactionDao.deleteTransaction(tx)
    revertTransactionBalanceEffect(tx)
  }

  suspend fun deleteTransactionById(id: Long) {
    val existing = transactionDao.getTransactionById(id) ?: return
    deleteTransaction(existing)
  }

  suspend fun insertAccount(account: AccountEntity): Long {
    return accountDao.insertAccount(account)
  }

  suspend fun updateAccount(account: AccountEntity) {
    accountDao.updateAccount(account)
  }

  suspend fun deleteAccount(account: AccountEntity) {
    accountDao.deleteAccount(account)
  }

  suspend fun transferBetweenAccounts(
    fromAccount: AccountEntity,
    toAccount: AccountEntity,
    amount: Long,
    memo: String
  ) {
    val tx = TransactionEntity(
      type = TransactionType.TRANSFER,
      amount = amount,
      category = "계좌이체",
      accountId = fromAccount.id,
      accountName = fromAccount.name,
      toAccountId = toAccount.id,
      toAccountName = toAccount.name,
      timestamp = System.currentTimeMillis(),
      memo = if (memo.isBlank()) "${toAccount.name}(으)로 송금" else memo,
      paymentMethod = "계좌이체"
    )
    insertTransaction(tx)
  }

  suspend fun saveBudget(monthKey: String, amount: Long, categoryBudgetsJson: String = "{}") {
    val budget = BudgetEntity(
      monthKey = monthKey,
      totalBudget = amount,
      categoryBudgetsJson = categoryBudgetsJson
    )
    budgetDao.insertBudget(budget)
  }

  suspend fun getActiveRecurring(): List<RecurringTransactionEntity> =
    recurringTransactionDao.getActiveRecurring()

  suspend fun insertRecurring(item: RecurringTransactionEntity): Long =
    recurringTransactionDao.insertRecurring(item)

  suspend fun updateRecurring(item: RecurringTransactionEntity) =
    recurringTransactionDao.updateRecurring(item)

  suspend fun deleteRecurring(item: RecurringTransactionEntity) =
    recurringTransactionDao.deleteRecurring(item)

  suspend fun toggleRecurringActive(id: Long, isActive: Boolean) =
    recurringTransactionDao.toggleActive(id, isActive)

  suspend fun markRecurringExecuted(id: Long, monthKey: String) =
    recurringTransactionDao.markExecuted(id, monthKey)

  suspend fun insertSavingGoal(goal: SavingGoalEntity): Long {
    return savingGoalDao.insertGoal(goal)
  }

  suspend fun addAmountToGoal(goalId: Long, amount: Long, fromAccountId: Long? = null) {
    savingGoalDao.addAmountToGoal(goalId, amount)
    fromAccountId?.let {
      accountDao.updateBalance(it, -amount)
    }
  }

  suspend fun deleteSavingGoal(goal: SavingGoalEntity) {
    savingGoalDao.deleteGoal(goal)
  }

  suspend fun seedInitialDataIfNeeded() {
    if (accountDao.getAccountCount() > 0) return

    // 1. Initial Accounts
    val acc1 = AccountEntity(
      name = "토스뱅크 통장",
      bankName = "토스뱅크",
      accountType = AccountType.BANK,
      accountNumber = "1000-0284-9124",
      balance = 3450000,
      colorHex = 0xFF0064FF,
      isFavorite = true
    )
    val acc2 = AccountEntity(
      name = "카카오 세이프박스",
      bankName = "카카오뱅크",
      accountType = AccountType.SAVINGS,
      accountNumber = "3333-14-8890123",
      balance = 12500000,
      colorHex = 0xFFFEE500,
      isFavorite = true
    )
    val acc3 = AccountEntity(
      name = "신한투자증권 (국내주식/ETF)",
      bankName = "신한투자증권",
      accountType = AccountType.INVESTMENT,
      accountNumber = "270-01-445892",
      balance = 8720000,
      colorHex = 0xFF0046FF,
      isFavorite = true
    )
    val acc4 = AccountEntity(
      name = "KB국민 ONE 입출금통장",
      bankName = "KB국민은행",
      accountType = AccountType.BANK,
      accountNumber = "432102-04-129482",
      balance = 1850000,
      colorHex = 0xFFFFBC00,
      isFavorite = false
    )
    val acc5 = AccountEntity(
      name = "현대카드 ZERO 결제예정",
      bankName = "현대카드",
      accountType = AccountType.CREDIT_CARD,
      accountNumber = "5461-****-****-8291",
      balance = 640000,
      colorHex = 0xFF191F28,
      isFavorite = false,
      isDebt = true
    )
    val acc6 = AccountEntity(
      name = "카카오뱅크 비상금대출",
      bankName = "카카오뱅크",
      accountType = AccountType.LOAN,
      accountNumber = "3333-89-1029384",
      balance = 3000000,
      colorHex = 0xFF7059F6,
      isFavorite = false,
      isDebt = true
    )

    accountDao.insertAll(listOf(acc1, acc2, acc3, acc4, acc5, acc6))

    // 2. Budget for Current Month (2026-10)
    budgetDao.insertBudget(
      BudgetEntity(
        monthKey = "2026-10",
        totalBudget = 1800000
      )
    )

    // 3. Saving Goals
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, 6)
    savingGoalDao.insertAll(
      listOf(
        SavingGoalEntity(
          title = "유럽 배낭여행 자금",
          targetAmount = 4000000,
          currentAmount = 2800000,
          targetDate = cal.timeInMillis,
          iconEmoji = "✈️",
          colorHex = 0xFF0064FF
        ),
        SavingGoalEntity(
          title = "비상금 1,000만원 모으기",
          targetAmount = 10000000,
          currentAmount = 7500000,
          targetDate = cal.timeInMillis + 86400000L * 180,
          iconEmoji = "🛡️",
          colorHex = 0xFF00B074
        ),
        SavingGoalEntity(
          title = "맥북 프로 교체",
          targetAmount = 2500000,
          currentAmount = 1600000,
          targetDate = cal.timeInMillis + 86400000L * 60,
          iconEmoji = "💻",
          colorHex = 0xFF7059F6
        )
      )
    )

    // 4. Initial Realistic Transactions in October 2026
    val now = System.currentTimeMillis()
    val dayMillis = 86400000L

    val seedTransactions = listOf(
      TransactionEntity(
        type = TransactionType.INCOME,
        amount = 3850000,
        category = "급여",
        accountId = 1,
        accountName = "토스뱅크 통장",
        timestamp = now - dayMillis * 2,
        memo = "10월 급여 (주식회사 핀플로우)",
        paymentMethod = "계좌이체"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 6500,
        category = "카페/간식",
        accountId = 1,
        accountName = "토스뱅크 통장",
        timestamp = now - 3600000L * 3,
        memo = "스타벅스 강남점 아이스 아메리카노",
        paymentMethod = "체크카드"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 12000,
        category = "식비",
        accountId = 1,
        accountName = "토스뱅크 통장",
        timestamp = now - 3600000L * 6,
        memo = "점심 김치찌개 정식",
        paymentMethod = "체크카드"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 26500,
        category = "식비",
        accountId = 5,
        accountName = "현대카드 ZERO 결제예정",
        timestamp = now - dayMillis * 1 - 3600000L * 2,
        memo = "배달의민족 (교촌치킨 허니콤보)",
        paymentMethod = "신용카드"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 45800,
        category = "쇼핑",
        accountId = 5,
        accountName = "현대카드 ZERO 결제예정",
        timestamp = now - dayMillis * 2,
        memo = "쿠팡 로켓배송 생필품/비타민",
        paymentMethod = "신용카드"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 68000,
        category = "교통",
        accountId = 1,
        accountName = "토스뱅크 통장",
        timestamp = now - dayMillis * 3,
        memo = "후불교통카드 대중교통 이용",
        paymentMethod = "체크카드"
      ),
      TransactionEntity(
        type = TransactionType.INCOME,
        amount = 28400,
        category = "금융소득/배당",
        accountId = 2,
        accountName = "카카오 세이프박스",
        timestamp = now - dayMillis * 4,
        memo = "세이프박스 월 이자 결산",
        paymentMethod = "계좌이체"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 24900,
        category = "주거/통신",
        accountId = 1,
        accountName = "토스뱅크 통장",
        timestamp = now - dayMillis * 5,
        memo = "넷플릭스 + 유튜브 프리미엄 구독",
        paymentMethod = "체크카드"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 89000,
        category = "생활/마트",
        accountId = 4,
        accountName = "KB국민 ONE 입출금통장",
        timestamp = now - dayMillis * 6,
        memo = "이마트 역삼점 장보기",
        paymentMethod = "체크카드"
      ),
      TransactionEntity(
        type = TransactionType.INCOME,
        amount = 120000,
        category = "부수입/중고거래",
        accountId = 1,
        accountName = "토스뱅크 통장",
        timestamp = now - dayMillis * 7,
        memo = "당근마켓 블루투스 스피커 판매",
        paymentMethod = "계좌이체"
      ),
      TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 35000,
        category = "문화/여가",
        accountId = 1,
        accountName = "토스뱅크 통장",
        timestamp = now - dayMillis * 8,
        memo = "CGV 영화관람 및 팝콘",
        paymentMethod = "체크카드"
      ),
      TransactionEntity(
        type = TransactionType.TRANSFER,
        amount = 500000,
        category = "저축/투자이체",
        accountId = 1,
        accountName = "토스뱅크 통장",
        toAccountId = 2,
        toAccountName = "카카오 세이프박스",
        timestamp = now - dayMillis * 9,
        memo = "월 정기 비상금 저축",
        paymentMethod = "계좌이체"
      )
    )

    transactionDao.insertAll(seedTransactions)

    // 5. Seed Initial Recurring Transactions
    val seedRecurring = listOf(
      RecurringTransactionEntity(
        title = "월 정기 급여",
        type = TransactionType.INCOME,
        amount = 3850000,
        category = "급여",
        dayOfMonth = 25,
        accountId = 1,
        accountName = "토스뱅크 통장",
        memo = "주식회사 핀플로우 급여 입금",
        paymentMethod = "계좌이체",
        isActive = true,
        lastExecutedMonth = "2026-10"
      ),
      RecurringTransactionEntity(
        title = "월세 및 오피스텔 관리비",
        type = TransactionType.EXPENSE,
        amount = 550000,
        category = "주거/통신",
        dayOfMonth = 10,
        accountId = 1,
        accountName = "토스뱅크 통장",
        memo = "역삼동 오피스텔 월세 송금",
        paymentMethod = "계좌이체",
        isActive = true,
        lastExecutedMonth = "2026-10"
      ),
      RecurringTransactionEntity(
        title = "넷플릭스 + 유튜브 구독료",
        type = TransactionType.EXPENSE,
        amount = 24900,
        category = "주거/통신",
        dayOfMonth = 1,
        accountId = 1,
        accountName = "토스뱅크 통장",
        memo = "정기 구독 서비스 결제",
        paymentMethod = "체크카드",
        isActive = true,
        lastExecutedMonth = "2026-10"
      ),
      RecurringTransactionEntity(
        title = "청약통장 월 자동이체",
        type = TransactionType.EXPENSE,
        amount = 100000,
        category = "금융/보험",
        dayOfMonth = 20,
        accountId = 1,
        accountName = "토스뱅크 통장",
        memo = "주택청약종합저축 납입",
        paymentMethod = "계좌이체",
        isActive = true,
        lastExecutedMonth = null
      )
    )
    recurringTransactionDao.insertAll(seedRecurring)
  }
}
