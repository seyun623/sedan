package com.moneyflow.domain.shop

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.InstallmentSplitter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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

/**
 * A ledger payment that is this order: its memo gets the product names. [namesShop] = the payment's
 * memo names the shop (or its payment service); other same-amount payments are only suggestions.
 */
data class OrderMatch(val transaction: TransactionEntity, val order: ShopOrder, val newMemo: String, val namesShop: Boolean)

/**
 * Reads orders from the visible text of a shopping site's 주문내역 page, on the device. Order list
 * pages put the order date on top of a block, then product names, then prices; the paid total
 * ("결제금액") wins over other totals and over the sum of item prices.
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
    // Cancelled / returned orders were not paid (or were refunded).
    if (lines.any { CANCELLED.containsMatchIn(it) }) return null
    var total: Pair<Int, Long>? = null
    val prices = mutableListOf<Long>()
    val items = mutableListOf<String>()
    var previous: String? = null
    for (line in lines) {
      val m = AMOUNT.find(line)
      val amount = m?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
      // "-2,000원" (discount) and "10%" are never prices.
      val negative = m != null && line.substring(0, m.range.first).trimEnd().endsWith("-")
      when {
        amount == null -> {}
        negative || line.contains('%') -> {}
        totalRank(line) != null -> {
          val rank = totalRank(line)!!
          if (total == null || rank < total!!.first) total = rank to amount
        }
        !NOT_PRICE.containsMatchIn(line) -> {
          val count = QTY_AFTER.find(line.substring(m!!.range.last))?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 2..99 } ?: 1
          prices += amount * count
          // The product name is on the price line or the line above it.
          val name = productName(line) ?: previous?.let(::productName)
          if (name != null && name !in items) items += name
        }
      }
      previous = if (amount == null) line else null
    }
    val sum = prices.sum()
    val amount = total?.second ?: sum.takeIf { it > 0 } ?: return null
    return ShopOrder(date, amount, items.take(5), shop)
  }

  /** The paid total first ("결제금액", "최종"), then order totals; null when not a total line. */
  private fun totalRank(line: String): Int? = when {
    PAID_TOTAL.containsMatchIn(line) -> 0
    OTHER_TOTAL.containsMatchIn(line) -> 1
    else -> null
  }

  private fun productName(line: String): String? {
    val cleaned = line.replace(AMOUNT, " ").replace(QUANTITY, " ").replace(NOISE, " ")
      .replace(Regex("[\\[\\]()|·•:,]+"), " ").replace(Regex("\\s+"), " ").trim()
    if (cleaned.length < 2 || cleaned.all { it.isDigit() || it == ' ' } || LABEL_ONLY.matches(cleaned)) return null
    return cleaned.take(40)
  }

  /** "2026. 11. 25 주문", "2026.11.25", "11.25 주문" — not delivery / confirmation dates. */
  private fun orderDate(line: String): LocalDate? {
    if (line.length > 40 || NOT_ORDER_DATE.containsMatchIn(line)) return null
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
    private val ORDER_WORD = Regex("주문(일|일자)?(?!\\s*상세)|결제일|구매일")
    /** Dates of delivery, confirmation, cancellation: not when the order was paid. */
    private val NOT_ORDER_DATE = Regex("도착|배송|확정|취소|반품|교환|환불|출발")
    private val CANCELLED = Regex("취소\\s*완료|주문\\s*취소됨|반품\\s*완료|환불\\s*완료|결제\\s*취소")
    private val AMOUNT = Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})\\s*원")
    private val PAID_TOTAL = Regex("결제\\s*금액|최종\\s*(결제)?\\s*금액|실\\s*결제")
    private val OTHER_TOTAL = Regex("총\\s*(주문|결제|상품)?\\s*금액|합계")
    /** Amounts that aren't what the item cost. */
    private val NOT_PRICE = Regex("할인|쿠폰|적립|배송비|포인트|캐시|정가|정상가|판매가|원가|혜택|무료배송|이상\\s*구매|상품\\s*금액")
    private val QUANTITY = Regex("[0-9]+\\s*개|수량\\s*[0-9]+|[xX×]\\s*[0-9]+")
    private val QTY_AFTER = Regex("^\\s*원?\\s*[xX×]\\s*([0-9]{1,2})")
    /** A line that is only a label ("판매가", "상품금액"): not a product. */
    private val LABEL_ONLY = Regex("(판매가|상품\\s*금액|정상가|주문\\s*금액|결제\\s*금액|원가|수량|합계)")
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
  private const val MAX_MEMO = 90

  /**
   * For each order, the payment of exactly that amount from the order day to 3 days later
   * (the card charges at order or at shipping): the closest day first, and one that names the shop
   * before one that doesn't. Payments already carrying these product names count as this order
   * (reading the page again changes nothing). Monthly installment parts and payments already
   * linked to something else are left alone. Each payment is used once.
   */
  fun match(orders: List<ShopOrder>, payments: List<TransactionEntity>, zone: ZoneId): List<OrderMatch> {
    val used = mutableSetOf<Long>()
    fun dayGap(order: ShopOrder, tx: TransactionEntity) =
      ChronoUnit.DAYS.between(order.date, Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate())
    fun inWindow(order: ShopOrder, tx: TransactionEntity) =
      tx.type == TransactionType.EXPENSE && tx.amount == order.amount && dayGap(order, tx) in 0..3
    val sorted = orders.sortedBy { it.date }
    // Already linked earlier (same order read twice): take those payments out first.
    val done = sorted.filter { o ->
      payments.firstOrNull { tx -> tx.id !in used && inWindow(o, tx) && o.summary.isNotEmpty() && tx.memo.contains(" · ${o.summary.take(20)}") }
        ?.also { used += it.id } != null
    }.toSet()
    return sorted.filter { it !in done }.mapNotNull { order ->
      if (order.items.isEmpty()) return@mapNotNull null
      val words = SHOP_WORDS[order.shop].orEmpty()
      fun namesShop(tx: TransactionEntity) = words.any { tx.memo.contains(it, ignoreCase = true) }
      val candidates = payments.filter { tx ->
        tx.id !in used && inWindow(order, tx) && !tx.memo.contains(" · ") &&
          InstallmentSplitter.partOf(tx.memo) == null
      }.sortedWith(compareBy({ !namesShop(it) }, { abs(dayGap(order, it)) }))
      val tx = candidates.firstOrNull { namesShop(it) }
        // Another payment of that amount is only offered when it is the only one (not pre-selected).
        ?: candidates.singleOrNull()
        ?: return@mapNotNull null
      used += tx.id
      OrderMatch(tx, order, newMemo(tx.memo, order), namesShop(tx))
    }
  }

  /** "쿠팡(쿠페이)" + 생수 → "쿠팡(쿠페이) · 생수 2L×12 외 1건"; a " (…)" tail (할부) is kept whole. */
  fun newMemo(memo: String, order: ShopOrder): String {
    val head = memo.substringBefore(" (").trim().ifEmpty { order.shop }
    val tail = memo.removePrefix(memo.substringBefore(" (")).trim()
    val room = MAX_MEMO - head.length - 3 - (if (tail.isNotEmpty()) tail.length + 1 else 0)
    val summary = order.summary.take(room.coerceAtLeast(8))
    return "$head · $summary" + if (tail.isNotEmpty()) " $tail" else ""
  }
}
