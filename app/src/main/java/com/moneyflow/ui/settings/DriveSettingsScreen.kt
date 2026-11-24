package com.moneyflow.ui.settings

import android.content.ClipData
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.data.repository.DriveCheck
import com.moneyflow.data.repository.DriveRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.drive.DriveFile
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DriveSettingsViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val drive: DriveRepository,
  private val settingsRepo: SettingsRepository,
  private val messages: UserMessageBus,
  private val clock: com.moneyflow.domain.AppClock
) : ViewModel() {
  val settings: StateFlow<AppSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()
  private val _folders = MutableStateFlow<List<DriveFile>?>(null)
  /** Folders to choose from (dialog open while non-null). */
  val folders: StateFlow<List<DriveFile>?> = _folders.asStateFlow()
  private val _checks = MutableStateFlow<List<DriveCheck>>(emptyList())
  val checks: StateFlow<List<DriveCheck>> = _checks.asStateFlow()
  private val _errors = Channel<String>(Channel.BUFFERED)
  val errors = _errors.receiveAsFlow()

  private fun run(block: suspend () -> Unit) = viewModelScope.launch {
    if (_busy.value) return@launch
    _busy.value = true
    try { block() } catch (e: Exception) { _errors.send(e.message ?: "처리하지 못했어요.") } finally { _busy.value = false }
  }

  fun importKey(uri: Uri) = run {
    val json = withContext(Dispatchers.IO) {
      context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size < 64_000 }?.toString(Charsets.UTF_8)
        ?: error("파일을 열 수 없어요.")
    }
    val email = drive.importKey(json)
    _checks.value = emptyList()
    messages.show("봇 열쇠를 불러왔어요: $email")
  }

  fun findFolders() = run {
    val list = drive.sharedFolders()
    if (list.isEmpty()) error("봇에게 공유된 폴더가 없어요. 드라이브에서 폴더를 봇 이메일에 '편집자'로 공유한 뒤 다시 눌러 주세요. (반영까지 1~2분 걸릴 수 있어요)")
    _folders.value = list
  }

  fun chooseFolder(f: DriveFile) = run {
    drive.chooseFolder(f)
    _folders.value = null
    messages.show("'${f.name}' 폴더를 골랐어요.")
  }

  fun dismissFolders() { _folders.value = null }

  /** Writes the test file the user just created with the system "save" screen (in the Drive folder). */
  fun writeTestFile(uri: Uri) = run {
    val name = withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(uri, "wt")?.use { it.write("moneyflow drive test ${clock.nowMillis()}".toByteArray()) }
        ?: error("파일을 저장할 수 없어요.")
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: DriveRepository.TEST_FILE_NAME
    }
    settingsRepo.setDriveTestFile(name)
    messages.show("시험 파일 '$name'을(를) 만들었어요. 1~2분 뒤 '연결 시험'을 눌러 주세요.")
  }

  fun check() = run { _checks.value = drive.runCheck() }

  fun disconnect() = run {
    drive.disconnect()
    _checks.value = emptyList()
    messages.show("드라이브 연결을 지웠어요.")
  }
}

