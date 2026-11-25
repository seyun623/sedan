package com.moneyflow.ui.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.domain.capture.AutoCaptureProcessor
import com.moneyflow.domain.capture.CaptureChannel
import com.moneyflow.domain.capture.CaptureLog
import com.moneyflow.domain.capture.CaptureLogEntry
import com.moneyflow.domain.capture.CaptureOutcome
import com.moneyflow.domain.capture.NotificationText
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureDiagnosticsViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val processor: AutoCaptureProcessor,
  private val log: CaptureLog
) : ViewModel() {
  val entries: StateFlow<List<CaptureLogEntry>> = log.entries

  fun clear() = log.clear()

  private val _testState = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
  /** What happened to the last test notification (shown under the button). */
  val testState: StateFlow<String?> = _testState

  /**
   * Sends the test notification and waits a few seconds for the listener to read it. The answer
   * tells the user directly whether the phone lets the app read notifications.
   */
  fun runNotificationTest(accessGranted: Boolean) = viewModelScope.launch {
    if (!accessGranted) { _testState.value = "⚠ 먼저 위 '알림 접근 허용'을 해 주세요."; return@launch }
    val sentAt = System.currentTimeMillis()
    if (!sendTestNotification()) {
      _testState.value = "⚠ 알림을 보낼 수 없어요. 휴대폰 설정 → 애플리케이션 → 머니플로우 → 알림을 켜 주세요."
      return@launch
    }
    _testState.value = "시험 알림을 보냈어요. 앱이 읽는지 확인하는 중…"
    val seen = kotlinx.coroutines.withTimeoutOrNull(10_000) {
      log.entries.first { list -> list.any { it.channel == "시험" && it.time >= sentAt - 1_000 } }
    }
    _testState.value = if (seen != null) {
      val e = seen.first { it.channel == "시험" && it.time >= sentAt - 1_000 }
      if (e.recorded) "✅ 정상이에요. 앱이 알림을 읽었어요. 이제 카드를 쓰면 자동으로 기록돼요. (시험이라 실제로 기록되지는 않아요)"
      else "⚠ 알림은 읽었지만 기록할 수 없어요: ${e.result.removePrefix("건너뜀 · ")}"
    } else {
      "⚠ 10초 안에 알림을 읽지 못했어요. 휴대폰이 알림 읽기를 막고 있어요.\n" +
        "① 위 '알림 접근 허용'에서 머니플로우를 껐다가 다시 켜 주세요.\n" +
        "② 배터리 사용을 '제한 없음'으로 바꿔 주세요.\n" +
        "③ 그래도 안 되면 휴대폰을 다시 시작한 뒤 한 번 더 시험해 주세요."
    }
    // The listener's own answer: whether it is connected right now.
    com.moneyflow.service.FinanceNotificationListenerService.rebind(context)
  }

  /** Runs pasted text through every real check (switches excluded) without saving. */
  fun tryText(text: String, onResult: (CaptureOutcome) -> Unit) = viewModelScope.launch {
    onResult(processor.process(CaptureChannel.TEST, "test", "시험", "", text, System.currentTimeMillis()))
  }

  /**
   * Posts a sample card alert as a real notification. When the listener is running it reads it
   * (marked as a test, nothing saved) and the result appears in the log within a few seconds.
   * Returns false when notifications can't be posted.
   */
  fun sendTestNotification(): Boolean {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return false
    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(NotificationChannel(TEST_CHANNEL, "자동 등록 시험", NotificationManager.IMPORTANCE_LOW))
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val n = NotificationCompat.Builder(context, TEST_CHANNEL)
      .setSmallIcon(com.moneyflow.R.mipmap.ic_launcher)
      .setContentTitle("신한카드")
      .setStyle(NotificationCompat.BigTextStyle().bigText(SAMPLE))
      .setTimeoutAfter(60_000)
      .setAutoCancel(true)
      // Tapping it opens the app (the test result is shown on the auto-capture screen).
      .setContentIntent(
        android.app.PendingIntent.getActivity(
          context, TEST_ID,
          Intent(context, com.moneyflow.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
          android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      .setContentText("머니플로우 자동 등록 시험이에요. 눌러서 결과를 확인하세요.")
      .setSubText("자동 등록 시험")
      .addExtras(android.os.Bundle().apply { putBoolean(NotificationText.TEST_MARKER, true) })
      .build()
    return try {
      NotificationManagerCompat.from(context).notify(TEST_ID, n)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  companion object {
    private const val TEST_CHANNEL = "capture_test"
    private const val TEST_ID = 4_242
    const val SAMPLE = "[신한카드 승인 안내]\n신한카드(1234) 승인\n▶승인금액 : 4,500원(일시불)\n▶가맹점명 : 시험 가맹점"
  }
}

/** One row of the "자동 등록 점검" checklist: ✅ / ⚠ with an optional fix button. */
@Composable
internal fun CheckRow(ok: Boolean, title: String, detail: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(
      if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = if (ok) "정상" else "확인 필요",
      tint = if (ok) AccentGreen else AccentOrange, modifier = Modifier.size(20.dp)
    )
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Gray900)
      detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Gray600) }
    }
    if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
  }
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean =
  context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

internal fun openAppDetails(context: Context) {
  runCatching {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}

internal fun openBatterySettings(context: Context) {
  val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
  runCatching { context.startActivity(direct) }.onFailure {
    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
  }
}

/** Step-by-step help for Android 13+ "제한된 설정" (apps installed from a file). */
@Composable
internal fun RestrictedSettingsHelp(onDismiss: () -> Unit, onOpenAppInfo: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("'제한된 설정' 풀기", fontWeight = FontWeight.Bold) },
    text = {
      Column {
        Text(
          "파일로 설치한 앱은 안드로이드가 알림 접근·문자 권한을 처음에 막아 둬요. 한 번만 이렇게 풀어 주세요.\n\n" +
            "1. 알림 접근 화면에서 머니플로우를 켜려고 하면 '제한된 설정' 안내가 떠요. 확인을 눌러요.\n" +
            "2. 아래 '앱 정보 열기' → 오른쪽 위 ⋮ → '제한된 설정 허용' → 휴대폰 잠금 확인.\n" +
            "3. 다시 이 화면으로 와서 '알림 접근 허용하러 가기'를 눌러 머니플로우를 켜요.\n\n" +
            "⋮ 메뉴에 '제한된 설정 허용'이 없으면 1번을 먼저 해 주세요.",
          style = MaterialTheme.typography.bodySmall
        )
      }
    },
    confirmButton = { TextButton(onClick = onOpenAppInfo) { Text("앱 정보 열기", fontWeight = FontWeight.Bold) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
  )
}

/** Recent messages the auto-capture looked at. */
@Composable
internal fun CaptureLogList(entries: List<CaptureLogEntry>, onClear: () -> Unit) {
  if (entries.isEmpty()) {
    Text(
      "아직 받은 결제 알림이 없어요. 카드를 한 번 쓰거나 '시험 알림 보내기'를 눌러 보세요.",
      style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(16.dp)
    )
    return
  }
  entries.take(20).forEach { e ->
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("capture_log_row")) {
      Icon(
        if (e.recorded) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null,
        tint = if (e.recorded) AccentGreen else AccentOrange, modifier = Modifier.size(16.dp).padding(top = 2.dp)
      )
      Spacer(Modifier.width(8.dp))
      Column {
        Text("${Formatters.formatDateTime(e.time)} · ${e.channel} · ${e.source}${if (e.sender.isNotBlank()) " · ${e.sender}" else ""}",
          style = MaterialTheme.typography.labelSmall, color = Gray600)
        Text(e.result, style = MaterialTheme.typography.bodySmall, color = Gray900)
        if (e.preview.isNotBlank()) Text(e.preview, style = MaterialTheme.typography.labelSmall, color = Gray600)
      }
    }
  }
  TextButton(onClick = onClear, modifier = Modifier.padding(horizontal = 8.dp)) { Text("기록 지우기") }
}
