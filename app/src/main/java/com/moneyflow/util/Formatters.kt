package com.moneyflow.util

import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/** Thread-safe formatting helpers (java.time formatters are immutable). */
object Formatters {
  private val zone: ZoneId get() = ZoneId.systemDefault()
  private val monthYearFormat = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA)
  private val monthKeyFormat = DateTimeFormatter.ofPattern("yyyy-MM", Locale.KOREA)
  private val dayHeaderFormat = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREA)
  private val timeFormat = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA)
  private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA)
  private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd a h:mm", Locale.KOREA)

  private fun numberFormat(): NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

  /** "12,345" without unit. */
  fun formatNumber(amount: Long): String = numberFormat().format(amount)

  fun formatCurrency(amount: Long, includeWon: Boolean = true): String {
    val formatted = numberFormat().format(amount)
    return if (includeWon) "${formatted}원" else formatted
  }

  fun formatSignedCurrency(amount: Long, isExpense: Boolean): String {
    val formatted = numberFormat().format(kotlin.math.abs(amount))
    return if (isExpense) "-${formatted}원" else "+${formatted}원"
  }

  /** "1억 2,345만 6,789원"; negative amounts keep their sign. */
  fun formatKoreanUnitCurrency(amount: Long): String {
    if (amount == 0L) return "0원"
    val sign = if (amount < 0) "-" else ""
    val abs = kotlin.math.abs(amount)
    val nf = numberFormat()
    val eok = abs / 100_000_000L
    val man = (abs % 100_000_000L) / 10_000L
    val rest = abs % 10_000L
    val parts = buildList {
      if (eok > 0) add("${nf.format(eok)}억")
      if (man > 0) add("${nf.format(man)}만")
      if (rest > 0) add(nf.format(rest))
    }
    return sign + parts.joinToString(" ") + "원"
  }

  fun formatMonth(month: YearMonth): String = month.format(monthYearFormat)

  fun formatMonthYear(timestamp: Long): String = at(timestamp).format(monthYearFormat)
  fun formatMonthKey(timestamp: Long): String = at(timestamp).format(monthKeyFormat)
  fun formatDayHeader(timestamp: Long): String = at(timestamp).format(dayHeaderFormat)
  fun formatTime(timestamp: Long): String = at(timestamp).format(timeFormat)
  fun formatFullDate(timestamp: Long): String = at(timestamp).format(fullDateFormat)
  fun formatDateTime(timestamp: Long): String = at(timestamp).format(dateTimeFormat)

  private fun at(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(zone)

  fun getStartOfMonth(calendar: Calendar): Long {
    val cal = calendar.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }

  fun getEndOfMonth(calendar: Calendar): Long {
    val cal = calendar.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
  }
}
