package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.FinanceCategories
import com.example.data.model.TransactionType
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentRed
import com.example.ui.theme.Gray100
import com.example.ui.theme.Gray200
import com.example.ui.theme.Gray300
import com.example.ui.theme.Gray400
import com.example.ui.theme.Gray50
import com.example.ui.theme.Gray500
import com.example.ui.theme.Gray600
import com.example.ui.theme.Gray700
import com.example.ui.theme.Gray800
import com.example.ui.theme.Gray900
import com.example.ui.theme.TossBlue
import com.example.ui.theme.TossBlueContainer
import com.example.ui.viewmodel.DateRangePreset
import com.example.util.Formatters
import java.util.Calendar

/**
 * Modern Material 3 Search and Filter Bar for the transaction list.
 * Allows finding expenses and transactions by keyword, date range presets or custom picker, and category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSearchFilterBar(
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  selectedType: TransactionType?,
  onTypeSelect: (TransactionType?) -> Unit,
  selectedCategory: String?,
  onCategorySelect: (String?) -> Unit,
  availableCategories: List<String>,
  categoryCounts: Map<String, Int> = emptyMap(),
  dateRangePreset: DateRangePreset,
  onDateRangePresetSelect: (DateRangePreset) -> Unit,
  customStartDate: Long?,
  customEndDate: Long?,
  onCustomDateRangeSet: (Long?, Long?) -> Unit,
  dateRangeDisplay: String,
  filteredCount: Int,
  filteredExpense: Long,
  filteredIncome: Long,
  isAnyFilterActive: Boolean,
  onResetAllFilters: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showCustomDateDialog by remember { mutableStateOf(false) }

  Surface(
    color = Color.White,
    tonalElevation = 1.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
      // 1. Keyword Search Input
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("ledger_search_input"),
        placeholder = {
          Text(
            text = "지출 키워드, 거래처, 금액 검색 (예: 스타벅스, 식비)",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray400
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "검색",
            tint = if (searchQuery.isNotEmpty()) TossBlue else Gray500,
            modifier = Modifier.size(20.dp)
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { onSearchQueryChange("") },
              modifier = Modifier.testTag("clear_search_query_button")
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "검색어 지우기",
                tint = Gray600,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = TossBlue,
          unfocusedBorderColor = Gray200,
          focusedContainerColor = Gray50,
          unfocusedContainerColor = Gray50
        )
      )

      Spacer(modifier = Modifier.height(10.dp))

      // 2. Type Filter Chips (Focus on Expense / All / Income / Transfer)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val typeOptions = listOf(
          TransactionType.EXPENSE to "지출만",
          null to "전체",
          TransactionType.INCOME to "수입",
          TransactionType.TRANSFER to "이체"
        )

        typeOptions.forEach { (type, label) ->
          val isSelected = selectedType == type
          val activeBg = when (type) {
            TransactionType.EXPENSE -> AccentRed
            TransactionType.INCOME -> AccentGreen
            TransactionType.TRANSFER -> TossBlue
            null -> Gray900
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) activeBg else Gray100,
            modifier = Modifier
              .weight(1f)
              .clickable { onTypeSelect(type) }
              .testTag("filter_${label}")
          ) {
            Box(
              modifier = Modifier.padding(vertical = 7.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Gray800
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 3. Date Range Filter Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(end = 2.dp)
        ) {
          Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "기간",
            tint = TossBlue,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "기간",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Gray600
          )
        }

        val datePresets = listOf(
          DateRangePreset.THIS_MONTH to "이번 달",
          DateRangePreset.TODAY to "오늘",
          DateRangePreset.LAST_7_DAYS to "최근 7일",
          DateRangePreset.LAST_30_DAYS to "최근 30일"
        )

        datePresets.forEach { (preset, label) ->
          val isSelected = dateRangePreset == preset
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) TossBlue else Gray100,
            border = BorderStroke(
              width = 1.dp,
              color = if (isSelected) TossBlue else Color.Transparent
            ),
            modifier = Modifier
              .clickable { onDateRangePresetSelect(preset) }
              .testTag("date_range_preset_${preset.name.lowercase()}")
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) Color.White else Gray800,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }

        // Custom Date Range Picker Chip
        val isCustomSelected = dateRangePreset == DateRangePreset.CUSTOM
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isCustomSelected) TossBlue else Gray100,
          border = BorderStroke(
            width = 1.dp,
            color = if (isCustomSelected) TossBlue else Color.Transparent
          ),
          modifier = Modifier
            .clickable { showCustomDateDialog = true }
            .testTag("date_range_preset_custom")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CalendarMonth,
              contentDescription = "직접 선택",
              tint = if (isCustomSelected) Color.White else TossBlue,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isCustomSelected) "직접 선택됨 📅" else "직접 설정 📅",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isCustomSelected) Color.White else Gray800
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 4. Category Filter Row with CategoryIconHelper
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // "전체" (All categories) chip
        val isAllSelected = selectedCategory == null
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isAllSelected) Gray900 else Gray100,
          modifier = Modifier
            .clickable { onCategorySelect(null) }
            .testTag("filter_category_all")
        ) {
          Text(
            text = "카테고리 전체",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isAllSelected) Color.White else Gray800,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
          )
        }

        availableCategories.forEach { catName ->
          val isSelected = selectedCategory == catName
          val catColor = Color(CategoryIconHelper.getColorForCategory(catName))
          val icon = CategoryIconHelper.getIconForCategory(catName)
          val count = categoryCounts[catName] ?: 0

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) catColor.copy(alpha = 0.15f) else Gray100,
            border = BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) catColor else Color.Transparent
            ),
            modifier = Modifier
              .clickable {
                if (isSelected) onCategorySelect(null) else onCategorySelect(catName)
              }
              .testTag("filter_category_$catName")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(20.dp)
                  .clip(CircleShape)
                  .background(if (isSelected) catColor else Gray200),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = catName,
                  tint = if (isSelected) Color.White else Gray700,
                  modifier = Modifier.size(12.dp)
                )
              }
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = if (count > 0) "$catName ($count)" else catName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) catColor else Gray800
              )
            }
          }
        }
      }

      // 5. Active Filters & Real-time Matching Summary Bar
      AnimatedVisibility(
        visible = isAnyFilterActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
        ) {
          // Active Tags Row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "적용 필터:",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray600
            )

            // Keyword tag
            if (searchQuery.isNotBlank()) {
              ActiveFilterChip(
                label = "검색: \"$searchQuery\"",
                color = TossBlue,
                onRemove = { onSearchQueryChange("") },
                tag = "active_tag_keyword"
              )
            }

            // Type tag
            if (selectedType != null) {
              ActiveFilterChip(
                label = when (selectedType) {
                  TransactionType.EXPENSE -> "지출만"
                  TransactionType.INCOME -> "수입만"
                  TransactionType.TRANSFER -> "이체만"
                },
                color = when (selectedType) {
                  TransactionType.EXPENSE -> AccentRed
                  TransactionType.INCOME -> AccentGreen
                  TransactionType.TRANSFER -> TossBlue
                },
                onRemove = { onTypeSelect(null) },
                tag = "active_tag_type"
              )
            }

            // Category tag
            if (selectedCategory != null) {
              ActiveFilterChip(
                label = "카테고리: $selectedCategory",
                color = Color(CategoryIconHelper.getColorForCategory(selectedCategory)),
                onRemove = { onCategorySelect(null) },
                tag = "active_tag_category"
              )
            }

            // Date Range tag
            if (dateRangePreset != DateRangePreset.THIS_MONTH) {
              ActiveFilterChip(
                label = "기간: $dateRangeDisplay",
                color = AccentOrange,
                onRemove = { onDateRangePresetSelect(DateRangePreset.THIS_MONTH) },
                tag = "active_tag_date_range"
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Filter Summary & Reset All Button
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(TossBlueContainer.copy(alpha = 0.6f))
              .padding(horizontal = 10.dp, vertical = 7.dp)
              .testTag("filter_summary_banner"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.FilterAlt,
                contentDescription = null,
                tint = TossBlue,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = "검색 결과 ${filteredCount}건",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TossBlue
              )
              if (filteredExpense > 0L) {
                Text(
                  text = "· 지출 -${Formatters.formatCurrency(filteredExpense)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = AccentRed
                )
              }
              if (filteredIncome > 0L) {
                Text(
                  text = "· 수입 +${Formatters.formatCurrency(filteredIncome)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = AccentGreen
                )
              }
            }

            // Reset all filters button
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color.White,
              border = BorderStroke(1.dp, Gray300),
              modifier = Modifier
                .clickable { onResetAllFilters() }
                .testTag("reset_all_filters_button")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = "초기화",
                  tint = Gray700,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "초기화",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Gray700
                )
              }
            }
          }
        }
      }
    }
  }

  // Custom Date Range Modal Dialog
  if (showCustomDateDialog) {
    CustomDateRangePickerDialog(
      initialStartDate = customStartDate ?: System.currentTimeMillis(),
      initialEndDate = customEndDate ?: System.currentTimeMillis(),
      onDismiss = { showCustomDateDialog = false },
      onConfirm = { start, end ->
        onCustomDateRangeSet(start, end)
        showCustomDateDialog = false
      }
    )
  }
}

@Composable
private fun ActiveFilterChip(
  label: String,
  color: Color,
  onRemove: () -> Unit,
  tag: String
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = color.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    modifier = Modifier
      .clickable { onRemove() }
      .testTag(tag)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color
      )
      Spacer(modifier = Modifier.width(3.dp))
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "삭제",
        tint = color,
        modifier = Modifier.size(12.dp)
      )
    }
  }
}

/**
 * Custom Date Range Picker Dialog supporting Start & End dates selection
 * along with quick presets (최근 7일, 최근 14일, 최근 30일, 이번 달, 지난 달).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePickerDialog(
  initialStartDate: Long,
  initialEndDate: Long,
  onDismiss: () -> Unit,
  onConfirm: (Long, Long) -> Unit
) {
  var startDate by remember { mutableStateOf(initialStartDate) }
  var endDate by remember { mutableStateOf(initialEndDate) }
  var pickingTarget by remember { mutableStateOf<String?>(null) } // "start" or "end" or null

  val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
  val endDatePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate)

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .testTag("custom_date_range_dialog")
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CalendarToday,
              contentDescription = null,
              tint = TossBlue,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "기간 직접 설정",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "닫기",
              tint = Gray600
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Preset Buttons inside dialog
        Text(
          text = "빠른 기간 선택",
          style = MaterialTheme.typography.labelSmall,
          color = Gray600,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val presets = listOf(
            "최근 7일" to 6,
            "최근 14일" to 13,
            "최근 30일" to 29,
            "최근 90일" to 89
          )

          presets.forEach { (label, daysBack) ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Gray100,
              modifier = Modifier
                .weight(1f)
                .clickable {
                  val endCal = Calendar.getInstance()
                  val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysBack) }
                  startDate = startCal.timeInMillis
                  endDate = endCal.timeInMillis
                }
            ) {
              Box(
                modifier = Modifier.padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray800,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Date & End Date Selectors
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Start Date Box
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "시작일",
              style = MaterialTheme.typography.labelSmall,
              color = Gray600,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Gray50,
              border = BorderStroke(1.dp, TossBlue.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { pickingTarget = "start" }
                .testTag("date_range_start_picker_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.CalendarToday,
                  contentDescription = null,
                  tint = TossBlue,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = Formatters.formatDayHeader(startDate),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
              }
            }
          }

          // End Date Box
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "종료일",
              style = MaterialTheme.typography.labelSmall,
              color = Gray600,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Gray50,
              border = BorderStroke(1.dp, TossBlue.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { pickingTarget = "end" }
                .testTag("date_range_end_picker_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.CalendarToday,
                  contentDescription = null,
                  tint = TossBlue,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = Formatters.formatDayHeader(endDate),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Confirm & Dismiss Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
          ) {
            Text("취소", color = Gray700)
          }

          Button(
            onClick = {
              val validStart = minOf(startDate, endDate)
              val validEnd = maxOf(startDate, endDate)
              onConfirm(validStart, validEnd)
            },
            modifier = Modifier
              .weight(1f)
              .testTag("date_range_apply_button"),
            colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
            shape = RoundedCornerShape(12.dp)
          ) {
            Text("적용하기", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }

  // Nested DatePickerDialog for picking start or end date
  if (pickingTarget != null) {
    val isPickingStart = pickingTarget == "start"
    val state = if (isPickingStart) startDatePickerState else endDatePickerState

    DatePickerDialog(
      onDismissRequest = { pickingTarget = null },
      confirmButton = {
        TextButton(
          onClick = {
            state.selectedDateMillis?.let { millis ->
              if (isPickingStart) {
                startDate = millis
              } else {
                endDate = millis
              }
            }
            pickingTarget = null
          }
        ) {
          Text("선택 완료", color = TossBlue, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { pickingTarget = null }) {
          Text("취소", color = Gray600)
        }
      }
    ) {
      DatePicker(state = state)
    }
  }
}
