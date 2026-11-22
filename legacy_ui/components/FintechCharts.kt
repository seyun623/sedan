package com.moneyflow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray300
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.viewmodel.CategorySpend
import com.moneyflow.util.Formatters
import kotlin.math.ceil

data class ChartSlice(
  val value: Float,
  val color: Color,
  val label: String
)

/**
 * Recharts-inspired interactive BarChart component (`<BarChart>`, `<CartesianGrid strokeDasharray="3 3" />`,
 * `<XAxis />`, `<YAxis />`, `<Tooltip />`, `<Bar radius={[8, 8, 0, 0]} />`) built with Jetpack Compose Canvas.
 * Visualizes total monthly spending per category with animated bars, dashed grid lines, axis labels,
 * and interactive bar selection tooltip.
 */
@Composable
fun RechartsCategoryBarChart(
  categories: List<CategorySpend>,
  selectedCategory: String?,
  onSelectCategory: (String?) -> Unit,
  isHorizontal: Boolean = false,
  modifier: Modifier = Modifier
) {
  val animProgress = remember { Animatable(0f) }

  LaunchedEffect(categories, isHorizontal) {
    animProgress.snapTo(0f)
    animProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
    )
  }

  if (categories.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .height(220.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Gray100.copy(alpha = 0.6f))
        .testTag("recharts_empty_state"),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "이번 달 지출 내역이 없습니다",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "지출 내역을 추가하면 카테고리별 막대 차트가 표시됩니다",
          style = MaterialTheme.typography.bodySmall,
          color = Gray500
        )
      }
    }
    return
  }

  val rawMax = categories.maxOfOrNull { it.amount } ?: 1L
  val niceMax = computeNiceAxisMax(rawMax)
  val yAxisTicks = listOf(1f, 0.75f, 0.5f, 0.25f, 0f).map { ratio ->
    (niceMax * ratio).toLong()
  }

  val selectedItem = categories.find { it.category == selectedCategory } ?: categories.firstOrNull()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("recharts_category_bar_chart")
  ) {
    // Recharts-style Active Tooltip Banner
    if (selectedItem != null) {
      RechartsTooltipCard(
        item = selectedItem,
        isPinned = selectedCategory != null,
        onClearSelection = { onSelectCategory(null) }
      )
      Spacer(modifier = Modifier.height(16.dp))
    }

    if (!isHorizontal) {
      // Vertical BarChart (<BarChart data={categoryBreakdown}>)
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
          verticalAlignment = Alignment.Bottom
        ) {
          // YAxis Column
          Column(
            modifier = Modifier
              .width(48.dp)
              .fillMaxHeight()
              .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
          ) {
            yAxisTicks.forEach { tickValue ->
              Text(
                text = formatCompactAxisValue(tickValue),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Gray500,
                maxLines = 1
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Plot Area with CartesianGrid & Bars
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          ) {
            Canvas(
              modifier = Modifier
                .fillMaxSize()
                .testTag("recharts_bar_canvas")
                .pointerInput(categories, selectedCategory) {
                  detectTapGestures { tapOffset ->
                    if (categories.isNotEmpty() && size.width > 0) {
                      val slotWidth = size.width.toFloat() / categories.size
                      val tappedIndex = (tapOffset.x / slotWidth).toInt().coerceIn(0, categories.lastIndex)
                      val tappedCategory = categories[tappedIndex].category
                      if (selectedCategory == tappedCategory) {
                        onSelectCategory(null)
                      } else {
                        onSelectCategory(tappedCategory)
                      }
                    }
                  }
                }
            ) {
              val chartWidth = size.width
              val chartHeight = size.height
              val gridDashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

              // 1. Draw <CartesianGrid strokeDasharray="3 3" />
              val gridSteps = 4
              for (i in 0..gridSteps) {
                val y = (chartHeight / gridSteps) * i
                val isBaseline = i == gridSteps
                drawLine(
                  color = if (isBaseline) Color(0xFFD1D6DB) else Color(0xFFE5E8EB),
                  start = Offset(0f, y),
                  end = Offset(chartWidth, y),
                  strokeWidth = if (isBaseline) 1.5.dp.toPx() else 1.dp.toPx(),
                  pathEffect = if (isBaseline) null else gridDashEffect
                )
              }

              // 2. Draw Active Column Cursor & <Bar radius={[8, 8, 0, 0]} />
              val count = categories.size
              val slotWidth = chartWidth / count
              val barWidth = (slotWidth * 0.56f).coerceIn(14.dp.toPx(), 42.dp.toPx())
              val cornerRadiusPx = 8.dp.toPx()

              categories.forEachIndexed { index, item ->
                val slotLeft = index * slotWidth
                val barCenter = slotLeft + slotWidth / 2f
                val barLeft = barCenter - barWidth / 2f

                val isSelected = selectedCategory == item.category
                val isDimmed = selectedCategory != null && !isSelected

                // Recharts active hover/tap cursor backdrop
                if (isSelected) {
                  drawRoundRect(
                    color = Color(0xFFF2F4F6),
                    topLeft = Offset(slotLeft + 2.dp.toPx(), 0f),
                    size = Size(slotWidth - 4.dp.toPx(), chartHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                  )
                }

                val ratio = (item.amount.toFloat() / niceMax.toFloat()).coerceIn(0f, 1f)
                val animatedBarHeight = (chartHeight * ratio * animProgress.value)
                  .coerceAtLeast(if (item.amount > 0) 6.dp.toPx() else 0f)
                val barTop = (chartHeight - animatedBarHeight).coerceAtLeast(0f)

                val baseColor = Color(item.colorHex)
                val topColor = if (isDimmed) baseColor.copy(alpha = 0.35f) else baseColor
                val bottomColor = if (isDimmed) {
                  baseColor.copy(alpha = 0.22f)
                } else {
                  baseColor.copy(alpha = 0.82f)
                }

                // Top-rounded bar path (radius = [8, 8, 0, 0])
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

                if (isSelected) {
                  drawPath(
                    path = barPath,
                    color = Gray900.copy(alpha = 0.25f),
                    style = Stroke(width = 1.5.dp.toPx())
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // XAxis Category Labels Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          categories.forEach { item ->
            val isSelected = selectedCategory == item.category
            val shortLabel = item.category.split("/").firstOrNull() ?: item.category
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                  onSelectCategory(if (isSelected) null else item.category)
                }
                .padding(vertical = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(Color(item.colorHex))
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) TossBlue else Gray600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }
    } else {
      // Horizontal BarChart (<BarChart layout="vertical" data={categoryBreakdown}>)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("recharts_horizontal_bars"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        categories.forEach { item ->
          val isSelected = selectedCategory == item.category
          val isDimmed = selectedCategory != null && !isSelected
          val ratio = (item.amount.toFloat() / niceMax.toFloat()).coerceIn(0.03f, 1f) * animProgress.value

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) Gray100 else Color.Transparent)
              .clickable {
                onSelectCategory(if (isSelected) null else item.category)
              }
              .padding(horizontal = 8.dp, vertical = 6.dp)
          ) {
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
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                  color = if (isDimmed) Gray500 else Gray900
                )
              }
              Text(
                text = "${Formatters.formatCurrency(item.amount)} (${(item.percentage * 100).toInt()}%)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) TossBlue else Gray600
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Gray100)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                  .fillMaxHeight()
                  .clip(RoundedCornerShape(7.dp))
                  .background(
                    if (isDimmed) Color(item.colorHex).copy(alpha = 0.35f)
                    else Color(item.colorHex)
                  )
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RechartsTooltipCard(
  item: CategorySpend,
  isPinned: Boolean,
  onClearSelection: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("recharts_tooltip_card"),
    shape = RoundedCornerShape(14.dp),
    color = Color(0xFFF8FAFC),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = if (isPinned) Color(item.colorHex).copy(alpha = 0.55f) else Gray200
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(item.colorHex))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = item.category,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              color = Gray900
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(item.colorHex).copy(alpha = 0.14f)
            ) {
              Text(
                text = "비중 ${(item.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(item.colorHex),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
          Text(
            text = if (isPinned) "선택된 카테고리 · 다시 탭하면 전체 보기" else "막대를 탭하면 카테고리별 상세 지출을 필터링합니다",
            style = MaterialTheme.typography.labelSmall,
            color = Gray500
          )
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = Formatters.formatCurrency(item.amount),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Black,
          color = Gray900
        )
        if (isPinned) {
          Text(
            text = "필터 해제",
            style = MaterialTheme.typography.labelSmall,
            color = TossBlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .clickable { onClearSelection() }
              .padding(horizontal = 4.dp, vertical = 2.dp)
          )
        }
      }
    }
  }
}

