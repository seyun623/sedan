package com.moneyflow.domain.capture

import android.content.Context

/**
 * Apps the user added in 결제 알림 자동 등록 → '추가로 읽을 앱' (e.g. a city's 지역화폐 app the app
 * doesn't know). Kept in plain preferences so the notification listener can check them at once.
 */
object CaptureExtraApps {
  private const val PREFS = "capture_extra_apps"
  private const val KEY = "packages"

  fun load(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().toSet()

  fun save(context: Context, packages: Set<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, packages).apply()
    FinancialApps.extraPackages = packages
  }
}
