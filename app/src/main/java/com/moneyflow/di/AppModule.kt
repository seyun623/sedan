package com.moneyflow.di

import android.content.Context
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.local.DatabaseFactory
import com.moneyflow.data.repository.settingsStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.domain.SystemAppClock
import com.moneyflow.security.DatabaseKeyManager
import com.moneyflow.security.KeystoreSecretStore
import com.moneyflow.security.SecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun provideClock(): AppClock = SystemAppClock

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
    DatabaseFactory.create(context, DatabaseKeyManager(context).getOrCreatePassphraseHex())

  @Provides
  @Singleton
  fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsStore

  @Provides
  @Singleton
  fun provideSecretStore(): SecretStore = KeystoreSecretStore()

  @Provides
  @Singleton
  fun provideCaptureNotifier(@ApplicationContext context: Context): com.moneyflow.domain.capture.CaptureNotifier =
    com.moneyflow.work.AndroidCaptureNotifier(context)

  @Provides
  fun provideStatsCalculator(clock: AppClock): MonthlyStatsCalculator = MonthlyStatsCalculator(clock.zone)
}
