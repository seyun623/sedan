package com.moneyflow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.BudgetEntity
import com.moneyflow.data.model.CategoryBudgetEntity
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity

@Database(
  entities = [
    AccountEntity::class,
    TransactionEntity::class,
    BudgetEntity::class,
    CategoryBudgetEntity::class,
    SavingGoalEntity::class,
    RecurringTransactionEntity::class
  ],
  version = AppDatabase.VERSION,
  exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao
  abstract fun transactionDao(): TransactionDao
  abstract fun budgetDao(): BudgetDao
  abstract fun savingGoalDao(): SavingGoalDao
  abstract fun recurringTransactionDao(): RecurringTransactionDao
  abstract fun backupDao(): BackupDao

  companion object {
    const val VERSION = 3
    const val NAME = "moneyflow_finance.db"
  }
}
