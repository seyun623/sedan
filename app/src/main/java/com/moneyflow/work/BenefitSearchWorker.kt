package com.moneyflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.moneyflow.ai.AiBusyException
import com.moneyflow.data.repository.CardRepository
import com.moneyflow.domain.alerts.Reminder
import com.moneyflow.domain.alerts.ReminderChannel
import com.moneyflow.domain.card.BenefitReader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 카드 혜택 찾기 when the AI's free limit was used up: tries again later (every few hours, up to a
 * day and a half), then registers what it found (benefits + 실적 조건) and tells the user.
 */
@HiltWorker
class BenefitSearchWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val reader: BenefitReader,
  private val cards: CardRepository
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val accountId = inputData.getLong(KEY_ACCOUNT, 0L)
    val name = inputData.getString(KEY_NAME).orEmpty()
    if (accountId <= 0 || name.isBlank()) return Result.failure()
    val found = try {
      reader.search(name)
    } catch (e: AiBusyException) {
      return if (runAttemptCount < MAX_TRIES) Result.retry() else { notify(name, "AI 한도가 계속 차 있어 찾지 못했어요. 내일 다시 '혜택 찾기'를 눌러 주세요."); Result.failure() }
    } catch (e: Exception) {
      notify(name, "혜택을 찾지 못했어요: ${e.message ?: "오류"}")
      return Result.failure()
    } ?: return Result.failure()
    if (found.drafts.isEmpty()) {
      notify(name, "'$name'의 혜택을 찾지 못했어요. 카드 이름을 정확히 넣어 다시 찾아 주세요.")
      return Result.success()
    }
    val added = cards.addBenefits(accountId, found.drafts, setTargetIfMissing = found.conditions == null)
    val conditions = found.conditions?.let { cards.applyConditions(accountId, it) }
    notify(name, "혜택 ${added.added}개를 등록했어요" + (conditions?.let { ", 실적 조건: $it" } ?: "") + ". 카드 화면에서 확인해 주세요.")
    return Result.success()
  }

  private fun notify(name: String, text: String) {
    Notifications.post(applicationContext, Reminder("benefit|$name|${System.currentTimeMillis()}", ReminderChannel.CARD, "$name 혜택 찾기", text))
  }

  companion object {
    private const val KEY_ACCOUNT = "account"
    private const val KEY_NAME = "name"
    private const val MAX_TRIES = 8

    fun schedule(context: Context, accountId: Long, cardName: String) {
      val request = OneTimeWorkRequestBuilder<BenefitSearchWorker>()
        .setInitialDelay(1, TimeUnit.HOURS)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 3, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(workDataOf(KEY_ACCOUNT to accountId, KEY_NAME to cardName.trim()))
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork("benefit_search_$accountId", ExistingWorkPolicy.REPLACE, request)
    }
  }
}
