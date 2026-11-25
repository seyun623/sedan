package com.moneyflow.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.data.model.CategoryBudgetProgress
import com.moneyflow.data.model.RecurringTransactionEntity
import com.moneyflow.data.model.SavingGoalEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.common.CategoryIconHelper
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.common.EmptyState
import com.moneyflow.ui.common.MonthSwitcher
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentGreenLight
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.AccentRedLight
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun BudgetScreen(
  onOpenTransaction: (Long) -> Unit,
  viewModel: BudgetViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  // Dialog state survives configuration changes; entities are looked up by id.
  var showBudgetDialog by rememberSaveable { mutableStateOf(false) }
  var showAddRecurring by rememberSaveable { mutableStateOf(false) }
  var editRecurringId by rememberSaveable { mutableStateOf<Long?>(null) }
  var deleteRecurringId by rememberSaveable { mutableStateOf<Long?>(null) }
  var showAddGoal by rememberSaveable { mutableStateOf(false) }
  var editGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
  var deleteGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
  var depositGoalId by rememberSaveable { mutableStateOf<Long?>(null) }

  BudgetContent(
    state = state,
    onPrevMonth = viewModel::previousMonth,
    onNextMonth = viewModel::nextMonth,
    onCurrentMonth = viewModel::goToCurrentMonth,
    onEditBudget = { showBudgetDialog = true },
    onAddRecurring = { showAddRecurring = true },
    onToggleRecurring = { item, active -> viewModel.setRecurringActive(item, active) },
    onExecuteRecurring = { viewModel.executeRecurringNow(it) },
    onEditRecurring = { editRecurringId = it.id },
    onDeleteRecurring = { deleteRecurringId = it.id },
    onAcceptSuggestion = viewModel::acceptSuggestion,
    onDismissSuggestion = viewModel::dismissSuggestion,
    onAddGoal = { showAddGoal = true },
    onDepositGoal = { depositGoalId = it.id },
    onEditGoal = { editGoalId = it.id },
    onDeleteGoal = { deleteGoalId = it.id }
  )

  // ---- Dialogs ----
  if (showBudgetDialog && !state.isLoading) {
    SetBudgetDialog(
      monthLabel = state.monthLabel,
      budget = state.budget,
      onDismiss = { showBudgetDialog = false },
      onSave = { total, limits -> viewModel.saveBudget(total, limits) }
    )
  }

  if (showAddRecurring) {
    RecurringDialog(
      initial = null,
      accounts = state.accounts,
      onDismiss = { showAddRecurring = false },
      onSave = { viewModel.addRecurring(it) }
    )
  }
  editRecurringId?.let { id ->
    val item = state.recurring.firstOrNull { it.id == id }
    if (item != null) {
      RecurringDialog(
        initial = item,
        accounts = state.accounts,
        onDismiss = { editRecurringId = null },
        onSave = { viewModel.updateRecurring(it) }
      )
    }
  }
  deleteRecurringId?.let { id ->
    val item = state.recurring.firstOrNull { it.id == id }
    if (item != null) {
      ConfirmDialog(
        title = "고정 거래 삭제",
        message = "'${item.title}'을(를) 삭제할까요? 이미 가계부에 기록된 거래는 그대로 남아 있어요.",
        confirmLabel = "삭제",
        onConfirm = { viewModel.deleteRecurring(item) },
        onDismiss = { deleteRecurringId = null }
      )
    }
  }

  if (showAddGoal) {
    GoalDialog(
      initial = null,
      onDismiss = { showAddGoal = false },
      onSave = { title, target, initialAmount, date, emoji ->
        viewModel.addGoal(
          SavingGoalEntity(
            title = title,
            targetAmount = target,
            currentAmount = initialAmount,
            targetDate = date,
            iconEmoji = emoji
          )
        )
      }
    )
  }
  editGoalId?.let { id ->
    val goal = state.goals.firstOrNull { it.id == id }
    if (goal != null) {
      GoalDialog(
        initial = goal,
        onDismiss = { editGoalId = null },
        onSave = { title, target, _, date, emoji ->
          viewModel.updateGoal(goal.copy(title = title, targetAmount = target, targetDate = date, iconEmoji = emoji))
        }
      )
    }
  }
  deleteGoalId?.let { id ->
    val goal = state.goals.firstOrNull { it.id == id }
    if (goal != null) {
      ConfirmDialog(
        title = "목표 삭제",
        message = "'${goal.title}' 목표를 삭제할까요? 저축 이체 내역은 가계부에 남아 있어요.",
        confirmLabel = "삭제",
        onConfirm = { viewModel.deleteGoal(goal) },
        onDismiss = { deleteGoalId = null }
      )
    }
  }
  depositGoalId?.let { id ->
    val goal = state.goals.firstOrNull { it.id == id }
    if (goal != null) {
      GoalDepositDialog(
        goal = goal,
        accounts = state.accounts,
        onDismiss = { depositGoalId = null },
        onDeposit = { accountId, amount -> viewModel.depositToGoal(goal, accountId, amount) }
      )
    }
  }
}

