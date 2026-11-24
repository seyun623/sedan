package com.moneyflow.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PinCheckResult {
  data object Success : PinCheckResult
  data class Wrong(val attemptsLeft: Int) : PinCheckResult
  data class LockedOut(val untilMillis: Long) : PinCheckResult
}

/**
 * Tracks whether the app is locked. The app locks on cold start and again after it has been in
 * the background longer than the configured timeout (observed via ProcessLifecycleOwner).
 * Wrong PINs are rate limited: after [FREE_ATTEMPTS] failures each further failure locks input
 * for an exponentially growing period.
 */
@Singleton
class AppLockManager @Inject constructor(
  private val settings: SettingsRepository,
  private val clock: AppClock
) : DefaultLifecycleObserver {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _locked = MutableStateFlow(true)
  val locked: StateFlow<Boolean> = _locked.asStateFlow()

  /** Settings kept in memory so lifecycle callbacks can decide synchronously. */
  private val cached: StateFlow<AppSettings?> = settings.settings.stateIn(scope, SharingStarted.Eagerly, null)

  /** Monotonic time when the app went to the background. */
  private var backgroundedAtElapsed: Long? = null

  /** In-memory lockout on the monotonic clock (cannot be skipped by changing the device time). */
  private var lockedUntilElapsed: Long = 0L

  /** Called once at startup: unlock immediately when no PIN is configured. */
  fun initialize() {
    scope.launch { if (!settings.current().isLockEnabled) _locked.value = false }
  }

  override fun onStop(owner: LifecycleOwner) {
    backgroundedAtElapsed = clock.elapsedRealtime()
    val s = cached.value
    // "Immediately": lock now, so the content is never drawn again on return.
    if (s == null || (s.isLockEnabled && s.lockTimeoutSeconds <= 0)) {
      if (s?.isLockEnabled != false) _locked.value = true
    }
  }

  override fun onStart(owner: LifecycleOwner) {
    val since = backgroundedAtElapsed ?: return
    backgroundedAtElapsed = null
    val s = cached.value
    if (s == null) {
      // Settings not known yet: lock to be safe, then unlock if no PIN is configured.
      _locked.value = true
      initialize()
      return
    }
    if (s.isLockEnabled && clock.elapsedRealtime() - since >= s.lockTimeoutSeconds * 1000L) {
      _locked.value = true
    }
  }

  fun lockNow() {
    scope.launch { if (settings.current().isLockEnabled) _locked.value = true }
  }

  fun unlockWithBiometric() {
    scope.launch {
      settings.resetPinFailures()
      lockedUntilElapsed = 0L
      _locked.value = false
    }
  }

  /** Unlock attempt from the lock screen. */
  suspend fun verifyPin(pin: String): PinCheckResult {
    val result = checkWithRateLimit(pin)
    if (result == PinCheckResult.Success) _locked.value = false
    return result
  }

  /**
   * Checks the current PIN for settings actions (change / turn off lock) without changing the
   * lock state. Shares the same failure counter and penalty as the lock screen.
   */
  suspend fun checkPin(pin: String): PinCheckResult = checkWithRateLimit(pin)

  private suspend fun checkWithRateLimit(pin: String): PinCheckResult {
    val s = settings.current()
    val hash = s.pinHash
    val salt = s.pinSalt
    if (hash == null || salt == null) return PinCheckResult.Success

    val now = clock.nowMillis()
    val elapsed = clock.elapsedRealtime()
    lockoutUntil(s, now, elapsed)?.let { return PinCheckResult.LockedOut(it) }

    val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, hash, salt) }
    if (ok) {
      settings.resetPinFailures()
      lockedUntilElapsed = 0L
      return PinCheckResult.Success
    }
    val attempts = s.failedPinAttempts + 1
    return if (attempts >= FREE_ATTEMPTS) {
      val penaltyMs = (30L shl (attempts - FREE_ATTEMPTS).coerceAtMost(8)) * 1000 // 30s .. ~2h
      lockedUntilElapsed = elapsed + penaltyMs
      // Also kept on the monotonic clock: restarting the app and moving the clock forward doesn't skip it.
      settings.recordPinFailure(attempts, now + penaltyMs, now, elapsed + penaltyMs, clock.bootId())
      PinCheckResult.LockedOut(now + penaltyMs)
    } else {
      settings.recordPinFailure(attempts, 0L, now)
      PinCheckResult.Wrong(FREE_ATTEMPTS - attempts)
    }
  }

  /** Wall-clock end of the current lockout, or null if the PIN may be tried. */
  private fun lockoutUntil(s: AppSettings, now: Long, elapsed: Long): Long? {
    if (elapsed < lockedUntilElapsed) return now + (lockedUntilElapsed - elapsed)
    if (s.pinLockBoot.isNotEmpty() && s.pinLockBoot == clock.bootId() && elapsed < s.pinLockedUntilElapsed) {
      return now + (s.pinLockedUntilElapsed - elapsed)
    }
    if (s.pinLockedUntil > now) return s.pinLockedUntil
    // The device clock was set back past the last failure while a penalty applied: keep it locked
    // for the full penalty measured from now.
    if (s.failedPinAttempts >= FREE_ATTEMPTS && s.pinLastFailureAt > now && s.pinLockedUntil > 0) {
      return now + (s.pinLockedUntil - s.pinLastFailureAt)
    }
    return null
  }

  suspend fun setPin(pin: String) {
    require(PinHasher.isValidPin(pin))
    val hashed = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
    settings.setPin(hashed.hash, hashed.salt)
    lockedUntilElapsed = 0L
    _locked.value = false
  }

  suspend fun disableLock() {
    settings.clearPin()
    lockedUntilElapsed = 0L
    _locked.value = false
  }

  companion object {
    const val FREE_ATTEMPTS = 5
  }
}
