package com.moneyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray300
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.viewmodel.MonthlyTrendItem
import com.moneyflow.ui.viewmodel.WeeklyProgressItem
import com.moneyflow.util.Formatters
import kotlin.math.abs
import kotlin.math.ceil

enum class TrendViewMode {
  MONTHLY_SIX_MONTHS, // 최근 6개월 월간 추이
  WEEKLY_CURRENT_MONTH // 당월 주차별 추이
}

/**
 * High-level interactive Monthly & Weekly Spending Trend Card designed with modern Toss & Recharts styling.
 * Displays interactive Canvas bar chart, view toggle pills, 6-month average benchmark line,
 * and detailed active tooltip card on tap.
 */
@Composable
fun MonthlySpendingTrendCard(
  monthlyTrends: List<MonthlyTrendItem>,
  weeklyProgress: List<WeeklyProgressItem>,
  sixMonthAverage: Long,
  currentMonthDisplay: String,
  modifier: Modifier = Modifier
) {
  var viewMode by remember { mutableStateOf(TrendViewMode.MONTHLY_SIX_MONTHS) }
  var selectedMonthKey by remember { mutableStateOf<String?>(null) }
  var selectedWeekIndex by remember { mutableStateOf<Int?>(null) }

  // Auto-select the current month or latest available if not explicitly chosen
  val activeMonthItem = remember(monthlyTrends, selectedMonthKey) {
    if (selectedMonthKey != null) {
      monthlyTrends.find { it.monthKey == selectedMonthKey }
    } else {
      monthlyTrends.find { it.isCurrentMonth } ?: monthlyTrends.lastOrNull()
    }
  }

  val activeWeekItem = remember(weeklyProgress, selectedWeekIndex) {
    if (selectedWeekIndex != null && selectedWeekIndex!! in weeklyProgress.indices) {
      weeklyProgress[selectedWeekIndex!!]
    } else {
      weeklyProgress.lastOrNull()
    }
  }

  val highestMonth = remember(monthlyTrends) {
    monthlyTrends.maxByOrNull { it.totalExpense }
  }

  val lowestMonth = remember(monthlyTrends) {
    monthlyTrends.filter { it.totalExpense > 0 }.minByOrNull { it.totalExpense }
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("monthly_spending_trend_card"),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier.padding(20.dp)
    ) {
      // Header: Title & View Mode Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.BarChart,
              contentDescription = null,
              tint = TossBlue,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "월간 지출 트렌드",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (viewMode == TrendViewMode.MONTHLY_SIX_MONTHS) {
              "최근 6개월 소비 비교 & 평균 대비 추이"
            } else {
              "$currentMonthDisplay 주차별 누적 지출 진행률"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        // View Mode Toggle (Pills)
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Gray100,
          modifier = Modifier.testTag("trend_mode_toggle")
        ) {
          Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val isMonthly = viewMode == TrendViewMode.MONTHLY_SIX_MONTHS
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (isMonthly) Color.White else Color.Transparent)
                .clickable { viewMode = TrendViewMode.MONTHLY_SIX_MONTHS }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("toggle_monthly_trend_btn"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "6개월",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isMonthly) FontWeight.Bold else FontWeight.Medium,
                color = if (isMonthly) TossBlue else Gray600
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (!isMonthly) Color.White else Color.Transparent)
                .clickable { viewMode = TrendViewMode.WEEKLY_CURRENT_MONTH }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("toggle_weekly_trend_btn"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "당월 주차별",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (!isMonthly) FontWeight.Bold else FontWeight.Medium,
                color = if (!isMonthly) TossBlue else Gray600
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 6-Month Macro Benchmark Summary Stat Ribbon
      if (viewMode == TrendViewMode.MONTHLY_SIX_MONTHS && sixMonthAverage > 0) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gray100.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "6개월 월평균 지출",
              style = MaterialTheme.typography.labelSmall,
              color = Gray600
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = Formatters.formatCurrency(sixMonthAverage),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
          }

          if (highestMonth != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "최고 지출월",
                style = MaterialTheme.typography.labelSmall,
                color = Gray600
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "${highestMonth.monthLabel} (${Formatters.formatKoreanUnitCurrency(highestMonth.totalExpense)})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentRed
              )
            }
          }

          if (lowestMonth != null) {
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "최저 지출월",
                style = MaterialTheme.typography.labelSmall,
                color = Gray600
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "${lowestMonth.monthLabel} (${Formatters.formatKoreanUnitCurrency(lowestMonth.totalExpense)})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentGreen
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
      }

      // Recharts-style Interactive Tooltip Card for Active Selection
      if (viewMode == TrendViewMode.MONTHLY_SIX_MONTHS && activeMonthItem != null) {
        TrendActiveTooltipCard(
          title = "${activeMonthItem.fullDisplay} 지출 분석",
          amount = activeMonthItem.totalExpense,
          transactionCount = activeMonthItem.transactionCount,
          isCurrent = activeMonthItem.isCurrentMonth,
          pctVsPrevious = activeMonthItem.changeVsPrevPct,
          avgDifference = if (sixMonthAverage > 0) activeMonthItem.totalExpense - sixMonthAverage else null,
          isPinned = selectedMonthKey != null,
          onResetSelection = { selectedMonthKey = null }
        )
        Spacer(modifier = Modifier.height(16.dp))
      } else if (viewMode == TrendViewMode.WEEKLY_CURRENT_MONTH && activeWeekItem != null) {
        TrendActiveTooltipCard(
          title = "${activeWeekItem.weekLabel} (${activeWeekItem.dateRangeLabel})",
          amount = activeWeekItem.totalExpense,
          transactionCount = activeWeekItem.transactionCount,
          isCurrent = false,
          pctVsPrevious = null,
          avgDifference = null,
          isPinned = selectedWeekIndex != null,
          onResetSelection = { selectedWeekIndex = null }
        )
        Spacer(modifier = Modifier.height(16.dp))
      }

      // The Interactive Canvas Bar Chart
      if (viewMode == TrendViewMode.MONTHLY_SIX_MONTHS) {
        MonthlySpendingTrendBarChart(
          trends = monthlyTrends,
          sixMonthAverage = sixMonthAverage,
          selectedMonthKey = selectedMonthKey,
          onSelectMonth = { key ->
            selectedMonthKey = if (selectedMonthKey == key) null else key
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
        )
      } else {
        WeeklySpendingTrendBarChart(
          weeklyItems = weeklyProgress,
          selectedWeekIndex = selectedWeekIndex,
          onSelectWeek = { idx ->
            selectedWeekIndex = if (selectedWeekIndex == idx) null else idx
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Chart Legend & Benchmark Guidance Note
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp, 10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TossBlue)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "월간 지출",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              color = Gray600
            )
          }

          if (viewMode == TrendViewMode.MONTHLY_SIX_MONTHS && sixMonthAverage > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .width(14.dp)
                  .height(2.dp)
                  .background(AccentOrange)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "6개월 평균선",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = Gray600
              )
            }
          }
        }

        Text(
          text = "막대를 터치하면 상세 지표를 확인합니다",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 11.sp,
          color = Gray500
        )
      }
    }
  }
}

