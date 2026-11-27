package com.moneyflow.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
  @Query("SELECT * FROM card_profiles") fun observeProfiles(): Flow<List<CardProfileEntity>>
  @Query("SELECT * FROM card_profiles") suspend fun getProfiles(): List<CardProfileEntity>
  @Query("SELECT * FROM card_profiles WHERE accountId = :accountId") fun observeProfile(accountId: Long): Flow<CardProfileEntity?>
  @Upsert suspend fun upsertProfile(profile: CardProfileEntity)
  @Query("DELETE FROM card_profiles WHERE accountId = :accountId") suspend fun deleteProfile(accountId: Long)

  @Query("SELECT * FROM card_benefits ORDER BY id") fun observeBenefits(): Flow<List<CardBenefitEntity>>
  @Query("SELECT * FROM card_benefits ORDER BY id") suspend fun getBenefits(): List<CardBenefitEntity>
  @Query("SELECT * FROM card_benefits WHERE accountId = :accountId ORDER BY id") fun observeBenefits(accountId: Long): Flow<List<CardBenefitEntity>>
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBenefit(benefit: CardBenefitEntity): Long
  @Update suspend fun updateBenefit(benefit: CardBenefitEntity)
  @Delete suspend fun deleteBenefit(benefit: CardBenefitEntity)
  @Query("DELETE FROM card_benefits WHERE accountId = :accountId") suspend fun deleteBenefitsOf(accountId: Long)

  // Backup / restore
  @Query("DELETE FROM card_profiles") suspend fun clearProfiles()
  @Query("DELETE FROM card_benefits") suspend fun clearBenefits()
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProfiles(items: List<CardProfileEntity>)
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBenefits(items: List<CardBenefitEntity>)
}
