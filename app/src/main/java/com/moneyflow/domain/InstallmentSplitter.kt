package com.moneyflow.domain

import com.moneyflow.data.model.TransactionEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Splits an installment purchase ("할부 3개월") into one expense per month so monthly spending
 * matches what is actually billed. The first part keeps the purchase date (and its dedupKey); the
 * remainder of the division goes into the first part. Later parts fall on the same day of the
 * following months (clamped to the month's last day).
 */
object InstallmentSplitter {
  const val MAX_MONTHS = 36
  private val PART = Regex("""\(할부 (\d+)/(\d+)\)""")

  fun split(tx: TransactionEntity, months: Int, zone: ZoneId): List<TransactionEntity> {
    if (months < 2 || months > MAX_MONTHS || tx.amount < months) return listOf(tx)
    val base = tx.amount / months
    val first = Instant.ofEpochMilli(tx.timestamp).atZone(zone)
    val memo = tx.memo.replace(Regex("""\s*\(할부 \d+개월\)"""), "").trim()
    val group = tx.dedupKey ?: "inst-${tx.timestamp}-${tx.amount}-${memo.hashCode()}"
    return (0 until months).map { i ->
      val date = first.toLocalDate().plusMonths(i.toLong())
      tx.copy(
        id = 0,
        amount = if (i == 0) base + tx.amount % months else base,
        timestamp = first.with(date).toInstant().toEpochMilli(),
        memo = "$memo (할부 ${i + 1}/$months)",
        dedupKey = if (i == 0 && tx.dedupKey != null) tx.dedupKey else "$group#${i + 1}"
      )
    }
  }

  /** (part, total) of a split installment memo, e.g. "(할부 2/3)" → 2 to 3. */
  fun partOf(memo: String): Pair<Int, Int>? =
    PART.find(memo)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

  /** Shared prefix of the dedupKeys of one split purchase. */
  fun groupKey(dedupKey: String?): String? = dedupKey?.substringBefore('#')
}
