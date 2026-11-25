package com.moneyflow.domain.capture

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.CategoryRules
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.InstallmentSplitter
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class CaptureChannel { NOTIFICATION, SMS }

sealed interface CaptureOutcome {
  data class Recorded(val transactionId: Long, val payment: ParsedPayment, val account: AccountEntity) : CaptureOutcome
  data class CancelledOriginal(val removed: TransactionEntity) : CaptureOutcome
  /** Foreign-currency payment: the user was asked to enter the KRW amount. */
  data class NeedsKrwAmount(val prompt: ForeignPaymentPrompt) : CaptureOutcome
  data class Skipped(val reason: String) : CaptureOutcome
}

/**
 * Turns an incoming notification / SMS into a ledger transaction.
 *
 * Safety rules:
 * - Notifications are accepted only from known financial apps (see [FinancialApps]); messengers are
 *   always rejected. SMS must name a card company or bank.
 * - The same payment is stored once even if it arrives via SMS, the SMS app's notification and the
 *   card app's push: a content-based dedup key plus a ±3 minute same-amount check.
 * - "승인취소" removes the matching purchase instead of adding another expense.
 */
@Singleton
class AutoCaptureProcessor @Inject constructor(
  private val db: AppDatabase,
  private val ledger: LedgerRepository,
  private val settings: SettingsRepository,
  private val clock: AppClock,
  private val notifier: CaptureNotifier
) {
  private val parser = PaymentMessageParser(clock.zone)

  /** Parse only (used by the settings screen's test box). */
  fun preview(text: String): ParseResult = parser.parse("", text, clock.nowMillis())

  suspend fun process(
    channel: CaptureChannel,
    origin: String,
    originLabel: String?,
    title: String,
    body: String,
    receivedAt: Long
  ): CaptureOutcome {
    val s = settings.current()
    when (channel) {
      CaptureChannel.NOTIFICATION -> {
        if (!s.notificationCaptureEnabled) return CaptureOutcome.Skipped("알림 자동 등록 꺼짐")
        if (!FinancialApps.isAllowed(origin, originLabel)) return CaptureOutcome.Skipped("금융 앱이 아님")
      }
      CaptureChannel.SMS -> if (!s.smsCaptureEnabled) return CaptureOutcome.Skipped("문자 자동 등록 꺼짐")
    }

    val rules = db.ruleDao().getAll()
    CategoryRules.ignores(rules, title, body)?.let { return CaptureOutcome.Skipped("무시 규칙: ${it.keyword}") }

    val result = parser.parse(title, body, receivedAt)
    val payment = when (result) {
      is ParseResult.Ignored -> return CaptureOutcome.Skipped(result.reason)
      is ParseResult.Success -> result.payment
    }
    if (channel == CaptureChannel.SMS && payment.provider == null) {
      return CaptureOutcome.Skipped("카드사/은행 이름이 없는 문자")
    }
    val timestamp = payment.paidAt ?: receivedAt
    if (payment.kind == PaymentKind.FOREIGN_CURRENCY) return foreignPayment(payment, timestamp, s, rules)

    return db.withTransaction {
      if (payment.kind == PaymentKind.CANCELLATION) {
        // The same cancellation can arrive by SMS and by app push: handle it once (±3 minutes).
        fun cancelKey(minute: Long) = "cancel|" + sha("${payment.amount}|${payment.merchant.replace(" ", "")}|$minute")
        val minute = timestamp / 60_000
        if ((minute - 3..minute + 3).any { cancelKey(it) in s.sentCardAlerts }) {
          return@withTransaction CaptureOutcome.Skipped("이미 처리한 취소")
        }
        var outcome = cancelOriginal(payment, timestamp)
        // No matching purchase in the ledger (bought before the app, or entered differently):
        // keep the money as a refund so spending still goes down.
        if (outcome is CaptureOutcome.Skipped && payment.merchant != UNKNOWN_MERCHANT) {
          val account = pickAccount(payment, s.autoCaptureDefaultAccountId)
          if (account != null) {
            val refund = TransactionEntity(
              type = TransactionType.REFUND,
              amount = payment.amount,
              category = CategoryRules.categoryFor(rules, TransactionType.EXPENSE, payment.merchant)
                ?: db.transactionDao().lastCategoryFor(TransactionType.EXPENSE, payment.merchant)
                  ?.takeIf { it in com.moneyflow.data.model.FinanceCategories.categoriesFor(TransactionType.EXPENSE) }
                ?: payment.category,
              accountId = account.id,
              timestamp = timestamp,
              memo = "${payment.merchant} (승인취소)",
              paymentMethod = if (account.accountType == AccountType.CREDIT_CARD) "카드" else "계좌이체",
              source = if (channel == CaptureChannel.SMS) TransactionSource.SMS else TransactionSource.NOTIFICATION,
              dedupKey = "refund|" + cancelKey(minute)
            )
            outcome = CaptureOutcome.Recorded(ledger.insertWithEffects(refund), payment, account)
          }
        }
        if (outcome !is CaptureOutcome.Skipped) settings.markAlertsSent(listOf(cancelKey(minute)))
        return@withTransaction outcome
      }

      val key = dedupKey(payment, timestamp)
      if (db.transactionDao().countByDedupKey(key) > 0) {
        return@withTransaction CaptureOutcome.Skipped("이미 등록된 결제")
      }
      val near = db.transactionDao().findAutoCapturedNear(
        payment.type, payment.amount, timestamp - DUPLICATE_WINDOW_MS, timestamp + DUPLICATE_WINDOW_MS
      )
      if (near.isNotEmpty()) return@withTransaction CaptureOutcome.Skipped("같은 결제가 다른 경로로 이미 등록됨")
      // An installment purchase already split into monthly parts by the other channel.
      if (payment.installmentMonths != null && splitPurchaseNear(payment.amount, timestamp) != null) {
        return@withTransaction CaptureOutcome.Skipped("같은 할부 결제가 다른 경로로 이미 등록됨")
      }

      var account = pickAccount(payment, s.autoCaptureDefaultAccountId)
        ?: return@withTransaction CaptureOutcome.Skipped("등록할 계좌가 없음")
      // A card bill moves money from a bank account to the card (lowers what the card owes).
      var billTarget: AccountEntity? = null
      if (payment.type == TransactionType.TRANSFER) {
        val accounts = db.accountDao().getAll()
        val card = findCard(accounts, payment.billCard ?: payment.provider?.takeIf { it.endsWith("카드") })
        if (account.isDebt) {
          // Pushed by the card app: the paying bank account is unknown; use the default one.
          billTarget = account
          account = accounts.firstOrNull { it.id == s.autoCaptureDefaultAccountId && !it.isDebt }
            ?: return@withTransaction CaptureOutcome.Skipped("카드 대금을 낸 통장을 알 수 없음")
        } else {
          billTarget = card
        }
      }

      // A user rule wins; otherwise reuse the category last used for this merchant/sender.
      val category = CategoryRules.categoryFor(rules, payment.type, payment.merchant)
        ?: db.transactionDao().lastCategoryFor(payment.type, payment.merchant)
          ?.takeIf { it in com.moneyflow.data.model.FinanceCategories.categoriesFor(payment.type) }
        ?: payment.category
      val memo = buildString {
        append(payment.merchant)
        payment.installmentMonths?.let { append(" (할부 ${it}개월)") }
      }
      val tx =
        TransactionEntity(
          type = payment.type,
          amount = payment.amount,
          category = category,
          accountId = account.id,
          timestamp = timestamp,
          memo = memo,
          paymentMethod = when {
            account.accountType == AccountType.CREDIT_CARD -> "카드"
            payment.type == TransactionType.EXPENSE && payment.provider?.contains("페이") == true -> "간편결제"
            else -> "계좌이체"
          },
          source = if (channel == CaptureChannel.SMS) TransactionSource.SMS else TransactionSource.NOTIFICATION,
          dedupKey = key,
          toAccountId = billTarget?.id,
          toAccountName = billTarget?.name
        )
      val months = payment.installmentMonths
      val id = if (s.splitInstallments && months != null && months > 1 && payment.type == TransactionType.EXPENSE) {
        InstallmentSplitter.split(tx, months, clock.zone).map { ledger.insertWithEffects(it) }.first()
      } else {
        ledger.insertWithEffects(tx)
      }
      CaptureOutcome.Recorded(id, payment, account)
    }
  }

  /** Must run inside a DB transaction. */
  private suspend fun cancelOriginal(payment: ParsedPayment, timestamp: Long): CaptureOutcome {
    val candidates = db.transactionDao().findExpensesForCancel(
      payment.amount, timestamp - CANCEL_LOOKBACK_MS, timestamp + DUPLICATE_WINDOW_MS
    )
    // Only the same place; without a readable place, only when exactly one purchase of that amount was made that day.
    val original = candidates.firstOrNull { it.source.isAutoCaptured && sameMerchant(it.memo, payment.merchant) }
      ?: candidates.filter { it.source.isAutoCaptured && kotlin.math.abs(it.timestamp - timestamp) <= DAY_MS }
        .singleOrNull()?.takeIf { payment.merchant == UNKNOWN_MERCHANT }
    if (original != null) {
      ledger.deleteWithEffects(original)
      return CaptureOutcome.CancelledOriginal(original)
    }
    // A purchase that was split into monthly installment parts: remove all parts.
    val firstPart = db.transactionDao().getBetween(timestamp - CANCEL_LOOKBACK_MS, timestamp + DUPLICATE_WINDOW_MS)
      .filter { it.source.isAutoCaptured && InstallmentSplitter.partOf(it.memo)?.first == 1 && sameMerchant(it.memo, payment.merchant) }
      .firstOrNull { ledger.installmentGroup(it).sumOf { p -> p.amount } == payment.amount }
      ?: return CaptureOutcome.Skipped("취소할 원거래를 찾지 못함")
    ledger.installmentGroup(firstPart).forEach { ledger.deleteWithEffects(it) }
    return CaptureOutcome.CancelledOriginal(firstPart)
  }

  /** Asks the user (once per payment) to enter the KRW amount of a foreign-currency payment. */
  private suspend fun foreignPayment(
    payment: ParsedPayment,
    timestamp: Long,
    s: com.moneyflow.data.repository.AppSettings,
    rules: List<com.moneyflow.data.model.CategoryRuleEntity>
  ): CaptureOutcome {
    // The same payment can arrive by SMS and by app push a little apart: look ±3 minutes.
    fun keyAt(minute: Long) = "foreign|" + sha("${payment.merchant.replace(" ", "")}|${payment.foreignAmount}|$minute")
    val minute = timestamp / 60_000
    if ((minute - 3..minute + 3).any { keyAt(it) in s.sentCardAlerts }) return CaptureOutcome.Skipped("이미 알린 외화 결제")
    val key = keyAt(minute)
    val account = pickAccount(payment, s.autoCaptureDefaultAccountId)
    val prompt = ForeignPaymentPrompt(
      key = key,
      merchant = payment.merchant,
      foreignAmount = payment.foreignAmount.orEmpty(),
      provider = payment.provider,
      prefill = FormPrefill(
        type = TransactionType.EXPENSE,
        memo = "${payment.merchant} (${payment.foreignAmount.orEmpty()})".replace(" ()", ""),
        category = CategoryRules.categoryFor(rules, TransactionType.EXPENSE, payment.merchant) ?: payment.category,
        accountId = account?.id,
        timestamp = timestamp
      )
    )
    settings.markAlertsSent(listOf(key))
    notifier.foreignPayment(prompt)
    return CaptureOutcome.NeedsKrwAmount(prompt)
  }

  /** First part of an auto-captured installment purchase of [total] recorded around [timestamp]. */
  private suspend fun splitPurchaseNear(total: Long, timestamp: Long): TransactionEntity? =
    db.transactionDao().getBetween(timestamp - DUPLICATE_WINDOW_MS, timestamp + DUPLICATE_WINDOW_MS)
      .filter { it.source.isAutoCaptured && InstallmentSplitter.partOf(it.memo)?.first == 1 }
      .firstOrNull { ledger.installmentGroup(it).sumOf { p -> p.amount } == total }

  /** The card account of [cardCompany] ("삼성카드"), if the user has one. */
  private fun findCard(accounts: List<AccountEntity>, cardCompany: String?): AccountEntity? {
    val core = cardCompany?.removeSuffix("카드")?.removePrefix("KB")?.takeIf { it.length >= 2 } ?: return null
    return accounts.filter { it.accountType == AccountType.CREDIT_CARD || it.isDebt }
      .firstOrNull { it.name.contains(core) || it.bankName.contains(core) }
  }

  private fun sameMerchant(memo: String, merchant: String): Boolean {
    val a = memo.replace(" ", "")
    val b = merchant.replace(" ", "")
    return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a.take(b.length)))
  }

  private suspend fun pickAccount(payment: ParsedPayment, defaultId: Long?): AccountEntity? {
    val accounts = db.accountDao().getAll()
    if (accounts.isEmpty()) return null
    payment.cardLast4?.filter { it.isDigit() }?.takeIf { it.length >= 3 }?.let { digits ->
      accounts.firstOrNull { it.accountNumber.filter(Char::isDigit).endsWith(digits) }?.let { return it }
    }
    payment.provider?.let { provider ->
      val core = provider.removeSuffix("카드").removeSuffix("은행").removeSuffix("뱅크").removeSuffix("페이")
      val wantsCard = provider.endsWith("카드")
      val matches = accounts.filter {
        it.name.contains(provider, true) || it.bankName.contains(provider, true) ||
          (core.length >= 2 && (it.bankName.contains(core, true) || it.name.contains(core, true)))
      }
      (matches.firstOrNull { (it.accountType == AccountType.CREDIT_CARD) == wantsCard } ?: matches.firstOrNull())
        ?.let { return it }
    }
    defaultId?.let { id -> accounts.firstOrNull { it.id == id }?.let { return it } }
    return null
  }

  private fun dedupKey(p: ParsedPayment, timestamp: Long): String {
    val minute = timestamp / 60_000
    return sha("${p.type}|${p.amount}|${p.merchant.replace(" ", "")}|$minute")
  }

  private fun sha(raw: String): String =
    MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

  companion object {
    const val DUPLICATE_WINDOW_MS = 3 * 60_000L
    const val CANCEL_LOOKBACK_MS = 60L * 24 * 60 * 60_000
    private const val DAY_MS = 24 * 60 * 60_000L
    private const val UNKNOWN_MERCHANT = "알 수 없는 가맹점"
  }
}

