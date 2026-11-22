package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.AccountEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.RecurringTransactionEntity
import com.example.data.model.SavingGoalEntity
import com.example.data.model.TransactionEntity

@Database(
  entities = [
    AccountEntity::class,
    TransactionEntity::class,
    BudgetEntity::class,
    SavingGoalEntity::class,
    RecurringTransactionEntity::class
  ],
  version = 2,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao
  abstract fun transactionDao(): TransactionDao
  abstract fun budgetDao(): BudgetDao
  abstract fun savingGoalDao(): SavingGoalDao
  abstract fun recurringTransactionDao(): RecurringTransactionDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "moneyflow_finance.db"
        )
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
