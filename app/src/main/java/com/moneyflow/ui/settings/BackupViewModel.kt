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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

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
  private val messages: UserMessageBus
) : ViewModel() {

  val lastBackupAt: StateFlow<Long> = settings.settings.map { it.lastBackupAt }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  /** A backup that was opened and is waiting for the user's confirmation to restore. */
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
    _pendingRestore.value = withContext(Dispatchers.Default) { backups.readBackup(bytes, password.toCharArray()) }
  }

  fun cancelRestore() { _pendingRestore.value = null }

  fun confirmRestore() = run {
    val content = _pendingRestore.value ?: return@run
    backups.restore(content)
    _pendingRestore.value = null
    // Record any fixed transactions that came due since the backup was made.
    recurring.applyDue()
    messages.show("백업에서 복원했어요.")
    _events.send(BackupEvent.Restored)
  }

  private fun run(block: suspend () -> Unit) = viewModelScope.launch {
    if (_busy.value) return@launch
    _busy.value = true
    try {
      block()
    } catch (e: Exception) {
      _events.send(BackupEvent.Error(e.message ?: "처리하지 못했어요."))
    } finally {
      _busy.value = false
    }
  }
}
