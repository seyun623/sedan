package com.moneyflow.ui.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.domain.CategorySpend
import com.moneyflow.domain.MonthlyTrendItem
import com.moneyflow.domain.WeeklyProgressItem
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray300
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot

// ------------------------------------------------------------------------------------------------
// Category donut
// ------------------------------------------------------------------------------------------------

/**
 * Category share donut. Tapping a slice selects it (tapping it again clears the selection);
 * non-selected slices are dimmed while a category is selected.
 */
@Composable
internal fun CategoryDonutChart(
  categories: List<CategorySpend>,
  selectedCategory: String?,
  onSelectCategory: (String?) -> Unit,
  modifier: Modifier = Modifier,
  strokeWidth: Dp = 26.dp,
  centerContent: @Composable () -> Unit = {}
) {
  val total = categories.sumOf { it.amount }.toFloat()
  val emptyRingColor = Gray200
  val progress = remember { Animatable(0f) }
  LaunchedEffect(categories) {
    progress.snapTo(0f)
    progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
  }
  val description = remember(categories) {
    categories.joinToString(", ") { "${it.category} ${(it.percentage * 100).toInt()}%" }
  }

  Box(
    modifier = modifier.semantics { contentDescription = "카테고리별 지출 비중: $description" },
    contentAlignment = Alignment.Center
  ) {
    Canvas(
      modifier = Modifier
        .matchParentSize()
        .pointerInput(categories, selectedCategory) {
          detectTapGestures { tap ->
            if (total <= 0f) return@detectTapGestures
            val center = Offset(size.width / 2f, size.height / 2f)
            val dx = tap.x - center.x
            val dy = tap.y - center.y
            val outer = size.width.coerceAtMost(size.height) / 2f
            val inner = outer - strokeWidth.toPx() * 1.6f
            val distance = hypot(dx, dy)
            if (distance < inner || distance > outer + 8f) return@detectTapGestures
            // Angle measured clockwise from 12 o'clock, 0..360.
            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
            if (angle < 0f) angle += 360f
            var start = 0f
            for (cat in categories) {
              val sweep = cat.amount / total * 360f
              if (angle >= start && angle < start + sweep) {
                onSelectCategory(if (selectedCategory == cat.category) null else cat.category)
                break
              }
              start += sweep
            }
          }
        }
    ) {
      val strokePx = strokeWidth.toPx()
      val radius = (size.minDimension - strokePx) / 2f
      val center = Offset(size.width / 2f, size.height / 2f)
      val topLeft = Offset(center.x - radius, center.y - radius)
      val arcSize = Size(radius * 2, radius * 2)
      if (total <= 0f) {
        drawCircle(emptyRingColor, radius, center, style = Stroke(strokePx))
        return@Canvas
      }
      var startAngle = -90f
      categories.forEach { cat ->
        val sweep = cat.amount / total * 360f * progress.value
        val dimmed = selectedCategory != null && selectedCategory != cat.category
        if (sweep > 0.5f) {
          drawArc(
            color = Color(cat.colorHex).copy(alpha = if (dimmed) 0.25f else 1f),
            startAngle = startAngle,
            sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = if (selectedCategory == cat.category) strokePx * 1.2f else strokePx, cap = StrokeCap.Butt)
          )
        }
        startAngle += sweep
      }
    }
    centerContent()
  }
}

// ------------------------------------------------------------------------------------------------
// Weekday spend
// ------------------------------------------------------------------------------------------------

