package com.moneyflow.domain

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.util.Tags
import com.moneyflow.widget.WidgetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TagsAndWidgetTest {
  private val zone = ZoneId.of("Asia/Seoul")
  private fun tx(id: Long, type: TransactionType, amount: Long, memo: String, day: Int = 20, excluded: Boolean = false) = TransactionEntity(
    id = id, type = type, amount = amount, category = "식비", accountId = 1,
    timestamp = LocalDateTime.of(2026, 11, day, 12, 0).atZone(zone).toInstant().toEpochMilli(), memo = memo, isExcludedFromBudget = excluded
  )

  @Test fun tagsExtractSummarizeAndAppend() {
    assertEquals(listOf("제주여행", "가족"), Tags.extract("흑돼지 #제주여행 #가족 #제주여행"))
    val sum = Tags.summarize(listOf(
      tx(1, TransactionType.EXPENSE, 50_000, "흑돼지 #제주여행"),
      tx(2, TransactionType.EXPENSE, 120_000, "항공권 #제주여행"),
      tx(3, TransactionType.EXPENSE, 30_000, "축의금 #경조사"),
      tx(4, TransactionType.TRANSFER, 999_999, "이체 #제주여행")
    ))
    assertEquals(listOf("제주여행", "경조사"), sum.map { it.tag })
    assertEquals(170_000, sum[0].expense)
    assertEquals(2, sum[0].transactions.size)
    assertEquals("점심 #회사", Tags.append("점심", "회사"))
    assertEquals("점심 #회사", Tags.append("점심 #회사", "회사"))
    assertEquals(listOf("a", "b"), Tags.recent(listOf(tx(1, TransactionType.EXPENSE, 1, "#a"), tx(2, TransactionType.EXPENSE, 1, "#b #a"))))
  }

  @Test fun widgetContentTotalsAndPrivacy() {
    val txs = listOf(
      tx(1, TransactionType.EXPENSE, 10_000, "점심", day = 24),
      tx(2, TransactionType.EXPENSE, 5_000, "커피", day = 23),
      tx(3, TransactionType.EXPENSE, 100_000, "경조사", day = 24, excluded = true),
      tx(4, TransactionType.INCOME, 1_000_000, "급여", day = 24)
    )
    val c = WidgetContent.of(txs, LocalDate.of(2026, 11, 24), zone, budgetTotal = 20_000, hidden = false)
    assertEquals(110_000, c.today)
    assertEquals(115_000, c.month)
    assertEquals(5_000L, c.remainingBudget)
    assertEquals("예산 5,000원 남음", c.budgetText)
    assertEquals(75, c.budgetPercent) // 15,000 of 20,000 (the excluded item doesn't count)
    assertEquals(listOf("식비" to 115_000L), c.topCategories)
    assertEquals(com.moneyflow.widget.SpendingWidget.WidgetSize.SMALL, com.moneyflow.widget.SpendingWidget.WidgetSize.of(110, 60))
    assertEquals(com.moneyflow.widget.SpendingWidget.WidgetSize.MEDIUM, com.moneyflow.widget.SpendingWidget.WidgetSize.of(250, 140))
    assertEquals(com.moneyflow.widget.SpendingWidget.WidgetSize.LARGE, com.moneyflow.widget.SpendingWidget.WidgetSize.of(250, 260))
    assertNull(WidgetContent.of(txs, LocalDate.of(2026, 11, 24), zone, 0, false).remainingBudget)
    val hidden = WidgetContent.of(txs, LocalDate.of(2026, 11, 24), zone, 20_000, hidden = true)
    assertEquals("앱 잠금 중", hidden.todayText)
    assertEquals("", hidden.budgetText)
    assertEquals(null, hidden.budgetPercent)
    assertEquals("", hidden.categoriesText)
  }
}
