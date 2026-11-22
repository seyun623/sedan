package com.moneyflow.ui.ledger

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.Formatters
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

/** Compact amount for calendar cells: 8,500 -> "8,500", 125,000 -> "12.5만", 1.5억 -> "1.5억". */
internal fun compactAmount(amount: Long): String = when {
  amount >= 100_000_000L -> trimDecimal(amount / 100_000_000.0) + "억"
  amount >= 10_000L -> trimDecimal(amount / 10_000.0) + "만"
  else -> Formatters.formatNumber(amount)
}

private fun trimDecimal(v: Double): String {
  val s = if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
  return s.removeSuffix(".0")
}

/**
 * Month grid (Sunday first) with the day's expense / income. Tapping a day filters the list
 * to that day; tapping it again clears the selection.
 */
@Composable
internal fun LedgerCalendar(
  month: YearMonth,
  dailyExpense: Map<Int, Long>,
  dailyIncome: Map<Int, Long>,
  selectedDate: LocalDate?,
  today: LocalDate,
  onSelectDate: (LocalDate) -> Unit,
  modifier: Modifier = Modifier
) {
  val selectedBg = TossBlueContainer
  val textPrimary = Gray900
  val textSecondary = Gray600
  // Leading blank cells: DayOfWeek.value is 1 (Mon)..7 (Sun); Sunday-first grid -> value % 7.
  val cells: List<Int?> = remember(month) {
    val lead = month.atDay(1).dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val list = List(lead) { null } + (1..days).toList()
    list + List((7 - list.size % 7) % 7) { null }
  }

  Surface(color = CardSurface, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth().testTag("ledger_calendar")) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
      Row(Modifier.fillMaxWidth()) {
        WEEKDAYS.forEachIndexed { i, d ->
          Text(
            d,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = when (i) { 0 -> AccentRed; 6 -> TossBlue; else -> textSecondary }
          )
        }
      }
      Spacer(Modifier.height(6.dp))
      cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          week.forEachIndexed { col, day ->
            if (day == null) {
              Spacer(Modifier.weight(1f))
            } else {
              val date = month.atDay(day)
              val isSelected = date == selectedDate
              val isToday = date == today
              val expense = dailyExpense[day] ?: 0L
              val income = dailyIncome[day] ?: 0L
              val description = buildString {
                append("${month.monthValue}월 ${day}일")
                if (expense > 0) append(", 지출 ${Formatters.formatCurrency(expense)}")
                if (income > 0) append(", 수입 ${Formatters.formatCurrency(income)}")
              }
              Box(
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 56.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) selectedBg else Color.Transparent)
                  .then(
                    if (isToday && !isSelected) Modifier.border(1.dp, TossBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    else Modifier
                  )
                  .clickable(role = Role.Button) { onSelectDate(date) }
                  .semantics {
                    contentDescription = description
                    this.selected = isSelected
                  }
                  .padding(vertical = 4.dp)
                  .testTag("calendar_day_$day"),
                contentAlignment = Alignment.TopCenter
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    day.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = when {
                      isSelected -> TossBlue
                      col == 0 -> AccentRed
                      else -> textPrimary
                    }
                  )
                  if (expense > 0) {
                    Text(
                      "-${compactAmount(expense)}",
                      fontSize = 9.sp,
                      lineHeight = 11.sp,
                      color = AccentRed,
                      maxLines = 1,
                      overflow = TextOverflow.Clip
                    )
                  }
                  if (income > 0) {
                    Text(
                      "+${compactAmount(income)}",
                      fontSize = 9.sp,
                      lineHeight = 11.sp,
                      color = AccentGreen,
                      maxLines = 1,
                      overflow = TextOverflow.Clip
                    )
                  }
                }
              }
            }
          }
        }
        Spacer(Modifier.height(2.dp))
      }
    }
  }
}
