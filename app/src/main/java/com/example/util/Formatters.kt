package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {
  private val currencyFormat = NumberFormat.getNumberInstance(Locale.KOREA)
  private val monthYearFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREA)
  private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.KOREA)
  private val dayHeaderFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)
  private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)
  private val fullDateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

  fun formatCurrency(amount: Long, includeWon: Boolean = true): String {
    val formatted = currencyFormat.format(amount)
    return if (includeWon) "${formatted}원" else formatted
  }

  fun formatSignedCurrency(amount: Long, isExpense: Boolean): String {
    val formatted = currencyFormat.format(amount)
    return if (isExpense) "-${formatted}원" else "+${formatted}원"
  }

  fun formatKoreanUnitCurrency(amount: Long): String {
    if (amount <= 0L) return "0원"
    val eok = amount / 100_000_000L
    val man = (amount % 100_000_000L) / 10_000L
    val rest = amount % 10_000L

    val parts = mutableListOf<String>()
    if (eok > 0) parts.add("${currencyFormat.format(eok)}억")
    if (man > 0) parts.add("${currencyFormat.format(man)}만")
    if (rest > 0) parts.add(currencyFormat.format(rest))

    return parts.joinToString(" ") + "원"
  }

  fun formatMonthYear(timestamp: Long): String {
    return monthYearFormat.format(Date(timestamp))
  }

  fun formatMonthKey(timestamp: Long): String {
    return monthKeyFormat.format(Date(timestamp))
  }

  fun formatDayHeader(timestamp: Long): String {
    return dayHeaderFormat.format(Date(timestamp))
  }

  fun formatTime(timestamp: Long): String {
    return timeFormat.format(Date(timestamp))
  }

  fun formatFullDate(timestamp: Long): String {
    return fullDateFormat.format(Date(timestamp))
  }

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
