package com.moneyflow

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.capture.FinancialApps
import com.moneyflow.domain.capture.PaymentMessageParser
import com.moneyflow.domain.report.CardDeduction
import com.moneyflow.domain.report.DeductionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 2.17: transit cards and the 연말정산 card deduction preview. */
class V217TransitTaxTest {
  private val parser = PaymentMessageParser(ZoneId.of("Asia/Seoul"))

  @Test fun transitIsFiledAsTransport() {
    assertEquals("교통", parser.classify("후불교통 이용대금", "신한카드 후불교통 45,600원"))
    assertEquals("교통", parser.classify("기후동행카드 충전", ""))
    assertEquals(FinancialApps.Gate.ALLOWED, FinancialApps.gate("com.lgt.tmoney", "모바일티머니"))
  }

  private val credit = AccountEntity(id = 1, name = "신한카드", bankName = "신한카드", accountType = AccountType.CREDIT_CARD, balance = 0, isDebt = true)
  private val check = AccountEntity(id = 2, name = "KB 체크카드", bankName = "KB국민카드", accountType = AccountType.CREDIT_CARD, balance = 0)
  private fun tx(amount: Long, memo: String, account: Long, method: String = "카드", type: TransactionType = TransactionType.EXPENSE) =
    TransactionEntity(type = type, amount = amount, category = "기타지출", accountId = account, timestamp = 0, memo = memo, paymentMethod = method)

  @Test fun kindsAreSorted() {
    assertEquals(DeductionKind.CREDIT, CardDeduction.kindOf(tx(1, "스타벅스", 1), credit))
    assertEquals(DeductionKind.DEBIT, CardDeduction.kindOf(tx(1, "스타벅스", 2), check))
    assertEquals(DeductionKind.MARKET, CardDeduction.kindOf(tx(1, "남대문시장 순대국", 1), credit))
    assertEquals(DeductionKind.MARKET, CardDeduction.kindOf(tx(1, "가게", 9, method = "온누리상품권"), null))
    assertEquals(DeductionKind.TRANSIT, CardDeduction.kindOf(tx(1, "후불교통", 1), credit))
    assertEquals(DeductionKind.CREDIT, CardDeduction.kindOf(tx(1, "카카오T 택시", 1), credit))
    assertNull(CardDeduction.kindOf(tx(1, "DB손해보험", 1), credit))
    assertNull(CardDeduction.kindOf(tx(1, "아파트 관리비", 1), credit))
  }

  @Test fun deductionFollowsTheThresholdOrder() {
    // 총급여 4천만 원 → 문턱 1천만 원. 신용 1,200만 + 체크 300만 + 대중교통 100만.
    val txs = listOf(tx(12_000_000, "백화점", 1), tx(3_000_000, "마트", 2), tx(1_000_000, "후불교통", 1))
    val r = CardDeduction.estimate(40_000_000, txs, listOf(credit, check), months = 12)
    assertEquals(10_000_000L, r.threshold)
    assertEquals(0L, r.toThreshold)
    // (1,200−1,000)×15% + 300×30% + 100×40% = 30 + 90 + 40 = 160만 원
    assertEquals(1_600_000L, r.deduction)
    assertTrue(CardDeduction.advice(r).contains("체크카드"))
    // Half a year so far: the projection doubles the spending.
    val half = CardDeduction.estimate(40_000_000, txs, listOf(credit, check), months = 6)
    assertTrue(half.projectedDeduction > half.deduction)
  }

  @Test fun limitsApply() {
    val r = CardDeduction.estimate(40_000_000, listOf(tx(60_000_000, "마트", 2)), listOf(check), months = 12)
    assertEquals(3_000_000L, r.deduction)
  }
}
