package com.moneyflow

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.security.AppLockManager
import com.moneyflow.work.AutoBackupWorker
import com.moneyflow.work.RecurringWorker
import com.moneyflow.work.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MoneyFlowApplication : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var appLockManager: AppLockManager
  /** Lazy: opening the encrypted database must not happen on the main thread here. */
  @Inject lateinit var db: dagger.Lazy<AppDatabase>
  @Inject lateinit var settings: dagger.Lazy<com.moneyflow.data.repository.SettingsRepository>

  /** Lives as long as the process. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  @OptIn(FlowPreview::class)
  override fun onCreate() {
    super.onCreate()
    com.moneyflow.util.ProblemLog.install(this, BuildConfig.VERSION_NAME)
    com.moneyflow.domain.capture.FinancialApps.extraPackages = com.moneyflow.domain.capture.CaptureExtraApps.load(this)
    appLockManager.initialize()
    ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
    RecurringWorker.schedule(this)
    com.moneyflow.work.CardAlertWorker.schedule(this)
    ReminderWorker.schedule(this)
    // After an app update Samsung may leave the notification reader unbound: ask to reconnect.
    if (BuildConfig.AUTO_CAPTURE_AVAILABLE &&
      androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    ) com.moneyflow.service.FinanceNotificationListenerService.rebind(this)
    AutoBackupWorker.schedule(this)
    com.moneyflow.work.FamilySyncWorker.schedule(this)

    watchWidgetPrivacy()
    // When records change (typed in, auto-captured, imported), check budget alerts and the widget soon.
    appScope.launch {
      try {
        // Only "something changed" is needed: watch the tables without loading their rows.
        db.get().invalidationTracker.createFlow("transactions", "saving_goals", emitInitialState = false)
          .debounce(3_000)
          .collect { onDataChanged() }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        // The database could not be opened here (e.g. no Keystore in tests); the app shows its own error.
      }
    }
  }

  private fun watchWidgetPrivacy() = appScope.launch {
    try {
      settings.get().settings
        .map { it.isLockEnabled to it.widgetShowAmountsWhenLocked }
        .distinctUntilChanged()
        .drop(1)
        .collect { runCatching { com.moneyflow.widget.SpendingWidget.updateAll(this@MoneyFlowApplication) } }
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
    }
  }

  private suspend fun onDataChanged() {
    runCatching { com.moneyflow.widget.SpendingWidget.updateAll(this) }
    runCatching {
      val s = settings.get()
      s.markFamilyDataChanged()
      if (s.current().familySyncEnabled) com.moneyflow.work.FamilySyncWorker.runSoon(this)
    }
    WorkManager.getInstance(this).enqueueUniqueWork(
      "reminders-now", ExistingWorkPolicy.REPLACE,
      OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(5, TimeUnit.SECONDS).build()
    )
  }
}
