package com.moneyflow.domain.receipt

import java.time.LocalDate

/** What a receipt says: the paid total, the shop, the date and a few item names. */
data class ReceiptInfo(val total: Long, val merchant: String, val date: LocalDate?, val items: List<String>) {
  /** "이마트 · 우유 외 2건" */
  val memo: String
    get() = when {
      items.isEmpty() -> merchant
      merchant.isEmpty() -> summary
      else -> "$merchant · $summary"
    }
  private val summary: String get() = if (items.size == 1) items.first() else "${items.first()} 외 ${items.size - 1}건"
}

/**
 * Reads a receipt from the text recognized in its photo (on the phone, nothing is sent out).
 * The paid total ("합계", "결제금액", "받을금액") wins; without one, the largest amount.
 */
class ReceiptParser(private val today: LocalDate) {

  fun parse(text: String): ReceiptInfo? {
    val lines = text.split('\n').map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val total = total(lines) ?: return null
    return ReceiptInfo(total.first, merchant(lines), date(lines), items(lines, total.second))
  }

  /** (amount, line index of the total). */
  private fun total(lines: List<String>): Pair<Long, Int>? {
    for (rank in TOTALS.indices) {
      for (i in lines.indices.reversed()) {
        val line = lines[i]
        if (!TOTALS[rank].containsMatchIn(line) || NOT_TOTAL.containsMatchIn(line)) continue
        // The amount is on the same line, or (two-column receipts) on the next line.
        val amount = amountIn(line.substring(TOTALS[rank].find(line)!!.range.last + 1))
          ?: lines.getOrNull(i + 1)?.takeIf { ONLY_AMOUNT.matches(it) }?.let(::amountIn)
        if (amount != null && amount in 100..100_000_000) return amount to i
      }
    }
    val all = lines.withIndex().filter { !NOT_TOTAL.containsMatchIn(it.value) && !PHONE_OR_NUMBER.containsMatchIn(it.value) && DATE.find(it.value) == null && !TIME.containsMatchIn(it.value) }
      .mapNotNull { (i, l) -> amountIn(l)?.takeIf { it in 100..100_000_000 }?.let { it to i } }
    return all.maxByOrNull { it.first }
  }

  private fun amountIn(text: String): Long? =
    AMOUNT.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").replace(".", "").toLongOrNull() }.lastOrNull()

  private fun merchant(lines: List<String>): String {
    lines.firstNotNullOfOrNull { l -> MERCHANT_LABEL.find(l)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 } }
      ?.let { return clean(it) }
    return lines.take(6).firstOrNull { l ->
      l.count { it in '가'..'힣' } >= 2 && l.count { it.isDigit() } <= 2 && !NOT_MERCHANT.containsMatchIn(l)
    }?.let(::clean).orEmpty()
  }

  private fun clean(s: String) = s.replace(Regex("[\\[\\]()*<>]"), " ").replace(Regex("\\s+"), " ").trim().take(30)

  private fun date(lines: List<String>): LocalDate? {
    for (l in lines) {
      val m = DATE.find(l) ?: continue
      val (y, mo, d) = m.destructured
      val year = y.toInt().let { if (it < 100) 2000 + it else it }
      val date = runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull() ?: continue
      if (!date.isAfter(today.plusDays(1)) && date.isAfter(today.minusYears(2))) return date
    }
    return null
  }

  /** Names on price lines above the total (at most 5). */
  private fun items(lines: List<String>, totalLine: Int): List<String> =
    lines.take(totalLine).asSequence()
      .filter { AMOUNT.containsMatchIn(it) && !NOT_ITEM.containsMatchIn(it) && !PHONE_OR_NUMBER.containsMatchIn(it) && DATE.find(it) == null }
      .map { it.replace(AMOUNT, " ").replace(Regex("[0-9*#@xX×]+"), " ").replace(Regex("\\s+"), " ").trim() }
      .filter { name -> name.count { it in '가'..'힣' || it.isLetter() } >= 2 }
      .map { it.take(20) }
      .distinct().take(5).toList()

  companion object {
    private val AMOUNT = Regex("(?<![0-9.,])([0-9]{1,3}(?:[,.][0-9]{3})+|[0-9]{3,8})\\s*원?(?![0-9])")
    private val TIME = Regex("(?<![0-9])[0-2]?[0-9]:[0-5][0-9]")
    private val ONLY_AMOUNT = Regex("[₩\\s]*[0-9][0-9,]{2,12}\\s*원?")
    /** Totals, best first. */
    private val TOTALS = listOf(
      Regex("(받을|결제|청구|승인)\\s*금\\s*액|카드\\s*결제|신용\\s*카드|총\\s*결제"),
      Regex("합\\s*계\\s*(금\\s*액)?|총\\s*(금\\s*액|합\\s*계|액)|판매\\s*총액|TOTAL", RegexOption.IGNORE_CASE)
    )
    private val NOT_TOTAL = Regex("부가세|부가\\s*가치|과세|면세|공급\\s*가|거스름|받은\\s*돈|잔돈|포인트|적립|할인|봉사료|사업자|전화|TEL|카드\\s*번호|승인\\s*번호|No\\.", RegexOption.IGNORE_CASE)
    private val PHONE_OR_NUMBER = Regex("[0-9]{2,4}-[0-9]{3,4}-[0-9]{4}|[0-9]{3}-[0-9]{2}-[0-9]{5}|[0-9]{4}[-*][0-9*]{4}[-*][0-9*]{4}")
    private val MERCHANT_LABEL = Regex("(?:상\\s*호|가맹점\\s*명?|매장\\s*명|점\\s*포\\s*명)\\s*[:：]?\\s*(.+)")
    private val NOT_MERCHANT = Regex("영수증|RECEIPT|전표|카드|고객|매출|사업자|대표|주소|TEL|전화|교환|환불", RegexOption.IGNORE_CASE)
    private val NOT_ITEM = Regex("합\\s*계|총|결제|부가세|과세|면세|공급|거스름|받은|할인|포인트|적립|카드|승인|사업자|대표|전화|금액|수량|단가")
    private val DATE = Regex("(?<![0-9])(20[0-9]{2}|[0-9]{2})\\s*[-./년]\\s*([0-9]{1,2})\\s*[-./월]\\s*([0-9]{1,2})")
  }
}
