package com.moneyflow.ui

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.inMemoryDb
import com.moneyflow.ui.common.SelectedMonthStore
import com.moneyflow.ui.common.UserMessage
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.ledger.LedgerUiState
import com.moneyflow.ui.ledger.LedgerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.LEGACY)
class LedgerViewModelTest {
  private lateinit var db: AppDatabase
  private lateinit var ledger: LedgerRepository
  private lateinit var vm: LedgerViewModel
  private lateinit var bus: UserMessageBus
  private val clock = FakeClock(LocalDateTime.of(2026, 11, 21, 12, 0))
  private val scope = CoroutineScope(Dispatchers.Default)

  @Before fun setUp() = runBlocking {
    Dispatchers.setMain(Dispatchers.Unconfined)
    db = inMemoryDb()
    ledger = LedgerRepository(db, clock)
    bus = UserMessageBus()
    vm = LedgerViewModel(ledger, SelectedMonthStore(clock), MonthlyStatsCalculator(clock.zone), bus, clock)
    val a = ledger.addAccount(bank(balance = 1_000_000))
    fun t(type: TransactionType, amount: Long, cat: String, memo: String, at: LocalDateTime) =
      TransactionEntity(type = type, amount = amount, category = cat, accountId = a, timestamp = clock.millis(at), memo = memo)
    ledger.addTransaction(t(TransactionType.EXPENSE, 5_000, "카페/간식", "스타벅스", LocalDateTime.of(2026, 11, 20, 9, 0)))
    ledger.addTransaction(t(TransactionType.EXPENSE, 12_000, "식비", "점심", LocalDateTime.of(2026, 11, 21, 12, 0)))
    ledger.addTransaction(t(TransactionType.INCOME, 50_000, "용돈", "용돈", LocalDateTime.of(2026, 11, 3, 9, 0)))
    ledger.addTransaction(t(TransactionType.EXPENSE, 9_999, "식비", "지난달", LocalDateTime.of(2026, 10, 30, 9, 0)))
    Unit
  }

  @After fun tearDown() { scope.cancel(); db.close(); Dispatchers.resetMain() }

  private suspend fun awaitState(predicate: (LedgerUiState) -> Boolean): LedgerUiState =
    withTimeout(5_000) { vm.uiState.first { !it.isLoading && predicate(it) } }

  @Test fun showsSelectedMonthOnlyWithTotals() = runBlocking {
    val s = awaitState { it.transactions.size == 3 }
    assertEquals(17_000, s.monthlyExpense)
    assertEquals(50_000, s.monthlyIncome)
    vm.previousMonth()
    assertEquals(9_999, awaitState { it.month.monthValue == 10 }.monthlyExpense)
  }

  @Test fun typeCategoryAndDebouncedSearchFilters() = runBlocking {
    awaitState { it.transactions.size == 3 }
    vm.setType(TransactionType.EXPENSE)
    awaitState { it.transactions.size == 2 }
    vm.setCategory("식비")
    awaitState { it.transactions.map { t -> t.memo } == listOf("점심") }
    vm.resetFilters()
    awaitState { it.transactions.size == 3 }
    vm.setQuery("스타")
    val s = awaitState { it.transactions.size == 1 }
    assertEquals("스타벅스", s.transactions.single().memo)
    assertEquals(5_000, s.filteredExpense)
  }

  @Test fun deleteOffersUndoThatRestoresTheTransaction() = runBlocking {
    val state = awaitState { it.transactions.size == 3 }
    var message: UserMessage? = null
    val job = scope.launch { message = bus.messages.first() }
    vm.delete(state.transactions.first { it.memo == "점심" })
    awaitState { it.transactions.size == 2 }
    withTimeout(5_000) { while (message == null) kotlinx.coroutines.delay(10) }
    job.cancel()
    assertNotNull(message!!.onAction)
    message!!.onAction!!.invoke()
    awaitState { it.transactions.size == 3 }
    assertEquals(1_000_000 + 50_000 - 17_000 - 9_999L, db.accountDao().getById(1)!!.balance)
  }
}
