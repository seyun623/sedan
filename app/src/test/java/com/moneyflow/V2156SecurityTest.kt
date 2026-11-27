package com.moneyflow

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.moneyflow.domain.capture.FormPrefill
import com.moneyflow.domain.cardimport.AiStatementReader
import com.moneyflow.security.AppLockManager
import com.moneyflow.security.PinCheckResult
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.testSettingsRepository
import com.moneyflow.work.PrefillIntents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

/** Hourly review ④ (security): fixes found by the reviewers. */
@RunWith(RobolectricTestRunner::class)
class V2156SecurityTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test fun onlyOurOwnPrefillIntentsOpenTheForm() {
    val ours = Intent().also { PrefillIntents.put(context, it, FormPrefill(amount = 12_000)) }
    assertNotNull(PrefillIntents.take(context, ours))
    // Another app sending the same extras without our token.
    val forged = Intent().putExtra("com.moneyflow.extra.OPEN_FORM", true).putExtra("com.moneyflow.extra.AMOUNT", 999_000L)
    assertNull(PrefillIntents.take(context, forged))
  }

  @Test fun moreNumbersAndNamesAreMaskedBeforeAi() {
    val out = AiStatementReader.redact("홍길동 님 입출금 1002123456789 11.25 스타벅스 4,500원")
    assertTrue(out, !out.contains("1002123456789") && !out.contains("홍길동"))
    assertTrue(out, out.contains("4,500원") && out.contains("11.25") && out.contains("스타벅스"))
  }

  @Test fun rebootAndClockForwardDoNotSkipThePenalty() = runBlocking {
    val clock = FakeClock()
    val settings = testSettingsRepository()
    val lock = AppLockManager(settings, clock)
    lock.setPin("1234")
    repeat(AppLockManager.FREE_ATTEMPTS) { lock.verifyPin("0000") }
    assertTrue(lock.checkPin("1234") is PinCheckResult.LockedOut)
    // Reboot and set the date a day ahead.
    clock.reboot()
    clock.setWallClockOnly(LocalDateTime.of(2026, 11, 22, 12, 0))
    val fresh = AppLockManager(settings, clock)
    assertTrue(fresh.checkPin("1234") is PinCheckResult.LockedOut)
    // After the penalty passes on this boot, the right PIN works again.
    clock.advanceMillis(60 * 60_000L)
    assertEquals(PinCheckResult.Success, fresh.checkPin("1234"))
  }
}
