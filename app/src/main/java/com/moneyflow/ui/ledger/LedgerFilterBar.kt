package com.moneyflow.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.common.CategoryIconHelper
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray300
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray50
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Search + filter card for the ledger: keyword, type, date range (presets or a custom range),
 * category, and a summary/reset banner while any filter is active.
 */
@Composable
internal fun LedgerFilterBar(
  query: String,
  onQueryChange: (String) -> Unit,
  state: LedgerUiState,
  onTypeSelect: (TransactionType?) -> Unit,
  onCategorySelect: (String?) -> Unit,
  onPresetSelect: (DateRangePreset) -> Unit,
  onCustomRange: (LocalDate, LocalDate) -> Unit,
  onResetFilters: () -> Unit,
  modifier: Modifier = Modifier
) {
  val filters = state.filters
  var showRangePicker by rememberSaveable { mutableStateOf(false) }

  val availableCategories = remember(filters.type, state.monthTransactions) {
    val standard = filters.type?.let { FinanceCategories.categoriesFor(it) }
      ?: TransactionType.entries.flatMap { FinanceCategories.categoriesFor(it) }
    // Categories that exist in the data but are not in the standard list (e.g. legacy ones).
    val extra = state.monthTransactions
      .filter { filters.type == null || it.type == filters.type }
      .map { it.category }
      .filter { it.isNotBlank() && it !in standard }
      .distinct()
    standard + extra
  }
  val categoryCounts = remember(filters.type, state.monthTransactions) {
    state.monthTransactions
      .filter { filters.type == null || it.type == filters.type }
      .groupingBy { it.category }
      .eachCount()
  }

  Surface(color = CardSurface, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
      // 1. Keyword search
      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).testTag("ledger_search_input"),
        placeholder = {
          Text(
            "메모, 카테고리, 계좌, 금액 검색",
            color = Gray400,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null, tint = if (query.isNotEmpty()) TossBlue else Gray500)
        },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("clear_search_query_button")) {
              Icon(Icons.Default.Clear, contentDescription = "검색어 지우기", tint = Gray600)
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

      Spacer(Modifier.height(4.dp))

      // 2. Type
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val options = listOf<Pair<TransactionType?, String>>(
          null to "전체",
          TransactionType.EXPENSE to "지출",
          TransactionType.INCOME to "수입",
          TransactionType.TRANSFER to "이체"
        )
        options.forEach { (type, label) ->
          val selected = filters.type == type
          val activeBg = when (type) {
            TransactionType.EXPENSE -> AccentRed
            TransactionType.INCOME -> AccentGreen
            TransactionType.TRANSFER -> TossBlue
            null -> Gray900
          }
          Surface(
            onClick = { onTypeSelect(type) },
            shape = RoundedCornerShape(10.dp),
            color = if (selected) activeBg else Gray100,
            modifier = Modifier.weight(1f).testTag("filter_type_${type?.name?.lowercase() ?: "all"}")
          ) {
            Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
              Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                // Gray900 fill flips to light in dark mode, so its content must use CardSurface.
                color = when {
                  !selected -> Gray800
                  type == null -> CardSurface
                  else -> OnBrand
                },
                maxLines = 1
              )
            }
          }
        }
      }

      // 3. Date range
      LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        items(DateRangePreset.entries.filter { it != DateRangePreset.CUSTOM }, key = { it.name }) { preset ->
          FilterPill(
            label = preset.label,
            selected = filters.preset == preset,
            onClick = { onPresetSelect(preset) },
            modifier = Modifier.testTag("date_range_preset_${preset.name.lowercase()}")
          )
        }
        item(key = "custom") {
          val selected = filters.preset == DateRangePreset.CUSTOM
          FilterPill(
            label = if (selected) state.dateRangeLabel else DateRangePreset.CUSTOM.label,
            selected = selected,
            onClick = { showRangePicker = true },
            leadingIcon = {
              Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = if (selected) OnBrand else TossBlue,
                modifier = Modifier.size(14.dp)
              )
            },
            modifier = Modifier.testTag("date_range_preset_custom")
          )
        }
      }

      // 4. Category
      LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        item(key = "all") {
          val selected = filters.category == null
          Surface(
            onClick = { onCategorySelect(null) },
            shape = RoundedCornerShape(12.dp),
            color = if (selected) Gray900 else Gray100,
            modifier = Modifier.testTag("filter_category_all")
          ) {
            Text(
              "카테고리 전체",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) CardSurface else Gray800,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
          }
        }
        items(availableCategories, key = { it }) { cat ->
          val selected = filters.category == cat
          val catColor = Color(CategoryIconHelper.getColorForCategory(cat))
          val count = categoryCounts[cat] ?: 0
          Surface(
            onClick = { onCategorySelect(if (selected) null else cat) },
            shape = RoundedCornerShape(12.dp),
            color = if (selected) catColor.copy(alpha = 0.15f) else Gray100,
            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) catColor else Color.Transparent),
            modifier = Modifier.testTag("filter_category_$cat")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
              Box(
                Modifier.size(22.dp).background(if (selected) catColor else Gray200, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  CategoryIconHelper.getIconForCategory(cat),
                  contentDescription = null,
                  tint = if (selected) OnBrand else Gray700,
                  modifier = Modifier.size(13.dp)
                )
              }
              Spacer(Modifier.width(5.dp))
              Text(
                if (count > 0) "$cat ($count)" else cat,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) catColor else Gray800,
                maxLines = 1
              )
            }
          }
        }
      }

      // 5. Summary + reset while a filter is active
      AnimatedVisibility(
        visible = filters.isAnyActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp)
            .background(TossBlueContainer, RoundedCornerShape(12.dp))
            .padding(start = 12.dp)
            .testTag("filter_summary_banner"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.FilterAlt, contentDescription = null, tint = TossBlue, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
              "검색 결과 ${state.transactions.size}건 · ${state.dateRangeLabel}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = TossBlue,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            val sums = buildList {
              if (state.filteredExpense > 0) add("지출 -${Formatters.formatCurrency(state.filteredExpense)}")
              if (state.filteredIncome > 0) add("수입 +${Formatters.formatCurrency(state.filteredIncome)}")
            }
            if (sums.isNotEmpty()) {
              Text(
                sums.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Gray700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          TextButton(onClick = onResetFilters, modifier = Modifier.testTag("reset_all_filters_button")) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Gray700, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("초기화", color = Gray700, fontWeight = FontWeight.Bold, maxLines = 1)
          }
        }
      }
    }
  }

  if (showRangePicker) {
    CustomRangeDialog(
      initialStart = filters.customStart ?: state.month.atDay(1),
      initialEnd = filters.customEnd ?: minOf(state.month.atEndOfMonth(), state.today.coerceAtLeast(state.month.atDay(1))),
      onDismiss = { showRangePicker = false },
      onConfirm = { s, e ->
        onCustomRange(s, e)
        showRangePicker = false
      }
    )
  }
}

