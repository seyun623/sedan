package com.moneyflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.CategoryRuleEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.RuleAction
import com.moneyflow.data.model.TransactionType
import com.moneyflow.data.repository.LearnedCategory
import com.moneyflow.data.repository.RuleRepository
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray600
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
  private val repo: RuleRepository,
  private val messages: UserMessageBus
) : ViewModel() {
  val rules: StateFlow<List<CategoryRuleEntity>> = repo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  val learned: StateFlow<List<LearnedCategory>> = repo.learned.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun save(rule: CategoryRuleEntity) = viewModelScope.launch {
    runCatching { repo.save(rule) }
      .onSuccess { messages.show(if (rule.action == RuleAction.IGNORE) "'${rule.keyword.trim()}'이(가) 들어간 알림은 기록하지 않아요." else "'${rule.keyword.trim()}' → ${rule.category} 규칙을 저장했어요.") }
      .onFailure { messages.show(it.message ?: "저장하지 못했어요.") }
  }

  fun delete(rule: CategoryRuleEntity) = viewModelScope.launch {
    repo.delete(rule)
    messages.show("'${rule.keyword}' 규칙을 지웠어요.")
  }
}

/** Settings → 자동 분류 규칙: keyword → category / ignore, and what the app learned. */
@Composable
fun RulesScreen(onBack: () -> Unit, viewModel: RulesViewModel = hiltViewModel()) {
  val rules by viewModel.rules.collectAsStateWithLifecycle()
  val learned by viewModel.learned.collectAsStateWithLifecycle()
  /** Rule being edited: a new one has id 0. */
  var editing by rememberSaveable(stateSaver = RuleSaver) { mutableStateOf<CategoryRuleEntity?>(null) }

  SettingsScaffold(title = "자동 분류 규칙", onBack = onBack) {
    SettingsSection("내 규칙") {
      if (rules.isEmpty()) {
        Text(
          "알림·문자·카드사 내역에 이 단어가 있으면 정한 카테고리로 기록하거나, 아예 기록하지 않도록 할 수 있어요.",
          style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(16.dp)
        )
      }
      rules.forEach { r ->
        SettingsItem(
          title = r.keyword,
          subtitle = if (r.action == RuleAction.IGNORE) "자동 기록 안 함" else "→ ${r.category}",
          icon = if (r.action == RuleAction.IGNORE) Icons.Filled.Block else Icons.Filled.Label,
          iconTint = if (r.action == RuleAction.IGNORE) AccentRed else com.moneyflow.ui.theme.TossBlue,
          onClick = { editing = r }
        )
      }
      SettingsItem(
        title = "규칙 추가",
        icon = Icons.Filled.Label,
        showChevron = false,
        onClick = { editing = CategoryRuleEntity(keyword = "", category = FinanceCategories.categoriesFor(TransactionType.EXPENSE).first()) },
        modifier = Modifier.testTag("add_rule")
      )
    }

    SettingsSection("앱이 기억한 분류") {
      Text(
        "자동으로 기록된 곳이에요. 거래의 카테고리를 고치면 다음부터 그 카테고리로 기록해요. 누르면 규칙으로 고정할 수 있어요.",
        style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )
      if (learned.isEmpty()) {
        Text("아직 자동으로 기록된 곳이 없어요.", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(16.dp))
      }
      learned.forEach { l ->
        SettingsItem(
          title = l.merchant,
          subtitle = "${l.category} · ${l.count}건",
          icon = Icons.Filled.History,
          onClick = { editing = CategoryRuleEntity(keyword = l.merchant, category = l.category) }
        )
      }
    }
  }

  editing?.let { rule ->
    RuleDialog(
      initial = rule,
      onDismiss = { editing = null },
      onSave = { viewModel.save(it); editing = null },
      onDelete = if (rule.id != 0L) ({ viewModel.delete(rule); editing = null }) else null
    )
  }
}

private val RuleSaver = androidx.compose.runtime.saveable.Saver<CategoryRuleEntity?, List<String>>(
  save = { r -> r?.let { listOf(it.id.toString(), it.keyword, it.action.name, it.category) } ?: emptyList() },
  restore = { l -> if (l.size == 4) CategoryRuleEntity(l[0].toLong(), l[1], RuleAction.valueOf(l[2]), l[3]) else null }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDialog(
  initial: CategoryRuleEntity,
  onDismiss: () -> Unit,
  onSave: (CategoryRuleEntity) -> Unit,
  onDelete: (() -> Unit)?
) {
  var keyword by rememberSaveable { mutableStateOf(initial.keyword) }
  var action by rememberSaveable { mutableStateOf(initial.action) }
  var category by rememberSaveable { mutableStateOf(initial.category) }
  val categories = FinanceCategories.categoriesFor(TransactionType.EXPENSE) + FinanceCategories.categoriesFor(TransactionType.INCOME)
  val valid = keyword.trim().length >= 2 && (action == RuleAction.IGNORE || category in categories)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial.id == 0L) "규칙 추가" else "규칙 수정", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = keyword, onValueChange = { keyword = it.take(30) },
          label = { Text("이 단어가 들어 있으면 (예: 스타벅스)") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rule_keyword")
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          RuleAction.entries.forEachIndexed { i, a ->
            SegmentedButton(selected = action == a, onClick = { action = a }, shape = SegmentedButtonDefaults.itemShape(i, RuleAction.entries.size)) { Text(a.label) }
          }
        }
        if (action == RuleAction.CATEGORY) {
          Text("지출", fontWeight = FontWeight.SemiBold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FinanceCategories.categoriesFor(TransactionType.EXPENSE).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
          }
          Text("수입", fontWeight = FontWeight.SemiBold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FinanceCategories.categoriesFor(TransactionType.INCOME).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
          }
        } else {
          Text("이 단어가 들어간 결제 알림·문자는 가계부에 기록하지 않아요. (포인트 적립, 광고 문자 등)", style = MaterialTheme.typography.bodySmall, color = Gray600)
        }
        if (onDelete != null) TextButton(onClick = onDelete) { Text("규칙 삭제", color = AccentRed) }
      }
    },
    confirmButton = {
      TextButton(enabled = valid, onClick = { onSave(initial.copy(keyword = keyword.trim(), action = action, category = category)) }) {
        Text("저장", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
}
