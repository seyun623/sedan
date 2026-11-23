package com.moneyflow.card

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.BenefitKind
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.data.repository.CardRepository
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.card
import com.moneyflow.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class CardRepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var repo: CardRepository
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 23, 12, 0))
  private var a = 0L
  private var b = 0L

  @Before fun setUp() = runBlocking {
    db = inMemoryDb()
    val ledger = LedgerRepository(db, clock)
    a = ledger.addAccount(card("신한카드"))
    b = ledger.addAccount(card("현대카드"))
    repo = CardRepository(db, clock)
    Unit
  }

  @After fun tearDown() = db.close()

  private fun draft(title: String, value: Long, minPerf: Long = 0, category: String? = "카페/간식") =
    CardBenefitEntity(accountId = 0, title = title, category = category, kind = BenefitKind.PERCENT, value = value, minPerformance = minPerf)

  @Test fun addBenefitsSkipsSameTitlesAndSetsTarget() = runBlocking {
    repo.saveBenefit(draft("카페 10%", 1000).copy(accountId = a))
    val r = repo.addBenefits(a, listOf(draft("카페 10 %", 1000), draft("영화 5%", 500, 400_000, "문화/여가"), draft("주유 3%", 300, 300_000, "교통")), setTargetIfMissing = true)
    assertEquals(2, r.added)
    assertEquals(1, r.skipped)
    assertEquals(300_000L, r.targetSet)
    assertEquals(3, repo.observeBenefits(a).first().size)
    assertEquals(300_000L, repo.observeProfile(a).first()?.performanceTarget)

    // An existing profile is never overwritten.
    repo.saveProfile(CardProfileEntity(b, 500_000))
    assertNull(repo.addBenefits(b, listOf(draft("x", 100, 200_000)), setTargetIfMissing = true).targetSet)
    assertEquals(500_000L, repo.observeProfile(b).first()?.performanceTarget)
  }

  @Test fun cardsWithOnlyBenefitsGetStatusAndGuide() = runBlocking {
    repo.saveBenefit(draft("카페 10%", 1000).copy(accountId = a))
    repo.saveBenefit(draft("카페 5%", 500).copy(accountId = b))
    val statuses = repo.statuses.first()
    assertEquals(setOf(a, b), statuses.map { it.accountId }.toSet())
    assertEquals(0L, statuses.first().target)
    val guide = repo.guide.first()
    assertEquals(1, guide.size)
    assertEquals(a, guide[0].best.accountId)
    assertEquals(1_000L, guide[0].best.expected) // 10% of the 10,000원 default
    assertEquals(b, guide[0].runnerUp?.accountId)
  }
}
