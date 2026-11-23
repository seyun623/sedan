package com.moneyflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.ui.common.PinDots
import com.moneyflow.ui.common.PinPad
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.BiometricAuthHelper
import com.moneyflow.util.BiometricStatus

private enum class PinFlow { SETUP, CHANGE, DISABLE }

private val TimeoutOptions = listOf(0 to "즉시", 30 to "30초", 60 to "1분", 300 to "5분")

@Composable
fun SecuritySettingsScreen(
  onBack: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val settings = state.settings
  val context = LocalContext.current
  val biometricStatus = remember { BiometricAuthHelper.checkBiometricAvailability(context) }
  var flow by rememberSaveable { mutableStateOf<PinFlow?>(null) }

  SettingsScaffold(title = "보안", onBack = onBack) {
    SettingsSection("앱 잠금") {
      SettingsSwitchItem(
        title = "앱 잠금",
        subtitle = if (settings.isLockEnabled) "앱을 열 때 PIN을 확인해요" else "숫자 4~6자리 PIN으로 앱을 잠가요",
        icon = Icons.Filled.Lock,
        checked = settings.isLockEnabled,
        onCheckedChange = { on -> flow = if (on) PinFlow.SETUP else PinFlow.DISABLE },
        modifier = Modifier.testTag("security_lock_switch")
      )
      if (settings.isLockEnabled) {
        SettingsItem(
          title = "PIN 변경",
          icon = Icons.Filled.Password,
          onClick = { flow = PinFlow.CHANGE },
          modifier = Modifier.testTag("security_change_pin")
        )
        if (biometricStatus == BiometricStatus.READY) {
          SettingsSwitchItem(
            title = "생체 인증",
            subtitle = "지문이나 얼굴로 잠금을 해제해요",
            icon = Icons.Filled.Fingerprint,
            checked = settings.biometricEnabled,
            onCheckedChange = viewModel::setBiometricEnabled,
            modifier = Modifier.testTag("security_biometric_switch")
          )
        } else {
          SettingsItem(
            title = "생체 인증",
            subtitle = biometricStatus.description,
            icon = Icons.Filled.Fingerprint,
            enabled = false
          )
        }
      }
    }

    if (settings.isLockEnabled) {
      SettingsSection("자동 잠금") {
        Text(
          "앱을 벗어난 뒤 이 시간이 지나면 다시 잠가요.",
          style = MaterialTheme.typography.bodySmall,
          color = Gray600,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        TimeoutOptions.forEach { (seconds, label) ->
          SettingsRadioItem(
            title = label,
            selected = settings.lockTimeoutSeconds == seconds,
            onSelect = { viewModel.setLockTimeout(seconds) }
          )
        }
      }
    }

    SettingsSection("화면 보호") {
      SettingsSwitchItem(
        title = "화면 보호",
        subtitle = "화면 캡처를 막고 최근 앱 화면에서 내용을 가려요",
        icon = Icons.Filled.VisibilityOff,
        checked = settings.secureScreen,
        onCheckedChange = viewModel::setSecureScreen,
        modifier = Modifier.testTag("security_secure_screen_switch")
      )
    }

    if (settings.isLockEnabled) {
      Button(
        onClick = viewModel::lockNow,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("lock_now_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
      ) {
        Icon(Icons.Filled.Lock, contentDescription = null)
        Text("  지금 잠그기", fontWeight = FontWeight.Bold)
      }
    }
    Spacer(Modifier.height(24.dp))
  }

  flow?.let { current ->
    PinFlowDialog(
      flow = current,
      onDismiss = { flow = null },
      onSetPin = { pin, done -> viewModel.setPin(pin) { ok -> done(ok, if (ok) null else "PIN은 숫자 4~6자리로 정해 주세요.") } },
      onChangePin = { old, new, done -> viewModel.changePin(old, new, done) },
      onDisable = { pin, done -> viewModel.disableLock(pin, done) }
    )
  }
}

private enum class PinStep { CURRENT, NEW, CONFIRM }

/**
 * Full-screen PIN entry for setting, changing or removing the PIN. Entered PINs are kept only in
 * memory (`remember`), never in saved state; after a configuration change the flow restarts.
 */
@Composable
private fun PinFlowDialog(
  flow: PinFlow,
  onDismiss: () -> Unit,
  onSetPin: (String, (Boolean, String?) -> Unit) -> Unit,
  onChangePin: (String, String, (Boolean, String?) -> Unit) -> Unit,
  onDisable: (String, (Boolean, String?) -> Unit) -> Unit
) {
  val firstStep = if (flow == PinFlow.SETUP) PinStep.NEW else PinStep.CURRENT
  var step by remember { mutableStateOf(firstStep) }
  var input by remember { mutableStateOf("") }
  var currentPin by remember { mutableStateOf("") }
  var newPin by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun restart(message: String, toStep: PinStep) {
    error = message
    input = ""
    if (toStep == PinStep.CURRENT) currentPin = ""
    if (toStep != PinStep.CONFIRM) newPin = ""
    step = toStep
  }

  fun finish(ok: Boolean, message: String?) {
    busy = false
    if (ok) onDismiss() else restart(message ?: "다시 시도해 주세요.", firstStep)
  }

  fun advance() {
    if (busy) return
    val pin = input
    when (step) {
      PinStep.CURRENT -> {
        if (pin.length < 4) return
        if (flow == PinFlow.DISABLE) {
          busy = true
          onDisable(pin, ::finish)
        } else {
          currentPin = pin
          input = ""
          error = null
          step = PinStep.NEW
        }
      }
      PinStep.NEW -> {
        if (pin.length < 4) return
        newPin = pin
        input = ""
        error = null
        step = PinStep.CONFIRM
      }
      PinStep.CONFIRM -> {
        if (pin != newPin) {
          restart("PIN이 서로 달라요. 새 PIN을 다시 입력해 주세요.", PinStep.NEW)
          return
        }
        busy = true
        if (flow == PinFlow.SETUP) onSetPin(pin, ::finish) else onChangePin(currentPin, pin, ::finish)
      }
    }
  }

  val title = when (flow) {
    PinFlow.SETUP -> "앱 잠금 설정"
    PinFlow.CHANGE -> "PIN 변경"
    PinFlow.DISABLE -> "앱 잠금 끄기"
  }
  val prompt = when (step) {
    PinStep.CURRENT -> "현재 PIN을 입력해 주세요"
    PinStep.NEW -> "새 PIN을 입력해 주세요 (숫자 4~6자리)"
    PinStep.CONFIRM -> "한 번 더 입력해 주세요"
  }
  val maxLen = if (step == PinStep.CONFIRM) newPin.length else 6

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    Box(
      Modifier.fillMaxSize().background(CardSurface).safeDrawingPadding().testTag("pin_flow_dialog"),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Gray900) }
          Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gray900)
        }
        Spacer(Modifier.height(32.dp))
        Icon(
          if (flow == PinFlow.DISABLE) Icons.Filled.LockOpen else Icons.Filled.Lock,
          contentDescription = null,
          tint = TossBlue
        )
        Spacer(Modifier.height(12.dp))
        Text(prompt, style = MaterialTheme.typography.titleMedium, color = Gray900, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PinDots(
          length = maxLen,
          filled = input.length,
          error = error != null && input.isEmpty(),
          modifier = Modifier.semantics { contentDescription = "PIN ${input.length}자리 입력됨" }
        )
        Box(Modifier.heightIn(min = 40.dp).padding(top = 12.dp), contentAlignment = Alignment.Center) {
          error?.let {
            Text(
              it,
              color = AccentRed,
              style = MaterialTheme.typography.bodySmall,
              textAlign = TextAlign.Center,
              modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
          }
        }
        PinPad(
          enabled = !busy,
          onDigit = { d ->
            if (input.length < maxLen) {
              input += d
              if (input.length == maxLen) advance()
            }
          },
          onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) },
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (step != PinStep.CONFIRM) {
          Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = ::advance, enabled = input.length >= 4 && !busy) {
              Text(if (step == PinStep.CURRENT && flow == PinFlow.DISABLE) "확인" else "다음", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}
