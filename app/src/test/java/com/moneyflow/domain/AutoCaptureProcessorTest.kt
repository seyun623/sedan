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
    processor = AutoCaptureProcessor(db, ledger, settings, clock)
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
    assertTrue(processor.process(CaptureChannel.SMS, "1588", null, "", msg, clock.nowMillis()) is CaptureOutcome.Skipped)
    settings.setDefaultCaptureAccount(bankId)
    val out = processor.process(CaptureChannel.SMS, "1588", null, "", msg, clock.nowMillis())
    assertTrue(out is CaptureOutcome.Recorded)
    assertEquals(bankId, txs().single().accountId)
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
}
