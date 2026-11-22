package com.moneyflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.components.ChartSlice
import com.moneyflow.ui.components.FintechDonutChart
import com.moneyflow.ui.components.MonthlySpendingTrendCard
import com.moneyflow.ui.components.RechartsCategoryBarChart
import com.moneyflow.ui.components.SpendingBreakdownCard
import com.moneyflow.ui.components.WeeklySpendBarChart
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.viewmodel.CategorySpend
import com.moneyflow.ui.viewmodel.FinanceUiState
import com.moneyflow.util.Formatters

@Composable
fun AnalyticsScreen(
  uiState: FinanceUiState,
  onPrevMonth: () -> Unit = {},
  onNextMonth: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedCategory by remember(uiState.currentMonthKey) { mutableStateOf<String?>(null) }
  var isHorizontalBar by remember { mutableStateOf(false) }

  val slices = uiState.categoryBreakdown.map {
    ChartSlice(
      value = it.amount.toFloat(),
      color = Color(it.colorHex),
      label = it.category
    )
  }

  val expenseTransactions = remember(uiState.filteredTransactions) {
    uiState.filteredTransactions.filter { it.type == TransactionType.EXPENSE }
  }

  val selectedCategoryTransactions = remember(expenseTransactions, selectedCategory) {
    if (selectedCategory == null) {
      emptyList()
    } else {
      expenseTransactions
        .filter { it.category == selectedCategory }
        .sortedByDescending { it.timestamp }
    }
  }

  val topExpenses = remember(expenseTransactions) {
    expenseTransactions
      .sortedByDescending { it.amount }
      .take(3)
  }

  val dailyAverageExpense = remember(uiState.monthlyExpense) {
    if (uiState.monthlyExpense > 0) uiState.monthlyExpense / 30L else 0L
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 16.dp)
      .testTag("analytics_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 0. Header & Month Navigator
    item {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "월간 지출 리포트",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Gray900
          )
          Text(
            text = "카테고리별 총 지출 막대 차트와 소비 패턴 요약",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        // Month selector pill
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color.White,
          shadowElevation = 1.dp
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
          ) {
            IconButton(
              onClick = onPrevMonth,
              modifier = Modifier
                .size(36.dp)
                .testTag("analytics_prev_month_btn")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "이전 달",
                tint = Gray900
              )
            }
            Text(
              text = uiState.currentMonthDisplay,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            IconButton(
              onClick = onNextMonth,
              modifier = Modifier
                .size(36.dp)
                .testTag("analytics_next_month_btn")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "다음 달",
                tint = Gray900
              )
            }
          }
        }
      }
    }

    // 1. Monthly Summary KPI Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("monthly_expense_summary_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text(
            text = "${uiState.currentMonthDisplay} 총 지출",
            style = MaterialTheme.typography.labelMedium,
            color = Gray600
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = Formatters.formatCurrency(uiState.monthlyExpense),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Gray900
          )

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(Gray100.copy(alpha = 0.7f))
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            SummaryStatItem(
              label = "월 총 수입",
              value = Formatters.formatCurrency(uiState.monthlyIncome),
              valueColor = TossBlue,
              modifier = Modifier.weight(1f)
            )
            SummaryStatItem(
              label = "일평균 지출",
              value = Formatters.formatCurrency(dailyAverageExpense),
              valueColor = Gray900,
              modifier = Modifier.weight(1f)
            )
            SummaryStatItem(
              label = "지출 건수",
              value = "${expenseTransactions.size}건",
              valueColor = Gray900,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    // 1.5. Primary Recharts-Style 'Spending Breakdown' Chart (Food, Transport, Housing, etc.)
    item {
      SpendingBreakdownCard(
        categories = uiState.categoryBreakdown,
        totalMonthlyExpense = uiState.monthlyExpense,
        monthDisplay = uiState.currentMonthDisplay,
        onCategorySelected = { selectedCategory = it }
      )
    }

    // 1.8. Interactive Monthly & Weekly Spending Trend Bar Chart Card
    item {
      MonthlySpendingTrendCard(
        monthlyTrends = uiState.monthlySpendingTrends,
        weeklyProgress = uiState.weeklySpendingProgress,
        sixMonthAverage = uiState.sixMonthAverageExpense,
        currentMonthDisplay = uiState.currentMonthDisplay
      )
    }

    // 2. Recharts-Style Category Spending Bar Chart Card (Primary Feature)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("category_bar_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "카테고리별 총 지출 막대 차트",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
              Text(
                text = "막대를 터치하면 해당 카테고리의 상세 내역을 볼 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
              )
            }

            // Orientation Toggle (Vertical vs Horizontal BarChart)
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Gray100)
                .padding(3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (!isHorizontalBar) Color.White else Color.Transparent)
                  .clickable { isHorizontalBar = false }
                  .padding(horizontal = 8.dp, vertical = 5.dp)
                  .testTag("toggle_vertical_bar_chart"),
                contentAlignment = Alignment.Center
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "세로 막대",
                    tint = if (!isHorizontalBar) TossBlue else Gray500,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "세로",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (!isHorizontalBar) FontWeight.Bold else FontWeight.Medium,
                    color = if (!isHorizontalBar) TossBlue else Gray600
                  )
                }
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isHorizontalBar) Color.White else Color.Transparent)
                  .clickable { isHorizontalBar = true }
                  .padding(horizontal = 8.dp, vertical = 5.dp)
                  .testTag("toggle_horizontal_bar_chart"),
                contentAlignment = Alignment.Center
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.AlignHorizontalLeft,
                    contentDescription = "가로 막대",
                    tint = if (isHorizontalBar) TossBlue else Gray500,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "가로",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isHorizontalBar) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHorizontalBar) TossBlue else Gray600
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          RechartsCategoryBarChart(
            categories = uiState.categoryBreakdown,
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            isHorizontal = isHorizontalBar
          )
        }
      }
    }

    // 2.5 Drill-Down Transactions for Selected Category Bar
    if (selectedCategory != null && selectedCategoryTransactions.isNotEmpty()) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_category_transactions_card"),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                val catColor = Color(FinanceCategories.getColorForCategory(selectedCategory!!))
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(catColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "'$selectedCategory' 지출 내역 (${selectedCategoryTransactions.size}건)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
              }
              Text(
                text = "닫기",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TossBlue,
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .clickable { selectedCategory = null }
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            selectedCategoryTransactions.take(5).forEachIndexed { idx, tx ->
              CategoryDrilldownTransactionRow(tx = tx)
              if (idx < selectedCategoryTransactions.take(5).lastIndex) {
                Spacer(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(Gray100)
                )
              }
            }
          }
        }
      }
    }

    // 3. Category Breakdown List
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "카테고리별 지출 순위",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Gray900
        )
        Text(
          text = "항목 터치 시 상세 필터",
          style = MaterialTheme.typography.labelSmall,
          color = Gray500
        )
      }
    }

    items(uiState.categoryBreakdown) { item ->
      val txCount = expenseTransactions.count { it.category == item.category }
      CategoryProgressCard(
        item = item,
        transactionCount = txCount,
        isSelected = selectedCategory == item.category,
        onClick = {
          selectedCategory = if (selectedCategory == item.category) null else item.category
        }
      )
    }

    // 4. Donut Chart Card (Proportional Share)
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.PieChart,
              contentDescription = null,
              tint = TossBlue,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "카테고리별 지출 도넛 비중",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          FintechDonutChart(
            slices = slices,
            modifier = Modifier.size(190.dp),
            strokeWidth = 24.dp,
            centerContent = {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "총 지출",
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray600
                )
                Text(
                  text = Formatters.formatCurrency(uiState.monthlyExpense),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Black,
                  color = Gray900
                )
              }
            }
          )

          Spacer(modifier = Modifier.height(20.dp))

          // Top insight pill
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(AccentGreen.copy(alpha = 0.1f))
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.ThumbUp,
              contentDescription = "절약",
              tint = AccentGreen,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val topCategory = uiState.categoryBreakdown.firstOrNull()
            val insightText = if (topCategory != null) {
              "이번 달 가장 지출이 큰 카테고리는 '${topCategory.category}' (${Formatters.formatCurrency(topCategory.amount)}) 입니다."
            } else {
              "이번 달 지출 내역이 아직 없습니다."
            }
            Text(
              text = insightText,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              color = AccentGreen
            )
          }
        }
      }
    }

    // 5. Weekly Spending Pattern
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text(
            text = "요일별 지출 패턴",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Text(
            text = "어느 요일에 지출이 집중되는지 확인하세요",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )

          Spacer(modifier = Modifier.height(16.dp))

          WeeklySpendBarChart(data = uiState.weeklySpendData)
        }
      }
    }

    // 6. Top Single Spends
    if (topExpenses.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "이번 달 가장 큰 지출 TOP 3",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Gray900,
          modifier = Modifier.padding(horizontal = 4.dp)
        )
      }

      items(topExpenses) { tx ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = tx.memo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
              Text(
                text = "${tx.category} · ${Formatters.formatFullDate(tx.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
              )
            }
            Text(
              text = "-${Formatters.formatCurrency(tx.amount)}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = AccentRed
            )
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun SummaryStatItem(
  label: String,
  value: String,
  valueColor: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Gray600
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.ExtraBold,
      color = valueColor
    )
  }
}

