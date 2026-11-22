package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountType(val displayName: String) {
  BANK("입출금"),
  SAVINGS("예적금"),
  INVESTMENT("증권/투자"),
  CREDIT_CARD("신용/체크카드"),
  LOAN("대출"),
  CASH("현금")
}

@Entity(tableName = "accounts")
data class AccountEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val bankName: String,
  val accountType: AccountType,
  val accountNumber: String = "",
  val balance: Long, // Balance in KRW (for debt/loans, positive number represents debt amount)
  val colorHex: Long = 0xFF0064FF,
  val isFavorite: Boolean = false,
  val isDebt: Boolean = false // If true, counted as liability in net worth calculation
)