/** Settings → 구글 드라이브 연결 (시험): bot key, shared folder, test file, connection test. */
@Composable
fun DriveSettingsScreen(onBack: () -> Unit, viewModel: DriveSettingsViewModel = hiltViewModel()) {
  val s by viewModel.settings.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val checks by viewModel.checks.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var error by remember { mutableStateOf<String?>(null) }
  var confirmDisconnect by remember { mutableStateOf(false) }

  val openKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importKey) }
  val createTest = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(viewModel::writeTestFile) }
  LaunchedEffect(viewModel) { viewModel.errors.collect { error = it } }

  val settings = s ?: return
  val hasKey = settings.driveKeyEncrypted != null
  SettingsScaffold(title = "구글 드라이브 연결 (시험)", onBack = onBack) {
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TossBlue)
    Text(
      "가족 자동 합산과 드라이브 자동 백업을 위한 연결 시험이에요. 안내서(구글 드라이브 봇 계정 만들기)대로 봇과 폴더를 만든 뒤 차례로 눌러 주세요.",
      style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(horizontal = 8.dp)
    )
    SettingsSection("연결") {
      SettingsItem(
        title = "① 열쇠 파일 불러오기",
        subtitle = settings.driveBotEmail?.let { "봇: $it" } ?: "구글 클라우드에서 받은 JSON 파일",
        icon = Icons.Filled.Key,
        enabled = !busy,
        showChevron = false,
        onClick = { openKey.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        modifier = Modifier.testTag("drive_import_key")
      )
      settings.driveBotEmail?.let { email ->
        SettingsItem(
          title = "봇 이메일 복사",
          subtitle = "드라이브 폴더를 이 이메일에 '편집자'로 공유하세요",
          icon = Icons.Filled.ContentCopy,
          showChevron = false,
          onClick = {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("bot", email))
          }
        )
      }
      SettingsItem(
        title = "② 공유된 폴더 찾기",
        subtitle = settings.driveFolderName?.let { "폴더: $it" } ?: "봇에게 공유한 '머니플로우 가족' 폴더 고르기",
        icon = Icons.Filled.Folder,
        enabled = !busy && hasKey,
        showChevron = false,
        onClick = viewModel::findFolders,
        modifier = Modifier.testTag("drive_find_folders")
      )
      SettingsItem(
        title = "③ 시험 파일 만들기",
        subtitle = settings.driveTestFileName?.let { "시험 파일: $it" } ?: "저장 화면에서 드라이브 → 같은 폴더를 골라 저장",
        icon = Icons.Filled.NoteAdd,
        enabled = !busy && settings.driveFolderId != null,
        showChevron = false,
        onClick = { createTest.launch(DriveRepository.TEST_FILE_NAME) },
        modifier = Modifier.testTag("drive_create_test")
      )
      SettingsItem(
        title = "④ 연결 시험",
        subtitle = "봇 로그인 → 폴더 보기 → 시험 파일 읽기 → 봇으로 덮어쓰기",
        icon = Icons.Filled.PlayArrow,
        enabled = !busy && hasKey,
        showChevron = false,
        onClick = viewModel::check,
        modifier = Modifier.testTag("drive_check")
      )
    }
    if (checks.isNotEmpty()) {
      SettingsSection("시험 결과") {
        Column(Modifier.padding(16.dp).testTag("drive_check_results")) {
          checks.forEach { c ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
              Text(if (c.ok) "✓" else "✗", color = if (c.ok) AccentGreen else AccentRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
              Column {
                Text(c.title, fontWeight = FontWeight.SemiBold, color = Gray900)
                Text(c.detail, style = MaterialTheme.typography.bodySmall, color = Gray600)
              }
            }
          }
          if (checks.size == 4 && checks.all { it.ok }) {
            Text("연결 성공! 이 화면을 캡처해서 보내 주세요.", color = AccentGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
          }
        }
      }
    }
    if (hasKey) {
      SettingsSection(null) {
        SettingsItem(
          title = "연결 지우기",
          subtitle = "이 휴대폰에 저장된 봇 열쇠와 폴더 선택을 지워요",
          icon = Icons.Filled.CloudDone,
          iconTint = AccentRed,
          showChevron = false,
          onClick = { confirmDisconnect = true }
        )
      }
    }
  }

  folders?.let { list ->
    AlertDialog(
      onDismissRequest = viewModel::dismissFolders,
      title = { Text("공유된 폴더", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          list.forEach { f ->
            TextButton(onClick = { viewModel.chooseFolder(f) }, modifier = Modifier.fillMaxWidth()) {
              Text(f.name + (f.ownerName?.let { " · $it" } ?: ""), modifier = Modifier.fillMaxWidth())
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = viewModel::dismissFolders) { Text("닫기") } }
    )
  }
  if (confirmDisconnect) {
    ConfirmDialog(
      title = "드라이브 연결을 지울까요?",
      message = "이 휴대폰의 봇 열쇠와 폴더 선택만 지워져요. 드라이브의 파일은 그대로예요.",
      confirmLabel = "지우기",
      onConfirm = { confirmDisconnect = false; viewModel.disconnect() },
      onDismiss = { confirmDisconnect = false }
    )
  }
  error?.let { msg ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("알림", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }
    )
  }
}
