package com.moneyflow.cardimport

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.CardImportRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.cardimport.AiStatementReader
import com.moneyflow.domain.cardimport.PerformanceInfo
import com.moneyflow.domain.cardimport.StatementItem
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.card
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class CardImportRepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var ledger: LedgerRepository
  private lateinit var settings: SettingsRepository
  private lateinit var repo: CardImportRepository
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 23, 12, 0))
  private var cardId = 0L

  @Before fun setUp() = runBlocking {
    db = inMemoryDb()
    ledger = LedgerRepository(db, clock)
    settings = testSettingsRepository()
    repo = CardImportRepository(db, ledger, settings, clock)
    ledger.addAccount(bank())
    cardId = ledger.addAccount(card(name = "현대카드 M"))
  }

  @After fun tearDown() = db.close()

  private fun item(day: Int, merchant: String, amount: Long, cancel: Boolean = false) =
    StatementItem(LocalDate.of(2026, 11, day), LocalTime.of(10, 0), merchant, amount, cancel)

  @Test fun bankFileDepositsBecomeIncomeAndBankProviderPrefersBankAccount() = runBlocking {
    val bankAcc = ledger.getAccounts().first { it.accountType == com.moneyflow.data.model.AccountType.BANK }
    assertEquals(bankAcc.id, repo.suggestAccount("토스뱅크", ledger.getAccounts())!!.id)
    val rows = repo.review(listOf(
      StatementItem(LocalDate.of(2026, 11, 21), null, "(주)머니회사 급여", 3_000_000, isIncome = true),
      StatementItem(LocalDate.of(2026, 11, 21), null, "관리비", 150_000)
    ), bankAcc.id)
    assertEquals(com.moneyflow.data.model.FinanceCategories.SALARY, rows[0].category)
    repo.import(rows, bankAcc)
    val txs = db.transactionDao().observeAll().first()
    assertEquals(TransactionType.INCOME, txs.first { it.amount == 3_000_000L }.type)
    assertEquals(TransactionType.EXPENSE, txs.first { it.amount == 150_000L }.type)
    assertEquals(1_000_000 + 3_000_000 - 150_000L, db.accountDao().getById(bankAcc.id)!!.balance)
    // Importing the same file again marks both as already there.
    assertTrue(repo.review(rows.map { it.item }, bankAcc.id).all { it.isDuplicate })
  }

  @Test fun suggestsMatchingCardAccount() = runBlocking {
    assertEquals(cardId, repo.suggestAccount("현대카드", ledger.getAccounts())!!.id)
  }

  @Test fun duplicatesAreDetectedAndImportAddsTheRest() = runBlocking {
    // Already captured by SMS on the 20th
    ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 5_600, category = "카페/간식", accountId = cardId,
      timestamp = clock.millis(LocalDateTime.of(2026, 11, 20, 9, 0)), memo = "스타벅스", source = TransactionSource.SMS))
    val rows = repo.review(listOf(item(20, "스타벅스", 5_600), item(21, "쿠팡", 32_000), item(21, "스타벅스", 5_600)), cardId)
    assertTrue(rows[0].isDuplicate)
    assertFalse(rows[1].isDuplicate)
    assertFalse(rows[2].isDuplicate) // second purchase of the same amount is a different one
    assertEquals("카페/간식", rows[0].category)
    assertEquals("쇼핑", rows[1].category)

    val account = db.accountDao().getById(cardId)!!
    val result = repo.import(rows.filter { it.selectedByDefault }, account)
    assertEquals(2, result.added)
    val txs = db.transactionDao().observeAll().first()
    assertEquals(3, txs.size)
    assertEquals(5_600 + 32_000 + 5_600L, db.accountDao().getById(cardId)!!.balance)
    assertTrue(txs.any { it.source == TransactionSource.CARD_IMPORT && it.memo == "쿠팡" })
  }

  @Test fun importingTheSameScreenTwiceDoesNotDuplicate() = runBlocking {
    val account = db.accountDao().getById(cardId)!!
    val items = listOf(item(21, "쿠팡", 32_000))
    repo.import(repo.review(items, cardId), account)
    val second = repo.review(items, cardId)
    assertTrue(second.single().isDuplicate)
    assertEquals(1, db.transactionDao().count())
  }

  @Test fun cancellationRemovesOriginalPurchase() = runBlocking {
    val account = db.accountDao().getById(cardId)!!
    repo.import(repo.review(listOf(item(18, "GS25 강남점", 3_500)), cardId), account)
    val result = repo.import(repo.review(listOf(item(19, "GS25 강남점", 3_500, cancel = true)), cardId), account)
    assertEquals(1, result.cancelled)
    assertEquals(0, db.transactionDao().count())
    assertEquals(0L, db.accountDao().getById(cardId)!!.balance)
  }

  @Test fun performanceIsSavedPerAccount() = runBlocking {
    repo.savePerformance(cardId, PerformanceInfo("전월 이용실적", 412_000))
    val p = settings.current().cardPerformance[cardId]!!
    assertEquals(412_000L, p.amount)
    assertEquals(clock.nowMillis(), p.fetchedAt)
  }

  @Test fun aiResultParsingDropsInvalidRows() {
    val json = JSONObject("""{"transactions":[
      {"date":"2026-11-21","time":"14:32","merchant":"스타벅스","amount":5600,"cancel":false,"installmentMonths":0},
      {"date":"bad","merchant":"x","amount":100,"cancel":false},
      {"date":"2026-11-20","merchant":"","amount":100,"cancel":false},
      {"date":"2026-11-19","merchant":"이마트","amount":-120000,"cancel":true,"installmentMonths":3}],
      "performance":[{"label":"전월 실적","amount":300000},{"label":"x","amount":0}]}""")
    val r = AiStatementReader.parseResult(json, "OpenRouter")
    assertEquals(2, r.items.size)
    assertEquals(LocalTime.of(14, 32), r.items[0].time)
    assertEquals(120_000L, r.items[1].amount)
    assertTrue(r.items[1].isCancel)
    assertEquals(3, r.items[1].installmentMonths)
    assertEquals(1, r.performance.size)
  }

  /** 2.17.2: 우리·농협·IBK BC member cards open BC카드 and save into their own card account. */
  @Test fun bcMemberCardsOpenBcAndUseTheirOwnAccount() = runBlocking {
    val members = com.moneyflow.domain.cardimport.CardSites.bcMembers
    assertEquals(listOf("bc_woori", "bc_nh", "bc_ibk"), members.map { it.id })
    val bc = com.moneyflow.domain.cardimport.CardSites.byId("bc")!!
    members.forEach { assertEquals(bc.url, it.url); assertEquals("bc", it.settingsId) }
    assertEquals("우리카드", com.moneyflow.domain.cardimport.CardSites.byId("bc_woori")!!.provider)
    val nhId = ledger.addAccount(card(name = "농협 채움카드"))
    val accounts = ledger.getAccounts()
    assertEquals(nhId, repo.namedAccount(members[1].provider, accounts)!!.id)
    // No 우리 / IBK account yet: the preview offers to make one named after the card.
    assertEquals(null, repo.namedAccount("우리카드", accounts))
    assertEquals(null, repo.namedAccount("IBK기업카드", accounts))
  }
}
