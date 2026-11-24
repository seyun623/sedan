package com.moneyflow.data.repository

import com.moneyflow.domain.AppClock
import com.moneyflow.drive.DriveClient
import com.moneyflow.drive.DriveFile
import com.moneyflow.drive.ServiceAccountKey
import com.moneyflow.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One line of the connection test. */
data class DriveCheck(val title: String, val ok: Boolean, val detail: String)

/**
 * Google Drive through a bot (service account) the user created. The bot only sees the folder
 * shared with its email; it reads files there and overwrites files people created (it can't
 * create files itself: service accounts have no Drive storage).
 */
@Singleton
class DriveRepository @Inject constructor(
  private val settings: SettingsRepository,
  private val secrets: SecretStore,
  private val clock: AppClock
) {
  /** Test hook. */
  internal var clientFactory: (ServiceAccountKey) -> DriveClient = { DriveClient(it, nowSeconds = { clock.nowMillis() / 1000 }) }
  private var cached: Pair<String, DriveClient>? = null

  /** Checks and stores the key file; returns the bot email the user must share the folder with. */
  suspend fun importKey(json: String): String {
    val key = ServiceAccountKey.parse(json)
    key.privateKey() // fails early on a broken key
    settings.setDriveKey(secrets.encrypt(json), key.clientEmail)
    cached = null
    return key.clientEmail
  }

  suspend fun disconnect() {
    settings.setDriveKey(null, null)
    cached = null
  }

  private suspend fun client(): DriveClient {
    val enc = settings.current().driveKeyEncrypted ?: throw IllegalStateException("먼저 열쇠 파일을 불러와 주세요.")
    cached?.let { (e, c) -> if (e == enc) return c }
    val json = secrets.decrypt(enc) ?: throw IllegalStateException("저장된 열쇠를 읽을 수 없어요. 열쇠 파일을 다시 불러와 주세요.")
    return clientFactory(ServiceAccountKey.parse(json)).also { cached = enc to it }
  }

  suspend fun sharedFolders(): List<DriveFile> = withContext(Dispatchers.IO) { client().sharedFolders() }

  suspend fun chooseFolder(folder: DriveFile) = settings.setDriveFolder(folder.id, folder.name)

  /**
   * The connection test: sign in as the bot, list the folder, read the test file the user made,
   * overwrite it and read it back.
   */
  suspend fun runCheck(): List<DriveCheck> = withContext(Dispatchers.IO) {
    val out = mutableListOf<DriveCheck>()
    val s = settings.current()
    val c = try {
      client().also { it.accessToken() }
    } catch (e: Exception) {
      out += DriveCheck("① 봇 로그인", false, e.message ?: "실패")
      return@withContext out
    }
    out += DriveCheck("① 봇 로그인", true, s.driveBotEmail.orEmpty())

    val folderId = s.driveFolderId ?: run {
      out += DriveCheck("② 폴더 보기", false, "공유된 폴더를 먼저 골라 주세요.")
      return@withContext out
    }
    val files = try { c.filesIn(folderId) } catch (e: Exception) {
      out += DriveCheck("② 폴더 보기", false, e.message ?: "실패")
      return@withContext out
    }
    out += DriveCheck("② 폴더 보기", true, "'${s.driveFolderName}' 안 파일 ${files.size}개")

    val name = s.driveTestFileName
    val test = files.firstOrNull { it.name == name }
    if (name == null || test == null) {
      out += DriveCheck("③ 시험 파일 읽기", false,
        if (name == null) "시험 파일을 먼저 만들어 주세요." else "폴더에서 '$name'을(를) 찾지 못했어요. 시험 파일을 이 폴더에 저장했는지 확인해 주세요. (드라이브에 올라가는 데 1~2분 걸릴 수 있어요)")
      return@withContext out
    }
    try {
      val text = c.download(test.id).toString(Charsets.UTF_8)
      out += DriveCheck("③ 시험 파일 읽기", true, "내용: ${text.take(60)}" + (test.ownerName?.let { " · 주인: $it" } ?: ""))
    } catch (e: Exception) {
      out += DriveCheck("③ 시험 파일 읽기", false, e.message ?: "실패")
      return@withContext out
    }
    try {
      val stamp = "updated-by-bot ${clock.nowMillis()}"
      c.update(test.id, stamp.toByteArray())
      val back = c.download(test.id).toString(Charsets.UTF_8)
      out += if (back == stamp) DriveCheck("④ 봇으로 덮어쓰기", true, "덮어쓰고 다시 읽기까지 성공")
      else DriveCheck("④ 봇으로 덮어쓰기", false, "덮어쓴 내용이 다시 읽히지 않아요: ${back.take(40)}")
    } catch (e: Exception) {
      out += DriveCheck("④ 봇으로 덮어쓰기", false, e.message ?: "실패")
    }
    out
  }

  companion object {
    const val TEST_FILE_NAME = "moneyflow-drive-test.txt"
  }
}
