package com.moneyflow.data.repository

import com.moneyflow.BuildConfig
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.backup.NotABackupFileException
import com.moneyflow.data.repository.BackupFormatException
import com.moneyflow.domain.backup.WrongPasswordException
import com.moneyflow.security.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What one sync run did. */
data class FamilySyncResult(
  val uploaded: Boolean,
  /** Names of family members whose newer file was loaded. */
  val loaded: List<String>,
  /** Problems with single files (the rest of the sync still ran). */
  val problems: List<String>
)

/**
 * 가족 자동 합산 through the Drive bot. Each phone keeps one family file in the shared folder
 * ("moneyflow-family-<name>.mfbackup", a backup encrypted with the common family password). The
 * phone creates it once with the system save screen; afterwards the bot overwrites it when the
 * data changes, and loads every other family file whose Drive modified time changed.
 */
@Singleton
class FamilySyncRepository @Inject constructor(
  private val drive: DriveRepository,
  private val backups: BackupRepository,
  private val family: FamilyRepository,
  private val settings: SettingsRepository,
  private val secrets: SecretStore,
  private val clock: AppClock
) {
  private val mutex = Mutex()

  /** Saves my name and the family password (same password on every family phone). */
  suspend fun setup(name: String, password: String) {
    require(name.isNotBlank()) { "이름을 입력해 주세요." }
    require(password.length >= MIN_PASSWORD) { "가족 비밀번호는 ${MIN_PASSWORD}자 이상으로 정해 주세요." }
    settings.setFamilySync(name.trim(), secrets.encrypt(password))
  }

  private suspend fun password(): CharArray {
    val enc = settings.current().familySyncPasswordEncrypted ?: throw IllegalStateException("가족 비밀번호를 먼저 정해 주세요.")
    return (secrets.decrypt(enc) ?: throw IllegalStateException("저장된 가족 비밀번호를 읽을 수 없어요. 다시 정해 주세요.")).toCharArray()
  }

  /** Contents for my family file (written once through the save screen, later by the bot). */
  suspend fun familyFileBytes(): ByteArray = backups.createBackup(password(), BuildConfig.VERSION_NAME)

  /** Records the file the user just saved into the Drive folder. */
  suspend fun fileCreated(displayName: String, generation: Long) = settings.setFamilySyncFile(displayName, clock.nowMillis(), generation)

  /** Current data version (read before making the file contents). */
  suspend fun dataGeneration(): Long = settings.familySyncGenerations().first

  suspend fun turnOff() = settings.clearFamilySync()

  /** Family files in the shared folder, newest first (to restore this phone from one of them). */
  suspend fun familyFiles(): List<com.moneyflow.drive.DriveFile> {
    drive.checkFolderAccess()
    return drive.folderFiles().filter { isFamilyFile(it.name) }.sortedByDescending { it.modifiedTime }
  }

  /** Downloads and opens a family file with the family password (it is a full backup of that phone). */
  suspend fun openFamilyFile(file: com.moneyflow.drive.DriveFile, password: String): BackupContent =
    backups.readBackup(drive.download(file.id), password.toCharArray())

  /**
   * After this phone was restored from [file]: keep using that file as this phone's family file,
   * with the same name and password, so the family's combined view simply continues.
   */
  suspend fun adoptRestoredFile(file: com.moneyflow.drive.DriveFile, password: String) {
    settings.setFamilySync(memberName(file.name), secrets.encrypt(password))
    settings.setFamilySyncFile(file.name, clock.nowMillis(), settings.familySyncGenerations().first)
    // The restored copy came from this file, so it is not another member's file.
    settings.setFamilySyncSeen(settings.familySyncSeen() - file.id)
  }

  suspend fun markDirty() = settings.markFamilyDataChanged()

  /**
   * Uploads my file (when the data changed, a day passed, or [forceUpload]) and loads family
   * files that changed. Returns null when family sync isn't set up. Network errors are thrown
   * (try again later) without being recorded; other problems are stored for the screen.
   */
  suspend fun sync(forceUpload: Boolean = false): FamilySyncResult? = mutex.withLock {
    val s = settings.current()
    if (!s.familySyncEnabled) return null
    val now = clock.nowMillis()
    val problems = mutableListOf<String>()
    var uploadedGen: Long? = null
    val loaded = mutableListOf<String>()
    try {
      val pw = password()
      // An unshared folder lists as empty: check it first so the user isn't told to remake files.
      drive.checkFolderAccess()
      val files = drive.folderFiles()
      val myName = s.familySyncFileName!!
      val mineAll = files.filter { it.name == myName }
      if (mineAll.size > 1) problems += "드라이브 폴더에 '$myName' 파일이 ${mineAll.size}개 있어요. 오래된 것을 지워 주세요."
      val mine = mineAll.maxByOrNull { it.modifiedTime }
      val (generation, uploadedGeneration) = settings.familySyncGenerations()
      if (mine == null) {
        problems += "드라이브 폴더에서 내 가족 파일 '$myName'을(를) 찾지 못했어요. 지웠다면 '내 가족 파일 만들기'를 다시 해 주세요. (방금 만들었다면 1~2분 뒤 다시 시도)"
      } else if (forceUpload || generation != uploadedGeneration || now - s.familySyncLastUploadAt >= DAY_MS) {
        // The version is read before the snapshot: a change made meanwhile stays "to upload".
        drive.overwrite(mine.id, backups.createBackup(pw, BuildConfig.VERSION_NAME))
        uploadedGen = generation
      }

      val seen = settings.familySyncSeen().toMutableMap()
      val failed = settings.familySyncFailed().toMutableMap()
      val myIds = mineAll.map { it.id }.toSet()
      // Newest first, so a stale duplicate of someone's file can't replace newer records.
      val others = files.filter { it.id !in myIds && isFamilyFile(it.name) }.sortedByDescending { it.modifiedTime }
      for (f in others) {
        if (seen[f.id] == f.modifiedTime) continue
        // A file that couldn't be opened is retried only when it changes (or on "지금 합산").
        if (!forceUpload && failed[f.id] == f.modifiedTime) continue
        try {
          val r = family.import(drive.download(f.id), pw, memberName(f.name), keepExistingName = true, allowOlder = false)
          if (!r.skippedAsOlder) loaded += r.member.name
          seen[f.id] = f.modifiedTime
          failed.remove(f.id)
        } catch (e: FamilyImportException) {
          // A copy of this phone's own file (e.g. made before reinstalling): skip it from now on.
          seen[f.id] = f.modifiedTime
        } catch (e: WrongPasswordException) {
          failed[f.id] = f.modifiedTime
          problems += "'${f.name}' 파일을 열 수 없어요. 그 사람과 가족 비밀번호가 같은지 확인해 주세요."
        } catch (e: NotABackupFileException) {
          seen[f.id] = f.modifiedTime
          problems += "'${f.name}'은(는) 머니플로우 파일이 아니에요."
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (e: IOException) {
          throw e
        } catch (e: Exception) {
          // One bad file (newer app version, deleted meanwhile …) must not stop the others.
          failed[f.id] = f.modifiedTime
          problems += "'${f.name}': " + (if (e is BackupFormatException) "더 새 버전의 앱에서 만든 파일이에요. 앱을 업데이트해 주세요." else e.message ?: "읽지 못했어요.")
        }
      }
      // Forget files that were removed from the folder (the listing worked, so this is real).
      settings.setFamilySyncSeen(seen.filterKeys { id -> files.any { it.id == id } })
      settings.setFamilySyncFailed(failed.filterKeys { id -> files.any { it.id == id } })
      settings.setFamilySyncResult(now, uploadedGen?.let { now }, uploadedGen, problems.joinToString("\n").ifEmpty { null })
      return FamilySyncResult(uploadedGen != null, loaded, problems)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: IOException) {
      throw e
    } catch (e: Exception) {
      if (e is com.moneyflow.drive.DriveException && e.transient) throw e
      settings.setFamilySyncResult(now, uploadedGen?.let { now }, uploadedGen, e.message ?: "가족 합산에 실패했어요.")
      throw e
    }
  }

  companion object {
    const val PREFIX = "moneyflow-family-"
    const val MIN_PASSWORD = 8
    private const val DAY_MS = 24L * 3_600_000

    fun fileNameFor(name: String): String =
      PREFIX + name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").ifEmpty { "가족" } + "." + BackupRepository.FILE_EXTENSION

    fun isFamilyFile(name: String): Boolean = name.startsWith(PREFIX) && name.endsWith("." + BackupRepository.FILE_EXTENSION)

    /** "moneyflow-family-엄마.mfbackup" → "엄마" (Drive may add " (1)" for duplicates). */
    fun memberName(fileName: String): String =
      fileName.removePrefix(PREFIX).removeSuffix("." + BackupRepository.FILE_EXTENSION)
        .replace(Regex("\\s*\\(\\d+\\)$"), "").trim().ifEmpty { "가족" }
  }
}
