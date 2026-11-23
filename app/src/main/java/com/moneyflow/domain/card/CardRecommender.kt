package com.moneyflow.domain.card

import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType

/** Which card to pay with. */
data class CardPick(
  val accountId: Long,
  /** Benefit expected for this payment (원), within the remaining monthly cap. 0 for a 실적 pick. */
  val expected: Long,
  /** The benefit that gives [expected]; null for a 실적 pick. */
  val benefit: CardBenefitEntity?,
  /** No card gives a benefit, but this card still needs [shortage] of 실적 this period. */
  val forPerformance: Boolean = false,
  val shortage: Long = 0
)

/** One line of "어디서 어떤 카드?": e.g. 카페/간식 (보통 6,000원) → 신한카드 10%. */
data class CardGuideRow(
  val label: String,
  val typicalAmount: Long,
  val best: CardPick,
  val runnerUp: CardPick?
)

/** Picks the card that gives the most benefit for a payment (pure). */
class CardRecommender(private val calc: CardPerformanceCalculator) {

  /** Best cards first. Falls back to cards that still need 실적 when no benefit applies. */
  fun recommend(statuses: List<CardStatus>, category: String, memo: String, amount: Long): List<CardPick> {
    if (amount <= 0 || statuses.isEmpty()) return emptyList()
    val benefitPicks = benefitPicks(statuses, category, memo, amount)
    if (benefitPicks.isNotEmpty()) return benefitPicks
    return statuses
      .filter { it.target > 0 && !it.achieved && calc.countsToward(it.profile, category, memo) }
      .sortedWith(compareBy<CardStatus> { it.daysLeft }.thenByDescending { it.shortage })
      .map { CardPick(it.accountId, 0, null, forPerformance = true, shortage = it.shortage) }
  }

  fun benefitPicks(statuses: List<CardStatus>, category: String, memo: String, amount: Long): List<CardPick> =
    statuses.mapNotNull { st ->
      st.benefits
        .map { it to expected(it, category, memo, amount) }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.let { (usage, value) -> CardPick(st.accountId, value, usage.benefit) }
    }.sortedByDescending { it.expected }

  /** What [usage]'s benefit would give for this payment now (0 if not eligible / cap used up). */
  fun expected(usage: BenefitUsage, category: String, memo: String, amount: Long): Long {
    val b = usage.benefit
    if (!b.isActive || !usage.eligible || !calc.matches(b, category, memo, amount)) return 0
    val raw = calc.benefitFor(b, amount)
    return usage.remainingCap?.let { minOf(raw, it) } ?: raw
  }

  /**
   * One row per benefit target (category or merchant keyword) across all cards.
   * @param typicalAmount usual payment for (category, keyword or null)
   * @param classify category of a merchant keyword (for keyword benefits without a category)
   */
  fun guide(
    statuses: List<CardStatus>,
    typicalAmount: (category: String, keyword: String?) -> Long,
    classify: (String) -> String
  ): List<CardGuideRow> {
    data class Target(val label: String, val category: String, val keyword: String?)
    val targets = LinkedHashMap<String, Target>()
    statuses.flatMap { it.benefits }.map { it.benefit }.filter { it.isActive }.forEach { b ->
      val words = b.merchantKeywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
      if (words.isEmpty()) {
        b.category?.let { targets.getOrPut(it) { Target(it, it, null) } }
      } else {
        words.take(MAX_KEYWORDS).forEach { w -> targets.getOrPut(w.lowercase()) { Target(w, b.category ?: classify(w), w) } }
      }
    }
    return targets.values.mapNotNull { t ->
      val amount = typicalAmount(t.category, t.keyword).takeIf { it > 0 } ?: DEFAULT_AMOUNT
      val picks = benefitPicks(statuses, t.category, t.keyword ?: "", amount)
      picks.firstOrNull()?.let { CardGuideRow(t.label, amount, it, picks.getOrNull(1)) }
    }.sortedByDescending { it.best.expected }
  }

  companion object {
    const val DEFAULT_AMOUNT = 10_000L
    private const val MAX_KEYWORDS = 3

    /** Median of past payments (category or memo keyword), for the guide's "보통 ○원". */
    fun typicalAmount(txs: List<TransactionEntity>, category: String, keyword: String?): Long {
      val expenses = txs.filter { it.type == TransactionType.EXPENSE }
      val byKeyword = keyword?.let { k -> expenses.filter { it.memo.contains(k, ignoreCase = true) } }.orEmpty()
      val sample = byKeyword.ifEmpty { expenses.filter { it.category == category } }.map { it.amount }.sorted()
      return if (sample.isEmpty()) 0 else sample[sample.size / 2]
    }
  }
}
