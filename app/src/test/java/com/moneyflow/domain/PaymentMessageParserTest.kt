package com.moneyflow.domain

import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.capture.ParseResult
import com.moneyflow.domain.capture.ParsedPayment
import com.moneyflow.domain.capture.PaymentKind
import com.moneyflow.domain.capture.PaymentMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PaymentMessageParserTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private val parser = PaymentMessageParser(zone)
  private val received = LocalDateTime.of(2026, 11, 21, 14, 35).atZone(zone).toInstant().toEpochMilli()

  private fun ok(text: String, title: String = "", at: Long = received): ParsedPayment =
    when (val r = parser.parse(title, text, at)) {
      is ParseResult.Success -> r.payment
      is ParseResult.Ignored -> { fail("expected success but ignored: ${r.reason} for '$text'"); error("") }
    }

  private fun ignored(text: String) {
    val r = parser.parse("", text, received)
    assertTrue("expected ignored: '$text' -> $r", r is ParseResult.Ignored)
  }

  @Test fun shinhanCardApproval_picksApprovalAmountNotCumulative() {
    val p = ok("[Web발신] 신한카드(1234)승인 홍*동 12,500원(일시불)11/21 14:32 스타벅스 누적1,234,560원")
    assertEquals(PaymentKind.PAYMENT, p.kind)
    assertEquals(TransactionType.EXPENSE, p.type)
    assertEquals(12_500L, p.amount)
    assertEquals("스타벅스", p.merchant)
    assertEquals("카페/간식", p.category)
    assertEquals("신한카드", p.provider)
    assertEquals("1234", p.cardLast4)
    assertEquals(LocalDateTime.of(2026, 11, 21, 14, 32).atZone(zone).toInstant().toEpochMilli(), p.paidAt)
  }

  @Test fun cumulativeWithSpace_isNotTheAmount() {
    val p = ok("KB국민카드 1*2*승인 김*수 48,300원 일시불 11/20 19:05 이마트 누적 1,234,560원")
    assertEquals(48_300L, p.amount)
    assertEquals("생활/마트", p.category)
  }

  @Test fun bankWithdrawal_ignoresBalance() {
    val p = ok("[KB] 11/21 09:10 출금 50,000원 잔액1,234,567원 홍길동")
    assertEquals(50_000L, p.amount)
    assertEquals(TransactionType.EXPENSE, p.type)
  }

  @Test fun cancellation_isMarkedAsCancellation() {
    val p = ok("[Web발신] 신한카드(1234)승인취소 홍*동 12,500원 11/21 14:40 스타벅스")
    assertEquals(PaymentKind.CANCELLATION, p.kind)
    assertEquals(12_500L, p.amount)
  }

  @Test fun foreignCurrency_isFlaggedWithoutKrwAmount() {
    val p = ok("하나카드(1234)해외승인 홍*동 USD 12.50 11/21 03:10 AMAZON")
    assertEquals(PaymentKind.FOREIGN_CURRENCY, p.kind)
    assertEquals(0L, p.amount)
  }

  @Test fun installment_isDetected() {
    val p = ok("현대카드 승인 홍*동 900,000원 03개월 11/21 10:00 삼성전자")
    assertEquals(900_000L, p.amount)
    assertEquals(3, p.installmentMonths)
  }

  @Test fun tossPayStyle_extractsMerchant() {
    val p = ok("스타벅스에서 5,600원 결제했어요", title = "토스")
    assertEquals(5_600L, p.amount)
    assertEquals("스타벅스", p.merchant)
    assertEquals("카페/간식", p.category)
  }

  @Test fun cashback_doesNotMakeIncome() {
    val p = ok("삼성카드 승인 12,000원 11/21 12:00 GS25 역삼점 (캐시백 120원 적립예정)")
    assertEquals(TransactionType.EXPENSE, p.type)
    assertEquals(12_000L, p.amount)
    assertEquals("GS25 역삼점", p.merchant)
    assertEquals("생활/마트", p.category)
  }

  @Test fun bankDeposit_isIncome() {
    val p = ok("[신한은행] 11/21 09:00 입금 3,200,000원 (주)머니플로우 급여 잔액 4,500,000원")
    assertEquals(TransactionType.INCOME, p.type)
    assertEquals(3_200_000L, p.amount)
    assertEquals(FinanceCategories.SALARY, p.category)
  }

  @Test fun cardBillWithdrawal_isTransferNotExpense() {
    val p = ok("[신한은행] 신한카드 결제대금 출금 640,000원 11/15 08:00 잔액 1,000,000원")
    assertEquals(TransactionType.TRANSFER, p.type)
    assertEquals(FinanceCategories.CARD_PAYMENT, p.category)
  }

  @Test fun providerIsEarliestMention() {
    val p = ok("현대카드 승인 홍*동 33,000원 11/21 10:00 토스페이먼츠")
    assertEquals("현대카드", p.provider)
  }

  @Test fun smartStoreIsShoppingNotMart() {
    assertEquals("쇼핑", parser.classify("네이버페이 스마트스토어", ""))
  }

  @Test fun unknownMerchant_usesAppCategoryName() {
    val p = ok("롯데카드 승인 15,000원 11/21 10:00 어떤가게")
    assertEquals(FinanceCategories.OTHER_EXPENSE, p.category)
  }

  @Test fun yearRollover_decemberMessageReadInJanuary() {
    val jan1 = LocalDateTime.of(2027, 1, 1, 0, 5).atZone(zone).toInstant().toEpochMilli()
    val p = ok("신한카드 승인 10,000원 12/31 23:59 편의점", at = jan1)
    assertEquals(LocalDateTime.of(2026, 12, 31, 23, 59).atZone(zone).toInstant().toEpochMilli(), p.paidAt)
  }

  @Test fun noDateInMessage_paidAtIsNull() {
    assertNull(ok("스타벅스에서 5,600원 결제했어요").paidAt)
  }

  @Test fun chatMessagesAreRejected() {
    ignored("어제 치킨 20,000원 나눠서 보낼게")
    ignored("회원님 오늘 병원 예약 잊지 마세요")
    ignored("이번 주 점심 메뉴 추천해줘")
  }
}
