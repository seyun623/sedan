package com.moneyflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.data.model.CategoryBudgetProgress
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.AccentTeal
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.viewmodel.CategorySpend
import com.moneyflow.util.Formatters
import java.util.Calendar

/**
 * Main Screen Dashboard Card that visually displays monthly spending metrics,
 * spending-to-income ratio, daily average spending, top category distributions,
 * and budget remaining funds.
 */
@Composable
fun MonthlySpendingDashboardCard(
  monthlyExpense: Long,
  monthlyIncome: Long,
  currentMonthDisplay: String,
  categoryBreakdown: List<CategorySpend>,
  categoryBudgetProgressList: List<CategoryBudgetProgress>,
  allMonthTransactions: List<TransactionEntity>,
  currentBudget: Long,
  remainingBudget: Long,
  budgetSpentPercentage: Float,
  onNavigateToAnalytics: () -> Unit,
  onOpenSetBudget: () -> Unit,
  modifier: Modifier = Modifier
) {
  val expensesOnly = remember(allMonthTransactions) {
    allMonthTransactions.filter { it.type == TransactionType.EXPENSE }
  }
  val expenseCount = expensesOnly.size

  // Calculate day-of-month for daily average spending
  val daysPassed = remember {
    val cal = Calendar.getInstance()
    cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
  }
  val dailyAverageExpense = if (daysPassed > 0) monthlyExpense / daysPassed else 0L

  // Expense to Income ratio (%)
  val spendingRatio = if (monthlyIncome > 0) {
    ((monthlyExpense.toFloat() / monthlyIncome.toFloat()) * 100f).coerceIn(0f, 200f)
  } else {
    0f
  }

  // Top spending category
  val topCategory = remember(categoryBreakdown) {
    categoryBreakdown.maxByOrNull { it.amount }
  }

  val animatedBudgetProgress by animateFloatAsState(
    targetValue = budgetSpentPercentage.coerceIn(0.01f, 1f),
    label = "dashboard_budget_progress"
  )

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("monthly_spending_dashboard_card"),
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(22.dp)) {
      // 1. Header: Month title + Analytics shortcut
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(AccentOrange)
          )
          Text(
            text = "$currentMonthDisplay 지출 브리핑",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Gray700
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onNavigateToAnalytics() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("dashboard_spending_analytics_shortcut")
        ) {
          Text(
            text = "소비 리포트",
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

      // 2. Primary Headline: Monthly Spending
      Text(
        text = "이번 달 총 지출",
        style = MaterialTheme.typography.bodySmall,
        color = Gray600,
        fontWeight = FontWeight.Medium
      )
      Spacer(modifier = Modifier.height(2.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Text(
          text = Formatters.formatCurrency(monthlyExpense),
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
          fontWeight = FontWeight.Black,
          color = Gray900,
          modifier = Modifier.testTag("monthly_spending_amount_text")
        )

        // Spending ratio badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
              when {
                spendingRatio > 80f -> AccentRed.copy(alpha = 0.12f)
                spendingRatio > 50f -> AccentOrange.copy(alpha = 0.12f)
                else -> AccentGreen.copy(alpha = 0.12f)
              }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "수입 대비 ${spendingRatio.toInt()}% 소비",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = when {
              spendingRatio > 80f -> AccentRed
              spendingRatio > 50f -> AccentOrange
              else -> AccentGreen
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 3. 4-Pillar Visual Metrics Grid (수입, 일평균, 최대지출, 지출건수)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MetricItemBox(
          icon = Icons.Default.TrendingUp,
          iconTint = AccentGreen,
          label = "월 총 수입",
          value = Formatters.formatCurrency(monthlyIncome),
          modifier = Modifier.weight(1f)
        )
        MetricItemBox(
          icon = Icons.Default.CalendarToday,
          iconTint = TossBlue,
          label = "일평균 지출",
          value = Formatters.formatCurrency(dailyAverageExpense),
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MetricItemBox(
          icon = Icons.Default.LocalOffer,
          iconTint = AccentOrange,
          label = "최대 지출",
          value = topCategory?.let { "${it.category} (${(it.percentage * 100).toInt()}%)" } ?: "기록 없음",
          modifier = Modifier.weight(1f)
        )
        MetricItemBox(
          icon = Icons.Default.Receipt,
          iconTint = AccentTeal,
          label = "총 지출 건수",
          value = "${expenseCount}건",
          modifier = Modifier.weight(1f)
        )
      }

      // 4. Budget Status & Remaining Funds Gauge (if budget set)
      if (currentBudget > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gray100)
            .padding(14.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = "월간 예산 현황",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
                Text(
                  text = if (remainingBudget >= 0) {
                    "남은 자금 ${Formatters.formatCurrency(remainingBudget)}"
                  } else {
                    "${Formatters.formatCurrency(-remainingBudget)} 초과"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (remainingBudget >= 0) TossBlue else AccentRed
                )
              }

              // Edit Budget Link
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .clickable { onOpenSetBudget() }
                  .padding(horizontal = 4.dp, vertical = 2.dp)
              ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Gray600, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                  text = "설정",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                  color = Gray600,
                  fontWeight = FontWeight.Medium
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
              progress = { animatedBudgetProgress },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
              color = when {
                budgetSpentPercentage > 1f -> AccentRed
                budgetSpentPercentage > 0.8f -> AccentOrange
                else -> TossBlue
              },
              trackColor = Gray200,
              strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "지출 ${Formatters.formatCurrency(monthlyExpense)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Gray600
              )
              Text(
                text = "한도 ${Formatters.formatCurrency(currentBudget)} (${(budgetSpentPercentage * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Gray800,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      // 5. Visual Top Spending Categories Breakdown
      if (categoryBreakdown.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "주요 소비 카테고리 TOP 4",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = Gray900
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          categoryBreakdown.take(4).forEach { cat ->
            val catColor = Color(cat.colorHex)
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(8.dp)
                      .clip(CircleShape)
                      .background(catColor)
                  )
                  Text(
                    text = cat.category,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Gray900
                  )
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = Formatters.formatCurrency(cat.amount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Gray900
                  )
                  Text(
                    text = "${(cat.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Gray500
                  )
                }
              }

              Spacer(modifier = Modifier.height(4.dp))

              LinearProgressIndicator(
                progress = { cat.percentage.coerceIn(0.01f, 1f) },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(5.dp)
                  .clip(RoundedCornerShape(3.dp)),
                color = catColor,
                trackColor = Gray100,
                strokeCap = StrokeCap.Round
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricItemBox(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconTint: Color,
  label: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(Gray100)
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Column {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(13.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = Gray600
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gray900,
        maxLines = 1
      )
    }
  }
}