private fun computeNiceAxisMax(maxAmount: Long): Long {
  if (maxAmount <= 10_000L) return 10_000L
  val magnitude = when {
    maxAmount <= 50_000L -> 10_000L
    maxAmount <= 200_000L -> 50_000L
    maxAmount <= 1_000_000L -> 100_000L
    maxAmount <= 5_000_000L -> 500_000L
    else -> 1_000_000L
  }
  val steps = ceil(maxAmount.toDouble() / magnitude.toDouble()).toLong().coerceAtLeast(1L)
  return steps * magnitude
}

private fun formatCompactAxisValue(amount: Long): String {
  if (amount <= 0L) return "0원"
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
    else -> "${amount}원"
  }
}

@Composable
fun FintechDonutChart(
  slices: List<ChartSlice>,
  modifier: Modifier = Modifier,
  strokeWidth: Dp = 24.dp,
  centerContent: @Composable () -> Unit = {}
) {
  val total = slices.sumOf { it.value.toDouble() }.toFloat()
  val animationProgress = remember { Animatable(0f) }

  LaunchedEffect(slices) {
    animationProgress.snapTo(0f)
    animationProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
    )
  }

  Box(
    modifier = modifier.testTag("fintech_donut_chart"),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val canvasSize = size.minDimension
      val radius = (canvasSize - strokeWidth.toPx()) / 2f
      val center = Offset(size.width / 2f, size.height / 2f)
      val arcSize = Size(radius * 2, radius * 2)
      val arcTopLeft = Offset(center.x - radius, center.y - radius)

      if (total <= 0f) {
        drawCircle(
          color = Color(0xFFE5E8EB),
          radius = radius,
          center = center,
          style = Stroke(width = strokeWidth.toPx())
        )
      } else {
        var startAngle = -90f
        slices.forEach { slice ->
          val sweepAngle = (slice.value / total) * 360f * animationProgress.value
          if (sweepAngle > 0.5f) {
            drawArc(
              color = slice.color,
              startAngle = startAngle,
              sweepAngle = sweepAngle - 2f, // Subtle gap between slices
              useCenter = false,
              topLeft = arcTopLeft,
              size = arcSize,
              style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
          }
          startAngle += (slice.value / total) * 360f * animationProgress.value
        }
      }
    }

    centerContent()
  }
}

