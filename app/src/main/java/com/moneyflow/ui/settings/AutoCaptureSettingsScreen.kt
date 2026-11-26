package com.moneyflow.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.domain.capture.ParseResult
import com.moneyflow.domain.capture.PaymentKind
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentGreenLight
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentOrangeLight
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

private data class SamplePreset(val label: String, val text: String)

private val SamplePresets = listOf(
  SamplePreset("카드 승인", "[Web발신] 신한카드(1234)승인 홍*동 12,500원(일시불)11/21 14:32 스타벅스 누적1,234,560원"),
  SamplePreset("승인 취소", "[Web발신] 신한카드(1234)승인취소 홍*동 12,500원(일시불)11/21 15:10 스타벅스"),
  SamplePreset("토스 결제", "스타벅스에서 5,600원 결제했어요"),
  SamplePreset("은행 입금", "[Web발신] KB국민 11/25 09:00 123456**789 입금 3,200,000원 (주)회사급여 잔액4,120,500원")
)

private fun isNotificationAccessGranted(context: Context): Boolean =
  NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun isSmsPermissionGranted(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoCaptureSettingsScreen(
  onBack: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel(),
  diagnostics: CaptureDiagnosticsViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val logEntries by diagnostics.entries.collectAsStateWithLifecycle()
  var listenerConnected by remember { mutableStateOf(false) }
  var batteryOk by remember { mutableStateOf(true) }
  var restrictedHelp by rememberSaveable { mutableStateOf(false) }
  var realResult by remember { mutableStateOf<String?>(null) }
  val settings = state.settings
  val context = LocalContext.current

  var notificationAccess by remember { mutableStateOf(isNotificationAccessGranted(context)) }
  var smsGranted by remember { mutableStateOf(isSmsPermissionGranted(context)) }
  var smsDenied by rememberSaveable { mutableStateOf(false) }

  // Re-check permissions whenever the user comes back from system settings.
  LifecycleResumeEffect(Unit) {
    val before = notificationAccess
    notificationAccess = isNotificationAccessGranted(context)
    smsGranted = isSmsPermissionGranted(context)
    batteryOk = isIgnoringBatteryOptimizations(context)
    // Access was just granted: turn capture on too (it used to stay off and nothing was recorded).
    if (!before && notificationAccess) viewModel.setNotificationCapture(true)
    if (notificationAccess) com.moneyflow.service.FinanceNotificationListenerService.rebind(context)
    listenerConnected = com.moneyflow.service.FinanceNotificationListenerService.isConnected(context)
    onPauseOrDispose { }
  }

  val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    smsGranted = granted
    smsDenied = !granted
    if (granted) viewModel.setSmsCapture(true)
  }

  var sampleText by rememberSaveable { mutableStateOf("") }
  var result by remember { mutableStateOf<ParseResult?>(null) }

  SettingsScaffold(title = "결제 알림 자동 등록", onBack = onBack) {
    // Explanation
    Column(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardSurface).padding(12.dp)
    ) {
      Text(
        "카드·은행·간편결제 앱 알림, 카카오톡 알림톡(카드사·은행 채널), 문자를 읽어 지출과 수입을 자동으로 기록해요. 친구와의 대화는 읽지 않아요. " +
          "금액, 가맹점, 시간을 분석하고 카테고리도 자동으로 분류해요.",
        style = MaterialTheme.typography.bodyMedium,
        color = Gray700,
        modifier = Modifier.padding(horizontal = 8.dp)
      )
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          "알림 내용은 기기 밖으로 전송되지 않습니다.",
          style = MaterialTheme.typography.bodySmall,
          color = AccentGreen,
          fontWeight = FontWeight.SemiBold
        )
      }
    }

    SettingsSection("점검 (하나라도 ⚠면 기록되지 않을 수 있어요)") {
      CheckRow(
        ok = notificationAccess,
        title = "알림 접근 허용",
        detail = if (notificationAccess) null else "카드사 앱·카카오톡 알림톡·문자 앱 알림을 읽으려면 필요해요",
        action = if (notificationAccess) null else "허용하기",
        onAction = {
          try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: ActivityNotFoundException) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
      )
      if (!notificationAccess && android.os.Build.VERSION.SDK_INT >= 33) {
        CheckRow(ok = false, title = "켜지지 않나요? '제한된 설정'", detail = "파일로 설치한 앱은 한 번 풀어 줘야 해요", action = "방법 보기", onAction = { restrictedHelp = true })
      }
      CheckRow(
        ok = settings.notificationCaptureEnabled && notificationAccess,
        title = "알림 자동 등록 켜짐",
        action = if (settings.notificationCaptureEnabled || !notificationAccess) null else "켜기",
        onAction = { viewModel.setNotificationCapture(true) }
      )
      if (notificationAccess) {
        CheckRow(
          ok = listenerConnected,
          title = "알림 읽기 연결됨",
          detail = if (listenerConnected) null else "휴대폰이 알림 읽기를 멈췄어요. '다시 연결'을 누르거나 알림 접근을 껐다 켜 주세요",
          action = if (listenerConnected) null else "다시 연결",
          onAction = {
            com.moneyflow.service.FinanceNotificationListenerService.rebind(context)
            listenerConnected = com.moneyflow.service.FinanceNotificationListenerService.isConnected(context)
          }
        )
      }
      CheckRow(
        ok = batteryOk,
        title = "배터리 사용 '제한 없음'",
        detail = if (batteryOk) null else "절전 때문에 알림을 늦게 받거나 놓칠 수 있어요",
        action = if (batteryOk) null else "설정",
        onAction = { openBatterySettings(context) }
      )
      CheckRow(
        ok = settings.autoCreateCaptureAccounts || settings.autoCaptureDefaultAccountId != null,
        title = "기록할 계좌",
        detail = when {
          settings.autoCreateCaptureAccounts -> null
          settings.autoCaptureDefaultAccountId == null -> "카드사 이름과 맞는 계좌가 없으면 건너뛰어요. 아래 '처음 보는 카드·은행은 계좌 만들기'를 켜거나 기본 계좌를 골라 주세요"
          else -> null
        }
      )
    }

    SettingsSection("최근 받은 결제 알림") {
      CaptureLogList(logEntries, onClear = diagnostics::clear)
      val testState by diagnostics.testState.collectAsStateWithLifecycle()
      OutlinedButton(
        onClick = { diagnostics.runNotificationTest(notificationAccess, settings.notificationCaptureEnabled) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("capture_send_test")
      ) { Text("시험 알림 보내기") }
      testState?.let {
        Text(
          it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
          color = if (it.startsWith("✅")) com.moneyflow.ui.theme.AccentGreenText else if (it.startsWith("⚠")) com.moneyflow.ui.theme.AccentRedText else Gray900,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("capture_test_state")
        )
      }
    }

    SettingsSection("알림") {
      PermissionStatusRow(
        granted = notificationAccess,
        grantedText = "알림 접근 권한이 허용되어 있어요",
        deniedText = "알림 접근 권한이 필요해요"
      )
      OutlinedButton(
        onClick = {
          try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
          } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
          }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("open_notification_access")
      ) { Text(if (notificationAccess) "알림 접근 설정 열기" else "알림 접근 허용하러 가기") }
      SettingsSwitchItem(
        title = "알림 자동 등록",
        subtitle = if (notificationAccess) "결제 알림이 오면 바로 기록해요" else "먼저 알림 접근 권한을 허용해 주세요",
        icon = Icons.Filled.Notifications,
        checked = settings.notificationCaptureEnabled && notificationAccess,
        enabled = notificationAccess,
        onCheckedChange = viewModel::setNotificationCapture,
        modifier = Modifier.testTag("notification_capture_switch")
      )
    }

    SettingsSection("문자(SMS)") {
      SettingsSwitchItem(
        title = "문자(SMS) 자동 등록",
        subtitle = when {
          smsDenied && !smsGranted -> "문자 수신 권한이 거부되었어요. 아래 '앱 정보 열기'에서 권한 → SMS를 허용해 주세요."
          else -> "짧은 문자도 바로 받아요 (선택). 장문 카드 문자는 위 '알림 자동 등록'으로 읽어요"
        },
        icon = Icons.Filled.Sms,
        checked = settings.smsCaptureEnabled && smsGranted,
        onCheckedChange = { on ->
          when {
            !on -> viewModel.setSmsCapture(false)
            smsGranted -> viewModel.setSmsCapture(true)
            else -> smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
          }
        },
        modifier = Modifier.testTag("sms_capture_switch")
      )
      if (smsDenied && !smsGranted) {
        OutlinedButton(onClick = { openAppDetails(context) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("앱 정보 열기") }
      }
    }

    SettingsSection("기록할 계좌") {
      SettingsSwitchItem(
        title = "처음 보는 카드·은행은 계좌 만들기",
        subtitle = "삼성카드 결제 알림이 왔는데 삼성카드 계좌가 없으면 '삼성카드' 계좌를 만들어 기록해요 (잔액은 자산 탭에서 맞춰 주세요)",
        icon = Icons.Filled.AccountBalance,
        checked = settings.autoCreateCaptureAccounts,
        onCheckedChange = viewModel::setAutoCreateCaptureAccounts,
        modifier = Modifier.testTag("capture_auto_create_accounts")
      )
      Text(
        "기본 등록 계좌: 카드·은행 이름을 알 수 없는 알림(간편결제 등)을 이 계좌에 기록해요.",
        style = MaterialTheme.typography.bodySmall,
        color = Gray600,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
      )
      SettingsRadioItem(
        title = "선택 안 함",
        selected = settings.autoCaptureDefaultAccountId == null,
        onSelect = { viewModel.setDefaultCaptureAccount(null) }
      )
      state.accounts.forEach { account ->
        SettingsRadioItem(
          title = account.name,
          subtitle = account.bankName,
          selected = settings.autoCaptureDefaultAccountId == account.id,
          onSelect = { viewModel.setDefaultCaptureAccount(account.id) }
        )
      }
      if (state.accounts.isEmpty()) {
        SettingsItem(
          title = "등록된 계좌가 없어요",
          subtitle = "홈에서 계좌를 먼저 추가해 주세요",
          icon = Icons.Filled.AccountBalance,
          enabled = false
        )
      }
    }

    SettingsSection("분석 테스트") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
          "예시 문자를 고르거나 직접 붙여 넣어 어떻게 인식되는지 확인해 보세요. 테스트는 아무것도 저장하지 않아요.",
          style = MaterialTheme.typography.bodySmall,
          color = Gray600
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          SamplePresets.forEach { preset ->
            AssistChip(
              onClick = {
                sampleText = preset.text
                result = null
              },
              label = { Text(preset.label) }
            )
          }
        }
        OutlinedTextField(
          value = sampleText,
          onValueChange = {
            sampleText = it.take(500)
            result = null
          },
          label = { Text("알림/문자 내용") },
          minLines = 3,
          maxLines = 6,
          modifier = Modifier.fillMaxWidth().testTag("parser_test_input")
        )
        Spacer(Modifier.height(8.dp))
        Button(
          onClick = {
            result = viewModel.previewParse(sampleText)
            realResult = null
            diagnostics.tryText(sampleText) { realResult = com.moneyflow.domain.capture.CaptureLog.describe(it).second }
          },
          enabled = sampleText.isNotBlank(),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("parser_test_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
        ) { Text("분석해 보기", fontWeight = FontWeight.Bold) }
        result?.let {
          Spacer(Modifier.height(12.dp))
          ParseResultView(it)
        }
        realResult?.let {
          Text("실제로 받으면: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Gray900,
            modifier = Modifier.padding(top = 8.dp).testTag("parser_test_real"))
        }
      }
    }
    Spacer(Modifier.height(24.dp))
  }
  if (restrictedHelp) {
    RestrictedSettingsHelp(onDismiss = { restrictedHelp = false }, onOpenAppInfo = { restrictedHelp = false; openAppDetails(context) })
  }
}

