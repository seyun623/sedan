package com.moneyflow.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.BuildConfig
import com.moneyflow.data.repository.ThemeMode
import com.moneyflow.util.CsvExportHelper
import com.moneyflow.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onOpenSecurity: () -> Unit,
  onOpenAutoCapture: () -> Unit,
  onOpenBackup: () -> Unit = {},
  onOpenAi: () -> Unit = {},
  onOpenCardImport: () -> Unit = {},
  onOpenReleaseHistory: () -> Unit = {},
  viewModel: SettingsViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val settings = state.settings
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var exporting by remember { mutableStateOf(false) }

  SettingsScaffold(title = "설정", onBack = onBack) {
    SettingsSection("보안") {
      SettingsItem(
        title = "앱 잠금 및 보안",
        subtitle = if (settings.isLockEnabled) {
          if (settings.biometricEnabled) "PIN · 생체 인증 사용 중" else "PIN 잠금 사용 중"
        } else "잠금이 꺼져 있어요",
        icon = Icons.Filled.Lock,
        onClick = onOpenSecurity,
        modifier = Modifier.testTag("settings_security")
      )
    }

    if (com.moneyflow.BuildConfig.AUTO_CAPTURE_AVAILABLE) SettingsSection("결제 알림 자동 등록") {
      val on = listOfNotNull(
        "알림".takeIf { settings.notificationCaptureEnabled },
        "문자".takeIf { settings.smsCaptureEnabled }
      )
      SettingsItem(
        title = "카드·은행 알림으로 자동 기록",
        subtitle = if (on.isEmpty()) "꺼져 있어요" else "${on.joinToString(", ")} 자동 등록 사용 중",
        icon = Icons.Filled.NotificationsActive,
        onClick = onOpenAutoCapture,
        modifier = Modifier.testTag("settings_auto_capture")
      )
    }

    SettingsSection("알림") {
      val context = androidx.compose.ui.platform.LocalContext.current
      val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
      ) { granted -> viewModel.setCardAlertsEnabled(granted) }
      SettingsSwitchItem(
        title = "카드 실적·혜택 알림",
        subtitle = "실적 기간이 5일 남았는데 모자랄 때, 혜택 한도를 다 썼을 때 알려 드려요",
        icon = Icons.Filled.NotificationsActive,
        checked = settings.cardAlertsEnabled,
        onCheckedChange = { on ->
          if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
          ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
          } else {
            viewModel.setCardAlertsEnabled(on)
          }
        },
        modifier = Modifier.testTag("settings_card_alerts")
      )
    }

    SettingsSection("AI") {
      SettingsItem(
        title = "AI 카테고리 추천",
        subtitle = when {
          settings.isAiReady -> "사용 중 · ${settings.aiModel}"
          settings.hasAiKey -> "API 키 등록됨 · 꺼져 있어요"
          else -> "내 Gemini API 키를 등록해서 사용해요"
        },
        icon = Icons.Filled.AutoAwesome,
        onClick = onOpenAi,
        modifier = Modifier.testTag("settings_ai")
      )
    }

    SettingsSection("화면") {
      ThemeMode.entries.forEach { mode ->
        SettingsRadioItem(
          title = when (mode) {
            ThemeMode.SYSTEM -> "시스템 설정 따르기"
            ThemeMode.LIGHT -> "라이트"
            ThemeMode.DARK -> "다크"
          },
          selected = settings.themeMode == mode,
          onSelect = { viewModel.setThemeMode(mode) }
        )
      }
    }

    SettingsSection("데이터") {
      SettingsItem(
        title = "카드사에서 가져오기",
        subtitle = "카드사 홈페이지에 로그인해 이용내역·실적을 불러와요",
        icon = Icons.Filled.CreditCard,
        onClick = onOpenCardImport,
        modifier = Modifier.testTag("settings_card_import")
      )
      val lastBackup = state.settings.lastBackupAt
      SettingsItem(
        title = "백업 및 복원",
        subtitle = if (lastBackup > 0) "마지막 백업 ${Formatters.formatFullDate(lastBackup)}" else "아직 백업한 적이 없어요",
        icon = Icons.Filled.Backup,
        onClick = onOpenBackup,
        modifier = Modifier.testTag("settings_backup")
      )
      SettingsItem(
        title = "전체 거래 CSV 내보내기",
        subtitle = if (exporting) "내보내는 중…" else "엑셀에서 열 수 있는 파일로 공유해요",
        icon = Icons.Filled.FileDownload,
        enabled = !exporting,
        showChevron = false,
        onClick = {
          exporting = true
          scope.launch {
            try {
              val txs = viewModel.allTransactions()
              CsvExportHelper.exportTransactionsToCsv(context, txs, "전체")
            } finally {
              exporting = false
            }
          }
        },
        modifier = Modifier.testTag("settings_export_csv")
      )
      if (state.canLoadSampleData) {
        SettingsItem(
          title = "샘플 데이터 불러오기",
          subtitle = "예시 계좌와 거래로 앱을 둘러볼 수 있어요",
          icon = Icons.Filled.Science,
          showChevron = false,
          onClick = viewModel::loadSampleData,
          modifier = Modifier.testTag("settings_load_sample")
        )
      }
    }

    SettingsSection("앱 정보") {
      SettingsItem(
        title = "버전",
        icon = Icons.Filled.Info,
        trailing = BuildConfig.VERSION_NAME,
        showChevron = false
      )
      SettingsItem(
        title = "업데이트 내역",
        subtitle = "버전마다 바뀐 점을 볼 수 있어요",
        onClick = onOpenReleaseHistory,
        modifier = Modifier.testTag("settings_release_history")
      )
      SettingsItem(
        title = "데이터 보관",
        subtitle = "거래와 계좌 기록은 이 기기 안에 암호화되어 저장돼요. 휴대폰을 바꿀 때는 백업 파일을 이용하세요. AI 추천을 켜면 메모만 선택한 AI 서비스로 전송돼요.",
        showChevron = false
      )
    }
    Spacer(Modifier.height(24.dp))
  }
}
