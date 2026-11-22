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
import androidx.hilt.navigation.compose.hiltViewModel
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
  viewModel: SettingsViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val settings = state.settings
  val context = LocalContext.current

  var notificationAccess by remember { mutableStateOf(isNotificationAccessGranted(context)) }
  var smsGranted by remember { mutableStateOf(isSmsPermissionGranted(context)) }
  var smsDenied by rememberSaveable { mutableStateOf(false) }

  // Re-check permissions whenever the user comes back from system settings.
  LifecycleResumeEffect(Unit) {
    notificationAccess = isNotificationAccessGranted(context)
    smsGranted = isSmsPermissionGranted(context)
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
        "카드·은행·간편결제 앱의 결제 알림이나 문자를 읽어 지출과 수입을 자동으로 기록해요. " +
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
          smsDenied && !smsGranted -> "문자 수신 권한이 거부되었어요. 앱 설정에서 허용해 주세요."
          else -> "카드사·은행 문자로 받은 결제 내역을 기록해요"
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
    }

    SettingsSection("기본 등록 계좌") {
      Text(
        "알림의 카드·은행 이름과 맞는 계좌가 없을 때 이 계좌에 기록해요.",
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
          onClick = { result = viewModel.previewParse(sampleText) },
          enabled = sampleText.isNotBlank(),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("parser_test_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
        ) { Text("분석해 보기", fontWeight = FontWeight.Bold) }
        result?.let {
          Spacer(Modifier.height(12.dp))
          ParseResultView(it)
        }
      }
    }
    Spacer(Modifier.height(24.dp))
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