@Composable
private fun PermissionStatusRow(granted: Boolean, grantedText: String, deniedText: String) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
      contentDescription = null,
      tint = if (granted) AccentGreen else AccentOrange,
      modifier = Modifier.size(20.dp)
    )
    Spacer(Modifier.width(8.dp))
    Text(
      if (granted) grantedText else deniedText,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = Gray900
    )
  }
}

@Composable
private fun ParseResultView(result: ParseResult) {
  when (result) {
    is ParseResult.Ignored -> Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AccentOrangeLight).padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Filled.Info, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Column {
        Text("결제 내역으로 인식하지 않았어요", fontWeight = FontWeight.Bold, color = Gray900, style = MaterialTheme.typography.bodyMedium)
        Text("이유: ${result.reason}", color = Gray700, style = MaterialTheme.typography.bodySmall)
      }
    }
    is ParseResult.Success -> {
      val p = result.payment
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AccentGreenLight).padding(12.dp).testTag("parser_test_result")
      ) {
        Text("인식 결과", fontWeight = FontWeight.Bold, color = Gray900, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        ResultLine(
          "종류",
          when (p.kind) {
            PaymentKind.PAYMENT -> "결제/입출금"
            PaymentKind.CANCELLATION -> "승인 취소 (원래 결제를 찾아 삭제)"
            PaymentKind.FOREIGN_CURRENCY -> "해외 결제 (원화 금액 미확정)"
          }
        )
        ResultLine("구분", p.type.title)
        ResultLine(
          "금액",
          if (p.kind == PaymentKind.FOREIGN_CURRENCY) p.foreignAmount ?: "-" else Formatters.formatCurrency(p.amount)
        )
        ResultLine("가맹점", p.merchant.ifBlank { "-" })
        ResultLine("카테고리", p.category)
        ResultLine(
          "결제 수단",
          listOfNotNull(p.provider, p.cardLast4?.let { "($it)" }).joinToString(" ").ifBlank { "-" }
        )
        p.installmentMonths?.let { ResultLine("할부", "${it}개월") }
        ResultLine("시간", p.paidAt?.let { Formatters.formatDateTime(it) } ?: "받은 시각으로 기록")
      }
    }
  }
}

@Composable
private fun ResultLine(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.width(72.dp))
    Text(
      value,
      style = MaterialTheme.typography.bodySmall,
      color = Gray900,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
  }
}
