package com.moneyflow.cardimport

import com.moneyflow.domain.cardimport.StatementTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StatementTextParserTest {
  private val parser = StatementTextParser(LocalDate.of(2026, 11, 23))

  /** 신한카드 마이페이지: merchant above the date line, amount below; statement totals on top. */
  @Test fun shinhanMerchantAboveDate() {
    val r = parser.parse(
      """
      마이페이지
      황*윤님
      함께한지 25년이 되었어요.
      12월 25일 명세서(예정)
      신용
      15,030원
      11월 25일 대금명세서
      92,820원
      최근이용내역
      DB손해보험
      2026.11.23 12:05 · 본인 782* · 일시불 · 자동납부
      33,170원
      DB손해보험
      2026.11.23 12:02 · 본인 782* · 일시불 · 자동납부
      51,060원
      쿠팡(쿠페이)
      2026.11.19 09:14 · 본인 782* · 3개월
      8,590원
      """.trimIndent()
    )
    assertEquals(listOf("DB손해보험", "DB손해보험", "쿠팡 쿠페이"), r.items.map { it.merchant })
    assertEquals(listOf(33_170L, 51_060L, 8_590L), r.items.map { it.amount })
    assertEquals(LocalTime.of(12, 5), r.items[0].time)
    assertEquals(3, r.items[2].installmentMonths)
  }

  @Test fun merchantAndAmountOnOneLineAboveDate() {
    val r = parser.parse(
      """
      최근이용내역
      스타벅스 역삼점 5,600원
      2026.11.21 14:32 · 본인 · 일시불
      GS25 강남점 3,500원
      2026.11.20 09:10 · 본인 · 일시불
      """.trimIndent()
    )
    assertEquals(listOf("스타벅스 역삼점", "GS25 강남점"), r.items.map { it.merchant })
    assertEquals(listOf(5_600L, 3_500L), r.items.map { it.amount })
  }

  @Test fun oneLineRows() {
    val r = parser.parse(
      """
      이용일자 가맹점명 이용금액
      2026.11.21 스타벅스 역삼점 5,600원
      2026.11.20 쿠팡 32,000원 일시불
      2026.11.19 이마트 성수점 120,000원 3개월
      """.trimIndent()
    )
    assertEquals(3, r.items.size)
    assertEquals(LocalDate.of(2026, 11, 21), r.items[0].date)
    assertEquals("스타벅스 역삼점", r.items[0].merchant)
    assertEquals(5_600L, r.items[0].amount)
    assertEquals("쿠팡", r.items[1].merchant)
    assertEquals(3, r.items[2].installmentMonths)
  }

  @Test fun multiLineMobileRows() {
    val r = parser.parse(
      """
      11.21
      14:32
      스타벅스
      5,600원
      일시불
      11.20
      카카오T
      12,300원
      """.trimIndent()
    )
    assertEquals(2, r.items.size)
    assertEquals("스타벅스", r.items[0].merchant)
    assertEquals(LocalTime.of(14, 32), r.items[0].time)
    assertEquals(LocalDate.of(2026, 11, 21), r.items[0].date)
    assertEquals("카카오T", r.items[1].merchant)
    assertEquals(12_300L, r.items[1].amount)
  }

  @Test fun cancellationsAreFlagged() {
    val r = parser.parse("26.11.18 GS25 강남점 승인취소 -3,500원")
    assertTrue(r.items.single().isCancel)
    assertEquals(3_500L, r.items.single().amount)
    assertEquals("GS25 강남점", r.items.single().merchant)
  }

  @Test fun balanceLimitAndPointFiguresAreNotAmounts() {
    val r = parser.parse("2026-11-17 올리브영 적립 120원 18,900원")
    assertEquals(18_900L, r.items.single().amount)
  }

  @Test fun previousYearForDecemberDatesReadInNovember() {
    val r = parser.parse("12.30 넷플릭스 13,500원")
    assertEquals(LocalDate.of(2025, 12, 30), r.items.single().date)
  }

  @Test fun performanceFigures() {
    val r = parser.parse(
      """
      전월 이용실적 412,000원
      이번 달 실적
      218,500원
      """.trimIndent()
    )
    assertEquals(listOf(412_000L, 218_500L), r.performance.map { it.amount })
    assertTrue(r.performance[0].label.contains("실적"))
  }

  @Test fun linesWithoutAmountsAreIgnored() {
    val r = parser.parse("2026.11.01 공지사항: 카드 이용 안내\n고객센터 1588-8100")
    assertTrue(r.items.isEmpty())
  }
}
