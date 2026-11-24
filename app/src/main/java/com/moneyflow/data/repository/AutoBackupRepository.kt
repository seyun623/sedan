package com.moneyflow.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.moneyflow.BuildConfig
import com.moneyflow.domain.AppClock
import com.moneyflow.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Where automatic backups are written (a folder the user picked). */
interface BackupDestination {
  /** File names in the folder. */
  fun list(): List<String>
  fun write(name: String, bytes: ByteArray)
  fun delete(name: String)
}

/** A folder picked with the system file picker (storage, Google Drive …), via the Storage Access Framework. */
class SafBackupDestination(private val context: Context, private val tree: Uri) : BackupDestination {
  private val resolver get() = context.contentResolver
  private val folderDoc: Uri get() = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

  private fun children(): List<Pair<String, Uri>> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val out = mutableListOf<Pair<String, Uri>>()
    resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null, null, null
    )?.use { c ->
      while (c.moveToNext()) out += c.getString(1) to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
    } ?: throw IllegalStateException("백업 폴더를 열 수 없어요.")
    return out
  }

  override fun list(): List<String> = children().map { it.first }

  override fun write(name: String, bytes: ByteArray) {
    val doc = DocumentsContract.createDocument(resolver, folderDoc, "application/octet-stream", name)
      ?: throw IllegalStateException("백업 폴더에 파일을 만들 수 없어요.")
    resolver.openOutputStream(doc, "wt")?.use { it.write(bytes) } ?: throw IllegalStateException("백업 파일을 저장할 수 없어요.")
  }

  override fun delete(name: String) {
    children().filter { it.first == name }.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.second) } }
  }
}

/** Writes password-encrypted backups to the chosen folder on a schedule, keeping the newest few. */
@Singleton
class AutoBackupRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val backups: BackupRepository,
  private val settings: SettingsRepository,
  private val secrets: SecretStore,
  private val clock: AppClock,
  private val drive: DriveRepository
) {
  private val mutex = Mutex()

  /** Test hook: replaces the folder access. */
  internal var destinationFactory: (String) -> BackupDestination = { SafBackupDestination(context, Uri.parse(it)) }

  fun isDue(s: AppSettings, now: Long): Boolean =
    s.autoBackupEnabled && now - s.autoBackupLastAt >= s.autoBackupIntervalDays * DAY_MS - GRACE_MS

  /** Turns auto backup on (the folder permission must already be taken). */
  suspend fun enable(folder: String, password: String, intervalDays: Int) {
    settings.setAutoBackup(folder, secrets.encrypt(password), intervalDays)
  }

  /** Turns auto backup on into [fileName], a file the user just saved in the shared Drive folder (first backup already in it). */
  suspend fun enableDrive(fileName: String, password: String, intervalDays: Int) {
    settings.setAutoBackup(DRIVE_PREFIX + fileName, secrets.encrypt(password), intervalDays)
    settings.setAutoBackupResult(clock.nowMillis(), null)
  }

  suspend fun disable() {
    val folder = settings.current().autoBackupFolder
    settings.clearAutoBackup()
    folder?.takeUnless(::isDrive)?.let { f ->
      runCatching {
        context.contentResolver.releasePersistableUriPermission(
          Uri.parse(f), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      }
    }
  }

  /**
   * Makes a backup now when due (or always with [force]). Returns the file name, or null if not due.
   * Failures are recorded in settings (shown in the backup screen and as a reminder) and rethrown.
   */
  suspend fun runIfDue(force: Boolean = false): String? = mutex.withLock {
    val s = settings.current()
    val now = clock.nowMillis()
    if (!s.autoBackupEnabled || (!force && !isDue(s, now))) return null
    try {
      val password = runCatching { secrets.decrypt(s.autoBackupPasswordEncrypted!!) }.getOrNull()
        ?: throw IllegalStateException("저장된 백업 비밀번호를 읽을 수 없어요. 자동 백업을 다시 켜 주세요.")
      val bytes = backups.createBackup(password.toCharArray(), BuildConfig.VERSION_NAME)
      val folder = s.autoBackupFolder!!
      if (isDrive(folder)) {
        // One file in the shared Drive folder, overwritten by the bot (Drive keeps older versions).
        val name = folder.removePrefix(DRIVE_PREFIX)
        drive.overwriteByName(name, bytes)
        settings.setAutoBackupResult(now, null)
        return name
      }
      val name = fileName(now)
      withContext(Dispatchers.IO) {
        val dest = try { destinationFactory(s.autoBackupFolder!!) } catch (e: Exception) {
          throw IllegalStateException("백업 폴더에 접근할 수 없어요. 폴더를 다시 골라 주세요.")
        }
        dest.write(name, bytes)
        prune(dest.list()).forEach { runCatching { dest.delete(it) } }
      }
      settings.setAutoBackupResult(now, null)
      name
    } catch (e: java.io.IOException) {
      // No connection (Drive): try again on the next run without warning the user.
      if (s.autoBackupFolder?.let(::isDrive) == true) throw e
      settings.setAutoBackupResult(now, e.message ?: "자동 백업에 실패했어요.")
      throw IllegalStateException(e.message ?: "자동 백업에 실패했어요.", e)
    } catch (e: Exception) {
      val msg = when (e) {
        is SecurityException -> "백업 폴더 권한이 없어요. 폴더를 다시 골라 주세요."
        else -> e.message ?: "자동 백업에 실패했어요."
      }
      settings.setAutoBackupResult(now, msg)
      throw IllegalStateException(msg, e)
    }
  }

  private fun fileName(now: Long): String =
    PREFIX + java.time.Instant.ofEpochMilli(now).atZone(clock.zone).format(STAMP) + "." + BackupRepository.FILE_EXTENSION

  companion object {
    /** autoBackupFolder value for a file in the shared Drive folder: "drive:<file name>". */
    const val DRIVE_PREFIX = "drive:"
    fun isDrive(folder: String) = folder.startsWith(DRIVE_PREFIX)
    /** e.g. "moneyflow-backup-세윤-3f2a.mfbackup": the install id part keeps family members' files apart. */
    fun driveFileNameFor(name: String, installId: String): String =
      "moneyflow-backup-" + name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").ifEmpty { "나" } + "-" + installId.filter { it.isLetterOrDigit() }.take(4) + "." + BackupRepository.FILE_EXTENSION

    const val PREFIX = "moneyflow-auto-"
    const val KEEP = 5
    private const val DAY_MS = 24L * 3_600_000
    /** Runs happen every few hours, so allow a little early. */
    private const val GRACE_MS = 3L * 3_600_000
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** Old automatic backups to delete (newest [KEEP] stay; other files are never touched). */
    fun prune(names: List<String>): List<String> =
      names.filter { it.startsWith(PREFIX) && it.endsWith("." + BackupRepository.FILE_EXTENSION) }
        .sortedDescending()
        .drop(KEEP)
  }
}
