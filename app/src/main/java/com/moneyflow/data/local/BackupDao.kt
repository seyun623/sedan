package com.moneyflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.BudgetEntity
import com.moneyflow.data.model.CategoryBudgetEntity
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity

/** Whole-table reads and writes used only by backup / restore. */
@Dao
interface BackupDao {
  @Query("SELECT * FROM accounts ORDER BY id") suspend fun accounts(): List<AccountEntity>
  @Query("SELECT * FROM transactions ORDER BY id") suspend fun transactions(): List<TransactionEntity>
  @Query("SELECT * FROM budgets ORDER BY id") suspend fun budgets(): List<BudgetEntity>
  @Query("SELECT * FROM category_budgets") suspend fun categoryBudgets(): List<CategoryBudgetEntity>
  @Query("SELECT * FROM saving_goals ORDER BY id") suspend fun goals(): List<SavingGoalEntity>
  @Query("SELECT * FROM recurring_transactions ORDER BY id") suspend fun recurring(): List<RecurringTransactionEntity>

  @Query("DELETE FROM transactions") suspend fun clearTransactions()
  @Query("DELETE FROM recurring_transactions") suspend fun clearRecurring()
  @Query("DELETE FROM saving_goals") suspend fun clearGoals()
  @Query("DELETE FROM category_budgets") suspend fun clearCategoryBudgets()
  @Query("DELETE FROM budgets") suspend fun clearBudgets()
  @Query("DELETE FROM accounts") suspend fun clearAccounts()

  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccounts(items: List<AccountEntity>)
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransactions(items: List<TransactionEntity>)
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBudgets(items: List<BudgetEntity>)
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategoryBudgets(items: List<CategoryBudgetEntity>)
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoals(items: List<SavingGoalEntity>)
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecurring(items: List<RecurringTransactionEntity>)
}
