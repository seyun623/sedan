@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.moneyflow.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.MonthBudget
import com.moneyflow.ui.common.CategoryIconHelper
import com.moneyflow.ui.common.KrwAmountField
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAY_MILLIS = 86_400_000L

private fun String.toAmount(): Long = toLongOrNull() ?: 0L
private fun Long.toAmountInput(): String = if (this > 0) toString() else ""

// ================================================================================================
// Shared dialog shell
// ================================================================================================

@Composable
private fun FormDialog(
  title: String,
  subtitle: String?,
  confirmLabel: String,
  confirmEnabled: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  testTag: String,
  content: @Composable ColumnScope.() -> Unit
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = CardSurface,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .testTag(testTag)
    ) {
      Column(Modifier.padding(vertical = 20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gray900)
            if (subtitle != null) {
              Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray600)
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600)
          }
        }
        Spacer(Modifier.height(12.dp))
        Column(
          modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          content = content
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp)
          ) { Text("취소", color = Gray900) }
          Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(2f).heightIn(min = 52.dp).testTag("${testTag}_confirm"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
          ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        }
      }
    }
  }
}

@Composable
private fun FieldLabel(text: String) {
  Text(text, style = MaterialTheme.typography.labelLarge, color = Gray600, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InfoText(text: String, color: Color = Gray600) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Read-only dropdown of accounts. */
@Composable
private fun AccountDropdown(
  accounts: List<AccountEntity>,
  selectedId: Long?,
  onSelect: (Long) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  showBalance: Boolean = false
) {
  var expanded by remember { mutableStateOf(false) }
  val selected = accounts.firstOrNull { it.id == selectedId }
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
    OutlinedTextField(
      value = selected?.let { "${it.name} (${it.bankName})" } ?: "",
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      placeholder = { Text("계좌를 선택하세요") },
      supportingText = if (showBalance && selected != null) {
        { Text("잔액 ${Formatters.formatCurrency(selected.balance)}") }
      } else null,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      accounts.forEach { account ->
        DropdownMenuItem(
          text = {
            Column {
              Text(
                "${account.name} (${account.bankName})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Gray900
              )
              if (showBalance) {
                Text(
                  Formatters.formatCurrency(account.balance),
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray500
                )
              }
            }
          },
          onClick = {
            onSelect(account.id)
            expanded = false
          },
          contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
        )
      }
    }
  }
}

// ================================================================================================
// Budget
// ================================================================================================

/**
 * Sets the month's total budget and per-expense-category limits, prefilled from [budget]
 * (which may be inherited from an earlier month).
 */
@Composable
internal fun SetBudgetDialog(
  monthLabel: String,
  budget: MonthBudget?,
  onDismiss: () -> Unit,
  onSave: (total: Long, limits: Map<String, Long>) -> Unit
) {
  // Standard categories plus any older/custom category that already has a limit (so it isn't dropped).
  val categories = remember(budget) {
    val standard = FinanceCategories.categoriesFor(TransactionType.EXPENSE)
    standard + (budget?.categoryLimits?.keys.orEmpty() - standard.toSet()).sorted()
  }
  var totalText by rememberSaveable { mutableStateOf((budget?.total ?: 0L).toAmountInput()) }
  val limitTexts = rememberSaveable(
    saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })
  ) {
    categories.map { (budget?.categoryLimits?.get(it) ?: 0L).toAmountInput() }.toMutableStateList()
  }
  val total = totalText.toAmount()
  val limitSum = limitTexts.sumOf { it.toAmount() }

  FormDialog(
    title = "$monthLabel 예산",
    subtitle = "한 달 동안 쓸 금액과 카테고리별 한도를 정해요",
    confirmLabel = "저장",
    confirmEnabled = total > 0,
    onConfirm = {
      val limits = categories.zip(limitTexts.toList())
        .mapNotNull { (cat, text) -> text.toAmount().takeIf { it > 0 }?.let { cat to it } }
        .toMap()
      onSave(total, limits)
      onDismiss()
    },
    onDismiss = onDismiss,
    testTag = "set_budget_dialog"
  ) {
    budget?.inheritedFrom?.let {
      InfoText("지금은 ${it.monthValue}월 예산을 이어서 사용 중이에요. 저장하면 $monthLabel 예산으로 따로 저장돼요.")
    }
    KrwAmountField(
      value = totalText,
      onValueChange = { totalText = it },
      label = "월 전체 예산",
      isError = totalText.isNotEmpty() && total <= 0,
      modifier = Modifier.testTag("budget_total_input")
    )
    FieldLabel("카테고리별 한도 (선택)")
    categories.forEachIndexed { index, category ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        val color = Color(FinanceCategories.getColorForCategory(category))
        Box(
          Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            CategoryIconHelper.getIconForCategory(category),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(Modifier.width(10.dp))
        KrwAmountField(
          value = limitTexts[index],
          onValueChange = { limitTexts[index] = it },
          label = category,
          placeholder = "한도 없음",
          modifier = Modifier.weight(1f)
        )
      }
    }
    if (total > 0 && limitSum > total) {
      InfoText(
        "카테고리 한도 합계(${Formatters.formatCurrency(limitSum)})가 전체 예산보다 ${Formatters.formatCurrency(limitSum - total)} 많아요.",
        color = AccentOrange
      )
    } else if (limitSum > 0) {
      InfoText("카테고리 한도 합계 ${Formatters.formatCurrency(limitSum)}")
    }
  }
}

