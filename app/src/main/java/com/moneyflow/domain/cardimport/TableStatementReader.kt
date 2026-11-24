package com.moneyflow.domain.cardimport

import java.time.LocalDate
import java.time.LocalTime

/**
 * Finds the header row of a card / bank statement table and reads its rows.
 * Works by column names ("이용일자", "가맹점명", "이용금액", "출금액", "입금액", "취소여부", "할부" …),
 * so it isn't tied to one company's layout.
 */
class TableStatementReader(private val today: LocalDate) {

  enum class Col { DATE, TIME, MERCHANT, AMOUNT, WITHDRAW, DEPOSIT, STATUS, INSTALLMENT }

  private val keywords: Map<Col, List<String>> = mapOf(
    Col.DATE to listOf("이용일자", "이용일시", "이용일", "거래일자", "거래일시", "거래일", "승인일자", "승인일시", "승인일", "매출일", "사용일", "결제일자", "일자", "날짜", "date"),
    Col.TIME to listOf("이용시간", "승인시간", "거래시간", "시간", "시각"),
    Col.MERCHANT to listOf("가맹점명", "이용가맹점", "가맹점", "이용처", "사용처", "거래내용", "기재내용", "내용", "적요", "받는분", "보낸분", "거래처", "상호", "merchant", "description"),
    Col.AMOUNT to listOf("이용금액", "승인금액", "결제금액", "매출금액", "사용금액", "거래금액", "결제원금", "금액", "amount"),
    Col.WITHDRAW to listOf("출금액", "출금금액", "찾으신금액", "출금", "지급액", "인출"),
    Col.DEPOSIT to listOf("입금액", "입금금액", "맡기신금액", "입금"),
    Col.STATUS to listOf("취소여부", "승인구분", "매출구분", "거래구분", "상태", "구분", "취소"),
    Col.INSTALLMENT to listOf("할부개월", "할부기간", "할부", "결제방법", "이용구분")
  )

  /** Columns found in the header row. */
  data class Header(val row: Int, val columns: Map<Col, Int>)

  fun findHeader(rows: List<List<String>>): Header? = rows.take(40).mapIndexedNotNull { r, cells ->
    val used = mutableSetOf<Int>()
    val cols = mutableMapOf<Col, Int>()
    // Longer / more specific names first so "이용금액" isn't taken as "이용일".
    for (col in listOf(Col.WITHDRAW, Col.DEPOSIT, Col.AMOUNT, Col.INSTALLMENT, Col.STATUS, Col.TIME, Col.DATE, Col.MERCHANT)) {
      val idx = keywords.getValue(col).firstNotNullOfOrNull { kw ->
        cells.indices.firstOrNull { i -> i !in used && norm(cells[i]).let { it == norm(kw) || (it.contains(norm(kw)) && it.length <= kw.length + 6) } }
      }
      if (idx != null) { cols[col] = idx; used += idx }
    }
    val ok = Col.DATE in cols && Col.MERCHANT in cols && (Col.AMOUNT in cols || Col.WITHDRAW in cols || Col.DEPOSIT in cols)
    if (ok) Header(r, cols) else null
  }.firstOrNull()

