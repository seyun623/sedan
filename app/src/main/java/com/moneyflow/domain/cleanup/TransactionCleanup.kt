package com.moneyflow.domain.cleanup

import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.RuleRepository

/** Transactions that look like the same payment recorded twice (oldest first). */
data class DuplicateGroup(val key: String, val items: List<TransactionEntity>) {
  val amount: Long get() = items.first().amount
  /** Total recorded too much if all but one are duplicates. */
  val extra: Long get() = amount * (items.size - 1)
}

/** '기타' transactions with the same place, to classify at once. */
data class OtherGroup(
  val merchant: String,
  val type: TransactionType,
  val items: List<TransactionEntity>,
  /** Category the same place got elsewhere in the ledger (most often), if any. */
  val suggestion: String?
) {
  val total: Long get() = items.sumOf { it.amount }
}

/** Finds likely duplicates and unclassified ('기타') transactions (pure). */
object TransactionCleanup {
  /** Two records closer than this with the same amount and account are suspicious even with different memos. */
  const val CLOSE_MS = 10 * 60_000L
  private const val DAY_MS = 24 * 3_600_000L

  private fun norm(memo: String) = RuleRepository.baseMerchant(memo).replace(" ", "").lowercase()

  private fun similar(a: TransactionEntity, b: TransactionEntity): Boolean {
    if (a.type != b.type || a.amount != b.amount || a.accountId != b.accountId) return false
    // Parts of the same installment purchase / recurring item are different payments.
    if (a.recurringId != null && a.recurringId == b.recurringId) return false
    if (a.dedupKey != null && b.dedupKey != null && a.dedupKey.substringBefore('#') == b.dedupKey.substringBefore('#')) return false
    val gap = kotlin.math.abs(a.timestamp - b.timestamp)
    if (gap <= CLOSE_MS) return true
    val ma = norm(a.memo); val mb = norm(b.memo)
    return gap < DAY_MS && ma.isNotEmpty() && mb.isNotEmpty() && (ma == mb || ma.contains(mb) || mb.contains(ma))
  }

  /** Groups of likely duplicates; [dismissed] holds keys the user marked "not a duplicate". */
  fun duplicates(txs: List<TransactionEntity>, dismissed: Set<String> = emptySet()): List<DuplicateGroup> {
    val sorted = txs.filter { it.amount > 0 }.sortedBy { it.timestamp }
    val used = HashSet<Long>()
    val out = mutableListOf<DuplicateGroup>()
    for ((i, a) in sorted.withIndex()) {
      if (a.id in used) continue
      val group = mutableListOf(a)
      var j = i + 1
      while (j < sorted.size && sorted[j].timestamp - a.timestamp < DAY_MS) {
        val b = sorted[j]
        if (b.id !in used && similar(a, b)) group += b
        j++
      }
      if (group.size >= 2) {
        val key = group.map { it.id }.sorted().joinToString(",")
        // A dismissed group's records are not offered again in smaller groups either.
        used += group.map { it.id }
        if (key !in dismissed) out += DuplicateGroup(key, group)
      }
    }
    return out.sortedByDescending { it.items.last().timestamp }
  }

  /** '기타지출' / '기타수입' transactions grouped by place, biggest groups first. */
  fun others(txs: List<TransactionEntity>): List<OtherGroup> {
    val otherCats = setOf(FinanceCategories.OTHER_EXPENSE, FinanceCategories.OTHER_INCOME)
    val classified = txs.filter { it.type != TransactionType.TRANSFER && it.category !in otherCats }
      .groupBy { norm(it.memo) to it.type }
    return txs.filter { it.type != TransactionType.TRANSFER && it.category in otherCats }
      .groupBy { norm(it.memo) to it.type }
      .map { (key, list) ->
        val suggestion = classified[key]?.groupingBy { it.category }?.eachCount()?.maxByOrNull { it.value }?.key
        OtherGroup(RuleRepository.baseMerchant(list.first().memo).ifBlank { "(메모 없음)" }, key.second, list, suggestion)
      }
      .sortedWith(compareByDescending<OtherGroup> { it.items.size }.thenByDescending { it.total })
  }
}
