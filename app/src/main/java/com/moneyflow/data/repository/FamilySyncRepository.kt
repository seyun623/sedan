package com.moneyflow.data.repository

import com.moneyflow.BuildConfig
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.backup.NotABackupFileException
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
  suspend fun fileCreated(displayName: String) = settings.setFamilySyncFile(displayName, clock.nowMillis())

  suspend fun turnOff() = settings.clearFamilySync()

  suspend fun markDirty() = settings.setFamilySyncDirty(true)

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
    var uploaded = false
    val loaded = mutableListOf<String>()
    try {
      val pw = password()
      val files = drive.folderFiles()
      val myName = s.familySyncFileName!!
      val mine = files.firstOrNull { it.name == myName }
      if (mine == null) {
        problems += "드라이브 폴더에서 내 가족 파일 '$myName'을(를) 찾지 못했어요. 지웠다면 '내 가족 파일 만들기'를 다시 해 주세요. (방금 만들었다면 1~2분 뒤 다시 시도)"
      } else if (forceUpload || s.familySyncDirty || now - s.familySyncLastUploadAt >= DAY_MS) {
        drive.overwrite(mine.id, backups.createBackup(pw, BuildConfig.VERSION_NAME))
        uploaded = true
      }

      val seen = settings.familySyncSeen().toMutableMap()
      val others = files.filter { it.id != mine?.id && isFamilyFile(it.name) }
      for (f in others) {
        if (seen[f.id] == f.modifiedTime) continue
        val name = memberName(f.name)
        try {
          val r = family.import(drive.download(f.id), pw, name, keepExistingName = true)
          loaded += r.member.name
          seen[f.id] = f.modifiedTime
        } catch (e: FamilyImportException) {
          // A copy of this phone's own file (e.g. made before reinstalling): skip it from now on.
          seen[f.id] = f.modifiedTime
        } catch (e: WrongPasswordException) {
          problems += "'${f.name}' 파일을 열 수 없어요. 그 사람과 가족 비밀번호가 같은지 확인해 주세요."
        } catch (e: NotABackupFileException) {
          seen[f.id] = f.modifiedTime
          problems += "'${f.name}'은(는) 머니플로우 파일이 아니에요."
        }
      }
      // Forget files that were removed from the folder.
      settings.setFamilySyncSeen(seen.filterKeys { id -> files.any { it.id == id } })
      settings.setFamilySyncResult(now, if (uploaded) now else null, problems.joinToString("\n").ifEmpty { null })
      return FamilySyncResult(uploaded, loaded, problems)
    } catch (e: IOException) {
      throw e
    } catch (e: Exception) {
      settings.setFamilySyncResult(now, if (uploaded) now else null, e.message ?: "가족 합산에 실패했어요.")
      throw e
    }
  }

  companion object {
    const val PREFIX = "moneyflow-family-"
    const val MIN_PASSWORD = 6
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
