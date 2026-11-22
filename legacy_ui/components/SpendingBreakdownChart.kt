package com.moneyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
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
import com.moneyflow.ui.viewmodel.CategorySpend
import com.moneyflow.util.Formatters
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Chart display modes emulating the Recharts library ecosystem:
 * - PIE: `<PieChart><Pie dataKey="value" innerRadius={60} outerRadius={85} /></PieChart>`
 * - BAR: `<BarChart><CartesianGrid /><XAxis /><YAxis /><Bar /></BarChart>`
 * - PROPORTION: `<Bar stackOffset="expand" />` (100% horizontal proportion bar)
 */
enum class RechartsChartType {
  PIE,
  BAR,
  PROPORTION
}

/**
 * Helper to map category names to English / Korean bilingual labels for common expense types
 * (e.g. Food, Transport, Housing, Shopping, Cafe).
 */
object SpendingCategoryMapper {
  fun getBilingualLabel(category: String): String {
    return when (category) {
      "식비" -> "식비 (Food)"
      "교통" -> "교통 (Transport)"
      "주거/통신" -> "주거·통신 (Housing)"
      "쇼핑" -> "쇼핑 (Shopping)"
      "카페/간식" -> "카페·간식 (Cafe)"
      "생활/마트" -> "생활·마트 (Mart)"
      "의료/건강" -> "의료·건강 (Medical)"
      "문화/여가" -> "문화·여가 (Leisure)"
      "금융/보험" -> "금융·보험 (Finance)"
      "기타지출" -> "기타지출 (Others)"
      else -> category
    }
  }

  fun getShortType(category: String): String {
    return when {
      category.contains("식") || category.contains("밥") -> "Food"
      category.contains("교통") || category.contains("지하철") -> "Transport"
      category.contains("주거") || category.contains("통신") -> "Housing"
      category.contains("쇼핑") -> "Shopping"
      category.contains("카페") || category.contains("간식") -> "Cafe"
      category.contains("마트") || category.contains("생활") -> "Mart"
      category.contains("의료") || category.contains("건강") -> "Medical"
      category.contains("문화") || category.contains("여가") -> "Leisure"
      category.contains("금융") -> "Finance"
      else -> "Misc"
    }
  }
}

