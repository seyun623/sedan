package com.moneyflow.data.local

import androidx.room.TypeConverter
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.TransactionType

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
