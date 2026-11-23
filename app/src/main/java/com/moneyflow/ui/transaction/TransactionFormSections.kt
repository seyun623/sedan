package com.moneyflow.ui.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.common.CategoryIconHelper
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

internal val PAYMENT_METHODS = listOf("체크카드", "신용카드", "계좌이체", "현금", "간편결제")

internal fun TransactionType.accentColor(): Color = when (this) {
  TransactionType.EXPENSE -> AccentRed
  TransactionType.INCOME -> AccentGreen
  TransactionType.TRANSFER -> TossBlue
}

/** White rounded card used for each form section. */
@Composable
internal fun FormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Surface(color = CardSurface, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), content = content)
  }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = Gray600,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
  )
}

/** 지출 / 수입 / 이체 segmented control. */
@Composable
internal fun TypeSegmentedControl(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
  Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
    Row(
      Modifier.padding(6.dp).background(Gray100, RoundedCornerShape(12.dp)).padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      TransactionType.entries.forEach { type ->
        val isSelected = type == selected
        Surface(
          onClick = { onSelect(type) },
          shape = RoundedCornerShape(10.dp),
          color = if (isSelected) type.accentColor() else Color.Transparent,
          modifier = Modifier
            .weight(1f)
            .semantics { this.selected = isSelected; role = Role.Tab }
            .testTag("form_type_tab_${type.name.lowercase()}")
        ) {
          Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
              type.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
              color = if (isSelected) OnBrand else Gray600,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

private data class QuickTemplate(val label: String, val amount: Long, val category: String, val memo: String, val paymentMethod: String)

private fun templatesFor(type: TransactionType): List<QuickTemplate> = when (type) {
  TransactionType.EXPENSE -> listOf(
    QuickTemplate("☕ 커피", 4500L, "카페/간식", "커피", "체크카드"),
    QuickTemplate("🍱 점심 식사", 10000L, "식비", "점심 식사", "체크카드"),
    QuickTemplate("🚇 대중교통", 1500L, "교통", "지하철/버스", "체크카드"),
    QuickTemplate("🛒 편의점/마트", 8500L, "생활/마트", "편의점", "체크카드"),
    QuickTemplate("💵 현금 결제", 10000L, "식비", "현금 결제", "현금"),
    QuickTemplate("👥 더치페이", 15000L, "식비", "모임 정산", "계좌이체")
  )
  TransactionType.INCOME -> listOf(
    QuickTemplate("💼 월급", 3_000_000L, "급여", "월급", "계좌이체"),
    QuickTemplate("👥 더치페이 받음", 15000L, "용돈", "모임 정산 받음", "계좌이체"),
    QuickTemplate("🥕 중고거래", 30000L, "부수입/중고거래", "중고거래", "계좌이체"),
    QuickTemplate("📈 이자", 10000L, "금융소득/배당", "예금 이자", "계좌이체")
  )
  TransactionType.TRANSFER -> listOf(
    QuickTemplate("🏦 저축", 300000L, "저축/투자이체", "저축 이체", "계좌이체"),
    QuickTemplate("💳 카드대금", 200000L, "카드대금납부", "카드대금 납부", "계좌이체")
  )
}

/** One-tap presets that fill amount, category, memo and payment method. */
@Composable
internal fun QuickTemplatesCard(
  type: TransactionType,
  onApply: (amount: Long, category: String, memo: String, paymentMethod: String) -> Unit
) {
  FormCard {
    SectionLabel("자주 쓰는 내역")
    Spacer(Modifier.height(4.dp))
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      templatesFor(type).forEachIndexed { idx, tpl ->
        Surface(
          onClick = { onApply(tpl.amount, tpl.category, tpl.memo, tpl.paymentMethod) },
          shape = RoundedCornerShape(12.dp),
          color = Gray100,
          modifier = Modifier.testTag("preset_chip_$idx")
        ) {
          Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(tpl.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Gray900, maxLines = 1)
            Text(Formatters.formatCurrency(tpl.amount), style = MaterialTheme.typography.labelSmall, color = TossBlue, maxLines = 1)
          }
        }
      }
    }
  }
}

/** "+1천 / +5천 / +1만 / +5만 / +10만" quick increments. */
@Composable
internal fun AmountIncrementRow(onAdd: (Long) -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    listOf(1_000L to "+1천", 5_000L to "+5천", 10_000L to "+1만", 50_000L to "+5만", 100_000L to "+10만").forEach { (inc, label) ->
      Surface(
        onClick = { onAdd(inc) },
        shape = RoundedCornerShape(10.dp),
        color = Gray100,
        modifier = Modifier.weight(1f).testTag("amount_add_$inc")
      ) {
        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
          Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Gray900, maxLines = 1)
        }
      }
    }
  }
}

