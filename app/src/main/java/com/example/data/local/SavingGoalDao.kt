package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SavingGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingGoalDao {
  @Query("SELECT * FROM saving_goals ORDER BY id ASC")
  fun getAllGoals(): Flow<List<SavingGoalEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGoal(goal: SavingGoalEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(goals: List<SavingGoalEntity>)

  @Update
  suspend fun updateGoal(goal: SavingGoalEntity)

  @Query("UPDATE saving_goals SET currentAmount = currentAmount + :amount WHERE id = :goalId")
  suspend fun addAmountToGoal(goalId: Long, amount: Long)

  @Delete
  suspend fun deleteGoal(goal: SavingGoalEntity)

  @Query("SELECT COUNT(*) FROM saving_goals")
  suspend fun getGoalCount(): Int
}
