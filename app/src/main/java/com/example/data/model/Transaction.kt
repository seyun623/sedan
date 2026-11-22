package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType(val title: String) {
  EXPENSE("지출"),
  INCOME("수입"),
  TRANSFER("이체")
}

object FinanceCategories {
  val expenseCategories = listOf(
    "식비" to 0xFFF04452,
    "카페/간식" to 0xFFFF8A00,
    "쇼핑" to 0xFF7059F6,
    "교통" to 0xFF0064FF,
    "주거/통신" to 0xFF00A389,
    "금융/보험" to 0xFF3182F6,
    "의료/건강" to 0xFFE03131,
    "문화/여가" to 0xFF9C36B5,
    "생활/마트" to 0xFF2F9E44,
    "기타지출" to 0xFF8B95A1
  )

  val incomeCategories = listOf(
    "급여" to 0xFF00B074,
    "상여금" to 0xFF00C788,
    "금융소득/배당" to 0xFF3182F6,
    "용돈" to 0xFFFF8A00,
    "부수입/중고거래" to 0xFF7059F6,
    "기타수입" to 0xFF8B95A1
  )

  val transferCategories = listOf(
    "계좌이체" to 0xFF0064FF,
    "저축/투자이체" to 0xFF00B074,
    "카드대금납부" to 0xFF7059F6
  )

  fun getColorForCategory(category: String): Long {
    val all = expenseCategories + incomeCategories + transferCategories
    return all.firstOrNull { it.first == category }?.second ?: 0xFF8B95A1
  }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val type: TransactionType,
  val amount: Long, // in KRW
  val category: String,
  val accountId: Long,
  val accountName: String = "",
  val toAccountId: Long? = null,
  val toAccountName: String? = null,
  val timestamp: Long, // Epoch millis
  val memo: String,
  val paymentMethod: String = "체크카드",
  val isExcludedFromBudget: Boolean = false
)
