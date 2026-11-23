package com.moneyflow.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Injectable clock so date-dependent logic (recurring, stats, parser) is testable. */
interface AppClock {
  fun now(): Instant
  val zone: ZoneId

  fun nowMillis(): Long = now().toEpochMilli()
  /** Monotonic time since boot; unaffected by the user changing the device clock. */
  fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()
  fun today(): LocalDate = now().atZone(zone).toLocalDate()
  fun currentMonth(): YearMonth = YearMonth.from(today())
  fun nowZoned(): ZonedDateTime = now().atZone(zone)
}

object SystemAppClock : AppClock {
  override fun now(): Instant = Instant.now()
  override val zone: ZoneId get() = ZoneId.systemDefault()
}

/** Month/day helpers shared by repositories, view models and tests. */
object MonthKeys {
  fun of(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)
  fun parse(key: String): YearMonth? = runCatching { YearMonth.parse(key) }.getOrNull()

  fun startMillis(month: YearMonth, zone: ZoneId): Long =
    month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

  /** Inclusive end (last millisecond of the month). */
  fun endMillis(month: YearMonth, zone: ZoneId): Long =
    month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

  fun dayStartMillis(date: LocalDate, zone: ZoneId): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()

  fun dayEndMillis(date: LocalDate, zone: ZoneId): Long =
    date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

  fun toLocalDate(millis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
