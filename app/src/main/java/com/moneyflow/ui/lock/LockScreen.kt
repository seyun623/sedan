package com.moneyflow.ui.lock

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.moneyflow.data.repository.AppSettings
import com.moneyflow.security.PinCheckResult

// TODO(port): placeholder until the screen is ported from legacy_ui.
@Composable
fun LockScreen(
  settings: AppSettings,
  onVerifyPin: (String, (PinCheckResult) -> Unit) -> Unit,
  onBiometricSuccess: () -> Unit
) {
  Text("LockScreen")
}
