package com.moneyflow.domain.family

import com.moneyflow.util.Tags

/** "엄마 → 아빠 30,000원" */
data class SettleTransfer(val from: String, val to: String, val amount: Long)

data class Settlement(
  /** Shared spending each person paid (everyone listed, 0 included), in the given order. */
  val paid: List<Pair<String, Long>>,
  val total: Long,
  /** Each person's equal part. */
  val share: Long,
  val transfers: List<SettleTransfer>
) {
  val isEmpty: Boolean get() = total <= 0
}

/**
 * 가족 정산 (2.18): spending marked with #공동 (or #같이, #더치) is split equally among the family,
 * and the fewest transfers that even it out are suggested. Pure and testable.
 */
object FamilySettlement {
  val SHARED_TAGS = setOf("공동", "같이", "더치", "공동경비")
  const val DEFAULT_TAG = "공동"

  fun isShared(memo: String): Boolean = Tags.extract(memo).any { it in SHARED_TAGS }

  fun settle(paid: List<Pair<String, Long>>): Settlement {
    val people = paid.map { it.first to it.second.coerceAtLeast(0) }
    val total = people.sumOf { it.second }
    if (people.size < 2 || total <= 0) return Settlement(people, total, if (people.isEmpty()) 0 else total / people.size, emptyList())
    val share = total / people.size
    // The won left over by dividing goes to the first people (no money lost or made).
    val extra = (total - share * people.size).toInt()
    val owed = people.mapIndexed { i, _ -> share + if (i < extra) 1 else 0 }
    val balance = people.mapIndexed { i, (name, amount) -> name to (amount - owed[i]) }.toMutableList()
    val transfers = mutableListOf<SettleTransfer>()
    while (true) {
      val creditor = balance.indices.maxByOrNull { balance[it].second } ?: break
      val debtor = balance.indices.minByOrNull { balance[it].second } ?: break
      val amount = minOf(balance[creditor].second, -balance[debtor].second)
      if (amount <= 0) break
      transfers += SettleTransfer(balance[debtor].first, balance[creditor].first, amount)
      balance[creditor] = balance[creditor].first to balance[creditor].second - amount
      balance[debtor] = balance[debtor].first to balance[debtor].second + amount
    }
    return Settlement(people, total, share, transfers)
  }

  /** Text to send to the family chat. */
  fun message(monthLabel: String, s: Settlement, won: (Long) -> String): String = buildString {
    appendLine("[$monthLabel 가족 정산] 같이 쓴 돈 ${won(s.total)}, 1인당 ${won(s.share)}")
    s.paid.forEach { (name, amount) -> appendLine("· $name 낸 돈 ${won(amount)}") }
    if (s.transfers.isEmpty()) append("주고받을 돈이 없어요.")
    else s.transfers.forEach { appendLine("→ ${it.from}이(가) ${it.to}에게 ${won(it.amount)}") }
  }.trim()
}
