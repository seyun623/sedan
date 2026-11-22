package com.example.ui.components

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.Gray100
import com.example.ui.theme.Gray200
import com.example.ui.theme.Gray300
import com.example.ui.theme.Gray500
import com.example.ui.theme.Gray600
import com.example.ui.theme.Gray900
import com.example.ui.theme.TossBlue
import com.example.ui.viewmodel.CategorySpend
import com.example.util.Formatters
import kotlin.math.atan2
import kotlin.math.sqrt

enum class ChartStyle {
  DONUT,
  PIE
}

/**
 * Interactive Recharts / Custom Canvas Pie & Donut Chart component for the Home Screen.
 * Visualizes monthly spending distribution by category with smooth touch detection,
 * pop-out segment highlight animation, center stat readout, and interactive category legend chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyCategoryPieChartCard(
  categories: List<CategorySpend>,
  totalMonthlyExpense: Long,
  monthDisplay: String,
  onNavigateToAnalytics: () -> Unit,
  onAddTransaction: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedCategory by remember(categories) { mutableStateOf<String?>(null) }
  var chartStyle by remember { mutableStateOf(ChartStyle.DONUT) }

  val animProgress = remember { Animatable(0f) }

  LaunchedEffect(categories, chartStyle) {
    animProgress.snapTo(0f)
    animProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
    )
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("home_monthly_category_pie_card"),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      // Header: Title, Subtitle, and Style Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (chartStyle == ChartStyle.DONUT) Icons.Default.DonutLarge else Icons.Default.PieChart,
              contentDescription = null,
              tint = TossBlue,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "$monthDisplay 소비 카테고리",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (categories.isNotEmpty()) "차트 조각을 터치해 카테고리별 비중을 확인해보세요" else "이번 달 소비 내역을 기록해보세요",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        // Donut vs Pie Toggle Button
        if (categories.isNotEmpty()) {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(Gray100)
              .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (chartStyle == ChartStyle.DONUT) Color.White else Color.Transparent)
                .clickable { chartStyle = ChartStyle.DONUT }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("toggle_chart_donut"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "도넛",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (chartStyle == ChartStyle.DONUT) FontWeight.Bold else FontWeight.Medium,
                color = if (chartStyle == ChartStyle.DONUT) TossBlue else Gray600
              )
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (chartStyle == ChartStyle.PIE) Color.White else Color.Transparent)
                .clickable { chartStyle = ChartStyle.PIE }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("toggle_chart_pie"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "파이",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (chartStyle == ChartStyle.PIE) FontWeight.Bold else FontWeight.Medium,
                color = if (chartStyle == ChartStyle.PIE) TossBlue else Gray600
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (categories.isEmpty() || totalMonthlyExpense <= 0L) {
        // Empty State
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Gray100.copy(alpha = 0.5f))
            .padding(24.dp)
            .testTag("pie_chart_empty_state"),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TossBlue.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.PieChart,
                contentDescription = null,
                tint = TossBlue,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "아직 $monthDisplay 지출 내역이 없습니다",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "가계부에 첫 지출을 기록하면 카테고리별 차트가 완성됩니다",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = onAddTransaction,
              colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("pie_chart_add_tx_btn")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("지출 기록하기", fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        // Active Chart Display
        val totalAmount = categories.sumOf { it.amount }.toFloat().coerceAtLeast(1f)
        val selectedSpend = categories.find { it.category == selectedCategory }

        // Top Insight Badge
        val topCategory = categories.firstOrNull()
        if (topCategory != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFF2F6FC))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(Color(topCategory.colorHex))
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "가장 큰 지출: '${topCategory.category}' (${(topCategory.percentage * 100).toInt()}%)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
            }
            Text(
              text = Formatters.formatCurrency(topCategory.amount),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.ExtraBold,
              color = TossBlue
            )
          }
          Spacer(modifier = Modifier.height(14.dp))
        }

        // Custom Canvas Pie / Donut Chart with Interactive Tap Detection
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
          contentAlignment = Alignment.Center
        ) {
          Canvas(
            modifier = Modifier
              .size(220.dp)
              .testTag("interactive_pie_canvas")
              .pointerInput(categories, selectedCategory, chartStyle) {
                detectTapGestures { tapOffset ->
                  val canvasWidth = size.width.toFloat()
                  val canvasHeight = size.height.toFloat()
                  val minDim = kotlin.math.min(canvasWidth, canvasHeight)
                  val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
                  val dx = tapOffset.x - center.x
                  val dy = tapOffset.y - center.y
                  val dist = sqrt(dx * dx + dy * dy)
                  val outerRadius = minDim / 2f
                  val innerRadius = if (chartStyle == ChartStyle.DONUT) outerRadius * 0.58f else 0f

                  if (dist in innerRadius..outerRadius) {
                    // Calculate angle starting from top (-90 degrees)
                    val rad = atan2(dy, dx)
                    var deg = Math.toDegrees(rad.toDouble()).toFloat() + 90f
                    if (deg < 0) deg += 360f

                    var cumulativeAngle = 0f
                    for (cat in categories) {
                      val sweep = (cat.amount.toFloat() / totalAmount) * 360f
                      if (deg in cumulativeAngle..(cumulativeAngle + sweep)) {
                        selectedCategory = if (selectedCategory == cat.category) null else cat.category
                        break
                      }
                      cumulativeAngle += sweep
                    }
                  } else if (dist < innerRadius) {
                    // Tap center resets selection
                    selectedCategory = null
                  }
                }
              }
          ) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) - 10.dp.toPx()
            val donutStrokeWidth = 28.dp.toPx()

            var startAngle = -90f

            categories.forEach { cat ->
              val sweep = (cat.amount.toFloat() / totalAmount) * 360f * animProgress.value
              if (sweep > 0.1f) {
                val isSelected = selectedCategory == cat.category
                val isDimmed = selectedCategory != null && !isSelected
                val baseColor = Color(cat.colorHex)
                val sliceColor = if (isDimmed) baseColor.copy(alpha = 0.35f) else baseColor

                // Pop-out radial effect for active slice
                val popOffset = if (isSelected) 6.dp.toPx() else 0f
                val midAngleRad = Math.toRadians((startAngle + sweep / 2.0)).toFloat()
                val sliceCenterOffset = Offset(
                  x = canvasCenter.x + kotlin.math.cos(midAngleRad) * popOffset,
                  y = canvasCenter.y + kotlin.math.sin(midAngleRad) * popOffset
                )

                if (chartStyle == ChartStyle.DONUT) {
                  val arcRadius = baseRadius - (donutStrokeWidth / 2f) + (if (isSelected) 3.dp.toPx() else 0f)
                  val arcTopLeft = Offset(sliceCenterOffset.x - arcRadius, sliceCenterOffset.y - arcRadius)
                  val arcSize = Size(arcRadius * 2, arcRadius * 2)

                  drawArc(
                    color = sliceColor,
                    startAngle = startAngle,
                    sweepAngle = (sweep - if (categories.size > 1) 2f else 0f).coerceAtLeast(0.1f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(
                      width = donutStrokeWidth + (if (isSelected) 4.dp.toPx() else 0f),
                      cap = StrokeCap.Round
                    )
                  )
                } else {
                  // Solid Pie Slice
                  val pieRadius = baseRadius + (if (isSelected) 4.dp.toPx() else 0f)
                  val pieTopLeft = Offset(sliceCenterOffset.x - pieRadius, sliceCenterOffset.y - pieRadius)
                  val pieSize = Size(pieRadius * 2, pieRadius * 2)

                  drawArc(
                    color = sliceColor,
                    startAngle = startAngle,
                    sweepAngle = (sweep - if (categories.size > 1) 1.5f else 0f).coerceAtLeast(0.1f),
                    useCenter = true,
                    topLeft = pieTopLeft,
                    size = pieSize,
                    style = Fill
                  )

                  if (isSelected) {
                    drawArc(
                      color = Gray900.copy(alpha = 0.25f),
                      startAngle = startAngle,
                      sweepAngle = sweep,
                      useCenter = true,
                      topLeft = pieTopLeft,
                      size = pieSize,
                      style = Stroke(width = 1.5.dp.toPx())
                    )
                  }
                }
              }
              startAngle += sweep
            }
          }

          // Center Content in Donut Mode
          if (chartStyle == ChartStyle.DONUT) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .clickable { selectedCategory = null }
                .padding(8.dp),
              verticalArrangement = Arrangement.Center
            ) {
              if (selectedSpend != null) {
                Text(
                  text = selectedSpend.category,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color(selectedSpend.colorHex),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = Formatters.formatCurrency(selectedSpend.amount),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Black,
                  color = Gray900,
                  maxLines = 1
                )
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color(selectedSpend.colorHex).copy(alpha = 0.12f)
                ) {
                  Text(
                    text = "비중 ${(selectedSpend.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(selectedSpend.colorHex),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                  )
                }
              } else {
                Text(
                  text = "총 지출",
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray600
                )
                Text(
                  text = Formatters.formatCurrency(totalMonthlyExpense),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Black,
                  color = Gray900,
                  maxLines = 1
                )
                Text(
                  text = "${categories.size}개 카테고리",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  color = Gray500
                )
              }
            }
          }
        }

        // Active Category Detail Banner (Especially helpful when in Pie mode)
        if (selectedSpend != null) {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(selectedSpend.colorHex).copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(selectedSpend.colorHex).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(selectedSpend.colorHex))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = selectedSpend.category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900
                  )
                  Text(
                    text = "이번 달 총 지출의 ${(selectedSpend.percentage * 100).toInt()}% 차지",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray600
                  )
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = Formatters.formatCurrency(selectedSpend.amount),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.ExtraBold,
                  color = Gray900
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                  modifier = Modifier
                    .clip(CircleShape)
                    .background(Gray200)
                    .clickable { selectedCategory = null }
                    .padding(4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "선택 해제",
                    tint = Gray600,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Category Legend Chips
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          categories.forEach { item ->
            val isSelected = selectedCategory == item.category
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = if (isSelected) Color(item.colorHex).copy(alpha = 0.16f) else Gray100,
              border = if (isSelected) androidx.compose.foundation.BorderStroke(1.2.dp, Color(item.colorHex)) else null,
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable {
                  selectedCategory = if (isSelected) null else item.category
                }
                .testTag("pie_legend_${item.category}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(item.colorHex))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = item.category,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Gray900 else Gray600
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "${(item.percentage * 100).toInt()}%",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.ExtraBold,
                  color = if (isSelected) Color(item.colorHex) else Gray500
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Link to full analytics report
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gray100.copy(alpha = 0.7f))
            .clickable { onNavigateToAnalytics() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("pie_chart_to_analytics_btn"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "막대 차트 및 소비 상세 분석 보기",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TossBlue
          )
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "소비 분석",
            tint = TossBlue,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}
