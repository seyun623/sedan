package com.moneyflow.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class PasswordPurpose { SAVE, SHARE, OPEN }

/** Create an encrypted backup file and restore from one. */
@Composable
fun BackupScreen(
  onBack: () -> Unit,
  onRestored: () -> Unit = onBack,
  viewModel: BackupViewModel = hiltViewModel()
) {
  val context = LocalContext.current
  val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val pending by viewModel.pendingRestore.collectAsStateWithLifecycle()

  var passwordPurpose by rememberSaveable { mutableStateOf<PasswordPurpose?>(null) }
  var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  // Kept only in memory between the password dialog and the file picker result.
  var heldPassword by remember { mutableStateOf<String?>(null) }
  var openedUri by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmRestore by rememberSaveable { mutableStateOf(false) }

  val fileName = remember { "moneyflow-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.${BackupRepository.FILE_EXTENSION}" }

  val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
    val pw = heldPassword
    heldPassword = null
    if (uri == null) return@rememberLauncherForActivityResult
    if (pw == null) { errorMessage = "다시 시도해 주세요."; return@rememberLauncherForActivityResult }
    viewModel.saveBackup(uri, pw)
  }
  val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      openedUri = uri.toString()
      passwordPurpose = PasswordPurpose.OPEN
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { e ->
      when (e) {
        is BackupEvent.Error -> errorMessage = e.message
        is BackupEvent.Share -> {
          val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", e.file)
          val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          context.startActivity(Intent.createChooser(send, "백업 파일 보내기"))
        }
        BackupEvent.Restored -> onRestored()
      }
    }
  }

  SettingsScaffold(title = "백업 및 복원", onBack = onBack) {
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TossBlue)

    SettingsSection("백업") {
      Text(
        text = if (lastBackupAt > 0) "마지막 백업: ${Formatters.formatDateTime(lastBackupAt)}" else "아직 백업한 적이 없어요",
        style = MaterialTheme.typography.bodyMedium,
        color = Gray600,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )
      SettingsItem(
        title = "백업 파일로 저장",
        subtitle = "휴대폰 저장공간이나 구글 드라이브에 저장해요",
        icon = Icons.Filled.SaveAlt,
        enabled = !busy,
        showChevron = false,
        onClick = { passwordPurpose = PasswordPurpose.SAVE },
        modifier = Modifier.testTag("backup_save")
      )
      SettingsItem(
        title = "백업 파일 보내기",
        subtitle = "카카오톡 '나에게 보내기', 메일 등으로 보내요",
        icon = Icons.AutoMirrored.Filled.Send,
        enabled = !busy,
        showChevron = false,
        onClick = { passwordPurpose = PasswordPurpose.SHARE },
        modifier = Modifier.testTag("backup_share")
      )
    }

    SettingsSection("복원") {
      SettingsItem(
        title = "백업 파일 불러오기",
        subtitle = "지금 있는 기록은 모두 백업 내용으로 바뀌어요",
        icon = Icons.Filled.Restore,
        enabled = !busy,
        showChevron = false,
        onClick = { openDocument.launch(arrayOf("*/*")) },
        modifier = Modifier.testTag("backup_restore")
      )
    }

    SettingsSection("알아 두세요") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(
          "백업 파일은 비밀번호로 암호화돼요. 비밀번호를 잊으면 아무도 열 수 없어요.",
          "계좌, 거래, 예산, 저축 목표, 고정 거래가 모두 들어가요. 앱 잠금 PIN과 화면 설정은 들어가지 않아요.",
          "휴대폰을 바꾸거나 초기화하기 전에 꼭 백업해 두세요. 앱을 지우면 기록도 함께 지워져요."
        ).forEach {
          Text("• $it", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(vertical = 3.dp))
        }
      }
    }
  }

  passwordPurpose?.let { purpose ->
    BackupPasswordDialog(
      confirm = purpose != PasswordPurpose.OPEN,
      onDismiss = { passwordPurpose = null; openedUri = null },
      onSubmit = { pw ->
        passwordPurpose = null
        when (purpose) {
          PasswordPurpose.SAVE -> { heldPassword = pw; createDocument.launch(fileName) }
          PasswordPurpose.SHARE -> viewModel.shareBackup(pw, fileName)
          PasswordPurpose.OPEN -> openedUri?.let { viewModel.openBackup(android.net.Uri.parse(it), pw) }
        }
      }
    )
  }

  pending?.let { content ->
    val s = content.summary
    if (!confirmRestore) {
      AlertDialog(
        onDismissRequest = viewModel::cancelRestore,
        title = { Text("백업 내용 확인", fontWeight = FontWeight.Bold) },
        text = {
          Column {
            Text("만든 날짜: ${Formatters.formatDateTime(s.createdAt)}")
            Text("계좌 ${s.accountCount}개 · 거래 ${s.transactionCount}건")
            Text("저축 목표 ${s.goalCount}개 · 고정 거래 ${s.recurringCount}개")
          }
        },
        confirmButton = { TextButton(onClick = { confirmRestore = true }) { Text("복원하기", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("취소") } }
      )
    } else {
      ConfirmDialog(
        title = "지금 기록을 바꿀까요?",
        message = "이 휴대폰에 있는 계좌와 거래 기록이 모두 지워지고 백업 내용으로 바뀌어요. 되돌릴 수 없어요.",
        confirmLabel = "바꾸기",
        onConfirm = { confirmRestore = false; viewModel.confirmRestore() },
        onDismiss = { confirmRestore = false; viewModel.cancelRestore() }
      )
    }
  }

  errorMessage?.let { msg ->
    AlertDialog(
      onDismissRequest = { errorMessage = null },
      title = { Text("알림", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("확인") } }
    )
  }
}

@Composable
private fun BackupPasswordDialog(
  confirm: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (String) -> Unit
) {
  var pw by remember { mutableStateOf("") }
  var pw2 by remember { mutableStateOf("") }
  val tooShort = pw.length < MIN_PASSWORD
  val mismatch = confirm && pw2.isNotEmpty() && pw != pw2
  val canSubmit = !tooShort && (!confirm || pw == pw2)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (confirm) "백업 비밀번호 정하기" else "백업 비밀번호 입력", fontWeight = FontWeight.Bold) },
    text = {
      Column {
        if (confirm) {
          Text("복원할 때 이 비밀번호가 필요해요. 잊으면 백업을 열 수 없어요.", style = MaterialTheme.typography.bodySmall, color = AccentRed)
          Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
          value = pw,
          onValueChange = { pw = it },
          label = { Text("비밀번호 (${MIN_PASSWORD}자 이상)") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier.fillMaxWidth().testTag("backup_password")
        )
        if (confirm) {
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = pw2,
            onValueChange = { pw2 = it },
            label = { Text("비밀번호 확인") },
            singleLine = true,
            isError = mismatch,
            supportingText = if (mismatch) { { Text("비밀번호가 서로 달라요") } } else null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("backup_password_confirm")
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onSubmit(pw) }, enabled = canSubmit) { Text("확인", fontWeight = FontWeight.Bold) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
}

private const val MIN_PASSWORD = 4