@Composable
private fun BudgetContent(
  state: BudgetUiState,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onCurrentMonth: () -> Unit,
  onEditBudget: () -> Unit,
  onAddRecurring: () -> Unit,
  onToggleRecurring: (RecurringTransactionEntity, Boolean) -> Unit,
  onExecuteRecurring: (RecurringTransactionEntity) -> Unit,
  onEditRecurring: (RecurringTransactionEntity) -> Unit,
  onDeleteRecurring: (RecurringTransactionEntity) -> Unit,
  onAcceptSuggestion: (com.moneyflow.domain.RecurringSuggestion) -> Unit = {},
  onDismissSuggestion: (com.moneyflow.domain.RecurringSuggestion) -> Unit = {},
  onAddGoal: () -> Unit,
  onDepositGoal: (SavingGoalEntity) -> Unit,
  onEditGoal: (SavingGoalEntity) -> Unit,
  onDeleteGoal: (SavingGoalEntity) -> Unit
) {
  val categoryItems = state.categoryProgress.filter { it.budgetLimit > 0 || it.spentAmount > 0 }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("budget_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item(key = "header") {
      Column {
        Text(
          text = "예산 관리",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Black,
          color = Gray900
        )
        MonthSwitcher(
          label = state.monthLabel,
          onPrevious = onPrevMonth,
          onNext = onNextMonth,
          showToday = !state.isCurrentMonth,
          onToday = onCurrentMonth
        )
      }
    }

    if (state.isLoading) {
      item(key = "loading") {
        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = TossBlue)
        }
      }
      return@LazyColumn
    }

    item(key = "budget_card") { BudgetCard(state, onEditBudget) }

    // ---- Category limits ----
    item(key = "category_header") {
      SectionHeader(
        title = "카테고리별 예산",
        subtitle = "한도를 정한 카테고리와 지출한 카테고리를 보여드려요",
        actionLabel = "한도 설정",
        actionIcon = Icons.Default.Edit,
        onAction = onEditBudget
      )
    }
    if (categoryItems.isEmpty()) {
      item(key = "category_empty") {
        SectionCard {
          EmptyState(
            icon = Icons.Default.PieChart,
            title = "카테고리 한도가 없어요",
            description = "식비·쇼핑처럼 카테고리별로 한도를 정하면 진행률을 볼 수 있어요.",
            actionLabel = "한도 설정하기",
            onAction = onEditBudget
          )
        }
      }
    } else {
      items(categoryItems, key = { "cat_${it.category}" }) { item -> CategoryProgressCard(item) }
    }

    // ---- No-spend challenge ----
    item(key = "no_spend") { NoSpendCard(state) }

    // ---- Recurring ----
    item(key = "recurring_header") {
      SectionHeader(
        title = "고정 거래",
        subtitle = "월세·구독료·월급처럼 매달 반복되는 거래",
        actionLabel = "추가",
        actionIcon = Icons.Default.Add,
        onAction = onAddRecurring,
        actionTestTag = "open_add_recurring_btn"
      )
    }
    if (state.recurring.isEmpty()) {
      item(key = "recurring_empty") {
        SectionCard {
          EmptyState(
            icon = Icons.Default.Repeat,
            title = "등록된 고정 거래가 없어요",
            description = "등록해 두면 매달 정한 날에 가계부에 자동으로 기록돼요.",
            actionLabel = "고정 거래 추가",
            onAction = onAddRecurring
          )
        }
      }
    } else {
      items(state.recurring, key = { "rec_${it.id}" }) { item ->
        RecurringCard(
          item = item,
          onToggle = { onToggleRecurring(item, it) },
          onExecute = { onExecuteRecurring(item) },
          onEdit = { onEditRecurring(item) },
          onDelete = { onDeleteRecurring(item) }
        )
      }
    }

    if (state.recurringSuggestions.isNotEmpty()) {
      items(state.recurringSuggestions.take(3), key = { "rec_sug_${it.key}" }) { sug ->
        RecurringSuggestionCard(sug, onAccept = { onAcceptSuggestion(sug) }, onDismiss = { onDismissSuggestion(sug) })
      }
    }

    // ---- Goals ----
    item(key = "goal_header") {
      SectionHeader(
        title = "저축 목표",
        subtitle = "모으고 싶은 돈을 목표로 정해 보세요",
        actionLabel = "목표 추가",
        actionIcon = Icons.Default.Add,
        onAction = onAddGoal,
        actionTestTag = "open_add_goal_btn"
      )
    }
    if (state.goals.isEmpty()) {
      item(key = "goal_empty") {
        SectionCard {
          EmptyState(
            icon = Icons.Default.Savings,
            title = "아직 저축 목표가 없어요",
            description = "여행, 비상금처럼 목표를 만들고 조금씩 모아 보세요.",
            actionLabel = "목표 만들기",
            onAction = onAddGoal
          )
        }
      }
    } else {
      items(state.goals, key = { "goal_${it.id}" }) { goal ->
        GoalCard(
          goal = goal,
          onDeposit = { onDepositGoal(goal) },
          onEdit = { onEditGoal(goal) },
          onDelete = { onDeleteGoal(goal) }
        )
      }
    }
  }
}

