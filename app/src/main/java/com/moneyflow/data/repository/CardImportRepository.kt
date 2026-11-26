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
  val isDuplicate: Boolean,
  /** Bought and cancelled within the same statement: neither needs recording. */
  val cancelledInStatement: Boolean = false
) {
  val selectedByDefault: Boolean get() = !isDuplicate && !item.isCancel && !cancelledInStatement
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

  /** An account named after the card company / bank (null when there is none). */
  fun namedAccount(provider: String, accounts: List<AccountEntity>): AccountEntity? {
    // "KB국민카드" ↔ an account named "국민카드", "NH농협카드" ↔ "농협카드".
    fun norm(s: String) = s.replace(Regex("^(KB|NH|IBK|BC)|\\s|WON", RegexOption.IGNORE_CASE), "")
    val core = norm(provider).removeSuffix("카드").removeSuffix("은행").removeSuffix("뱅크")
    if (core.length < 2) return null
    val isBank = provider.endsWith("은행") || provider.endsWith("뱅크")
    val named = accounts.filter { norm(it.bankName).contains(core) || norm(it.name).contains(core) }
    return named.firstOrNull { (it.accountType == AccountType.CREDIT_CARD) != isBank } ?: named.firstOrNull()
  }

  /** Makes the card account for a card company that has none yet ("삼성카드"). */
  suspend fun createCardAccount(provider: String): AccountEntity {
    val fresh = AccountEntity(name = provider, bankName = provider, accountType = AccountType.CREDIT_CARD, balance = 0, isDebt = true)
    return fresh.copy(id = db.accountDao().insert(fresh))
  }

  /** Card accounts first, preferring one whose name matches the card company. */
  fun suggestAccount(provider: String, accounts: List<AccountEntity>): AccountEntity? {
    val core = provider.removeSuffix("카드").removeSuffix("은행").removeSuffix("뱅크")
    val isBank = provider.endsWith("은행") || provider.endsWith("뱅크")
    val preferred = accounts.filter { (it.accountType == AccountType.CREDIT_CARD) != isBank }
    val named = { a: AccountEntity -> core.isNotEmpty() && (a.bankName.contains(core) || a.name.contains(core)) }
    return preferred.firstOrNull(named)
      ?: accounts.firstOrNull(named)
      ?: preferred.firstOrNull()
      ?: accounts.firstOrNull()
  }

  suspend fun review(items: List<StatementItem>, accountId: Long): List<ImportRow> {
    if (items.isEmpty()) return emptyList()
    val zone = clock.zone
    val from = MonthKeys.dayStartMillis(items.minOf { it.date }.minusDays(1), zone)
    val to = MonthKeys.dayEndMillis(items.maxOf { it.date }.plusDays(1), zone)
    val existing = db.transactionDao().getForAccountBetween(accountId, from, to)
    // Purchases already split into monthly installment parts: compare with the whole amount.
    val splitTotals = existing.filter { com.moneyflow.domain.InstallmentSplitter.partOf(it.memo)?.first == 1 }
      .associate { it.id to ledger.installmentGroup(it).sumOf { p -> p.amount } }
    val used = mutableSetOf<Long>()
    val paired = pairCancellations(items)
    return items.mapIndexed { index, item ->
      val wanted = if (item.isIncome) TransactionType.INCOME else TransactionType.EXPENSE
      val match = existing.firstOrNull { tx ->
        tx.id !in used && tx.type == wanted && (tx.amount == item.amount || splitTotals[tx.id] == item.amount) &&
          kotlin.math.abs(MonthKeys.toLocalDate(tx.timestamp, zone).toEpochDay() - item.date.toEpochDay()) <= 1
      }
      if (match != null && !item.isCancel) used += match.id
      ImportRow(
        item, if (item.isIncome) incomeCategoryFor(item.merchant) else categoryFor(item.merchant),
        isDuplicate = match != null && !item.isCancel,
        cancelledInStatement = index in paired
      )
    }
  }

  private suspend fun categoryFor(merchant: String): String =
    com.moneyflow.data.model.CategoryRules.categoryFor(db.ruleDao().getAll(), TransactionType.EXPENSE, merchant)
      ?: db.transactionDao().lastCategoryFor(TransactionType.EXPENSE, merchant)
      ?.takeIf { it in FinanceCategories.categoriesFor(TransactionType.EXPENSE) }
      ?: rules.classify(merchant, merchant)

  private suspend fun incomeCategoryFor(sender: String): String =
    com.moneyflow.data.model.CategoryRules.categoryFor(db.ruleDao().getAll(), TransactionType.INCOME, sender)
      ?: db.transactionDao().lastCategoryFor(TransactionType.INCOME, sender)
        ?.takeIf { it in FinanceCategories.categoriesFor(TransactionType.INCOME) }
      ?: when {
        Regex("급여|월급").containsMatchIn(sender) -> FinanceCategories.SALARY
        Regex("이자|배당").containsMatchIn(sender) -> "금융소득/배당"
        else -> FinanceCategories.OTHER_INCOME
      }

  /** Adds the selected purchases; selected cancellations remove the matching purchase. */
  suspend fun import(rows: List<ImportRow>, account: AccountEntity, splitInstallments: Boolean = false): ImportResult {
    var added = 0
    var cancelled = 0
    var skipped = 0
    val zone = clock.zone
    // Identical rows (same day, time, amount, place) are told apart by how many came before.
    val seenRows = HashMap<String, Int>()
    for (row in rows) {
      val item = row.item
      val millis = item.date.atTime(item.time ?: LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
      if (item.isCancel) {
        val removed = db.withTransaction {
          val candidates = db.transactionDao().getForAccountBetween(
            account.id, millis - CANCEL_LOOKBACK_MS, millis + 24 * 3_600_000L
          ).filter { it.type == TransactionType.EXPENSE && it.amount == item.amount }
          // Same place only; otherwise just when a single purchase of that amount was made that day.
          val original = candidates.firstOrNull { sameMerchant(it.memo, item.merchant) }
            ?: candidates.filter { kotlin.math.abs(it.timestamp - millis) <= 24 * 3_600_000L }.singleOrNull()
          if (original != null) {
            ledger.deleteWithEffects(original)
            true
          } else {
            // The purchase isn't in the ledger (e.g. from before using the app): record the money back
            // as a refund so this card's spending still goes down. Imported once only.
            val key = "refund-" + dedupKey(account.id, item, 0)
            if (db.transactionDao().countByDedupKey(key) == 0) {
              ledger.insertWithEffects(
                TransactionEntity(
                  type = TransactionType.REFUND, amount = item.amount, category = categoryFor(item.merchant),
                  accountId = account.id, timestamp = millis, memo = "${item.merchant} (취소)",
                  paymentMethod = "카드", source = TransactionSource.CARD_IMPORT, dedupKey = key
                )
              )
              true
            } else false
          }
        }
        if (removed) cancelled++ else skipped++
        continue
      }
      val content = "${item.date}|${item.time}|${item.amount}|${item.merchant.replace(" ", "")}|${item.isIncome}"
      val occurrence = seenRows.merge(content, 1, Int::plus)!! - 1
      val key = dedupKey(account.id, item, occurrence)
      if (db.transactionDao().countByDedupKey(key) > 0) { skipped++; continue }
      // Also skip a row already imported before (older versions keyed rows by list position).
      val sameMoment = db.transactionDao().getForAccountBetween(account.id, millis, millis)
        .count { it.amount == item.amount && it.source == TransactionSource.CARD_IMPORT && sameMerchant(it.memo, item.merchant) }
      if (sameMoment > occurrence) { skipped++; continue }
      val tx = TransactionEntity(
        type = if (item.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
        amount = item.amount,
        category = row.category,
        accountId = account.id,
        timestamp = millis,
        memo = item.merchant + (item.installmentMonths?.takeIf { !item.isIncome }?.let { " (할부 ${it}개월)" } ?: ""),
        paymentMethod = when {
          item.isIncome -> "계좌이체"
          account.accountType == AccountType.CREDIT_CARD -> "카드"
          else -> "체크카드"
        },
        source = TransactionSource.CARD_IMPORT,
        dedupKey = key
      )
      val months = item.installmentMonths ?: 0
      if (splitInstallments && months > 1 && !item.isIncome) ledger.addInstallments(tx, months) else ledger.addTransaction(tx)
      added++
    }
    return ImportResult(added, cancelled, skipped)
  }

  suspend fun saveBill(accountId: Long, info: com.moneyflow.domain.card.BillInfo) =
    CardBills.save(db, settings, accountId, info, "카드사 화면", clock.nowMillis())

  suspend fun savePoints(accountId: Long, info: com.moneyflow.domain.card.PointInfo) =
    settings.setCardPoint(accountId, com.moneyflow.domain.card.CardPoint(info.label, info.amount, clock.nowMillis()))

  suspend fun savePerformance(accountId: Long, info: PerformanceInfo) =
    settings.setCardPerformance(accountId, CardPerformance(info.label, info.amount, clock.nowMillis()))

  private fun sameMerchant(memo: String, merchant: String): Boolean {
    val a = memo.replace(" ", "")
    val b = merchant.replace(" ", "")
    return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
  }

  private fun dedupKey(accountId: Long, item: StatementItem, index: Int): String {
    // The occurrence number keeps identical purchases on the same day apart.
    val raw = "card|$accountId|${item.date}|${item.time}|${item.amount}|${item.merchant.replace(" ", "")}|${if (item.isIncome) "in" else ""}$index"
    return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
  }

  companion object {
    /**
     * Rows of a purchase and its cancellation in the same statement (same place and amount, the
     * cancellation on or after the purchase): both are left unselected.
     */
    fun pairCancellations(items: List<StatementItem>): Set<Int> {
      val out = mutableSetOf<Int>()
      fun norm(s: String) = s.replace(" ", "").lowercase()
      items.forEachIndexed { ci, c ->
        if (!c.isCancel) return@forEachIndexed
        val pi = items.indices.firstOrNull { i ->
          i !in out && !items[i].isCancel && !items[i].isIncome && items[i].amount == c.amount &&
            norm(items[i].merchant) == norm(c.merchant) && !items[i].date.isAfter(c.date)
        } ?: return@forEachIndexed
        out += pi
        out += ci
      }
      return out
    }

    const val CANCEL_LOOKBACK_MS = 90L * 24 * 3_600_000
  }
}
