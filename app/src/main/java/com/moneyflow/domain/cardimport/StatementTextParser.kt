package com.moneyflow.domain.cardimport

import java.time.LocalDate
import java.time.LocalTime

/**
 * Reads card transactions from the visible text of a card company's 이용내역 / 명세서 screen,
 * without sending anything off the device.
 *
 * Card sites lay rows out either on one line ("11.21 스타벅스 5,600원") or across several lines
 * (date / merchant / amount each on its own line), so every date starts a small window of lines
 * that is searched for the merchant and the amount. Balance, limit and point figures are ignored.
 */
class StatementTextParser(private val today: LocalDate) {

  fun parse(text: String): StatementExtraction {
    val lines = text.split('\n').map { it.replace('\u00A0', ' ').trim() }.filter { it.isNotEmpty() }
    val dated = lines.indices.mapNotNull { i -> findDate(lines[i])?.let { i to it } }
    val items = if (merchantBeforeDate(lines, dated.map { it.first })) parseMerchantFirst(lines, dated) else parseDateFirst(lines, dated)
    return StatementExtraction(items.filterNot { SUMMARY.containsMatchIn(it.merchant) }, findPerformance(lines), READ_LOCAL)
  }

  /** "11.21 / 스타벅스 / 5,600원": each date starts a window of following lines. */
  private fun parseDateFirst(lines: List<String>, dated: List<Pair<Int, Pair<LocalDate, String>>>): List<StatementItem> =
    dated.mapIndexedNotNull { n, (i, date) ->
      val end = minOf(dated.getOrNull(n + 1)?.first ?: lines.size, i + WINDOW + 1)
      buildItem(date.first, lines.subList(i, end), date.second, merchantLine = null)
    }

  /**
   * "DB손해보험 / 2026.11.23 12:05 · 본인 · 일시불 · 자동납부 / 33,170원" (신한카드 등): the merchant is the
   * line above the date; the amount is on the merchant line, the date line or right after it.
   */
  private fun parseMerchantFirst(lines: List<String>, dated: List<Pair<Int, Pair<LocalDate, String>>>): List<StatementItem> =
    dated.mapIndexedNotNull { n, (i, date) ->
      val next = dated.getOrNull(n + 1)?.first ?: lines.size
      // Stop before the next item's merchant line (the line just above the next date).
      val end = minOf(if (next < lines.size) next - 1 else next, i + 1 + MERCHANT_FIRST_TAIL).coerceAtLeast(i + 1)
      val merchantLine = lines.getOrNull(i - 1)?.takeIf { isMerchantLine(it) }
      buildItem(date.first, lines.subList(i, end), date.second, merchantLine)
    }

  /** Most dated rows have a merchant-only line right above them → merchant-first layout. */
  private fun merchantBeforeDate(lines: List<String>, dateIdx: List<Int>): Boolean {
    val withAmount = dateIdx.filter { i -> !SUMMARY.containsMatchIn(lines[i]) }.filter { i ->
      val end = minOf(lines.size, i + 3)
      (i until end).any { pickAmount(lines[it]) != null } || lines.getOrNull(i - 1)?.let { pickAmount(it) } != null
    }
    if (withAmount.isEmpty()) return false
    val dateOnly = withAmount.count { i -> cleanMerchant(lines[i].replace(findDate(lines[i])!!.second, " ")).isEmpty() }
    val above = withAmount.count { i -> lines.getOrNull(i - 1)?.let(::isMerchantLine) == true }
    // The date line itself names no merchant, and a merchant line sits above most rows.
    return above * 10 >= withAmount.size * 6 && dateOnly * 10 >= withAmount.size * 6
  }

  private fun isMerchantLine(line: String): Boolean =
    findDate(line) == null && cleanMerchant(line).isNotEmpty() && !SUMMARY.containsMatchIn(line) && !SECTION.containsMatchIn(line)

