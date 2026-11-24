package com.moneyflow.ui.cardimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.ai.AiService
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.capture.PaymentMessageParser
import com.moneyflow.domain.cardimport.AiStatementReader
import com.moneyflow.domain.cardimport.CardImportSession
import com.moneyflow.domain.cardimport.CardSite
import com.moneyflow.domain.cardimport.TableFileReader
import com.moneyflow.domain.cardimport.TableStatementReader
import com.moneyflow.ui.settings.SettingsItem
import com.moneyflow.ui.settings.SettingsScaffold
import com.moneyflow.ui.settings.SettingsSection
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.TossBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FileImportState(
  val busy: Boolean = false,
  val error: String? = null,
  /** The table could not be read by column names; AI can try (needs consent). */
  val aiFallback: Boolean = false,
  val aiLabel: String? = null
)

@HiltViewModel
class FileImportViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val session: CardImportSession,
  private val ai: AiService,
  private val aiReader: AiStatementReader,
  private val clock: AppClock
) : ViewModel() {
  private val _state = MutableStateFlow(FileImportState())
  val state: StateFlow<FileImportState> = _state.asStateFlow()
  private val _openPreview = Channel<Unit>(Channel.BUFFERED)
  val openPreview = _openPreview.receiveAsFlow()

  /** Kept for the AI fallback only (memory). */
  private var lastTable: Pair<String, List<List<String>>>? = null

  fun open(uri: Uri) = viewModelScope.launch {
    _state.value = FileImportState(busy = true)
    try {
      val (name, bytes) = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
          ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "파일"
        name to (context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("파일을 열 수 없어요."))
      }
      val rows = withContext(Dispatchers.Default) { TableFileReader.read(bytes, name) }
      val extraction = withContext(Dispatchers.Default) { TableStatementReader(clock.today()).read(rows, "파일 읽기") }
      if (extraction.items.isEmpty()) {
        lastTable = name to rows
        val label = if (ai.isReady()) ai.providerLabel() else null
        _state.value = FileImportState(
          error = "표에서 날짜·가맹점·금액 칸을 찾지 못했어요." + if (label != null) " AI로 읽어 볼까요?" else " 카드사·은행에서 받은 원래 파일인지 확인해 주세요.",
          aiFallback = label != null, aiLabel = label
        )
        return@launch
      }
      session.put(siteFor(name, rows), extraction)
      _state.value = FileImportState()
      _openPreview.send(Unit)
    } catch (e: Exception) {
      _state.value = FileImportState(error = com.moneyflow.util.UserErrors.message(e, "파일을 읽지 못했어요."))
    }
  }

  fun readWithAi() = viewModelScope.launch {
    val (name, rows) = lastTable ?: return@launch
    _state.value = FileImportState(busy = true)
    try {
      val text = rows.take(400).joinToString("\n") { it.joinToString("\t") }
      val extraction = aiReader.read(text, clock.today())
      if (extraction.isEmpty) {
        _state.value = FileImportState(error = "AI도 이 파일에서 결제 내역을 찾지 못했어요.")
        return@launch
      }
      session.put(siteFor(name, rows), extraction)
      lastTable = null
      _state.value = FileImportState()
      _openPreview.send(Unit)
    } catch (e: Exception) {
      _state.value = FileImportState(error = com.moneyflow.util.UserErrors.message(e, "AI가 읽지 못했어요."))
    }
  }

  fun dismissError() { _state.value = _state.value.copy(error = null, aiFallback = false) }

  /** The card company / bank named in the file name or its first rows (picks the account). */
  private fun siteFor(name: String, rows: List<List<String>>): CardSite {
    val text = name + " " + rows.take(8).joinToString(" ") { it.joinToString(" ") }
    val provider = PaymentMessageParser(clock.zone).detectProvider(text).orEmpty()
    return CardSite("file", if (provider.isNotEmpty()) "$provider 파일" else "파일", "", provider)
  }
}

/** 엑셀·CSV 파일에서 가져오기: card company / bank "이용내역 다운로드" files. */
@Composable
fun FileImportScreen(onBack: () -> Unit, onOpenPreview: () -> Unit, viewModel: FileImportViewModel = hiltViewModel()) {
  val s by viewModel.state.collectAsStateWithLifecycle()
  val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::open) }
  LaunchedEffect(viewModel) { viewModel.openPreview.collect { onOpenPreview() } }

  SettingsScaffold(title = "엑셀·CSV 파일에서 가져오기", onBack = onBack) {
    if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TossBlue)
    SettingsSection(null) {
      SettingsItem(
        title = "파일 고르기",
        subtitle = "xlsx, csv, 카드사 홈페이지의 xls(웹 표) 파일",
        icon = Icons.Filled.UploadFile,
        enabled = !s.busy,
        showChevron = false,
        onClick = {
          pick.launch(arrayOf(
            "text/csv", "text/comma-separated-values", "text/plain", "text/html", "text/tab-separated-values",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"
          ))
        },
        modifier = Modifier.testTag("file_import_pick")
      )
    }
    SettingsSection("이렇게 받아요") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(
          "카드사·은행 홈페이지(PC 화면)의 이용내역·거래내역에서 '엑셀 다운로드' 또는 '파일 저장'을 눌러 받아요.",
          "받은 파일을 휴대폰으로 옮기거나 구글 드라이브에 올린 뒤 여기서 골라요.",
          "이용일·가맹점(내용)·금액 칸을 찾아 읽어요. 은행 파일은 입금은 수입, 출금은 지출로 넣어요.",
          "미리보기에서 넣을 계좌와 내역을 확인한 뒤 추가해요. 이미 있는 내역은 빼 둬요.",
          "옛 .xls(엑셀 97-2003) 파일은 엑셀이나 구글 시트에서 xlsx 또는 CSV로 저장한 뒤 골라 주세요."
        ).forEach {
          Row(Modifier.padding(vertical = 3.dp)) {
            Text("• ", style = MaterialTheme.typography.bodySmall, color = Gray600)
            Text(it, style = MaterialTheme.typography.bodySmall, color = Gray600)
          }
        }
      }
    }
  }

  s.error?.let { msg ->
    AlertDialog(
      onDismissRequest = viewModel::dismissError,
      title = { Text("파일을 읽지 못했어요", fontWeight = FontWeight.Bold) },
      text = {
        Text(if (s.aiFallback) "$msg\n\n파일의 표 내용(가맹점, 금액, 날짜 등)이 ${s.aiLabel}(으)로 전송돼요." else msg)
      },
      confirmButton = {
        if (s.aiFallback) TextButton(onClick = viewModel::readWithAi) { Text("AI로 읽기", fontWeight = FontWeight.Bold) }
        else TextButton(onClick = viewModel::dismissError) { Text("확인") }
      },
      dismissButton = { if (s.aiFallback) TextButton(onClick = viewModel::dismissError) { Text("취소") } }
    )
  }
}
