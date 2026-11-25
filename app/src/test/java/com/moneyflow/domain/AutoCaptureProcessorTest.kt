package com.moneyflow.domain

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.capture.AutoCaptureProcessor
import com.moneyflow.domain.capture.CaptureChannel
import com.moneyflow.domain.capture.CaptureOutcome
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.card
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoCaptureProcessorTest {
  private lateinit var db: AppDatabase
  private lateinit var ledger: LedgerRepository
  private lateinit var settings: SettingsRepository
  private lateinit var processor: AutoCaptureProcessor
  private val clock = FakeClock()
  private val prompts = mutableListOf<com.moneyflow.domain.capture.ForeignPaymentPrompt>()
  private val notifier = object : com.moneyflow.domain.capture.CaptureNotifier {
    override fun foreignPayment(prompt: com.moneyflow.domain.capture.ForeignPaymentPrompt) { prompts += prompt }
  }
  private var cardId = 0L
  private var bankId = 0L

  private val approval = "[Web발신] 신한카드(1234)승인 홍*동 12,500원(일시불)11/21 14:32 스타벅스 누적1,234,560원"
  private val shinhanApp = "com.shcard.smartpay"

  @Before fun setUp() = runBlocking {
    db = inMemoryDb()
    ledger = LedgerRepository(db, clock)
    settings = testSettingsRepository()
    settings.setNotificationCapture(true)
    settings.setSmsCapture(true)
    processor = AutoCaptureProcessor(db, ledger, settings, clock, notifier)
    bankId = ledger.addAccount(bank(balance = 1_000_000))
    cardId = ledger.addAccount(card(name = "신한카드 Deep", number = "1234-56**-****-1234"))
  }

  @After fun tearDown() = db.close()

  private suspend fun txs() = db.transactionDao().observeAll().first()

  @Test fun recordsApprovalOnMatchingCardAccount() = runBlocking {
    val out = processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis())
    assertTrue(out is CaptureOutcome.Recorded)
    val t = txs().single()
    assertEquals(cardId, t.accountId)
    assertEquals(12_500, t.amount)
    assertEquals(TransactionSource.SMS, t.source)
    assertEquals(12_500, db.accountDao().getById(cardId)!!.balance)
  }

  @Test fun sameApprovalViaSmsAndAppNotificationIsStoredOnce() = runBlocking {
    processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis())
    processor.process(CaptureChannel.NOTIFICATION, shinhanApp, "신한 SOL페이", "신한카드", approval, clock.nowMillis() + 5_000)
    processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis() + 9_000)
    assertEquals(1, txs().size)
  }

  @Test fun cancellationRemovesTheOriginalAndRestoresBalance() = runBlocking {
    processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis())
    val out = processor.process(
      CaptureChannel.SMS, "15447200", null, "",
      "[Web발신] 신한카드(1234)승인취소 홍*동 12,500원 11/21 15:00 스타벅스", clock.nowMillis() + 60_000
    )
    assertTrue(out is CaptureOutcome.CancelledOriginal)
    assertEquals(0, txs().size)
    assertEquals(0, db.accountDao().getById(cardId)!!.balance)
  }

  @Test fun messengerNotificationsAreNeverRead() = runBlocking {
    val out = processor.process(CaptureChannel.NOTIFICATION, "com.kakao.talk", "카카오톡", "친구", "신한카드 승인 20,000원 11/21 10:00 치킨", clock.nowMillis())
    assertTrue(out is CaptureOutcome.Skipped)
    assertEquals(0, txs().size)
  }

  @Test fun unknownAppWithFinancialLabelIsAllowed() = runBlocking {
    val out = processor.process(CaptureChannel.NOTIFICATION, "com.example.newcard", "새로운카드", "결제", approval, clock.nowMillis())
    assertTrue(out is CaptureOutcome.Recorded)
  }

  @Test fun disabledChannelIsSkipped() = runBlocking {
    settings.setSmsCapture(false)
    val out = processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis())
    assertTrue(out is CaptureOutcome.Skipped)
  }

  @Test fun smsWithoutProviderIsSkipped() = runBlocking {
    val out = processor.process(CaptureChannel.SMS, "01012345678", null, "", "결제 10,000원 완료되었습니다", clock.nowMillis())
    assertTrue(out is CaptureOutcome.Skipped)
  }

  @Test fun noMatchingAccountUsesDefaultAccountOrSkips() = runBlocking {
    val msg = "롯데카드 승인 15,000원 11/21 10:00 어떤가게"
    settings.setAutoCreateCaptureAccounts(false)
    assertTrue(processor.process(CaptureChannel.SMS, "1588", null, "", msg, clock.nowMillis()) is CaptureOutcome.Skipped)
    settings.setDefaultCaptureAccount(bankId)
    val out = processor.process(CaptureChannel.SMS, "1588", null, "", msg, clock.nowMillis())
    assertTrue(out is CaptureOutcome.Recorded)
    assertEquals(bankId, txs().single().accountId)
  }

  @Test fun unknownCardCompanyGetsItsOwnAccount() = runBlocking {
    // 2.14.3: "등록할 계좌가 없음" for every 삼성카드 alert → the account is made once.
    val msg = "[Web발신] 삼성1337승인 황*윤 12,500원 일시불 11/25 19:57 후라토식당"
    val dry = processor.process(CaptureChannel.TEST, "test", "시험", "", msg, clock.nowMillis())
    assertTrue(dry.toString(), dry is CaptureOutcome.WouldRecord && dry.newAccount)
    assertTrue(db.accountDao().getAll().none { it.name == "삼성카드" })
    val out = processor.process(CaptureChannel.SMS, "15888900", null, "", msg, clock.nowMillis())
    assertTrue(out.toString(), out is CaptureOutcome.Recorded && out.newAccount)
    val card = db.accountDao().getAll().single { it.name == "삼성카드" }
    assertEquals(com.moneyflow.data.model.AccountType.CREDIT_CARD, card.accountType)
    assertTrue(card.isDebt)
    assertEquals("1337", card.accountNumber)
    // The next alert goes to the same account without making another one.
    clock.advanceMillis(10 * 60_000L)
    val again = processor.process(CaptureChannel.SMS, "15888900", null, "", msg.replace("12,500", "8,000").replace("19:57", "20:07"), clock.nowMillis())
    assertTrue(again.toString(), again is CaptureOutcome.Recorded && !again.newAccount)
    assertEquals(1, db.accountDao().getAll().count { it.name == "삼성카드" })
  }

  @Test fun paymentWithoutCompanyGoesToCatchAllAccount() = runBlocking {
    // 2.14.5: "결제수단 없으면 등록 안 됨" → kept in '기타 결제수단' instead of being lost.
    val outcome = processor.process(
      CaptureChannel.NOTIFICATION, "com.nhn.android.search", "네이버", "네이버 결제",
      "결제 완료 23,000원 무신사", clock.nowMillis()
    )
    if (outcome is CaptureOutcome.Recorded) {
      assertTrue(outcome.account.name == "네이버페이" || outcome.account.name == "기타 결제수단")
    }
    val pay = processor.process(
      CaptureChannel.NOTIFICATION, "com.kakaopay.app", "카카오페이", "카카오페이",
      "카카오페이머니 결제 12,000원 스타벅스", clock.nowMillis()
    )
    assertTrue(pay.toString(), pay is CaptureOutcome.Recorded && pay.account.name == "카카오페이")
  }

  @Test fun billNoticeIsKeptNotRecorded() = runBlocking {
    // 2.15: the monthly 결제 예정 금액 message fills the card's next bill, not the ledger.
    val msg = "[Web발신]\n[신한카드] 황*윤님 12월 결제예정금액 523,000원 결제일 12/14"
    val out = processor.process(CaptureChannel.SMS, "15447200", null, "", msg, clock.nowMillis())
    assertTrue(out.toString(), out is CaptureOutcome.BillNoticed)
    assertTrue(txs().isEmpty())
    val card = db.accountDao().getAll().single { it.name.contains("신한") && it.accountType == com.moneyflow.data.model.AccountType.CREDIT_CARD }
    val bill = settings.current().cardBills[card.id]!!
    assertEquals(523_000L, bill.amount)
    assertEquals(14, bill.dueDate?.dayOfMonth)
    assertEquals(14, db.accountDao().getById(card.id)!!.paymentDay)
  }

  @Test fun bankAppNameGivesTheBank() = runBlocking {
    val outcome = processor.process(
      CaptureChannel.NOTIFICATION, "com.wooribank.smart.npib", "우리WON뱅킹", "우리WON뱅킹 입출금알림",
      "입금 50,000원 홍길동 잔액 1,250,000원", clock.nowMillis()
    )
    assertTrue(outcome.toString(), outcome is CaptureOutcome.Recorded)
    assertEquals("우리은행", (outcome as CaptureOutcome.Recorded).account.name)
  }

  @Test fun categoryCorrectedOnceIsReusedForTheSameSender() = runBlocking {
    val salary = "[Web발신]\n신한 11/25 09:01\n110-***-123456\n입금 3,500,000\n잔액 4,200,000\n (주)머니플로우"
    settings.setDefaultCaptureAccount(bankId)
    processor.process(CaptureChannel.SMS, "15778000", null, "", salary, clock.nowMillis())
    val first = txs().single()
    assertEquals("기타수입", first.category)
    // The user fixes the category once…
    ledger.updateTransaction(first.copy(category = "급여"))
    // …and next month's salary is filed as 급여 automatically.
    clock.advanceMillis(31L * 24 * 60 * 60 * 1000)
    val next = salary.replace("11/25", "12/25")
    processor.process(CaptureChannel.SMS, "15778000", null, "", next, clock.nowMillis())
    assertEquals(listOf("급여", "급여"), txs().map { it.category })
  }

  // ---- 1.6: rules, installments, foreign payments ----

  private val installment = "[Web발신] 신한카드(1234)승인 홍*동 300,001원(03개월)11/21 14:32 하이마트 누적1,234,560원"

  @Test fun ignoreRuleSkipsAndCategoryRuleWins() = runBlocking {
    db.ruleDao().upsert(com.moneyflow.data.model.CategoryRuleEntity(keyword = "스타 벅스", category = "식비"))
    processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis())
    assertEquals("식비", txs().single().category)
    db.ruleDao().upsert(com.moneyflow.data.model.CategoryRuleEntity(keyword = "하이마트", action = com.moneyflow.data.model.RuleAction.IGNORE))
    val out = processor.process(CaptureChannel.SMS, "15447200", null, "", installment, clock.nowMillis())
    assertTrue(out is CaptureOutcome.Skipped)
    assertEquals(1, txs().size)
  }

  @Test fun installmentIsSplitWhenEnabledAndCancelRemovesAllParts() = runBlocking {
    settings.setSplitInstallments(true)
    processor.process(CaptureChannel.SMS, "15447200", null, "", installment, clock.nowMillis())
    val parts = txs().sortedBy { it.timestamp }
    assertEquals(listOf(100_001L, 100_000L, 100_000L), parts.map { it.amount })
    assertEquals("하이마트 (할부 1/3)", parts[0].memo)
    assertEquals(300_001, db.accountDao().getById(cardId)!!.balance)
    val cancel = "[Web발신] 신한카드(1234)승인취소 홍*동 300,001원 11/22 10:00 하이마트"
    clock.advanceMillis(24 * 3_600_000L)
    assertTrue(processor.process(CaptureChannel.SMS, "15447200", null, "", cancel, clock.nowMillis()) is CaptureOutcome.CancelledOriginal)
    assertTrue(txs().isEmpty())
    assertEquals(0, db.accountDao().getById(cardId)!!.balance)
  }

  @Test fun installmentNotSplitByDefault() = runBlocking {
    processor.process(CaptureChannel.SMS, "15447200", null, "", installment, clock.nowMillis())
    assertEquals("하이마트 (할부 3개월)", txs().single().memo)
  }

  @Test fun foreignPaymentPromptsOnceWithPrefill() = runBlocking {
    val msg = "[Web발신] 신한카드(1234)해외승인 홍*동 USD 12.50 11/21 03:10 AMAZON"
    val out = processor.process(CaptureChannel.SMS, "15447200", null, "", msg, clock.nowMillis())
    assertTrue(out is CaptureOutcome.NeedsKrwAmount)
    processor.process(CaptureChannel.NOTIFICATION, shinhanApp, "신한 SOL페이", "신한카드", msg, clock.nowMillis() + 60_000)
    assertEquals(1, prompts.size)
    val p = prompts.single().prefill
    assertEquals(cardId, p.accountId)
    assertTrue(p.memo.startsWith("AMAZON"))
    assertTrue(p.memo.contains("12.50"))
    assertTrue(txs().isEmpty())
  }

  // ---- 2.9 review fixes ----

  @Test fun aCancellationArrivingTwiceRemovesOnlyOnePurchase() = runBlocking {
    processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis())
    clock.advanceMillis(24 * 3_600_000L)
    processor.process(CaptureChannel.SMS, "15447200", null, "", approval.replace("11/21 14:32", "11/22 14:32"), clock.nowMillis())
    assertEquals(2, txs().size)
    val cancel = "[Web발신] 신한카드(1234)승인취소 홍*동 12,500원 11/22 15:00 스타벅스"
    processor.process(CaptureChannel.SMS, "15447200", null, "", cancel, clock.nowMillis())
    processor.process(CaptureChannel.NOTIFICATION, shinhanApp, "신한카드", "", cancel, clock.nowMillis() + 30_000)
    assertEquals(1, txs().size)
  }

  @Test fun declinedAndAdMessagesAreNotRecorded() = runBlocking {
    listOf(
      "[KB국민카드]홍*동님 승인거절 12,000원 한도초과 11/25 14:30 스타벅스",
      "[KB국민카드] 11월 결제예정금액 523,400원 (11/25 결제)",
      "(광고)[삼성카드] 삼성페이로 결제 시 최대 30,000원 캐시백",
      "[신한카드] 승인 12,000원 확인 https://bit.ly/abc"
    ).forEach { processor.process(CaptureChannel.SMS, "15447200", null, "", it, clock.nowMillis()) }
    assertTrue(txs().isEmpty())
  }

  @Test fun bankAutopayOfACardBillLowersTheCardDebt() = runBlocking {
    ledger.addAccount(card(name = "삼성카드 taptap", number = "").copy(bankName = "삼성카드", balance = 640_000))
    val samsung = ledger.getAccounts().first { it.name.startsWith("삼성카드") }
    val bank = ledger.getAccounts().first { it.id == bankId }
    ledger.updateAccount(bank.copy(bankName = "KB국민", name = "KB 생활비"))
    processor.process(CaptureChannel.SMS, "15881688", null, "", "[KB]11/25 09:00 123-***-456 출금 640,000원 삼성카드", clock.nowMillis())
    val t = txs().single()
    assertEquals(com.moneyflow.data.model.TransactionType.TRANSFER, t.type)
    assertEquals(samsung.id, t.toAccountId)
    assertEquals(0L, db.accountDao().getById(samsung.id)!!.balance)
  }

  @Test fun aCancellationWithoutThePurchaseIsKeptAsARefund() = runBlocking {
    val cancel = "[Web발신] 신한카드(1234)승인취소 홍*동 39,000원 11/21 15:00 무신사"
    processor.process(CaptureChannel.SMS, "15447200", null, "", cancel, clock.nowMillis())
    processor.process(CaptureChannel.NOTIFICATION, shinhanApp, "신한카드", "", cancel, clock.nowMillis() + 20_000)
    val t = txs().single()
    assertEquals(com.moneyflow.data.model.TransactionType.REFUND, t.type)
    assertEquals(39_000, t.amount)
    assertEquals(-39_000, db.accountDao().getById(cardId)!!.balance)
  }

  // ---- 2.13: KakaoTalk 알림톡 and the text-message app ----

  @Test fun kakaoCardChannelIsRecordedButFriendsAreNot() = runBlocking {
    val alimtalk = "[신한카드 승인 안내]\n홍*동님, 신한카드(1234) 승인 내역입니다.\n▶승인금액 : 12,500원(일시불)\n▶승인일시 : 11/21 14:32\n▶가맹점명 : 스타벅스 강남R점"
    val out = processor.process(CaptureChannel.NOTIFICATION, "com.kakao.talk", "카카오톡", "신한카드", alimtalk, clock.nowMillis())
    assertTrue(out.toString(), out is CaptureOutcome.Recorded)
    assertEquals("스타벅스 강남R점", txs().single().memo)
    // The same text sent by a friend, or a channel of another company, is not recorded.
    assertTrue(processor.process(CaptureChannel.NOTIFICATION, "com.kakao.talk", "카카오톡", "엄마", alimtalk, clock.nowMillis()) is CaptureOutcome.Skipped)
    assertTrue(processor.process(CaptureChannel.NOTIFICATION, "com.kakao.talk", "카카오톡", "삼성카드", alimtalk, clock.nowMillis()) is CaptureOutcome.Skipped)
    assertEquals(1, txs().size)
  }

  @Test fun textMessageAppNotificationIsReadLikeSms() = runBlocking {
    // Long "[Web발신]" alerts (MMS) never reach the SMS receiver; the messages app's notification is read.
    val out = processor.process(CaptureChannel.NOTIFICATION, "com.samsung.android.messaging", "메시지", "15447200", approval, clock.nowMillis())
    assertTrue(out is CaptureOutcome.Recorded)
    // The same message arriving by SMS too is not recorded twice.
    processor.process(CaptureChannel.SMS, "15447200", null, "", approval, clock.nowMillis() + 30_000)
    assertEquals(1, txs().size)
    // A personal text through the same app is not a payment.
    assertTrue(processor.process(CaptureChannel.NOTIFICATION, "com.samsung.android.messaging", "메시지", "엄마", "저녁 먹었어?", clock.nowMillis()) is CaptureOutcome.Skipped)
  }

  @Test fun testRunChecksEverythingButSavesNothing() = runBlocking {
    val out = processor.process(CaptureChannel.TEST, "test", "시험", "", approval, clock.nowMillis())
    assertTrue(out is CaptureOutcome.WouldRecord)
    assertTrue(txs().isEmpty())
  }
}
