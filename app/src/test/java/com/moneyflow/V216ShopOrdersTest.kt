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
    val arr = JSONArray("""[{"date":"2026-11-25","amount":12900,"items":["삼다수"]},{"date":"2027-01-01","amount":100,"items":[]},{"date":"x","amount":1,"items":[]},{"date":"2026-11-24","amount":4500,"items":["지어낸 것"]}]""")
    // The made-up 4,500 is not on the screen.
    assertEquals(listOf(12_900L), AiOrderReader.parse(arr, "쿠팡", today, "2026.11.25 주문 삼다수 12,900원").map { it.amount })
    val masked = AiOrderReader.redactDelivery("받는 분 : 홍길동\n배송지: 서울시 강남구 테헤란로 123\n안심번호 0504-1234-5678\n삼다수 12,900원")
    assertTrue(masked, !masked.contains("홍길동") && !masked.contains("테헤란로") && !masked.contains("0504") && masked.contains("12,900원"))
  }

  // ---- 2.16.1: from the 2.16 review ----

  private fun pay(id: Long, memo: String, amount: Long, day: Int) = TransactionEntity(
    id = id, type = TransactionType.EXPENSE, amount = amount, category = "쇼핑", accountId = 1,
    timestamp = millis(LocalDateTime.of(2026, 11, day, 12, 0)), memo = memo
  )

  @Test fun readingTheSamePageAgainChangesNothing() {
    val order = ShopOrder(LocalDate.of(2026, 11, 25), 4_500, listOf("커피 원두"), "쿠팡")
    val first = OrderMatcher.match(listOf(order), listOf(pay(1, "쿠팡", 4_500, 25), pay(2, "스타벅스 강남점", 4_500, 25)), zone)
    assertEquals(1L, first.single().transaction.id)
    val linked = pay(1, first.single().newMemo, 4_500, 25)
    assertTrue(OrderMatcher.match(listOf(order), listOf(linked, pay(2, "스타벅스 강남점", 4_500, 25)), zone).isEmpty())
  }

  @Test fun sameOrderOnNearbyDaysTakesItsOwnDay() {
    val orders = listOf(
      ShopOrder(LocalDate.of(2026, 11, 20), 12_900, listOf("A"), "쿠팡"),
      ShopOrder(LocalDate.of(2026, 11, 22), 12_900, listOf("B"), "쿠팡")
    )
    val m = OrderMatcher.match(orders, listOf(pay(2, "쿠팡", 12_900, 22), pay(1, "쿠팡", 12_900, 20)), zone)
    assertEquals(listOf(1L, 2L), m.map { it.transaction.id })
  }

  @Test fun installmentPartsAndOtherShopsAreNotPicked() {
    val order = ShopOrder(LocalDate.of(2026, 11, 24), 10_000, listOf("양말"), "쿠팡")
    val m = OrderMatcher.match(listOf(order), listOf(pay(1, "쿠팡 (할부 2/3)", 10_000, 24), pay(2, "쿠팡", 10_000, 25)), zone)
    assertEquals(2L, m.single().transaction.id)
    val other = OrderMatcher.match(listOf(order), listOf(pay(3, "편의점", 10_000, 24)), zone).single()
    assertTrue(!other.namesShop)
  }

  @Test fun deliveryDatesTotalsDiscountsAndCancelsOnTheDevice() {
    val screen = """
      주문일 2026.11.20
      배송완료 2026.11.22
      구매확정 2026.11.27
      햇반 210g 24개
      판매가 23,500원
      카드 즉시 혜택 -2,000원
      결제금액 21,500원
      총 상품금액 23,500원
      2026.11.18 주문
      취소완료
      면도기
      89,000원
    """.trimIndent()
    val orders = OrderTextParser(today, "11번가").parse(screen)
    val o = orders.single()
    assertEquals(LocalDate.of(2026, 11, 20), o.date)
    assertEquals(21_500L, o.amount)
    assertTrue(o.items.toString(), o.items.none { it.contains("판매가") || it.contains("혜택") })
    assertEquals(19_800L, OrderTextParser(today, "G마켓").parse("2026.11.25 주문\n수건 세트\n9,900원 x 2개").single().amount)
  }

  @Test fun installmentTailSurvivesLongNames() {
    val order = ShopOrder(LocalDate.of(2026, 11, 24), 10_000, listOf("아주 긴 상품 이름 ".repeat(8)), "쿠팡")
    val memo = OrderMatcher.newMemo("주식회사 쿠팡 결제대행 서비스 이용 (할부 12/12)", order)
    assertTrue(memo, memo.endsWith("(할부 12/12)") && memo.length <= 90)
  }
}
