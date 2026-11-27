package com.moneyflow.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.moneyflow.data.model.CategoryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
  @Query("SELECT * FROM category_rules ORDER BY keyword") fun observeAll(): Flow<List<CategoryRuleEntity>>
  @Query("SELECT * FROM category_rules") suspend fun getAll(): List<CategoryRuleEntity>
  @Query("SELECT * FROM category_rules WHERE keyword = :keyword") suspend fun byKeyword(keyword: String): CategoryRuleEntity?
  @Upsert suspend fun upsert(rule: CategoryRuleEntity): Long
  @Delete suspend fun delete(rule: CategoryRuleEntity)
  @Query("DELETE FROM category_rules") suspend fun clear()
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(items: List<CategoryRuleEntity>)
}
