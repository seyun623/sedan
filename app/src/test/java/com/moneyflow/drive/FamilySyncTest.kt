package com.moneyflow.drive

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.AutoBackupRepository
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.DriveRepository
import com.moneyflow.data.repository.FamilyRepository
import com.moneyflow.data.repository.FamilySyncRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class FamilySyncTest {
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 24, 12, 0))
  private val keyJson: String = run {
    val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val pem = "-----BEGIN PRIVATE KEY-----\n" + android.util.Base64.encodeToString(kp.private.encoded, android.util.Base64.DEFAULT) + "-----END PRIVATE KEY-----\n"
    JSONObject().put("type", "service_account").put("private_key", pem)
      .put("client_email", "moneyflow-bot@p.iam.gserviceaccount.com").put("token_uri", "https://oauth2.googleapis.com/token").toString()
  }

  /** The shared "머니플로우 가족" folder as the bot sees it. */
  class FakeFolder : DriveTransport {
    class F(val name: String, var bytes: ByteArray, var version: Int = 1)
    val files = linkedMapOf<String, F>()
    var offline = false
    var unshared = false
    fun add(name: String, bytes: ByteArray): String = "id${files.size + 1}".also { files[it] = F(name, bytes) }
    fun byName(name: String) = files.values.single { it.name == name }

    override fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
      if (offline) throw java.net.UnknownHostException("www.googleapis.com")
      fun json(s: String) = 200 to s.toByteArray()
      return when {
        url.startsWith("https://oauth2.googleapis.com/token") -> json("""{"access_token":"tok","expires_in":3600}""")
        url.contains("/drive/v3/files?") && url.contains("+in+parents") -> json(JSONObject().put("files", JSONArray(files.map { (id, f) ->
          JSONObject().put("id", id).put("name", f.name).put("mimeType", "application/octet-stream").put("modifiedTime", "v${f.version}")
        })).toString())
        url.contains("/drive/v3/files?") -> json("""{"files":[{"id":"folder1","name":"머니플로우 가족","mimeType":"application/vnd.google-apps.folder"}]}""")
        url.contains("/files/folder1?") -> if (unshared) 404 to "{}".toByteArray() else json("""{"id":"folder1"}""")
        method == "GET" && url.contains("alt=media") -> 200 to files.getValue(url.substringAfter("/files/").substringBefore("?")).bytes
        method == "PATCH" -> {
          val f = files.getValue(url.substringAfter("/files/").substringBefore("?"))
          f.bytes = body!!; f.version++
          json("{}")
        }
        else -> 404 to "{}".toByteArray()
      }
    }
  }

  /** One family phone: its own database, settings and Drive connection. */
  inner class Phone(name: String) {
    val db: AppDatabase = inMemoryDb()
    val settings: SettingsRepository = testSettingsRepository()
    val ledger = LedgerRepository(db, clock)
    val backups = BackupRepository(db, settings, clock)
    val family = FamilyRepository(db, backups, settings, clock)
    val drive = DriveRepository(settings, PlainSecretStore, clock).also { d -> d.clientFactory = { DriveClient(it, folder) } }
    val sync = FamilySyncRepository(drive, backups, family, settings, PlainSecretStore, clock)
    var account = 0L

    init {
      runBlocking {
        account = ledger.addAccount(bank("$name 통장"))
        drive.importKey(keyJson)
        drive.chooseFolder(drive.sharedFolders().single())
        sync.setup(name, "family12")
      }
    }

    /** "내 가족 파일 만들기": the phone saves the first file with the system save screen. */
    suspend fun createFile(): String {
      val name = FamilySyncRepository.fileNameFor(settings.current().familySyncName!!)
      folder.add(name, sync.familyFileBytes())
      sync.fileCreated(name, sync.dataGeneration())
      return name
    }

    suspend fun spend(amount: Long, memo: String) {
      ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = amount, category = "식비", accountId = account, timestamp = clock.nowMillis(), memo = memo))
      sync.markDirty()
    }

    suspend fun familyExpense() = family.month(YearMonth.of(2026, 11)).first().totalExpense
  }

  private val folder = FakeFolder()
  private lateinit var me: Phone
  private lateinit var wife: Phone

  @Before fun setUp() { me = Phone("세윤"); wife = Phone("아내") }
  @After fun tearDown() { me.db.close(); wife.db.close() }

  @Test fun familyFilesAreExchangedThroughTheFolder() = runBlocking {
    assertNull("not set up until the file exists", me.sync.sync())
    me.spend(30_000, "점심")
    wife.spend(70_000, "장보기")
    me.createFile()
    wife.createFile()

    val first = me.sync.sync()!!
    assertEquals(listOf("아내"), first.loaded)
    assertTrue(first.problems.isEmpty())
    assertEquals(100_000L, me.familyExpense())
    assertEquals("아내", me.family.members.first().single().name)

    // Nothing changed: no upload, nothing loaded again.
    val again = me.sync.sync()!!
    assertFalse(again.uploaded)
    assertTrue(again.loaded.isEmpty())

    // The wife spends more; her phone uploads, mine picks it up. Memos come along.
    wife.spend(5_000, "커피")
    val w = wife.sync.sync()!!
    assertTrue(w.uploaded)
    assertEquals(listOf("세윤"), w.loaded)
    assertEquals(listOf("아내"), me.sync.sync()!!.loaded)
    assertEquals(105_000L, me.familyExpense())
    assertEquals(1, me.family.members.first().size)
    assertTrue(me.db.familyDao().observeBetween(0, Long.MAX_VALUE).first().any { it.memo == "커피" })
    assertNull(me.settings.current().familySyncError)
  }

  @Test fun problemsAreExplained() = runBlocking {
    me.createFile()
    // A family member with a different password.
    folder.add(FamilySyncRepository.fileNameFor("동생"), BackupRepository(inMemoryDb(), testSettingsRepository(), clock).createBackup("other!!".toCharArray(), "2.5.0"))
    val r = me.sync.sync(forceUpload = true)!!
    assertTrue(r.uploaded)
    assertTrue(r.problems.single().contains("가족 비밀번호"))
    assertNotNull(me.settings.current().familySyncError)

    // My file was removed from the folder.
    val myFile = me.settings.current().familySyncFileName
    folder.files.entries.removeIf { it.value.name == myFile }
    assertTrue(me.sync.sync()!!.problems.first().contains("찾지 못했어요"))

    // Offline: thrown for a retry, not recorded as a problem.
    folder.offline = true
    val before = me.settings.current().familySyncError
    try { me.sync.sync(); throw AssertionError("expected IOException") } catch (e: java.io.IOException) { }
    assertEquals(before, me.settings.current().familySyncError)
  }

  @Test fun aChangeDuringUploadIsNotLost() = runBlocking {
    me.createFile(); wife.createFile()
    me.spend(1_000, "a")
    // The data version is read before the snapshot; a change after it stays "to upload".
    val (genBefore, _) = me.settings.familySyncGenerations()
    me.settings.setFamilySyncResult(clock.nowMillis(), clock.nowMillis(), genBefore - 1, null)
    me.sync.markDirty()
    val (gen, up) = me.settings.familySyncGenerations()
    assertTrue(gen != up)
    assertTrue(me.sync.sync()!!.uploaded)
    assertFalse(me.sync.sync()!!.uploaded)
  }

  @Test fun oneBadFileDoesNotStopTheOthers() = runBlocking {
    me.createFile(); wife.createFile()
    // A file from a newer app version (database version 99) comes first in the listing.
    val future = org.json.JSONObject().put("format", "moneyflow-backup").put("formatVersion", 1).put("dbVersion", 99)
      .put("appVersion", "9.0.0").put("createdAt", 1L).put("accounts", org.json.JSONArray()).put("transactions", org.json.JSONArray())
    folder.add(FamilySyncRepository.fileNameFor("미래"), com.moneyflow.domain.backup.BackupCrypto.encrypt(future.toString().toByteArray(), "family12".toCharArray()))
    wife.spend(70_000, "장보기")
    wife.sync.sync()
    val r = me.sync.sync()!!
    assertEquals(listOf("아내"), r.loaded)
    assertTrue(r.problems.toString(), r.problems.any { it.contains("미래") })
  }

  @Test fun unsharedFolderIsReportedWithoutForgettingFiles() = runBlocking {
    me.createFile(); wife.createFile()
    me.sync.sync()
    folder.unshared = true
    try { me.sync.sync(); throw AssertionError("expected an error") } catch (e: DriveException) { assertTrue(e.message!!.contains("편집자")) }
    folder.unshared = false
    // Nothing is loaded again: the seen list was kept.
    assertTrue(me.sync.sync()!!.loaded.isEmpty())
  }

  @Test fun anOlderCopyDoesNotReplaceNewerRecords() = runBlocking {
    me.createFile()
    wife.spend(10_000, "old")
    val old = wife.sync.familyFileBytes()
    clock.set(java.time.LocalDateTime.of(2026, 11, 24, 13, 0))
    wife.spend(20_000, "new")
    wife.createFile()
    me.sync.sync()
    assertEquals(30_000L, me.familyExpense())
    clock.set(java.time.LocalDateTime.of(2026, 11, 24, 14, 0))
    folder.add(FamilySyncRepository.fileNameFor("아내 사본"), old)
    me.sync.sync()
    assertEquals(30_000L, me.familyExpense())
  }

  @Test fun memberNamesComeFromFileNames() {
    assertEquals("moneyflow-family-엄마.mfbackup", FamilySyncRepository.fileNameFor(" 엄마 "))
    assertEquals("엄마", FamilySyncRepository.memberName("moneyflow-family-엄마 (1).mfbackup"))
    assertTrue(FamilySyncRepository.isFamilyFile("moneyflow-family-a.mfbackup"))
    assertFalse(FamilySyncRepository.isFamilyFile("moneyflow-backup-a-1234.mfbackup"))
    assertEquals("moneyflow-backup-세윤-ab12.mfbackup", AutoBackupRepository.driveFileNameFor("세윤", "ab12-cd34"))
  }

  @Test fun autoBackupOverwritesTheDriveFile() = runBlocking {
    val auto = AutoBackupRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext(), me.backups, me.settings, PlainSecretStore, clock, me.drive)
    val name = AutoBackupRepository.driveFileNameFor("세윤", me.settings.installId())
    folder.add(name, me.backups.createBackup("backup1".toCharArray(), "2.5.0"))
    auto.enableDrive(name, "backup1", intervalDays = 1)
    me.spend(12_000, "택시")
    assertEquals(name, auto.runIfDue(force = true))
    val content = me.backups.readBackup(folder.byName(name).bytes, "backup1".toCharArray())
    assertTrue(content.transactions.any { it.memo == "택시" })
    assertEquals(2, folder.byName(name).version)

    folder.offline = true
    try { auto.runIfDue(force = true); throw AssertionError() } catch (e: java.io.IOException) { }
    assertNull("offline is not an error", me.settings.current().autoBackupError)
    folder.offline = false
    folder.files.clear()
    try { auto.runIfDue(force = true); throw AssertionError() } catch (e: IllegalStateException) { }
    assertTrue(me.settings.current().autoBackupError!!.contains("찾지 못했어요"))
  }
}
