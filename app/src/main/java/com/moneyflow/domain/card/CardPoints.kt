package com.moneyflow.domain.card

/** A card's point balance read from its screen ("마이신한포인트 12,345P"). */
data class PointInfo(val label: String, val amount: Long)

/** A saved point balance of a card account. */
data class CardPoint(val label: String, val amount: Long, val fetchedAt: Long)

/**
 * Finds the usable point balance on a card company screen (on the phone). Point names of the
 * card companies; 적립 예정 / 소멸 예정 amounts are not the balance.
 */
object PointText {
  private val NAME = Regex(
    "(마이신한포인트|포인트리|M포인트|하나머니|L\\.?POINT|엘포인트|빅포인트|탑포인트|TOP포인트|우리\\s*포인트|NH\\s*포인트|채움\\s*포인트|BC\\s*TOP\\s*포인트|마일리지|캐시백|포인트)",
    RegexOption.IGNORE_CASE
  )
  private val NOT_BALANCE = Regex("소멸|예정|적립\\s*률|적립\\s*내역|사용\\s*내역|전환|기준|이벤트|최대|%")
  private val AMOUNT = Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,9})\\s*(P|p|점|원|마일|M)(?![a-zA-Z가-힣])")

  fun find(text: String): PointInfo? {
    val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    for ((i, line) in lines.withIndex()) {
      val name = NAME.find(line) ?: continue
      if (NOT_BALANCE.containsMatchIn(line) || line.length > 50) continue
      val rest = line.substring(name.range.last + 1)
      val m = AMOUNT.find(rest) ?: lines.getOrNull(i + 1)?.takeIf { it.length <= 20 && !NOT_BALANCE.containsMatchIn(it) }?.let { AMOUNT.find(it) } ?: continue
      val amount = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
      val label = name.value.replace(Regex("\\s+"), "").let { if (it.contains("마일리지")) "마일리지" else it }
      return PointInfo(label, amount)
    }
    return null
  }
}
