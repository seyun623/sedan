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

  @Test fun bankSalaryDepositsInCommonFormats() {
    val cases = listOf(
      "[Web발신]\n신한 11/25 09:01\n110-***-123456\n입금 3,500,000\n잔액 4,200,000\n (주)머니플로우" to "신한은행",
      "[Web발신]\n[KB]11/25 09:00\n123456**789\n(주)머니플로우\n입금\n3,500,000\n잔액4,200,000" to "KB국민은행",
      "[Web발신]\n[하나]11/25 09:00 123-******-12345 입금 3,500,000원 (주)머니플로우 잔액4,200,000원" to "하나은행",
      "[Web발신]\n농협 입금3,500,000원 11/25 09:00 351-****-1234-56 (주)머니플로우 잔액4,200,000원" to "NH농협은행",
      "[카카오뱅크] 입금 3,500,000원 (주)머니플로우 11/25 09:00 잔액 4,200,000원" to "카카오뱅크"
    )
    for ((text, bank) in cases) {
      val p = ok(text)
      assertEquals(text, TransactionType.INCOME, p.type)
      assertEquals(text, 3_500_000L, p.amount)
      assertEquals(text, "(주)머니플로우", p.merchant)
      assertEquals(text, bank, p.provider)
    }
  }

  @Test fun salaryKeywordInSenderIsSalary() {
    val p = ok("[Web발신]\n우리 11/25 09:00\n1002***123456\n입금 3,500,000원\n(주)머니플로우급여\n잔액 4,200,000원")
    assertEquals(FinanceCategories.SALARY, p.category)
    assertEquals("우리은행", p.provider)
  }

  @Test fun someoneSentMeMoneyIsIncome() {
    val p = ok("[토스뱅크] 홍길동님이 50,000원을 보냈어요. 잔액 4,200,000원")
    assertEquals(TransactionType.INCOME, p.type)
    assertEquals(50_000L, p.amount)
    assertEquals("홍길동", p.merchant)
  }

  @Test fun iSentMoneyIsExpense() {
    val p = ok("[토스뱅크] 홍길동님에게 50,000원을 보냈어요.")
    assertEquals(TransactionType.EXPENSE, p.type)
    assertEquals("홍길동", p.merchant)
  }

  // ---- 2.13: real-world layouts (KakaoTalk 알림톡, short card tags, labelled lines) ----

  private data class Exp(val title: String, val body: String, val amount: Long, val merchant: String, val type: TransactionType, val provider: String? = null)

  @Test fun realWorldAlerts() = listOf(
    Exp("신한카드", "[신한카드 승인 안내]\n홍*동님, 신한카드(1234) 승인 내역입니다.\n\n▶승인금액 : 12,500원(일시불)\n▶승인일시 : 11/25 14:32\n▶가맹점명 : 스타벅스 강남R점\n▶누적금액 : 1,234,560원", 12_500, "스타벅스 강남R점", TransactionType.EXPENSE, "신한카드"),
    Exp("KB국민카드", "KB국민카드 승인\n홍*동님\n카드 : KB국민카드(1*2*)\n승인금액 : 48,300원\n승인일시 : 11/25 19:05\n가맹점명 : 이마트 성수점\n할부 : 일시불\n누적금액 : 1,234,560원\n\n자세히 보기", 48_300, "이마트 성수점", TransactionType.EXPENSE, "KB국민카드"),
    Exp("현대카드", "홍*동 님, 현대카드 M(1234) 승인\n누적 이용금액: 1,234,560원\n금액: 33,000원 일시불\n일시: 11/25 12:10\n가맹점: 쿠팡\n결제 예정일: 12/14", 33_000, "쿠팡", TransactionType.EXPENSE, "현대카드"),
    Exp("", "[Web발신]\n삼성1337승인 홍*동\n7,500원 일시불\n11/25 12:34 스타벅스\n누적123,456원", 7_500, "스타벅스", TransactionType.EXPENSE, "삼성카드"),
    Exp("", "[Web발신]\n우리(9*0*)승인\n홍*동님\n8,900원 일시불\n11/25 13:10\n올리브영 강남\n누적1,234,560원", 8_900, "올리브영 강남", TransactionType.EXPENSE, "우리카드"),
    Exp("", "[Web발신]\nNH카드3*4*승인\n홍*동\n6,000원 일시불\n11/25 08:45\n메가MGC커피\n총누적1,234,560원", 6_000, "메가MGC커피", TransactionType.EXPENSE, "NH농협카드"),
    Exp("우리카드", "우리카드 승인\n승인금액 : 8,900\n가맹점 : 올리브영 강남\n이용일시 : 11/25 13:10", 8_900, "올리브영 강남", TransactionType.EXPENSE, "우리카드"),
    Exp("", "[Web발신]\n신한 11/25 09:01\n입출금(1234)\n입금 3,500,000\n잔액 4,200,000\n (주)머니플로우", 3_500_000, "(주)머니플로우", TransactionType.INCOME),
    Exp("토스", "GS25 역삼점에서 3,200원 결제했어요", 3_200, "GS25 역삼점", TransactionType.EXPENSE),
    Exp("KB국민카드", "KB국민카드1*2*승인\n홍*동님\n48,300원 일시불\n11/25 19:05\n이마트 성수점\n누적1,234,560원\n※이용대금명세서는 KB Pay에서 확인", 48_300, "이마트 성수점", TransactionType.EXPENSE)
  ).forEach { e ->
    val p = ok(e.body, title = e.title)
    assertEquals(e.body, e.amount, p.amount)
    assertEquals(e.body, e.merchant, p.merchant)
    assertEquals(e.body, e.type, p.type)
    e.provider?.let { assertEquals(e.body, it, p.provider) }
  }

  @Test fun bareCancelAndDeposits() {
    assertEquals(com.moneyflow.domain.capture.PaymentKind.CANCELLATION, ok("[Web발신]\n삼성1337취소 홍*동\n7,500원\n11/25 12:50 스타벅스").kind)
    assertEquals(TransactionType.INCOME, ok("홍길동님이 내 입출금통장(1234)에 50,000원을 입금했어요", title = "카카오뱅크").type)
    // Smishing with a short link is still refused, a card company's own site is fine.
    assertTrue(parser.parse("", "[국외발신] 해외결제 승인 980,000원 홍*동 확인 bit.ly/abc", received) is ParseResult.Ignored)
    assertEquals(12_000L, ok("신한카드(1234)승인 홍*동 12,000원 11/25 10:00 스타벅스 https://www.shinhancard.com").amount)
  }
}
