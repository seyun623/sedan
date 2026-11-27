package com.moneyflow.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.moneyflow.ui.theme.AccentRedText
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.BiometricAuthHelper

/**
 * Lock screen of the "휴대폰 잠금으로 열기" mode: the phone's own lock (fingerprint, face,
 * pattern, phone PIN) opens the app, so there is no app PIN to remember or forget.
 */
@Composable
fun DeviceLockScreen(
  onUnlocked: () -> Unit,
  /** The phone has no screen lock any more: the app can't ask for one. */
  onNoDeviceLock: () -> Unit,
  fingerprintFirst: Boolean = false
) {
  val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
  var message by remember { mutableStateOf<String?>(null) }
  var autoPrompted by rememberSaveable { mutableStateOf(false) }

  fun prompt() {
    val act = activity ?: return
    if (!BiometricAuthHelper.isDeviceSecure(act)) { onNoDeviceLock(); return }
    message = null
    BiometricAuthHelper.showDeviceLockPrompt(
      activity = act,
      fingerprintFirst = fingerprintFirst,
      subtitle = if (fingerprintFirst) "지문으로 확인해요 (휴대폰 PIN·패턴도 돼요)" else "휴대폰 잠금(지문·얼굴·패턴·PIN)으로 확인해요",
      onSuccess = onUnlocked,
      onError = { code, err ->
        // Cancelled by the user: just stay on this screen.
        if (code != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
          code != androidx.biometric.BiometricPrompt.ERROR_CANCELED &&
          code != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
        ) message = if (err.any { it in '가'..'힣' }) err.toString() else "확인하지 못했어요. 다시 눌러 주세요."
      }
    )
  }

  // Ask once as soon as the screen is in front (the system prompt needs a resumed activity).
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      if (!autoPrompted) {
        autoPrompted = true
        prompt()
      }
    }
  }

  Box(
    Modifier.fillMaxSize().background(CardSurface).safeDrawingPadding().testTag("device_lock_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(Modifier.size(72.dp).background(TossBlueContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = com.moneyflow.ui.theme.TossBlueText, modifier = Modifier.size(36.dp))
      }
      Spacer(Modifier.height(16.dp))
      Text("머니플로우가 잠겨 있어요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gray900, textAlign = TextAlign.Center)
      Text(
        "휴대폰을 열 때 쓰는 지문·얼굴·패턴·PIN으로 열 수 있어요.",
        style = MaterialTheme.typography.bodyMedium, color = Gray600, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp)
      )
      Box(Modifier.heightIn(min = 40.dp).padding(top = 12.dp), contentAlignment = Alignment.Center) {
        message?.let {
          Text(it, color = AccentRedText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
      }
      Button(
        onClick = ::prompt,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("device_lock_open"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
      ) { Text("열기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
      Text(
        "휴대폰 잠금을 잊었다면 삼성 계정의 '내 디바이스 찾기'나 구글 계정으로 휴대폰 잠금을 먼저 풀어 주세요.",
        style = MaterialTheme.typography.bodySmall, color = Gray600, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp)
      )
    }
  }
}
