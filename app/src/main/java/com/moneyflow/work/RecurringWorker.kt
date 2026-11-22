package com.moneyflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyflow.data.repository.RecurringRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Records due recurring transactions: once at app start and then every ~12 hours. */
@HiltWorker
class RecurringWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val recurring: RecurringRepository
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = runCatching { recurring.applyDue() }
    .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

  companion object {
    fun schedule(context: Context) {
      val wm = WorkManager.getInstance(context)
      wm.enqueueUniqueWork("recurring-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RecurringWorker>().build())
      wm.enqueueUniquePeriodicWork(
        "recurring-periodic",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<RecurringWorker>(12, TimeUnit.HOURS).build()
      )
    }
  }
}
