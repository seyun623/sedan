package com.moneyflow.security

import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.AutoBackupRepository
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.CardImportRepository
import com.moneyflow.data.repository.DriveRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.card.CardPerformanceCalculator
import com.moneyflow.domain.cardimport.StatementItem
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class V210Test {
  @Test fun recoveryCodeUnlocksAndTurnsThePinOff() = runBlocking {
    val settings = testSettingsRepository()
    val lock = AppLockManager(settings, FakeClock())
    lock.setPin("1234")
    val code = lock.createRecoveryCode()
    assertTrue(Regex("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}").matches(code))
    assertTrue(settings.current().hasRecoveryCode)
    assertTrue(lock.recoverWithCode("AAAA-BBBB-CCCC-DDDD") is PinCheckResult.Wrong)
    // Case and dashes don't matter.
    assertEquals(PinCheckResult.Success, lock.recoverWithCode(code.lowercase().replace("-", " ")))
    assertFalse(settings.current().isLockEnabled)
    assertFalse(settings.current().hasRecoveryCode)
  }

  @Test fun aRestoreCanBeUndone() = runBlocking {
    val db = inMemoryDb()
    val clock = FakeClock()
    val settings = testSettingsRepository()
    val ledger = LedgerRepository(db, clock)
    val backups = BackupRepository(db, settings, clock)
    val acc = ledger.addAccount(bank())
    ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 5_000, category = "식비", accountId = acc, timestamp = clock.nowMillis(), memo = "지금 기록"))
    val auto = AutoBackupRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext(), backups, settings, PlainSecretStore, clock,
      DriveRepository(settings, PlainSecretStore, clock))
    // Someone restores an empty backup by mistake.
    val empty = BackupRepository(inMemoryDb(), testSettingsRepository(), clock).createBackup("password1".toCharArray(), "2.9.0")
    auto.saveBeforeRestore()
    assertEquals(0L, settings.current().lastBackupAt) // the safety copy isn't counted as a backup
    backups.restore(backups.readBackup(empty, "password1".toCharArray()))
    assertTrue(db.transactionDao().observeAll().first().isEmpty())
    backups.restore(auto.readBeforeRestore())
    assertEquals("지금 기록", db.transactionDao().observeAll().first().single().memo)
    db.close()
  }

  @Test fun purchaseAndCancellationInOneStatementArePaired() {
    val d = LocalDate.of(2026, 11, 20)
    val items = listOf(
      StatementItem(d, null, "스타벅스", 5_600),
      StatementItem(d, null, "GS25", 1_200),
      StatementItem(d.plusDays(1), null, "스타벅스", 5_600, isCancel = true)
    )
    assertEquals(setOf(0, 2), CardImportRepository.pairCancellations(items))
  }

  @Test fun installmentBenefitCountsOnceOnTheWholeAmount() {
    val calc = CardPerformanceCalculator(ZoneId.of("Asia/Seoul"))
    val benefit = CardBenefitEntity(accountId = 1, title = "쇼핑 10%", category = "쇼핑", value = 1000, monthlyCap = 50_000, kind = BenefitKind.PERCENT)
    fun part(n: Int) = TransactionEntity(type = TransactionType.EXPENSE, amount = 100_000, category = "쇼핑", accountId = 1, timestamp = 0, memo = "냉장고 (할부 $n/3)")
    assertEquals(30_000L, calc.usage(benefit, listOf(part(1)), 0).earned)
    assertEquals(0L, calc.usage(benefit, listOf(part(2)), 0).earned)
  }
}

@RunWith(RobolectricTestRunner::class)
class V211LockModeTest {
  @Test fun phoneLockModeHasNoPinAndNothingTypedOpensIt() = runBlocking {
    val settings = testSettingsRepository()
    val lock = AppLockManager(settings, FakeClock())
    lock.setPin("1234")
    lock.createRecoveryCode()
    lock.enableDeviceLock()
    val s = settings.current()
    assertTrue(s.isLockEnabled && s.usesDeviceLock)
    assertFalse(s.usesPinLock || s.hasRecoveryCode || s.biometricEnabled)
    // The old PIN, an empty PIN and recovery codes no longer open anything.
    lock.lockNow()
    assertTrue(lock.verifyPin("1234") is PinCheckResult.Wrong)
    assertTrue(lock.verifyPin("") is PinCheckResult.Wrong)
    assertTrue(lock.recoverWithCode("AAAA-BBBB-CCCC-DDDD") is PinCheckResult.Wrong)

    // Choosing an app PIN again leaves the phone-lock mode.
    lock.setPin("5678")
    assertTrue(settings.current().usesPinLock)
    assertFalse(settings.current().usesDeviceLock)
    lock.disableLock()
    assertFalse(settings.current().isLockEnabled)
  }
}
