package com.moneyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.moneyflow.data.model.FinanceCategories
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

/**
 * A modern, Toss-inspired LazyColumn list component that displays recent transactions
 * fetched from the Room database, formatted with appropriate icons for each expense category.
 */
@Composable
fun RecentTransactionsLazyColumn(
  transactions: List<TransactionEntity>,
  modifier: Modifier = Modifier,
  title: String = "최근 거래 내역",
  subtitle: String = "Room Database 실시간 연동",
  selectedCategory: String? = null,
  onCategorySelected: ((String?) -> Unit)? = null,
  onTransactionClick: ((TransactionEntity) -> Unit)? = null,
  onTransactionDelete: ((TransactionEntity) -> Unit)? = null,
  showCategoryFilters: Boolean = true,
  showHeader: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
) {
  var internalCategoryFilter by remember { mutableStateOf<String?>(null) }
  val activeCategory = selectedCategory ?: internalCategoryFilter

  val filteredTransactions = remember(transactions, activeCategory) {
    if (activeCategory == null) {
      transactions
    } else {
      transactions.filter { it.category == activeCategory }
    }
  }

  // Group by day for daily transaction breakdown
  val groupedByDate = remember(filteredTransactions) {
    filteredTransactions.groupBy { tx ->
      val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
      cal.set(Calendar.HOUR_OF_DAY, 0)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
      cal.timeInMillis
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("recent_transactions_lazy_column"),
    contentPadding = contentPadding,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Optional Header section
    if (showHeader) {
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Gray900
              )
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(TossBlue.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(
                text = "총 ${filteredTransactions.size}건",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TossBlue
              )
            }
          }
        }
      }
    }

    // 2. Category Filter Chips Row
    if (showCategoryFilters) {
      item {
        val expenseCats = remember {
          listOf("전체") + FinanceCategories.expenseCategories.map { it.first }
        }

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(vertical = 4.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_tx_category_filters")
        ) {
          items(expenseCats) { catName ->
            val isAll = catName == "전체"
            val isSelected = (isAll && activeCategory == null) || (activeCategory == catName)
            val icon = if (isAll) Icons.Default.ReceiptLong else CategoryIconHelper.getIconForCategory(catName)

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isSelected) TossBlue else Color.White,
              border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isSelected) TossBlue else Gray200
              ),
              modifier = Modifier
                .clickable {
                  val newCat = if (isAll) null else catName
                  if (onCategorySelected != null) {
                    onCategorySelected(newCat)
                  } else {
                    internalCategoryFilter = newCat
                  }
                }
                .testTag("category_filter_chip_$catName")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = catName,
                  tint = if (isSelected) Color.White else Gray600,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = catName,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else Gray800
                )
              }
            }
          }
        }
      }
    }

    // 3. Empty State
    if (filteredTransactions.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("recent_transactions_empty")
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Gray100),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.ReceiptLong,
                contentDescription = null,
                tint = Gray400,
                modifier = Modifier.size(28.dp)
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (activeCategory != null) "'$activeCategory' 카테고리 내역이 없습니다" else "기록된 최근 거래 내역이 없습니다",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "새 지출을 기록하거나 결제 알림을 연동해보세요",
              style = MaterialTheme.typography.bodySmall,
              color = Gray500
            )
          }
        }
      }
    } else {
      // 4. Daily Grouped Transaction Items
      groupedByDate.forEach { (dayTimestamp, dayList) ->
        val dailyExpenseTotal = dayList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val dailyIncomeTotal = dayList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val dateHeaderLabel = formatFriendlyDayHeader(dayTimestamp)

        // Day Section Header
        item(key = "header_$dayTimestamp") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = dateHeaderLabel,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = Gray700
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (dailyExpenseTotal > 0) {
                Text(
                  text = "지출 -${Formatters.formatCurrency(dailyExpenseTotal)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = AccentRed
                )
              }
              if (dailyIncomeTotal > 0) {
                Text(
                  text = "수입 +${Formatters.formatCurrency(dailyIncomeTotal)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = AccentGreen
                )
              }
            }
          }
        }

        // Transactions in this day
        items(
          items = dayList,
          key = { it.id }
        ) { transaction ->
          TransactionListItem(
            transaction = transaction,
            onClick = { onTransactionClick?.invoke(transaction) },
            onDelete = if (onTransactionDelete != null) { { onTransactionDelete(transaction) } } else null
          )
        }
      }
    }
  }
}