// ================================================================================================
// Budget card
// ================================================================================================

@Composable
private fun BudgetCard(state: BudgetUiState, onEdit: () -> Unit) {
  val budget = state.budget
  SectionCard(modifier = Modifier.testTag("total_budget_card")) {
    if (budget == null || !budget.isSet) {
      Text("${state.monthLabel} 예산", style = MaterialTheme.typography.titleMedium, color = Gray600)
      Spacer(Modifier.height(6.dp))
      Text(
        "아직 예산을 정하지 않았어요",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Gray900
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "한 달 예산을 정하면 남은 금액과 하루에 쓸 수 있는 돈을 알려드려요. " +
          "지금까지 지출 ${Formatters.formatCurrency(state.budgetExpense)}",
        style = MaterialTheme.typography.bodySmall,
        color = Gray600
      )
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("open_set_budget_dialog_btn"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
      ) { Text("예산 설정하기", fontWeight = FontWeight.Bold) }
      return@SectionCard
    }

    val ratio = state.budgetSpentRatio
    val over = state.remainingBudget < 0
    val statusColor = when {
      over -> AccentRed
      ratio > 0.8f -> AccentOrange
      else -> AccentGreen
    }
    val usedPct = if (budget.total > 0) (state.budgetExpense * 100 / budget.total) else 0L

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "${state.monthLabel} 남은 예산",
        style = MaterialTheme.typography.titleMedium,
        color = Gray600,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Box(
        Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(statusColor.copy(alpha = 0.12f))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text("${usedPct}% 사용", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusColor)
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = if (over) "${Formatters.formatCurrency(-state.remainingBudget)} 초과" else Formatters.formatCurrency(state.remainingBudget),
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Black,
      color = if (over) AccentRed else Gray900,
      maxLines = 1
    )
    Spacer(Modifier.height(14.dp))
    ProgressBar(
      ratio = ratio,
      color = if (over) AccentRed else TossBlue,
      height = 10,
      description = "예산의 ${usedPct}% 사용"
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
      Text(
        "예산 ${Formatters.formatCurrency(budget.total)}",
        style = MaterialTheme.typography.bodySmall,
        color = Gray600,
        modifier = Modifier.weight(1f),
        maxLines = 1
      )
      Text(
        "지출 ${Formatters.formatCurrency(state.budgetExpense)}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = Gray900,
        maxLines = 1
      )
    }
    budget.inheritedFrom?.let { from ->
      Spacer(Modifier.height(8.dp))
      val label = if (from.year == state.month.year) "${from.monthValue}월" else "${from.year}년 ${from.monthValue}월"
      Text(
        "$label 예산을 이어서 사용 중",
        style = MaterialTheme.typography.labelSmall,
        color = Gray500
      )
    }

    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      val allowance = state.dailyAllowance
      if (allowance != null) {
        Row(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(TossBlueContainer)
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Savings, contentDescription = null, tint = com.moneyflow.ui.theme.TossBlueText, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Column {
            Text(
              "오늘부터 하루 ${Formatters.formatCurrency(allowance)} 쓸 수 있어요",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Bold,
              color = com.moneyflow.ui.theme.TossBlueText
            )
            Text("남은 ${state.daysLeft}일 기준", style = MaterialTheme.typography.labelSmall, color = Gray600)
          }
        }
        Spacer(Modifier.width(8.dp))
      } else {
        Spacer(Modifier.weight(1f))
      }
      TextButton(onClick = onEdit, modifier = Modifier.testTag("open_set_budget_dialog_btn")) {
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = com.moneyflow.ui.theme.TossBlueText)
        Spacer(Modifier.width(4.dp))
        Text("예산 수정", color = com.moneyflow.ui.theme.TossBlueText, fontWeight = FontWeight.Bold)
      }
    }
  }
}

