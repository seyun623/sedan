package com.moneyflow.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.AccentTeal
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.Formatters
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(24.dp)

/** Dismissible tip suggesting the app lock. */
@Composable
internal fun LockTipCard(onOpenSecurity: () -> Unit, onDismiss: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = TossBlueContainer,
    modifier = Modifier.fillMaxWidth().testTag("home_lock_tip")
  ) {
    Row(
      modifier = Modifier
        .clickable(role = Role.Button, onClickLabel = "보안 설정 열기", onClick = onOpenSecurity)
        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Filled.Lock, contentDescription = null, tint = com.moneyflow.ui.theme.TossBlueText, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
        Text("앱 잠금을 켜 보세요", fontWeight = FontWeight.Bold, color = Gray900, style = MaterialTheme.typography.titleSmall)
        Text(
          "휴대폰을 여는 지문·얼굴·패턴으로 가계부도 잠글 수 있어요. 따로 외울 비밀번호가 없어요.",
          color = Gray700,
          style = MaterialTheme.typography.bodySmall
        )
      }
      IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "안내 닫기", tint = Gray600) }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TotalAssetsCard(state: HomeUiState, onTransfer: () -> Unit) {
  val accounts = state.accounts
  val alloc = remember(accounts) {
    val assets = accounts.filter { !it.isDebt && it.balance > 0 }
    listOf(
      Triple("입출금", assets.filter { it.accountType == AccountType.BANK }.sumOf { it.balance }, 0),
      Triple("예적금", assets.filter { it.accountType == AccountType.SAVINGS }.sumOf { it.balance }, 1),
      Triple("투자", assets.filter { it.accountType == AccountType.INVESTMENT }.sumOf { it.balance }, 2),
      Triple("현금·기타", assets.filter {
        it.accountType == AccountType.CASH || it.accountType == AccountType.CREDIT_CARD || it.accountType == AccountType.LOAN
      }.sumOf { it.balance }, 3)
    ).filter { it.second > 0 }
  }
  val allocColors = listOf(TossBlue, AccentTeal, AccentOrange, Gray500)
  val allocTotal = alloc.sumOf { it.second }
  val cashFlow = state.monthlyIncome - state.monthlyExpense

  Surface(shape = CardShape, color = CardSurface, modifier = Modifier.fillMaxWidth().testTag("total_assets_card")) {
    Column(Modifier.padding(20.dp)) {
      Text("순자산", style = MaterialTheme.typography.bodyMedium, color = Gray600)
      Text(
        Formatters.formatCurrency(state.netWorth),
        fontSize = 30.sp,
        fontWeight = FontWeight.Black,
        color = if (state.netWorth < 0) AccentRed else Gray900,
        maxLines = 1,
        modifier = Modifier.testTag("net_worth_amount_text")
      )
      Spacer(Modifier.height(4.dp))
      Row {
        Text(
          "자산 ${Formatters.formatCurrency(state.totalAssets)}",
          style = MaterialTheme.typography.bodySmall,
          color = Gray700,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        if (state.totalDebts != 0L) {
          Text(
            "  ·  부채 ${Formatters.formatCurrency(state.totalDebts)}",
            style = MaterialTheme.typography.bodySmall,
            color = AccentRed,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      if (allocTotal > 0) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Gray200)) {
          alloc.forEach { (_, amount, colorIdx) ->
            Box(Modifier.weight(amount.toFloat() / allocTotal).fillMaxHeight().background(allocColors[colorIdx]))
          }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          alloc.forEach { (label, amount, colorIdx) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(8.dp).background(allocColors[colorIdx], CircleShape))
              Spacer(Modifier.width(4.dp))
              Text(
                "$label ${(amount * 100f / allocTotal).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Gray700,
                maxLines = 1
              )
            }
          }
        }
      }

      Spacer(Modifier.height(16.dp))
      Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Gray100).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text("${state.monthLabel} 수입 − 지출", style = MaterialTheme.typography.labelSmall, color = Gray600, maxLines = 1)
          Text(
            (if (cashFlow >= 0) "+" else "-") + Formatters.formatCurrency(abs(cashFlow)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (cashFlow >= 0) AccentGreen else AccentRed,
            maxLines = 1
          )
        }
        if (state.monthlyIncome > 0) {
          val rate = (cashFlow * 100f / state.monthlyIncome).roundToInt().coerceIn(-999, 100)
          Text(
            if (cashFlow >= 0) "저축률 $rate%" else "수입보다 많이 썼어요",
            style = MaterialTheme.typography.labelMedium,
            color = Gray700,
            maxLines = 1
          )
        }
      }

      if (accounts.size >= 2) {
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = onTransfer,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("home_transfer_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
        ) {
          Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("내 계좌 간 이체", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

/** The single compact spending summary on Home (details live in 소비분석). */
@Composable
internal fun SpendingSummaryCard(
  state: HomeUiState,
  onNavigateToAnalytics: () -> Unit,
  onNavigateToBudget: () -> Unit
) {
  Surface(shape = CardShape, color = CardSurface, modifier = Modifier.fillMaxWidth().testTag("home_spending_card")) {
    Column(Modifier.padding(20.dp)) {
      Text("${state.monthLabel} 지출", style = MaterialTheme.typography.bodyMedium, color = Gray600, maxLines = 1)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          Formatters.formatCurrency(state.monthlyExpense),
          fontSize = 26.sp,
          fontWeight = FontWeight.Black,
          color = Gray900,
          maxLines = 1,
          modifier = Modifier.weight(1f, fill = false).testTag("monthly_spending_amount_text")
        )
        state.expenseChangeVsLastMonthPct?.let { pct ->
          Spacer(Modifier.width(8.dp))
          ChangeBadge(pct)
        }
      }
      if (state.monthlyIncome > 0) {
        Text(
          "수입 ${Formatters.formatCurrency(state.monthlyIncome)}",
          style = MaterialTheme.typography.bodySmall,
          color = AccentGreen,
          maxLines = 1
        )
      }

      Spacer(Modifier.height(16.dp))
      val budget = state.budget
      if (budget?.isSet == true) {
        BudgetProgress(state, onNavigateToBudget)
      } else {
        BudgetPrompt(onNavigateToBudget)
      }

      val top = state.categoryBreakdown.take(3)
      if (top.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text("많이 쓴 카테고리", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Gray900)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          top.forEach { cat ->
            val color = Color(cat.colorHex)
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                  cat.category,
                  style = MaterialTheme.typography.bodySmall,
                  color = Gray900,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                Text(
                  "${Formatters.formatCurrency(cat.amount)} · ${(cat.percentage * 100).roundToInt()}%",
                  style = MaterialTheme.typography.bodySmall,
                  color = Gray700,
                  maxLines = 1
                )
              }
              Spacer(Modifier.height(4.dp))
              LinearProgressIndicator(
                progress = { cat.percentage.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = Gray100,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {}
              )
            }
          }
        }
      }

      Spacer(Modifier.height(8.dp))
      TextButton(
        onClick = onNavigateToAnalytics,
        modifier = Modifier.align(Alignment.End).testTag("home_analytics_link")
      ) {
        Text("소비분석 보기", color = com.moneyflow.ui.theme.TossBlueText, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = com.moneyflow.ui.theme.TossBlueText)
      }
    }
  }
}