/**
 * A compact, high-polish Transaction List Item with category icon,
 * account badge, signed currency formatting, and touch affordance.
 */
@Composable
fun TransactionListItem(
  transaction: TransactionEntity,
  onClick: () -> Unit,
  onDelete: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val catColorLong = CategoryIconHelper.getColorForCategory(transaction.category)
  val catColor = Color(catColorLong)
  val categoryIcon = CategoryIconHelper.getIconForCategory(transaction.category)

  val signText = when (transaction.type) {
    TransactionType.EXPENSE -> "-${Formatters.formatCurrency(transaction.amount)}"
    TransactionType.INCOME -> "+${Formatters.formatCurrency(transaction.amount)}"
    TransactionType.TRANSFER -> Formatters.formatCurrency(transaction.amount)
  }

  val amountColor = when (transaction.type) {
    TransactionType.EXPENSE -> AccentRed
    TransactionType.INCOME -> AccentGreen
    TransactionType.TRANSFER -> TossBlue
  }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable { onClick() }
      .testTag("transaction_item_${transaction.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left side: Category Icon & Details
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        // Formatted Category Icon with Tinted Container
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(catColor.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = categoryIcon,
            contentDescription = transaction.category,
            tint = catColor,
            modifier = Modifier.size(22.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          // Transaction Memo / Title
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = transaction.memo,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = Gray900,
              maxLines = 1
            )
            if (transaction.isExcludedFromBudget) {
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(Gray100)
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = "예산제외",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  color = Gray600
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(3.dp))

          // Sub-details: Category tag & Account/Payment method
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(catColor.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = transaction.category,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = catColor
              )
            }

            Text(
              text = "·",
              style = MaterialTheme.typography.bodySmall,
              color = Gray400
            )

            Text(
              text = if (transaction.accountName.isNotBlank()) {
                "${transaction.accountName} (${transaction.paymentMethod})"
              } else {
                transaction.paymentMethod
              },
              style = MaterialTheme.typography.bodySmall,
              fontSize = 12.sp,
              color = Gray600,
              maxLines = 1
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Right side: Amount and Time
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = signText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = amountColor
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = Formatters.formatTime(transaction.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = Gray400
          )
        }

        if (onDelete != null) {
          Spacer(modifier = Modifier.width(4.dp))
          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .size(32.dp)
              .testTag("delete_tx_btn_${transaction.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "삭제",
              tint = Gray400,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * An embeddable Recent Transactions Section card designed for the Home screen or Overview tabs.
 * Fetches and displays recent transactions with category icons and a link to view all.
 */
@Composable
fun RecentTransactionsSection(
  transactions: List<TransactionEntity>,
  onTransactionClick: ((TransactionEntity) -> Unit)? = null,
  onViewAllClick: () -> Unit,
  modifier: Modifier = Modifier,
  maxDisplayCount: Int = 5
) {
  val recentList = remember(transactions, maxDisplayCount) {
    transactions.take(maxDisplayCount)
  }

  Card(
    modifier = modifier.testTag("home_recent_transactions_section"),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      // Header with Title and "View All >"
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "최근 거래 내역",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Text(
            text = "카테고리별 실시간 지출 내역",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onViewAllClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("home_view_all_transactions_btn")
        ) {
          Text(
            text = "전체보기",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TossBlue
          )
          Spacer(modifier = Modifier.width(2.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = TossBlue,
            modifier = Modifier.size(14.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (recentList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gray100)
            .padding(20.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "등록된 최근 거래가 없습니다",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          recentList.forEach { transaction ->
            TransactionListItem(
              transaction = transaction,
              onClick = { onTransactionClick?.invoke(transaction) }
            )
          }
        }
      }
    }
  }
}

/**
 * Formats a timestamp into a friendly Korean day header:
 * e.g., "오늘 (10월 31일)", "어제 (10월 30일)", or "10월 29일 수요일".
 */
private fun formatFriendlyDayHeader(timestamp: Long): String {
  val targetCal = Calendar.getInstance().apply { timeInMillis = timestamp }
  val todayCal = Calendar.getInstance()
  val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

  val isToday = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
    targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

  val isYesterday = targetCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
    targetCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

  val month = targetCal.get(Calendar.MONTH) + 1
  val day = targetCal.get(Calendar.DAY_OF_MONTH)

  return when {
    isToday -> "오늘 (${month}월 ${day}일)"
    isYesterday -> "어제 (${month}월 ${day}일)"
    else -> Formatters.formatDayHeader(timestamp)
  }
}
