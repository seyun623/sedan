package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import com.moneyflow.domain.capture.PaymentMessageParser
import com.moneyflow.domain.cardimport.PerformanceInfo
import com.moneyflow.domain.cardimport.StatementItem
import java.security.MessageDigest
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** One statement line prepared for the preview screen. */
data class ImportRow(
  val item: StatementItem,
  val category: String,
  /** A transaction with the same amount on the same day (±1) already exists in the account. */
  val isDuplicate: Boolean
) {
  val selectedByDefault: Boolean get() = !isDuplicate && !item.isCancel
}

data class ImportResult(val added: Int, val cancelled: Int, val skipped: Int)

/** Saved "실적" figure read from a card company screen, per account. */
data class CardPerformance(val label: String, val amount: Long, val fetchedAt: Long)

@Singleton
class CardImportRepository @Inject constructor(
  private val db: AppDatabase,
  private val ledger: LedgerRepository,
  private val settings: SettingsRepository,
  private val clock: AppClock
) {
  private val rules = PaymentMessageParser(clock.zone)

  /** Card accounts first, preferring one whose name matches the card company. */
  fun suggestAccount(provider: String, accounts: List<AccountEntity>): AccountEntity? {
    val core = provider.removeSuffix("카드").removeSuffix("은행")
    val cards = accounts.filter { it.accountType == AccountType.CREDIT_CARD }
    return cards.firstOrNull { it.bankName.contains(core) || it.name.contains(core) }
      ?: accounts.firstOrNull { it.bankName.contains(core) || it.name.contains(core) }
      ?: cards.firstOrNull()
  }

  suspend fun review(items: List<StatementItem>, accountId: Long): List<ImportRow> {
    if (items.isEmpty()) return emptyList()
    val zone = clock.zone
    val from = MonthKeys.dayStartMillis(items.minOf { it.date }.minusDays(1), zone)
    val to = MonthKeys.dayEndMillis(items.maxOf { it.date }.plusDays(1), zone)
    val existing = db.transactionDao().getForAccountBetween(accountId, from, to).filter { it.type == TransactionType.EXPENSE }
    val used = mutableSetOf<Long>()
    return items.map { item ->
      val match = existing.firstOrNull { tx ->
        tx.id !in used && tx.amount == item.amount &&
          kotlin.math.abs(MonthKeys.toLocalDate(tx.timestamp, zone).toEpochDay() - item.date.toEpochDay()) <= 1
      }
      if (match != null && !item.isCancel) used += match.id
      ImportRow(item, categoryFor(item.merchant), isDuplicate = match != null && !item.isCancel)
    }
  }

  private suspend fun categoryFor(merchant: String): String =
    db.transactionDao().lastCategoryFor(TransactionType.EXPENSE, merchant)
      ?.takeIf { it in FinanceCategories.categoriesFor(TransactionType.EXPENSE) }
      ?: rules.classify(merchant, merchant)

  /** Adds the selected purchases; selected cancellations remove the matching purchase. */
  suspend fun import(rows: List<ImportRow>, account: AccountEntity): ImportResult {
    var added = 0
    var cancelled = 0
    var skipped = 0
    val zone = clock.zone
    for ((index, row) in rows.withIndex()) {
      val item = row.item
      val millis = item.date.atTime(item.time ?: LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
      if (item.isCancel) {
        val removed = db.withTransaction {
          val candidates = db.transactionDao().getForAccountBetween(
            account.id, millis - CANCEL_LOOKBACK_MS, millis + 24 * 3_600_000L
          ).filter { it.type == TransactionType.EXPENSE && it.amount == item.amount }
          val original = candidates.firstOrNull { sameMerchant(it.memo, item.merchant) } ?: candidates.maxByOrNull { it.timestamp }
          original?.let { ledger.deleteWithEffects(it) } != null
        }
        if (removed) cancelled++ else skipped++
        continue
      }
      val key = dedupKey(account.id, item, index)
      if (db.transactionDao().countByDedupKey(key) > 0) { skipped++; continue }
      ledger.addTransaction(
        TransactionEntity(
          type = TransactionType.EXPENSE,
          amount = item.amount,
          category = row.category,
          accountId = account.id,
          timestamp = millis,
          memo = item.merchant + (item.installmentMonths?.let { " (할부 ${it}개월)" } ?: ""),
          paymentMethod = "카드",
          source = TransactionSource.CARD_IMPORT,
          dedupKey = key
        )
      )
      added++
    }
    return ImportResult(added, cancelled, skipped)
  }

  suspend fun savePerformance(accountId: Long, info: PerformanceInfo) =
    settings.setCardPerformance(accountId, CardPerformance(info.label, info.amount, clock.nowMillis()))

  private fun sameMerchant(memo: String, merchant: String): Boolean {
    val a = memo.replace(" ", "")
    val b = merchant.replace(" ", "")
    return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
  }

  private fun dedupKey(accountId: Long, item: StatementItem, index: Int): String {
    // The index keeps two identical purchases on the same day apart within one import.
    val raw = "card|$accountId|${item.date}|${item.time}|${item.amount}|${item.merchant.replace(" ", "")}|$index"
    return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
  }

  private companion object {
    const val CANCEL_LOOKBACK_MS = 90L * 24 * 3_600_000
  }
}
