package com.moneyflow.ui.account

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

// TODO(port): placeholder until the screen is ported from legacy_ui.
@Composable
fun AccountDetailScreen(
  onBack: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  viewModel: AccountDetailViewModel = hiltViewModel()
) {
  Text("AccountDetailScreen")
}
