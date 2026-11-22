package com.moneyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import java.util.Calendar

private enum class HistoryFilterPreset(val label: String) {
  ALL("전체"),
  EXPENSE_ONLY("지출만"),
  INCOME_ONLY("수입만"),
  LARGE_EXPENSE("5만원 이상")
}

/**
 * Visual Monthly Spending History Card for the Main Dashboard, featuring
 * quick filter tabs, category pills, rich transaction list items with category icons,
 * payment methods, and click-to-edit interaction.
 */
@Composable
fun MonthlySpendingHistorySection(
  transactions: List<TransactionEntity>,
  currentMonthDisplay: String,
  onTransactionClick: ((TransactionEntity) -> Unit)?,
  onAddTransaction: () -> Unit,
  onViewAllClick: () -> Unit,
  modifier: Modifier = Modifier,
  maxDisplayCount: Int = 6
) {
  var selectedFilter by remember { mutableStateOf(HistoryFilterPreset.ALL) }
  var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

  // Extract unique categories present in the month's transactions
  val availableCategories = remember(transactions) {
    transactions.map { it.category }.distinct().take(6)
  }

  // Filter transactions
  val filteredList = remember(transactions, selectedFilter, selectedCategoryFilter) {
    transactions.filter { tx ->
      val matchesType = when (selectedFilter) {
        HistoryFilterPreset.ALL -> true
        HistoryFilterPreset.EXPENSE_ONLY -> tx.type == TransactionType.EXPENSE
        HistoryFilterPreset.INCOME_ONLY -> tx.type == TransactionType.INCOME
        HistoryFilterPreset.LARGE_EXPENSE -> tx.type == TransactionType.EXPENSE && tx.amount >= 50_000L
      }
      val matchesCategory = selectedCategoryFilter == null || tx.category == selectedCategoryFilter
      matchesType && matchesCategory
    }
  }

  val displayList = remember(filteredList, maxDisplayCount) {
    filteredList.take(maxDisplayCount)
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("monthly_spending_history_section"),
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(22.dp)) {
      // 1. Header: Title + Subtitle + "View All" link
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(TossBlue)
            )
            Text(
              text = "월간 지출 및 거래 내역",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "$currentMonthDisplay 총 ${transactions.size}건의 기록",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onViewAllClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("dashboard_view_all_ledger_btn")
        ) {
          Text(
            text = "가계부 전체보기",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TossBlue
          )
          Spacer(modifier = Modifier.width(2.dp))
          Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = TossBlue,
            modifier = Modifier.size(13.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 2. Filter Pills Row (전체, 지출만, 수입만, 5만원 이상)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        HistoryFilterPreset.entries.forEach { preset ->
          val isSelected = selectedFilter == preset
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) TossBlue else Gray100)
              .clickable { selectedFilter = preset }
              .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = preset.label,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color.White else Gray700
            )
          }
        }
      }

      // 3. Category Filter Chips (if multiple categories available)
      if (availableCategories.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          item {
            CategoryChipItem(
              label = "모든 카테고리",
              isSelected = selectedCategoryFilter == null,
              onClick = { selectedCategoryFilter = null }
            )
          }
          items(availableCategories) { cat ->
            CategoryChipItem(
              label = cat,
              isSelected = selectedCategoryFilter == cat,
              onClick = {
                selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
              }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 4. Transaction List Items
      if (displayList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gray100)
            .padding(vertical = 24.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Gray400, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "선택한 조건의 지출 내역이 없습니다.",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(TossBlue)
                .clickable { onAddTransaction() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text(
                text = "지출 기록 추가",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          displayList.forEach { tx ->
            DashboardTransactionItem(
              transaction = tx,
              onClick = { onTransactionClick?.invoke(tx) }
            )
          }
        }

        if (filteredList.size > maxDisplayCount) {
          Spacer(modifier = Modifier.height(12.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Gray100)
              .clickable { onViewAllClick() }
              .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "${filteredList.size - maxDisplayCount}개 내역 더보기",
              style = MaterialTheme.typography.labelMedium,
              color = TossBlue,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CategoryChipItem(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (isSelected) Gray800 else Gray100)
      .clickable { onClick() }
      .padding(horizontal = 10.dp, vertical = 5.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
      color = if (isSelected) Color.White else Gray700,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
    )
  }
}

@Composable
private fun DashboardTransactionItem(
  transaction: TransactionEntity,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isExpense = transaction.type == TransactionType.EXPENSE
  val categoryIcon = CategoryIconHelper.getIconForCategory(transaction.category)
  val categoryColor = Color(CategoryIconHelper.getColorForCategory(transaction.category))

  val formattedDate = remember(transaction.timestamp) {
    val cal = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    "${month}월 ${day}일 %02d:%02d".format(hour, minute)
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable { onClick() }
      .background(Gray100.copy(alpha = 0.5f))
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .testTag("dashboard_tx_item_${transaction.id}"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.weight(1f)
    ) {
      // Category Icon with Circle background
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(categoryColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          categoryIcon,
          contentDescription = transaction.category,
          tint = categoryColor,
          modifier = Modifier.size(20.dp)
        )
      }

      Column {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = transaction.memo.ifBlank { transaction.category },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Gray900,
            maxLines = 1
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = transaction.category,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = categoryColor,
            fontWeight = FontWeight.Bold
          )
          Text(text = "·", style = MaterialTheme.typography.labelSmall, color = Gray400)
          Text(
            text = transaction.accountName,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Gray600
          )
          if (transaction.paymentMethod.isNotBlank()) {
            Text(text = "·", style = MaterialTheme.typography.labelSmall, color = Gray400)
            Text(
              text = transaction.paymentMethod,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              color = Gray500
            )
          }
        }
      }
    }

    // Amount & Date
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = if (isExpense) {
          "-${Formatters.formatCurrency(transaction.amount)}"
        } else {
          "+${Formatters.formatCurrency(transaction.amount)}"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = if (isExpense) Gray900 else AccentGreen
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = formattedDate,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = Gray500
      )
    }
  }
}
