package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
  @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
  fun getAllTransactions(): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
  suspend fun getTransactionById(id: Long): TransactionEntity?

  @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
  fun observeTransactionById(id: Long): Flow<TransactionEntity?>

  @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
  fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId ORDER BY timestamp DESC")
  fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
  fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>

  @Query(
    """
    SELECT * FROM transactions 
    WHERE memo LIKE '%' || :query || '%' 
       OR category LIKE '%' || :query || '%' 
       OR accountName LIKE '%' || :query || '%' 
    ORDER BY timestamp DESC
    """
  )
  fun searchTransactions(query: String): Flow<List<TransactionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTransaction(transaction: TransactionEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(transactions: List<TransactionEntity>)

  @Update
  suspend fun updateTransaction(transaction: TransactionEntity)

  @Delete
  suspend fun deleteTransaction(transaction: TransactionEntity)

  @Query("DELETE FROM transactions WHERE id = :id")
  suspend fun deleteTransactionById(id: Long)

  @Query("SELECT COUNT(*) FROM transactions")
  suspend fun getTransactionCount(): Int
}