// ================================================================================================
// Category progress
// ================================================================================================

@Composable
private fun CategoryProgressCard(item: CategoryBudgetProgress) {
  val hasLimit = item.budgetLimit > 0
  val over = item.isOverBudget
  val catColor = Color(item.colorHex)
  val barColor = when {
    over -> AccentRed
    item.percentage > 0.8f -> AccentOrange
    else -> catColor
  }
  val overBorder = AccentRed.copy(alpha = 0.6f)
  SectionCard(
    modifier = Modifier
      .then(if (over) Modifier.border(1.2.dp, overBorder, RoundedCornerShape(20.dp)) else Modifier)
      .testTag("cat_progress_card_${item.category}"),
    padding = 16,
    background = if (over) AccentRedLight else CardSurface
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(36.dp).clip(CircleShape).background(catColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(CategoryIconHelper.getIconForCategory(item.category), contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          item.category,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = Gray900,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          if (hasLimit) "한도 ${Formatters.formatCurrency(item.budgetLimit)}" else "한도 없음",
          style = MaterialTheme.typography.labelSmall,
          color = Gray600,
          maxLines = 1
        )
      }
      when {
        over -> Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Warning, contentDescription = "예산 초과", tint = AccentRed, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(3.dp))
          Text(
            "${Formatters.formatCurrency(-item.remainingAmount)} 초과",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = AccentRed,
            maxLines = 1
          )
        }
        hasLimit -> Text(
          "${Formatters.formatCurrency(item.remainingAmount)} 남음",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = if (item.percentage > 0.8f) AccentOrange else AccentGreen,
          maxLines = 1
        )
        else -> Text(
          Formatters.formatCurrency(item.spentAmount),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = Gray900,
          maxLines = 1
        )
      }
    }
    if (hasLimit) {
      Spacer(Modifier.height(12.dp))
      ProgressBar(
        ratio = item.percentage,
        color = barColor,
        height = 8,
        description = "${item.category} 한도의 ${(item.percentage * 100).toInt()}% 사용"
      )
      Spacer(Modifier.height(6.dp))
      Text(
        "지출 ${Formatters.formatCurrency(item.spentAmount)} (${(item.percentage * 100).toInt()}%)",
        style = MaterialTheme.typography.bodySmall,
        color = Gray600
      )
    }
  }
}

// ================================================================================================
// No-spend challenge
// ================================================================================================

