package com.moneyflow.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.BudgetEntity
import com.moneyflow.data.model.CategoryBudgetEntity
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
  @Query("SELECT * FROM accounts ORDER BY isFavorite DESC, id ASC")
  fun observeAll(): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts ORDER BY isFavorite DESC, id ASC")
  suspend fun getAll(): List<AccountEntity>

  @Query("SELECT * FROM accounts WHERE id = :id")
  fun observeById(id: Long): Flow<AccountEntity?>

  @Query("SELECT * FROM accounts WHERE id = :id")
  suspend fun getById(id: Long): AccountEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(account: AccountEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

  @Update
  suspend fun update(account: AccountEntity)

  @Query("UPDATE accounts SET isFavorite = NOT isFavorite WHERE id = :accountId")
  suspend fun toggleFavorite(accountId: Long)

  @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :accountId")
  suspend fun adjustBalance(accountId: Long, delta: Long)

  @Delete
  suspend fun delete(account: AccountEntity)

  @Query("SELECT COUNT(*) FROM accounts")
  suspend fun count(): Int
}

@Dao
interface TransactionDao {
  @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
  fun observeAll(): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC, id DESC")
  fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId ORDER BY timestamp DESC, id DESC")
  fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE id = :id")
  suspend fun getById(id: Long): TransactionEntity?

  @Query("SELECT * FROM transactions WHERE id = :id")
  fun observeById(id: Long): Flow<TransactionEntity?>

  @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
  suspend fun countByAccount(accountId: Long): Int

  @Query("SELECT * FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
  suspend fun getByAccount(accountId: Long): List<TransactionEntity>

  @Query("SELECT * FROM transactions WHERE goalId = :goalId")
  suspend fun getByGoal(goalId: Long): List<TransactionEntity>

  @Query("SELECT COUNT(*) FROM transactions WHERE dedupKey = :key")
  suspend fun countByDedupKey(key: String): Int

  /** Auto-captured candidates around a time, used for cross-source duplicate detection. */
  @Query(
    """
    SELECT * FROM transactions
    WHERE type = :type AND amount = :amount
      AND timestamp BETWEEN :from AND :to
      AND source IN ('NOTIFICATION', 'SMS')
    """
  )
  suspend fun findAutoCapturedNear(type: TransactionType, amount: Long, from: Long, to: Long): List<TransactionEntity>

  /** Original purchase candidates for a cancellation (most recent first). */
  @Query(
    """
    SELECT * FROM transactions
    WHERE type = 'EXPENSE' AND amount = :amount AND timestamp BETWEEN :from AND :to
    ORDER BY timestamp DESC
    """
  )
  suspend fun findExpensesForCancel(amount: Long, from: Long, to: Long): List<TransactionEntity>

  /**
   * Category last used for this merchant/sender (memo starting with it), so a category the user
   * corrected once is reused for later auto-captured payments from the same place.
   */
  @Query(
    """
    SELECT category FROM transactions
    WHERE type = :type AND (memo = :merchant OR memo LIKE :merchant || ' (%')
    ORDER BY timestamp DESC, id DESC LIMIT 1
    """
  )
  suspend fun lastCategoryFor(type: TransactionType, merchant: String): String?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(tx: TransactionEntity): Long

  @Update
  suspend fun update(tx: TransactionEntity)

  @Delete
  suspend fun delete(tx: TransactionEntity)

  @Query("UPDATE transactions SET accountName = :name WHERE accountId = :accountId")
  suspend fun renameAccount(accountId: Long, name: String)

  @Query("UPDATE transactions SET toAccountName = :name WHERE toAccountId = :accountId")
  suspend fun renameToAccount(accountId: Long, name: String)

  @Query("SELECT COUNT(*) FROM transactions")
  suspend fun count(): Int
}

@Dao
interface BudgetDao {
  @Query("SELECT * FROM budgets ORDER BY monthKey")
  fun observeAllBudgets(): Flow<List<BudgetEntity>>

