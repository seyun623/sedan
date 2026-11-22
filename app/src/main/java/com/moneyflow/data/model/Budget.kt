package com.moneyflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val monthKey: String, // e.g. "2026-10"
  val totalBudget: Long, // e.g. 1,800,000 KRW
  val categoryBudgetsJson: String = "{}" // Category-specific budget allocations in JSON
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
  val dayOfMonth: Int, // 1..31
  val accountId: Long,
  val accountName: String,
  val memo: String = "",
  val paymentMethod: String = "계좌이체",
  val isActive: Boolean = true,
  val lastExecutedMonth: String? = null // e.g. "2026-10"
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
