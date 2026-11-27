package com.moneyflow.domain

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.alerts.BillReminders
import com.moneyflow.domain.cleanup.TransactionCleanup
import com.moneyflow.util.ProblemLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V26FeaturesTest {
  private val card = AccountEntity(id = 7, name = "현대카드", bankName = "현대카드", accountType = AccountType.CREDIT_CARD, balance = 420_000, isDebt = true, paymentDay = 25)

  @Test fun cardPaymentDayIsRemindedThreeDaysBeforeOnce() {
    assertEquals(emptyList<Any>(), BillReminders.due(LocalDate.of(2026, 11, 21), listOf(card), emptyList(), emptySet()))
    val r = BillReminders.due(LocalDate.of(2026, 11, 22), listOf(card), emptyList(), emptySet()).single()
    assertTrue(r.title, r.title.contains("결제일"))
    assertTrue(r.text.contains("3일 뒤") && r.text.contains("420,000"))
    assertEquals(emptyList<Any>(), BillReminders.due(LocalDate.of(2026, 11, 23), listOf(card), emptyList(), setOf(r.key)))
    // Day 31 in a 30-day month falls on the 30th; after it passes, next month's date is used.
    assertEquals(LocalDate.of(2026, 11, 30), BillReminders.nextDate(LocalDate.of(2026, 11, 2), 31))
    assertEquals(LocalDate.of(2026, 12, 25), BillReminders.nextDate(LocalDate.of(2026, 11, 26), 25))
  }

  @Test fun recurringPaymentIsRemindedTheDayBefore() {
    val rent = RecurringTransactionEntity(id = 3, title = "월세", type = TransactionType.EXPENSE, amount = 700_000, category = "주거/통신",
      dayOfMonth = 25, accountId = 1, accountName = "생활비 통장", lastExecutedMonth = "2026-10")
    val salary = rent.copy(id = 4, title = "급여", type = TransactionType.INCOME)
    val r = BillReminders.due(LocalDate.of(2026, 11, 24), emptyList(), listOf(rent, salary), emptySet()).single()
    assertTrue(r.title.contains("월세"))
    assertEquals(emptyList<Any>(), BillReminders.due(LocalDate.of(2026, 11, 24), emptyList(), listOf(rent.copy(lastExecutedMonth = "2026-11")), emptySet()))
    assertEquals(emptyList<Any>(), BillReminders.due(LocalDate.of(2026, 11, 23), emptyList(), listOf(rent), emptySet()))
  }

  private fun tx(id: Long, minute: Long, amount: Long, memo: String, account: Long = 1, category: String = "식비", source: TransactionSource = TransactionSource.MANUAL) =
    TransactionEntity(id = id, type = TransactionType.EXPENSE, amount = amount, category = category, accountId = account,
      timestamp = 1_700_000_000_000 + minute * 60_000, memo = memo, source = source)

  @Test fun findsDuplicates() {
    val txs = listOf(
      tx(1, 0, 12_000, "스타벅스", source = TransactionSource.NOTIFICATION),
      tx(2, 3, 12_000, "스타벅스 강남", source = TransactionSource.SMS),      // same payment from SMS
      tx(3, 300, 12_000, "스타벅스"),                                         // same place, same day
      tx(4, 1, 12_000, "스타벅스", account = 2),                              // other account
      tx(5, 2, 9_000, "스타벅스"),                                            // other amount
      tx(6, 600, 50_000, "이마트"), tx(7, 900, 50_000, "다이소")               // different places hours apart
    )
    val groups = TransactionCleanup.duplicates(txs)
    assertEquals(listOf(listOf(1L, 2L, 3L)), groups.map { g -> g.items.map { it.id } })
    assertEquals(24_000L, groups.single().extra)
    assertTrue(TransactionCleanup.duplicates(txs, setOf("1,2,3")).isEmpty())
  }

  @Test fun groupsOtherTransactionsWithSuggestions() {
    val txs = listOf(
      tx(1, 0, 5_000, "메가커피", category = FinanceCategories.OTHER_EXPENSE),
      tx(2, 2000, 4_500, "메가커피", category = FinanceCategories.OTHER_EXPENSE),
      tx(3, 4000, 4_000, "메가커피", category = "카페/간식"),
      tx(4, 6000, 30_000, "알수없음", category = FinanceCategories.OTHER_EXPENSE)
    )
    val groups = TransactionCleanup.others(txs)
    assertEquals(listOf("메가커피", "알수없음"), groups.map { it.merchant })
    assertEquals("카페/간식", groups.first().suggestion)
    assertEquals(9_500L, groups.first().total)
  }

  @Test fun problemLogKeepsShortTraces() {
    val text = ProblemLog.format("자동 백업", IllegalStateException("폴더 없음", RuntimeException("원인")), "2026-11-24 12:00:00", "2.6.0")
    assertTrue(text.startsWith("[2026-11-24 12:00:00] v2.6.0 자동 백업"))
    assertTrue(text.contains("IllegalStateException") && text.contains("Caused by"))
  }
}
