package com.moneyflow.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.security.AppLockManager
import com.moneyflow.security.PinCheckResult
import com.moneyflow.ui.common.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activity-level state: theme, screen security, app lock and the snackbar channel. */
@HiltViewModel
class AppViewModel @Inject constructor(
  private val settingsRepo: SettingsRepository,
  private val ledger: com.moneyflow.data.repository.LedgerRepository,
  private val lock: AppLockManager,
  val messages: UserMessageBus,
  /** Add-form requests from notifications / the widget. */
  val prefill: com.moneyflow.ui.common.PrefillStore
) : ViewModel() {

  /** null until settings have loaded (avoid flashing the wrong theme / lock state). */
  val settings: StateFlow<AppSettings?> = settingsRepo.settings
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val locked: StateFlow<Boolean> = lock.locked

  private val _whatsNew = kotlinx.coroutines.flow.MutableStateFlow<List<com.moneyflow.ui.whatsnew.ReleaseNote>>(emptyList())
  /** Release notes to show once after an update (empty = nothing to show). */
  val whatsNew: StateFlow<List<com.moneyflow.ui.whatsnew.ReleaseNote>> = _whatsNew

  init {
    viewModelScope.launch {
      val current = com.moneyflow.BuildConfig.VERSION_NAME
      val s = settingsRepo.current()
      if (s.lastSeenVersion == current) return@launch
      // A brand-new install has nothing to compare with: just remember the version.
      val existingUser = s.lastSeenVersion != null || ledger.getAccounts().isNotEmpty()
      val notes = if (existingUser) com.moneyflow.ui.whatsnew.ReleaseNotes.since(s.lastSeenVersion, current) else emptyList()
      if (notes.isEmpty()) settingsRepo.setLastSeenVersion(current) else _whatsNew.value = notes
    }
  }

  fun dismissWhatsNew() = viewModelScope.launch {
    _whatsNew.value = emptyList()
    settingsRepo.setLastSeenVersion(com.moneyflow.BuildConfig.VERSION_NAME)
  }

  fun verifyPin(pin: String, onResult: (PinCheckResult) -> Unit) = viewModelScope.launch {
    onResult(lock.verifyPin(pin))
  }

  fun unlockWithBiometric() = lock.unlockWithBiometric()

  fun unlockWithDeviceLock() = lock.unlockWithDeviceCredential()

  /** The phone's screen lock was removed: the app opens, and the user is told why it isn't protected. */
  fun deviceLockMissing() {
    lock.unlockWithDeviceCredential()
    viewModelScope.launch {
      // The message area appears together with the app content.
      kotlinx.coroutines.delay(800)
      messages.show("휴대폰 화면 잠금이 꺼져 있어 앱 잠금이 동작하지 않아요. 휴대폰 설정에서 화면 잠금을 켜 주세요.")
    }
  }

  fun recover(code: String, onResult: (PinCheckResult) -> Unit) = viewModelScope.launch {
    onResult(lock.recoverWithCode(code))
  }
}
