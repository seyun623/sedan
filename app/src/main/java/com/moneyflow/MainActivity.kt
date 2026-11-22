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
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.ui.lock.AppViewModel
import com.moneyflow.ui.lock.LockScreen
import com.moneyflow.ui.navigation.MoneyFlowNavHost
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.MoneyFlowTheme
import com.moneyflow.util.configureAppCheck
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
  private val appViewModel: AppViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this)
    enableEdgeToEdge()
    // Hide content in the recents screen until settings say otherwise.
    applySecureScreen(true)
    setContent {
      val settings by appViewModel.settings.collectAsStateWithLifecycle()
      val locked by appViewModel.locked.collectAsStateWithLifecycle()
      val s = settings
      MoneyFlowTheme(themeMode = s?.themeMode ?: com.moneyflow.data.repository.ThemeMode.SYSTEM) {
        LaunchedEffect(s?.secureScreen) { s?.let { applySecureScreen(it.secureScreen) } }
        when {
          s == null -> Box(Modifier.fillMaxSize().background(Gray100))
          s.isLockEnabled && locked -> LockScreen(
            settings = s,
            onVerifyPin = appViewModel::verifyPin,
            onBiometricSuccess = appViewModel::unlockWithBiometric
          )
          else -> MoneyFlowNavHost(messages = appViewModel.messages)
        }
      }
    }
  }

  private fun applySecureScreen(secure: Boolean) {
    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!secure)
  }
}
