package com.moneyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SampleDataSeeder
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.data.repository.ThemeMode
import com.moneyflow.domain.capture.AutoCaptureProcessor
import com.moneyflow.domain.capture.ParseResult
import com.moneyflow.security.AppLockManager
import com.moneyflow.security.PinCheckResult
import com.moneyflow.security.PinHasher
import com.moneyflow.ui.common.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
  val settings: AppSettings = AppSettings(),
  val accounts: List<AccountEntity> = emptyList(),
  val canLoadSampleData: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settingsRepo: SettingsRepository,
  private val ledger: LedgerRepository,
  private val lock: AppLockManager,
  private val capture: AutoCaptureProcessor,
  private val seeder: SampleDataSeeder,
  private val messages: UserMessageBus
) : ViewModel() {

  val uiState: StateFlow<SettingsUiState> = combine(settingsRepo.settings, ledger.accounts) { s, accounts ->
    SettingsUiState(s, accounts, canLoadSampleData = accounts.isEmpty())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

  // ---- Lock / PIN ----

  private val _recoveryCode = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
  /** A just-made recovery code to show once (null = no dialog). */
  val recoveryCode: StateFlow<String?> = _recoveryCode

  /** Switches to the phone-lock mode; call only after the phone's own lock was confirmed. */
  fun enableDeviceLock() = viewModelScope.launch {
    lock.enableDeviceLock()
    messages.show("이제 휴대폰 잠금으로 앱을 열어요.")
  }

  /** Turns the phone-lock mode off; call only after the phone's own lock was confirmed. */
  fun disableDeviceLock() = viewModelScope.launch {
    lock.disableLock()
    messages.show("앱 잠금을 껐어요.")
  }

  fun newRecoveryCode() = viewModelScope.launch { _recoveryCode.value = lock.createRecoveryCode() }
  fun recoveryCodeShown() { _recoveryCode.value = null }

  /** Sets a new PIN (first time). Returns false if the PIN format is invalid. */
  fun setPin(pin: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
    if (!PinHasher.isValidPin(pin)) { onDone(false); return@launch }
    lock.setPin(pin)
    onDone(true)
    // A way back in if the PIN is forgotten.
    _recoveryCode.value = lock.createRecoveryCode()
  }

  /** Changes the PIN after verifying the current one. */
  fun changePin(currentPin: String, newPin: String, onDone: (Boolean, String?) -> Unit) = viewModelScope.launch {
    pinError(lock.checkPin(currentPin))?.let { onDone(false, it); return@launch }
    if (!PinHasher.isValidPin(newPin)) { onDone(false, "PIN은 숫자 4~6자리로 정해 주세요."); return@launch }
    lock.setPin(newPin)
    messages.show("PIN을 변경했어요.")
    onDone(true, null)
  }

  fun disableLock(currentPin: String, onDone: (Boolean, String?) -> Unit) = viewModelScope.launch {
    pinError(lock.checkPin(currentPin))?.let { onDone(false, it); return@launch }
    lock.disableLock()
    messages.show("앱 잠금을 껐어요.")
    onDone(true, null)
  }

  /** null when the PIN was correct, otherwise the message to show. */
  private fun pinError(result: PinCheckResult): String? = when (result) {
    PinCheckResult.Success -> null
    is PinCheckResult.Wrong -> "현재 PIN이 맞지 않아요. (${result.attemptsLeft}회 남음)"
    is PinCheckResult.LockedOut -> {
      val secs = ((result.untilMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
      "PIN을 여러 번 틀렸어요. ${if (secs >= 60) "${secs / 60}분" else "${secs}초"} 뒤에 다시 시도해 주세요."
    }
  }

  fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBiometricEnabled(enabled) }
  fun setLockTimeout(seconds: Int) = viewModelScope.launch { settingsRepo.setLockTimeout(seconds) }
  fun setWidgetShowAmountsWhenLocked(enabled: Boolean) = viewModelScope.launch { settingsRepo.setWidgetShowAmountsWhenLocked(enabled) }
  fun setSecureScreen(enabled: Boolean) = viewModelScope.launch { settingsRepo.setSecureScreen(enabled) }
  fun lockNow() = lock.lockNow()

  fun setCardAlertsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepo.setCardAlertsEnabled(enabled) }
  fun setBudgetAlerts(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBudgetAlerts(enabled) }
  fun setMonthlySummary(enabled: Boolean) = viewModelScope.launch { settingsRepo.setMonthlySummary(enabled) }
  fun setSplitInstallments(enabled: Boolean) = viewModelScope.launch { settingsRepo.setSplitInstallments(enabled) }
  fun setSimpleMode(enabled: Boolean) = viewModelScope.launch { settingsRepo.setSimpleMode(enabled) }
  fun setBillReminders(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBillReminders(enabled) }
  fun setBackupReminder(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBackupReminder(enabled) }

  // ---- Appearance ----
  fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }

  // ---- Auto capture ----
  fun setNotificationCapture(enabled: Boolean) = viewModelScope.launch { settingsRepo.setNotificationCapture(enabled) }
  fun setSmsCapture(enabled: Boolean) = viewModelScope.launch { settingsRepo.setSmsCapture(enabled) }
  fun setDefaultCaptureAccount(id: Long?) = viewModelScope.launch { settingsRepo.setDefaultCaptureAccount(id) }

  /** Parses sample text without saving anything (test box on the auto-capture screen). */
  fun previewParse(text: String): ParseResult = capture.preview(text)

  // ---- Data ----
  fun loadSampleData() = viewModelScope.launch {
    if (seeder.canSeed()) {
      seeder.seed()
      messages.show("샘플 데이터를 불러왔어요.")
    }
  }

  /** All transactions for a full CSV export. */
  suspend fun allTransactions(): List<TransactionEntity> = ledger.transactions.first()
}
