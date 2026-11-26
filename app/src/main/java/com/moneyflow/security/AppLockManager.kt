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

  /** Switches to the phone-lock mode (call after the phone's own lock was confirmed). */
  suspend fun enableDeviceLock() {
    settings.setDeviceLock(true)
    lockedUntilElapsed = 0L
    _locked.value = false
  }

  /** The phone's lock was confirmed on the lock screen (or the phone has no screen lock any more). */
  fun unlockWithDeviceCredential() = unlockWithBiometric()

  fun unlockWithBiometric() {
    scope.launch {
      settings.resetPinFailures()
      lockedUntilElapsed = 0L
      _locked.value = false
    }
  }

  /** Unlock attempt from the lock screen. */
  suspend fun verifyPin(pin: String): PinCheckResult {
    // In phone-lock mode there is no PIN: nothing typed here may open the app.
    if (!settings.current().usesPinLock && settings.current().deviceLockEnabled) return PinCheckResult.Wrong(0)
    val result = checkWithRateLimit(pin)
    if (result == PinCheckResult.Success) _locked.value = false
    return result
  }

  /**
   * Checks the current PIN for settings actions (change / turn off lock) without changing the
   * lock state. Shares the same failure counter and penalty as the lock screen.
   */
  suspend fun checkPin(pin: String): PinCheckResult = checkWithRateLimit(pin)

  /**
   * Makes a new recovery code for a forgotten PIN ("ABCD-EFGH-JKLM-NPQR"). Only its hash is
   * stored; the code is shown once for the user to write down.
   */
  suspend fun createRecoveryCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = java.security.SecureRandom()
    val raw = (1..16).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    val hashed = withContext(Dispatchers.Default) { PinHasher.hash(normalizeCode(raw)) }
    settings.setRecoveryCode(hashed.hash, hashed.salt)
    return raw.chunked(4).joinToString("-")
  }

  /**
   * Unlocks with the recovery code and turns the PIN off (the user sets a new one in Settings).
   * Wrong codes count as wrong PIN attempts.
   */
  suspend fun recoverWithCode(code: String): PinCheckResult {
    val s = settings.current()
    if (s.recoveryHash == null || s.recoverySalt == null) return PinCheckResult.Wrong(0)
    val result = checkWithRateLimit(normalizeCode(code), s.recoveryHash, s.recoverySalt)
    if (result == PinCheckResult.Success) disableLock()
    return result
  }

  private fun normalizeCode(code: String) = code.uppercase().filter { it.isLetterOrDigit() }

  private suspend fun checkWithRateLimit(pin: String, expectedHash: String? = null, expectedSalt: String? = null): PinCheckResult {
    val s = settings.current()
    val hash = expectedHash ?: s.pinHash
    val salt = expectedSalt ?: s.pinSalt
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
  private suspend fun lockoutUntil(s: AppSettings, now: Long, elapsed: Long): Long? {
    if (elapsed < lockedUntilElapsed) return now + (lockedUntilElapsed - elapsed)
    // A penalty from before a reboot: the monotonic clock restarted and the wall clock may have
    // been moved forward. Start the same penalty again on this boot (only after 5+ wrong PINs).
    val boot = clock.bootId()
    if (s.failedPinAttempts >= FREE_ATTEMPTS && s.pinLockedUntil > 0 && s.pinLockBoot.isNotEmpty() && s.pinLockBoot != boot) {
      val penalty = (s.pinLockedUntil - s.pinLastFailureAt).coerceIn(0L, MAX_PENALTY_MS)
      if (penalty > 0) {
        lockedUntilElapsed = elapsed + penalty
        settings.recordPinFailure(s.failedPinAttempts, now + penalty, now, elapsed + penalty, boot)
        return now + penalty
      }
    }
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
    /** Longest penalty (30 s doubled 8 times ≈ 2 h). */
    private const val MAX_PENALTY_MS = (30L shl 8) * 1000
  }
}
