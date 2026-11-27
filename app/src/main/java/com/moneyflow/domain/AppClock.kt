package com.moneyflow.domain


import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Dates follow Korean time even when the phone is abroad (statements, budgets and cards do). */
val APP_ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")

/** Injectable clock so date-dependent logic (recurring, stats, parser) is testable. */
interface AppClock {
  fun now(): Instant
  val zone: ZoneId

  fun nowMillis(): Long = now().toEpochMilli()
  /** Monotonic time since boot; unaffected by the user changing the device clock. */
  fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()
  /** Changes on every reboot (tells whether [elapsedRealtime] values are comparable). */
  fun bootId(): String = runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("")
  fun today(): LocalDate = now().atZone(zone).toLocalDate()
  fun currentMonth(): YearMonth = YearMonth.from(today())
  fun nowZoned(): ZonedDateTime = now().atZone(zone)
}

object SystemAppClock : AppClock {
  override fun now(): Instant = Instant.now()
  override val zone: ZoneId get() = com.moneyflow.domain.APP_ZONE
}

/**
 * Today's date, re-emitted when the day changes (checked every minute while collected), so
 * screens left open over midnight move on to the new day / month.
 */
fun AppClock.todayFlow(): Flow<LocalDate> = flow {
  while (true) {
    emit(today())
    delay(60_000)
  }
}.distinctUntilChanged()

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
