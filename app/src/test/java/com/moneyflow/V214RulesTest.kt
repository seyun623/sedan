package com.moneyflow

import com.moneyflow.data.model.CategoryRuleEntity
import com.moneyflow.data.model.RuleAction
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionSource
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.RuleRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class V214RulesTest {
  private fun tx(id: Long, memo: String, category: String, source: TransactionSource = TransactionSource.NOTIFICATION, type: TransactionType = TransactionType.EXPENSE) =
    TransactionEntity(id = id, type = type, amount = 9_000, category = category, accountId = 1, timestamp = id, memo = memo, source = source)

  @Test fun newRuleChangesRecordedPayments() {
    val rules = listOf(CategoryRuleEntity(keyword = "후라토식당", action = RuleAction.CATEGORY, category = "식비"))
    val txs = listOf(
      tx(1, "후라토식당 성수점", "기타지출"),
      tx(2, "후라토식당 성수점 (할부 3개월)", "기타지출", TransactionSource.CARD_IMPORT),
      tx(3, "후라토식당 성수점", "카페/간식", TransactionSource.MANUAL), // filed by hand: kept
      tx(4, "후라토식당", "기타지출", TransactionSource.MANUAL), // still 기타: changed
      tx(5, "후라토식당 성수점", "식비"), // already right
      tx(6, "후라토식당 성수점", "기타지출", type = TransactionType.REFUND),
      tx(7, "이마트24", "기타지출")
    )
    assertEquals(listOf(1L to "식비", 2L to "식비", 4L to "식비", 6L to "식비"), RuleRepository.recategorize(txs, rules))
  }

  @Test fun incomeRuleDoesNotTouchExpenses() {
    val rules = listOf(CategoryRuleEntity(keyword = "회사", action = RuleAction.CATEGORY, category = "식비"))
    assertEquals(emptyList<Pair<Long, String>>(), RuleRepository.recategorize(listOf(tx(1, "회사", "기타수입", type = TransactionType.INCOME)), rules))
  }
}
