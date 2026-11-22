package com.moneyflow.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

  private var backgroundedAt: Long? = null

  /** Called once at startup: unlock immediately when no PIN is configured. */
  fun initialize() {
    scope.launch { if (!settings.current().isLockEnabled) _locked.value = false }
  }

  override fun onStop(owner: LifecycleOwner) {
    backgroundedAt = clock.nowMillis()
  }

  override fun onStart(owner: LifecycleOwner) {
    val since = backgroundedAt ?: return
    backgroundedAt = null
    scope.launch {
      val s = settings.current()
      if (s.isLockEnabled && clock.nowMillis() - since >= s.lockTimeoutSeconds * 1000L) {
        _locked.value = true
      }
    }
  }

  fun lockNow() {
    scope.launch { if (settings.current().isLockEnabled) _locked.value = true }
  }

  fun unlockWithBiometric() {
    scope.launch {
      settings.resetPinFailures()
      _locked.value = false
    }
  }

  suspend fun verifyPin(pin: String): PinCheckResult {
    val s = settings.current()
    val hash = s.pinHash
    val salt = s.pinSalt
    if (hash == null || salt == null) {
      _locked.value = false
      return PinCheckResult.Success
    }
    val now = clock.nowMillis()
    if (s.pinLockedUntil > now) return PinCheckResult.LockedOut(s.pinLockedUntil)

    val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, hash, salt) }
    if (ok) {
      settings.resetPinFailures()
      _locked.value = false
      return PinCheckResult.Success
    }
    val attempts = s.failedPinAttempts + 1
    return if (attempts >= FREE_ATTEMPTS) {
      val penaltySeconds = 30L shl (attempts - FREE_ATTEMPTS).coerceAtMost(8) // 30s .. ~2h
      val until = now + penaltySeconds * 1000
      settings.recordPinFailure(attempts, until)
      PinCheckResult.LockedOut(until)
    } else {
      settings.recordPinFailure(attempts, 0L)
      PinCheckResult.Wrong(FREE_ATTEMPTS - attempts)
    }
  }

  /** Verifies the current PIN without changing lock state (for PIN change / disable). */
  suspend fun checkPinOnly(pin: String): Boolean {
    val s = settings.current()
    val hash = s.pinHash ?: return true
    val salt = s.pinSalt ?: return true
    return withContext(Dispatchers.Default) { PinHasher.verify(pin, hash, salt) }
  }

  suspend fun setPin(pin: String) {
    require(PinHasher.isValidPin(pin))
    val hashed = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
    settings.setPin(hashed.hash, hashed.salt)
    _locked.value = false
  }

  suspend fun disableLock() {
    settings.clearPin()
    _locked.value = false
  }

  companion object {
    const val FREE_ATTEMPTS = 5
  }
}
