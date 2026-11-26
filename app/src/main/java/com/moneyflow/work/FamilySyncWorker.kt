package com.moneyflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyflow.data.repository.FamilySyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 가족 자동 합산: uploads this phone's family file and loads the others (every few hours and after changes). */
@HiltWorker
class FamilySyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val sync: FamilySyncRepository,
  private val settings: com.moneyflow.data.repository.SettingsRepository
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = try {
    sync.sync()
    // Changes made while this run was uploading go out in one more run.
    val (generation, uploaded) = settings.familySyncGenerations()
    // (Appended: this run is itself the unique "now" work, which KEEP would leave as it is.)
    if (generation > uploaded && settings.current().familySyncEnabled) runSoon(applicationContext, followUp = true)
    Result.success()
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: IOException) {
    if (runAttemptCount < 3) Result.retry() else Result.success()
  } catch (e: com.moneyflow.drive.DriveException) {
    // Drive busy (429 / 5xx): try again shortly; other Drive errors are shown on screen.
    if (e.transient && runAttemptCount < 3) Result.retry() else Result.success()
  } catch (e: Exception) {
    com.moneyflow.util.ProblemLog.record("가족 자동 합산", e)
    // Recorded for the family / Drive screens; the periodic run tries again.
    Result.success()
  }

  companion object {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "family-sync", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<FamilySyncWorker>(3, TimeUnit.HOURS).setConstraints(network).build()
      )
    }

    /** Soon after the records change (several changes in a row become one upload). */
    fun runSoon(context: Context, delaySeconds: Long = 60, followUp: Boolean = false) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        // KEEP: changes in a row become one upload (a waiting run reads the newest data); a change made
        // while a run is uploading is picked up by the follow-up run that doWork() schedules.
        "family-sync-now", if (followUp) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<FamilySyncWorker>()
          .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
          .setConstraints(network)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
          .build()
      )
    }
  }
}
