package com.moneyflow.data.repository

import androidx.room.withTransaction
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.capture.PaymentMessageParser
import com.moneyflow.domain.card.CardGuideRow
import com.moneyflow.domain.card.CardPerformanceCalculator
import com.moneyflow.domain.card.CardPick
import com.moneyflow.domain.card.CardRecommender
import com.moneyflow.domain.card.CardStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Result of adding benefits read by AI. */
data class BenefitImportResult(val added: Int, val skipped: Int, val targetSet: Long?)

/** Card 실적 settings, benefits, their live status and card recommendations. */
@Singleton
class CardRepository @Inject constructor(
  private val db: AppDatabase,
  private val clock: AppClock
) {
  private val dao = db.cardDao()
  private val calc = CardPerformanceCalculator(clock.zone)
  val recommender = CardRecommender(calc)
  private val rules = PaymentMessageParser(clock.zone)

  fun observeProfile(accountId: Long): Flow<CardProfileEntity?> = dao.observeProfile(accountId)
  fun observeBenefits(accountId: Long): Flow<List<CardBenefitEntity>> = dao.observeBenefits(accountId)

  private val transactions = db.transactionDao().observeAll()

  /**
   * Status of every card that has 실적 settings or benefits, recomputed when transactions change.
   * A card with benefits only gets a profile without a 실적 target (target = 0).
   */
  val statuses: Flow<List<CardStatus>> = combine(dao.observeProfiles(), dao.observeBenefits(), transactions) { profiles, benefits, txs ->
    val today = clock.today()
    val withProfile = profiles.map { it.accountId }.toSet()
    val benefitOnly = benefits.map { it.accountId }.distinct().filter { it !in withProfile }.map { CardProfileEntity(it, 0) }
    (profiles + benefitOnly).map { calc.status(it, benefits, txs, today) }
  }

  /** "어디서 어떤 카드?" rows for the home screen. */
  val guide: Flow<List<CardGuideRow>> = combine(statuses, transactions) { st, txs ->
    if (st.none { s -> s.benefits.isNotEmpty() }) return@combine emptyList()
    val since = clock.nowMillis() - GUIDE_HISTORY_MS
    val recent = txs.filter { it.timestamp >= since }
    recommender.guide(
      st,
      typicalAmount = { category, keyword -> CardRecommender.typicalAmount(recent, category, keyword) },
      classify = { rules.classify(it, it) }
    )
  }

  suspend fun currentStatuses(): List<CardStatus> = statuses.first()

  fun recommend(statuses: List<CardStatus>, category: String, memo: String, amount: Long): List<CardPick> =
    recommender.recommend(statuses, category, memo, amount)

  suspend fun saveProfile(profile: CardProfileEntity) = dao.upsertProfile(profile)
  suspend fun deleteProfile(accountId: Long) = dao.deleteProfile(accountId)

  suspend fun saveBenefit(benefit: CardBenefitEntity) {
    if (benefit.id == 0L) dao.insertBenefit(benefit) else dao.updateBenefit(benefit)
  }

  suspend fun deleteBenefit(benefit: CardBenefitEntity) = dao.deleteBenefit(benefit)

  /**
   * Adds benefits read from a card company screen. Ones with the same title as an existing
   * benefit of the card are skipped. With [setTargetIfMissing], a card without 실적 settings
   * gets the lowest 전월 실적 the benefits need as its target.
   */
  suspend fun addBenefits(accountId: Long, drafts: List<CardBenefitEntity>, setTargetIfMissing: Boolean): BenefitImportResult =
    db.withTransaction {
      val titles = dao.getBenefits().filter { it.accountId == accountId }.map { normalize(it.title) }.toMutableSet()
      var added = 0
      var skipped = 0
      drafts.forEach { d ->
        if (normalize(d.title) in titles) { skipped++; return@forEach }
        dao.insertBenefit(d.copy(id = 0, accountId = accountId))
        titles += normalize(d.title)
        added++
      }
      var targetSet: Long? = null
      if (setTargetIfMissing && dao.getProfiles().none { it.accountId == accountId }) {
        drafts.map { it.minPerformance }.filter { it > 0 }.minOrNull()?.let { target ->
          dao.upsertProfile(CardProfileEntity(accountId, target))
          targetSet = target
        }
      }
      BenefitImportResult(added, skipped, targetSet)
    }

  /**
   * Saves 실적 조건 found with benefits: the target when none is set, and the 실적 제외 words added
   * to the ones already there. Returns a short description of what changed, or null.
   */
  suspend fun applyConditions(accountId: Long, c: com.moneyflow.domain.card.BenefitReader.Conditions): String? = db.withTransaction {
    val current = dao.getProfiles().firstOrNull { it.accountId == accountId }
    val oldWords = current?.excludedKeywords.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val words = (oldWords + c.excluded).distinct()
    val target = current?.performanceTarget?.takeIf { it > 0 } ?: c.performanceTarget
    if (target <= 0 && words == oldWords) return@withTransaction null
    dao.upsertProfile(
      (current ?: CardProfileEntity(accountId, 0)).copy(performanceTarget = target, excludedKeywords = words.joinToString(","))
    )
    buildList {
      if ((current?.performanceTarget ?: 0) <= 0 && target > 0) add("실적 기준 ${com.moneyflow.util.Formatters.formatCurrency(target)}")
      val added = words - oldWords.toSet()
      if (added.isNotEmpty()) add("실적 제외 ${added.size}개(${added.take(3).joinToString(", ")}${if (added.size > 3) " 등" else ""})")
    }.takeIf { it.isNotEmpty() }?.joinToString(", ")
  }

  private fun normalize(title: String) = title.replace(" ", "").lowercase()

  private companion object {
    const val GUIDE_HISTORY_MS = 90L * 24 * 3_600_000
  }
}
