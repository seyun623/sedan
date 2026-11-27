package com.moneyflow.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.data.model.TransactionType
import com.moneyflow.domain.CategorySpend
import com.moneyflow.domain.MonthlyTrendItem
import com.moneyflow.ui.common.EmptyState
import com.moneyflow.ui.common.MonthSwitcher
import com.moneyflow.ui.common.TransactionRow
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.Formatters
import kotlin.math.abs

@Composable
fun AnalyticsScreen(
  onOpenTransaction: (Long) -> Unit,
  onOpenYearly: () -> Unit = {},
  onOpenTax: () -> Unit = {},
  viewModel: AnalyticsViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  AnalyticsContent(
    state = state,
    onPrevMonth = viewModel::previousMonth,
    onNextMonth = viewModel::nextMonth,
    onCurrentMonth = viewModel::goToCurrentMonth,
    onOpenTransaction = onOpenTransaction,
    onOpenYearly = onOpenYearly,
    onOpenTax = onOpenTax
  )
}

@Composable
private fun AnalyticsContent(
  state: AnalyticsUiState,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onCurrentMonth: () -> Unit,
  onOpenTransaction: (Long) -> Unit,
  onOpenYearly: () -> Unit = {},
  onOpenTax: () -> Unit = {}
) {
  val monthKey = state.month.toString()
  var selectedCategory by rememberSaveable(monthKey) { mutableStateOf<String?>(null) }
  var selectedTag by rememberSaveable(monthKey) { mutableStateOf<String?>(null) }
  // Clear the selection when that category no longer has spending (deleted / edited away).
  LaunchedEffect(state.categoryBreakdown) {
    if (selectedCategory != null && state.categoryBreakdown.none { it.category == selectedCategory }) selectedCategory = null
  }
  // "이번 달" only for the current month; otherwise "11월".
  val periodLabel = if (state.isCurrentMonth) "이번 달" else "${state.month.monthValue}월"

  val expenses = remember(state.monthTransactions) {
    state.monthTransactions.filter { it.type == TransactionType.EXPENSE }
  }
  val categories = remember(state.categoryBreakdown) { state.categoryBreakdown.sortedByDescending { it.amount } }
  val selectedCategoryTxs = remember(expenses, selectedCategory) {
    selectedCategory?.let { cat -> expenses.filter { it.category == cat }.sortedByDescending { it.amount } }.orEmpty()
  }
  val countsByCategory = remember(expenses) { expenses.groupingBy { it.category }.eachCount() }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("analytics_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item(key = "header") {
      Column {
        Text(
          text = "소비 분석",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Black,
          color = Gray900
        )
        MonthSwitcher(
          label = state.monthLabel,
          onPrevious = onPrevMonth,
          onNext = onNextMonth,
          showToday = !state.isCurrentMonth,
          onToday = onCurrentMonth,
          modifier = Modifier.padding(start = 0.dp)
        )
      }
    }

    if (state.isLoading) {
      item(key = "loading") {
        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = TossBlue)
        }
      }
      return@LazyColumn
    }

    if (state.monthTransactions.isEmpty()) {
      item(key = "empty") {
        SectionCard {
          EmptyState(
            icon = Icons.Default.BarChart,
            title = "${state.monthLabel}에 기록된 거래가 없어요",
            description = "거래를 기록하면 카테고리·요일별 소비 패턴을 분석해 드려요."
          )
        }
      }
    } else {
      item(key = "summary") {
        SummaryCard(state = state, periodLabel = periodLabel, expenseCount = expenses.size)
      }

      item(key = "category") {
        SectionCard {
          SectionTitle("카테고리별 지출", "그래프나 항목을 누르면 해당 카테고리 내역을 볼 수 있어요")
          Spacer(Modifier.height(16.dp))
          if (categories.isEmpty()) {
            EmptyState(icon = Icons.Default.BarChart, title = "$periodLabel 지출이 없어요")
          } else {
            CategorySection(
              categories = categories,
              total = state.monthlyExpense,
              counts = countsByCategory,
              selectedCategory = selectedCategory,
              onSelect = { selectedCategory = it }
            )
          }
        }
      }

      if (selectedCategory != null && selectedCategoryTxs.isNotEmpty()) {
        item(key = "drilldown_header") {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "'$selectedCategory' 지출 ${selectedCategoryTxs.size}건",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { selectedCategory = null }) {
              Icon(Icons.Default.Close, contentDescription = "카테고리 선택 해제", tint = Gray600)
            }
          }
        }
        items(selectedCategoryTxs.take(10), key = { "cat_${it.id}" }) { tx ->
          TransactionRow(
            transaction = tx,
            onClick = { onOpenTransaction(tx.id) },
            showDate = true,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(CardSurface)
          )
        }
      }

      item(key = "weekday") {
        SectionCard {
          SectionTitle("요일별 지출", "어느 요일에 지출이 몰리는지 확인해 보세요")
          Spacer(Modifier.height(16.dp))
          WeekdaySpendChart(data = state.weekdaySpend)
          val peak = state.weekdaySpend.maxByOrNull { it.second }
          if (peak != null && peak.second > 0) {
            Spacer(Modifier.height(12.dp))
            InsightPill("${peak.first}요일에 가장 많이 썼어요 (${Formatters.formatCurrency(peak.second)})")
          }
        }
      }
    }

    if (state.monthlyTrends.any { it.totalExpense > 0 }) {
      item(key = "trend") {
        TrendCard(trends = state.monthlyTrends, average = state.averageMonthlyExpense, monthKey = monthKey)
      }
    }

    if (state.monthTransactions.isNotEmpty()) {
      if (state.weeklyProgress.isNotEmpty()) {
        item(key = "weekly") {
          SectionCard {
            SectionTitle("주차별 지출", "${state.monthLabel} 주마다 얼마나 썼는지 보여드려요")
            Spacer(Modifier.height(16.dp))
            WeeklyProgressList(weeks = state.weeklyProgress)
          }
        }
      }

      if (state.tags.isNotEmpty()) {
        item(key = "tags") {
          TagCard(
            tags = state.tags,
            periodLabel = periodLabel,
            selected = selectedTag,
            onSelect = { selectedTag = if (selectedTag == it) null else it }
          )
        }
        state.tags.firstOrNull { it.tag == selectedTag }?.let { t ->
          items(t.transactions, key = { "tag_${it.id}" }) { tx ->
            TransactionRow(
              transaction = tx,
              onClick = { onOpenTransaction(tx.id) },
              showDate = true,
              modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(CardSurface)
            )
          }
        }
      }

      val top = state.topExpenses.take(5)
      if (top.isNotEmpty()) {
        item(key = "top_header") {
          Text(
            text = "$periodLabel 가장 큰 지출",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
          )
        }
        items(top, key = { "top_${it.id}" }) { tx ->
          TransactionRow(
            transaction = tx,
            onClick = { onOpenTransaction(tx.id) },
            showDate = true,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(CardSurface)
          )
        }
      }
    }

    if (state.netWorth.isNotEmpty()) {
      item(key = "net_worth") { NetWorthCard(state.netWorth) }
    }
    item(key = "yearly") {
      SectionCard(modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onOpenYearly).testTag("open_yearly_report")) {
        SectionTitle("${state.month.year}년 결산 보기 →", "1년 동안 번 돈·쓴 돈, 가장 많이 쓴 곳을 한눈에 보고 이미지로 공유해요")
      }
    }
    item(key = "tax") {
      SectionCard(modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onOpenTax).testTag("open_tax_preview")) {
        SectionTitle("연말정산 카드 소득공제 미리보기 →", "올해 신용·체크카드, 전통시장·대중교통 사용액으로 공제 예상액과 어떤 결제가 유리한지 알려 줘요")
      }
    }
  }
}

