package com.moneyflow.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

// TODO(port): placeholder until the screen is ported from legacy_ui.
@Composable
fun HomeScreen(
  onAddTransaction: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  onOpenAccount: (Long) -> Unit,
  onOpenSettings: () -> Unit,
  onOpenSecurity: () -> Unit,
  onNavigateToLedger: () -> Unit,
  onNavigateToAnalytics: () -> Unit,
  onNavigateToBudget: () -> Unit,
  viewModel: HomeViewModel = hiltViewModel()
) {
  Text("HomeScreen")
}
