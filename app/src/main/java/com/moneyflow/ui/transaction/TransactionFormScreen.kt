package com.moneyflow.ui.transaction

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

// TODO(port): placeholder until the screen is ported from legacy_ui.
@Composable
fun TransactionFormScreen(
  onClose: () -> Unit,
  viewModel: TransactionFormViewModel = hiltViewModel()
) {
  Text("TransactionFormScreen")
}
