package com.moneyflow.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.components.CategoryIconHelper
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.components.TransactionSearchFilterBar
import com.moneyflow.ui.viewmodel.DateRangePreset
import com.moneyflow.ui.viewmodel.FinanceUiState
import com.moneyflow.util.Formatters
import java.util.Calendar

@Composable
fun LedgerScreen(
  uiState: FinanceUiState,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onFilterTypeSelected: (TransactionType?) -> Unit,
  onCategoryFilterSelected: (String?) -> Unit = {},
  onSearchQueryChanged: (String) -> Unit,
  onDateRangePresetSelect: (DateRangePreset) -> Unit = {},
  onCustomDateRangeSet: (Long?, Long?) -> Unit = { _, _ -> },
  onResetAllFilters: () -> Unit = {},
  onToggleCalendarView: () -> Unit,
  onSelectCalendarDate: (Long?) -> Unit,
  onOpenAddTransaction: () -> Unit,
  onEditTransaction: (TransactionEntity) -> Unit,
  onDeleteTransaction: (TransactionEntity) -> Unit,
  onExportCsv: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("ledger_screen")
  ) {
    // 1. Top Month Navigator & View Toggle
    Surface(
      color = Color.White,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevMonth) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전 달", tint = Gray900)
            }
            Text(
              text = uiState.currentMonthDisplay,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Black,
              color = Gray900
            )
            IconButton(onClick = onNextMonth) {
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음 달", tint = Gray900)
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // CSV Export Icon Button
            IconButton(
              onClick = onExportCsv,
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Gray100)
                .testTag("ledger_export_csv_button")
            ) {
              Icon(
                Icons.Default.FileDownload,
                contentDescription = "CSV 내보내기",
                tint = TossBlue,
                modifier = Modifier.size(20.dp)
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(TossBlue)
                .clickable { onOpenAddTransaction() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("ledger_add_tx_button"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "내역 입력", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "내역 입력",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color.White,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // Toggle List vs Calendar View
            IconButton(
              onClick = onToggleCalendarView,
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Gray100)
                .testTag("toggle_calendar_button")
            ) {
              Icon(
                imageVector = if (uiState.isCalendarView) Icons.Default.FormatListBulleted else Icons.Default.CalendarMonth,
                contentDescription = "보기 모드 전환",
                tint = TossBlue
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Month In/Out Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gray100)
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(text = "총 수입", style = MaterialTheme.typography.labelSmall, color = Gray600)
            Text(
              text = "+${Formatters.formatCurrency(uiState.monthlyIncome)}",
              style = MaterialTheme.typography.titleMedium,
              color = AccentGreen,
              fontWeight = FontWeight.Bold
            )
          }
          Box(
            modifier = Modifier
              .width(1.dp)
              .height(28.dp)
              .background(Gray200)
          )
          Column(horizontalAlignment = Alignment.End) {
            Text(text = "총 지출", style = MaterialTheme.typography.labelSmall, color = Gray600)
            Text(
              text = "-${Formatters.formatCurrency(uiState.monthlyExpense)}",
              style = MaterialTheme.typography.titleMedium,
              color = AccentRed,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    val availableCategories = remember(uiState.filterType, uiState.allMonthTransactions) {
      when (uiState.filterType) {
        TransactionType.EXPENSE -> FinanceCategories.expenseCategories.map { it.first }
        TransactionType.INCOME -> FinanceCategories.incomeCategories.map { it.first }
        TransactionType.TRANSFER -> FinanceCategories.transferCategories.map { it.first }
        null -> {
          val presentCategories = uiState.allMonthTransactions.map { it.category }.distinct()
          val standardOrder = listOf(
            "식비", "카페/간식", "쇼핑", "교통", "급여", "주거/통신",
            "생활/마트", "문화/여가", "의료/건강", "금융/보험", "상여금", "계좌이체"
          )
          (presentCategories + standardOrder + FinanceCategories.expenseCategories.map { it.first } + FinanceCategories.incomeCategories.map { it.first }).distinct()
        }
      }
    }

    val categoryCounts = remember(uiState.allMonthTransactions, uiState.filterType) {
      uiState.allMonthTransactions
        .filter { uiState.filterType == null || it.type == uiState.filterType }
        .groupingBy { it.category }
        .eachCount()
    }

    // 2. Modern Search & Filter Bar (Keyword, Date Range, Category, Type)
    TransactionSearchFilterBar(
      searchQuery = uiState.searchQuery,
      onSearchQueryChange = onSearchQueryChanged,
      selectedType = uiState.filterType,
      onTypeSelect = onFilterTypeSelected,
      selectedCategory = uiState.selectedCategory,
      onCategorySelect = onCategoryFilterSelected,
      availableCategories = availableCategories,
      categoryCounts = categoryCounts,
      dateRangePreset = uiState.dateRangePreset,
      onDateRangePresetSelect = onDateRangePresetSelect,
      customStartDate = uiState.customStartDate,
      customEndDate = uiState.customEndDate,
      onCustomDateRangeSet = onCustomDateRangeSet,
      dateRangeDisplay = uiState.dateRangeDisplay,
      filteredCount = uiState.filteredTransactions.size,
      filteredExpense = uiState.filteredTotalExpense,
      filteredIncome = uiState.filteredTotalIncome,
      isAnyFilterActive = uiState.isAnyFilterActive,
      onResetAllFilters = onResetAllFilters
    )

    // 3. Content: Calendar View OR List View
    if (uiState.isCalendarView) {
      LedgerCalendarView(
        uiState = uiState,
        onSelectDate = onSelectCalendarDate
      )
    }

    // List of Transactions
    val grouped = uiState.filteredTransactions.groupBy {
      Formatters.formatDayHeader(it.timestamp)
    }

    if (grouped.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(text = "🔍", style = MaterialTheme.typography.headlineLarge)
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = if (uiState.isAnyFilterActive) {
              "검색 조건과 일치하는 내역이 없습니다"
            } else {
              "기록된 금융 내역이 없습니다"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Spacer(modifier = Modifier.height(6.dp))
          if (uiState.isAnyFilterActive) {
            Text(
              text = "키워드, 기간, 또는 카테고리 필터를 변경하거나 초기화해보세요",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(TossBlue)
                .clickable { onResetAllFilters() }
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("empty_view_reset_filters"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "모든 검색 필터 초기화",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
              )
            }
          } else {
            Text(
              text = "하단 + 버튼을 눌러 첫 지출 또는 수입을 기록해보세요",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        grouped.forEach { (dayHeader, dayTransactions) ->
          val dayExpense = dayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
          val dayIncome = dayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = dayHeader,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dayIncome > 0) {
                  Text(
                    text = "+${Formatters.formatCurrency(dayIncome)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                  )
                }
                if (dayExpense > 0) {
                  Text(
                    text = "-${Formatters.formatCurrency(dayExpense)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentRed,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }

          items(dayTransactions, key = { it.id }) { tx ->
            TransactionItemRow(
              transaction = tx,
              onEdit = { onEditTransaction(tx) },
              onDelete = { onDeleteTransaction(tx) }
            )
          }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
      }
    }
  }
}

@Composable
private fun TransactionItemRow(
  transaction: TransactionEntity,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val catColor = FinanceCategories.getColorForCategory(transaction.category)

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable { onEdit() }
      .testTag("tx_item_${transaction.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(catColor).copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = CategoryIconHelper.getIconForCategory(transaction.category),
            contentDescription = transaction.category,
            tint = Color(catColor),
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = transaction.memo,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = Gray900
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
                  color = Gray600
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = transaction.category,
              style = MaterialTheme.typography.labelSmall,
              color = Color(catColor),
              fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "· ${transaction.accountName} (${transaction.paymentMethod})",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
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

          Text(
            text = signText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor
          )
          Text(
            text = Formatters.formatTime(transaction.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = Gray400
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
          onClick = onEdit,
          modifier = Modifier
            .size(28.dp)
            .testTag("edit_tx_${transaction.id}")
        ) {
          Icon(
            Icons.Default.Edit,
            contentDescription = "수정",
            tint = TossBlue,
            modifier = Modifier.size(16.dp)
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(28.dp)
            .testTag("delete_tx_${transaction.id}")
        ) {
          Icon(
            Icons.Default.Delete,
            contentDescription = "삭제",
            tint = Gray400,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun LedgerCalendarView(
  uiState: FinanceUiState,
  onSelectDate: (Long?) -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")

      // Days of week header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        daysOfWeek.forEach { d ->
          Text(
            text = d,
            style = MaterialTheme.typography.labelSmall,
            color = if (d == "일") AccentRed else if (d == "토") TossBlue else Gray600,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Simple calendar day matrix
      val cal = Calendar.getInstance()
      val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
      val rows = (totalDays + 6) / 7

      for (r in 0 until rows) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          for (c in 0..6) {
            val dayNum = r * 7 + c + 1
            if (dayNum <= totalDays) {
              val isSelected = false
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) TossBlue else Color.Transparent)
                  .clickable {
                    val targetCal = Calendar.getInstance().apply {
                      set(Calendar.DAY_OF_MONTH, dayNum)
                    }
                    onSelectDate(targetCal.timeInMillis)
                  },
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = dayNum.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (c == 0) AccentRed else Gray900,
                    fontWeight = FontWeight.SemiBold
                  )
                  // Tiny indicator dot for activity
                  Box(
                    modifier = Modifier
                      .size(4.dp)
                      .clip(CircleShape)
                      .background(if (dayNum % 2 == 0) AccentRed else AccentGreen)
                  )
                }
              }
            } else {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

@Composable
fun CategoryFilterChipRow(
  categories: List<String>,
  selectedCategory: String?,
  transactions: List<TransactionEntity>,
  onCategorySelected: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val countMap = remember(transactions) {
    transactions.groupingBy { it.category }.eachCount()
  }

  LazyRow(
    modifier = modifier
      .fillMaxWidth()
      .testTag("category_filter_chip_row"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
  ) {
    // "전체" Chip
    item(key = "all_categories") {
      val isAllSelected = selectedCategory == null
      Surface(
        onClick = { onCategorySelected(null) },
        shape = RoundedCornerShape(20.dp),
        color = if (isAllSelected) TossBlue else Gray100,
        border = if (isAllSelected) null else BorderStroke(1.dp, Gray200),
        tonalElevation = if (isAllSelected) 2.dp else 0.dp,
        modifier = Modifier
          .height(36.dp)
          .testTag("category_chip_all")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (isAllSelected) {
            Icon(
              Icons.Default.Check,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(14.dp)
            )
          }
          Text(
            text = "전체",
            style = MaterialTheme.typography.labelMedium,
            color = if (isAllSelected) Color.White else Gray900,
            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
          )
          Text(
            text = "${transactions.size}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isAllSelected) Color.White.copy(alpha = 0.85f) else Gray600,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // Dynamic Category Chips
    items(categories, key = { it }) { category ->
      val isSelected = selectedCategory == category
      val count = countMap[category] ?: 0
      val colorHex = FinanceCategories.getColorForCategory(category)
      val catColor = Color(colorHex)

      Surface(
        onClick = {
          if (isSelected) {
            onCategorySelected(null)
          } else {
            onCategorySelected(category)
          }
        },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) catColor else Gray100,
        border = if (isSelected) null else BorderStroke(1.dp, Gray200),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier
          .height(36.dp)
          .testTag("category_chip_$category")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (isSelected) {
            Icon(
              Icons.Default.Check,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(14.dp)
            )
          } else {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(catColor)
            )
          }
          Text(
            text = category,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else Gray900,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
          )
          if (count > 0) {
            Text(
              text = "$count",
              style = MaterialTheme.typography.labelSmall,
              color = if (isSelected) Color.White.copy(alpha = 0.9f) else Gray600,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}