@Composable
private fun ChangeBadge(pct: Float) {
  val up = pct > 0f
  val color = if (up) AccentRed else AccentGreen
  val label = when {
    abs(pct) < 0.5f -> "지난달과 비슷"
    up -> "지난달보다 ${abs(pct).roundToInt()}% ▲"
    else -> "지난달보다 ${abs(pct).roundToInt()}% ▼"
  }
  Text(
    label,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = if (abs(pct) < 0.5f) Gray600 else color,
    maxLines = 1,
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(if (abs(pct) < 0.5f) Gray100 else color.copy(alpha = 0.12f))
      .padding(horizontal = 6.dp, vertical = 3.dp)
  )
}

@Composable
private fun BudgetProgress(state: HomeUiState, onNavigateToBudget: () -> Unit) {
  val total = state.budget?.total ?: 0L
  val over = state.remainingBudget < 0
  val ratio = state.budgetSpentRatio
  val animated by animateFloatAsState(targetValue = ratio, label = "home_budget_progress")
  val color = when {
    over -> AccentRed
    ratio > 0.8f -> AccentOrange
    else -> TossBlue
  }
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(Gray100)
      .clickable(role = Role.Button, onClickLabel = "예산 보기", onClick = onNavigateToBudget)
      .padding(14.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("이번 달 예산", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.weight(1f))
      Text(
        if (over) "${Formatters.formatCurrency(-state.remainingBudget)} 초과" else "${Formatters.formatCurrency(state.remainingBudget)} 남음",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1
      )
    }
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
      progress = { animated },
      modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).testTag("home_budget_progress_bar"),
      color = color,
      trackColor = Gray200,
      strokeCap = StrokeCap.Round,
      drawStopIndicator = {}
    )
    Spacer(Modifier.height(6.dp))
    Text(
      "예산 지출 ${Formatters.formatCurrency(state.budgetExpense)} / ${Formatters.formatCurrency(total)}",
      style = MaterialTheme.typography.labelSmall,
      color = Gray600,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun BudgetPrompt(onNavigateToBudget: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(Gray100)
      .clickable(role = Role.Button, onClick = onNavigateToBudget)
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag("home_set_budget_prompt"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(Icons.Filled.Tune, contentDescription = null, tint = com.moneyflow.ui.theme.TossBlueText, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text("예산을 설정해 보세요", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Gray900)
      Text("한 달 예산을 정하면 남은 금액을 알려드려요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Gray500)
  }
}

@Composable
internal fun AccountCard(account: AccountEntity, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = CardSurface,
    modifier = Modifier.fillMaxWidth().testTag("account_card_${account.id}")
  ) {
    Row(
      modifier = Modifier
        .clickable(role = Role.Button, onClickLabel = "계좌 상세 보기", onClick = onClick)
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color(account.colorHex)),
        contentAlignment = Alignment.Center
      ) {
        Icon(accountIcon(account.accountType), contentDescription = account.accountType.displayName, tint = OnBrand, modifier = Modifier.size(20.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            account.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Gray900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          if (account.isFavorite) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Star, contentDescription = "즐겨찾기", tint = AccentOrange, modifier = Modifier.size(14.dp))
          }
        }
        Text(
          account.accountNumber.ifBlank { account.bankName },
          style = MaterialTheme.typography.bodySmall,
          color = Gray600,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Spacer(Modifier.width(8.dp))
      Text(
        Formatters.formatCurrency(account.balance),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (account.isDebt || account.balance < 0) AccentRed else Gray900,
        maxLines = 1
      )
    }
  }
}

private fun accountIcon(type: AccountType): ImageVector = when (type) {
  AccountType.BANK -> Icons.Filled.AccountBalance
  AccountType.SAVINGS -> Icons.Filled.Savings
  AccountType.INVESTMENT -> Icons.AutoMirrored.Filled.TrendingUp
  AccountType.CREDIT_CARD, AccountType.LOAN -> Icons.Filled.CreditCard
  AccountType.CASH -> Icons.Filled.Payments
}

/** "이번 기간 카드 실적" summary: one row per card with 실적 settings. */
@Composable
internal fun CardGoalsCard(
  statuses: List<com.moneyflow.domain.card.CardStatus>,
  names: Map<Long, String>,
  onOpenAccount: (Long) -> Unit
) {
  androidx.compose.material3.Surface(
    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    color = CardSurface,
    modifier = Modifier.fillMaxWidth().testTag("home_card_goals")
  ) {
    Column(Modifier.padding(20.dp)) {
      Text("카드 실적", fontWeight = FontWeight.Bold, color = Gray900, style = MaterialTheme.typography.titleMedium)
      statuses.forEach { st ->
        Column(
          Modifier.fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpenAccount(st.accountId) }
            .padding(vertical = 8.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(names[st.accountId] ?: "카드", fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              if (st.achieved) "달성" else "${Formatters.formatCurrency(st.shortage)} 남음",
              color = if (st.achieved) AccentGreen else if (st.needsAttention) AccentOrange else Gray600,
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodyMedium
            )
          }
          androidx.compose.material3.LinearProgressIndicator(
            progress = { st.progress },
            color = if (st.achieved) AccentGreen else if (st.needsAttention) AccentOrange else TossBlue,
            trackColor = Gray200,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
          )
          Text(
            "${Formatters.formatCurrency(st.performance)} / ${Formatters.formatCurrency(st.target)} · ${st.daysLeft}일 남음" +
              if (st.totalBenefit > 0) " · 혜택 ${Formatters.formatCurrency(st.totalBenefit)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      }
    }
  }
}

/** "어디서 어떤 카드?": the card with the most benefit for each benefit category / merchant. */
@Composable
internal fun CardGuideCard(
  rows: List<com.moneyflow.domain.card.CardGuideRow>,
  names: Map<Long, String>,
  onOpenAccount: (Long) -> Unit
) {
  var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
  val shown = if (expanded) rows else rows.take(GUIDE_ROWS)
  androidx.compose.material3.Surface(
    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    color = CardSurface,
    modifier = Modifier.fillMaxWidth().testTag("home_card_guide")
  ) {
    Column(Modifier.padding(20.dp)) {
      Text("어디서 어떤 카드?", fontWeight = FontWeight.Bold, color = Gray900, style = MaterialTheme.typography.titleMedium)
      Text("등록한 혜택과 남은 한도로 계산했어요", style = MaterialTheme.typography.bodySmall, color = Gray600)
      shown.forEach { row ->
        val best = row.best
        Column(
          Modifier.fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpenAccount(best.accountId) }
            .padding(vertical = 8.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.label, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(names[best.accountId] ?: "카드", fontWeight = FontWeight.Bold, color = com.moneyflow.ui.theme.TossBlueText, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Text(
            buildString {
              append("${Formatters.formatCurrency(row.typicalAmount)} 결제 시 약 ${Formatters.formatCurrency(best.expected)} 혜택")
              best.benefit?.let { append(" · ${it.title}") }
              row.runnerUp?.let { r -> append(" (다음: ${names[r.accountId] ?: "카드"} ${Formatters.formatCurrency(r.expected)})") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = Gray600,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
      }
      if (rows.size > GUIDE_ROWS) {
        androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
          Text(if (expanded) "접기" else "${rows.size - GUIDE_ROWS}개 더 보기")
        }
      }
    }
  }
}

private const val GUIDE_ROWS = 4