/**
 * Native Jetpack Compose Canvas Interactive Bar Chart for Monthly Spending Trends.
 * Recharts aesthetics: rounded bars, Cartesian grid, benchmark average reference line,
 * touch gestures, and selection highlight.
 */
@Composable
fun MonthlySpendingTrendBarChart(
  trends: List<MonthlyTrendItem>,
  sixMonthAverage: Long,
  selectedMonthKey: String?,
  onSelectMonth: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val animProgress = remember { Animatable(0f) }

  LaunchedEffect(trends) {
    animProgress.snapTo(0f)
    animProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
    )
  }

  if (trends.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Gray100.copy(alpha = 0.6f)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "표시할 지출 트렌드 데이터가 없습니다",
        style = MaterialTheme.typography.bodyMedium,
        color = Gray600
      )
    }
    return
  }

  val maxTrendAmount = (trends.maxOfOrNull { it.totalExpense } ?: 1L).coerceAtLeast(sixMonthAverage)
  val niceMax = computeNiceChartMax(maxTrendAmount)
  val yAxisTicks = listOf(1f, 0.75f, 0.5f, 0.25f, 0f).map { ratio -> (niceMax * ratio).toLong() }

  Column(modifier = modifier.testTag("monthly_trend_bar_chart")) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      verticalAlignment = Alignment.Bottom
    ) {
      // Y-Axis Ticks
      Column(
        modifier = Modifier
          .width(44.dp)
          .fillMaxHeight()
          .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
      ) {
        yAxisTicks.forEach { tick ->
          Text(
            text = formatCompactChartAmount(tick),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Gray500,
            maxLines = 1
          )
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Canvas Plot Area
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxSize()
            .testTag("trend_canvas")
            .pointerInput(trends, selectedMonthKey) {
              detectTapGestures { tapOffset ->
                if (trends.isNotEmpty() && size.width > 0) {
                  val slotWidth = size.width.toFloat() / trends.size
                  val tappedIndex = (tapOffset.x / slotWidth).toInt().coerceIn(0, trends.lastIndex)
                  onSelectMonth(trends[tappedIndex].monthKey)
                }
              }
            }
        ) {
          val chartWidth = size.width
          val chartHeight = size.height
          val count = trends.size
          val slotWidth = chartWidth / count
          val barWidth = (slotWidth * 0.52f).coerceIn(16.dp.toPx(), 44.dp.toPx())
          val cornerRadiusPx = 7.dp.toPx()

          // 1. Cartesian Dashed Grid Lines
          val gridSteps = 4
          val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
          for (i in 0..gridSteps) {
            val y = (chartHeight / gridSteps) * i
            val isBaseline = i == gridSteps
            drawLine(
              color = if (isBaseline) Color(0xFFD1D6DB) else Color(0xFFECEFF3),
              start = Offset(0f, y),
              end = Offset(chartWidth, y),
              strokeWidth = if (isBaseline) 1.5.dp.toPx() else 1.dp.toPx(),
              pathEffect = if (isBaseline) null else gridDash
            )
          }

          // 2. Average Benchmark Line (6-Month Average)
          if (sixMonthAverage > 0 && niceMax > 0) {
            val avgRatio = (sixMonthAverage.toFloat() / niceMax.toFloat()).coerceIn(0f, 1f)
            val avgY = chartHeight - (chartHeight * avgRatio)
            val avgDash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            drawLine(
              color = AccentOrange.copy(alpha = 0.85f),
              start = Offset(0f, avgY),
              end = Offset(chartWidth, avgY),
              strokeWidth = 1.5.dp.toPx(),
              pathEffect = avgDash
            )
          }

          // 3. Render Monthly Bars
          trends.forEachIndexed { index, item ->
            val slotLeft = index * slotWidth
            val barCenter = slotLeft + slotWidth / 2f
            val barLeft = barCenter - barWidth / 2f

            val isSelected = selectedMonthKey == item.monthKey ||
              (selectedMonthKey == null && item.isCurrentMonth)
            val isDimmed = selectedMonthKey != null && !isSelected

            // Active Slot Hover/Tap Backdrop
            if (isSelected) {
              drawRoundRect(
                color = Color(0xFFF1F5F9),
                topLeft = Offset(slotLeft + 2.dp.toPx(), 0f),
                size = Size(slotWidth - 4.dp.toPx(), chartHeight),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
              )
            }

            val ratio = if (niceMax > 0) (item.totalExpense.toFloat() / niceMax.toFloat()).coerceIn(0f, 1f) else 0f
            val animatedBarHeight = (chartHeight * ratio * animProgress.value)
              .coerceAtLeast(if (item.totalExpense > 0) 6.dp.toPx() else 2.dp.toPx())
            val barTop = (chartHeight - animatedBarHeight).coerceAtLeast(0f)

            // Bar Gradient Colors
            val topColor = when {
              isSelected -> TossBlue
              item.isCurrentMonth -> TossBlue.copy(alpha = 0.8f)
              isDimmed -> Color(0xFFCBD5E1)
              else -> Color(0xFF94A3B8)
            }
            val bottomColor = when {
              isSelected -> Color(0xFF1B64DA)
              item.isCurrentMonth -> TossBlue.copy(alpha = 0.55f)
              isDimmed -> Color(0xFFE2E8F0)
              else -> Color(0xFF64748B)
            }

            // Draw Top-Rounded Bar
            val barPath = Path().apply {
              addRoundRect(
                RoundRect(
                  rect = Rect(
                    left = barLeft,
                    top = barTop,
                    right = barLeft + barWidth,
                    bottom = chartHeight
                  ),
                  topLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                  topRight = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                  bottomLeft = CornerRadius.Zero,
                  bottomRight = CornerRadius.Zero
                )
              )
            }

            drawPath(
              path = barPath,
              brush = Brush.verticalGradient(
                colors = listOf(topColor, bottomColor),
                startY = barTop,
                endY = chartHeight
              )
            )

            // Active bar indicator dot / highlight ring
            if (isSelected && animatedBarHeight > 8.dp.toPx()) {
              drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(barCenter, barTop + 6.dp.toPx())
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // X-Axis Month Labels Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 50.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      trends.forEach { item ->
        val isSelected = selectedMonthKey == item.monthKey ||
          (selectedMonthKey == null && item.isCurrentMonth)

        Column(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onSelectMonth(item.monthKey) }
            .padding(vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = item.monthLabel,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) TossBlue else Gray700,
            textAlign = TextAlign.Center
          )
          if (item.isCurrentMonth) {
            Box(
              modifier = Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(TossBlue)
            )
          }
        }
      }
    }
  }
}