/** Small selectable pill; Surface(onClick) keeps the touch target at 48dp. */
@Composable
private fun FilterPill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = if (selected) TossBlue else Gray100,
    border = if (selected) null else BorderStroke(1.dp, Gray300.copy(alpha = 0.4f)),
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      if (leadingIcon != null) {
        leadingIcon()
        Spacer(Modifier.width(4.dp))
      }
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) OnBrand else Gray800,
        maxLines = 1
      )
    }
  }
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.pickerMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
  initialStart: LocalDate,
  initialEnd: LocalDate,
  onDismiss: () -> Unit,
  onConfirm: (LocalDate, LocalDate) -> Unit
) {
  val pickerState = rememberDateRangePickerState(
    initialSelectedStartDateMillis = initialStart.toPickerMillis(),
    initialSelectedEndDateMillis = initialEnd.toPickerMillis()
  )
  val start = pickerState.selectedStartDateMillis
  val end = pickerState.selectedEndDateMillis
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          val s = start?.pickerMillisToDate() ?: return@TextButton
          // A single tapped day means a one-day range.
          val e = end?.pickerMillisToDate() ?: s
          onConfirm(minOf(s, e), maxOf(s, e))
        },
        enabled = start != null,
        modifier = Modifier.testTag("date_range_apply_button")
      ) { Text("적용", color = if (start != null) TossBlue else Gray400, fontWeight = FontWeight.Bold) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = Gray600) } },
    colors = DatePickerDefaults.colors(containerColor = CardSurface)
  ) {
    DateRangePicker(
      state = pickerState,
      modifier = Modifier.weight(1f).testTag("custom_date_range_picker"),
      title = {
        Text(
          "기간 직접 설정",
          modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
          fontWeight = FontWeight.Bold,
          color = Gray900
        )
      },
      colors = DatePickerDefaults.colors(containerColor = CardSurface)
    )
  }
}
