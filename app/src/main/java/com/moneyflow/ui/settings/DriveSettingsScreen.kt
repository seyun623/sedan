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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
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
  private val familySync: com.moneyflow.data.repository.FamilySyncRepository,
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
    try { block() } catch (e: Exception) { _errors.send(com.moneyflow.util.UserErrors.message(e)) } finally { _busy.value = false }
  }

  fun importKey(uri: Uri) = run {
    val json = withContext(Dispatchers.IO) {
      context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size < 64_000 }?.toString(Charsets.UTF_8)
        ?: error("파일을 열 수 없어요.")
    }
    val email = drive.importKey(json)
    _checks.value = emptyList()
    messages.show("연결 파일을 불러왔어요.")
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

  fun setupFamily(name: String, password: String) = run {
    familySync.setup(name, password)
    messages.show("이름과 가족 비밀번호를 저장했어요.")
  }

  /** Writes my family file into the document the user just created in the Drive folder. */
  fun writeFamilyFile(uri: Uri) = run {
    val generation = familySync.dataGeneration()
    val bytes = familySync.familyFileBytes()
    val name = withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("파일을 저장할 수 없어요.")
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } ?: error("저장한 파일 이름을 알 수 없어요. 드라이브에 저장했는지 확인해 주세요.")
    familySync.fileCreated(name, generation)
    com.moneyflow.work.FamilySyncWorker.runSoon(context, delaySeconds = 120)
    messages.show("가족 파일 '$name'을(를) 만들었어요. 이제 자동으로 합산돼요.")
  }

  fun syncNow() = run {
    val r = familySync.sync(forceUpload = true) ?: error("가족 자동 합산이 아직 설정되지 않았어요.")
    messages.show(buildString {
      append(if (r.uploaded) "내 기록을 올렸어요." else "내 파일을 아직 찾지 못했어요.")
      append(if (r.loaded.isEmpty()) " 새로 바뀐 가족 기록은 없어요." else " ${r.loaded.joinToString(", ")}님의 기록을 받았어요.")
    })
  }

  fun turnOffFamily() = run {
    familySync.turnOff()
    messages.show("가족 자동 합산을 껐어요. 받은 가족 기록과 드라이브 파일은 그대로예요.")
  }

  fun disconnect() = run {
    drive.disconnect()
    _checks.value = emptyList()
    messages.show("드라이브 연결을 지웠어요.")
  }
}