/** Date + time selectors with 오늘/어제/그제 shortcuts. */
@Composable
internal fun DateTimeCard(
  timestamp: Long,
  dayOffsetSelected: (Int) -> Boolean,
  onPickDate: () -> Unit,
  onPickTime: () -> Unit,
  onQuickDay: (Int) -> Unit
) {
  FormCard {
    SectionLabel("거래 일시")
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      PickerButton(
        icon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TossBlue, modifier = Modifier.size(18.dp)) },
        text = Formatters.formatDayHeader(timestamp),
        onClickLabel = "날짜 선택",
        onClick = onPickDate,
        modifier = Modifier.weight(1.4f).testTag("open_date_picker_button")
      )
      PickerButton(
        icon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = TossBlue, modifier = Modifier.size(18.dp)) },
        text = Formatters.formatTime(timestamp),
        onClickLabel = "시간 선택",
        onClick = onPickTime,
        modifier = Modifier.weight(1f).testTag("open_time_picker_button")
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(0 to "오늘", 1 to "어제", 2 to "그제").forEach { (offset, label) ->
        val sel = dayOffsetSelected(offset)
        Surface(
          onClick = { onQuickDay(offset) },
          shape = RoundedCornerShape(10.dp),
          color = if (sel) TossBlue else Gray100,
          modifier = Modifier.weight(1f).semantics { selected = sel }.testTag("date_chip_$offset")
        ) {
          Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
              label,
              style = MaterialTheme.typography.labelMedium,
              color = if (sel) OnBrand else Gray900,
              fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PickerButton(
  icon: @Composable () -> Unit,
  text: String,
  onClickLabel: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = TossBlue.copy(alpha = 0.08f),
    border = BorderStroke(1.dp, TossBlue.copy(alpha = 0.3f)),
    modifier = modifier.semantics { role = Role.Button }
  ) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      icon()
      Spacer(Modifier.width(6.dp))
      Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Gray900,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/** Category chips for the current type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryCard(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
  FormCard {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        "카테고리",
        style = MaterialTheme.typography.titleSmall,
        color = Gray900,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f)
      )
      Text(
        selected,
        style = MaterialTheme.typography.labelLarge,
        color = Color(CategoryIconHelper.getColorForCategory(selected)),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      categories.forEach { cat ->
        val isSelected = cat == selected
        val catColor = Color(CategoryIconHelper.getColorForCategory(cat))
        Surface(
          onClick = { onSelect(cat) },
          shape = RoundedCornerShape(14.dp),
          color = if (isSelected) catColor.copy(alpha = 0.15f) else Gray100,
          border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) catColor else Color.Transparent),
          modifier = Modifier.semantics { this.selected = isSelected }.testTag("form_category_$cat")
        ) {
          Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
              Modifier.size(24.dp).background(if (isSelected) catColor else Gray200, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                CategoryIconHelper.getIconForCategory(cat),
                contentDescription = null,
                tint = if (isSelected) OnBrand else Gray700,
                modifier = Modifier.size(14.dp)
              )
            }
            Spacer(Modifier.width(6.dp))
            Text(
              cat,
              style = MaterialTheme.typography.bodyMedium,
              color = if (isSelected) catColor else Gray800,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

/** Account dropdown. [accounts] are the selectable options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDropdown(
  label: String,
  accounts: List<AccountEntity>,
  selectedId: Long,
  onSelect: (Long) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = "form_account_dropdown"
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val selected = accounts.firstOrNull { it.id == selectedId }
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier.fillMaxWidth()) {
    OutlinedTextField(
      value = selected?.name ?: "",
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      placeholder = { Text("계좌를 선택해 주세요") },
      supportingText = selected?.let { { Text("${it.bankName} · 잔액 ${Formatters.formatCurrency(it.balance)}", maxLines = 1, overflow = TextOverflow.Ellipsis) } },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        .testTag(testTag)
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = CardSurface) {
      accounts.forEach { acc ->
        DropdownMenuItem(
          text = {
            Column {
              Text(acc.name, color = Gray900, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                "${acc.bankName} · ${Formatters.formatCurrency(acc.balance)}",
                style = MaterialTheme.typography.labelSmall,
                color = Gray500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          },
          leadingIcon = { Box(Modifier.size(10.dp).background(Color(acc.colorHex), CircleShape)) },
          onClick = {
            onSelect(acc.id)
            expanded = false
          },
          contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaymentMethodChips(selected: String, onSelect: (String) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
    PAYMENT_METHODS.forEach { method ->
      val isSelected = method == selected
      Surface(
        onClick = { onSelect(method) },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) TossBlue else Gray100,
        modifier = Modifier.semantics { this.selected = isSelected }.testTag("payment_method_$method")
      ) {
        Text(
          method,
          style = MaterialTheme.typography.labelLarge,
          color = if (isSelected) OnBrand else Gray900,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          maxLines = 1,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
      }
    }
  }
}

@Composable
internal fun ExcludeFromBudgetRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text("예산에서 제외", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Gray900)
      Text(
        "경조사비, 회사 대납금 등 예외 지출일 때 켜 주세요",
        style = MaterialTheme.typography.labelSmall,
        color = Gray600
      )
    }
    Spacer(Modifier.width(8.dp))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedTrackColor = TossBlue, checkedThumbColor = OnBrand),
      modifier = Modifier.testTag("form_budget_exclude_switch")
    )
  }
}

/** "이 결제는 ○○카드가 유리해요 · 약 600원 혜택" under the account picker (expenses only). */
@Composable
internal fun CardPickHint(
  picks: List<com.moneyflow.domain.card.CardPick>,
  accounts: List<AccountEntity>,
  selectedId: Long,
  onUse: (Long) -> Unit
) {
  val best = picks.firstOrNull { p -> accounts.any { it.id == p.accountId } } ?: return
  val name = accounts.first { it.id == best.accountId }.name
  val selectedIsBest = best.accountId == selectedId
  val text = when {
    best.forPerformance && selectedIsBest -> "이 카드는 실적이 ${com.moneyflow.util.Formatters.formatCurrency(best.shortage)} 남았어요"
    best.forPerformance -> "혜택 카드는 없어요. ${name} 실적이 ${com.moneyflow.util.Formatters.formatCurrency(best.shortage)} 남았어요"
    selectedIsBest -> "가장 유리한 카드예요 · 약 ${com.moneyflow.util.Formatters.formatCurrency(best.expected)} 혜택 (${best.benefit?.title.orEmpty()})"
    else -> "${name}(으)로 내면 약 ${com.moneyflow.util.Formatters.formatCurrency(best.expected)} 혜택 (${best.benefit?.title.orEmpty()})"
  }
  androidx.compose.material3.Surface(
    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    color = if (selectedIsBest) com.moneyflow.ui.theme.Gray100 else com.moneyflow.ui.theme.TossBlueContainer,
    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("card_pick_hint")
  ) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("💳 $text", style = MaterialTheme.typography.bodySmall, color = Gray900, modifier = Modifier.weight(1f))
      if (!selectedIsBest) {
        androidx.compose.material3.TextButton(onClick = { onUse(best.accountId) }) { Text("이 카드로", fontWeight = FontWeight.Bold) }
      }
    }
  }
}
