package com.moneyflow.domain

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.report.NetWorthHistory
import com.moneyflow.domain.report.YearlyReports
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class V27ReportsTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 12, 0).atZone(zone).toInstant().toEpochMilli()

  @Test fun netWorthIsWorkedOutBackwardsFromTodaysBalances() = runBlocking {
    val db = inMemoryDb()
    val ledger = LedgerRepository(db, FakeClock(LocalDateTime.of(2026, 11, 24, 12, 0)))
    val bankId = ledger.addAccount(bank(balance = 1_000_000))
    val cardId = ledger.addAccount(AccountEntity(name = "카드", bankName = "현대카드", accountType = AccountType.CREDIT_CARD, balance = 0, isDebt = true))
    // October: salary 3,000,000, card spending 200,000. November: card bill paid from the bank, 50,000 groceries.
    ledger.addTransaction(TransactionEntity(type = TransactionType.INCOME, amount = 3_000_000, category = "급여", accountId = bankId, timestamp = at(2026, 10, 25), memo = "급여"))
    ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 200_000, category = "쇼핑", accountId = cardId, timestamp = at(2026, 10, 28), memo = "옷"))
    ledger.addTransaction(TransactionEntity(type = TransactionType.TRANSFER, amount = 200_000, category = "카드대금납부", accountId = bankId, toAccountId = cardId, timestamp = at(2026, 11, 14), memo = "카드값"))
    ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 50_000, category = "생활/마트", accountId = bankId, timestamp = at(2026, 11, 20), memo = "마트"))

    val points = NetWorthHistory.monthly(ledger.getAccounts(), db.transactionDao().getBetween(0, Long.MAX_VALUE), YearMonth.of(2026, 11), 12, zone)
    assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), points.map { it.month })
    // Now: bank 1,000,000 + 3,000,000 - 200,000 - 50,000 = 3,750,000, card 0.
    assertEquals(3_750_000L, points.last().netWorth)
    // End of October: bank 4,000,000 (before paying the bill and the groceries), card owes 200,000.
    assertEquals(4_000_000L, points.first().assets)
    assertEquals(200_000L, points.first().debts)
    assertEquals(3_800_000L, points.first().netWorth)
    db.close()
  }

  @Test fun yearlyReport() {
    val stats = MonthlyStatsCalculator(zone)
    fun tx(type: TransactionType, amount: Long, m: Int, memo: String, cat: String = "식비", y: Int = 2026) =
      TransactionEntity(type = type, amount = amount, category = cat, accountId = 1, timestamp = at(y, m, 10), memo = memo)
    val all = listOf(
      tx(TransactionType.INCOME, 3_000_000, 1, "급여", "급여"),
      tx(TransactionType.EXPENSE, 10_000, 1, "스타벅스"), tx(TransactionType.EXPENSE, 12_000, 3, "스타벅스"),
      tx(TransactionType.EXPENSE, 500_000, 3, "냉장고", "쇼핑"),
      tx(TransactionType.TRANSFER, 1_000_000, 4, "적금", "저축/투자이체"),
      tx(TransactionType.EXPENSE, 90_000, 6, "작년", y = 2025)
    )
    val r = YearlyReports.build(all, 2026, zone, stats)
    assertEquals(522_000L, r.expense)
    assertEquals(3_000_000L, r.income)
    assertEquals(82, r.savingRate)
    assertEquals(3, r.topMonth!!.month)
    assertEquals("냉장고", r.places.first().name)
    assertEquals(2, r.places.first { it.name == "스타벅스" }.count)
    assertEquals(90_000L, r.previousExpense)
    assertEquals(261_000L, r.monthlyAverage)
    assertNull(YearlyReports.build(all, 2025, zone, stats).previousExpense)
    assertEquals(listOf(2026, 2025), YearlyReports.years(all, java.time.LocalDate.of(2026, 11, 24), zone))
    // The picture can be drawn.
    com.moneyflow.ui.analytics.YearlyReportImage.draw(r).recycle()
  }
}
