package com.moneyflow

import com.moneyflow.domain.card.BillText
import com.moneyflow.domain.cardimport.SavedPages
import com.moneyflow.domain.cardimport.StatementExtraction
import com.moneyflow.domain.cardimport.StatementItem
import com.moneyflow.domain.cardimport.StatementTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Hourly review ② (card import): fixes found by the reviewers. */
class V2154ReviewTest {
  private val today = LocalDate.of(2026, 11, 26)

  @Test fun hyundaiPcRowsTakeTheShopNotTheCardName() {
    val screen = "최근 이용 내역\n네이버페이\n네이버 현대카드 Edition3 · 26. 9. 12 · 03:15 · 일시불\n13,680원\n" +
      "코스트코코리아\n국민행복 현대카드 · 26. 9. 9 · 15:36 · 일시불\n230,550원"
    val items = StatementTextParser(today).parse(screen).items
    assertEquals(listOf("네이버페이" to 13_680L, "코스트코코리아" to 230_550L), items.map { it.merchant to it.amount })
    assertEquals(LocalDate.of(2026, 9, 12), items.first().date)
  }

  @Test fun billAndTotalsAreNotPurchases() {
    val r = StatementTextParser(today).parse("12월 14일 결제하실 금액\n523,000원\n이용기간 10.13~11.12\n총 이용금액 1,234,000원\n11.21\n스타벅스\n5,600원")
    assertEquals(listOf(5_600L), r.items.map { it.amount })
    assertEquals(523_000L, r.bill?.amount)
  }

  @Test fun smallCancellationIsACancel() {
    val r = StatementTextParser(today).parse("11.21\n편의점\n-800원")
    assertTrue(r.items.single().isCancel)
  }

  @Test fun billPrefersTheTotal() {
    assertEquals(523_000L, BillText.find("결제 예정 금액\n일시불 400,000원\n할부 123,000원\n합계 523,000원", today)?.amount)
  }

  @Test fun twoEqualCoffeesOnOnePageStayTwo() {
    val coffee = StatementItem(LocalDate.of(2026, 11, 25), null, "스타벅스", 4_500)
    val page1 = StatementExtraction(listOf(coffee, coffee), emptyList(), "x")
    val page2 = StatementExtraction(listOf(coffee), emptyList(), "x")
    assertEquals(2, SavedPages.merge(listOf(page1, page2), "x").items.size)
  }
}
