package com.moneyflow.data.repository

import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.model.CardBenefitEntity
import com.moneyflow.data.model.CardProfileEntity
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.card.CardPerformanceCalculator
import com.moneyflow.domain.card.CardStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Card 실적 settings, benefits and their live status. */
@Singleton
class CardRepository @Inject constructor(
  private val db: AppDatabase,
  private val clock: AppClock
) {
  private val dao = db.cardDao()
  private val calc = CardPerformanceCalculator(clock.zone)

  fun observeProfile(accountId: Long): Flow<CardProfileEntity?> = dao.observeProfile(accountId)
  fun observeBenefits(accountId: Long): Flow<List<CardBenefitEntity>> = dao.observeBenefits(accountId)

  /** Status of every card that has 실적 settings, recomputed when transactions change. */
  val statuses: Flow<List<CardStatus>> = combine(
    dao.observeProfiles(), dao.observeBenefits(), db.transactionDao().observeAll()
  ) { profiles, benefits, txs ->
    val today = clock.today()
    profiles.map { calc.status(it, benefits, txs, today) }
  }

  suspend fun currentStatuses(): List<CardStatus> = statuses.first()

  suspend fun saveProfile(profile: CardProfileEntity) = dao.upsertProfile(profile)
  suspend fun deleteProfile(accountId: Long) = dao.deleteProfile(accountId)

  suspend fun saveBenefit(benefit: CardBenefitEntity) {
    if (benefit.id == 0L) dao.insertBenefit(benefit) else dao.updateBenefit(benefit)
  }

  suspend fun deleteBenefit(benefit: CardBenefitEntity) = dao.deleteBenefit(benefit)
}
