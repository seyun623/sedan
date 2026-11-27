package com.moneyflow.data

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.LedgerResult
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.bank
import com.moneyflow.testing.card
import com.moneyflow.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LedgerRepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var ledger: LedgerRepository
  private val clock = FakeClock()

  @Before fun setUp() { db = inMemoryDb(); ledger = LedgerRepository(db, clock) }
  @After fun tearDown() = db.close()

  private suspend fun balance(id: Long) = db.accountDao().getById(id)!!.balance

  private fun tx(type: TransactionType, amount: Long, account: Long, to: Long? = null, category: String = "식비") =
    TransactionEntity(type = type, amount = amount, category = category, accountId = account, toAccountId = to, timestamp = clock.nowMillis(), memo = "")

  @Test fun expenseIncomeAndDeleteRestoreBalances() = runBlocking {
    val a = ledger.addAccount(bank(balance = 100_000))
    val id = ledger.addTransaction(tx(TransactionType.EXPENSE, 30_000, a))
    assertEquals(70_000, balance(a))
    ledger.addTransaction(tx(TransactionType.INCOME, 5_000, a))
    assertEquals(75_000, balance(a))
    val removed = ledger.deleteTransaction(ledger.getTransaction(id)!!)!!
    assertEquals(105_000, balance(a))
    ledger.restoreTransaction(removed) // undo
    assertEquals(75_000, balance(a))
  }

  @Test fun debtAccountSigns() = runBlocking {
    val bankId = ledger.addAccount(bank(balance = 1_000_000))
    val cardId = ledger.addAccount(card(owed = 0))
    ledger.addTransaction(tx(TransactionType.EXPENSE, 40_000, cardId)) // spend on card -> owe more
    assertEquals(40_000, balance(cardId))
    val bankAcc = db.accountDao().getById(bankId)!!
    val cardAcc = db.accountDao().getById(cardId)!!
    ledger.transfer(bankAcc, cardAcc, 40_000, "") // pay off the card
    assertEquals(0, balance(cardId))
    assertEquals(960_000, balance(bankId))
    val payment = db.transactionDao().observeAll().first().first { it.type == TransactionType.TRANSFER }
    assertEquals(FinanceCategories.CARD_PAYMENT, payment.category)
  }

  @Test fun updateMovesEffectBetweenAccounts() = runBlocking {
    val a = ledger.addAccount(bank("A", 100_000))
    val b = ledger.addAccount(bank("B", 100_000))
    val id = ledger.addTransaction(tx(TransactionType.EXPENSE, 10_000, a))
    val updated = ledger.getTransaction(id)!!.copy(accountId = b, amount = 25_000)
    assertEquals(LedgerResult.Ok, ledger.updateTransaction(updated))
    assertEquals(100_000, balance(a))
    assertEquals(75_000, balance(b))
    assertEquals("B", ledger.getTransaction(id)!!.accountName)
  }

  @Test fun invalidTransactionsAreRejectedWithoutSideEffects() = runBlocking {
    val a = ledger.addAccount(bank(balance = 100_000))
    runCatching { ledger.addTransaction(tx(TransactionType.EXPENSE, 0, a)) }
    runCatching { ledger.addTransaction(tx(TransactionType.EXPENSE, 1_000, 999)) }
    runCatching { ledger.addTransaction(tx(TransactionType.TRANSFER, 1_000, a, to = a)) }
    assertEquals(100_000, balance(a))
    assertEquals(0, db.transactionDao().count())
  }

  @Test fun renameUpdatesSnapshotsAndDebtFlagIsLockedOnceUsed() = runBlocking {
    val a = ledger.addAccount(bank("Old", 10_000))
    ledger.addTransaction(tx(TransactionType.EXPENSE, 1_000, a))
    val acc = db.accountDao().getById(a)!!
    assertEquals(LedgerResult.Ok, ledger.updateAccount(acc.copy(name = "New")))
    assertEquals("New", db.transactionDao().observeAll().first().single().accountName)
    assertTrue(ledger.updateAccount(acc.copy(name = "New", isDebt = true)) is LedgerResult.Rejected)
  }

  @Test fun deleteAccountRefusesWhenUsedAndCascadesWhenConfirmed() = runBlocking {
    val a = ledger.addAccount(bank("A", 100_000))
    val b = ledger.addAccount(bank("B", 100_000))
    ledger.transfer(db.accountDao().getById(a)!!, db.accountDao().getById(b)!!, 30_000, "")
    assertTrue(ledger.deleteAccount(a, deleteRelated = false) is LedgerResult.Rejected)
    db.cardDao().upsertProfile(com.moneyflow.data.model.CardProfileEntity(a, 100))
    db.cardDao().insertBenefit(com.moneyflow.data.model.CardBenefitEntity(accountId = a, title = "x", value = 100))
    assertEquals(LedgerResult.Ok, ledger.deleteAccount(a, deleteRelated = true))
    assertTrue(db.cardDao().getProfiles().isEmpty())
    assertTrue(db.cardDao().getBenefits().isEmpty())
    assertNull(db.accountDao().getById(a))
    assertEquals(100_000, balance(b)) // transfer into B was reverted
    assertEquals(0, db.transactionDao().count())
  }

  @Test fun goalDepositIsATransferAndDeletingItRevertsTheGoal() = runBlocking {
    val a = ledger.addAccount(bank(balance = 500_000))
    val goalId = ledger.addGoal(SavingGoalEntity(title = "여행", targetAmount = 1_000_000, currentAmount = 0, targetDate = 0))
    val goal = db.savingGoalDao().getById(goalId)!!
    val txId = ledger.depositToGoal(goal, a, 200_000)
    assertEquals(300_000, balance(a))
    assertEquals(200_000, db.savingGoalDao().getById(goalId)!!.currentAmount)
    ledger.deleteTransaction(ledger.getTransaction(txId)!!)
    assertEquals(500_000, balance(a))
    assertEquals(0, db.savingGoalDao().getById(goalId)!!.currentAmount)
  }

  @Test fun deletingGoalKeepsDepositHistory() = runBlocking {
    val a = ledger.addAccount(bank(balance = 500_000))
    val goalId = ledger.addGoal(SavingGoalEntity(title = "여행", targetAmount = 1_000_000, currentAmount = 0, targetDate = 0))
    val txId = ledger.depositToGoal(db.savingGoalDao().getById(goalId)!!, a, 100_000)
    ledger.deleteGoal(db.savingGoalDao().getById(goalId)!!)
    val kept = ledger.getTransaction(txId)!!
    assertNull(kept.goalId)
    assertEquals(400_000, balance(a))
  }

  @Test fun editingGoalDepositNeverCreditsAnotherAccount() = runBlocking {
    val a = ledger.addAccount(bank("A", 500_000))
    val b = ledger.addAccount(bank("B", 100_000))
    val goalId = ledger.addGoal(SavingGoalEntity(title = "여행", targetAmount = 1_000_000, currentAmount = 0, targetDate = 0))
    val txId = ledger.depositToGoal(db.savingGoalDao().getById(goalId)!!, a, 50_000)
    // An edit that (wrongly) tries to attach a receiving account
    val edited = ledger.getTransaction(txId)!!.copy(memo = "메모 수정", toAccountId = b)
    assertEquals(LedgerResult.Ok, ledger.updateTransaction(edited))
    assertEquals(450_000, balance(a))
    assertEquals(100_000, balance(b))
    assertEquals(50_000, db.savingGoalDao().getById(goalId)!!.currentAmount)
    assertNull(ledger.getTransaction(txId)!!.toAccountId)
  }

  @Test fun accountEditKeepsBalanceChangedWhileFormWasOpen() = runBlocking {
    val a = ledger.addAccount(bank("A", 100_000))
    val shown = db.accountDao().getById(a)!!          // form opened, shows 100,000
    ledger.addTransaction(tx(TransactionType.EXPENSE, 30_000, a)) // captured while form open
    ledger.updateAccount(shown.copy(name = "새 이름"), balanceShownWhenEditing = shown.balance)
    assertEquals(70_000, balance(a))
    assertEquals("새 이름", db.accountDao().getById(a)!!.name)
    // an explicit balance correction is still applied
    ledger.updateAccount(db.accountDao().getById(a)!!.copy(balance = 65_000), balanceShownWhenEditing = 70_000)
    assertEquals(65_000, balance(a))
  }
}
