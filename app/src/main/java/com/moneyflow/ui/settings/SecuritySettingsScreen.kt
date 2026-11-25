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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
  var notice by rememberSaveable { mutableStateOf<String?>(null) }

  SettingsScaffold(title = "보안", onBack = onBack) {
    SettingsSection("앱 잠금") {
      val activity = context as? androidx.fragment.app.FragmentActivity
      // Changing the lock always needs the current lock first (phone lock or app PIN).
      fun confirmPhoneLock(then: () -> Unit) {
        val act = activity ?: return
        if (!BiometricAuthHelper.isDeviceSecure(act)) {
          notice = "휴대폰에 화면 잠금이 없어요. 휴대폰 설정 → 잠금화면에서 패턴·PIN·지문 중 하나를 먼저 켜 주세요."
          return
        }
        BiometricAuthHelper.showDeviceLockPrompt(act, title = "휴대폰 잠금 확인", onSuccess = then, onError = { _, _ -> })
      }
      SettingsRadioItem(
        title = "휴대폰 잠금으로 열기 (추천)",
        subtitle = "지문·얼굴·패턴처럼 휴대폰을 여는 방법으로 앱도 열어요. 따로 외울 비밀번호가 없어요",
        selected = settings.usesDeviceLock,
        onSelect = { if (!settings.usesDeviceLock) confirmPhoneLock { viewModel.enableDeviceLock() } }
      )
      SettingsRadioItem(
        title = "앱 전용 PIN",
        subtitle = "숫자 4~6자리를 따로 정해요. 휴대폰 비밀번호를 가족과 같이 쓸 때 골라 주세요",
        selected = settings.usesPinLock,
        onSelect = {
          when {
            settings.usesPinLock -> Unit
            settings.usesDeviceLock -> confirmPhoneLock { flow = PinFlow.SETUP }
            else -> flow = PinFlow.SETUP
          }
        }
      )
      SettingsRadioItem(
        title = "잠그지 않기",
        selected = !settings.isLockEnabled,
        onSelect = {
          when {
            settings.usesPinLock -> flow = PinFlow.DISABLE
            settings.usesDeviceLock -> confirmPhoneLock { viewModel.disableDeviceLock() }
          }
        }
      )
      if (settings.usesPinLock) {
        SettingsItem(
          title = "PIN 변경",
          icon = Icons.Filled.Password,
          onClick = { flow = PinFlow.CHANGE },
          modifier = Modifier.testTag("security_change_pin")
        )
        SettingsItem(
          title = if (settings.hasRecoveryCode) "복구 코드 새로 만들기" else "복구 코드 만들기",
          subtitle = if (settings.hasRecoveryCode) "PIN을 잊었을 때 쓰는 코드예요. 새로 만들면 예전 코드는 쓸 수 없어요"
            else "PIN을 잊었을 때 앱을 열 수 있는 코드를 만들어 두세요",
          icon = Icons.Filled.Password,
          showChevron = false,
          onClick = viewModel::newRecoveryCode,
          modifier = Modifier.testTag("security_recovery_code")
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
      if (settings.isLockEnabled) {
        SettingsSwitchItem(
          title = "잠금 중에도 위젯에 금액 표시",
          subtitle = "끄면 앱 잠금을 켠 동안 홈 화면 위젯에 금액을 보여 주지 않아요",
          icon = Icons.Filled.VisibilityOff,
          checked = settings.widgetShowAmountsWhenLocked,
          onCheckedChange = viewModel::setWidgetShowAmountsWhenLocked,
          modifier = Modifier.testTag("security_widget_amounts")
        )
      }
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

  notice?.let { msg ->
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { notice = null },
      title = { Text("확인이 필요해요", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = { TextButton(onClick = { notice = null }) { Text("확인") } }
    )
  }
  val recoveryCode by viewModel.recoveryCode.collectAsStateWithLifecycle()
  recoveryCode?.let { code ->
    RecoveryCodeShownDialog(code, onDone = viewModel::recoveryCodeShown)
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

/** Shows a new recovery code once. It can't be seen again later. */
@Composable
private fun RecoveryCodeShownDialog(code: String, onDone: () -> Unit) {
  val context = LocalContext.current
  androidx.compose.material3.AlertDialog(
    onDismissRequest = { },
    title = { Text("복구 코드를 적어 두세요", fontWeight = FontWeight.Bold) },
    text = {
      androidx.compose.foundation.layout.Column {
        Text(
          "PIN을 잊었을 때 잠금 화면의 'PIN을 잊었어요'에서 이 코드를 넣으면 앱이 열려요. 이 화면을 닫으면 다시 볼 수 없어요. 종이에 적거나 가족에게 보내 두세요.",
          style = MaterialTheme.typography.bodySmall, color = Gray600
        )
        Text(
          code,
          style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
          fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
          modifier = Modifier.padding(vertical = 16.dp).testTag("recovery_code_text")
        )
        androidx.compose.material3.OutlinedButton(onClick = {
          val cm = context.getSystemService(android.content.ClipboardManager::class.java)
          cm.setPrimaryClip(android.content.ClipData.newPlainText("복구 코드", code))
        }) { Text("복사하기") }
      }
    },
    confirmButton = { TextButton(onClick = onDone, modifier = Modifier.testTag("recovery_code_done")) { Text("적어 두었어요", fontWeight = FontWeight.Bold) } }
  )
}
