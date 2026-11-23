package com.moneyflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyflow.data.repository.AutoBackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Checks every few hours whether an automatic backup is due and writes it. */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val autoBackup: AutoBackupRepository
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = try {
    autoBackup.runIfDue()
    Result.success()
  } catch (e: Exception) {
    // Recorded in settings; the reminder worker tells the user. Try again on the next run.
    Result.success()
  }

  companion object {
    fun schedule(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "auto-backup", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<AutoBackupWorker>(6, TimeUnit.HOURS)
          .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
          .build()
      )
    }
  }
}
