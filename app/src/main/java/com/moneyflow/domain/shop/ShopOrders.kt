package com.moneyflow.domain.shop

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One order read from a shopping site's order list ("2026.11.25 주문 · 생수 2L×12 · 12,900원"). */
data class ShopOrder(val date: LocalDate, val amount: Long, val items: List<String>, val shop: String) {
  /** "생수 2L×12 외 1건" */
  val summary: String
    get() = when {
      items.isEmpty() -> ""
      items.size == 1 -> items.first()
      else -> "${items.first()} 외 ${items.size - 1}건"
    }
}

/** A ledger payment that is this order: its memo gets the product names. */
data class OrderMatch(val transaction: TransactionEntity, val order: ShopOrder, val newMemo: String)

/**
 * Reads orders from the visible text of a shopping site's 주문내역 page, on the device. Order list
 * pages put the order date on top of a block, then product names, then prices; the order total
 * ("결제금액", "총 주문금액") wins over the sum of item prices when shown.
 */
class OrderTextParser(private val today: LocalDate, private val shop: String) {

  fun parse(text: String): List<ShopOrder> {
    val lines = text.split('\n').map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
    val dated = lines.indices.mapNotNull { i -> orderDate(lines[i])?.let { i to it } }
    return dated.mapIndexedNotNull { n, (i, date) ->
      val end = dated.getOrNull(n + 1)?.first ?: lines.size
      block(date, lines.subList(i + 1, end))
    }.filter { it.amount > 0 }
  }

  private fun block(date: LocalDate, lines: List<String>): ShopOrder? {
    var total: Long? = null
    val prices = mutableListOf<Long>()
    val items = mutableListOf<String>()
    var previous: String? = null
    for (line in lines) {
      val amount = AMOUNT.find(line)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
      when {
        amount != null && TOTAL.containsMatchIn(line) -> total = amount
        amount != null && !NOT_PRICE.containsMatchIn(line) -> {
          prices += amount
          // The product name is on the price line or the line above it.
          val name = productName(line) ?: previous?.let(::productName)
          if (name != null && name !in items) items += name
        }
      }
      previous = if (amount == null) line else null
    }
    val sum = prices.sum()
    val amount = total ?: sum.takeIf { it > 0 } ?: return null
    return ShopOrder(date, amount, items.take(5), shop)
  }

  private fun productName(line: String): String? {
    val cleaned = line.replace(AMOUNT, " ").replace(QUANTITY, " ").replace(NOISE, " ")
      .replace(Regex("[\\[\\]()|·•:]+"), " ").replace(Regex("\\s+"), " ").trim()
    return cleaned.takeIf { it.length >= 2 && !it.all { c -> c.isDigit() || c == ' ' } }?.take(40)
  }

  /** "2026. 11. 25 주문", "2026.11.25", "11.25 주문" (order lists show the order date). */
  private fun orderDate(line: String): LocalDate? {
    if (line.length > 40) return null
    FULL_DATE.find(line)?.let { m ->
      val (y, mo, d) = m.destructured
      return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
    if (!ORDER_WORD.containsMatchIn(line)) return null
    SHORT_DATE.find(line)?.let { m ->
      val (mo, d) = m.destructured
      val date = runCatching { LocalDate.of(today.year, mo.toInt(), d.toInt()) }.getOrNull() ?: return null
      return if (date.isAfter(today.plusDays(1))) date.minusYears(1) else date
    }
    return null
  }

  companion object {
    private val FULL_DATE = Regex("(?<![0-9])(20\\d{2})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})")
    private val SHORT_DATE = Regex("(?<![0-9.])(\\d{1,2})\\s*[./월]\\s*(\\d{1,2})")
    private val ORDER_WORD = Regex("주문|결제|구매")
    private val AMOUNT = Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})\\s*원")
    private val TOTAL = Regex("결제\\s*금액|총\\s*(주문|결제|상품)?\\s*금액|합계|최종")
    /** Amounts that aren't what the item cost. */
    private val NOT_PRICE = Regex("할인|쿠폰|적립|배송비|포인트|캐시|정가|원래|무료배송|이상\\s*구매")
    private val QUANTITY = Regex("[0-9]+\\s*개|수량\\s*[0-9]+|x\\s*[0-9]+")
    private val NOISE = Regex(
      "배송\\s*(완료|중|준비)|도착(\\s*예정)?|주문\\s*(상세|번호|완료|취소)?|구매\\s*확정|교환|반품|리뷰|장바구니|" +
        "상세\\s*보기|다시\\s*담기|배송\\s*조회|판매자|로켓\\S*|무료\\s*배송|오늘\\s*출발"
    )
  }
}

/** Matches orders with ledger payments and makes the new memos (pure, testable). */
object OrderMatcher {
  /** Words in a card payment's memo that name the shop (card alerts use the company or PG name). */
  private val SHOP_WORDS = mapOf(
    "쿠팡" to listOf("쿠팡", "coupang", "쿠페이"),
    "네이버페이" to listOf("네이버", "naver"),
    "11번가" to listOf("11번가", "11st", "에스케이플래닛", "SK플래닛", "십일번가"),
    "G마켓" to listOf("지마켓", "G마켓", "gmarket", "이베이", "ebay", "스마일페이"),
    "SSG" to listOf("SSG", "쓱", "에스에스지", "이마트"),
    "컬리" to listOf("컬리", "kurly")
  )

  /**
   * For each order, the payment of exactly that amount from the order day to 3 days later
   * (the card charges at order or at shipping). A payment that names the shop wins; one already
   * carrying product names is left alone. Each payment is used once.
   */
  fun match(orders: List<ShopOrder>, payments: List<TransactionEntity>, zone: ZoneId): List<OrderMatch> {
    val used = mutableSetOf<Long>()
    return orders.sortedBy { it.date }.mapNotNull { order ->
      if (order.items.isEmpty()) return@mapNotNull null
      val words = SHOP_WORDS[order.shop].orEmpty()
      val candidates = payments.filter { tx ->
        tx.id !in used && tx.type == TransactionType.EXPENSE && tx.amount == order.amount &&
          !tx.memo.contains(" · ") &&
          ChronoUnit.DAYS.between(order.date, Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()) in 0..3
      }
      val tx = candidates.firstOrNull { c -> words.any { c.memo.contains(it, ignoreCase = true) } }
        ?: candidates.singleOrNull()
        ?: return@mapNotNull null
      used += tx.id
      OrderMatch(tx, order, newMemo(tx.memo, order))
    }
  }

  /** "쿠팡(쿠페이)" + 생수 → "쿠팡 · 생수 2L×12 외 1건" (the payment's own name stays in front). */
  fun newMemo(memo: String, order: ShopOrder): String {
    val head = memo.substringBefore(" (").trim().ifEmpty { order.shop }
    // Keep what followed the shop name ("(할부 3개월)").
    val tail = memo.removePrefix(memo.substringBefore(" (")).trim()
    return ("$head · ${order.summary.take(50)}" + if (tail.isNotEmpty()) " $tail" else "").take(90)
  }
}
