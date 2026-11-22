package com.moneyflow.ui.ledger

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

// TODO(port): placeholder until the screen is ported from legacy_ui.
@Composable
fun LedgerScreen(
  onAddTransaction: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  viewModel: LedgerViewModel = hiltViewModel()
) {
  Text("LedgerScreen")
}
