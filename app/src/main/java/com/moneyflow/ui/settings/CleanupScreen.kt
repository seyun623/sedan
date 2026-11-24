package com.moneyflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.CategoryRuleEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.data.repository.RuleRepository
import com.moneyflow.data.repository.SettingsRepository
import com.moneyflow.domain.cleanup.DuplicateGroup
import com.moneyflow.domain.cleanup.OtherGroup
import com.moneyflow.domain.cleanup.TransactionCleanup
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.common.UserMessageBus
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanupUi(val duplicates: List<DuplicateGroup> = emptyList(), val others: List<OtherGroup> = emptyList(), val loaded: Boolean = false)

@HiltViewModel
class CleanupViewModel @Inject constructor(
  private val ledger: LedgerRepository,
  private val rules: RuleRepository,
  private val settings: SettingsRepository,
  private val messages: UserMessageBus
) : ViewModel() {
  val ui: StateFlow<CleanupUi> = combine(ledger.transactions, settings.settings.map { it.cleanupDismissed }.distinctUntilChanged()) { txs, dismissed ->
    CleanupUi(TransactionCleanup.duplicates(txs, dismissed), TransactionCleanup.others(txs), loaded = true)
  }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanupUi())

  /** Keeps the first (oldest) record and deletes the rest; balances are corrected. */
  fun keepOne(group: DuplicateGroup) = viewModelScope.launch {
    ledger.deleteTransactions(group.items.drop(1))
    messages.show("중복 ${group.items.size - 1}건을 지웠어요.")
  }

  fun notDuplicate(group: DuplicateGroup) = viewModelScope.launch { settings.dismissDuplicate(group.key) }

  fun classify(group: OtherGroup, category: String, addRule: Boolean) = viewModelScope.launch {
    ledger.recategorize(group.items.map { it.id }, category)
    val keyword = group.merchant
    if (addRule && keyword.length >= 2 && keyword != "(메모 없음)") runCatching { rules.save(CategoryRuleEntity(keyword = keyword, category = category)) }
    messages.show("'${group.merchant}' ${group.items.size}건을 $category(으)로 바꿨어요." + if (addRule) " 다음부터 자동으로 분류해요." else "")
  }
}

/** Settings → 거래 정리: likely duplicates and '기타' transactions to classify at once. */
@Composable
fun CleanupScreen(onBack: () -> Unit, viewModel: CleanupViewModel = hiltViewModel()) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  var deleting by remember { mutableStateOf<DuplicateGroup?>(null) }
  var classifying by remember { mutableStateOf<OtherGroup?>(null) }

  SettingsScaffold(title = "거래 정리", onBack = onBack) {
    SettingsSection("중복 의심 (${ui.duplicates.size})") {
      if (ui.loaded && ui.duplicates.isEmpty()) {
        Text("중복으로 보이는 거래가 없어요.", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(16.dp))
      }
      ui.duplicates.take(50).forEach { g ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("cleanup_duplicate")) {
          Text("${Formatters.formatCurrency(g.amount)} × ${g.items.size}건 · ${g.items.first().accountName}", fontWeight = FontWeight.SemiBold, color = Gray900)
          g.items.forEach { t ->
            Text("• ${Formatters.formatDateTime(t.timestamp)}  ${t.memo.ifBlank { t.category }}  (${t.source.label})",
              style = MaterialTheme.typography.bodySmall, color = Gray600)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { deleting = g }) { Text("하나만 남기기", color = AccentRed) }
            TextButton(onClick = { viewModel.notDuplicate(g) }) { Text("중복 아님") }
          }
        }
      }
    }
    SettingsSection("'기타'로 분류된 거래 (${ui.others.sumOf { it.items.size }}건)") {
      if (ui.loaded && ui.others.isEmpty()) {
        Text("'기타'로 남은 거래가 없어요.", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(16.dp))
      }
      ui.others.take(60).forEach { g ->
        SettingsItem(
          title = "${g.merchant} · ${g.items.size}건",
          subtitle = Formatters.formatCurrency(g.total) + (g.suggestion?.let { " · 추천: $it" } ?: ""),
          icon = Icons.Filled.CleaningServices,
          onClick = { classifying = g },
          modifier = Modifier.testTag("cleanup_other")
        )
      }
    }
    SettingsSection("알아 두세요") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(
          "같은 계좌·같은 금액이 10분 안에 두 번, 또는 하루 안에 같은 가맹점으로 두 번 기록되면 중복으로 의심해요.",
          "'하나만 남기기'는 가장 먼저 기록된 것만 남기고 나머지를 지워요. 계좌 잔액도 함께 바로잡아요.",
          "할부·고정 거래처럼 원래 여러 번인 거래는 중복으로 보지 않아요."
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(vertical = 3.dp)) }
      }
    }
  }

  deleting?.let { g ->
    ConfirmDialog(
      title = "중복 ${g.items.size - 1}건을 지울까요?",
      message = "가장 먼저 기록된 ${Formatters.formatDateTime(g.items.first().timestamp)} 거래만 남기고 지워요.",
      confirmLabel = "지우기",
      onConfirm = { deleting = null; viewModel.keepOne(g) },
      onDismiss = { deleting = null }
    )
  }
  classifying?.let { g -> ClassifyDialog(g, onDismiss = { classifying = null }, onSave = { c, rule -> classifying = null; viewModel.classify(g, c, rule) }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassifyDialog(group: OtherGroup, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
  var category by remember { mutableStateOf(group.suggestion) }
  var addRule by remember { mutableStateOf(group.merchant.length >= 2 && group.merchant != "(메모 없음)") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${group.merchant} ${group.items.size}건", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("한꺼번에 바꿀 카테고리를 골라 주세요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FinanceCategories.categoriesFor(group.type).filter { it != FinanceCategories.OTHER_EXPENSE && it != FinanceCategories.OTHER_INCOME }.forEach { c ->
            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
          }
        }
        if (group.merchant.length >= 2 && group.merchant != "(메모 없음)") {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = addRule, onCheckedChange = { addRule = it })
            Text("앞으로 '${group.merchant}'은(는) 자동으로 이 카테고리", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    },
    confirmButton = { TextButton(enabled = category != null, onClick = { onSave(category!!, addRule) }) { Text("바꾸기", fontWeight = FontWeight.Bold) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
}
