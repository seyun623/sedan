package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountEntity
import com.example.data.model.AccountType
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
import com.example.util.Formatters

/**
 * Main Dashboard Hero Card that visually displays the user's Total Assets,
 * Net Worth, Asset Portfolio allocation (Bank, Savings, Investments, Cash, Debt),
 * and monthly net cashflow / savings capacity.
 */
@Composable
fun TotalAssetsDashboardCard(
  totalAssets: Long,
  totalDebts: Long,
  netWorth: Long,
  monthlyIncome: Long,
  monthlyExpense: Long,
  accounts: List<AccountEntity>,
  onOpenAddAccount: () -> Unit,
  onOpenTransfer: (AccountEntity?) -> Unit,
  modifier: Modifier = Modifier
) {
  var isPortfolioDetailExpanded by remember { mutableStateOf(false) }

  // Categorize account balances
  val bankAccounts = accounts.filter { it.accountType == AccountType.BANK }
  val savingsAccounts = accounts.filter { it.accountType == AccountType.SAVINGS }
  val investmentAccounts = accounts.filter { it.accountType == AccountType.INVESTMENT }
  val cashAccounts = accounts.filter { it.accountType == AccountType.CASH }
  val debtAccounts = accounts.filter { it.isDebt }

  val bankTotal = bankAccounts.sumOf { it.balance }
  val savingsTotal = savingsAccounts.sumOf { it.balance }
  val investTotal = investmentAccounts.sumOf { it.balance }
  val cashTotal = cashAccounts.sumOf { it.balance }
  val debtTotal = debtAccounts.sumOf { it.balance }

  // Monthly net cash flow (수입 - 지출)
  val monthlyNetCashFlow = monthlyIncome - monthlyExpense
  val savingsRatePct = if (monthlyIncome > 0) {
    ((monthlyNetCashFlow.toFloat() / monthlyIncome.toFloat()) * 100f).coerceIn(-100f, 100f)
  } else {
    0f
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("total_assets_dashboard_card"),
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(22.dp)) {
      // 1. Header: Asset Status Title + Badge + Connect Account Button
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
              .background(TossBlue)
          )
          Text(
            text = "통합 자산 대시보드",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Gray700
          )
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(AccentGreen.copy(alpha = 0.12f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "전월 대비 +4.8% ▲",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              color = AccentGreen,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Add / Connect Account Button
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TossBlue.copy(alpha = 0.08f))
            .clickable { onOpenAddAccount() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("btn_connect_asset_in_card"),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = TossBlue, modifier = Modifier.size(13.dp))
            Text(
              text = "자산 연결",
              style = MaterialTheme.typography.labelSmall,
              color = TossBlue,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 2. Primary Hero Figure: Total Assets (총 자산)
      Text(
        text = "내 총 자산",
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
          text = Formatters.formatCurrency(totalAssets),
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
          fontWeight = FontWeight.Black,
          color = Gray900,
          modifier = Modifier.testTag("total_assets_amount_text")
        )

        // Net Worth Indicator Chip
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "순자산 (부채 제외)",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Gray500
          )
          Text(
            text = Formatters.formatCurrency(netWorth),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TossBlue
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 3. Multi-segment Visual Asset Allocation Progress Bar
      VisualAssetAllocationBar(
        bankAmount = bankTotal,
        savingsAmount = savingsTotal,
        investAmount = investTotal,
        cashAmount = cashTotal,
        totalAssets = totalAssets
      )

      Spacer(modifier = Modifier.height(14.dp))

      // 4. Asset Class Mini Summary Chips Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        AssetPillSummary(
          icon = Icons.Default.AccountBalance,
          title = "입출금",
          amount = bankTotal,
          color = TossBlue,
          modifier = Modifier.weight(1f)
        )
        AssetPillSummary(
          icon = Icons.Default.Savings,
          title = "예적금",
          amount = savingsTotal,
          color = AccentTeal,
          modifier = Modifier.weight(1f)
        )
        AssetPillSummary(
          icon = Icons.Default.TrendingUp,
          title = "투자/주식",
          amount = investTotal,
          color = AccentOrange,
          modifier = Modifier.weight(1f)
        )
        if (debtTotal > 0) {
          AssetPillSummary(
            icon = Icons.Default.CreditCard,
            title = "대출/부채",
            amount = debtTotal,
            color = AccentRed,
            isDebt = true,
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 5. Monthly Net Cash Flow & Savings Capacity Bar (월간 잉여자금/저축력)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(Gray100)
          .padding(horizontal = 14.dp, vertical = 12.dp)
      ) {
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
                .size(32.dp)
                .clip(CircleShape)
                .background(if (monthlyNetCashFlow >= 0) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                if (monthlyNetCashFlow >= 0) Icons.Default.Wallet else Icons.Default.CreditCard,
                contentDescription = null,
                tint = if (monthlyNetCashFlow >= 0) AccentGreen else AccentRed,
                modifier = Modifier.size(16.dp)
              )
            }
            Column {
              Text(
                text = "이번 달 잉여 자금 (수입 - 지출)",
                style = MaterialTheme.typography.labelSmall,
                color = Gray600
              )
              Text(
                text = if (monthlyNetCashFlow >= 0) {
                  "+${Formatters.formatCurrency(monthlyNetCashFlow)} (저축률 ${savingsRatePct.toInt()}%)"
                } else {
                  "-${Formatters.formatCurrency(-monthlyNetCashFlow)} (초과 지출)"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (monthlyNetCashFlow >= 0) AccentGreen else AccentRed
              )
            }
          }

          // Expand / Collapse Details Button
          Box(
            modifier = Modifier
              .clip(CircleShape)
              .clickable { isPortfolioDetailExpanded = !isPortfolioDetailExpanded }
              .padding(4.dp)
          ) {
            Icon(
              imageVector = if (isPortfolioDetailExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
              contentDescription = "자산 포트폴리오 상세",
              tint = Gray600,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      // 6. Expandable Detailed Breakdown of Accounts
      AnimatedVisibility(visible = isPortfolioDetailExpanded) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "계좌별 세부 잔액 (${accounts.size}개 계좌)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )

          accounts.forEach { account ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Gray100.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(account.colorHex))
                )
                Column {
                  Text(
                    text = account.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Gray900
                  )
                  Text(
                    text = if (account.accountNumber.isNotBlank()) account.accountNumber else account.bankName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Gray500
                  )
                }
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = Formatters.formatCurrency(account.balance),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (account.isDebt) AccentRed else Gray900
                )
                if (!account.isDebt) {
                  Text(
                    text = "송금",
                    style = MaterialTheme.typography.labelSmall,
                    color = TossBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(TossBlue.copy(alpha = 0.1f))
                      .clickable { onOpenTransfer(account) }
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Visual multi-colored continuous allocation bar illustrating asset distribution.
 */
@Composable
private fun VisualAssetAllocationBar(
  bankAmount: Long,
  savingsAmount: Long,
  investAmount: Long,
  cashAmount: Long,
  totalAssets: Long,
  modifier: Modifier = Modifier
) {
  val safeTotal = if (totalAssets > 0) totalAssets.toFloat() else 1f

  val bankRatio = (bankAmount.toFloat() / safeTotal).coerceIn(0f, 1f)
  val savingsRatio = (savingsAmount.toFloat() / safeTotal).coerceIn(0f, 1f)
  val investRatio = (investAmount.toFloat() / safeTotal).coerceIn(0f, 1f)
  val cashRatio = (cashAmount.toFloat() / safeTotal).coerceIn(0f, 1f)

  Column(modifier = modifier.fillMaxWidth()) {
    // Segmented bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(10.dp)
        .clip(RoundedCornerShape(5.dp))
        .background(Gray200)
    ) {
      if (bankRatio > 0.01f) {
        Box(
          modifier = Modifier
            .weight(bankRatio)
            .fillMaxWidth()
            .height(10.dp)
            .background(TossBlue)
        )
      }
      if (savingsRatio > 0.01f) {
        Box(
          modifier = Modifier
            .weight(savingsRatio)
            .fillMaxWidth()
            .height(10.dp)
            .background(AccentTeal)
        )
      }
      if (investRatio > 0.01f) {
        Box(
          modifier = Modifier
            .weight(investRatio)
            .fillMaxWidth()
            .height(10.dp)
            .background(AccentOrange)
        )
      }
      if (cashRatio > 0.01f) {
        Box(
          modifier = Modifier
            .weight(cashRatio)
            .fillMaxWidth()
            .height(10.dp)
            .background(Gray500)
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Percentage legend labels
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "입출금 ${(bankRatio * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = TossBlue,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "예적금 ${(savingsRatio * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = AccentTeal,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "투자 ${(investRatio * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = AccentOrange,
        fontWeight = FontWeight.Bold
      )
      if (cashRatio > 0.03f) {
        Text(
          text = "현금 ${(cashRatio * 100).toInt()}%",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = Gray600,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

@Composable
private fun AssetPillSummary(
  icon: ImageVector,
  title: String,
  amount: Long,
  color: Color,
  isDebt: Boolean = false,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(color.copy(alpha = 0.08f))
      .padding(horizontal = 8.dp, vertical = 8.dp)
  ) {
    Column {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = Gray600,
          fontWeight = FontWeight.Medium
        )
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = Formatters.formatKoreanUnitCurrency(amount),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (isDebt) AccentRed else Gray900
      )
    }
  }
}
