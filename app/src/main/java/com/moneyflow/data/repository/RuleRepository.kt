package com.moneyflow.data.repository

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.CategoryRuleEntity
import com.moneyflow.data.model.RuleAction
import com.moneyflow.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
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

  /** Saves a rule; a rule with the same keyword is replaced. */
  suspend fun save(rule: CategoryRuleEntity) {
    val keyword = rule.keyword.trim()
    require(keyword.length >= 2) { "단어를 두 글자 이상 입력해 주세요." }
    require(rule.action == RuleAction.IGNORE || rule.category.isNotBlank()) { "카테고리를 골라 주세요." }
    val sameKeyword = dao.byKeyword(keyword)
    if (sameKeyword != null && sameKeyword.id != rule.id) dao.delete(sameKeyword)
    dao.upsert(rule.copy(keyword = keyword, category = if (rule.action == RuleAction.IGNORE) "" else rule.category))
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

    /** "스타벅스 강남점 (할부 3개월)" → "스타벅스 강남점". */
    fun baseMerchant(memo: String): String = memo.substringBefore(" (").trim()
  }
}
