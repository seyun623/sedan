package com.moneyflow.family

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.BackupRepository
import com.moneyflow.data.repository.FamilyImportException
import com.moneyflow.data.repository.FamilyRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class FamilyRepositoryTest {
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 24, 12, 0))
  private lateinit var mine: AppDatabase
  private lateinit var spouse: AppDatabase
  private val mySettings = testSettingsRepository()
  private val spouseSettings = testSettingsRepository()
  private lateinit var family: FamilyRepository
  private val pw = "family1".toCharArray()

  @Before fun setUp() = runBlocking {
    mine = inMemoryDb()
    spouse = inMemoryDb()
    family = FamilyRepository(mine, BackupRepository(mine, mySettings, clock), mySettings, clock)
    val myLedger = LedgerRepository(mine, clock)
    val a = myLedger.addAccount(bank())
    myLedger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 30_000, category = "식비", accountId = a, timestamp = clock.nowMillis(), memo = "점심"))
    val sp = LedgerRepository(spouse, clock)
    val b = sp.addAccount(bank("배우자 통장"))
    val c = sp.addAccount(bank("배우자 적금"))
    sp.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 70_000, category = "식비", accountId = b, timestamp = clock.nowMillis(), memo = "장보기"))
    sp.addTransaction(TransactionEntity(type = TransactionType.INCOME, amount = 2_000_000, category = "급여", accountId = b, timestamp = clock.nowMillis(), memo = "급여"))
    sp.addTransaction(TransactionEntity(type = TransactionType.TRANSFER, amount = 500_000, category = "계좌이체", accountId = b, toAccountId = c, timestamp = clock.nowMillis(), memo = "적금"))
    Unit
  }

  @After fun tearDown() { mine.close(); spouse.close() }

  @Test fun importsOthersBackupAndCombinesTheMonth() = runBlocking {
    val file = BackupRepository(spouse, spouseSettings, clock).createBackup(pw, "2.0.0")
    val r = family.import(file, pw, "남편")
    assertFalse(r.updated)
    assertEquals(2, r.transactionCount) // the transfer is left out
    val m = family.month(YearMonth.of(2026, 11)).first()
    assertEquals(listOf("나", "남편"), m.people.map { it.name })
    assertEquals(100_000, m.totalExpense)
    assertEquals(2_000_000, m.totalIncome)
    with(m.categories.first()) {
      assertEquals("식비", category); assertEquals(100_000L, total)
      assertEquals(listOf("남편" to 70_000L, "나" to 30_000L), byPerson)
    }
    family.setBudget(90_000)
    assertEquals(90_000L, family.month(YearMonth.of(2026, 11)).first().budget)

    // A newer backup of the same phone replaces the copy instead of adding a person.
    LedgerRepository(spouse, clock).addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 5_000, category = "카페/간식", accountId = 1, timestamp = clock.nowMillis(), memo = "커피"))
    val again = family.import(BackupRepository(spouse, spouseSettings, clock).createBackup(pw, "2.0.0"), pw, "")
    assertTrue(again.updated)
    assertEquals("남편", again.member.name)
    val m2 = family.month(YearMonth.of(2026, 11)).first()
    assertEquals(2, m2.people.size)
    assertEquals(105_000, m2.totalExpense)

    family.delete(family.members.first().single())
    assertEquals(30_000, family.month(YearMonth.of(2026, 11)).first().totalExpense)
  }

  @Test fun sameNamedGoalsAreCombined() = runBlocking {
    fun goal(title: String, target: Long, current: Long) =
      com.moneyflow.data.model.SavingGoalEntity(title = title, targetAmount = target, currentAmount = current, targetDate = clock.nowMillis())
    LedgerRepository(mine, clock).addGoal(goal("가족 여행", 3_000_000, 500_000))
    LedgerRepository(mine, clock).addGoal(goal("내 노트북", 2_000_000, 100_000))
    LedgerRepository(spouse, clock).addGoal(goal("가족여행", 4_000_000, 700_000))
    assertTrue(family.sharedGoals.first().isEmpty()) // nobody loaded yet
    family.import(BackupRepository(spouse, spouseSettings, clock).createBackup(pw, "2.3.0"), pw, "아내")
    val g = family.sharedGoals.first().single()
    assertEquals("가족 여행", g.title)
    assertEquals(4_000_000, g.target)
    assertEquals(1_200_000, g.saved)
    assertEquals(listOf("아내" to 700_000L, "나" to 500_000L), g.byPerson)
    family.delete(family.members.first().single())
    assertTrue(family.sharedGoals.first().isEmpty())
  }

  @Test fun ownBackupIsRejected() = runBlocking {
    val own = BackupRepository(mine, mySettings, clock).createBackup(pw, "2.0.0")
    try { family.import(own, pw, "나"); fail() } catch (e: FamilyImportException) { assertTrue(e.message!!.contains("이 휴대폰")) }
  }
}
