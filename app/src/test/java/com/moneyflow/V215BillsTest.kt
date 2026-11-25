package com.moneyflow

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.domain.alerts.BillReminders
import com.moneyflow.domain.card.BillText
import com.moneyflow.domain.card.CardBill
import com.moneyflow.domain.cardimport.StatementTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V215BillsTest {
  private val today = LocalDate.of(2026, 11, 25)

  @Test fun billNoticesFromCardCompanies() {
    val shinhan = BillText.find("[신한카드] 황*윤님 12월 결제예정금액 523,000원 결제일 12/14 자세히 보기", today, message = true)!!
    assertEquals(523_000, shinhan.amount)
    assertEquals(LocalDate.of(2026, 12, 14), shinhan.dueDate)
    val hyundai = BillText.find("현대카드 이용대금 명세서 안내\n청구금액: 1,234,560원\n결제일: 2026년 12월 25일", today, message = true)!!
    assertEquals(1_234_560, hyundai.amount)
    assertEquals(LocalDate.of(2026, 12, 25), hyundai.dueDate)
    // Next January read in late November is still upcoming.
    assertEquals(LocalDate.of(2027, 1, 14), BillText.find("결제하실 금액 88,000원 (1/14 결제)", LocalDate.of(2026, 12, 20), message = true)!!.dueDate)
  }

  @Test fun notBills() {
    assertNull(BillText.find("(광고)[삼성카드] 결제금액의 5% 캐시백 최대 10,000원", today, message = true))
    assertNull(BillText.find("[KB국민카드] 결제 금액 안내 이벤트 응모하고 3,000원 받기", today, message = true))
    // Weak words count on a card company's screen, not in a message.
    assertNull(BillText.find("결제금액 30,000원", today, message = true))
    assertEquals(30_000, BillText.find("결제금액 30,000원", today)!!.amount)
    // The minimum payment is not the bill.
    assertEquals(400_000L, BillText.find("결제하실 금액 최소결제금액 40,000원 400,000원", today)?.amount)
  }

  @Test fun statementScreenReadsTheBill() {
    val screen = "MY 카드\n12월 결제하실 금액\n412,300원\n결제일 2026.12.14\n이용내역\n11.21\n스타벅스\n5,600원"
    val r = StatementTextParser(today).parse(screen)
    assertEquals(412_300L, r.bill?.amount)
    assertEquals(LocalDate.of(2026, 12, 14), r.bill?.dueDate)
    assertTrue(r.items.any { it.merchant.contains("스타벅스") })
    assertTrue(r.items.none { it.amount == 412_300L })
  }

  @Test fun reminderUsesTheBill() {
    val card = AccountEntity(id = 1, name = "신한카드", bankName = "신한카드", accountType = AccountType.CREDIT_CARD, balance = 900_000, isDebt = true)
    val bill = CardBill(523_000, LocalDate.of(2026, 11, 27).toEpochDay(), "카드사 안내", 0)
    val r = BillReminders.due(today, listOf(card), emptyList(), emptySet(), mapOf(1L to bill)).single()
    assertTrue(r.text, r.text.contains("523,000원") && r.text.contains("2일 뒤"))
    // An old bill (already paid) is ignored; without 결제일 there is no reminder.
    val old = CardBill(100, LocalDate.of(2026, 11, 1).toEpochDay(), "카드사 안내", 0)
    assertTrue(BillReminders.due(today, listOf(card), emptyList(), emptySet(), mapOf(1L to old)).isEmpty())
  }
}