/** Settings → 구글 드라이브 · 가족 합산: bot key, shared folder, family auto-sync, connection test. */
@Composable
fun DriveSettingsScreen(onBack: () -> Unit, viewModel: DriveSettingsViewModel = hiltViewModel()) {
  val s by viewModel.settings.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val checks by viewModel.checks.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

  val openKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importKey) }
  var familyDialog by rememberSaveable { mutableStateOf(false) }
  var confirmFamilyOff by rememberSaveable { mutableStateOf(false) }
  val createFamily = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(viewModel::writeFamilyFile) }
  val createTest = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(viewModel::writeTestFile) }
  LaunchedEffect(viewModel) { viewModel.errors.collect { error = it } }

  val settings = s ?: return
  val hasKey = settings.driveKeyEncrypted != null
  SettingsScaffold(title = "구글 드라이브 · 가족 합산", onBack = onBack) {
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TossBlue)
    Text(
      "①~④를 차례로 한 번만 하면, 그다음부터 가족 기록이 자동으로 모여요. 연결 파일은 세윤님이 카카오톡으로 보내 준 .json 파일이에요. 드라이브 자동 백업은 설정 → 백업 및 복원에서 켤 수 있어요.",
      style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(horizontal = 8.dp)
    )
    SettingsSection("연결") {
      SettingsItem(
        title = "① 연결 파일 불러오기",
        subtitle = if (settings.driveBotEmail != null) "불러왔어요" else "카카오톡 등으로 받은 연결 파일(.json) 고르기",
        icon = Icons.Filled.Key,
        enabled = !busy,
        showChevron = false,
        onClick = { openKey.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        modifier = Modifier.testTag("drive_import_key")
      )
      settings.driveBotEmail?.let { email ->
        SettingsItem(
          title = "봇 이메일 복사 (관리자용)",
          subtitle = "폴더를 처음 만드는 사람만: 이 이메일에 폴더를 '편집자'로 공유해요",
          icon = Icons.Filled.ContentCopy,
          showChevron = false,
          onClick = {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("bot", email))
          }
        )
      }
      SettingsItem(
        title = "② 가족 폴더 고르기",
        subtitle = settings.driveFolderName?.let { "폴더: $it" } ?: "목록에서 '머니플로우 가족' 고르기",
        icon = Icons.Filled.Folder,
        enabled = !busy && hasKey,
        showChevron = false,
        onClick = viewModel::findFolders,
        modifier = Modifier.testTag("drive_find_folders")
      )
    }
    SettingsSection("가족 자동 합산") {
      val ready = hasKey && settings.driveFolderId != null
      SettingsItem(
        title = "③ 내 이름 · 가족 비밀번호",
        subtitle = if (settings.familySyncPasswordEncrypted != null) "${settings.familySyncName ?: "나"} · 비밀번호 저장됨"
          else "가족 모두 같은 비밀번호를 정해 주세요",
        icon = Icons.Filled.Key,
        enabled = !busy && ready,
        showChevron = false,
        onClick = { familyDialog = true },
        modifier = Modifier.testTag("family_sync_setup")
      )
      SettingsItem(
        title = "④ 내 가족 파일 만들기",
        subtitle = settings.familySyncFileName?.let { "내 파일: $it" } ?: "저장 화면에서 드라이브 → '${settings.driveFolderName ?: "머니플로우 가족"}' 폴더를 골라 저장 (한 번만)",
        icon = Icons.Filled.NoteAdd,
        enabled = !busy && ready && settings.familySyncPasswordEncrypted != null,
        showChevron = false,
        onClick = { createFamily.launch(com.moneyflow.data.repository.FamilySyncRepository.fileNameFor(settings.familySyncName ?: "나")) },
        modifier = Modifier.testTag("family_sync_create_file")
      )
      if (settings.familySyncEnabled) {
        Text(
          buildString {
            append(if (settings.familySyncLastAt > 0) "마지막 합산: ${com.moneyflow.util.Formatters.formatDateTime(settings.familySyncLastAt)}" else "곧 첫 합산을 해요")
            if (settings.familySyncLastUploadAt > 0) append(" · 내 기록 올림: ${com.moneyflow.util.Formatters.formatDateTime(settings.familySyncLastUploadAt)}")
          },
          style = MaterialTheme.typography.bodySmall, color = Gray600,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        settings.familySyncError?.let {
          Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = AccentRed, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        SettingsItem(
          title = "지금 합산하기",
          subtitle = "내 기록을 올리고 가족 기록을 받아요 (평소에는 자동)",
          icon = Icons.Filled.PlayArrow,
          enabled = !busy,
          showChevron = false,
          onClick = viewModel::syncNow,
          modifier = Modifier.testTag("family_sync_now")
        )
        SettingsItem(
          title = "가족 자동 합산 끄기",
          subtitle = "이 휴대폰에서만 꺼져요",
          icon = Icons.Filled.CloudDone,
          iconTint = AccentRed,
          showChevron = false,
          onClick = { confirmFamilyOff = true }
        )
      }
    }
    SettingsSection("문제가 있을 때 (연결 시험)") {
      SettingsItem(
        title = "시험 파일 만들기",
        subtitle = settings.driveTestFileName?.let { "시험 파일: $it" } ?: "저장 화면에서 드라이브 → 같은 폴더를 골라 저장",
        icon = Icons.Filled.NoteAdd,
        enabled = !busy && settings.driveFolderId != null,
        showChevron = false,
        onClick = { createTest.launch(DriveRepository.TEST_FILE_NAME) },
        modifier = Modifier.testTag("drive_create_test")
      )
      SettingsItem(
        title = "연결 시험",
        subtitle = "로그인 → 폴더 보기 → 시험 파일 읽기 → 덮어쓰기를 차례로 확인해요",
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
            Text("연결 성공! 위쪽 ③, ④를 해 주세요.", color = AccentGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
          }
        }
      }
    }
    if (hasKey) {
      SettingsSection(null) {
        SettingsItem(
          title = "연결 지우기",
          subtitle = "이 휴대폰에 저장된 연결 파일과 폴더 선택을 지워요",
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
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
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
  if (familyDialog) {
    FamilySyncDialog(
      initialName = s?.familySyncName.orEmpty(),
      onDismiss = { familyDialog = false },
      onSave = { name, pw -> familyDialog = false; viewModel.setupFamily(name, pw) }
    )
  }
  if (confirmFamilyOff) {
    ConfirmDialog(
      title = "가족 자동 합산을 끌까요?",
      message = "이 휴대폰에서 자동으로 올리고 받는 것만 멈춰요. 받아 둔 가족 기록과 드라이브의 파일은 그대로예요.",
      confirmLabel = "끄기",
      onConfirm = { confirmFamilyOff = false; viewModel.turnOffFamily() },
      onDismiss = { confirmFamilyOff = false }
    )
  }
  if (confirmDisconnect) {
    ConfirmDialog(
      title = "드라이브 연결을 지울까요?",
      message = "이 휴대폰의 연결 파일과 폴더 선택만 지워져요. 드라이브의 파일은 그대로예요.",
      confirmLabel = "지우기",
      onConfirm = { confirmDisconnect = false; viewModel.disconnect() },
      onDismiss = { confirmDisconnect = false }
    )
  }
  error?.let { msg ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("확인이 필요해요", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }
    )
  }
}

@Composable
private fun FamilySyncDialog(initialName: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
  var name by rememberSaveable { mutableStateOf(initialName) }
  var pw by remember { mutableStateOf("") }
  var pw2 by remember { mutableStateOf("") }
  val min = com.moneyflow.data.repository.FamilySyncRepository.MIN_PASSWORD
  val ok = name.isNotBlank() && pw.length >= min && pw == pw2
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("가족 자동 합산", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
          "가족 화면에 보일 내 이름과, 가족 모두가 같이 쓸 비밀번호예요. 드라이브의 가족 파일은 이 비밀번호로 암호화돼요. (메모 포함, 앱 잠금 PIN은 들어가지 않아요)",
          style = MaterialTheme.typography.bodySmall, color = Gray600
        )
        androidx.compose.material3.OutlinedTextField(
          value = name, onValueChange = { name = it.take(10) }, label = { Text("내 이름 (예: 세윤, 엄마)") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("family_sync_name")
        )
        androidx.compose.material3.OutlinedTextField(
          value = pw, onValueChange = { pw = it }, label = { Text("가족 비밀번호 (${min}자 이상)") }, singleLine = true,
          visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("family_sync_pw")
        )
        androidx.compose.material3.OutlinedTextField(
          value = pw2, onValueChange = { pw2 = it }, label = { Text("한 번 더") }, singleLine = true,
          visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
          isError = pw2.isNotEmpty() && pw != pw2,
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("family_sync_pw2")
        )
      }
    },
    confirmButton = { TextButton(onClick = { onSave(name.trim(), pw) }, enabled = ok) { Text("저장") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
}
