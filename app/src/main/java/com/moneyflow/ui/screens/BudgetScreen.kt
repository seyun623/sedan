package com.moneyflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
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
import com.moneyflow.ui.viewmodel.FinanceUiState
import com.moneyflow.util.Formatters
import java.util.Calendar

@Composable
fun BudgetScreen(
  uiState: FinanceUiState,
  onOpenSetBudget: () -> Unit,
  onOpenAddRecurring: () -> Unit,
  onToggleRecurringActive: (Long, Boolean) -> Unit,
  onExecuteRecurringNow: (RecurringTransactionEntity) -> Unit,
  onDeleteRecurring: (RecurringTransactionEntity) -> Unit,
  onOpenAddGoal: () -> Unit,
  onDepositToGoal: (SavingGoalEntity) -> Unit,
  onExportCsv: () -> Unit,
  modifier: Modifier = Modifier
) {
  val cal = Calendar.getInstance()
  val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
  val currentDay = cal.get(Calendar.DAY_OF_MONTH)
  val remainingDays = (totalDaysInMonth - currentDay + 1).coerceAtLeast(1)
  val dailyAllowance = if (uiState.remainingBudget > 0) uiState.remainingBudget / remainingDays else 0L

  val progress = uiState.budgetSpentPercentage

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 16.dp)
      .testTag("budget_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 0. Screen Header
    item {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "예산 & 정기 관리",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Gray900
          )
          Text(
            text = "카테고리별 한도, 정기 고정비, 목표 관리",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        // CSV Export quick button
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color.White,
          shadowElevation = 1.dp,
          modifier = Modifier
            .clickable { onExportCsv() }
            .testTag("export_csv_btn")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.FileDownload,
              contentDescription = "CSV 내보내기",
              tint = TossBlue,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "CSV 내보내기",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = TossBlue
            )
          }
        }
      }
    }

    // 1. Monthly Total Budget Burn-rate Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("total_budget_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${uiState.currentMonthDisplay} 남은 예산",
              style = MaterialTheme.typography.titleMedium,
              color = Gray600
            )

            val badgeColor = when {
              progress > 1f -> AccentRed
              progress > 0.8f -> AccentOrange
              else -> AccentGreen
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "${(progress * 100).toInt()}% 사용",
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = Formatters.formatCurrency(uiState.remainingBudget),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = if (uiState.remainingBudget < 0) AccentRed else Gray900
          )

          Spacer(modifier = Modifier.height(14.dp))

          LinearProgressIndicator(
            progress = { progress.coerceIn(0.01f, 1f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp)),
            color = if (progress > 1f) AccentRed else TossBlue,
            trackColor = Gray100,
            strokeCap = StrokeCap.Round
          )

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "총 설정 예산: ${Formatters.formatCurrency(uiState.currentBudget)}",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
            Text(
              text = "지출: ${Formatters.formatCurrency(uiState.monthlyExpense)}",
              style = MaterialTheme.typography.bodySmall,
              color = Gray900,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Daily Allowance Recommendation & Budget Edit Button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Gray100)
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(TossBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Savings, contentDescription = "하루 권장", tint = TossBlue, modifier = Modifier.size(16.dp))
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "하루 권장 지출액",
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray600
                )
                Text(
                  text = "하루 ${Formatters.formatCurrency(dailyAllowance)}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = TossBlue
                )
              }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = TossBlue.copy(alpha = 0.1f),
              modifier = Modifier
                .clickable { onOpenSetBudget() }
                .testTag("open_set_budget_dialog_btn")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Edit, contentDescription = "예산 수정", tint = TossBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "예산 수정",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = TossBlue
                )
              }
            }
          }
        }
      }
    }

    // 2. Category Spending Limits Section (Primary User Feature)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "카테고리별 지출 한도",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Text(
            text = "설정한 한도 대비 현재 지출 진행률",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        Text(
          text = "한도 변경",
          style = MaterialTheme.typography.labelMedium,
          color = TossBlue,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenSetBudget() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
        )
      }
    }

    items(uiState.categoryBudgetProgressList) { item ->
      CategoryBudgetProgressCard(
        item = item,
        onEditBudget = onOpenSetBudget
      )
    }

    // 3. Recurring Transactions Section (Primary User Feature)
    item {
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Repeat,
            contentDescription = null,
            tint = TossBlue,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            Text(
              text = "정기 고정 거래 (자동 등록)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Text(
              text = "월세, 급여 등 지정일에 자동 가계부 기록",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TossBlue.copy(alpha = 0.1f))
            .clickable { onOpenAddRecurring() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("open_add_recurring_btn")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = "추가", tint = TossBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "고정비 추가",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = TossBlue
            )
          }
        }
      }
    }

    if (uiState.recurringTransactions.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "등록된 정기 고정 거래가 없습니다.",
              style = MaterialTheme.typography.bodyMedium,
              color = Gray500
            )
          }
        }
      }
    } else {
      items(uiState.recurringTransactions) { item ->
        RecurringTransactionCard(
          item = item,
          onToggle = { isActive -> onToggleRecurringActive(item.id, isActive) },
          onExecuteNow = { onExecuteRecurringNow(item) },
          onDelete = { onDeleteRecurring(item) }
        )
      }
    }

    // 4. Zero-Spend Challenge Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
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
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(AccentGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = "챌린지", tint = AccentGreen, modifier = Modifier.size(18.dp))
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "무지출 데이 챌린지",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
                Text(
                  text = "지출 0원으로 알뜰하게 보낸 날",
                  style = MaterialTheme.typography.bodySmall,
                  color = Gray600
                )
              }
            }
            Text(
              text = "${uiState.noSpendDaysCount}일 달성! 🎉",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Black,
              color = AccentGreen
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            (1..7).forEach { dayIndex ->
              val isAchieved = dayIndex <= uiState.noSpendDaysCount
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(if (isAchieved) AccentGreen else Gray100),
                contentAlignment = Alignment.Center
              ) {
                if (isAchieved) {
                  Icon(Icons.Default.CheckCircle, contentDescription = "성공", tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                  Text(
                    text = "${dayIndex}일",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray600
                  )
                }
              }
            }
          }
        }
      }
    }

    // 5. Savings Goals Section
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "목돈 모으기 목표",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Gray900
        )
        Text(
          text = "목표 추가",
          style = MaterialTheme.typography.labelMedium,
          color = TossBlue,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenAddGoal() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
        )
      }
    }

    items(uiState.savingGoals) { goal ->
      SavingGoalCard(
        goal = goal,
        onDeposit = { onDepositToGoal(goal) }
      )
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun CategoryBudgetProgressCard(
  item: CategoryBudgetProgress,
  onEditBudget: () -> Unit = {}
) {
  val hasLimit = item.budgetLimit > 0
  val isOver = item.isOverBudget
  val remainingPct = if (hasLimit) ((1f - item.percentage) * 100).toInt().coerceAtLeast(0) else 100
  val catColor = Color(item.colorHex)
  val progressColor = when {
    isOver -> AccentRed
    item.percentage > 0.8f -> AccentOrange
    else -> catColor
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onEditBudget() }
      .testTag("cat_progress_card_${item.category}"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isOver) Color(0xFFFFF5F5) else Color.White
    ),
    border = if (isOver) androidx.compose.foundation.BorderStroke(1.2.dp, AccentRed.copy(alpha = 0.6f)) else null,
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(catColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = CategoryIconHelper.getIconForCategory(item.category),
              contentDescription = item.category,
              tint = catColor,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = item.category,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Text(
              text = if (hasLimit) "총 한도: ${Formatters.formatCurrency(item.budgetLimit)}" else "지출 한도 미설정",
              style = MaterialTheme.typography.labelSmall,
              color = Gray600
            )
          }
        }

        // Remaining Funds or Over-Budget Badge
        if (isOver) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = AccentRed.copy(alpha = 0.12f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(13.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${Formatters.formatCurrency(-item.remainingAmount)} 초과!",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AccentRed
              )
            }
          }
        } else if (hasLimit) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (item.percentage > 0.8f) AccentOrange.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.12f)
          ) {
            Column(
              horizontalAlignment = Alignment.End,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "남은 금액 ${Formatters.formatCurrency(item.remainingAmount)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.percentage > 0.8f) AccentOrange else AccentGreen
              )
              Text(
                text = "잔여 ${remainingPct}% 남음",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (item.percentage > 0.8f) AccentOrange else AccentGreen
              )
            }
          }
        } else {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Gray100,
            modifier = Modifier.clickable { onEditBudget() }
          ) {
            Text(
              text = "+ 한도 설정",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = TossBlue,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Progress Bar Indicating Remaining Funds
      LinearProgressIndicator(
        progress = {
          if (!hasLimit) 0f else item.percentage.coerceIn(0.01f, 1f)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = progressColor,
        trackColor = Gray100,
        strokeCap = StrokeCap.Round
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (hasLimit) "지출: ${Formatters.formatCurrency(item.spentAmount)} (${(item.percentage * 100).toInt()}%)" else "현재 지출: ${Formatters.formatCurrency(item.spentAmount)}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = Gray600
        )
        if (hasLimit) {
          Text(
            text = if (item.remainingAmount >= 0) "남은 예산: ${Formatters.formatCurrency(item.remainingAmount)}" else "초과: ${Formatters.formatCurrency(-item.remainingAmount)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isOver) AccentRed else TossBlue
          )
        }
      }
    }
  }
}