/** Package allow/deny lists for the notification listener. */
object FinancialApps {
  /** Messengers and SMS apps are never read (SMS is handled by the SMS receiver). */
  private val BLOCKED = setOf(
    "com.kakao.talk", "jp.naver.line.android", "com.facebook.orca", "org.telegram.messenger",
    "com.whatsapp", "com.instagram.android", "com.discord", "com.Slack",
    "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms"
  )

  /** Known Korean card / bank / pay apps. */
  val KNOWN = setOf(
    "com.shcard.smartpay", "com.shinhan.sbanking", "com.shinhancard.smartshinhan",
    "com.kbcard.cxh.appcard", "com.kbstar.kbbank", "com.kbstar.liivbank", "com.kbcard.kbkookmincard",
    "com.hyundaicard.appcard", "kr.co.samsungcard.mpocket", "com.lcacApp", "com.lottecard.lcap",
    "com.wooricard.smartapp", "com.wooribank.smart.npib", "com.hanaskcard.paycla", "com.hanabank.ebk.channel.android.hananbank",
    "nh.smart.nhallonepay", "nh.smart.banking", "com.nh.cashcardapp", "kvp.jjy.MispAndroid320",
    "com.ibk.android.ionebank", "com.kakaobank.channel", "com.kbankwith.smartbank", "viva.republica.toss",
    "com.kakaopay.app", "com.nhn.android.search", "com.naverfin.payapp", "com.samsung.android.spay",
    "com.bccard.android.mobile", "com.citibank.cardapp", "com.scbank.ma30", "com.epost.psf.sdsi", "com.knb.psb", "com.dgb.mobilebank"
  )

  private val FINANCIAL_LABEL = Regex("카드|은행|뱅크|뱅킹|페이|증권|Card|Bank|Pay", RegexOption.IGNORE_CASE)

  /** Known apps, or apps whose launcher label looks like a card/bank/pay app. */
  fun isAllowed(packageName: String, appLabel: String?): Boolean =
    packageName !in BLOCKED &&
      (packageName in KNOWN || (appLabel != null && FINANCIAL_LABEL.containsMatchIn(appLabel)))
}
