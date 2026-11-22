package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import java.util.Calendar
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AccountEntity
import com.example.data.model.AccountType
import com.example.data.model.TransactionEntity
import com.example.ui.components.MonthlyCategoryPieChartCard
import com.example.ui.components.MonthlySpendingDashboardCard
import com.example.ui.components.MonthlySpendingHistorySection
import com.example.ui.components.MonthlySpendingTrendCard
import com.example.ui.components.RecentTransactionsSection
import com.example.ui.components.StackedAssetDistributionBar
import com.example.ui.components.TotalAssetsDashboardCard
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.Gray100
import com.example.ui.theme.Gray200
import com.example.ui.theme.Gray400
import com.example.ui.theme.Gray500
import com.example.ui.theme.Gray600
import com.example.ui.theme.Gray700
import com.example.ui.theme.Gray800
import com.example.ui.theme.Gray900
import com.example.ui.theme.TossBlue
import com.example.ui.theme.TossBlueLight
import com.example.ui.viewmodel.FinanceUiState
import com.example.util.Formatters

@Composable
fun HomeScreen(
  uiState: FinanceUiState,
  onOpenTransfer: (AccountEntity?) -> Unit,
  onOpenAddTransaction: () -> Unit,
  onOpenAddAccount: () -> Unit,
  onNavigateToAnalytics: () -> Unit,
  onOpenSetBudget: () -> Unit = {},
  onOpenSecurity: () -> Unit = {},
  onNavigateToLedger: () -> Unit = {},
  onEditTransaction: ((TransactionEntity) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val bankAccounts = uiState.accounts.filter { it.accountType == AccountType.BANK || it.accountType == AccountType.SAVINGS || it.accountType == AccountType.CASH }
  val investmentAccounts = uiState.accounts.filter { it.accountType == AccountType.INVESTMENT }
  val debtAccounts = uiState.accounts.filter { it.isDebt }

  val bankTotal = bankAccounts.filter { it.accountType == AccountType.BANK || it.accountType == AccountType.CASH }.sumOf { it.balance }
  val savingsTotal = bankAccounts.filter { it.accountType == AccountType.SAVINGS }.sumOf { it.balance }
  val investTotal = investmentAccounts.sumOf { it.balance }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 16.dp)
      .testTag("home_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(modifier = Modifier.height(8.dp))
      // App Bar Title & User Greeting
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "토스 & 뱅크샐러드 감성",
            style = MaterialTheme.typography.labelSmall,
            color = TossBlue,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "통합 자산 관리",
            style = MaterialTheme.typography.headlineMedium,
            color = Gray900,
            fontWeight = FontWeight.ExtraBold
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Security / Biometric lock button
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White)
              .clickable { onOpenSecurity() }
              .padding(horizontal = 10.dp, vertical = 8.dp)
              .testTag("home_security_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.Security,
                contentDescription = "생체 인증 보안 설정",
                tint = if (uiState.isBiometricEnabled) TossBlue else Gray400,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "보안",
                style = MaterialTheme.typography.labelMedium,
                color = if (uiState.isBiometricEnabled) TossBlue else Gray600,
                fontWeight = FontWeight.Bold
              )
            }
          }

          // Add Account button
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White)
              .clickable { onOpenAddAccount() }
              .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Add, contentDescription = "계좌 추가", tint = TossBlue, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "자산 연결",
                style = MaterialTheme.typography.labelMedium,
                color = TossBlue,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }

    // 1. Hero Total Assets & Net Worth Visual Dashboard Card
    item {
      TotalAssetsDashboardCard(
        totalAssets = uiState.totalAssets,
        totalDebts = uiState.totalDebts,
        netWorth = uiState.netWorth,
        monthlyIncome = uiState.monthlyIncome,
        monthlyExpense = uiState.monthlyExpense,
        accounts = uiState.accounts,
        onOpenAddAccount = onOpenAddAccount,
        onOpenTransfer = onOpenTransfer,
        modifier = Modifier.testTag("net_worth_card")
      )
    }

    // 2. Visual Monthly Spending Dashboard Card
    item {
      MonthlySpendingDashboardCard(
        monthlyExpense = uiState.monthlyExpense,
        monthlyIncome = uiState.monthlyIncome,
        currentMonthDisplay = uiState.currentMonthDisplay,
        categoryBreakdown = uiState.categoryBreakdown,
        categoryBudgetProgressList = uiState.categoryBudgetProgressList,
        allMonthTransactions = uiState.allMonthTransactions,
        currentBudget = uiState.currentBudget,
        remainingBudget = uiState.remainingBudget,
        budgetSpentPercentage = uiState.budgetSpentPercentage,
        onNavigateToAnalytics = onNavigateToAnalytics,
        onOpenSetBudget = onOpenSetBudget
      )
    }

    // Quick Action Fintech Buttons
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuickActionButton(
          title = "송금하기",
          subtitle = "수수료 무료",
          icon = Icons.Default.Send,
          iconBgColor = TossBlue,
          modifier = Modifier.weight(1f),
          onClick = { onOpenTransfer(null) },
          tag = "quick_transfer_button"
        )
        QuickActionButton(
          title = "지출 기록",
          subtitle = "간편 가계부",
          icon = Icons.Default.Add,
          iconBgColor = AccentOrange,
          modifier = Modifier.weight(1f),
          onClick = onOpenAddTransaction,
          tag = "quick_add_tx_button"
        )
        QuickActionButton(
          title = "소비 분석",
          subtitle = "리포트 보기",
          icon = Icons.Default.Analytics,
          iconBgColor = AccentTeal,
          modifier = Modifier.weight(1f),
          onClick = onNavigateToAnalytics,
          tag = "quick_analytics_button"
        )
        QuickActionButton(
          title = "예산 설정",
          subtitle = "한도 관리",
          icon = Icons.Default.Tune,
          iconBgColor = Gray700,
          modifier = Modifier.weight(1f),
          onClick = onOpenSetBudget,
          tag = "quick_budget_button"
        )
      }
    }

    // Monthly Category Spending Pie/Donut Chart Card
    item {
      MonthlyCategoryPieChartCard(
        categories = uiState.categoryBreakdown,
        totalMonthlyExpense = uiState.monthlyExpense,
        monthDisplay = uiState.currentMonthDisplay,
        onNavigateToAnalytics = onNavigateToAnalytics,
        onAddTransaction = onOpenAddTransaction
      )
    }

    // Monthly Spending Trend Interactive Bar Chart Card
    item {
      MonthlySpendingTrendCard(
        monthlyTrends = uiState.monthlySpendingTrends,
        weeklyProgress = uiState.weeklySpendingProgress,
        sixMonthAverage = uiState.sixMonthAverageExpense,
        currentMonthDisplay = uiState.currentMonthDisplay
      )
    }

    // High Polish Fintech Visual Banner
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column {
          Image(
            painter = painterResource(id = R.drawable.fintech_wealth_banner_1793095387430),
            contentDescription = "스마트 자산 솔루션 배너",
            modifier = Modifier
              .fillMaxWidth()
              .height(130.dp),
            contentScale = ContentScale.Crop
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "10월 머니 리포트가 도착했습니다",
                style = MaterialTheme.typography.titleSmall,
                color = Gray900,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "식비 지출이 줄어 이번 달 예산이 여유로워요!",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
              )
            }
            Icon(
              Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "확인",
              tint = Gray500,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }

    // 1. Bank Accounts Section
    item {
      SectionHeader(
        title = "입출금 · 예적금",
        count = bankAccounts.size,
        total = bankAccounts.sumOf { it.balance }
      )
    }

    items(bankAccounts) { account ->
      AccountCardItem(
        account = account,
        onTransfer = { onOpenTransfer(account) }
      )
    }

    // 2. Investment Accounts Section
    if (investmentAccounts.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
        SectionHeader(
          title = "투자 · 주식",
          count = investmentAccounts.size,
          total = investmentAccounts.sumOf { it.balance }
        )
      }

      items(investmentAccounts) { account ->
        AccountCardItem(
          account = account,
          onTransfer = { onOpenTransfer(account) }
        )
      }
    }

    // 3. Liabilities / Debt Section
    if (debtAccounts.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
        SectionHeader(
          title = "카드 결제예정금 · 대출",
          count = debtAccounts.size,
          total = debtAccounts.sumOf { it.balance },
          isDebt = true
        )
      }

      items(debtAccounts) { account ->
        AccountCardItem(
          account = account,
          onTransfer = { onOpenTransfer(account) }
        )
      }
    }

    // 4. Visual Monthly Spending History Section (with interactive type filter tabs & category pills)
    item {
      Spacer(modifier = Modifier.height(4.dp))
      val allTxs = uiState.allMonthTransactions.ifEmpty { uiState.recentTransactions }
      MonthlySpendingHistorySection(
        transactions = allTxs,
        currentMonthDisplay = uiState.currentMonthDisplay,
        onTransactionClick = onEditTransaction,
        onAddTransaction = onOpenAddTransaction,
        onViewAllClick = onNavigateToLedger,
        modifier = Modifier.fillMaxWidth()
      )
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun QuickActionButton(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconBgColor: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
  tag: String
) {
  Card(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .clickable { onClick() }
      .testTag(tag),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(iconBgColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(18.dp))
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Gray900
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = Gray600
      )
    }
  }
}