@Composable
private fun RecurringTransactionCard(
  item: RecurringTransactionEntity,
  onToggle: (Boolean) -> Unit,
  onExecuteNow: () -> Unit,
  onDelete: () -> Unit
) {
  val isIncome = item.type == TransactionType.INCOME

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("recurring_card_${item.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = if (isIncome) TossBlue.copy(alpha = 0.12f) else AccentRed.copy(alpha = 0.12f)
            ) {
              Text(
                text = if (isIncome) "매월 ${item.dayOfMonth}일 수입" else "매월 ${item.dayOfMonth}일 지출",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) TossBlue else AccentRed,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = item.category,
              style = MaterialTheme.typography.labelSmall,
              color = Gray500
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )

          Text(
            text = "${item.accountName} · ${if (item.memo.isNotBlank()) item.memo else item.paymentMethod}",
            style = MaterialTheme.typography.labelSmall,
            color = Gray600
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = if (isIncome) "+${Formatters.formatCurrency(item.amount)}" else "-${Formatters.formatCurrency(item.amount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isIncome) TossBlue else AccentRed
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
              checked = item.isActive,
              onCheckedChange = { onToggle(it) },
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TossBlue
              )
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Card action row: execute now & delete
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Gray100,
          modifier = Modifier
            .clickable { onExecuteNow() }
            .testTag("execute_recurring_now_${item.id}")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "지금 실행", tint = TossBlue, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "지금 가계부 반영",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = TossBlue
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(28.dp)
        ) {
          Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Gray400, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun SavingGoalCard(
  goal: SavingGoalEntity,
  onDeposit: () -> Unit
) {
  val progress = (goal.currentAmount.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f)

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(text = goal.iconEmoji, style = MaterialTheme.typography.titleLarge)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = goal.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Text(
              text = "목표: ${Formatters.formatCurrency(goal.targetAmount)}",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TossBlue.copy(alpha = 0.1f))
            .clickable { onDeposit() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = "+ 10만원 저금",
            style = MaterialTheme.typography.labelSmall,
            color = TossBlue,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = Color(goal.colorHex),
        trackColor = Gray100,
        strokeCap = StrokeCap.Round
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "${(progress * 100).toInt()}% 달성",
          style = MaterialTheme.typography.labelSmall,
          color = Color(goal.colorHex),
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "모은 돈 ${Formatters.formatCurrency(goal.currentAmount)}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = Gray900
        )
      }
    }
  }
}
