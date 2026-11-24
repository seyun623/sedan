package com.moneyflow.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.security.PinCheckResult
import com.moneyflow.ui.common.PinDots
import com.moneyflow.ui.common.PinPad
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.BiometricAuthHelper
import com.moneyflow.util.BiometricStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_PIN = 6
private const val MIN_PIN = 4

@Composable
fun LockScreen(
  settings: AppSettings,
  onVerifyPin: (String, (PinCheckResult) -> Unit) -> Unit,
  onBiometricSuccess: () -> Unit
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  val biometricStatus = remember { BiometricAuthHelper.checkBiometricAvailability(context) }
  val canUseBiometric = settings.biometricEnabled && biometricStatus == BiometricStatus.READY && activity != null

  // The PIN is deliberately kept in plain `remember`: it must never be written to saved state.
  var pin by remember { mutableStateOf("") }
  var verifying by remember { mutableStateOf(false) }
  var isError by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var lockedUntil by remember { mutableLongStateOf(settings.pinLockedUntil) }
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var autoPrompted by rememberSaveable { mutableStateOf(false) }
  val shake = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()

  val lockedOut = lockedUntil > now
  LaunchedEffect(lockedUntil) {
    now = System.currentTimeMillis()
    while (lockedUntil > now) {
      delay(1_000)
      now = System.currentTimeMillis()
    }
  }

  fun submit() {
    if (verifying || lockedOut || pin.length < MIN_PIN) return
    verifying = true
    val entered = pin
    onVerifyPin(entered) { result ->
      verifying = false
      when (result) {
        PinCheckResult.Success -> {
          message = null
          isError = false
        }
        is PinCheckResult.Wrong -> {
          pin = ""
          isError = true
          message = "PIN이 맞지 않아요. ${result.attemptsLeft}회 남음"
          scope.launch {
            shake.animateTo(0f, keyframes {
              durationMillis = 400
              -18f at 50; 18f at 100; -14f at 150; 14f at 200; -8f at 250; 8f at 300; 0f at 400
            })
          }
        }
        is PinCheckResult.LockedOut -> {
          pin = ""
          isError = true
          lockedUntil = result.untilMillis
          now = System.currentTimeMillis()
          message = null
        }
      }
    }
  }

  fun promptBiometric() {
    val act = activity ?: return
    message = null
    BiometricAuthHelper.showBiometricPrompt(
      activity = act,
      title = "머니플로우 잠금 해제",
      subtitle = "지문 또는 얼굴로 잠금을 해제하세요",
      negativeButtonText = "PIN 입력",
      onSuccess = { onBiometricSuccess() },
      onError = { code, err ->
        // User cancelled / chose PIN: no message needed.
        if (code != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
          code != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
          code != androidx.biometric.BiometricPrompt.ERROR_CANCELED
        ) message = err.toString()
      },
      onFailed = { message = "생체 인증에 실패했어요. 다시 시도해 주세요." }
    )
  }

  LaunchedEffect(canUseBiometric) {
    if (canUseBiometric && !autoPrompted) {
      autoPrompted = true
      promptBiometric()
    }
  }

  Box(
    Modifier
      .fillMaxSize()
      .background(CardSurface)
      .safeDrawingPadding()
      .testTag("lock_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        Modifier.size(72.dp).background(TossBlueContainer, CircleShape),
        contentAlignment = Alignment.Center
      ) { Icon(Icons.Filled.Lock, contentDescription = null, tint = TossBlue, modifier = Modifier.size(36.dp)) }
      Spacer(Modifier.height(16.dp))
      Text("머니플로우 잠금", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Gray900)
      Spacer(Modifier.height(6.dp))
      Text("PIN을 입력해 주세요", style = MaterialTheme.typography.bodyMedium, color = Gray600, textAlign = TextAlign.Center)

      Spacer(Modifier.height(24.dp))
      PinDots(
        length = MAX_PIN,
        filled = pin.length,
        error = isError && pin.isEmpty(),
        modifier = Modifier
          .offset { IntOffset(shake.value.roundToInt(), 0) }
          .semantics { contentDescription = "PIN ${pin.length}자리 입력됨" }
      )

      val statusText = when {
        lockedOut -> {
          val secs = ((lockedUntil - now + 999) / 1000).coerceAtLeast(1)
          val label = if (secs >= 60) "${secs / 60}분 ${secs % 60}초" else "${secs}초"
          "입력 시도가 너무 많아요. $label 후 다시 시도해 주세요."
        }
        else -> message
      }
      Box(Modifier.heightIn(min = 40.dp).padding(top = 12.dp), contentAlignment = Alignment.Center) {
        if (statusText != null) {
          // The countdown changes every second: announce only a fixed sentence, not every tick.
          Text(
            statusText,
            color = com.moneyflow.ui.theme.AccentRedText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
              liveRegion = LiveRegionMode.Polite
              contentDescription = if (lockedOut) "입력 시도가 너무 많아요. 잠시 후 다시 시도해 주세요." else statusText
            }
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      PinPad(
        enabled = !lockedOut && !verifying,
        onDigit = { d ->
          if (pin.length < MAX_PIN) {
            isError = false
            if (!lockedOut) message = null
            pin += d
            if (pin.length == MAX_PIN) submit()
          }
        },
        onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        modifier = Modifier.fillMaxWidth().testTag("lock_pin_pad")
      )

      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canUseBiometric) {
          TextButton(onClick = ::promptBiometric, modifier = Modifier.testTag("lock_biometric_button")) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = TossBlue)
            Text(" 생체 인증", color = TossBlue, fontWeight = FontWeight.Bold)
          }
        }
        TextButton(
          onClick = ::submit,
          enabled = pin.length >= MIN_PIN && !lockedOut && !verifying,
          modifier = Modifier.testTag("lock_confirm_button")
        ) { Text("확인", fontWeight = FontWeight.Bold) }
      }
      var forgotHelp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
      TextButton(onClick = { forgotHelp = true }, modifier = Modifier.testTag("lock_forgot_pin")) {
        Text("PIN을 잊었어요", color = Gray600)
      }
      if (forgotHelp) {
        androidx.compose.material3.AlertDialog(
          onDismissRequest = { forgotHelp = false },
          title = { Text("PIN을 잊었을 때", fontWeight = FontWeight.Bold) },
          text = {
            Text(
              "PIN은 이 휴대폰에만 있어서 다시 알려 드릴 수 없어요.\n\n" +
                "1. 앱을 지우고 다시 설치해요.\n" +
                "2. 설정 → 백업 및 복원 → '백업 파일 불러오기'로 마지막 백업을 복원해요. (드라이브 자동 백업을 켜 두었다면 '머니플로우 가족' 폴더에 있어요)\n\n" +
                "백업 뒤에 적은 기록은 다시 입력해야 해요. 가족 자동 합산을 쓰고 있다면 가족 폰에는 마지막으로 올라간 기록이 남아 있어요."
            )
          },
          confirmButton = { TextButton(onClick = { forgotHelp = false }) { Text("확인") } }
        )
      }
    }
  }
}
