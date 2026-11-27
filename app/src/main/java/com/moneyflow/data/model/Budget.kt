package com.moneyflow.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "budgets",
  indices = [Index(value = ["monthKey"], unique = true)]
)
data class BudgetEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val monthKey: String, // e.g. "2026-10"
  val totalBudget: Long
)

@Entity(
  tableName = "category_budgets",
  primaryKeys = ["monthKey", "category"]
)
data class CategoryBudgetEntity(
  val monthKey: String,
  val category: String,
  val limitAmount: Long
)

@Entity(tableName = "saving_goals")
data class SavingGoalEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val targetAmount: Long,
  val currentAmount: Long,
  val targetDate: Long, // timestamp millis
  val iconEmoji: String = "💰",
  val colorHex: Long = 0xFF0064FF
)

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String, // e.g. "월세 및 관리비", "매월 급여", "넷플릭스 구독"
  val type: TransactionType,
  val amount: Long,
  val category: String,
  val dayOfMonth: Int, // 1..31 (clamped to the month's last day)
  val accountId: Long,
  val accountName: String,
  val memo: String = "",
  val paymentMethod: String = "계좌이체",
  val isActive: Boolean = true,
  /** Last month ("yyyy-MM") for which a transaction was recorded. */
  val lastExecutedMonth: String? = null,
  /** First month ("yyyy-MM") this item applies to. */
  @ColumnInfo(defaultValue = "")
  val startMonth: String = ""
)

data class CategoryBudgetProgress(
  val category: String,
  val budgetLimit: Long,
  val spentAmount: Long,
  val percentage: Float, // spentAmount / budgetLimit
  val remainingAmount: Long,
  val isOverBudget: Boolean,
  val colorHex: Long
)
