package com.moneyflow.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RuleAction(val label: String) {
  /** Payments whose merchant / message contains the keyword get [CategoryRuleEntity.category]. */
  CATEGORY("카테고리 지정"),
  /** Auto-captured messages containing the keyword are not recorded. */
  IGNORE("자동 기록 안 함")
}

/** A user rule for auto-captured / imported payments, e.g. "스타벅스 → 카페/간식", "포인트 적립 → 무시". */
@Entity(tableName = "category_rules", indices = [Index(value = ["keyword"], unique = true)])
data class CategoryRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val keyword: String,
  val action: RuleAction = RuleAction.CATEGORY,
  /** Category for [RuleAction.CATEGORY]; empty for IGNORE. */
  val category: String = ""
)

/** Applies rules (pure). The longest matching keyword wins. */
object CategoryRules {
  private fun norm(s: String) = s.replace(" ", "").lowercase()

  fun match(rules: List<CategoryRuleEntity>, vararg texts: String): CategoryRuleEntity? {
    val hay = texts.map(::norm)
    return rules
      .filter { r -> norm(r.keyword).let { k -> k.isNotEmpty() && hay.any { it.contains(k) } } }
      .maxByOrNull { norm(it.keyword).length }
  }

  /** Category from a CATEGORY rule that suits [type], or null. */
  fun categoryFor(rules: List<CategoryRuleEntity>, type: TransactionType, vararg texts: String): String? =
    match(rules.filter { it.action == RuleAction.CATEGORY && it.category in FinanceCategories.categoriesFor(type) }, *texts)?.category

  fun ignores(rules: List<CategoryRuleEntity>, vararg texts: String): CategoryRuleEntity? =
    match(rules.filter { it.action == RuleAction.IGNORE }, *texts)
}