@Composable
private fun CategoryDrilldownTransactionRow(tx: TransactionEntity) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = tx.memo,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = Gray900
      )
      Text(
        text = "${Formatters.formatFullDate(tx.timestamp)} · ${tx.paymentMethod}",
        style = MaterialTheme.typography.labelSmall,
        color = Gray500
      )
    }
    Text(
      text = "-${Formatters.formatCurrency(tx.amount)}",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      color = AccentRed
    )
  }
}

@Composable
private fun CategoryProgressCard(
  item: CategorySpend,
  transactionCount: Int,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .testTag("category_card_${item.category}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) Color(0xFFF0F6FF) else Color.White
    ),
    border = if (isSelected) {
      androidx.compose.foundation.BorderStroke(1.5.dp, TossBlue)
    } else {
      null
    },
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(Color(item.colorHex))
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = item.category,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Gray900
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${transactionCount}건",
            style = MaterialTheme.typography.labelSmall,
            color = Gray500
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${(item.percentage * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = Gray600
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = Formatters.formatCurrency(item.amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      LinearProgressIndicator(
        progress = { item.percentage.coerceIn(0.01f, 1f) },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = Color(item.colorHex),
        trackColor = Gray200,
        strokeCap = StrokeCap.Round
      )
    }
  }
}
