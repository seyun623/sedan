package com.moneyflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneyflow.data.model.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
  @Query("SELECT * FROM budgets WHERE monthKey = :monthKey LIMIT 1")
  fun getBudgetForMonth(monthKey: String): Flow<BudgetEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBudget(budget: BudgetEntity): Long

  @Update
  suspend fun updateBudget(budget: BudgetEntity)
}
