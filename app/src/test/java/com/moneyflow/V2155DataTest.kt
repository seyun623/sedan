package com.moneyflow

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.FamilyMemberEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.card.CardBill
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Hourly review ③ (data / backup): fixes found by the reviewers. */
@RunWith(RobolectricTestRunner::class)
class V2155DataTest {
  private val clock = FakeClock()

  @Test fun deletingAStaleListTwiceUndoesTheBalanceOnce() = runBlocking {
    val db = inMemoryDb()
    val ledger = LedgerRepository(db, clock)
    val acc = ledger.addAccount(AccountEntity(name = "통장", bankName = "은행", accountType = AccountType.BANK, balance = 100_000))
    ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 10_000, category = "식비", accountId = acc, timestamp = clock.nowMillis(), memo = "점심"))
    val list = db.transactionDao().observeAll().first()
    ledger.deleteTransactions(list)
    ledger.deleteTransactions(list)
    assertEquals(100_000L, db.accountDao().getById(acc)!!.balance)
    db.close()
  }

  @Test fun restoringMyOwnFileRemovesMyFamilyCopyAndCardFigures() = runBlocking {
    val db = inMemoryDb()
    val settings = testSettingsRepository()
    val backups = BackupRepository(db, settings, clock)
    val content = backups.readBackup(backups.createBackup("family12".toCharArray(), "2.15.5"), "family12".toCharArray())
    // The old phone's file was loaded as a family member before the restore.
    db.familyDao().insertMember(FamilyMemberEntity(name = "나", deviceId = content.deviceId, backupCreatedAt = 0, importedAt = 0))
    settings.setCardBill(1L, CardBill(523_000, null, "카드사 안내", 0))
    backups.restore(content)
    assertNull(db.familyDao().byDevice(content.deviceId))
    assertTrue(settings.current().cardBills.isEmpty())
    db.close()
  }
}
