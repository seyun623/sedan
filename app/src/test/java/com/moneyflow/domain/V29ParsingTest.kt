package com.moneyflow.domain

import com.moneyflow.domain.capture.ParseResult
import com.moneyflow.domain.capture.PaymentKind
import com.moneyflow.domain.capture.PaymentMessageParser
import com.moneyflow.domain.cardimport.AiStatementReader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class V29ParsingTest {
  private val parser = PaymentMessageParser(ZoneId.of("Asia/Seoul"))
  private val now = 1_795_000_000_000L
  private fun amountOf(text: String): Long = when (val r = parser.parse("", text, now)) {
    is ParseResult.Success -> r.payment.amount
    is ParseResult.Ignored -> throw AssertionError("ignored: ${r.reason} / $text")
  }

  @Test fun amountsAreNotTakenFromNamesOrDates() {
    assertEquals(50_000L, amountOf("[KB]11/25 14:30 801-***-***123 원할머니보쌈 출금 50,000원 잔액1,000,000원"))
    assertEquals(12_000L, amountOf("[하나카드] 승인 11/25 14:30 홍*동 12,000 스타벅스"))
    assertEquals(4_500L, amountOf("스타벅스에서 4,500원을 결제했어요"))
  }

  @Test fun foreignAmountsKeepThousands() {
    val p = (parser.parse("", "삼성카드 해외승인 홍*동 USD 1,234.50 AMAZON", now) as ParseResult.Success).payment
    assertEquals(PaymentKind.FOREIGN_CURRENCY, p.kind)
    assertEquals("USD 1,234.50", p.foreignAmount)
  }

  @Test fun merchantDomainsAreFine() {
    assertTrue(parser.parse("", "[신한카드] 승인 홍*동 17,000원 11/25 10:00 NETFLIX.COM", now) is ParseResult.Success)
  }

  @Test fun aiAnswersAreCheckedAgainstTheScreen() {
    val json = JSONObject("""{"transactions":[
      {"date":"2026-11-20","merchant":"스타벅스","amount":4500,"cancel":false},
      {"date":"2026-11-20","merchant":"지어낸 가게","amount":99000,"cancel":false},
      {"date":"2019-01-01","merchant":"옛날","amount":4500,"cancel":false}],"performance":[]}""")
    val r = AiStatementReader.parseResult(json, "AI", LocalDate.of(2026, 11, 24), "11.20 스타벅스 4,500원")
    assertEquals(listOf("스타벅스"), r.items.map { it.merchant })
  }

  @Test fun personalNumbersAreMaskedBeforeAi() {
    val t = AiStatementReader.redact("홍길동 010-1234-5678 카드 1234-56**-****-7890 계좌 110-123-456789 a@b.com 2026-11-21 4,500원")
    assertFalse(t.contains("5678") || t.contains("7890") || t.contains("456789") || t.contains("a@b.com"))
    assertTrue(t.contains("2026-11-21") && t.contains("4,500원"))
  }
}