@Composable
private fun NetWorthCard(points: List<com.moneyflow.domain.report.NetWorthPoint>) {
  val last = points.last()
  val prev = points.getOrNull(points.size - 2)
  SectionCard(modifier = Modifier.testTag("net_worth_card")) {
    SectionTitle("순자산 추이", "자산에서 부채(카드 대금·대출)를 뺀 금액이에요. 지난달까지는 기록으로 거꾸로 계산한 추정치예요")
    Text(
      Formatters.formatCurrency(last.netWorth),
      style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
      color = if (last.netWorth < 0) com.moneyflow.ui.theme.AccentRedText else Gray900,
      maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 8.dp)
    )
    prev?.let { p ->
      val diff = last.netWorth - p.netWorth
      Text(
        (if (diff >= 0) "지난달보다 +" else "지난달보다 -") + Formatters.formatCurrency(kotlin.math.abs(diff)),
        style = MaterialTheme.typography.bodySmall, color = if (diff >= 0) com.moneyflow.ui.theme.AccentGreenText else com.moneyflow.ui.theme.AccentRedText
      )
    }
    if (points.size >= 2) {
      val line = TossBlue
      val grid = Gray200
      val min = points.minOf { it.netWorth }
      val max = points.maxOf { it.netWorth }
      val span = (max - min).coerceAtLeast(1)
      val chartDescription = "월말 순자산: " + points.joinToString(", ") { "${it.month.monthValue}월 ${Formatters.formatCurrency(it.netWorth)}" }
      androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)
          .semantics { contentDescription = chartDescription }
      ) {
        val stepX = size.width / (points.size - 1)
        fun y(v: Long) = size.height - (v - min).toFloat() / span * size.height
        drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 2f)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, p ->
          val x = i * stepX
          if (i == 0) path.moveTo(x, y(p.netWorth)) else path.lineTo(x, y(p.netWorth))
        }
        drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
        points.forEachIndexed { i, p -> drawCircle(line, radius = 7f, center = androidx.compose.ui.geometry.Offset(i * stepX, y(p.netWorth))) }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${points.first().month.monthValue}월", style = MaterialTheme.typography.labelSmall, color = Gray600)
        Text("${last.month.monthValue}월", style = MaterialTheme.typography.labelSmall, color = Gray600)
      }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("자산 ${Formatters.formatCurrency(last.assets)}", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.weight(1f))
      Text("부채 ${Formatters.formatCurrency(last.debts)}", style = MaterialTheme.typography.bodySmall, color = com.moneyflow.ui.theme.AccentRedText)
    }
  }
}

