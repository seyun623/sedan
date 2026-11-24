package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.AppClock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LedgerResult {
  data object Ok : LedgerResult
  data class Rejected(val reason: String) : LedgerResult
}

/**
 * Accounts, transactions and saving goals. Every write that changes a balance runs in a
 * single database transaction so the ledger and the account balances can never diverge.
 *
 * Balance convention: for debt accounts (cards, loans) the balance is the amount owed,
 * so spending on them increases the balance and paying them off decreases it.
 */
@Singleton
class LedgerRepository @Inject constructor(
  private val db: AppDatabase,
  private val clock: AppClock
) {
  private val accountDao = db.accountDao()
  private val txDao = db.transactionDao()
  private val goalDao = db.savingGoalDao()
  private val recurringDao = db.recurringTransactionDao()

  // ---- Observation -------------------------------------------------------------------------

  val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
  val transactions: Flow<List<TransactionEntity>> = txDao.observeAll()
  val goals: Flow<List<SavingGoalEntity>> = goalDao.observeAll()

  fun observeAccount(id: Long): Flow<AccountEntity?> = accountDao.observeById(id)
  fun observeTransaction(id: Long): Flow<TransactionEntity?> = txDao.observeById(id)
  fun observeAccountTransactions(id: Long): Flow<List<TransactionEntity>> = txDao.observeByAccount(id)
  fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = txDao.observeRecent(limit)

  suspend fun getAccounts(): List<AccountEntity> = accountDao.getAll()
  suspend fun getTransaction(id: Long): TransactionEntity? = txDao.getById(id)

  // ---- Transactions ------------------------------------------------------------------------

  suspend fun addTransaction(tx: TransactionEntity): Long = db.withTransaction {
    insertWithEffects(tx)
  }

  suspend fun updateTransaction(updated: TransactionEntity): LedgerResult = db.withTransaction {
    val old = txDao.getById(updated.id) ?: return@withTransaction LedgerResult.Rejected("거래를 찾을 수 없습니다.")
    validate(updated)?.let { return@withTransaction LedgerResult.Rejected(it) }
    revertEffects(old)
    // A goal deposit has no receiving account; never let an edit attach one (it would credit
    // an account and the goal with the same money).
    val safe = if (old.goalId != null && updated.goalId != null) updated.copy(toAccountId = null) else updated
    val normalized = withAccountNames(safe)
    txDao.update(normalized)
    applyEffects(normalized)
    LedgerResult.Ok
  }

  /** Records an installment purchase as one expense per month; returns the first part's id. */
  suspend fun addInstallments(tx: TransactionEntity, months: Int): Long = db.withTransaction {
    com.moneyflow.domain.InstallmentSplitter.split(tx, months, clock.zone).map { insertWithEffects(it) }.first()
  }

  /** All parts of the split installment purchase [tx] belongs to (just [tx] if it isn't one). */
  suspend fun installmentGroup(tx: TransactionEntity): List<TransactionEntity> {
    if (com.moneyflow.domain.InstallmentSplitter.partOf(tx.memo) == null) return listOf(tx)
    val group = com.moneyflow.domain.InstallmentSplitter.groupKey(tx.dedupKey) ?: return listOf(tx)
    return txDao.getInstallmentGroup(group).ifEmpty { listOf(tx) }
  }

  /** Deletes every part of a split installment purchase; returns how many were deleted. */
  suspend fun deleteInstallmentGroup(tx: TransactionEntity): Int = db.withTransaction {
    val parts = installmentGroup(tx)
    parts.forEach { p -> txDao.getById(p.id)?.let { deleteWithEffects(it) } }
    parts.size
  }

  suspend fun deleteTransaction(tx: TransactionEntity): TransactionEntity? = db.withTransaction {
    val current = txDao.getById(tx.id) ?: return@withTransaction null
    txDao.delete(current)
    revertEffects(current)
    current
  }

  /** Re-inserts a previously deleted transaction (undo). */
  suspend fun restoreTransaction(tx: TransactionEntity): Long = db.withTransaction {
    val goalStillExists = tx.goalId?.let { goalDao.getById(it) != null } ?: false
    insertWithEffects(tx.copy(id = 0, goalId = if (goalStillExists) tx.goalId else null))
  }

  suspend fun transfer(from: AccountEntity, to: AccountEntity, amount: Long, memo: String, timestamp: Long = clock.nowMillis()): Long =
    addTransaction(
      TransactionEntity(
        type = TransactionType.TRANSFER,
        amount = amount,
        category = if (to.isDebt) FinanceCategories.CARD_PAYMENT else FinanceCategories.ACCOUNT_TRANSFER,
        accountId = from.id,
        toAccountId = to.id,
        timestamp = timestamp,
        memo = memo.ifBlank { "${to.name}(으)로 이체" },
        paymentMethod = "계좌이체"
      )
    )

  /** Must be called inside a DB transaction. */
  internal suspend fun insertWithEffects(tx: TransactionEntity): Long {
    validate(tx)?.let { throw IllegalArgumentException(it) }
    val normalized = withAccountNames(tx)
    val id = txDao.insert(normalized)
    applyEffects(normalized)
    return id
  }

  /** Must be called inside a DB transaction. */
  internal suspend fun deleteWithEffects(tx: TransactionEntity) {
    txDao.delete(tx)
    revertEffects(tx)
  }

  private suspend fun validate(tx: TransactionEntity): String? {
    if (tx.amount <= 0) return "금액은 0보다 커야 합니다."
    if (accountDao.getById(tx.accountId) == null) return "계좌를 찾을 수 없습니다."
    if (tx.type == TransactionType.TRANSFER && tx.toAccountId != null) {
      if (tx.toAccountId == tx.accountId) return "같은 계좌로는 이체할 수 없습니다."
      if (accountDao.getById(tx.toAccountId) == null) return "받는 계좌를 찾을 수 없습니다."
    }
    return null
  }

  private suspend fun withAccountNames(tx: TransactionEntity): TransactionEntity {
    val from = accountDao.getById(tx.accountId)
    val to = tx.toAccountId?.let { accountDao.getById(it) }
    return tx.copy(
      accountName = from?.name ?: tx.accountName,
      toAccountName = if (tx.type == TransactionType.TRANSFER) to?.name ?: tx.toAccountName else null,
      toAccountId = if (tx.type == TransactionType.TRANSFER) tx.toAccountId else null
    )
  }

  private suspend fun applyEffects(tx: TransactionEntity) = effects(tx, sign = 1)
  private suspend fun revertEffects(tx: TransactionEntity) = effects(tx, sign = -1)

  private suspend fun effects(tx: TransactionEntity, sign: Int) {
    val amount = tx.amount * sign
    val from = accountDao.getById(tx.accountId)
    if (from != null) {
      val delta = when (tx.type) {
        TransactionType.EXPENSE, TransactionType.TRANSFER -> if (from.isDebt) amount else -amount
        TransactionType.INCOME -> if (from.isDebt) -amount else amount
      }
      accountDao.adjustBalance(from.id, delta)
    }
    if (tx.type == TransactionType.TRANSFER) {
      tx.toAccountId?.let { accountDao.getById(it) }?.let { to ->
        accountDao.adjustBalance(to.id, if (to.isDebt) -amount else amount)
      }
      tx.goalId?.let { goalDao.adjustAmount(it, amount) }
    }
  }

  /** Gives several transactions a new category (the balances don't change). */
  suspend fun recategorize(ids: List<Long>, category: String) {
    if (ids.isNotEmpty()) txDao.setCategory(ids, category)
  }

  /** Deletes several transactions in one database transaction (all or nothing). */
  suspend fun deleteTransactions(txs: List<TransactionEntity>) = db.withTransaction {
    txs.forEach { deleteWithEffects(it) }
  }

  // ---- Accounts ----------------------------------------------------------------------------

  suspend fun addAccount(account: AccountEntity): Long = accountDao.insert(account.copy(id = 0))

  /**
   * Updates account details. If [balanceShownWhenEditing] is given and the edited balance equals
   * it (the user did not touch the balance), the current stored balance is kept, so transactions
   * recorded while the edit form was open are not overwritten.
   */
  suspend fun updateAccount(updated: AccountEntity, balanceShownWhenEditing: Long? = null): LedgerResult = db.withTransaction {
    val old = accountDao.getById(updated.id) ?: return@withTransaction LedgerResult.Rejected("계좌를 찾을 수 없습니다.")
    if (old.isDebt != updated.isDebt && txDao.countByAccount(old.id) > 0) {
      return@withTransaction LedgerResult.Rejected("거래 내역이 있는 계좌는 자산/부채 구분을 바꿀 수 없습니다.")
    }
    val balance = if (balanceShownWhenEditing != null && updated.balance == balanceShownWhenEditing) old.balance else updated.balance
    accountDao.update(updated.copy(balance = balance))
    if (old.name != updated.name) {
      txDao.renameAccount(updated.id, updated.name)
      txDao.renameToAccount(updated.id, updated.name)
      recurringDao.renameAccount(updated.id, updated.name)
    }
    LedgerResult.Ok
  }

  suspend fun toggleFavorite(accountId: Long) = accountDao.toggleFavorite(accountId)

  data class AccountUsage(val transactionCount: Int, val recurringCount: Int) {
    val isUsed: Boolean get() = transactionCount > 0 || recurringCount > 0
  }

  suspend fun accountUsage(accountId: Long) =
    AccountUsage(txDao.countByAccount(accountId), recurringDao.countByAccount(accountId))

  /**
   * Deletes an account. If it is used by transactions or recurring items, the call is rejected
   * unless [deleteRelated] is true, in which case those are deleted too (reverting their effect
   * on other accounts and goals).
   */
  suspend fun deleteAccount(accountId: Long, deleteRelated: Boolean): LedgerResult = db.withTransaction {
    val account = accountDao.getById(accountId) ?: return@withTransaction LedgerResult.Ok
    val related = txDao.getByAccount(accountId)
    val recurringCount = recurringDao.countByAccount(accountId)
    if ((related.isNotEmpty() || recurringCount > 0) && !deleteRelated) {
      return@withTransaction LedgerResult.Rejected("이 계좌를 사용하는 거래 ${related.size}건, 고정 거래 ${recurringCount}건이 있습니다.")
    }
    related.forEach { tx ->
      txDao.delete(tx)
      revertEffects(tx)
    }
    recurringDao.deleteByAccount(accountId)
    db.cardDao().deleteProfile(accountId)
    db.cardDao().deleteBenefitsOf(accountId)
    accountDao.delete(account)
    LedgerResult.Ok
  }

  // ---- Saving goals ------------------------------------------------------------------------

  suspend fun addGoal(goal: SavingGoalEntity): Long = goalDao.insert(goal.copy(id = 0))

  suspend fun updateGoal(goal: SavingGoalEntity) = goalDao.update(goal)

  /** Deletes a goal; deposit transactions are kept as plain savings transfers. */
  suspend fun deleteGoal(goal: SavingGoalEntity) = db.withTransaction {
    txDao.getByGoal(goal.id).forEach { txDao.update(it.copy(goalId = null)) }
    goalDao.delete(goal)
  }

  /** Moves money from an account into a goal, recorded as a savings transfer. */
  suspend fun depositToGoal(goal: SavingGoalEntity, fromAccountId: Long, amount: Long): Long =
    addTransaction(
      TransactionEntity(
        type = TransactionType.TRANSFER,
        amount = amount,
        category = FinanceCategories.SAVING_TRANSFER,
        accountId = fromAccountId,
        timestamp = clock.nowMillis(),
        memo = "${goal.iconEmoji} ${goal.title} 저축",
        paymentMethod = "계좌이체",
        source = TransactionSource.GOAL,
        goalId = goal.id
      )
    )
}