@Composable
private fun NoSpendCard(state: BudgetUiState) {
  val notStarted = state.month.isAfter(YearMonth.now())
  val days = state.elapsedDays.coerceAtLeast(0)
  val ratio = if (days > 0) state.noSpendDays.toFloat() / days else 0f
  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(36.dp).clip(CircleShape).background(AccentGreenLight),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
      }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text("무지출 챌린지", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gray900)
        Text("지출 없이 보낸 날을 세어 봐요", style = MaterialTheme.typography.bodySmall, color = Gray600)
      }
      if (!notStarted) {
        Text(
          "${state.noSpendDays}일",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Black,
          color = AccentGreen
        )
      }
    }
    Spacer(Modifier.height(14.dp))
    if (notStarted) {
      Text("아직 시작되지 않은 달이에요", style = MaterialTheme.typography.bodySmall, color = Gray600)
    } else {
      ProgressBar(
        ratio = ratio,
        color = AccentGreen,
        height = 10,
        description = "${days}일 중 ${state.noSpendDays}일 무지출"
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = (if (state.isCurrentMonth) "지금까지 " else "") + "${days}일 중 ${state.noSpendDays}일 무지출" +
          if (state.noSpendDays > 0 && state.isCurrentMonth) " 🎉" else "",
        style = MaterialTheme.typography.bodySmall,
        color = Gray600
      )
    }
  }
}

// ================================================================================================
// Recurring
// ================================================================================================

@Composable
private fun RecurringCard(
  item: RecurringTransactionEntity,
  onToggle: (Boolean) -> Unit,
  onExecute: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val isIncome = item.type == TransactionType.INCOME
  val amountColor = if (isIncome) AccentGreen else AccentRed
  SectionCard(modifier = Modifier.testTag("recurring_card_${item.id}"), padding = 16) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f).alpha(if (item.isActive) 1f else 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(amountColor.copy(alpha = 0.12f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              "매월 ${item.dayOfMonth}일 ${item.type.title}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = amountColor,
              maxLines = 1
            )
          }
          Spacer(Modifier.width(6.dp))
          Text(
            item.category,
            style = MaterialTheme.typography.labelSmall,
            color = Gray500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          item.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = Gray900,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          "${item.accountName} · ${item.paymentMethod}" + if (item.memo.isNotBlank()) " · ${item.memo}" else "",
          style = MaterialTheme.typography.labelSmall,
          color = Gray600,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
          (if (isIncome) "+" else "-") + Formatters.formatCurrency(item.amount),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = amountColor,
          maxLines = 1
        )
      }
      Spacer(Modifier.width(8.dp))
      Switch(
        checked = item.isActive,
        onCheckedChange = onToggle,
        colors = SwitchDefaults.colors(checkedThumbColor = OnBrand, checkedTrackColor = TossBlue),
        modifier = Modifier.semantics {
          contentDescription = "${item.title} 자동 기록"
          stateDescription = if (item.isActive) "사용 중" else "일시 중지됨"
        }
      )
    }
    if (!item.isActive) {
      Text("일시 중지됨 · 자동으로 기록되지 않아요", style = MaterialTheme.typography.labelSmall, color = Gray500)
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onExecute, modifier = Modifier.testTag("execute_recurring_now_${item.id}")) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = com.moneyflow.ui.theme.TossBlueText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("지금 기록", color = com.moneyflow.ui.theme.TossBlueText, fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.weight(1f))
      IconButton(onClick = onEdit) {
        Icon(Icons.Default.Edit, contentDescription = "${item.title} 수정", tint = Gray600)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.Delete, contentDescription = "${item.title} 삭제", tint = Gray600)
      }
    }
  }
}

// ================================================================================================
// Goals
// ================================================================================================