/**
 * A comprehensive Recharts-inspired Spending Breakdown chart component.
 * Visualizes monthly expenses categorized by type (Food, Transport, Housing, etc.)
 * with interactive Pie/Donut, Cartesian Bar, and Proportion views, custom floating Tooltip,
 * and interactive Legend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingBreakdownCard(
  categories: List<CategorySpend>,
  totalMonthlyExpense: Long,
  monthDisplay: String,
  modifier: Modifier = Modifier,
  initialChartType: RechartsChartType = RechartsChartType.PIE,
  onCategorySelected: ((String?) -> Unit)? = null
) {
  var selectedChartType by remember { mutableStateOf(initialChartType) }
  var activeCategory by remember(categories) { mutableStateOf<String?>(null) }

  val topCategory = remember(categories) {
    categories.maxByOrNull { it.amount }
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("spending_breakdown_card"),
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
  ) {
    Column(modifier = Modifier.padding(22.dp)) {
      // 1. Header: Title, Subtitle, and Recharts Chart Type Switcher
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(TossBlue.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.DonutLarge,
                contentDescription = null,
                tint = TossBlue,
                modifier = Modifier.size(16.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Spending Breakdown",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Black,
              color = Gray900
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "$monthDisplay 카테고리별 지출 분석 (Food · Transport · Housing)",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        // Recharts Type Toggle: Pie / Bar / Proportion
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Gray100,
          modifier = Modifier.padding(start = 4.dp)
        ) {
          Row(modifier = Modifier.padding(2.dp)) {
            // Donut / Pie Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedChartType == RechartsChartType.PIE) Color.White else Color.Transparent)
                .clickable { selectedChartType = RechartsChartType.PIE }
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("recharts_toggle_pie"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.PieChart,
                contentDescription = "PieChart",
                tint = if (selectedChartType == RechartsChartType.PIE) TossBlue else Gray500,
                modifier = Modifier.size(16.dp)
              )
            }

            // Bar Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedChartType == RechartsChartType.BAR) Color.White else Color.Transparent)
                .clickable { selectedChartType = RechartsChartType.BAR }
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("recharts_toggle_bar"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = "BarChart",
                tint = if (selectedChartType == RechartsChartType.BAR) TossBlue else Gray500,
                modifier = Modifier.size(16.dp)
              )
            }

            // Proportion Stacked Bar Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedChartType == RechartsChartType.PROPORTION) Color.White else Color.Transparent)
                .clickable { selectedChartType = RechartsChartType.PROPORTION }
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("recharts_toggle_stacked"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.TableChart,
                contentDescription = "StackedBar",
                tint = if (selectedChartType == RechartsChartType.PROPORTION) TossBlue else Gray500,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 2. Spending Total Headline
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column {
          Text(
            text = "이번 달 총 지출액",
            style = MaterialTheme.typography.labelMedium,
            color = Gray600
          )
          Text(
            text = Formatters.formatCurrency(totalMonthlyExpense),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Gray900
          )
        }

        if (topCategory != null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(Color(topCategory.colorHex).copy(alpha = 0.12f))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "최대 지출: ${topCategory.category} ${(topCategory.percentage * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color(topCategory.colorHex)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 3. Interactive Chart Canvas View
      if (categories.isEmpty() || totalMonthlyExpense <= 0L) {
        // Empty State
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Gray100),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DonutLarge, contentDescription = null, tint = Gray400, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "이번 달 기록된 지출 내역이 없습니다",
              style = MaterialTheme.typography.bodyMedium,
              color = Gray600,
              fontWeight = FontWeight.Medium
            )
          }
        }
      } else {
        // Render according to selected Recharts mode
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .testTag("spending_breakdown_chart"),
          contentAlignment = Alignment.Center
        ) {
          when (selectedChartType) {
            RechartsChartType.PIE -> {
              RechartsPieCanvas(
                categories = categories,
                totalExpense = totalMonthlyExpense,
                selectedCategory = activeCategory,
                onSelectCategory = { cat ->
                  activeCategory = if (activeCategory == cat) null else cat
                  onCategorySelected?.invoke(activeCategory)
                }
              )
            }
            RechartsChartType.BAR -> {
              RechartsBarCanvas(
                categories = categories,
                selectedCategory = activeCategory,
                onSelectCategory = { cat ->
                  activeCategory = if (activeCategory == cat) null else cat
                  onCategorySelected?.invoke(activeCategory)
                }
              )
            }
            RechartsChartType.PROPORTION -> {
              RechartsProportionCanvas(
                categories = categories,
                selectedCategory = activeCategory,
                onSelectCategory = { cat ->
                  activeCategory = if (activeCategory == cat) null else cat
                  onCategorySelected?.invoke(activeCategory)
                }
              )
            }
          }
        }
      }

      // 4. Recharts Interactive Tooltip Overlay
      AnimatedVisibility(
        visible = activeCategory != null,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        val selectedItem = categories.find { it.category == activeCategory }
        if (selectedItem != null) {
          val icon = CategoryIconHelper.getIconForCategory(selectedItem.category)
          val catColor = Color(selectedItem.colorHex)

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Gray900,
            tonalElevation = 6.dp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
              .testTag("recharts_custom_tooltip")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(catColor),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = selectedItem.category,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = SpendingCategoryMapper.getBilingualLabel(selectedItem.category),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                  Text(
                    text = "전체 지출 중 ${(selectedItem.percentage * 100).toInt()}% 차지",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Gray400
                  )
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "-${Formatters.formatCurrency(selectedItem.amount)}",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Black,
                  color = Color.White
                )
                Text(
                  text = "터치하여 선택 해제",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  color = Gray400
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 5. Recharts Interactive Legend Section
      Text(
        text = "카테고리 범례 (Legend)",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gray800
      )
      Spacer(modifier = Modifier.height(8.dp))

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        categories.forEach { item ->
          val isSelected = activeCategory == item.category
          val catColor = Color(item.colorHex)
          val icon = CategoryIconHelper.getIconForCategory(item.category)

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) catColor.copy(alpha = 0.15f) else Gray100,
            border = androidx.compose.foundation.BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) catColor else Gray200
            ),
            modifier = Modifier
              .clickable {
                activeCategory = if (activeCategory == item.category) null else item.category
                onCategorySelected?.invoke(activeCategory)
              }
              .testTag("spending_breakdown_legend_item_${item.category}")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(catColor)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${item.category} (${(item.percentage * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) catColor else Gray900
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = Formatters.formatCurrency(item.amount),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = if (isSelected) catColor else Gray600
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Recharts PieChart component:
 * `<PieChart><Pie dataKey="value" innerRadius={65} outerRadius={90} paddingAngle={3} /></PieChart>`
 * Canvas rendering with touch detection and active slice explosion.
 */
