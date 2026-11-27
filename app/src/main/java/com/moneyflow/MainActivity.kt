package com.moneyflow

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.ui.lock.AppViewModel
import com.moneyflow.ui.lock.LockScreen
import com.moneyflow.ui.navigation.MoneyFlowNavHost
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.MoneyFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
  private val appViewModel: AppViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Hide content in the recents screen until settings say otherwise.
    applySecureScreen(true)
    com.moneyflow.work.PrefillIntents.take(this, intent)?.let(appViewModel.prefill::request)
    setContent {
      val settings by appViewModel.settings.collectAsStateWithLifecycle()
      val locked by appViewModel.locked.collectAsStateWithLifecycle()
      val s = settings
      // The lock screen replaces the app content, but the content's state (back stack, form
      // inputs, pending file-picker results) is kept and restored after unlocking.
      val contentState = rememberSaveableStateHolder()
      val navController = rememberNavController()
      MoneyFlowTheme(themeMode = s?.themeMode ?: com.moneyflow.data.repository.ThemeMode.SYSTEM) {
        // With an app lock, the recents thumbnail (taken before the lock kicks in) must not show the ledger.
        LaunchedEffect(s?.secureScreen, s?.isLockEnabled) { s?.let { applySecureScreen(it.secureScreen, hideRecents = it.isLockEnabled) } }
        when {
          s == null -> Box(Modifier.fillMaxSize().background(Gray100))
          s.usesDeviceLock && locked -> com.moneyflow.ui.lock.DeviceLockScreen(
            onUnlocked = appViewModel::unlockWithDeviceLock,
            onNoDeviceLock = appViewModel::deviceLockMissing,
            fingerprintFirst = s.deviceLockFingerprint
          )
          s.isLockEnabled && locked -> LockScreen(
            settings = s,
            onVerifyPin = appViewModel::verifyPin,
            onBiometricSuccess = appViewModel::unlockWithBiometric,
            onRecover = appViewModel::recover
          )
          else -> {
            contentState.SaveableStateProvider("app-content") {
              val density = androidx.compose.ui.platform.LocalDensity.current
              // 간단 모드: everything a little bigger.
              val scaled = if (s.simpleMode) androidx.compose.ui.unit.Density(density.density, (density.fontScale * 1.15f).coerceAtMost(2f)) else density
              androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaled) {
                MoneyFlowNavHost(messages = appViewModel.messages, navController = navController, prefill = appViewModel.prefill, simpleMode = s.simpleMode)
              }
            }
            val whatsNew by appViewModel.whatsNew.collectAsStateWithLifecycle()
            com.moneyflow.ui.whatsnew.WhatsNewDialog(whatsNew, onDismiss = appViewModel::dismissWhatsNew)
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    com.moneyflow.work.PrefillIntents.take(this, intent)?.let(appViewModel.prefill::request)
  }

  private fun applySecureScreen(secure: Boolean, hideRecents: Boolean = secure) {
    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!(secure || hideRecents))
  }
}
