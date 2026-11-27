package com.moneyflow.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyflow.ui.common.KrwAmountField
import com.moneyflow.ui.theme.Gray600

/** Amount range, accounts and #tags for the ledger, plus saving the current filter under a name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailFilterDialog(
  state: LedgerUiState,
  onDismiss: () -> Unit,
  onApply: (minAmount: Long?, maxAmount: Long?, accountIds: Set<Long>, tags: Set<String>) -> Unit,
  onSave: (String) -> Unit,
  onDeleteSaved: (SavedFilter) -> Unit
) {
  val f = state.filters
  var min by rememberSaveable { mutableStateOf(f.minAmount?.toString() ?: "") }
  var max by rememberSaveable { mutableStateOf(f.maxAmount?.toString() ?: "") }
  var accounts by rememberSaveable { mutableStateOf(f.accountIds.toList()) }
  var tags by rememberSaveable { mutableStateOf(f.tags.toList()) }
  var saveName by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("상세 필터", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("금액", fontWeight = FontWeight.SemiBold)
        KrwAmountField(value = min, onValueChange = { min = it }, label = "최소 금액")
        KrwAmountField(value = max, onValueChange = { max = it }, label = "최대 금액")
        if (state.accounts.isNotEmpty()) {
          Text("계좌·카드", fontWeight = FontWeight.SemiBold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.accounts.forEach { a ->
              FilterChip(selected = a.id in accounts, onClick = { accounts = if (a.id in accounts) accounts - a.id else accounts + a.id }, label = { Text(a.name) })
            }
          }
        }
        if (state.allTags.isNotEmpty()) {
          Text("태그 (모두 가진 거래)", fontWeight = FontWeight.SemiBold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.allTags.take(30).forEach { t ->
              FilterChip(selected = t in tags, onClick = { tags = if (t in tags) tags - t else tags + t }, label = { Text("#$t") })
            }
          }
        }
        Text("지금 조건 저장", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text("검색어·종류·카테고리·기간과 위 조건을 이름을 붙여 저장해요. 필터 줄의 ★을 누르면 바로 적용돼요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
        Row {
          OutlinedTextField(
            value = saveName, onValueChange = { saveName = it.take(12) }, singleLine = true, label = { Text("이름 (예: 큰 지출)") },
            modifier = Modifier.weight(1f).testTag("saved_filter_name")
          )
          TextButton(enabled = saveName.isNotBlank(), onClick = {
            onApply(min.toLongOrNull(), max.toLongOrNull(), accounts.toSet(), tags.toSet())
            onSave(saveName)
            onDismiss()
          }) { Text("저장") }
        }
        if (state.savedFilters.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.savedFilters.forEach { s ->
              InputChip(
                selected = false, onClick = { }, label = { Text("★ ${s.name}") },
                trailingIcon = {
                  androidx.compose.material3.IconButton(onClick = { onDeleteSaved(s) }, modifier = Modifier.size(32.dp)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "'${s.name}' 저장된 필터 지우기", modifier = Modifier.size(16.dp))
                  }
                }
              )
            }
          }
          Text("✕를 누르면 저장된 필터가 지워져요.", style = MaterialTheme.typography.bodySmall, color = Gray600)
        }
        if (f.detailCount > 0) {
          TextButton(onClick = { onApply(null, null, emptySet(), emptySet()); onDismiss() }) { Text("상세 조건 모두 지우기") }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onApply(min.toLongOrNull(), max.toLongOrNull(), accounts.toSet(), tags.toSet()); onDismiss() }, modifier = Modifier.testTag("detail_filter_apply")) {
        Text("적용", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
  )
}
