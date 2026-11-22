package com.moneyflow

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.moneyflow.security.AppLockManager
import com.moneyflow.work.RecurringWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyFlowApplication : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var appLockManager: AppLockManager

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  override fun onCreate() {
    super.onCreate()
    appLockManager.initialize()
    ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
    RecurringWorker.schedule(this)
  }
}