  fun read(rows: List<List<String>>, readBy: String): StatementExtraction {
    val header = findHeader(rows) ?: return StatementExtraction(emptyList(), emptyList(), readBy)
    val c = header.columns
    val items = rows.drop(header.row + 1).mapNotNull { cells ->
      fun cell(col: Col) = c[col]?.let { cells.getOrNull(it) }.orEmpty()
      val merchant = cell(Col.MERCHANT).replace(Regex("\\s+"), " ").trim().take(40)
      if (merchant.isEmpty() || SUMMARY.containsMatchIn(merchant) || SUMMARY.matches(cell(Col.DATE).trim())) return@mapNotNull null
      val (date, dateTime) = parseDate(cell(Col.DATE)) ?: return@mapNotNull null
      val time = dateTime ?: parseTime(cell(Col.TIME))
      val status = cell(Col.STATUS)
      val withdraw = parseAmount(cell(Col.WITHDRAW))
      val deposit = parseAmount(cell(Col.DEPOSIT))
      val amount = parseAmount(cell(Col.AMOUNT))
      val (value, income) = when {
        withdraw != null && withdraw != 0L -> withdraw to false
        deposit != null && deposit != 0L -> deposit to true
        amount != null && amount != 0L -> amount to false
        else -> return@mapNotNull null
      }
      val cancel = CANCEL.containsMatchIn(status) || (!income && value < 0)
      StatementItem(
        date = date,
        time = time,
        merchant = merchant,
        amount = kotlin.math.abs(value),
        isCancel = cancel && !income,
        installmentMonths = parseInstallment(cell(Col.INSTALLMENT)),
        isIncome = income
      )
    }
    return StatementExtraction(items, emptyList(), readBy)
  }

  // ---- cell parsing ------------------------------------------------------------------------

  private fun norm(s: String) = s.replace(Regex("[\\s()\\[\\]/._:-]"), "").lowercase()

  /** "2026.11.21", "2026-11-21 14:32", "20261121", "26.11.21", "11/21", Excel serial 46347. */
  internal fun parseDate(raw: String): Pair<LocalDate, LocalTime?>? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    s.toDoubleOrNull()?.let { serial ->
      if (s.length == 8 && s.all(Char::isDigit)) {
        return runCatching { LocalDate.of(s.take(4).toInt(), s.substring(4, 6).toInt(), s.takeLast(2).toInt()) to null }.getOrNull()
      }
      if (serial in 30_000.0..80_000.0) {
        // Excel date serial (days since 1899-12-30), the fraction is the time of day.
        val date = LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
        val frac = serial - serial.toLong()
        val time = if (frac > 0) LocalTime.ofSecondOfDay(Math.round(frac * 86_400).coerceIn(0, 86_399)) else null
        return date to time
      }
      return null
    }
    FULL_DATE.find(s)?.let { m ->
      val y = m.groupValues[1].toInt().let { if (it < 100) 2000 + it else it }
      val date = runCatching { LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() ?: return null
      return date to parseTime(s.substring(m.range.last + 1))
    }
    SHORT_DATE.find(s)?.let { m ->
      val month = m.groupValues[1].toInt()
      val day = m.groupValues[2].toInt()
      var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
      if (date.isAfter(today.plusDays(1))) date = date.minusYears(1)
      return date to parseTime(s.substring(m.range.last + 1))
    }
    return null
  }

  internal fun parseTime(raw: String): LocalTime? = TIME.find(raw)?.let { m ->
    runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
  }

  /** "12,500", "-3,500원", "(3,500)", "₩12,500" → signed amount; null when empty / not a number. */
  internal fun parseAmount(raw: String): Long? {
    val s = raw.trim().replace(",", "").replace("원", "").replace("₩", "").replace(" ", "")
    if (s.isEmpty() || s == "-") return null
    val negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"))
    val digits = s.trim('(', ')', '-', '+').substringBefore('.')
    if (digits.isEmpty() || !digits.all(Char::isDigit) || digits.length > 12) return null
    val v = digits.toLong()
    return if (negative) -v else v
  }

  internal fun parseInstallment(raw: String): Int? {
    val s = raw.trim()
    if (s.isEmpty() || s.contains("일시불")) return null
    return Regex("(\\d{1,2})").find(s)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 2..36 }
  }

  companion object {
    private val FULL_DATE = Regex("(\\d{2,4})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})")
    private val SHORT_DATE = Regex("^(\\d{1,2})[./-](\\d{1,2})(?![./-]\\d)")
    private val TIME = Regex("(\\d{1,2}):(\\d{2})")
    private val CANCEL = Regex("취소|환불|반품")
    private val SUMMARY = Regex("^(합\\s*계|소\\s*계|총\\s*계|총\\s*합계|합계금액)")
  }
}