@Composable
fun WeeklySpendBarChart(
  data: List<Pair<String, Long>>,
  modifier: Modifier = Modifier
) {
  val maxAmount = (data.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(140.dp)
      .testTag("weekly_spend_chart"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Bottom
  ) {
    data.forEach { (day, amount) ->
      val heightRatio = (amount.toFloat() / maxAmount.toFloat()).coerceIn(0.08f, 1f)
      val isPeak = amount == maxAmount && amount > 0

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
      ) {
        if (isPeak) {
          Text(
            text = "최대",
            style = MaterialTheme.typography.labelSmall,
            color = TossBlue,
            fontWeight = FontWeight.Bold
          )
        } else {
          Spacer(modifier = Modifier.height(14.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
          modifier = Modifier
            .width(16.dp)
            .height((80 * heightRatio).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPeak) TossBlue else Gray300)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = day,
          style = MaterialTheme.typography.bodySmall,
          color = if (isPeak) Gray900 else Gray600,
          fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal
        )
      }
    }
  }
}

@Composable
fun StackedAssetDistributionBar(
  bankAmount: Long,
  savingsAmount: Long,
  investAmount: Long,
  modifier: Modifier = Modifier
) {
  val total = (bankAmount + savingsAmount + investAmount).coerceAtLeast(1L).toFloat()
  val bankRatio = (bankAmount / total).coerceIn(0f, 1f)
  val savingsRatio = (savingsAmount / total).coerceIn(0f, 1f)
  val investRatio = (investAmount / total).coerceIn(0f, 1f)

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(12.dp)
        .clip(RoundedCornerShape(6.dp))
    ) {
      if (bankRatio > 0) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .weight(bankRatio.coerceAtLeast(0.01f))
            .background(TossBlue)
        )
      }
      if (savingsRatio > 0) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .weight(savingsRatio.coerceAtLeast(0.01f))
            .background(Color(0xFF00B074))
        )
      }
      if (investRatio > 0) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .weight(investRatio.coerceAtLeast(0.01f))
            .background(Color(0xFFFF8A00))
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      LegendItem(color = TossBlue, label = "입출금", ratio = bankRatio)
      LegendItem(color = Color(0xFF00B074), label = "예적금", ratio = savingsRatio)
      LegendItem(color = Color(0xFFFF8A00), label = "투자/주식", ratio = investRatio)
    }
  }
}

@Composable
private fun LegendItem(color: Color, label: String, ratio: Float) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = "$label ${(ratio * 100).toInt()}%",
      style = MaterialTheme.typography.bodySmall,
      color = Gray600
    )
  }
}
