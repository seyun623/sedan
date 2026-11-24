package com.moneyflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moneyflow.data.model.FamilyMemberEntity
import com.moneyflow.data.model.FamilyTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
  @Query("SELECT * FROM family_members ORDER BY id") fun observeMembers(): Flow<List<FamilyMemberEntity>>
  @Query("SELECT * FROM family_members WHERE deviceId = :deviceId LIMIT 1") suspend fun byDevice(deviceId: String): FamilyMemberEntity?
  @Insert suspend fun insertMember(m: FamilyMemberEntity): Long
  @Query("UPDATE family_members SET name = :name, backupCreatedAt = :createdAt, importedAt = :importedAt WHERE id = :id")
  suspend fun updateMember(id: Long, name: String, createdAt: Long, importedAt: Long)
  @Query("DELETE FROM family_members WHERE id = :id") suspend fun deleteMember(id: Long)

  @Query("SELECT * FROM family_transactions WHERE timestamp BETWEEN :start AND :end")
  fun observeBetween(start: Long, end: Long): Flow<List<FamilyTransactionEntity>>
  @Query("SELECT COUNT(*) FROM family_transactions WHERE memberId = :memberId") suspend fun count(memberId: Long): Int
  @Query("DELETE FROM family_transactions WHERE memberId = :memberId") suspend fun clearTransactions(memberId: Long)
  @Insert suspend fun insertTransactions(items: List<FamilyTransactionEntity>)

  @Query("SELECT * FROM family_goals") fun observeGoals(): Flow<List<com.moneyflow.data.model.FamilyGoalEntity>>
  @Query("DELETE FROM family_goals WHERE memberId = :memberId") suspend fun clearGoals(memberId: Long)
  @Insert suspend fun insertGoals(items: List<com.moneyflow.data.model.FamilyGoalEntity>)
}
