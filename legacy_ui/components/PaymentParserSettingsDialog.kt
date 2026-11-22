package com.moneyflow.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.AccentTeal
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray300
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import com.moneyflow.util.PaymentNotificationParser
import com.moneyflow.util.ParsedTransactionResult

data class ParserSamplePreset(
  val label: String,
  val text: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentParserSettingsDialog(
  accounts: List<AccountEntity>,
  onDismiss: () -> Unit,
  onRegisterParsedPayment: (text: String, accountId: Long?) -> Unit
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  var hasSmsPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    )
  }

  var isNotificationListenerEnabled by remember {
    mutableStateOf(
      NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    )
  }

  val smsPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasSmsPermission = isGranted
  }

  // Sample presets for quick testing
  val samplePresets = remember {
    listOf(
      ParserSamplePreset(
        label = "신한카드 스타벅스",
        text = "[Web발신]\n신한카드 승인 신*화(1234)\n15,000원 일시불\n10/29 18:20 스타벅스강남점\n잔여한도 3,450,000원"
      ),
      ParserSamplePreset(
        label = "토스뱅크 배민",
        text = "[토스뱅크] 배달의민족에서 23,000원 결제되었어요."
      ),
      ParserSamplePreset(
        label = "카카오뱅크 GS25",
        text = "[Web발신]\n카카오뱅크 체크카드 승인\n4,500원\n10/29 19:10 GS25역삼점"
      ),
      ParserSamplePreset(
        label = "현대카드 택시",
        text = "[Web발신]\n현대카드 승인 12,800원 일시불\n10/29 23:45 카카오T 택시"
      ),
      ParserSamplePreset(
        label = "급여 입금",
        text = "[Web발신]\n토스뱅크 입금 3,200,000원\n10/25 10:00 (주)회사 급여"
      )
    )
  }

  var inputText by remember { mutableStateOf(samplePresets.first().text) }
  var selectedAccountId by remember { mutableStateOf<Long?>(accounts.firstOrNull { !it.isDebt }?.id) }
  var successFeedback by remember { mutableStateOf<String?>(null) }

  // Live parsed result preview
  val parsedPreview = remember(inputText) {
    PaymentNotificationParser.parse("결제 알림", inputText)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface)
        .testTag("payment_parser_settings_dialog"),
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(scrollState)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(TossBlue.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = TossBlue,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "결제 알림 & SMS 자동 등록",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
              Text(
                text = "개인용 실시간 결제 파싱 (사업자 인증 불필요)",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_parser_dialog_btn")
          ) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Explanation Card: Why this is the true individual approach
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Gray100.copy(alpha = 0.8f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = TossBlue,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "개인 사용자를 위한 안전한 가계부 방식",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "은행사 공식 오픈뱅킹 API는 금융당국 인가 및 사업자등록이 필요하지만, 개인 사용자는 안드로이드의 결제 알림 및 문자(SMS) 감지를 통해 카드 승인 즉시 100% 무료로 가계부에 자동 등록할 수 있습니다.",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 12.sp,
              color = Gray700,
              lineHeight = 17.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: Android System Permissions Status
        Text(
          text = "안드로이드 실시간 수신 권한 설정",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = Gray900
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Notification Listener Permission Row
        PermissionStatusCard(
          title = "카드사/은행 앱 푸시 알림 감지",
          description = "신한, 국민, 토스, 카카오뱅크 등의 결제 푸시를 실시간 감지합니다.",
          icon = Icons.Default.NotificationsActive,
          isGranted = isNotificationListenerEnabled,
          buttonText = if (isNotificationListenerEnabled) "연결됨" else "알림 접근 권한 켜기",
          onClickAction = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            context.startActivity(intent)
          },
          testTag = "perm_notification_listener_btn"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. SMS Permission Row
        PermissionStatusCard(
          title = "카드 결제 문자(SMS) 감지",
          description = "1588 번호 등 금융사 결제 승인 SMS를 감지하여 자동 파싱합니다.",
          icon = Icons.Default.Sms,
          isGranted = hasSmsPermission,
          buttonText = if (hasSmsPermission) "허용됨" else "문자 권한 허용",
          onClickAction = {
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
          },
          testTag = "perm_sms_btn"
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Section 2: One-click Presets & Testing Simulator
        Text(
          text = "결제 알림 시뮬레이터 (원클릭 테스트)",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = Gray900
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "아래 금융사 샘플을 누르거나 문자를 직접 입력하여 파싱 엔진을 즉시 테스트할 수 있습니다.",
          style = MaterialTheme.typography.bodySmall,
          color = Gray600
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Chips
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          samplePresets.forEach { preset ->
            val isSelected = inputText == preset.text
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) TossBlue else Gray100)
                .clickable {
                  inputText = preset.text
                  successFeedback = null
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White else Gray800,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Text Input Area
        OutlinedTextField(
          value = inputText,
          onValueChange = {
            inputText = it
            successFeedback = null
          },
          label = { Text("결제 문자 / 푸시 알림 텍스트") },
          placeholder = { Text("카드 결제 문자나 알림을 여기에 붙여넣으세요...") },
          modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .testTag("parser_input_field"),
          shape = RoundedCornerShape(12.dp),
          maxLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Live Parsing Result Preview Card
        Text(
          text = "파싱 및 카테고리 자동 분류 결과",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = Gray800
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (parsedPreview.isSuccess && parsedPreview.amount > 0L) {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = TossBlue.copy(alpha = 0.08f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, TossBlue.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(TossBlue)
                      .padding(horizontal = 6.dp, vertical = 3.dp)
                  ) {
                    Text(
                      text = parsedPreview.providerName,
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                      fontWeight = FontWeight.Bold
                    )
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = parsedPreview.merchantOrSender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Gray900
                  )
                }

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (parsedPreview.type == TransactionType.INCOME) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Text(
                    text = if (parsedPreview.type == TransactionType.INCOME) "수입" else "지출",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (parsedPreview.type == TransactionType.INCOME) AccentGreen else AccentRed,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
              ) {
                Column {
                  Text(
                    text = "자동 분류 카테고리: ${parsedPreview.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray700,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    text = "가맹점 키워드 스마트 매칭 완료",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = Gray500
                  )
                }

                Text(
                  text = Formatters.formatCurrency(parsedPreview.amount),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Black,
                  color = Gray900
                )
              }
            }
          }
        } else {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Gray100)
              .padding(14.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = parsedPreview.reason ?: "유효한 결제 문자를 입력하면 실시간으로 파싱됩니다.",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Success Feedback Banner
        if (successFeedback != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(AccentGreen.copy(alpha = 0.12f))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = successFeedback!!,
              style = MaterialTheme.typography.labelMedium,
              color = AccentGreen,
              fontWeight = FontWeight.Bold
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        // Action Button: Save to Ledger
        Button(
          onClick = {
            if (parsedPreview.isSuccess && parsedPreview.amount > 0L) {
              onRegisterParsedPayment(inputText, selectedAccountId)
              successFeedback = "'${parsedPreview.merchantOrSender}' (${Formatters.formatCurrency(parsedPreview.amount)}) 가계부 자동 등록 완료!"
            }
          },
          enabled = parsedPreview.isSuccess && parsedPreview.amount > 0L,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("register_parsed_payment_btn"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue)
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "이 결제 내역 가계부에 바로 등록하기",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun PermissionStatusCard(
  title: String,
  description: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isGranted: Boolean,
  buttonText: String,
  onClickAction: () -> Unit,
  testTag: String
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, if (isGranted) AccentGreen.copy(alpha = 0.4f) else Gray200),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isGranted) AccentGreen.copy(alpha = 0.12f) else Gray100),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isGranted) Icons.Default.Check else icon,
            contentDescription = null,
            tint = if (isGranted) AccentGreen else Gray600,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = Gray600
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (isGranted) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AccentGreen.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = buttonText,
            style = MaterialTheme.typography.labelSmall,
            color = AccentGreen,
            fontWeight = FontWeight.Bold
          )
        }
      } else {
        Button(
          onClick = onClickAction,
          modifier = Modifier
            .height(36.dp)
            .testTag(testTag),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
          Text(text = buttonText, style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
