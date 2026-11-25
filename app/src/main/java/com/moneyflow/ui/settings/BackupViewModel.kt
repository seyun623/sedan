package com.moneyflow.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyflow.BuildConfig
import com.moneyflow.data.repository.BackupContent
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.RecurringRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.ui.common.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class AutoBackupUi(
  val enabled: Boolean = false,
  /** Writing to one file in the shared Google Drive folder through the bot. */
  val toDrive: Boolean = false,
  /** Drive bot and folder are set up (Drive auto backup can be turned on). */
  val driveReady: Boolean = false,
  val driveFolderName: String? = null,
  val driveFileName: String = "moneyflow-backup.mfbackup",
  val lockEnabled: Boolean = false,
  val folderName: String? = null,
  val intervalDays: Int = 7,
  val lastAt: Long = 0L,
  val error: String? = null
)

sealed interface BackupEvent {
  data class Error(val message: String) : BackupEvent
  /** A backup was written to a cache file; share it (e.g. KakaoTalk "나에게 보내기"). */
  data class Share(val file: File) : BackupEvent
  data object Restored : BackupEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val backups: BackupRepository,
  private val recurring: RecurringRepository,
  settings: SettingsRepository,
  private val autoBackup: com.moneyflow.data.repository.AutoBackupRepository,
  private val messages: UserMessageBus
) : ViewModel() {

  /** Auto backup state for the screen. */
  val autoState: StateFlow<AutoBackupUi> = settings.settings.map { s ->
    AutoBackupUi(
      enabled = s.autoBackupEnabled,
      toDrive = s.autoBackupFolder?.let(com.moneyflow.data.repository.AutoBackupRepository::isDrive) == true,
      driveReady = s.driveKeyEncrypted != null && s.driveFolderId != null,
      driveFolderName = s.driveFolderName,
      lockEnabled = s.isLockEnabled,
      driveFileName = com.moneyflow.data.repository.AutoBackupRepository.driveFileNameFor(s.familySyncName ?: "나", settings.installId()),
      folderName = s.autoBackupFolder?.let {
        if (com.moneyflow.data.repository.AutoBackupRepository.isDrive(it)) "구글 드라이브 '${s.driveFolderName ?: "공유 폴더"}'의 ${it.removePrefix(com.moneyflow.data.repository.AutoBackupRepository.DRIVE_PREFIX)}"
        else folderName(it)
      },
      intervalDays = s.autoBackupIntervalDays,
      lastAt = s.autoBackupLastAt,
      error = s.autoBackupError
    )
  }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupUi())

  /** Turns auto backup on with a folder from the system picker and makes the first backup. */
  fun enableAutoBackup(tree: Uri, password: String, intervalDays: Int) = run {
    withContext(Dispatchers.IO) {
      context.contentResolver.takePersistableUriPermission(
        tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      )
    }
    autoBackup.enable(tree.toString(), password, intervalDays)
    autoBackup.runIfDue(force = true)
    messages.show("자동 백업을 켰어요. 첫 백업을 저장했어요.")
  }

  /** Writes the first backup into the file the user just saved in the Drive folder, then lets the bot overwrite it. */
  fun enableDriveAutoBackup(uri: Uri, password: String) = run {
    val bytes = backups.createBackup(password.toCharArray(), BuildConfig.VERSION_NAME)
    val name = withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("파일을 저장할 수 없어요.")
      context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } ?: error("저장한 파일 이름을 알 수 없어요. 드라이브에 저장했는지 확인해 주세요.")
    autoBackup.enableDrive(name, password, intervalDays = 1)
    messages.show("드라이브 자동 백업을 켰어요. 첫 백업 '$name'을(를) 저장했어요.")
  }

  fun disableAutoBackup() = run {
    autoBackup.disable()
    messages.show("자동 백업을 껐어요. 저장된 백업 파일은 그대로 있어요.")
  }

  fun autoBackupNow() = run {
    val name = autoBackup.runIfDue(force = true)
    messages.show("백업했어요: $name")
  }

  fun setAutoBackupInterval(days: Int) = viewModelScope.launch { settingsRepo.setAutoBackupInterval(days) }

  private val settingsRepo = settings

  private fun folderName(tree: String): String = runCatching {
    val uri = Uri.parse(tree)
    val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, android.provider.DocumentsContract.getTreeDocumentId(uri))
    context.contentResolver.query(doc, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
      ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
  }.getOrNull() ?: runCatching {
    android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfter(':').ifEmpty { "선택한 폴더" }
  }.getOrDefault("선택한 폴더")

  val lastBackupAt: StateFlow<Long> = settings.settings.map { it.lastBackupAt }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  /** A backup that was opened and is waiting for the user's confirmation to restore. */
  private val _pendingRestoreOwner = MutableStateFlow<String?>(null)
  /** The opened backup belongs to this family member (shown as a warning before restoring). */
  val pendingRestoreOwner: StateFlow<String?> = _pendingRestoreOwner.asStateFlow()
  private val _pendingRestore = MutableStateFlow<BackupContent?>(null)
  val pendingRestore: StateFlow<BackupContent?> = _pendingRestore.asStateFlow()

  private val _events = Channel<BackupEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  /** Saves an encrypted backup to a document the user picked. */
  fun saveBackup(uri: Uri, password: String) = run {
    val bytes = backups.createBackup(password.toCharArray(), BuildConfig.VERSION_NAME)
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
        ?: error("파일을 저장할 수 없어요.")
    }
    messages.show("백업 파일을 저장했어요.")
  }

  /** Writes an encrypted backup to the share cache and asks the UI to share it. */
  fun shareBackup(password: String, fileName: String) = run {
    val bytes = backups.createBackup(password.toCharArray(), BuildConfig.VERSION_NAME)
    val file = withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, "exports").apply { mkdirs() }
      dir.listFiles()?.forEach { it.delete() }
      File(dir, fileName).apply { writeBytes(bytes) }
    }
    _events.send(BackupEvent.Share(file))
  }

  /** Reads and checks a backup file; on success it becomes [pendingRestore]. */
  fun openBackup(uri: Uri, password: String) = run {
    val bytes = withContext(Dispatchers.IO) {
      context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("파일을 열 수 없어요.")
    }
    val content = withContext(Dispatchers.Default) { backups.readBackup(bytes, password.toCharArray()) }
    _pendingRestoreOwner.value = backups.familyMemberOf(content)
    _pendingRestore.value = content
  }

  fun cancelRestore() { _pendingRestore.value = null }

  fun confirmRestore() = run {
    val content = _pendingRestore.value ?: return@run
    // A safety copy first, so a wrong file can be undone.
    autoBackup.saveBeforeRestore()
    backups.restore(content)
    _pendingRestore.value = null
    // Record any fixed transactions that came due since the backup was made.
    recurring.applyDue()
    messages.show("백업에서 복원했어요.")
    _events.send(BackupEvent.Restored)
  }

  val preRestoreAt: StateFlow<Long> = settingsRepo.settings.map { it.preRestoreAt }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

  /** Puts back the records from just before the last restore. */
  fun undoRestore() = run {
    val content = autoBackup.readBeforeRestore()
    backups.restore(content)
    autoBackup.clearBeforeRestore()
    recurring.applyDue()
    messages.show("복원하기 전 기록으로 되돌렸어요.")
    _events.send(BackupEvent.Restored)
  }

  private fun run(block: suspend () -> Unit) = viewModelScope.launch {
    if (_busy.value) return@launch
    _busy.value = true
    try {
      block()
    } catch (e: Exception) {
      _events.send(BackupEvent.Error(com.moneyflow.util.UserErrors.message(e)))
    } finally {
      _busy.value = false
    }
  }
}
