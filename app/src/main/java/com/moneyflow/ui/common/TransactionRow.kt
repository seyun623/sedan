package com.moneyflow.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

/**
 * One transaction in a list. Tapping opens the edit form; deleting is done there (or via
 * swipe in the ledger), so the row has no small icon buttons that are easy to mis-tap.
 *
 * @param showDate show the date instead of only the time (lists that are not grouped by day)
 */
@Composable
fun TransactionRow(
  transaction: TransactionEntity,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  showDate: Boolean = false
) {
  val catColor = Color(FinanceCategories.getColorForCategory(transaction.category))
  val title = transaction.memo.ifBlank { transaction.category }
  val subtitle = when (transaction.type) {
    TransactionType.TRANSFER -> buildString {
      append(transaction.accountName)
      transaction.toAccountName?.let { append(" → ").append(it) }
    }
    else -> "${transaction.accountName} · ${transaction.paymentMethod}"
  }
  val amountText = when (transaction.type) {
    TransactionType.EXPENSE -> "-${Formatters.formatCurrency(transaction.amount)}"
    TransactionType.INCOME -> "+${Formatters.formatCurrency(transaction.amount)}"
    TransactionType.TRANSFER -> Formatters.formatCurrency(transaction.amount)
  }
  val amountColor = when (transaction.type) {
    TransactionType.EXPENSE -> AccentRed
    TransactionType.INCOME -> AccentGreen
    TransactionType.TRANSFER -> TossBlue
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(role = Role.Button, onClickLabel = "내역 수정", onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 12.dp)
      .testTag("tx_item_${transaction.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier.size(42.dp).clip(CircleShape).background(catColor.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = CategoryIconHelper.getIconForCategory(transaction.category),
        contentDescription = transaction.category,
        tint = catColor,
        modifier = Modifier.size(20.dp)
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = Gray900,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        if (transaction.isExcludedFromBudget) Badge("예산제외")
        if (transaction.source.isAutoCaptured) Badge("자동")
      }
      Spacer(Modifier.height(2.dp))
      Text(
        text = "${transaction.category} · $subtitle",
        style = MaterialTheme.typography.bodySmall,
        color = Gray600,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(amountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = amountColor, maxLines = 1)
      Text(
        text = if (showDate) Formatters.formatFullDate(transaction.timestamp) else Formatters.formatTime(transaction.timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = Gray400,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun Badge(text: String) {
  Spacer(Modifier.width(6.dp))
  Box(
    Modifier.clip(RoundedCornerShape(6.dp)).background(Gray100).padding(horizontal = 6.dp, vertical = 2.dp)
  ) { Text(text, style = MaterialTheme.typography.labelSmall, color = Gray600, maxLines = 1) }
}