// ------------------------------------------------------------------------------------------------

@Composable
private fun SummaryCard(state: AnalyticsUiState, periodLabel: String, expenseCount: Int) {
  SectionCard(modifier = Modifier.testTag("monthly_expense_summary_card")) {
    Text("$periodLabel 총 지출", style = MaterialTheme.typography.labelLarge, color = Gray600)
    Spacer(Modifier.height(4.dp))
    Text(
      text = Formatters.formatCurrency(state.monthlyExpense),
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Black,
      color = Gray900,
      maxLines = 1
    )
    Spacer(Modifier.height(16.dp))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Gray100)
        .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
      StatItem("수입", Formatters.formatCurrency(state.monthlyIncome), AccentGreen, Modifier.weight(1f))
      StatItem(
        label = "일평균 지출",
        value = Formatters.formatCurrency(state.dailyAverageExpense),
        valueColor = Gray900,
        modifier = Modifier.weight(1f),
        caption = "${state.elapsedDays}일 기준"
      )
      StatItem("지출 건수", "${expenseCount}건", Gray900, Modifier.weight(1f))
    }
  }
}

@Composable
private fun StatItem(
  label: String,
  value: String,
  valueColor: Color,
  modifier: Modifier = Modifier,
  caption: String? = null
) {
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Gray600, maxLines = 1)
    Spacer(Modifier.height(4.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.ExtraBold,
      color = valueColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    if (caption != null) {
      Text(caption, style = MaterialTheme.typography.labelSmall, color = Gray500, maxLines = 1)
    }
  }
}

@Composable
private fun CategorySection(
  categories: List<CategorySpend>,
  total: Long,
  counts: Map<String, Int>,
  selectedCategory: String?,
  onSelect: (String?) -> Unit
) {
  val selected = categories.firstOrNull { it.category == selectedCategory }
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    CategoryDonutChart(
      categories = categories,
      selectedCategory = selectedCategory,
      onSelectCategory = onSelect,
      modifier = Modifier.size(200.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
        Text(
          text = selected?.category ?: "총 지출",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = Formatters.formatCurrency(selected?.amount ?: total),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Black,
          color = Gray900,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (selected != null) {
          Text(
            text = "${(selected.percentage * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(selected.colorHex)
          )
        }
      }
    }
  }
  Spacer(Modifier.height(20.dp))
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    categories.forEachIndexed { index, item ->
      CategoryRankRow(
        rank = index + 1,
        item = item,
        count = counts[item.category] ?: 0,
        isSelected = item.category == selectedCategory,
        onClick = { onSelect(if (item.category == selectedCategory) null else item.category) }
      )
    }
  }
}

@Composable
private fun CategoryRankRow(
  rank: Int,
  item: CategorySpend,
  count: Int,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val color = Color(item.colorHex)
  val track = Gray200
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) TossBlueContainer else Color.Transparent)
      .clickable(role = Role.Button, onClickLabel = "${item.category} 내역 보기", onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 10.dp)
      .testTag("category_rank_${item.category}")
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "$rank",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (rank <= 3) TossBlue else Gray500,
        modifier = Modifier.width(22.dp)
      )
      Box(Modifier.size(10.dp).clip(CircleShape).background(color))
      Spacer(Modifier.width(8.dp))
      Text(
        text = item.category,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = Gray900,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
      )
      Spacer(Modifier.width(6.dp))
      Text("${count}건", style = MaterialTheme.typography.labelSmall, color = Gray500, maxLines = 1)
      Spacer(Modifier.weight(1f))
      Text(
        text = "${(item.percentage * 100).toInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = Gray600,
        maxLines = 1
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = Formatters.formatCurrency(item.amount),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Gray900,
        maxLines = 1
      )
    }
    Spacer(Modifier.height(8.dp))
    Box(
      Modifier
        .fillMaxWidth()
        .padding(start = 22.dp)
        .height(6.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(track)
    ) {
      Box(
        Modifier
          .fillMaxWidth(item.percentage.coerceIn(0.01f, 1f))
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(color)
      )
    }
  }
}

