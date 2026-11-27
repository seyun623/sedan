package com.moneyflow.data

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.BackupFormatException
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.RecurringRepository
import com.moneyflow.data.repository.SampleDataSeeder
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.backup.BackupCrypto
import com.moneyflow.domain.backup.NotABackupFileException
import com.moneyflow.domain.backup.WrongPasswordException
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
  private val clock = FakeClock()
  private val pw = "가족비밀1234".toCharArray()
  private lateinit var source: AppDatabase
  private lateinit var target: AppDatabase
  private lateinit var settings: SettingsRepository

  @Before fun setUp() { source = inMemoryDb(); target = inMemoryDb(); settings = testSettingsRepository() }
  @After fun tearDown() { source.close(); target.close() }

  private suspend fun seedSource(): LedgerRepository {
    val ledger = LedgerRepository(source, clock)
    val budgets = BudgetRepository(source)
    val recurring = RecurringRepository(source, ledger, clock)
    SampleDataSeeder(ledger, budgets, recurring, clock).seed()
    // a goal deposit and a transfer so goal/transfer relations are covered
    val goal = source.savingGoalDao().observeAll().first().first()
    val accounts = ledger.getAccounts()
    ledger.depositToGoal(goal, accounts.first().id, 50_000)
    ledger.transfer(accounts[0], accounts[1], 10_000, "이체")
    val cardId = accounts.first { it.isDebt }.id
    source.cardDao().upsertProfile(com.moneyflow.data.model.CardProfileEntity(cardId, 300_000, 13, "주거/통신", "관리비"))
    source.cardDao().insertBenefit(com.moneyflow.data.model.CardBenefitEntity(accountId = cardId, title = "카페 10%", category = "카페/간식", value = 1000, monthlyCap = 10_000))
    return ledger
  }

  private suspend fun snapshot(db: AppDatabase) = with(db.backupDao()) {
    listOf(accounts(), transactions(), budgets(), categoryBudgets().sortedBy { it.category }, goals(), recurring(),
      db.cardDao().getProfiles(), db.cardDao().getBenefits())
  }

  @Test fun cryptoRoundTripAndWrongPassword() {
    val data = "머니플로우 백업 테스트".toByteArray()
    val enc = BackupCrypto.encrypt(data, pw)
    assertArrayEquals(data, BackupCrypto.decrypt(enc, pw))
    assertNotEquals(BackupCrypto.encrypt(data, pw).toList(), enc.toList()) // random salt/iv
    runCatching { BackupCrypto.decrypt(enc, "틀린비번".toCharArray()) }.also { assertTrue(it.exceptionOrNull() is WrongPasswordException) }
  }

  @Test fun tamperedOrForeignFilesAreRejected() {
    val enc = BackupCrypto.encrypt("x".repeat(100).toByteArray(), pw)
    val tampered = enc.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 1).toByte() }
    assertTrue(runCatching { BackupCrypto.decrypt(tampered, pw) }.exceptionOrNull() is WrongPasswordException)
    val headerTampered = enc.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() } // salt changed
    assertTrue(runCatching { BackupCrypto.decrypt(headerTampered, pw) }.exceptionOrNull() is WrongPasswordException)
    assertTrue(runCatching { BackupCrypto.decrypt("not a backup file at all, just text".toByteArray(), pw) }.exceptionOrNull() is NotABackupFileException)
  }

  @Test fun backupThenRestoreReproducesEverythingExactly() = runBlocking {
    seedSource()
    val file = BackupRepository(source, settings, clock).createBackup(pw, "1.0.0")
    assertEquals(clock.nowMillis(), settings.current().lastBackupAt)

    // target already has unrelated data that must be replaced
    LedgerRepository(target, clock).addAccount(bank("지울 계좌", 1))

    val repo = BackupRepository(target, settings, clock)
    val content = repo.readBackup(file, pw)
    assertEquals(3, content.summary.accountCount)
    repo.restore(content)

    assertEquals(snapshot(source), snapshot(target))
    // restored DB keeps working normally: balances still respond to new transactions
    val ledger = LedgerRepository(target, clock)
    val acc = ledger.getAccounts().first { !it.isDebt }
    ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 1_000, category = "식비", accountId = acc.id, timestamp = clock.nowMillis(), memo = ""))
    assertEquals(acc.balance - 1_000, target.accountDao().getById(acc.id)!!.balance)
    // budget carried over
    assertEquals(1_500_000L, BudgetRepository(target).observeBudget(YearMonth.of(2026, 11)).first().total)
  }

  @Test fun categoryRulesAreBackedUp() = runBlocking {
    seedSource()
    source.ruleDao().upsert(com.moneyflow.data.model.CategoryRuleEntity(keyword = "스타벅스", category = "카페/간식"))
    source.ruleDao().upsert(com.moneyflow.data.model.CategoryRuleEntity(keyword = "광고", action = com.moneyflow.data.model.RuleAction.IGNORE))
    target.ruleDao().upsert(com.moneyflow.data.model.CategoryRuleEntity(keyword = "지울 규칙", category = "식비"))
    val file = BackupRepository(source, settings, clock).createBackup(pw, "1.6.0")
    val repo = BackupRepository(target, settings, clock)
    repo.restore(repo.readBackup(file, pw))
    assertEquals(source.ruleDao().getAll().sortedBy { it.id }, target.ruleDao().getAll().sortedBy { it.id })
  }

  @Test fun restoreClearsDefaultCaptureAccountThatNoLongerExists() = runBlocking {
    seedSource()
    val file = BackupRepository(source, settings, clock).createBackup(pw, "1.0.0")
    settings.setDefaultCaptureAccount(999)
    val repo = BackupRepository(target, settings, clock)
    repo.restore(repo.readBackup(file, pw))
    assertEquals(null, settings.current().autoCaptureDefaultAccountId)
  }

  @Test fun backupFromNewerAppVersionIsRefused() {
    val json = """{"format":"moneyflow-backup","formatVersion":99,"dbVersion":3,"createdAt":0,
      "accounts":[],"transactions":[],"budgets":[],"categoryBudgets":[],"goals":[],"recurring":[]}"""
    val file = BackupCrypto.encrypt(json.toByteArray(), pw)
    val e = runCatching { BackupRepository(target, settings, clock).readBackup(file, pw) }.exceptionOrNull()
    assertTrue(e is BackupFormatException)
  }

  @Test fun inconsistentBackupIsRefusedBeforeTouchingData() = runBlocking {
    LedgerRepository(target, clock).addAccount(bank("기존", 5))
    val json = """{"format":"moneyflow-backup","formatVersion":1,"dbVersion":3,"createdAt":0,
      "accounts":[],"transactions":[{"id":1,"type":"EXPENSE","amount":1,"category":"식비","accountId":7,"timestamp":0}],
      "budgets":[],"categoryBudgets":[],"goals":[],"recurring":[]}"""
    val file = BackupCrypto.encrypt(json.toByteArray(), pw)
    assertTrue(runCatching { BackupRepository(target, settings, clock).readBackup(file, pw) }.exceptionOrNull() is BackupFormatException)
    assertEquals(1, target.accountDao().count())
  }
}
