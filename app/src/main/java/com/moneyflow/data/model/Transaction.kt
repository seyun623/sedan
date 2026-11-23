package com.moneyflow.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType(val title: String) {
  EXPENSE("지출"),
  INCOME("수입"),
  TRANSFER("이체")
}

/** Where a transaction came from. Stored as its name. */
enum class TransactionSource(val label: String) {
  MANUAL("직접 입력"),
  RECURRING("고정 거래"),
  NOTIFICATION("알림 자동 등록"),
  SMS("문자 자동 등록"),
  GOAL("목표 저축"),
  CARD_IMPORT("카드사 가져오기");

  val isAutoCaptured: Boolean get() = this == NOTIFICATION || this == SMS
}

object FinanceCategories {
  const val OTHER_EXPENSE = "기타지출"
  const val OTHER_INCOME = "기타수입"
  const val SALARY = "급여"
  const val FINANCE_INSURANCE = "금융/보험"
  const val ACCOUNT_TRANSFER = "계좌이체"
  const val SAVING_TRANSFER = "저축/투자이체"
  const val CARD_PAYMENT = "카드대금납부"

  val expenseCategories = listOf(
    "식비" to 0xFFF04452,
    "카페/간식" to 0xFFFF8A00,
    "쇼핑" to 0xFF7059F6,
    "교통" to 0xFF0064FF,
    "주거/통신" to 0xFF00A389,
    FINANCE_INSURANCE to 0xFF3182F6,
    "의료/건강" to 0xFFE03131,
    "문화/여가" to 0xFF9C36B5,
    "생활/마트" to 0xFF2F9E44,
    OTHER_EXPENSE to 0xFF8B95A1
  )

  val incomeCategories = listOf(
    SALARY to 0xFF00B074,
    "상여금" to 0xFF00C788,
    "금융소득/배당" to 0xFF3182F6,
    "용돈" to 0xFFFF8A00,
    "부수입/중고거래" to 0xFF7059F6,
    OTHER_INCOME to 0xFF8B95A1
  )

  val transferCategories = listOf(
    ACCOUNT_TRANSFER to 0xFF0064FF,
    SAVING_TRANSFER to 0xFF00B074,
    CARD_PAYMENT to 0xFF7059F6
  )

  fun categoriesFor(type: TransactionType): List<String> = when (type) {
    TransactionType.EXPENSE -> expenseCategories.map { it.first }
    TransactionType.INCOME -> incomeCategories.map { it.first }
    TransactionType.TRANSFER -> transferCategories.map { it.first }
  }

  fun getColorForCategory(category: String): Long {
    val all = expenseCategories + incomeCategories + transferCategories
    return all.firstOrNull { it.first == category }?.second ?: 0xFF8B95A1
  }
}

@Entity(
  tableName = "transactions",
  indices = [
    Index("timestamp"),
    Index("accountId"),
    Index("toAccountId"),
    Index(value = ["dedupKey"], unique = true)
  ]
)
data class TransactionEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val type: TransactionType,
  val amount: Long, // in KRW, always > 0
  val category: String,
  val accountId: Long,
  /** Snapshot of the account name; kept in sync on rename, retained if the account is deleted. */
  val accountName: String = "",
  val toAccountId: Long? = null,
  val toAccountName: String? = null,
  val timestamp: Long, // Epoch millis
  val memo: String,
  val paymentMethod: String = "체크카드",
  val isExcludedFromBudget: Boolean = false,
  @ColumnInfo(defaultValue = "MANUAL")
  val source: TransactionSource = TransactionSource.MANUAL,
  /** Unique key for auto-captured transactions (prevents the same notification being stored twice). */
  val dedupKey: String? = null,
  val recurringId: Long? = null,
  val goalId: Long? = null
)