/** Monday..Sunday expense bars; the peak day is highlighted. */
@Composable
internal fun WeekdaySpendChart(
  data: List<Pair<String, Long>>,
  modifier: Modifier = Modifier
) {
  val maxAmount = (data.maxOfOrNull { it.second } ?: 0L)
  val barArea = 96.dp
  val peakColor = TossBlue
  val normalColor = Gray300

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Bottom
  ) {
    data.forEach { (day, amount) ->
      val isPeak = maxAmount > 0 && amount == maxAmount
      val ratio = if (maxAmount > 0) (amount.toFloat() / maxAmount).coerceIn(0f, 1f) else 0f
      Column(
        modifier = Modifier
          .weight(1f)
          .semantics { contentDescription = "${day}요일 ${Formatters.formatCurrency(amount)}" },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = if (isPeak) compactAmount(amount) else "",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = peakColor,
          maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(barArea), contentAlignment = Alignment.BottomCenter) {
          Box(
            Modifier
              .width(18.dp)
              .height(if (amount > 0) (barArea * ratio).coerceAtLeast(6.dp) else 4.dp)
              .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
              .background(if (isPeak) peakColor else normalColor)
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text = day,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
          color = if (isPeak) Gray900 else Gray600,
          maxLines = 1
        )
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 6-month trend
// ------------------------------------------------------------------------------------------------

/**
 * Bar chart of the last months' expense with an average reference line.
 * [selectedIndex] is highlighted; tapping a bar or a month label calls [onSelect].
 */
@Composable
internal fun MonthlyTrendChart(
  trends: List<MonthlyTrendItem>,
  average: Long,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  if (trends.isEmpty()) return
  val progress = remember { Animatable(0f) }
  LaunchedEffect(trends) {
    progress.snapTo(0f)
    progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
  }

  val niceMax = niceAxisMax(maxOf(trends.maxOf { it.totalExpense }, average))
  val ticks = listOf(1f, 0.5f, 0f).map { (niceMax * it).toLong() }

  // Theme colors must be read here: DrawScope is not composable.
  val gridColor = Gray200
  val baselineColor = Gray300
  val backdropColor = Gray100
  val selectedBarColor = TossBlue
  val currentBarColor = TossBlue.copy(alpha = 0.55f)
  val normalBarColor = Gray300
  val averageColor = AccentOrange

  Column(modifier) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
    ) {
      Column(
        modifier = Modifier
          .width(40.dp)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
      ) {
        ticks.forEach {
          Text(compactAmount(it), fontSize = 10.sp, color = Gray500, maxLines = 1)
        }
      }
      Spacer(Modifier.width(6.dp))
      Canvas(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .semantics {
            contentDescription = "최근 ${trends.size}개월 지출 추이: " +
              trends.joinToString(", ") { "${it.monthLabel} ${Formatters.formatCurrency(it.totalExpense)}" }
          }
          .pointerInput(trends) {
            detectTapGestures { tap ->
              val slot = size.width.toFloat() / trends.size
              onSelect((tap.x / slot).toInt().coerceIn(0, trends.lastIndex))
            }
          }
      ) {
        val w = size.width
        val h = size.height
        val slot = w / trends.size
        val barWidth = (slot * 0.5f).coerceIn(12.dp.toPx(), 40.dp.toPx())
        val corner = 6.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        for (i in 0..2) {
          val y = h / 2f * i
          drawLine(
            color = if (i == 2) baselineColor else gridColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (i == 2) null else dash
          )
        }

        trends.forEachIndexed { index, item ->
          val left = index * slot
          val isSelected = index == selectedIndex
          if (isSelected) {
            drawRoundRect(
              color = backdropColor,
              topLeft = Offset(left + 2.dp.toPx(), 0f),
              size = Size(slot - 4.dp.toPx(), h),
              cornerRadius = CornerRadius(corner, corner)
            )
          }
          val ratio = if (niceMax > 0) item.totalExpense.toFloat() / niceMax else 0f
          val barHeight = (h * ratio * progress.value)
            .coerceAtLeast(if (item.totalExpense > 0) 4.dp.toPx() else 1.dp.toPx())
          val color = when {
            isSelected -> selectedBarColor
            item.isCurrentMonth -> currentBarColor
            else -> normalBarColor
          }
          drawRoundRect(
            color = color,
            topLeft = Offset(left + (slot - barWidth) / 2f, h - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(corner, corner)
          )
        }

        if (average > 0 && niceMax > 0) {
          val y = h - h * (average.toFloat() / niceMax).coerceIn(0f, 1f)
          drawLine(
            color = averageColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
          )
        }
      }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().padding(start = 46.dp)) {
      trends.forEachIndexed { index, item ->
        val isSelected = index == selectedIndex
        Column(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "${item.fullDisplay} 선택") { onSelect(index) },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = item.monthLabel,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) TossBlue else Gray700,
            textAlign = TextAlign.Center,
            maxLines = 1
          )
          if (item.isCurrentMonth) {
            Box(
              Modifier
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

// ------------------------------------------------------------------------------------------------
// Weekly progress
// ------------------------------------------------------------------------------------------------

/** Horizontal bars of each week's expense within the month. */
@Composable
internal fun WeeklyProgressList(
  weeks: List<WeeklyProgressItem>,
  modifier: Modifier = Modifier
) {
  val max = (weeks.maxOfOrNull { it.totalExpense } ?: 0L)
  val trackColor = Gray100
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    weeks.forEach { week ->
      val isPeak = max > 0 && week.totalExpense == max
      val ratio = if (max > 0) (week.totalExpense.toFloat() / max).coerceIn(0f, 1f) else 0f
      Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = week.weekLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Gray900,
            maxLines = 1
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = week.dateRangeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Gray500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = Formatters.formatCurrency(week.totalExpense),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPeak) TossBlue else Gray900,
            maxLines = 1
          )
        }
        Spacer(Modifier.height(6.dp))
        Box(
          Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor)
        ) {
          if (ratio > 0f) {
            Box(
              Modifier
                .fillMaxWidth(ratio)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isPeak) TossBlue else TossBlue.copy(alpha = 0.45f))
            )
          }
        }
        if (week.transactionCount > 0) {
          Spacer(Modifier.height(2.dp))
          Text(
            text = "${week.transactionCount}건",
            style = MaterialTheme.typography.labelSmall,
            color = Gray600
          )
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------------------------------------

/** A rounded Y-axis maximum (at least 10,000원). */
internal fun niceAxisMax(maxAmount: Long): Long {
  if (maxAmount <= 10_000L) return 10_000L
  val step = when {
    maxAmount <= 100_000L -> 20_000L
    maxAmount <= 500_000L -> 100_000L
    maxAmount <= 2_000_000L -> 250_000L
    maxAmount <= 5_000_000L -> 500_000L
    maxAmount <= 20_000_000L -> 2_000_000L
    else -> 10_000_000L
  }
  return ceil(maxAmount.toDouble() / step).toLong().coerceAtLeast(1L) * step
}

/** "1.2억", "35만", "8천", "500" for axis labels. */
internal fun compactAmount(amount: Long): String = when {
  amount <= 0L -> "0"
  amount >= 100_000_000L -> {
    val eok = amount / 100_000_000.0
    if (eok % 1.0 == 0.0) "${eok.toLong()}억" else String.format(java.util.Locale.KOREA, "%.1f억", eok)
  }
  amount >= 10_000L -> "${amount / 10_000}만"
  amount >= 1_000L -> "${amount / 1_000}천"
  else -> amount.toString()
}