  private fun buildItem(date: LocalDate, window: List<String>, dateText: String, merchantLine: String?): StatementItem? {
    val all = listOfNotNull(merchantLine) + window
    val joined = all.joinToString(" ")
    if (HEADER.containsMatchIn(joined) && AMOUNT.findAll(joined).none()) return null
    if (SUMMARY.containsMatchIn(window.first())) return null
    val amount = all.asSequence().mapNotNull { pickAmount(it) }.firstOrNull() ?: return null
    if (amount <= 0) return null
    val time = TIME.find(joined)?.let { runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull() }
    val merchant = (listOfNotNull(merchantLine) + window).asSequence()
      .map { cleanMerchant(it.replace(dateText, " ")) }
      .firstOrNull { it.isNotEmpty() }
      ?: return null
    return StatementItem(
      date = date,
      time = time,
      merchant = merchant,
      amount = amount,
      isCancel = CANCEL.containsMatchIn(joined) || NEGATIVE.containsMatchIn(joined),
      installmentMonths = INSTALLMENT.find(joined)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 1 }
    )
  }

  /** First amount in a line that is not a balance / limit / point / fee figure. */
  private fun pickAmount(line: String): Long? {
    var lastEnd = 0
    for (m in AMOUNT.findAll(line)) {
      // Only the words between the previous amount and this one describe this amount.
      val before = line.substring(maxOf(lastEnd, m.range.first - 8), m.range.first)
      lastEnd = m.range.last + 1
      if (!NOT_PAYMENT.containsMatchIn(before)) return m.groupValues[1].replace(",", "").toLongOrNull()
    }
    return null
  }

  private fun cleanMerchant(line: String): String {
    val cleaned = line
      .replace(TIME, " ").replace(AMOUNT, " ").replace(BARE_NUMBER, " ").replace(MASKED, " ")
      .replace(NOISE, " ").replace(Regex("[()\\[\\]|·•:]+"), " ")
      .replace(Regex("\\s+"), " ").trim()
    return cleaned.takeIf { s -> s.length >= 2 && s.any { it.isLetter() } && !HEADER.matches(s) }?.take(40).orEmpty()
  }

  /** Date in the line and the matched text. Years default to the most recent past date. */
  private fun findDate(line: String): Pair<LocalDate, String>? {
    FULL_DATE.find(line)?.let { m ->
      val (y, mo, d) = m.destructured
      val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
      return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()?.let { it to m.value }
    }
    SHORT_DATE.find(line)?.takeIf { m -> NOT_A_DATE_AFTER.find(line.substring(m.range.last + 1))?.range?.first != 0 && !CURRENCY_BEFORE.containsMatchIn(line.substring(0, m.range.first)) }?.let { m ->
      val (mo, d) = m.destructured
      val date = runCatching { LocalDate.of(today.year, mo.toInt(), d.toInt()) }.getOrNull() ?: return null
      return (if (date.isAfter(today.plusDays(1))) date.minusYears(1) else date) to m.value
    }
    return null
  }

  private fun findPerformance(lines: List<String>): List<PerformanceInfo> =
    lines.flatMapIndexed { idx, line ->
      if (!PERFORMANCE.containsMatchIn(line)) return@flatMapIndexed emptyList()
      val amount = AMOUNT.find(line)?.groupValues?.get(1)
        ?: lines.getOrNull(idx + 1)?.let { AMOUNT.find(it)?.groupValues?.get(1) }
        ?: return@flatMapIndexed emptyList()
      val label = line.replace(AMOUNT, "").replace(Regex("\\s+"), " ").trim().take(30)
      listOf(PerformanceInfo(label.ifEmpty { "이용 실적" }, amount.replace(",", "").toLong()))
    }.distinct()

  companion object {
    const val READ_LOCAL = "기기에서 읽기"
    private const val WINDOW = 7
    /** Lines after the date line that can hold the amount in the merchant-first layout. */
    private const val MERCHANT_FIRST_TAIL = 3
    /** Statement totals and due amounts, not purchases. */
    private val SUMMARY = Regex("명세서|결제\\s*예정|청구\\s*(금액|예정)|납부\\s*예정|이용\\s*대금|결제\\s*금액|결제일|합계|총\\s*이용")
    /** Section titles that are never a merchant. */
    private val SECTION = Regex("^(최근\\s*)?이용\\s*내역|마이페이지|전체\\s*보기|더\\s*보기")
    private val FULL_DATE = Regex("(?<![0-9])(20\\d{2}|\\d{2})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})(?![0-9])")
    private val SHORT_DATE = Regex("(?<![0-9./\\-])(\\d{1,2})\\s*[./월]\\s*(\\d{1,2})일?(?![0-9]|\\s*[.:/]\\s*\\d)")
    private val TIME = Regex("(?<![0-9])([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?")
    /** "1.5% 적립", "3.25배": a decimal number, not a date. */
    private val NOT_A_DATE_AFTER = Regex("\\s*[%배]")
    /** "USD 3.25", "$3.25": a foreign amount, not a date. */
    private val CURRENCY_BEFORE = Regex("(USD|EUR|JPY|GBP|CNY|US\\$|\\$|€|¥)\\s*$")
    private val AMOUNT = Regex("-?\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*원")
    private val BARE_NUMBER = Regex("(?<![가-힣A-Za-z0-9])[-+]?[0-9][0-9,.*\\-]*(?![가-힣A-Za-z0-9])")
    private val MASKED = Regex("[0-9*]{2,}[-*][0-9*\\-]+")
    private val NOT_PAYMENT = Regex("누적|잔액|잔고|한도|포인트|적립|할인|수수료|이자|캐시백")
    private val CANCEL = Regex("취소|환불")
    private val NEGATIVE = Regex("(?<![0-9])-\\s*[0-9]{1,3}(?:,[0-9]{3})+\\s*원")
    private val INSTALLMENT = Regex("([0-9]{1,2})\\s*개월")
    private val PERFORMANCE = Regex("실적")
    private val HEADER = Regex("^(이용일(자|시)?|거래일(자|시)?|승인일(자|시)?|가맹점(명)?|이용(금액|내역)|금액|구분|비고|합계|총\\s*이용금액)$")
    private val NOISE = Regex(
      "승인\\s*취소|승인|취소|일시불|할부|자동\\s*납부|정기\\s*결제|[0-9]{1,2}\\s*개월|국내|해외|체크|신용|본인|가족|결제\\s*예정|결제예정|" +
        "이용금액|이용일자|이용일시|이용일|이용|가맹점명|가맹점|사용처|거래|원$"
    )
  }
}
