package com.moneyflow.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneyflow.data.model.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {
  @Query("SELECT * FROM recurring_transactions ORDER BY dayOfMonth ASC")
  fun getAllRecurring(): Flow<List<RecurringTransactionEntity>>

  @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY dayOfMonth ASC")
  suspend fun getActiveRecurring(): List<RecurringTransactionEntity>

  @Query("SELECT * FROM recurring_transactions WHERE id = :id LIMIT 1")
  suspend fun getRecurringById(id: Long): RecurringTransactionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRecurring(item: RecurringTransactionEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(items: List<RecurringTransactionEntity>)

  @Update
  suspend fun updateRecurring(item: RecurringTransactionEntity)

  @Delete
  suspend fun deleteRecurring(item: RecurringTransactionEntity)

  @Query("UPDATE recurring_transactions SET lastExecutedMonth = :monthKey WHERE id = :id")
  suspend fun markExecuted(id: Long, monthKey: String)

  @Query("UPDATE recurring_transactions SET isActive = :isActive WHERE id = :id")
  suspend fun toggleActive(id: Long, isActive: Boolean)

  @Query("SELECT COUNT(*) FROM recurring_transactions")
  suspend fun getRecurringCount(): Int
}
