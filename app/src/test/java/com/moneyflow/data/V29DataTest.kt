package com.moneyflow.data

import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.RecurringRepository
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class V29DataTest {
  @Test fun resumingAPausedRecurringItemDoesNotRecordThePausedMonths() = runBlocking {
    val db = inMemoryDb()
    val clock = FakeClock(LocalDateTime.of(2026, 6, 20, 12, 0))
    val ledger = LedgerRepository(db, clock)
    val repo = RecurringRepository(db, ledger, clock)
    val acc = ledger.addAccount(bank())
    val id = repo.add(RecurringTransactionEntity(title = "넷플릭스", type = TransactionType.EXPENSE, amount = 17_000, category = "문화/여가",
      dayOfMonth = 5, accountId = acc, accountName = "생활비 통장"))
    assertEquals(1, db.transactionDao().observeAll().first().size) // June
    repo.setActive(id, false)
    clock.set(LocalDateTime.of(2026, 11, 20, 12, 0))
    repo.setActive(id, true)
    // Only November is recorded, not July–October.
    assertEquals(2, db.transactionDao().observeAll().first().size)
    db.close()
  }

  @Test fun undoAfterTheAccountIsGoneFailsWithoutLosingData() = runBlocking {
    val db = inMemoryDb()
    val clock = FakeClock()
    val ledger = LedgerRepository(db, clock)
    val acc = ledger.addAccount(bank())
    val id = ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 1_000, category = "식비", accountId = acc, timestamp = clock.nowMillis(), memo = "a"))
    val removed = ledger.deleteTransaction(ledger.getTransaction(id)!!)!!
    ledger.deleteAccount(acc, deleteRelated = true)
    // The UI catches this and shows a message (MoneyFlowNavHost) instead of crashing.
    val failed = runCatching { ledger.restoreTransaction(removed) }.isFailure
    assertEquals(true, failed)
    db.close()
  }
}
