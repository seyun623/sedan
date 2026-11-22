package com.moneyflow.data

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.RecurringRepository
import com.moneyflow.domain.MonthKeys
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
class RecurringRepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var ledger: LedgerRepository
  private lateinit var repo: RecurringRepository
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 21, 12, 0))
  private var accountId = 0L

  @Before fun setUp() = runBlocking {
    db = inMemoryDb()
    ledger = LedgerRepository(db, clock)
    repo = RecurringRepository(db, ledger, clock)
    accountId = ledger.addAccount(bank(balance = 1_000_000))
  }

  @After fun tearDown() = db.close()

  private fun item(day: Int, amount: Long = 10_000, start: String = "") = RecurringTransactionEntity(
    title = "구독", type = TransactionType.EXPENSE, amount = amount, category = "문화/여가",
    dayOfMonth = day, accountId = accountId, accountName = "생활비 통장", startMonth = start
  )

  private suspend fun txs() = db.transactionDao().observeAll().first()
  private fun dateOf(millis: Long) = MonthKeys.toLocalDate(millis, clock.zone)

  @Test fun recordsOnlyWhenTheDayHasCome_onTheScheduledDate() = runBlocking {
    repo.add(item(day = 25))
    assertEquals(0, txs().size) // 21st: not yet
    clock.set(LocalDateTime.of(2026, 11, 25, 8, 0))
    assertEquals(1, repo.applyDue())
    val t = txs().single()
    assertEquals(LocalDate.of(2026, 11, 25), dateOf(t.timestamp))
    assertEquals(TransactionSource.RECURRING, t.source)
    assertEquals(990_000, db.accountDao().getById(accountId)!!.balance)
    assertEquals(0, repo.applyDue()) // idempotent
  }

  @Test fun day31IsClampedToMonthEnd_andMissedMonthsAreCaughtUp() = runBlocking {
    clock.set(LocalDateTime.of(2027, 1, 5, 9, 0))
    repo.add(item(day = 31, start = "2026-11"))
    // Nov 30, Dec 31 recorded; Jan 31 not yet
    assertEquals(listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 11, 30)), txs().map { dateOf(it.timestamp) })
    clock.set(LocalDateTime.of(2027, 2, 28, 9, 0))
    repo.applyDue()
    assertEquals(LocalDate.of(2027, 2, 28), dateOf(txs().first().timestamp))
    assertEquals(4, txs().size)
  }

  @Test fun concurrentRunsDoNotDuplicate() = runBlocking {
    clock.set(LocalDateTime.of(2026, 11, 25, 8, 0))
    db.recurringTransactionDao().insert(item(day = 1, start = "2026-11"))
    (1..5).map { async { repo.applyDue() } }.awaitAll()
    assertEquals(1, txs().size)
  }

  @Test fun executeNowRecordsOncePerMonthAndBlocksAutoRun() = runBlocking {
    val id = repo.add(item(day = 28))
    val saved = db.recurringTransactionDao().getById(id)!!
    assertTrue(repo.executeNow(saved))
    assertFalse(repo.executeNow(saved))
    clock.set(LocalDateTime.of(2026, 11, 28, 10, 0))
    repo.applyDue()
    assertEquals(1, txs().size)
  }

  @Test fun inactiveItemsAreSkipped() = runBlocking {
    val id = repo.add(item(day = 25))
    repo.setActive(id, false)
    clock.set(LocalDateTime.of(2026, 11, 26, 8, 0))
    assertEquals(0, repo.applyDue())
  }
}
