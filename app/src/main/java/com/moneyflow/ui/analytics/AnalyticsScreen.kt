package com.moneyflow.ui.analytics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

// TODO(port): placeholder until the screen is ported from legacy_ui.
@Composable
fun AnalyticsScreen(
  onOpenTransaction: (Long) -> Unit,
  viewModel: AnalyticsViewModel = hiltViewModel()
) {
  Text("AnalyticsScreen")
}
