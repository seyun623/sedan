package com.moneyflow.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

// TODO(port): placeholder until the screen is ported from legacy_ui.
@Composable
fun AutoCaptureSettingsScreen(
  onBack: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel()
) {
  Text("AutoCaptureSettingsScreen")
}