@Composable
private fun SectionHeader(
  title: String,
  count: Int,
  total: Long,
  isDebt: Boolean = false
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "$title $count",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = Gray900
    )
    Text(
      text = Formatters.formatCurrency(total),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = if (isDebt) AccentRed else TossBlue
    )
  }
}

@Composable
private fun AccountCardItem(
  account: AccountEntity,
  onTransfer: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("account_card_${account.id}"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
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
            .background(Color(account.colorHex)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            when (account.accountType) {
              AccountType.BANK, AccountType.SAVINGS -> Icons.Default.AccountBalance
              AccountType.INVESTMENT -> Icons.Default.TrendingUp
              AccountType.CREDIT_CARD, AccountType.LOAN -> Icons.Default.CreditCard
              else -> Icons.Default.AccountBalance
            },
            contentDescription = account.name,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Text(
            text = account.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (account.accountNumber.isNotBlank()) account.accountNumber else account.bankName,
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = Formatters.formatCurrency(account.balance),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = if (account.isDebt) AccentRed else Gray900
        )
        if (!account.isDebt) {
          Spacer(modifier = Modifier.height(4.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(TossBlue.copy(alpha = 0.1f))
              .clickable { onTransfer() }
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "송금",
              style = MaterialTheme.typography.labelSmall,
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
fun HomeBudgetProgressCard(
  uiState: FinanceUiState,
  onOpenSetBudget: () -> Unit,
  modifier: Modifier = Modifier
) {
  val totalBudget = uiState.currentBudget
  val monthlyExpense = uiState.monthlyExpense
  val remaining = uiState.remainingBudget
  val progress = uiState.budgetSpentPercentage

  if (totalBudget <= 0L) {
    Card(
      modifier = modifier
        .fillMaxWidth()
        .testTag("home_budget_card"),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(TossBlue.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Tune, contentDescription = null, tint = TossBlue, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "${uiState.currentMonthDisplay} 지출 한도 설정",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Gray900
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "월간 지출 한도를 설정하면 실시간 진행률과 일일 권장 지출액을 안내해 드려요.",
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
          onClick = onOpenSetBudget,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("home_set_budget_button")
        ) {
          Text(text = "이번 달 예산 설정하기", fontWeight = FontWeight.Bold)
        }
      }
    }
    return
  }

  val daysRemaining = remember {
    val cal = Calendar.getInstance()
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = cal.get(Calendar.DAY_OF_MONTH)
    (totalDays - today + 1).coerceAtLeast(1)
  }
  val dailyAllowance = if (remaining > 0) remaining / daysRemaining else 0L

  val animatedProgress by animateFloatAsState(
    targetValue = progress.coerceIn(0.01f, 1f),
    label = "budget_progress"
  )

  val statusColor = when {
    progress > 1f -> AccentRed
    progress > 0.8f -> AccentOrange
    else -> TossBlue
  }

  val statusText = when {
    progress > 1f -> "예산 초과"
    progress > 0.8f -> "예산 주의"
    else -> "안정적 사용 중"
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("home_budget_card"),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      // Header: Month + Status Badge + Edit Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "${uiState.currentMonthDisplay} 예산 현황",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(statusColor.copy(alpha = 0.12f))
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Text(
              text = "${(progress * 100).toInt()}% 사용",
              style = MaterialTheme.typography.labelSmall,
              color = statusColor,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Gray100)
            .clickable { onOpenSetBudget() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("home_set_budget_button"),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              Icons.Default.Tune,
              contentDescription = "예산 및 카테고리 한도 설정",
              tint = TossBlue,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "한도 설정",
              style = MaterialTheme.typography.labelSmall,
              color = TossBlue,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Main Budget Headline
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column {
          Text(
            text = if (remaining >= 0) "남은 예산" else "초과 지출",
            style = MaterialTheme.typography.labelMedium,
            color = Gray600
          )
          Text(
            text = Formatters.formatCurrency(if (remaining >= 0) remaining else -remaining),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = if (remaining < 0) AccentRed else Gray900
          )
        }
        Text(
          text = statusText,
          style = MaterialTheme.typography.labelSmall,
          color = statusColor,
          fontWeight = FontWeight.SemiBold
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Main Progress Bar Indicating Budget Usage Status
      LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(10.dp)
          .clip(RoundedCornerShape(5.dp))
          .testTag("home_budget_progress_bar"),
        color = statusColor,
        trackColor = Gray100,
        strokeCap = StrokeCap.Round
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Budget & Expense Details Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "지출 ${Formatters.formatCurrency(monthlyExpense)}",
          style = MaterialTheme.typography.bodySmall,
          color = Gray600,
          fontWeight = FontWeight.Medium
        )
        Text(
          text = "총 예산 ${Formatters.formatCurrency(totalBudget)}",
          style = MaterialTheme.typography.bodySmall,
          color = Gray900,
          fontWeight = FontWeight.Bold
        )
      }

      // Daily Allowance Recommendation Box
      Spacer(modifier = Modifier.height(10.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(Gray100)
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "남은 ${daysRemaining}일 동안 하루 권장",
            style = MaterialTheme.typography.labelSmall,
            color = Gray600
          )
          Text(
            text = if (remaining > 0) "약 ${Formatters.formatCurrency(dailyAllowance)}" else "예산 소진",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (remaining > 0) TossBlue else AccentRed
          )
        }
      }

      // Category Spending Limits Progress Section
      val categoryLimits = remember(uiState.categoryBudgetProgressList) {
        uiState.categoryBudgetProgressList.filter { it.budgetLimit > 0 }
      }

      if (categoryLimits.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Gray100)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "카테고리별 지출 한도",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          Text(
            text = "${categoryLimits.count { it.isOverBudget }}개 초과",
            style = MaterialTheme.typography.labelSmall,
            color = if (categoryLimits.any { it.isOverBudget }) AccentRed else TossBlue,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          categoryLimits.take(4).forEach { catProgress ->
            val catColor = Color(catProgress.colorHex)
            val catPct = catProgress.percentage.coerceIn(0.01f, 1f)

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("home_category_budget_progress_${catProgress.category}")
            ) {
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
                    text = catProgress.category,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Gray900
                  )
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  if (catProgress.isOverBudget) {
                    Text(
                      text = "${Formatters.formatCurrency(-catProgress.remainingAmount)} 초과",
                      style = MaterialTheme.typography.labelSmall,
                      color = AccentRed,
                      fontWeight = FontWeight.Bold
                    )
                  } else {
                    Text(
                      text = "남은 금액 ${Formatters.formatCurrency(catProgress.remainingAmount)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = TossBlue,
                      fontWeight = FontWeight.Bold
                    )
                  }
                  Text(
                    text = "(${Formatters.formatCurrency(catProgress.spentAmount)}/${Formatters.formatCurrency(catProgress.budgetLimit)})",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Gray500
                  )
                }
              }

              Spacer(modifier = Modifier.height(4.dp))

              LinearProgressIndicator(
                progress = { catPct },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp)),
                color = if (catProgress.isOverBudget) AccentRed else if (catProgress.percentage > 0.8f) AccentOrange else catColor,
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

