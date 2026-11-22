package com.moneyflow.security

import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SecurityTest {

  @Test fun pinHashIsSaltedAndVerifiable() {
    val a = PinHasher.hash("482915")
    val b = PinHasher.hash("482915")
    assertNotEquals(a.hash, b.hash)
    assertTrue(PinHasher.verify("482915", a.hash, a.salt))
    assertFalse(PinHasher.verify("482916", a.hash, a.salt))
  }

  @Test fun pinFormat() {
    assertTrue(PinHasher.isValidPin("1234"))
    assertTrue(PinHasher.isValidPin("123456"))
    assertFalse(PinHasher.isValidPin("123"))
    assertFalse(PinHasher.isValidPin("12a4"))
  }

  @Test fun wrongPinsAreRateLimited() = runBlocking {
    val clock = FakeClock(LocalDateTime.of(2026, 11, 21, 12, 0))
    val settings = testSettingsRepository()
    val lock = AppLockManager(settings, clock)
    lock.setPin("482915")
    repeat(AppLockManager.FREE_ATTEMPTS - 1) {
      assertTrue(lock.verifyPin("000000") is PinCheckResult.Wrong)
    }
    val locked = lock.verifyPin("000000")
    assertTrue(locked is PinCheckResult.LockedOut)
    // Even the right PIN is refused while locked out
    assertTrue(lock.verifyPin("482915") is PinCheckResult.LockedOut)
    clock.set(LocalDateTime.of(2026, 11, 21, 12, 5))
    assertEquals(PinCheckResult.Success, lock.verifyPin("482915"))
    assertEquals(0, settings.current().failedPinAttempts)
  }
}