/**
 * Native Jetpack Compose Canvas Interactive Bar Chart for In-Month Weekly Spending Progress.
 */
@Composable
fun WeeklySpendingTrendBarChart(
  weeklyItems: List<WeeklyProgressItem>,
  selectedWeekIndex: Int?,
  onSelectWeek: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val animProgress = remember { Animatable(0f) }

  LaunchedEffect(weeklyItems) {
    animProgress.snapTo(0f)
    animProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
    )
  }

  if (weeklyItems.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Gray100.copy(alpha = 0.6f)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "이번 달 주차별 지출 데이터가 없습니다",
        style = MaterialTheme.typography.bodyMedium,
        color = Gray600
      )
    }
    return
  }

  val maxAmount = (weeklyItems.maxOfOrNull { it.totalExpense } ?: 1L).coerceAtLeast(10_000L)
  val niceMax = computeNiceChartMax(maxAmount)
  val yAxisTicks = listOf(1f, 0.75f, 0.5f, 0.25f, 0f).map { ratio -> (niceMax * ratio).toLong() }

  Column(modifier = modifier.testTag("weekly_trend_bar_chart")) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      verticalAlignment = Alignment.Bottom
    ) {
      // Y-Axis Ticks
      Column(
        modifier = Modifier
          .width(44.dp)
          .fillMaxHeight()
          .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
      ) {
        yAxisTicks.forEach { tick ->
          Text(
            text = formatCompactChartAmount(tick),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Gray500,
            maxLines = 1
          )
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Canvas Plot Area
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxSize()
            .testTag("weekly_canvas")
            .pointerInput(weeklyItems, selectedWeekIndex) {
              detectTapGestures { tapOffset ->
                if (weeklyItems.isNotEmpty() && size.width > 0) {
                  val slotWidth = size.width.toFloat() / weeklyItems.size
                  val tappedIndex = (tapOffset.x / slotWidth).toInt().coerceIn(0, weeklyItems.lastIndex)
                  onSelectWeek(tappedIndex)
                }
              }
            }
        ) {
          val chartWidth = size.width
          val chartHeight = size.height
          val count = weeklyItems.size
          val slotWidth = chartWidth / count
          val barWidth = (slotWidth * 0.54f).coerceIn(18.dp.toPx(), 48.dp.toPx())
          val cornerRadiusPx = 7.dp.toPx()

          // Cartesian Dashed Grid Lines
          val gridSteps = 4
          val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
          for (i in 0..gridSteps) {
            val y = (chartHeight / gridSteps) * i
            val isBaseline = i == gridSteps
            drawLine(
              color = if (isBaseline) Color(0xFFD1D6DB) else Color(0xFFECEFF3),
              start = Offset(0f, y),
              end = Offset(chartWidth, y),
              strokeWidth = if (isBaseline) 1.5.dp.toPx() else 1.dp.toPx(),
              pathEffect = if (isBaseline) null else gridDash
            )
          }

          // Render Weekly Bars
          weeklyItems.forEachIndexed { index, item ->
            val slotLeft = index * slotWidth
            val barCenter = slotLeft + slotWidth / 2f
            val barLeft = barCenter - barWidth / 2f

            val isSelected = selectedWeekIndex == index ||
              (selectedWeekIndex == null && index == weeklyItems.lastIndex)
            val isDimmed = selectedWeekIndex != null && !isSelected

            if (isSelected) {
              drawRoundRect(
                color = Color(0xFFF1F5F9),
                topLeft = Offset(slotLeft + 2.dp.toPx(), 0f),
                size = Size(slotWidth - 4.dp.toPx(), chartHeight),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
              )
            }

            val ratio = if (niceMax > 0) (item.totalExpense.toFloat() / niceMax.toFloat()).coerceIn(0f, 1f) else 0f
            val animatedBarHeight = (chartHeight * ratio * animProgress.value)
              .coerceAtLeast(if (item.totalExpense > 0) 6.dp.toPx() else 2.dp.toPx())
            val barTop = (chartHeight - animatedBarHeight).coerceAtLeast(0f)

            val topColor = if (isSelected) TossBlue else if (isDimmed) Color(0xFFCBD5E1) else Color(0xFF38BDF8)
            val bottomColor = if (isSelected) Color(0xFF1B64DA) else if (isDimmed) Color(0xFFE2E8F0) else Color(0xFF0284C7)

            val barPath = Path().apply {
              addRoundRect(
                RoundRect(
                  rect = Rect(
                    left = barLeft,
                    top = barTop,
                    right = barLeft + barWidth,
                    bottom = chartHeight
                  ),
                  topLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                  topRight = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                  bottomLeft = CornerRadius.Zero,
                  bottomRight = CornerRadius.Zero
                )
              )
            }

            drawPath(
              path = barPath,
              brush = Brush.verticalGradient(
                colors = listOf(topColor, bottomColor),
                startY = barTop,
                endY = chartHeight
              )
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // X-Axis Week Labels Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 50.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      weeklyItems.forEachIndexed { index, item ->
        val isSelected = selectedWeekIndex == index ||
          (selectedWeekIndex == null && index == weeklyItems.lastIndex)

        Column(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onSelectWeek(index) }
            .padding(vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = item.weekLabel,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) TossBlue else Gray700,
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}

/**
 * Recharts-style Tooltip Card showing active breakdown of the tapped bar.
 */
@Composable
private fun TrendActiveTooltipCard(
  title: String,
  amount: Long,
  transactionCount: Int,
  isCurrent: Boolean,
  pctVsPrevious: Float?,
  avgDifference: Long?,
  isPinned: Boolean,
  onResetSelection: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Gray100.copy(alpha = 0.9f),
    border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("trend_tooltip_card")
  ) {
    Column(
      modifier = Modifier.padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(TossBlue)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Gray900
          )
          if (isCurrent) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(TossBlue.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = "현재 진행중",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TossBlue
              )
            }
          }
        }

        if (isPinned) {
          IconButton(
            onClick = onResetSelection,
            modifier = Modifier.size(24.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "선택 해제",
              tint = Gray500,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column {
          Text(
            text = Formatters.formatCurrency(amount),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Gray900
          )
          if (transactionCount > 0) {
            Text(
              text = "총 ${transactionCount}건 지출 기록",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
        }

        // Percentage & Average Comparative Badges
        Column(horizontalAlignment = Alignment.End) {
          if (pctVsPrevious != null) {
            val isIncrease = pctVsPrevious > 0
            val absPct = String.format("%.1f", abs(pctVsPrevious))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isIncrease) AccentRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
              Icon(
                imageVector = if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = if (isIncrease) AccentRed else AccentGreen,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = if (isIncrease) "전월비 +${absPct}% 증가" else "전월비 -${absPct}% 절약",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isIncrease) AccentRed else AccentGreen
              )
            }
          }

          if (avgDifference != null) {
            Spacer(modifier = Modifier.height(3.dp))
            val isAboveAvg = avgDifference > 0
            val diffFormatted = Formatters.formatKoreanUnitCurrency(abs(avgDifference))
            Text(
              text = if (isAboveAvg) "평균보다 $diffFormatted 초과" else "평균보다 $diffFormatted 절약",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              color = if (isAboveAvg) Gray700 else AccentGreen,
              fontWeight = if (!isAboveAvg) FontWeight.Bold else FontWeight.Normal
            )
          }
        }
      }
    }
  }
}

/**
 * Computes a rounded, clean maximum value for the Y axis.
 */
private fun computeNiceChartMax(maxAmount: Long): Long {
  if (maxAmount <= 10_000L) return 10_000L
  val magnitude = when {
    maxAmount <= 100_000L -> 20_000L
    maxAmount <= 500_000L -> 100_000L
    maxAmount <= 2_000_000L -> 250_000L
    maxAmount <= 5_000_000L -> 500_000L
    else -> 1_000_000L
  }
  val steps = ceil(maxAmount.toDouble() / magnitude.toDouble()).toLong().coerceAtLeast(1L)
  return steps * magnitude
}

/**
 * Formats Korean currency values for compact Y-axis charts.
 */
private fun formatCompactChartAmount(amount: Long): String {
  if (amount <= 0L) return "0"
  return when {
    amount >= 100_000_000L -> {
      val eok = amount / 100_000_000.0
      if (eok % 1.0 == 0.0) "${eok.toInt()}억" else String.format("%.1f억", eok)
    }
    amount >= 10_000L -> {
      val man = amount / 10_000
      "${man}만"
    }
    amount >= 1_000L -> {
      val cheon = amount / 1_000
      "${cheon}천"
    }
    else -> "$amount"
  }
}
