package com.moneyflow.util

import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.model.spending

/** Spending of one #tag. */
data class TagSpend(val tag: String, val expense: Long, val income: Long, val transactions: List<TransactionEntity>)

/** Tags are written in the memo as "#여행", "#경조사" … */
object Tags {
  private val TAG = Regex("""#([\p{L}\p{N}_]{1,20})""")

  fun extract(memo: String): List<String> = TAG.findAll(memo).map { it.groupValues[1] }.distinct().toList()

  /** Per-tag totals, biggest spending first. Transfers are not counted. */
  fun summarize(txs: List<TransactionEntity>): List<TagSpend> =
    txs.filter { it.type != TransactionType.TRANSFER }
      .flatMap { tx -> extract(tx.memo).map { it to tx } }
      .groupBy({ it.first }, { it.second })
      .map { (tag, list) ->
        TagSpend(
          tag,
          list.sumOf { it.spending }.coerceAtLeast(0),
          list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
          list.sortedByDescending { it.timestamp }
        )
      }
      .sortedWith(compareByDescending<TagSpend> { it.expense }.thenBy { it.tag })

  /** Most recently used tags (newest first), for quick picking in the form. */
  fun recent(txsNewestFirst: List<TransactionEntity>, limit: Int = 8): List<String> =
    txsNewestFirst.asSequence().flatMap { extract(it.memo).asSequence() }.distinct().take(limit).toList()

  /** Adds "#tag" to the memo (once). */
  fun append(memo: String, tag: String): String =
    if (tag in extract(memo)) memo else (memo.trimEnd() + " #" + tag).trim()
}
