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
  settingsRepo: SettingsRepository,
  private val lock: AppLockManager,
  val messages: UserMessageBus
) : ViewModel() {

  /** null until settings have loaded (avoid flashing the wrong theme / lock state). */
  val settings: StateFlow<AppSettings?> = settingsRepo.settings
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val locked: StateFlow<Boolean> = lock.locked

  fun verifyPin(pin: String, onResult: (PinCheckResult) -> Unit) = viewModelScope.launch {
    onResult(lock.verifyPin(pin))
  }

  fun unlockWithBiometric() = lock.unlockWithBiometric()
}
