package com.moneyflow.data

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.repository.AutoBackupRepository
import com.moneyflow.data.repository.BackupDestination
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class AutoBackupRepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var settings: SettingsRepository
  private lateinit var backups: BackupRepository
  private lateinit var repo: AutoBackupRepository
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 23, 9, 30))
  private val folder = FakeFolder()

  class FakeFolder : BackupDestination {
    val files = linkedMapOf<String, ByteArray>()
    var failWrite = false
    override fun list() = files.keys.toList()
    override fun write(name: String, bytes: ByteArray) { if (failWrite) throw IllegalStateException("저장 공간이 부족해요."); files[name] = bytes }
    override fun delete(name: String) { files.remove(name) }
  }

  @Before fun setUp() = runBlocking {
    db = inMemoryDb()
    LedgerRepository(db, clock).addAccount(bank())
    settings = testSettingsRepository()
    backups = BackupRepository(db, settings, clock)
    repo = AutoBackupRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext(), backups, settings, PlainSecretStore, clock)
    repo.destinationFactory = { folder }
    Unit
  }

  @After fun tearDown() = db.close()

  @Test fun notEnabledDoesNothing() = runBlocking {
    assertNull(repo.runIfDue(force = true))
    assertTrue(folder.files.isEmpty())
  }

  @Test fun writesEncryptedBackupWhenDueAndKeepsNewestFive() = runBlocking {
    folder.files["my-notes.txt"] = ByteArray(1)
    repo.enable("content://tree", "family-pw", 7)
    val first = repo.runIfDue()
    assertEquals("moneyflow-auto-20261123-0930.mfbackup", first)
    // The stored file opens with the password.
    assertEquals(1, backups.readBackup(folder.files[first]!!, "family-pw".toCharArray()).summary.accountCount)
    assertEquals(clock.nowMillis(), settings.current().lastBackupAt)

    clock.advanceMillis(2L * 24 * 3_600_000)
    assertNull(repo.runIfDue()) // not due yet
    repeat(6) {
      clock.advanceMillis(7L * 24 * 3_600_000)
      repo.runIfDue()
    }
    val autos = folder.files.keys.filter { it.startsWith(AutoBackupRepository.PREFIX) }
    assertEquals(AutoBackupRepository.KEEP, autos.size)
    assertTrue("other files are untouched", "my-notes.txt" in folder.files)
  }

  @Test fun failureIsRecordedThenClearedOnSuccess() = runBlocking {
    repo.enable("content://tree", "pw12", 1)
    folder.failWrite = true
    try { repo.runIfDue(force = true); fail() } catch (e: IllegalStateException) { assertEquals("저장 공간이 부족해요.", e.message) }
    assertEquals("저장 공간이 부족해요.", settings.current().autoBackupError)
    folder.failWrite = false
    repo.runIfDue(force = true)
    assertNull(settings.current().autoBackupError)
  }

  @Test fun pruneOnlyOldAutoFiles() {
    val names = (1..7).map { "moneyflow-auto-2026110$it-0900.mfbackup" } + "moneyflow-backup-20261101.mfbackup"
    assertEquals(listOf("moneyflow-auto-20261102-0900.mfbackup", "moneyflow-auto-20261101-0900.mfbackup"), AutoBackupRepository.prune(names))
  }
}
