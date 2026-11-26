package com.moneyflow

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.RuleRepository
import com.moneyflow.domain.shop.AiOrderReader
import com.moneyflow.domain.shop.OrderMatcher
import com.moneyflow.domain.shop.OrderTextParser
import com.moneyflow.domain.shop.ShopOrder
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 2.16: product names from shopping-site orders onto card payments. */
@RunWith(RobolectricTestRunner::class)
class V216ShopOrdersTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private val today = LocalDate.of(2026, 11, 26)
  private fun millis(d: LocalDateTime) = d.atZone(zone).toInstant().toEpochMilli()

  @Test fun coupangOrderListIsRead() {
    val screen = """
      주문목록
      2026. 11. 25 주문
      배송완료
      11/26(목) 도착
      제주 삼다수 2L, 12개
      12,900원 1개
      장바구니 담기
      2026. 11. 20 주문
      배송완료
      코멧 물티슈 100매 10팩
      9,900원 1개
      로켓와우 무료배송
      다우니 섬유유연제 1L
      7,500원 1개
    """.trimIndent()
    val orders = OrderTextParser(today, "쿠팡").parse(screen)
    assertEquals(2, orders.size)
    assertEquals(ShopOrder(LocalDate.of(2026, 11, 25), 12_900, listOf("제주 삼다수 2L,"), "쿠팡").date, orders[0].date)
    assertEquals(12_900L, orders[0].amount)
    assertTrue(orders[0].items.first().startsWith("제주 삼다수"))
    assertEquals(17_400L, orders[1].amount)
    assertEquals(2, orders[1].items.size)
  }

  @Test fun theOrderTotalWinsOverItemPrices() {
    val orders = OrderTextParser(today, "컬리").parse("2026.11.24 주문\n유기농 우유 900ml\n3,500원\n쿠폰 할인 -1,000원\n배송비 3,000원\n결제금액 5,500원")
    assertEquals(5_500L, orders.single().amount)
  }

  @Test fun ordersMatchPaymentsOfTheSameAmount() {
    fun tx(id: Long, memo: String, amount: Long, day: Int) = TransactionEntity(
      id = id, type = TransactionType.EXPENSE, amount = amount, category = "쇼핑", accountId = 1,
      timestamp = millis(LocalDateTime.of(2026, 11, day, 12, 0)), memo = memo
    )
    val payments = listOf(
      tx(1, "쿠팡(쿠페이)", 12_900, 25),
      tx(2, "편의점", 12_900, 25), // same amount, not the shop: the shop-named one wins
      tx(3, "쿠팡 (할부 3개월)", 17_400, 22),
      tx(4, "쿠팡", 5_000, 10) // too long before
    )
    val orders = listOf(
      ShopOrder(LocalDate.of(2026, 11, 25), 12_900, listOf("삼다수 2L 12개"), "쿠팡"),
      ShopOrder(LocalDate.of(2026, 11, 20), 17_400, listOf("물티슈", "섬유유연제"), "쿠팡"),
      ShopOrder(LocalDate.of(2026, 11, 1), 5_000, listOf("과자"), "쿠팡")
    )
    val m = OrderMatcher.match(orders, payments, zone)
    assertEquals(listOf(3L, 1L), m.map { it.transaction.id })
    assertEquals("쿠팡 · 물티슈 외 1건 (할부 3개월)", m[0].newMemo)
    assertEquals("쿠팡(쿠페이) · 삼다수 2L 12개", m[1].newMemo)
    // The learned category still sees the same shop name as before.
    assertEquals(RuleRepository.baseMerchant("쿠팡(쿠페이)"), RuleRepository.baseMerchant(m[1].newMemo))
    assertEquals("쿠팡", RuleRepository.baseMerchant(m[0].newMemo))
  }

  @Test fun aiAnswerIsChecked() {
    val arr = JSONArray("""[{"date":"2026-11-25","amount":12900,"items":["삼다수"]},{"date":"2027-01-01","amount":100,"items":[]},{"date":"x","amount":1,"items":[]}]""")
    assertEquals(1, AiOrderReader.parse(arr, "쿠팡", today).size)
  }
}
