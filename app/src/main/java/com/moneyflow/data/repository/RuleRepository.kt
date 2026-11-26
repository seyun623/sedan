package com.moneyflow.data.repository

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.CategoryRuleEntity
import com.moneyflow.data.model.RuleAction
import com.moneyflow.data.model.TransactionType
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A place the app learned from auto-recorded payments: the category it will reuse next time. */
data class LearnedCategory(val merchant: String, val type: TransactionType, val category: String, val count: Int, val lastAt: Long)

/** User rules for auto-captured / imported payments. */
@Singleton
class RuleRepository @Inject constructor(private val db: AppDatabase) {
  private val dao = db.ruleDao()

  val rules: Flow<List<CategoryRuleEntity>> = dao.observeAll()

  suspend fun all(): List<CategoryRuleEntity> = dao.getAll()

  /**
   * Saves a rule; a rule with the same keyword is replaced. A category rule also changes the
   * payments already recorded for that place (recorded automatically or imported, and manual ones
   * still in 기타). Returns how many were changed.
   */
  suspend fun save(rule: CategoryRuleEntity): Int {
    val keyword = rule.keyword.trim()
    require(keyword.length >= 2) { "단어를 두 글자 이상 입력해 주세요." }
    require(rule.action == RuleAction.IGNORE || rule.category.isNotBlank()) { "카테고리를 골라 주세요." }
    val sameKeyword = dao.byKeyword(keyword)
    if (sameKeyword != null && sameKeyword.id != rule.id) dao.delete(sameKeyword)
    val saved = rule.copy(keyword = keyword, category = if (rule.action == RuleAction.IGNORE) "" else rule.category)
    return db.withTransaction {
      dao.upsert(saved)
      if (saved.action == RuleAction.CATEGORY) applyToExisting(saved) else 0
    }
  }

  private suspend fun applyToExisting(rule: CategoryRuleEntity): Int {
    val rules = dao.getAll()
    val txs = db.transactionDao().observeAll().first()
    val changes = recategorize(txs, rules).filter { (_, category) -> category == rule.category }
    changes.groupBy({ it.second }, { it.first }).forEach { (category, ids) -> db.transactionDao().setCategory(ids, category) }
    return changes.size
  }

  suspend fun delete(rule: CategoryRuleEntity) = dao.delete(rule)

  /** Places recorded automatically (notification, SMS, card import) and the category they now get. */
  val learned: Flow<List<LearnedCategory>> = db.transactionDao().observeAll().map { txs ->
    txs.filter { it.source.isAutoCaptured || it.source == com.moneyflow.data.model.TransactionSource.CARD_IMPORT }
      .filter { it.type != TransactionType.TRANSFER }
      .groupBy { baseMerchant(it.memo) to it.type }
      .filterKeys { it.first.isNotBlank() }
      .map { (key, list) ->
        // Transactions are newest first: the newest category is the one reused next time.
        LearnedCategory(key.first, key.second, list.first().category, list.size, list.first().timestamp)
      }
      .sortedByDescending { it.lastAt }
      .take(MAX_LEARNED)
  }

  companion object {
    private const val MAX_LEARNED = 60

    /**
     * Existing payments whose category a rule now decides: (transaction id, new category).
     * Payments the user filed by hand keep their category unless it is still 기타.
     */
    fun recategorize(txs: List<com.moneyflow.data.model.TransactionEntity>, rules: List<CategoryRuleEntity>): List<Pair<Long, String>> =
      txs.filter { it.type != TransactionType.TRANSFER }
        .filter {
          it.source.isAutoCaptured || it.source == com.moneyflow.data.model.TransactionSource.CARD_IMPORT ||
            it.category == com.moneyflow.data.model.FinanceCategories.OTHER_EXPENSE ||
            it.category == com.moneyflow.data.model.FinanceCategories.OTHER_INCOME
        }
        .mapNotNull { tx ->
          // A refund is filed under the spending category it reduces.
          val asType = if (tx.type == TransactionType.REFUND) TransactionType.EXPENSE else tx.type
          com.moneyflow.data.model.CategoryRules.categoryFor(rules, asType, baseMerchant(tx.memo))
            ?.takeIf { it != tx.category }?.let { tx.id to it }
        }

    /** "스타벅스 강남점 (할부 3개월)" → "스타벅스 강남점". */
    fun baseMerchant(memo: String): String = memo.substringBefore(" (").substringBefore(" · ").trim()
  }
}