  @Query("SELECT * FROM category_budgets")
  fun observeAllCategoryBudgets(): Flow<List<CategoryBudgetEntity>>

  @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
  suspend fun getBudget(monthKey: String): BudgetEntity?

  /** Most recent budget at or before the given month (used to carry budgets forward). */
  @Query("SELECT * FROM budgets WHERE monthKey <= :monthKey ORDER BY monthKey DESC LIMIT 1")
  suspend fun getLatestBudgetUpTo(monthKey: String): BudgetEntity?

  @Query("SELECT * FROM category_budgets WHERE monthKey = :monthKey")
  fun observeCategoryBudgets(monthKey: String): Flow<List<CategoryBudgetEntity>>

  @Query("SELECT * FROM category_budgets WHERE monthKey = :monthKey")
  suspend fun getCategoryBudgets(monthKey: String): List<CategoryBudgetEntity>

  @Query("INSERT INTO budgets(monthKey, totalBudget) VALUES(:monthKey, :total) ON CONFLICT(monthKey) DO UPDATE SET totalBudget = excluded.totalBudget")
  suspend fun upsertBudget(monthKey: String, total: Long)

  @Query("DELETE FROM category_budgets WHERE monthKey = :monthKey")
  suspend fun clearCategoryBudgets(monthKey: String)

  @Upsert
  suspend fun upsertCategoryBudgets(items: List<CategoryBudgetEntity>)
}

@Dao
interface SavingGoalDao {
  @Query("SELECT * FROM saving_goals ORDER BY id ASC")
  fun observeAll(): Flow<List<SavingGoalEntity>>

  @Query("SELECT * FROM saving_goals WHERE id = :id")
  suspend fun getById(id: Long): SavingGoalEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(goal: SavingGoalEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAll(goals: List<SavingGoalEntity>)

  @Update
  suspend fun update(goal: SavingGoalEntity)

  @Query("UPDATE saving_goals SET currentAmount = currentAmount + :delta WHERE id = :goalId")
  suspend fun adjustAmount(goalId: Long, delta: Long)

  @Delete
  suspend fun delete(goal: SavingGoalEntity)
}

@Dao
interface RecurringTransactionDao {
  @Query("SELECT * FROM recurring_transactions ORDER BY dayOfMonth ASC, id ASC")
  fun observeAll(): Flow<List<RecurringTransactionEntity>>

  @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY dayOfMonth ASC, id ASC")
  suspend fun getActive(): List<RecurringTransactionEntity>

  @Query("SELECT * FROM recurring_transactions WHERE id = :id")
  suspend fun getById(id: Long): RecurringTransactionEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(item: RecurringTransactionEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAll(items: List<RecurringTransactionEntity>)

  @Update
  suspend fun update(item: RecurringTransactionEntity)

  @Delete
  suspend fun delete(item: RecurringTransactionEntity)

  /**
   * Marks [monthKey] executed only if it has not been executed yet (compare-and-set).
   * Returns the number of rows changed: 1 means this caller owns the execution.
   */
  @Query(
    """
    UPDATE recurring_transactions SET lastExecutedMonth = :monthKey
    WHERE id = :id AND (lastExecutedMonth IS NULL OR lastExecutedMonth < :monthKey)
    """
  )
  suspend fun markExecutedIfNewer(id: Long, monthKey: String): Int

  @Query("UPDATE recurring_transactions SET isActive = :isActive WHERE id = :id")
  suspend fun setActive(id: Long, isActive: Boolean)

  @Query("UPDATE recurring_transactions SET accountName = :name WHERE accountId = :accountId")
  suspend fun renameAccount(accountId: Long, name: String)

  @Query("DELETE FROM recurring_transactions WHERE accountId = :accountId")
  suspend fun deleteByAccount(accountId: Long)

  @Query("SELECT COUNT(*) FROM recurring_transactions WHERE accountId = :accountId")
  suspend fun countByAccount(accountId: Long): Int
}
