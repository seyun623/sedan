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

enum class CaptureChannel {
  NOTIFICATION, SMS,
  /** "시험해 보기": every check runs, nothing is saved. */
  TEST
}

sealed interface CaptureOutcome {
  data class Recorded(val transactionId: Long, val payment: ParsedPayment, val account: AccountEntity, val newAccount: Boolean = false) : CaptureOutcome
  data class CancelledOriginal(val removed: TransactionEntity) : CaptureOutcome
  /** Foreign-currency payment: the user was asked to enter the KRW amount. */
  data class NeedsKrwAmount(val prompt: ForeignPaymentPrompt) : CaptureOutcome
  data class Skipped(val reason: String) : CaptureOutcome
  /** A card company's monthly "결제 예정 금액" notice: kept on the card account (not a payment). */
  data class BillNoticed(val account: AccountEntity, val bill: com.moneyflow.domain.card.BillInfo) : CaptureOutcome
  /** Test run: this is what would be recorded. */
  data class WouldRecord(val payment: ParsedPayment, val account: AccountEntity, val newAccount: Boolean = false) : CaptureOutcome
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
  private val notifier: CaptureNotifier,
  private val log: CaptureLog? = null
) {
  private val parser = PaymentMessageParser(clock.zone)

  /** Parse only (used by the settings screen's test box). */
  fun preview(text: String): ParseResult = parser.parse("", text, clock.nowMillis())

  /** Processes a message and writes the outcome to the diagnostic log. */
  suspend fun process(
    channel: CaptureChannel,
    origin: String,
    originLabel: String?,
    title: String,
    body: String,
    receivedAt: Long
  ): CaptureOutcome {
    val outcome = try {
      decide(channel, origin, originLabel, title, body, receivedAt)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      com.moneyflow.util.ProblemLog.record("자동 등록", e)
      CaptureOutcome.Skipped("처리 중 오류 (설정 → 문제 보고)")
    }
    log?.let { l ->
      // Ordinary chats from KakaoTalk / text apps are not logged at all.
      val gateBlocked = channel == CaptureChannel.NOTIFICATION && FinancialApps.gate(origin, originLabel, title) == FinancialApps.Gate.BLOCKED
      val looksFinancial = parser.detectProvider("$title $body") != null
      // Ads and app news ("결제/입출금 문구가 없음" without a card company) would bury the real ones.
      val noise = outcome is CaptureOutcome.Skipped && (
        outcome.reason.startsWith("광고") ||
          (!looksFinancial && outcome.reason.startsWith("결제/입출금 문구가 없음"))
        )
      if (!(gateBlocked && !looksFinancial) && !noise) {
        val (ok, result) = CaptureLog.describe(outcome)
        l.add(
          CaptureLogEntry(
            time = clock.nowMillis(),
            channel = when (channel) { CaptureChannel.NOTIFICATION -> "알림"; CaptureChannel.SMS -> "문자"; CaptureChannel.TEST -> "시험" },
            source = originLabel ?: if (channel == CaptureChannel.SMS) "문자" else origin,
            sender = CaptureLog.maskSender(if (channel == CaptureChannel.SMS) origin else title),
            recorded = ok,
            result = result,
            preview = if (looksFinancial) CaptureLog.maskPreview(body) else ""
          )
        )
      }
    }
    return outcome
  }

  private suspend fun decide(
    channel: CaptureChannel,
    origin: String,
    originLabel: String?,
    title: String,
    body: String,
    receivedAt: Long
  ): CaptureOutcome {
    val s = settings.current()
    val dryRun = channel == CaptureChannel.TEST
    // Messages read from KakaoTalk / the SMS app's notification are checked like text messages.
    var gate = FinancialApps.Gate.ALLOWED
    when (channel) {
      CaptureChannel.NOTIFICATION -> {
        if (!s.notificationCaptureEnabled) return CaptureOutcome.Skipped("알림 자동 등록 꺼짐")
        gate = FinancialApps.gate(origin, originLabel, title)
        when (gate) {
          FinancialApps.Gate.BLOCKED -> return CaptureOutcome.Skipped(
            when {
              origin == FinancialApps.KAKAO && PAY_SERVICE.containsMatchIn(title) ->
                "결제 서비스 알림(${title.take(12)}) · 같은 결제의 카드사 알림으로 기록돼요"
              origin == FinancialApps.KAKAO -> "카카오톡: 카드사·은행 채널 메시지가 아님"
              else -> "금융 앱이 아님"
            }
          )
          else -> Unit
        }
      }
      CaptureChannel.SMS -> if (!s.smsCaptureEnabled) return CaptureOutcome.Skipped("문자 자동 등록 꺼짐")
      CaptureChannel.TEST -> Unit
    }

    val rules = db.ruleDao().getAll()
    CategoryRules.ignores(rules, title, body)?.let { return CaptureOutcome.Skipped("무시 규칙: ${it.keyword}") }

    val result = parser.parse(title, body, receivedAt)
    // A bill notice is all "footer" lines (명세서, 결제 예정) to the payment parser.
    if (result is ParseResult.Ignored && (result.reason == "광고·안내 메시지" || result.reason == "빈 메시지")) {
      billNotice(gate, originLabel, title, body, s, dryRun)?.let { return it }
    }
    val parsed = when (result) {
      is ParseResult.Ignored -> return CaptureOutcome.Skipped(result.reason)
      is ParseResult.Success -> result.payment
    }
    // A bank / card app's own notification often doesn't repeat the company name: take it from the app.
    val payment = if (parsed.provider == null && channel == CaptureChannel.NOTIFICATION && gate == FinancialApps.Gate.ALLOWED) {
      parsed.copy(provider = originLabel?.let { parser.detectProvider(it) } ?: parser.detectProvider(title))
    } else parsed
    val textLike = channel == CaptureChannel.SMS || gate == FinancialApps.Gate.SMS_APP || gate == FinancialApps.Gate.KAKAO_CHANNEL
    if (textLike && payment.provider == null) {
      return CaptureOutcome.Skipped("카드사/은행 이름이 없는 메시지")
    }
    // A KakaoTalk channel must be the company the message is about ("신한카드" channel → 신한카드 payment).
    if (gate == FinancialApps.Gate.KAKAO_CHANNEL && !FinancialApps.sameCompany(title, payment.provider)) {
      return CaptureOutcome.Skipped("카카오톡 채널과 카드사가 다름")
    }
    val timestamp = payment.paidAt ?: receivedAt
    if (payment.kind == PaymentKind.FOREIGN_CURRENCY) {
      if (dryRun) return CaptureOutcome.Skipped("해외 결제: 원화 금액을 입력하라는 알림을 보내요")
      return foreignPayment(payment, timestamp, s, rules)
    }

    return db.withTransaction {
      if (payment.kind == PaymentKind.CANCELLATION && dryRun) {
        val account = pickAccount(payment, s, dryRun)
          ?: return@withTransaction CaptureOutcome.Skipped(NO_ACCOUNT)
        return@withTransaction CaptureOutcome.WouldRecord(payment, account, account.id == 0L)
      }
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
          val account = pickAccount(payment, s, dryRun)
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

      var account = pickAccount(payment, s, dryRun)
        ?: return@withTransaction CaptureOutcome.Skipped(NO_ACCOUNT)
      val createdAccount = account.id == lastCreatedId
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
      if (dryRun) return@withTransaction CaptureOutcome.WouldRecord(payment, account, account.id == 0L)
      val months = payment.installmentMonths
      val id = if (s.splitInstallments && months != null && months > 1 && payment.type == TransactionType.EXPENSE) {
        InstallmentSplitter.split(tx, months, clock.zone).map { ledger.insertWithEffects(it) }.first()
      } else {
        ledger.insertWithEffects(tx)
      }
      CaptureOutcome.Recorded(id, payment, account, createdAccount)
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
    val account = pickAccount(payment, s, dryRun = false)
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

  /**
   * "[신한카드] 12월 결제 예정 금액 523,000원 (결제일 12/14)": keeps the bill on that card's account
   * so the payment-day reminder can show it. Null when the message isn't a bill notice.
   */
  private suspend fun billNotice(
    gate: FinancialApps.Gate,
    originLabel: String?,
    title: String,
    body: String,
    s: com.moneyflow.data.repository.AppSettings,
    dryRun: Boolean
  ): CaptureOutcome? {
    val text = "$title\n$body"
    val bill = com.moneyflow.domain.card.BillText.find(text, clock.today(), message = true) ?: return null
    val provider = parser.detectProvider(text) ?: originLabel?.let { parser.detectProvider(it) } ?: return null
    if (!provider.endsWith("카드")) return null
    if (gate == FinancialApps.Gate.KAKAO_CHANNEL && !FinancialApps.sameCompany(title, provider)) return null
    val asPayment = ParsedPayment(PaymentKind.PAYMENT, TransactionType.EXPENSE, bill.amount, "", "", provider, null, null, null)
    val account = db.withTransaction { pickAccount(asPayment, s, dryRun) }
      ?.takeIf { it.accountType == AccountType.CREDIT_CARD }
      ?: return CaptureOutcome.Skipped("청구 안내: $provider 카드 계좌가 없음")
    if (!dryRun) com.moneyflow.data.repository.CardBills.save(db, settings, account.id, bill, "카드사 안내", clock.nowMillis())
    return CaptureOutcome.BillNoticed(account, bill)
  }

  /** Id of the account the last [pickAccount] call created (to say so in the log). */
  private var lastCreatedId: Long = -1

  /**
   * The account for a payment: the card's last digits, then the card company / bank name, then
   * (when allowed) a new account for that company, then the default account. Must run inside a
   * DB transaction when it may create. A test run never creates: it returns an unsaved account (id 0).
   */
  private suspend fun pickAccount(payment: ParsedPayment, s: com.moneyflow.data.repository.AppSettings, dryRun: Boolean): AccountEntity? {
    lastCreatedId = -1
    val accounts = db.accountDao().getAll()
    payment.cardLast4?.filter { it.isDigit() }?.takeIf { it.length >= 3 }?.let { digits ->
      accounts.firstOrNull { it.accountNumber.filter(Char::isDigit).endsWith(digits) }?.let { return it }
    }
    val provider = payment.provider
    provider?.let {
      val core = provider.removeSuffix("카드").removeSuffix("은행").removeSuffix("뱅크").removeSuffix("페이")
      val wantsCard = provider.endsWith("카드")
      val matches = accounts.filter {
        it.name.contains(provider, true) || it.bankName.contains(provider, true) ||
          (core.length >= 2 && (it.bankName.contains(core, true) || it.name.contains(core, true)))
      }
      (matches.firstOrNull { (it.accountType == AccountType.CREDIT_CARD) == wantsCard } ?: matches.firstOrNull())
        ?.let { return it }
    }
    if (s.autoCreateCaptureAccounts && provider != null && newAccountType(provider) != null) {
      val type = newAccountType(provider)!!
      val fresh = AccountEntity(
        name = provider,
        bankName = provider,
        accountType = type,
        accountNumber = payment.cardLast4?.filter { it.isDigit() }.orEmpty(),
        balance = 0,
        isDebt = type == AccountType.CREDIT_CARD
      )
      if (dryRun) return fresh
      val id = db.accountDao().insert(fresh)
      lastCreatedId = id
      return fresh.copy(id = id)
    }
    s.autoCaptureDefaultAccountId?.let { id -> accounts.firstOrNull { it.id == id }?.let { return it } }
    // No card company / bank in the message and no default account: keep it in one catch-all
    // account rather than losing the payment (the user can move it later).
    if (s.autoCreateCaptureAccounts) {
      accounts.firstOrNull { it.name == OTHER_ACCOUNT }?.let { return it }
      val fresh = AccountEntity(name = OTHER_ACCOUNT, bankName = "자동 등록", accountType = AccountType.CASH, balance = 0)
      if (dryRun) return fresh
      val id = db.accountDao().insert(fresh)
      lastCreatedId = id
      return fresh.copy(id = id)
    }
    return null
  }

  /** A card company gets a card account, a bank a bank account; pay services get none (their card pays). */
  private fun newAccountType(provider: String): AccountType? = when {
    provider.endsWith("카드") -> AccountType.CREDIT_CARD
    provider.endsWith("은행") || provider.endsWith("뱅크") || provider in setOf("새마을금고", "신협", "우체국", "농협", "수협") -> AccountType.BANK
    // 카카오페이·네이버페이 머니 etc.: a wallet with its own balance.
    // (삼성페이 only passes a card payment through: the card's own alert records it.)
    provider == "삼성페이" -> null
    provider.endsWith("페이") || provider == "토스" -> AccountType.BANK
    else -> null
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
    private val PAY_SERVICE = Regex("페이|PAY|Pay|pay")
    const val OTHER_ACCOUNT = "기타 결제수단"
    const val NO_ACCOUNT = "등록할 계좌가 없음 (자동 등록 설정 → 기본 등록 계좌를 골라 주세요)"
  }
}

/** Which notifications may be read, and how strictly. */
object FinancialApps {
  const val KAKAO = "com.kakao.talk"

  /**
   * Text-message apps. Card alerts are mostly long "[Web발신]" messages (MMS) or RCS chats,
   * which never reach the SMS receiver, so their notifications are read — checked like SMS.
   */
  val SMS_APPS = setOf(
    "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms", "com.android.messaging"
  )

  /** KakaoTalk 알림톡 channels of card companies / banks / pay services (title, spaces removed). */
  val KAKAO_CHANNELS = listOf(
    "신한카드", "KB국민카드", "국민카드", "현대카드", "삼성카드", "롯데카드", "우리카드", "하나카드",
    "NH농협카드", "농협카드", "BC카드", "비씨카드", "IBK기업은행", "KB국민은행", "국민은행", "신한은행", "우리은행",
    "하나은행", "NH농협은행", "농협은행", "카카오뱅크", "토스뱅크", "케이뱅크", "카카오페이", "네이버페이", "토스",
    "SC제일은행", "씨티카드", "새마을금고", "우체국"
  )

  /** Other messengers are never read. */
  private val BLOCKED = setOf(
    "jp.naver.line.android", "com.facebook.orca", "org.telegram.messenger",
    "com.whatsapp", "com.instagram.android", "com.discord", "com.Slack"
  )

  enum class Gate {
    /** A card / bank / pay app. */
    ALLOWED,
    /** A KakaoTalk 1:1 chat from a card / bank channel. */
    KAKAO_CHANNEL,
    /** The phone's text-message app. */
    SMS_APP,
    BLOCKED
  }

  private fun norm(s: String?) = s.orEmpty().replace(" ", "").replace("\u00A0", "")

  /** The KakaoTalk chat title is a card / bank channel ("신한카드", "KB국민카드 알림" …). */
  fun isKakaoChannel(title: String?): Boolean {
    val t = norm(title)
    return t.isNotEmpty() && KAKAO_CHANNELS.any { c -> t == c || (t.startsWith(c) && t.length <= c.length + 6) }
  }

  fun gate(packageName: String, appLabel: String?, title: String? = null): Gate = when {
    packageName == KAKAO -> if (isKakaoChannel(title)) Gate.KAKAO_CHANNEL else Gate.BLOCKED
    packageName in SMS_APPS -> Gate.SMS_APP
    packageName in BLOCKED -> Gate.BLOCKED
    packageName in KNOWN || (appLabel != null && FINANCIAL_LABEL.containsMatchIn(appLabel)) -> Gate.ALLOWED
    else -> Gate.BLOCKED
  }

  /** The channel title and the company found in the message are the same company. */
  fun sameCompany(channelTitle: String?, provider: String?): Boolean {
    val core = { s: String -> norm(s).removePrefix("NH").removePrefix("KB").removePrefix("IBK").removeSuffix("카드").removeSuffix("은행").removeSuffix("뱅크").take(3) }
    val a = core(channelTitle ?: return false)
    val b = core(provider ?: return false)
    return a.isNotEmpty() && b.isNotEmpty() && (a.startsWith(b) || b.startsWith(a))
  }

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

  /** May the listener look at this app's notifications at all (the title is checked later for KakaoTalk). */
  fun isAllowed(packageName: String, appLabel: String?): Boolean =
    packageName == KAKAO || gate(packageName, appLabel) != Gate.BLOCKED
}