@Composable
private fun RechartsPieCanvas(
  categories: List<CategorySpend>,
  totalExpense: Long,
  selectedCategory: String?,
  onSelectCategory: (String) -> Unit
) {
  val animProgress = remember { Animatable(0f) }

  LaunchedEffect(categories) {
    animProgress.snapTo(0f)
    animProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
    )
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Canvas(
      modifier = Modifier
        .size(240.dp)
        .pointerInput(categories, totalExpense) {
          detectTapGestures { tapOffset ->
            val center = Offset(size.width / 2f, size.height / 2f)
            val dx = tapOffset.x - center.x
            val dy = tapOffset.y - center.y
            val distance = sqrt(dx * dx + dy * dy)
            val outerRadius = size.width / 2f
            val innerRadius = outerRadius * 0.58f

            if (distance in innerRadius..outerRadius) {
              var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
              if (angle < 0) angle += 360f

              var currentAngle = -90f
              for (cat in categories) {
                val sweep = (cat.amount.toFloat() / totalExpense.toFloat()) * 360f
                val start = (currentAngle % 360f + 360f) % 360f
                val end = ((currentAngle + sweep) % 360f + 360f) % 360f

                val isHit = if (start <= end) {
                  angle in start..end
                } else {
                  angle >= start || angle <= end
                }

                if (isHit) {
                  onSelectCategory(cat.category)
                  break
                }
                currentAngle += sweep
              }
            }
          }
        }
    ) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val outerRadius = size.width / 2f
      val strokeWidth = outerRadius * 0.42f
      val radius = outerRadius - strokeWidth / 2f

      var startAngle = -90f
      val progressVal = animProgress.value

      categories.forEach { cat ->
        val sweepAngle = (cat.amount.toFloat() / totalExpense.toFloat()) * 360f * progressVal
        val isSelected = selectedCategory == cat.category

        val effectiveStroke = if (isSelected) strokeWidth * 1.12f else strokeWidth
        val effectiveColor = Color(cat.colorHex)

        // Draw active arc with slight gap (Recharts paddingAngle={3})
        val padding = 2.5f
        if (sweepAngle > padding) {
          drawArc(
            color = effectiveColor,
            startAngle = startAngle + padding / 2f,
            sweepAngle = sweepAngle - padding,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = effectiveStroke, cap = StrokeCap.Round)
          )
        }

        startAngle += sweepAngle
      }
    }

    // Center Display Label (Recharts inner text)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      if (selectedCategory != null) {
        val sel = categories.find { it.category == selectedCategory }
        Text(
          text = selectedCategory,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color(sel?.colorHex ?: 0xFF191F28)
        )
        Text(
          text = if (sel != null) Formatters.formatCurrency(sel.amount) else "",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.ExtraBold,
          color = Gray900
        )
      } else {
        Text(
          text = "지출 합계",
          style = MaterialTheme.typography.labelSmall,
          color = Gray500
        )
        Text(
          text = Formatters.formatCurrency(totalExpense),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Black,
          color = Gray900
        )
      }
    }
  }
}

/**
 * Recharts BarChart component:
 * `<BarChart data={categories}><CartesianGrid strokeDasharray="3 3"/><XAxis /><YAxis /><Bar /></BarChart>`
 */
