package com.moneyflow.data.repository

import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthKeys
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional demo data, loaded only when the user asks for it (Settings > 샘플 데이터) and only
 * into an empty ledger. Dates are relative to today; balances go through the ledger so they stay
 * consistent with the transactions.
 */
@Singleton
class SampleDataSeeder @Inject constructor(
  private val ledger: LedgerRepository,
  private val budgets: BudgetRepository,
  private val recurring: RecurringRepository,
  private val clock: AppClock
) {
  suspend fun canSeed(): Boolean = ledger.getAccounts().isEmpty()

  suspend fun seed() {
    if (!canSeed()) return
    val main = AccountEntity(name = "생활비 통장", bankName = "토스뱅크", accountType = AccountType.BANK, balance = 2_400_000, colorHex = 0xFF0064FF, isFavorite = true)
    val savings = AccountEntity(name = "비상금 통장", bankName = "카카오뱅크", accountType = AccountType.SAVINGS, balance = 5_000_000, colorHex = 0xFFFEC600)
    val card = AccountEntity(name = "현대카드", bankName = "현대카드", accountType = AccountType.CREDIT_CARD, balance = 0, colorHex = 0xFF191F28, isDebt = true)
    val mainId = ledger.addAccount(main)
    ledger.addAccount(savings)
    val cardId = ledger.addAccount(card)

    val today = clock.today()
    fun at(daysAgo: Long, hour: Int) =
      today.minusDays(daysAgo).atTime(LocalTime.of(hour, 0)).atZone(clock.zone).toInstant().toEpochMilli()

    listOf(
      TransactionEntity(type = TransactionType.EXPENSE, amount = 5_600, category = "카페/간식", accountId = cardId, timestamp = at(0, 9), memo = "스타벅스", paymentMethod = "카드"),
      TransactionEntity(type = TransactionType.EXPENSE, amount = 12_000, category = "식비", accountId = cardId, timestamp = at(1, 12), memo = "점심 식사", paymentMethod = "카드"),
      TransactionEntity(type = TransactionType.EXPENSE, amount = 1_450, category = "교통", accountId = mainId, timestamp = at(1, 8), memo = "지하철", paymentMethod = "체크카드"),
      TransactionEntity(type = TransactionType.EXPENSE, amount = 48_300, category = "생활/마트", accountId = cardId, timestamp = at(3, 19), memo = "이마트 장보기", paymentMethod = "카드"),
      TransactionEntity(type = TransactionType.EXPENSE, amount = 32_000, category = "문화/여가", accountId = cardId, timestamp = at(6, 20), memo = "영화 관람", paymentMethod = "카드"),
      TransactionEntity(type = TransactionType.INCOME, amount = 150_000, category = "부수입/중고거래", accountId = mainId, timestamp = at(9, 15), memo = "중고거래 판매", paymentMethod = "계좌이체"),
      TransactionEntity(type = TransactionType.EXPENSE, amount = 89_000, category = "쇼핑", accountId = cardId, timestamp = at(12, 21), memo = "온라인 쇼핑", paymentMethod = "카드"),
      TransactionEntity(type = TransactionType.EXPENSE, amount = 23_500, category = "식비", accountId = cardId, timestamp = at(35, 19), memo = "저녁 외식", paymentMethod = "카드"),
      TransactionEntity(type = TransactionType.EXPENSE, amount = 64_000, category = "생활/마트", accountId = cardId, timestamp = at(40, 18), memo = "생필품", paymentMethod = "카드")
    ).forEach { ledger.addTransaction(it) }

    budgets.saveBudget(
      clock.currentMonth(), 1_500_000,
      mapOf("식비" to 400_000, "카페/간식" to 100_000, "쇼핑" to 250_000, "교통" to 100_000, "생활/마트" to 200_000)
    )
    ledger.addGoal(
      SavingGoalEntity(
        title = "여행 자금", targetAmount = 2_000_000, currentAmount = 0,
        targetDate = today.plusMonths(6).atStartOfDay(clock.zone).toInstant().toEpochMilli(), iconEmoji = "✈️"
      )
    )
    recurring.add(
      RecurringTransactionEntity(
        title = "통신요금", type = TransactionType.EXPENSE, amount = 55_000, category = "주거/통신",
        dayOfMonth = 25, accountId = mainId, accountName = main.name, paymentMethod = "자동이체",
        startMonth = MonthKeys.of(clock.currentMonth())
      )
    )
  }
}
