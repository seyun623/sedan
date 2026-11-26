package com.moneyflow

import com.moneyflow.data.repository.BudgetCarry
import com.moneyflow.data.repository.MonthBudget
import com.moneyflow.domain.family.FamilySettlement
import com.moneyflow.domain.family.SettleTransfer
import com.moneyflow.domain.receipt.ReceiptParser
import com.moneyflow.ui.transaction.ReceiptScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 2.18: 예산 이월, 가족 정산, 영수증 사진 입력. */
class V218Test {
  // ---- 예산 이월 ----
  private fun budget(m: YearMonth, total: Long) = MonthBudget(m, total, emptyMap(), null)
  private val nov = YearMonth.of(2026, 11)
  private val dec = YearMonth.of(2026, 12)

  @Test fun leftoverIsAddedAndOverspendingTakenOff() {
    assertEquals(200_000L, BudgetCarry.amount(budget(nov, 1_000_000), 800_000, budget(dec, 1_000_000)))
    assertEquals(-150_000L, BudgetCarry.amount(budget(nov, 1_000_000), 1_150_000, budget(dec, 1_000_000)))
    // No budget last month or this month: nothing carried.
    assertEquals(0L, BudgetCarry.amount(budget(nov, 0), 300_000, budget(dec, 1_000_000)))
    assertEquals(0L, BudgetCarry.amount(budget(nov, 1_000_000), 0, budget(dec, 0)))
  }

  // ---- 가족 정산 ----
  @Test fun sharedSpendingIsSplitEquallyWithFewestTransfers() {
    val s = FamilySettlement.settle(listOf("나" to 90_000L, "엄마" to 30_000L, "아빠" to 0L))
    assertEquals(120_000L, s.total)
    assertEquals(40_000L, s.share)
    assertEquals(listOf(SettleTransfer("아빠", "나", 40_000), SettleTransfer("엄마", "나", 10_000)), s.transfers)
  }

  @Test fun unevenWonIsNotLost() {
    val s = FamilySettlement.settle(listOf("나" to 10_001L, "엄마" to 0L))
    assertEquals(listOf(SettleTransfer("엄마", "나", 5_000)), s.transfers)
    assertTrue(FamilySettlement.settle(listOf("나" to 5_000L, "엄마" to 5_000L)).transfers.isEmpty())
    assertTrue(FamilySettlement.settle(listOf("나" to 0L, "엄마" to 0L)).isEmpty)
  }

  @Test fun sharedTagsAndMessage() {
    assertTrue(FamilySettlement.isShared("장보기 #공동"))
    assertTrue(FamilySettlement.isShared("외식 #더치"))
    assertFalse(FamilySettlement.isShared("공동구매 마스크"))
    val text = FamilySettlement.message("2026년 11월", FamilySettlement.settle(listOf("나" to 20_000L, "엄마" to 0L))) { "${it}원" }
    assertTrue(text.contains("→ 엄마이(가) 나에게 10000원"))
  }

  // ---- 영수증 ----
  private val parser = ReceiptParser(LocalDate.of(2026, 11, 26))

  @Test fun martReceipt() {
    val r = parser.parse(
      """
      이마트 성수점
      사업자 206-86-50913 대표 홍길동
      TEL 02-123-4567
      2026-11-25 18:32
      서울우유 1L        2,980
      신라면 5입          4,480
      대파                2,000
      과세물품가액       8,600
      부가세               860
      합계              9,460
      신용카드          9,460
      카드번호 1234-****-****-5678
      """.trimIndent()
    )!!
    assertEquals(9_460L, r.total)
    assertEquals("이마트 성수점", r.merchant)
    assertEquals(LocalDate.of(2026, 11, 25), r.date)
    assertEquals("서울우유 L", r.items.first())
    assertEquals("이마트 성수점 · 서울우유 L 외 2건", r.memo)
  }

  @Test fun amountOnTheNextLineAndLabelledShop() {
    val r = parser.parse(
      """
      [매출전표]
      상호: 스타벅스 강남점
      결제금액
      12,300원
      26.11.20
      """.trimIndent()
    )!!
    assertEquals(12_300L, r.total)
    assertEquals("스타벅스 강남점", r.merchant)
    assertEquals(LocalDate.of(2026, 11, 20), r.date)
  }

  @Test fun noTotalWordUsesLargestAmountAndNothingReadable() {
    assertEquals(15_000L, parser.parse("김밥천국\n김밥 3,000\n라면 4,000\n15,000원")!!.total)
    assertNull(parser.parse("영수증\n감사합니다"))
    // A future date (misread) is not used.
    assertNull(parser.parse("가게\n2027-05-01\n합계 5,000")!!.date)
  }

  @Test fun piecesOnTheSameRowAreJoined() {
    val text = ReceiptScanner.mergeRows(listOf(Triple("합계", 100, 20), Triple("9,460", 104, 20), Triple("신용카드", 140, 20)))
    assertEquals("합계 9,460\n신용카드", text)
  }

  /** 2.19.1: a date or time is never taken as the amount when there is no total line. */
  @Test fun dateIsNotTheAmount() {
    val r = parser.parse("동네분식\n2026-11-25 12:30\n떡볶이 4,500\n순대 3,000")!!
    assertEquals(4_500L, r.total)
  }
}