@Composable
private fun RechartsBarCanvas(
  categories: List<CategorySpend>,
  selectedCategory: String?,
  onSelectCategory: (String) -> Unit
) {
  val animProgress = remember { Animatable(0f) }

  LaunchedEffect(categories) {
    animProgress.snapTo(0f)
    animProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
    )
  }

  val maxAmount = remember(categories) {
    categories.maxOfOrNull { it.amount }?.coerceAtLeast(100_000L) ?: 100_000L
  }

  Canvas(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp, vertical = 6.dp)
      .pointerInput(categories, maxAmount) {
        detectTapGestures { tapOffset ->
          val barCount = categories.size
          val availableWidth = size.width - 50.dp.toPx()
          val slotWidth = availableWidth / barCount
          val startX = 50.dp.toPx()

          if (tapOffset.x >= startX) {
            val index = ((tapOffset.x - startX) / slotWidth).toInt().coerceIn(0, barCount - 1)
            onSelectCategory(categories[index].category)
          }
        }
      }
  ) {
    val leftAxisWidth = 45.dp.toPx()
    val bottomAxisHeight = 25.dp.toPx()
    val chartWidth = size.width - leftAxisWidth
    val chartHeight = size.height - bottomAxisHeight
    val progressVal = animProgress.value

    // 1. Cartesian Grid lines (strokeDasharray="3 3")
    val gridLinesCount = 4
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    for (i in 0..gridLinesCount) {
      val y = (chartHeight / gridLinesCount) * i
      drawLine(
        color = Gray200,
        start = Offset(leftAxisWidth, y),
        end = Offset(size.width, y),
        pathEffect = dashEffect,
        strokeWidth = 1.dp.toPx()
      )
    }

    // 2. Bars
    val barCount = categories.size
    val slotWidth = chartWidth / barCount
    val barWidth = (slotWidth * 0.55f).coerceIn(12.dp.toPx(), 28.dp.toPx())

    categories.forEachIndexed { index, cat ->
      val xCenter = leftAxisWidth + (index * slotWidth) + (slotWidth / 2f)
      val barFraction = (cat.amount.toFloat() / maxAmount.toFloat()).coerceIn(0.02f, 1f)
      val barHeight = chartHeight * barFraction * progressVal
      val barTop = chartHeight - barHeight
      val isSelected = selectedCategory == cat.category

      val color = Color(cat.colorHex)
      val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

      // Draw Bar (radius={[6, 6, 0, 0]})
      drawRoundRect(
        color = if (isSelected) color else color.copy(alpha = 0.85f),
        topLeft = Offset(xCenter - barWidth / 2f, barTop),
        size = Size(barWidth, barHeight),
        cornerRadius = cornerRadius
      )

      // Active border outline if selected
      if (isSelected) {
        drawRoundRect(
          color = Gray900,
          topLeft = Offset(xCenter - barWidth / 2f - 2f, barTop - 2f),
          size = Size(barWidth + 4f, barHeight + 2f),
          cornerRadius = cornerRadius,
          style = Stroke(width = 2.dp.toPx())
        )
      }
    }

    // 3. X-Axis Baseline
    drawLine(
      color = Gray300,
      start = Offset(leftAxisWidth, chartHeight),
      end = Offset(size.width, chartHeight),
      strokeWidth = 1.5.dp.toPx()
    )
  }
}

/**
 * Recharts Proportion Bar (100% Horizontal Stacked Bar):
 * `<Bar stackOffset="expand" />` showing proportion of Food, Transport, Housing, etc.
 */
@Composable
private fun RechartsProportionCanvas(
  categories: List<CategorySpend>,
  selectedCategory: String?,
  onSelectCategory: (String) -> Unit
) {
  val total = categories.sumOf { it.amount }.coerceAtLeast(1L)
  val animProgress = remember { Animatable(0f) }

  LaunchedEffect(categories) {
    animProgress.snapTo(0f)
    animProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(12.dp),
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "100% 비례 지출 구성비 (Proportional Distribution)",
      style = MaterialTheme.typography.labelSmall,
      color = Gray600,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Horizontal Stacked Bar
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(34.dp)
        .clip(RoundedCornerShape(12.dp))
        .pointerInput(categories, total) {
          detectTapGestures { tapOffset ->
            var accumulated = 0f
            for (cat in categories) {
              val width = (cat.amount.toFloat() / total.toFloat()) * size.width
              if (tapOffset.x in accumulated..(accumulated + width)) {
                onSelectCategory(cat.category)
                break
              }
              accumulated += width
            }
          }
        }
    ) {
      val progress = animProgress.value
      var currentX = 0f
      val barHeight = size.height

      categories.forEach { cat ->
        val segmentWidth = (cat.amount.toFloat() / total.toFloat()) * size.width * progress
        val isSelected = selectedCategory == cat.category

        drawRect(
          color = Color(cat.colorHex),
          topLeft = Offset(currentX, 0f),
          size = Size(segmentWidth, barHeight)
        )

        if (isSelected) {
          drawRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(currentX, 0f),
            size = Size(segmentWidth, barHeight)
          )
        }

        currentX += segmentWidth
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // Top 3 Types Summary
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      categories.take(3).forEach { cat ->
        val pct = (cat.amount.toFloat() / total.toFloat() * 100).toInt()
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(cat.colorHex))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = cat.category,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray800
            )
          }
          Text(
            text = "$pct% (${Formatters.formatCurrency(cat.amount)})",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = Gray600
          )
        }
      }
    }
  }
}
