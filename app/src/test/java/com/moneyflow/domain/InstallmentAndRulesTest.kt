package com.moneyflow.domain

import com.moneyflow.data.model.CategoryRuleEntity
import com.moneyflow.data.model.CategoryRules
import com.moneyflow.data.model.RuleAction
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class InstallmentAndRulesTest {
  private val zone = ZoneId.of("Asia/Seoul")

  private fun tx(amount: Long, at: LocalDateTime, memo: String = "하이마트 (할부 3개월)") = TransactionEntity(
    type = TransactionType.EXPENSE, amount = amount, category = "쇼핑", accountId = 1,
    timestamp = at.atZone(zone).toInstant().toEpochMilli(), memo = memo, dedupKey = "abc"
  )

  @Test fun splitsIntoMonthlyPartsClampingTheDay() {
    val parts = InstallmentSplitter.split(tx(100_000, LocalDateTime.of(2026, 1, 31, 14, 0)), 3, zone)
    assertEquals(listOf(33_334L, 33_333L, 33_333L), parts.map { it.amount })
    assertEquals(
      listOf("2026-01-31", "2026-02-28", "2026-03-31"),
      parts.map { java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().toString() }
    )
    assertEquals(listOf("하이마트 (할부 1/3)", "하이마트 (할부 2/3)", "하이마트 (할부 3/3)"), parts.map { it.memo })
    assertEquals(listOf("abc", "abc#2", "abc#3"), parts.map { it.dedupKey })
    assertEquals(2 to 3, InstallmentSplitter.partOf(parts[1].memo))
    assertEquals("abc", InstallmentSplitter.groupKey(parts[2].dedupKey))
  }

  @Test fun noSplitForOneMonthOrTinyAmount() {
    val t = tx(2, LocalDateTime.of(2026, 1, 1, 0, 0))
    assertEquals(listOf(t), InstallmentSplitter.split(t, 3, zone))
    assertEquals(1, InstallmentSplitter.split(tx(10_000, LocalDateTime.of(2026, 1, 1, 0, 0)), 1, zone).size)
    assertNull(InstallmentSplitter.partOf("스타벅스"))
  }

  @Test fun rulesLongestKeywordWinsAndTypeMustFit() {
    val rules = listOf(
      CategoryRuleEntity(1, "스타", RuleAction.CATEGORY, "문화/여가"),
      CategoryRuleEntity(2, "스타벅스", RuleAction.CATEGORY, "카페/간식"),
      CategoryRuleEntity(3, "(주)회사", RuleAction.CATEGORY, "급여"),
      CategoryRuleEntity(4, "포인트 적립", RuleAction.IGNORE, "")
    )
    assertEquals("카페/간식", CategoryRules.categoryFor(rules, TransactionType.EXPENSE, "스타벅스 강남점"))
    assertEquals("문화/여가", CategoryRules.categoryFor(rules, TransactionType.EXPENSE, "스타필드"))
    assertNull(CategoryRules.categoryFor(rules, TransactionType.EXPENSE, "(주)회사"))
    assertEquals("급여", CategoryRules.categoryFor(rules, TransactionType.INCOME, "(주) 회사"))
    assertEquals(4L, CategoryRules.ignores(rules, "", "OK캐시백 포인트적립 안내")?.id)
    assertNull(CategoryRules.ignores(rules, "스타벅스"))
  }
}