@Composable
private fun TrendCard(trends: List<MonthlyTrendItem>, average: Long, monthKey: String) {
  // Default selection = the selected month (last bar).
  var selectedIndex by rememberSaveable(monthKey) { mutableIntStateOf(trends.lastIndex) }
  val active = trends.getOrNull(selectedIndex) ?: trends.last()

  SectionCard(modifier = Modifier.testTag("monthly_spending_trend_card")) {
    SectionTitle("최근 6개월 지출 추이", "막대를 누르면 그 달의 지출을 자세히 볼 수 있어요")
    Spacer(Modifier.height(14.dp))

    Surface(
      shape = RoundedCornerShape(14.dp),
      color = Gray100,
      border = BorderStroke(1.dp, Gray200),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(Modifier.padding(14.dp)) {
        Text(
          text = active.fullDisplay + if (active.isCurrentMonth) " (진행 중)" else "",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = Gray900,
          maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Column(Modifier.weight(1f)) {
            Text(
              text = Formatters.formatCurrency(active.totalExpense),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Black,
              color = Gray900,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "지출 ${active.transactionCount}건",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
          ChangeBadge(active.changeVsPrevPct)
        }
        if (average > 0) {
          Spacer(Modifier.height(6.dp))
          val diff = active.totalExpense - average
          Text(
            text = when {
              diff > 0 -> "월평균보다 ${Formatters.formatCurrency(diff)} 더 썼어요"
              diff < 0 -> "월평균보다 ${Formatters.formatCurrency(-diff)} 덜 썼어요"
              else -> "월평균과 같아요"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (diff > 0) AccentRed else AccentGreen,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    MonthlyTrendChart(
      trends = trends,
      average = average,
      selectedIndex = selectedIndex,
      onSelect = { selectedIndex = it },
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(TossBlue))
      Spacer(Modifier.width(5.dp))
      Text("월 지출", fontSize = 11.sp, color = Gray600)
      if (average > 0) {
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(14.dp).height(2.dp).background(AccentOrange))
        Spacer(Modifier.width(5.dp))
        Text(
          text = "월평균 ${Formatters.formatCurrency(average)}",
          fontSize = 11.sp,
          color = Gray600,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

/** Change vs previous month; "-" when unknown. */
@Composable
private fun ChangeBadge(pct: Float?) {
  val (text, color, icon) = when {
    pct == null -> Triple("전월 대비 -", Gray600, null)
    pct > 0f -> Triple("전월 대비 +${formatPct(pct)}%", AccentRed, Icons.AutoMirrored.Filled.TrendingUp)
    pct < 0f -> Triple("전월 대비 -${formatPct(pct)}%", AccentGreen, Icons.AutoMirrored.Filled.TrendingDown)
    else -> Triple("전월과 같아요", Gray600, null)
  }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(color.copy(alpha = 0.12f))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(3.dp))
    }
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
  }
}

private fun formatPct(pct: Float): String = String.format(java.util.Locale.KOREA, "%.1f", abs(pct))

@Composable
private fun InsightPill(text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(TossBlueContainer)
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = com.moneyflow.ui.theme.TossBlueText)
  }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
  Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gray900)
  if (subtitle != null) {
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray600)
  }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(CardSurface)
      .padding(20.dp),
    content = content
  )
}

/** "#여행 3건 · 320,000원" rows; tapping one lists its transactions below. */
@Composable
private fun TagCard(tags: List<com.moneyflow.util.TagSpend>, periodLabel: String, selected: String?, onSelect: (String) -> Unit) {
  SectionCard(modifier = Modifier.testTag("tag_card")) {
    Text("$periodLabel 태그별", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gray900)
    Text("메모에 #여행 처럼 붙인 태그로 모아 봐요", style = MaterialTheme.typography.bodySmall, color = Gray600)
    Spacer(Modifier.height(8.dp))
    tags.take(10).forEach { t ->
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
          .background(if (selected == t.tag) Gray100 else androidx.compose.ui.graphics.Color.Transparent)
          .clickable { onSelect(t.tag) }
          .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("#${t.tag}", fontWeight = FontWeight.SemiBold, color = com.moneyflow.ui.theme.TossBlueText, modifier = Modifier.weight(1f))
        Text("${t.transactions.size}건 · ", style = MaterialTheme.typography.bodySmall, color = Gray600)
        Text(Formatters.formatCurrency(t.expense), fontWeight = FontWeight.Bold, color = Gray900)
      }
    }
  }
}
