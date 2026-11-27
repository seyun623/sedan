package com.moneyflow.domain

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.FamilyRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.cardimport.TableFileReader
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.card
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class V212Test {
  private val clock = FakeClock()
  private val stats = MonthlyStatsCalculator(clock.zone)

  @Test fun aRefundLowersSpendingAndTheCardDebt() = runBlocking {
    val db = inMemoryDb()
    val ledger = LedgerRepository(db, clock)
    val cardId = ledger.addAccount(card(name = "현대카드"))
    fun tx(type: TransactionType, amount: Long, category: String, excluded: Boolean = false) =
      TransactionEntity(type = type, amount = amount, category = category, accountId = cardId, timestamp = clock.nowMillis(), memo = "옷", isExcludedFromBudget = excluded)
    ledger.addTransaction(tx(TransactionType.EXPENSE, 150_000, "쇼핑"))
    ledger.addTransaction(tx(TransactionType.EXPENSE, 20_000, "식비"))
    ledger.addTransaction(tx(TransactionType.REFUND, 150_000, "쇼핑"))
    // The card owes only the meal now.
    assertEquals(20_000L, db.accountDao().getById(cardId)!!.balance)
    val txs = db.transactionDao().getBetween(0, Long.MAX_VALUE)
    assertEquals(20_000L, stats.totalExpense(txs))
    assertEquals(20_000L, stats.budgetExpense(txs))
    assertEquals(0L, stats.totalIncome(txs)) // a refund is not income
    assertEquals(listOf("식비"), stats.categoryBreakdown(txs).map { it.category })
    db.close()
  }

  @Test fun mergedHtmlCellsAreRepeated() {
    val html = """<table>
      <tr><th>이용일</th><th>가맹점</th><th>금액</th></tr>
      <tr><td rowspan=2>2026.11.21</td><td>스타벅스</td><td>5,600</td></tr>
      <tr><td>GS25</td><td>1,200</td></tr>
      <tr><td>2026.11.22</td><td colspan="2">합계 6,800</td></tr>
    </table>"""
    val rows = TableFileReader.readHtml(html)
    assertEquals(listOf("2026.11.21", "GS25", "1,200"), rows[2])
    assertEquals(listOf("2026.11.22", "합계 6,800", "합계 6,800"), rows[3])
  }

  @Test fun aFamilyMembersBackupIsRecognised() = runBlocking {
    val mine = inMemoryDb(); val theirs = inMemoryDb()
    val mySettings = testSettingsRepository(); val theirSettings = testSettingsRepository()
    val myBackups = BackupRepository(mine, mySettings, clock)
    val family = FamilyRepository(mine, myBackups, mySettings, clock)
    LedgerRepository(theirs, clock).addAccount(bank("아내 통장"))
    val file = BackupRepository(theirs, theirSettings, clock).createBackup("password1".toCharArray(), "2.12.0")
    family.import(file, "password1".toCharArray(), "아내")
    assertEquals("아내", myBackups.familyMemberOf(myBackups.readBackup(file, "password1".toCharArray())))
    val own = myBackups.createBackup("password1".toCharArray(), "2.12.0")
    assertNull(myBackups.familyMemberOf(myBackups.readBackup(own, "password1".toCharArray())))
    mine.close(); theirs.close()
  }
}
