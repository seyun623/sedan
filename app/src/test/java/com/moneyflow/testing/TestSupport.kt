package com.moneyflow.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.domain.AppClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Controllable clock (Asia/Seoul). */
class FakeClock(start: LocalDateTime = LocalDateTime.of(2026, 11, 21, 12, 0)) : AppClock {
  override val zone: ZoneId = ZoneId.of("Asia/Seoul")
  var instant: Instant = start.atZone(zone).toInstant()
  override fun now(): Instant = instant
  /** Monotonic clock; advances with [set]/[advance] unless [setWallClockOnly] is used. */
  var elapsed: Long = 1_000_000L
  override fun elapsedRealtime(): Long = elapsed
  fun set(dateTime: LocalDateTime) {
    val next = dateTime.atZone(zone).toInstant()
    if (next.isAfter(instant)) elapsed += next.toEpochMilli() - instant.toEpochMilli()
    instant = next
  }
  /** Changes only the wall clock (the user changing the device time). */
  fun setWallClockOnly(dateTime: LocalDateTime) { instant = dateTime.atZone(zone).toInstant() }
  fun advanceMillis(ms: Long) { instant = instant.plusMillis(ms); elapsed += ms }
  fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()
}

fun inMemoryDb(): AppDatabase =
  Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
    .allowMainThreadQueries()
    .build()

fun bank(name: String = "생활비 통장", balance: Long = 1_000_000, bankName: String = "토스뱅크", number: String = "") =
  AccountEntity(name = name, bankName = bankName, accountType = AccountType.BANK, balance = balance, accountNumber = number)

fun card(name: String = "현대카드", owed: Long = 0, number: String = "") =
  AccountEntity(name = name, bankName = name, accountType = AccountType.CREDIT_CARD, balance = owed, isDebt = true, accountNumber = number)

fun testSettingsRepository(): com.moneyflow.data.repository.SettingsRepository {
  val file = java.io.File.createTempFile("settings", ".preferences_pb").apply { delete(); deleteOnExit() }
  val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
    scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
    produceFile = { file }
  )
  return com.moneyflow.data.repository.SettingsRepository(store)
}
