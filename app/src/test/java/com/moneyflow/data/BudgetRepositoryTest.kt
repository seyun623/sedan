package com.moneyflow.data

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.repository.BudgetRepository
import com.moneyflow.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class BudgetRepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var repo: BudgetRepository

  @Before fun setUp() { db = inMemoryDb(); repo = BudgetRepository(db) }
  @After fun tearDown() = db.close()

  @Test fun savingTwiceKeepsOneRowAndLatestValues() = runBlocking {
    val nov = YearMonth.of(2026, 11)
    repo.saveBudget(nov, 1_000_000, mapOf("식비" to 300_000, "쇼핑" to 100_000))
    repo.saveBudget(nov, 1_200_000, mapOf("식비" to 400_000))
    val b = repo.observeBudget(nov).first()
    assertEquals(1_200_000, b.total)
    assertEquals(mapOf("식비" to 400_000L), b.categoryLimits)
    assertNull(b.inheritedFrom)
    assertEquals(1, db.budgetDao().observeAllBudgets().first().size)
  }

  @Test fun laterMonthsInheritButEarlierMonthsDoNot() = runBlocking {
    repo.saveBudget(YearMonth.of(2026, 10), 900_000, emptyMap())
    val dec = repo.observeBudget(YearMonth.of(2026, 12)).first()
    assertEquals(900_000, dec.total)
    assertEquals(YearMonth.of(2026, 10), dec.inheritedFrom)
    assertFalse(repo.observeBudget(YearMonth.of(2026, 9)).first().isSet)
  }
}
