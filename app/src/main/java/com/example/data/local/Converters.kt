package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.AccountType
import com.example.data.model.TransactionType

class Converters {
  @TypeConverter
  fun fromAccountType(type: AccountType?): String? = type?.name

  @TypeConverter
  fun toAccountType(value: String?): AccountType? =
    value?.let { runCatching { AccountType.valueOf(it) }.getOrDefault(AccountType.BANK) }

  @TypeConverter
  fun fromTransactionType(type: TransactionType?): String? = type?.name

  @TypeConverter
  fun toTransactionType(value: String?): TransactionType? =
    value?.let { runCatching { TransactionType.valueOf(it) }.getOrDefault(TransactionType.EXPENSE) }
}
