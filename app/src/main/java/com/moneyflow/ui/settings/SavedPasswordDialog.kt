package com.moneyflow.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.security.AppLockManager
import com.moneyflow.security.PinCheckResult
import com.moneyflow.security.SecretStore
import com.moneyflow.ui.theme.Gray600
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Passwords the app keeps (encrypted with the phone's secure key) and can show again. */
enum class SavedPassword(val title: String) { AUTO_BACKUP("자동 백업 비밀번호"), FAMILY("가족 비밀번호") }

sealed interface RevealResult {
  data class Shown(val password: String) : RevealResult
  data class Failed(val message: String) : RevealResult
}

@HiltViewModel
class SavedPasswordViewModel @Inject constructor(
  private val lock: AppLockManager,
  private val settings: SettingsRepository,
  private val secrets: SecretStore
) : ViewModel() {
  /** Checks the app PIN (when the lock is on) and returns the saved password. */
  fun reveal(kind: SavedPassword, pin: String?, onResult: (RevealResult) -> Unit) = viewModelScope.launch {
    val s = settings.current()
    if (s.isLockEnabled) {
      when (val r = lock.checkPin(pin.orEmpty())) {
        PinCheckResult.Success -> Unit
        is PinCheckResult.Wrong -> { onResult(RevealResult.Failed("PIN이 맞지 않아요.")); return@launch }
        is PinCheckResult.LockedOut -> { onResult(RevealResult.Failed("입력 시도가 너무 많아요. 잠시 후 다시 시도해 주세요.")); return@launch }
      }
    }
    val enc = when (kind) {
      SavedPassword.AUTO_BACKUP -> s.autoBackupPasswordEncrypted
      SavedPassword.FAMILY -> s.familySyncPasswordEncrypted
    }
    val pw = enc?.let { runCatching { secrets.decrypt(it) }.getOrNull() }
    onResult(if (pw != null) RevealResult.Shown(pw) else RevealResult.Failed("저장된 비밀번호가 없어요."))
  }

  fun lockEnabled(onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(settings.current().isLockEnabled) }
}

/** Shows a saved password after the app PIN is entered (if the app lock is on). */
@Composable
fun SavedPasswordDialog(kind: SavedPassword, lockEnabled: Boolean, onDismiss: () -> Unit, viewModel: SavedPasswordViewModel = hiltViewModel()) {
  var pin by remember { mutableStateOf("") }
  var shown by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(kind.title, fontWeight = FontWeight.Bold) },
    text = {
      Column {
        val pw = shown
        if (pw != null) {
          Text("다른 사람이 보지 않도록 주의해 주세요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
          Text(pw, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp).testTag("saved_password_text"))
        } else if (lockEnabled) {
          Text("확인을 위해 앱 잠금 PIN을 입력해 주세요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
          OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6); error = null },
            label = { Text("PIN") }, singleLine = true, isError = error != null,
            supportingText = error?.let { { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("saved_password_pin")
          )
        } else {
          Text("앱 잠금이 꺼져 있어요. 비밀번호를 화면에 보여 드릴까요?", style = MaterialTheme.typography.bodySmall, color = Gray600)
          error?.let { Text(it, color = com.moneyflow.ui.theme.AccentRedText) }
        }
      }
    },
    confirmButton = {
      if (shown == null) {
        TextButton(enabled = !lockEnabled || pin.length >= 4, onClick = {
          viewModel.reveal(kind, pin) { r ->
            when (r) {
              is RevealResult.Shown -> shown = r.password
              is RevealResult.Failed -> error = r.message
            }
          }
        }, modifier = Modifier.testTag("saved_password_show")) { Text("보기", fontWeight = FontWeight.Bold) }
      } else {
        TextButton(onClick = onDismiss) { Text("닫기") }
      }
    },
    dismissButton = { if (shown == null) TextButton(onClick = onDismiss) { Text("취소") } }
  )
}