// ================================================================================================
// Recurring
// ================================================================================================

private val recurringPaymentMethods = listOf("계좌이체", "자동이체", "체크카드", "신용카드", "현금", "간편결제")

/** Adds ([initial] == null) or edits a recurring transaction. */
@Composable
internal fun RecurringDialog(
  initial: RecurringTransactionEntity?,
  accounts: List<AccountEntity>,
  onDismiss: () -> Unit,
  onSave: (RecurringTransactionEntity) -> Unit
) {
  val isEdit = initial != null
  var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
  var type by rememberSaveable { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
  var amountText by rememberSaveable { mutableStateOf((initial?.amount ?: 0L).toAmountInput()) }
  var category by rememberSaveable { mutableStateOf(initial?.category ?: "주거/통신") }
  var day by rememberSaveable { mutableStateOf(initial?.dayOfMonth ?: 10) }
  var accountId by rememberSaveable {
    mutableStateOf(initial?.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id)
  }
  var memo by rememberSaveable { mutableStateOf(initial?.memo ?: "") }
  var paymentMethod by rememberSaveable { mutableStateOf(initial?.paymentMethod ?: "계좌이체") }

  // Keep an item's existing category selectable even if it is not in the standard list.
  val categories = FinanceCategories.categoriesFor(type).let { standard ->
    val existing = initial?.category?.takeIf { initial.type == type && it !in standard }
    if (existing != null) standard + existing else standard
  }
  val methods = remember(paymentMethod) {
    if (paymentMethod in recurringPaymentMethods) recurringPaymentMethods else recurringPaymentMethods + paymentMethod
  }
  val amount = amountText.toAmount()
  val account = accounts.firstOrNull { it.id == accountId }
  val canSave = title.isNotBlank() && amount > 0 && day in 1..31 && category in categories && account != null

  FormDialog(
    title = if (isEdit) "고정 거래 수정" else "고정 거래 추가",
    subtitle = "매월 정한 날에 가계부에 자동으로 기록돼요",
    confirmLabel = if (isEdit) "저장" else "추가",
    confirmEnabled = canSave,
    onConfirm = {
      val acc = account ?: return@FormDialog
      val base = initial ?: RecurringTransactionEntity(
        title = "", type = type, amount = 0, category = category, dayOfMonth = day,
        accountId = acc.id, accountName = acc.name
      )
      onSave(
        base.copy(
          title = title.trim(),
          type = type,
          amount = amount,
          category = category,
          dayOfMonth = day,
          accountId = acc.id,
          accountName = acc.name,
          memo = memo.trim(),
          paymentMethod = paymentMethod
        )
      )
      onDismiss()
    },
    onDismiss = onDismiss,
    testTag = "recurring_dialog"
  ) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEachIndexed { index, t ->
        SegmentedButton(
          selected = type == t,
          onClick = {
            if (type != t) {
              type = t
              val options = FinanceCategories.categoriesFor(t)
              if (category !in options) category = options.first()
            }
          },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
          colors = SegmentedButtonDefaults.colors(
            activeContainerColor = TossBlueContainer,
            activeContentColor = TossBlue
          ),
          modifier = Modifier.heightIn(min = 48.dp)
        ) { Text(t.title) }
      }
    }

    OutlinedTextField(
      value = title,
      onValueChange = { title = it.take(40) },
      label = { Text("이름") },
      placeholder = { Text(if (type == TransactionType.EXPENSE) "예: 월세, 넷플릭스" else "예: 월급, 용돈") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("recurring_title_input")
    )

    KrwAmountField(value = amountText, onValueChange = { amountText = it }, modifier = Modifier.testTag("recurring_amount_input"))

    Column {
      FieldLabel("매월 기록일")
      Spacer(Modifier.height(6.dp))
      val listState = rememberLazyListState(initialFirstVisibleItemIndex = (day - 3).coerceAtLeast(0))
      LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items((1..31).toList(), key = { it }) { d ->
          val selected = d == day
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(if (selected) TossBlue else Gray100)
              .clickable(role = Role.RadioButton, onClickLabel = "${d}일 선택") { day = d }
              .semantics { this.selected = selected },
            contentAlignment = Alignment.Center
          ) {
            Text(
              "${d}일",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              color = if (selected) OnBrand else Gray900
            )
          }
        }
      }
      Spacer(Modifier.height(4.dp))
      InfoText("말일보다 큰 날짜는 그 달 말일에 기록돼요")
    }

    Column {
      FieldLabel("카테고리")
      Spacer(Modifier.height(4.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { cat ->
          FilterChip(
            selected = category == cat,
            onClick = { category = cat },
            label = { Text(cat, maxLines = 1) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = TossBlueContainer,
              selectedLabelColor = TossBlue
            )
          )
        }
      }
    }

    if (accounts.isEmpty()) {
      InfoText("등록된 계좌가 없어요. 자산 탭에서 계좌를 먼저 추가해 주세요.", color = AccentRed)
    } else {
      AccountDropdown(
        accounts = accounts,
        selectedId = accountId,
        onSelect = { accountId = it },
        label = if (type == TransactionType.EXPENSE) "출금 계좌" else "입금 계좌"
      )
    }

    Column {
      FieldLabel("결제 수단")
      Spacer(Modifier.height(4.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        methods.forEach { m ->
          FilterChip(
            selected = paymentMethod == m,
            onClick = { paymentMethod = m },
            label = { Text(m, maxLines = 1) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = TossBlueContainer,
              selectedLabelColor = TossBlue
            )
          )
        }
      }
    }

    OutlinedTextField(
      value = memo,
      onValueChange = { memo = it.take(100) },
      label = { Text("메모 (선택)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

// ================================================================================================
// Goals
// ================================================================================================

private val goalEmojis = listOf("🎯", "✈️", "🚗", "💻", "💍", "🏠", "🛡️", "💰", "🎓", "🎁")
private val dateLabelFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA)

/** Formats a goal's target date ("2027년 5월 1일"). */
internal fun formatGoalDate(millis: Long): String =
  Instant.ofEpochMilli(millis).atZone(com.moneyflow.domain.APP_ZONE).toLocalDate().format(dateLabelFormat)

private object FutureOrTodayDates : SelectableDates {
  override fun isSelectableDate(utcTimeMillis: Long): Boolean =
    utcTimeMillis / DAY_MILLIS >= LocalDate.now().toEpochDay()

  override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
}

/**
 * Creates ([initial] == null) or edits a saving goal. The initial saved amount can only be set
 * when creating; afterwards money is added with "저축하기".
 */
@Composable
internal fun GoalDialog(
  initial: SavingGoalEntity?,
  onDismiss: () -> Unit,
  onSave: (title: String, targetAmount: Long, initialAmount: Long, targetDate: Long, emoji: String) -> Unit
) {
  val isEdit = initial != null
  var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
  var emoji by rememberSaveable { mutableStateOf(initial?.iconEmoji ?: goalEmojis.first()) }
  var targetText by rememberSaveable { mutableStateOf((initial?.targetAmount ?: 0L).toAmountInput()) }
  var initialText by rememberSaveable { mutableStateOf("") }
  // Selected target date as epoch day (local date), null until chosen.
  var targetEpochDay by rememberSaveable {
    mutableStateOf(
      initial?.targetDate?.let { Instant.ofEpochMilli(it).atZone(com.moneyflow.domain.APP_ZONE).toLocalDate().toEpochDay() }
    )
  }
  var showDatePicker by rememberSaveable { mutableStateOf(false) }

  val target = targetText.toAmount()
  val canSave = title.isNotBlank() && target > 0 && targetEpochDay != null
  val emojiChoices = if (emoji in goalEmojis) goalEmojis else goalEmojis + emoji

  FormDialog(
    title = if (isEdit) "목표 수정" else "저축 목표 만들기",
    subtitle = null,
    confirmLabel = if (isEdit) "저장" else "목표 시작하기",
    confirmEnabled = canSave,
    onConfirm = {
      val epochDay = targetEpochDay ?: return@FormDialog
      val millis = LocalDate.ofEpochDay(epochDay).atStartOfDay(com.moneyflow.domain.APP_ZONE).toInstant().toEpochMilli()
      onSave(title.trim(), target, initialText.toAmount(), millis, emoji)
      onDismiss()
    },
    onDismiss = onDismiss,
    testTag = "goal_dialog"
  ) {
    Column {
      FieldLabel("아이콘")
      Spacer(Modifier.height(6.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        emojiChoices.forEach { e ->
          val selected = e == emoji
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(if (selected) TossBlueContainer else Gray100)
              .clickable(role = Role.RadioButton, onClickLabel = "아이콘 선택") { emoji = e }
              .semantics {
                this.selected = selected
                contentDescription = "아이콘 $e"
              },
            contentAlignment = Alignment.Center
          ) { Text(e, style = MaterialTheme.typography.titleMedium) }
        }
      }
    }

    OutlinedTextField(
      value = title,
      onValueChange = { title = it.take(30) },
      label = { Text("목표 이름") },
      placeholder = { Text("예: 여행 자금, 비상금") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("goal_title_input")
    )

    KrwAmountField(
      value = targetText,
      onValueChange = { targetText = it },
      label = "목표 금액",
      modifier = Modifier.testTag("goal_target_amount_input")
    )

    if (!isEdit) {
      KrwAmountField(
        value = initialText,
        onValueChange = { initialText = it },
        label = "지금까지 모은 금액 (선택)",
        modifier = Modifier.testTag("goal_initial_amount_input")
      )
    } else {
      InfoText("모은 금액은 목표 카드의 '저축하기'로 늘릴 수 있어요.")
    }

    Column {
      FieldLabel("목표 날짜")
      Spacer(Modifier.height(6.dp))
      OutlinedButton(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goal_date_button"),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TossBlue)
        Spacer(Modifier.width(8.dp))
        Text(
          text = targetEpochDay?.let { LocalDate.ofEpochDay(it).format(dateLabelFormat) } ?: "날짜 선택",
          color = if (targetEpochDay != null) Gray900 else Gray500,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }

  if (showDatePicker) {
    val pickerState = rememberDatePickerState(
      initialSelectedDateMillis = targetEpochDay?.let { it * DAY_MILLIS },
      selectableDates = FutureOrTodayDates
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { targetEpochDay = it / DAY_MILLIS }
            showDatePicker = false
          },
          enabled = pickerState.selectedDateMillis != null
        ) { Text("확인", fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } }
    ) {
      DatePicker(state = pickerState)
    }
  }
}

/** Moves money from one of the user's (non-debt) accounts into a goal. */
@Composable
internal fun GoalDepositDialog(
  goal: SavingGoalEntity,
  accounts: List<AccountEntity>,
  onDismiss: () -> Unit,
  onDeposit: (accountId: Long, amount: Long) -> Unit
) {
  val sources = remember(accounts) { accounts.filter { !it.isDebt } }
  var amountText by rememberSaveable { mutableStateOf("") }
  var accountId by rememberSaveable { mutableStateOf(sources.firstOrNull()?.id) }
  val amount = amountText.toAmount()
  val account = sources.firstOrNull { it.id == accountId }
  val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0)

  FormDialog(
    title = "${goal.iconEmoji} 저축하기",
    subtitle = goal.title,
    confirmLabel = "저축하기",
    confirmEnabled = amount > 0 && account != null && amount <= account.balance,
    onConfirm = {
      val acc = account ?: return@FormDialog
      if (amount > 0) {
        onDeposit(acc.id, amount)
        onDismiss()
      }
    },
    onDismiss = onDismiss,
    testTag = "goal_deposit_dialog"
  ) {
    if (remaining > 0) InfoText("목표까지 ${Formatters.formatCurrency(remaining)} 남았어요")
    KrwAmountField(
      value = amountText,
      onValueChange = { amountText = it },
      label = "저축할 금액",
      isError = account != null && amount > account.balance,
      supportingText = if (account != null && amount > account.balance) "계좌 잔액(${Formatters.formatCurrency(account.balance)})보다 많이 저축할 수 없어요" else null,
      modifier = Modifier.testTag("goal_deposit_amount_input")
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(10_000L, 50_000L, 100_000L).forEach { step ->
        FilterChip(
          selected = false,
          onClick = { amountText = (amount + step).coerceAtMost(9_999_999_999_999L).toString() },
          label = { Text("+${step / 10_000}만원") }
        )
      }
      if (remaining > 0) {
        FilterChip(
          selected = false,
          onClick = { amountText = remaining.toString() },
          label = { Text("남은 금액") }
        )
      }
    }
    if (sources.isEmpty()) {
      InfoText("저축할 돈을 꺼낼 계좌가 없어요. 자산 탭에서 계좌를 먼저 추가해 주세요.", color = AccentRed)
    } else {
      AccountDropdown(
        accounts = sources,
        selectedId = accountId,
        onSelect = { accountId = it },
        label = "출금 계좌",
        showBalance = true
      )
    }
  }
}
