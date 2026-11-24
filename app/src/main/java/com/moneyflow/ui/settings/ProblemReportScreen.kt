package com.moneyflow.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.util.ProblemLog
import java.io.File

/** Settings → 문제 보고: the error log kept on this phone, and sending it. */
@Composable
fun ProblemReportScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var log by remember { mutableStateOf(ProblemLog.read()) }
  SettingsScaffold(title = "문제 보고", onBack = onBack) {
    Text(
      "앱이 멈추거나 백그라운드 작업이 실패하면 오류 위치를 이 휴대폰에만 기록해 둬요. 문제가 생기면 '보고서 보내기'로 카카오톡 등으로 보내 주세요. 거래 내역은 들어가지 않아요.",
      style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(horizontal = 8.dp)
    )
    SettingsSection(null) {
      SettingsItem(
        title = "보고서 보내기",
        subtitle = "앱 버전, 기기 이름, 오류 기록을 텍스트 파일로 보내요",
        icon = Icons.AutoMirrored.Filled.Send,
        showChevron = false,
        onClick = {
          val dir = File(context.cacheDir, "exports").apply { mkdirs() }
          val file = File(dir, "moneyflow-report.txt").apply { writeText(ProblemLog.report()) }
          val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
          val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "머니플로우 문제 보고")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          context.startActivity(Intent.createChooser(send, "문제 보고 보내기"))
        },
        modifier = Modifier.testTag("report_send")
      )
      SettingsItem(
        title = "오류 기록 지우기",
        subtitle = "문제가 해결되었으면 지워 주세요",
        icon = Icons.Filled.Delete,
        iconTint = AccentRed,
        showChevron = false,
        enabled = log.isNotEmpty(),
        onClick = { ProblemLog.clear(); log = "" }
      )
    }
    SettingsSection("오류 기록") {
      Column(Modifier.padding(16.dp)) {
        if (log.isEmpty()) {
          Text("기록된 오류가 없어요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
        } else {
          SelectionContainer {
            Text(log.takeLast(8_000), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.testTag("report_log"))
          }
        }
      }
    }
  }
}