@Composable
private fun GoalCard(
  goal: SavingGoalEntity,
  onDeposit: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val ratio = if (goal.targetAmount > 0) (goal.currentAmount.toFloat() / goal.targetAmount).coerceIn(0f, 1f) else 0f
  val achieved = goal.targetAmount > 0 && goal.currentAmount >= goal.targetAmount
  val goalColor = Color(goal.colorHex)
  val today = LocalDate.now()
  val targetDay = Instant.ofEpochMilli(goal.targetDate).atZone(com.moneyflow.domain.APP_ZONE).toLocalDate()
  val dDays = ChronoUnit.DAYS.between(today, targetDay)
  val dLabel = when {
    dDays > 0 -> "D-$dDays"
    dDays == 0L -> "D-Day"
    else -> "기한 지남"
  }

  SectionCard(modifier = Modifier.testTag("goal_card_${goal.id}"), padding = 16) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(44.dp).clip(CircleShape).background(Gray100),
        contentAlignment = Alignment.Center
      ) { Text(goal.iconEmoji, style = MaterialTheme.typography.titleLarge) }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
          goal.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = Gray900,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          "${formatGoalDate(goal.targetDate)}까지 · $dLabel",
          style = MaterialTheme.typography.labelSmall,
          color = if (dDays < 0 && !achieved) AccentRed else Gray600,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Default.Edit, contentDescription = "${goal.title} 수정", tint = Gray600)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.Delete, contentDescription = "${goal.title} 삭제", tint = Gray600)
      }
    }
    Spacer(Modifier.height(12.dp))
    ProgressBar(
      ratio = ratio,
      color = if (achieved) AccentGreen else goalColor,
      height = 8,
      description = "목표의 ${(ratio * 100).toInt()}% 달성"
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (achieved) "목표 달성! 🎉" else "${(ratio * 100).toInt()}% 달성",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (achieved) AccentGreen else goalColor
      )
      Spacer(Modifier.weight(1f))
      Text(
        "${Formatters.formatCurrency(goal.currentAmount)} / ${Formatters.formatCurrency(goal.targetAmount)}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = Gray900,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Spacer(Modifier.height(12.dp))
    Button(
      onClick = onDeposit,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goal_deposit_btn_${goal.id}"),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(containerColor = TossBlueContainer, contentColor = TossBlue)
    ) { Text("저축하기", fontWeight = FontWeight.Bold) }
  }
}

// ================================================================================================
// Shared pieces
// ================================================================================================

@Composable
private fun SectionHeader(
  title: String,
  subtitle: String?,
  actionLabel: String,
  actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
  onAction: () -> Unit,
  actionTestTag: String? = null
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gray900)
      if (subtitle != null) {
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = Gray600,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
    TextButton(
      onClick = onAction,
      modifier = if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier
    ) {
      Icon(actionIcon, contentDescription = null, tint = com.moneyflow.ui.theme.TossBlueText, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(4.dp))
      Text(actionLabel, color = com.moneyflow.ui.theme.TossBlueText, fontWeight = FontWeight.Bold, maxLines = 1)
    }
  }
}

@Composable
private fun ProgressBar(ratio: Float, color: Color, height: Int, description: String) {
  val track = Gray200
  Box(
    Modifier
      .fillMaxWidth()
      .height(height.dp)
      .clip(RoundedCornerShape((height / 2).dp))
      .background(track)
      .semantics { contentDescription = description }
  ) {
    val r = ratio.coerceIn(0f, 1f)
    if (r > 0f) {
      Box(
        Modifier
          .fillMaxWidth(r)
          .fillMaxHeight()
          .clip(RoundedCornerShape((height / 2).dp))
          .background(color)
      )
    }
  }
}

@Composable
private fun SectionCard(
  modifier: Modifier = Modifier,
  padding: Int = 20,
  background: Color = CardSurface,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(background)
      .padding(padding.dp),
    content = content
  )
}

/** "매달 반복되는 것 같아요": a hand-typed payment seen every month, offered as a 고정 거래. */
@Composable
private fun RecurringSuggestionCard(
  s: com.moneyflow.domain.RecurringSuggestion,
  onAccept: () -> Unit,
  onDismiss: () -> Unit
) {
  SectionCard(background = TossBlueContainer, modifier = Modifier.testTag("recurring_suggestion")) {
    Text("매달 반복되는 것 같아요", style = MaterialTheme.typography.labelMedium, color = com.moneyflow.ui.theme.TossBlueText, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(s.title, fontWeight = FontWeight.Bold, color = Gray900)
    Text(
      "${s.months}개월째 · 매달 ${s.dayOfMonth}일쯤 · ${if (s.type == com.moneyflow.data.model.TransactionType.INCOME) "+" else ""}${Formatters.formatCurrency(s.amount)} · ${s.category}",
      style = MaterialTheme.typography.bodySmall, color = Gray600
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
      TextButton(onClick = onDismiss) { Text("아니에요", color = Gray600) }
      TextButton(onClick = onAccept) { Text("고정 거래로 등록", fontWeight = FontWeight.Bold, color = com.moneyflow.ui.theme.TossBlueText) }
    }
  }
}
