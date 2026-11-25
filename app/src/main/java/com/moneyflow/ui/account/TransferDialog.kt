package com.moneyflow.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.ui.common.KrwAmountField
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

/**
 * Records a transfer between two of the user's accounts. Both accounts can be chosen from all
 * accounts; the same account cannot be picked twice. With [fromLocked] the source account is
 * fixed to [initialFromId] (account detail screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
  accounts: List<AccountEntity>,
  initialFromId: Long?,
  onDismiss: () -> Unit,
  onTransfer: (from: AccountEntity, to: AccountEntity, amount: Long, memo: String) -> Unit,
  fromLocked: Boolean = false
) {
  var fromId by rememberSaveable {
    mutableStateOf(initialFromId ?: (accounts.firstOrNull { !it.isDebt } ?: accounts.firstOrNull())?.id)
  }
  var toId by rememberSaveable { mutableStateOf(accounts.firstOrNull { it.id != fromId }?.id) }
  var amountText by rememberSaveable { mutableStateOf("") }
  var memo by rememberSaveable { mutableStateOf("") }
  var submitted by rememberSaveable { mutableStateOf(false) }

  val from = accounts.firstOrNull { it.id == fromId }
  val to = accounts.firstOrNull { it.id == toId }
  val amount = amountText.toLongOrNull() ?: 0L
  val amountError = submitted && amount <= 0L
  val overBalance = from != null && !from.isDebt && amount > from.balance

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.92f).testTag("transfer_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = CardSurface
    ) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("내 계좌 간 이체", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gray900)
            Text("내 계좌끼리 옮긴 돈을 기록해요", style = MaterialTheme.typography.bodySmall, color = Gray600)
          }
          IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600) }
        }

        if (accounts.size < 2) {
          Spacer(Modifier.height(16.dp))
          Text("이체하려면 계좌가 2개 이상 필요해요.", color = Gray600, style = MaterialTheme.typography.bodyMedium)
        } else {

        Spacer(Modifier.height(16.dp))
        AccountPicker(
          label = "보내는 계좌",
          accounts = accounts,
          selected = from,
          disabledId = toId,
          enabled = !fromLocked,
          onSelect = { fromId = it.id },
          testTag = "transfer_from_picker"
        )
        if (!fromLocked) {
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = { val f = fromId; fromId = toId; toId = f }) {
              Icon(Icons.Default.SwapVert, contentDescription = "보내는 계좌와 받는 계좌 바꾸기", tint = com.moneyflow.ui.theme.TossBlueText)
            }
          }
        } else {
          Spacer(Modifier.height(8.dp))
        }
        AccountPicker(
          label = "받는 계좌",
          accounts = accounts,
          selected = to,
          disabledId = fromId,
          enabled = true,
          onSelect = { toId = it.id },
          testTag = "transfer_to_picker"
        )

        Spacer(Modifier.height(12.dp))
        KrwAmountField(
          value = amountText,
          onValueChange = { amountText = it },
          label = "보낼 금액",
          isError = amountError,
          supportingText = when {
            amountError -> "0원보다 큰 금액을 입력해 주세요"
            overBalance -> "보내는 계좌 잔액(${Formatters.formatCurrency(from!!.balance)})보다 커요"
            else -> null
          },
          modifier = Modifier.testTag("transfer_amount_input")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(10_000L to "+1만", 50_000L to "+5만", 100_000L to "+10만").forEach { (add, label) ->
            AssistChip(
              onClick = { amountText = (amount + add).coerceAtMost(9_999_999_999_999L).toString() },
              label = { Text(label) }
            )
          }
          if (from != null && !from.isDebt && from.balance > 0) {
            AssistChip(onClick = { amountText = from.balance.toString() }, label = { Text("전액") })
          }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = memo,
          onValueChange = { memo = it.take(40) },
          label = { Text("메모 (선택)") },
          placeholder = { Text("예: 비상금 옮기기, 카드값") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("transfer_memo_input")
        )

        Spacer(Modifier.height(20.dp))
        Button(
          onClick = {
            submitted = true
            val f = from ?: return@Button
            val t = to ?: return@Button
            if (amount <= 0L || f.id == t.id) return@Button
            onTransfer(f, t, amount, memo.trim())
            onDismiss()
          },
          enabled = from != null && to != null && from.id != to.id,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("execute_transfer_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
        ) { Text("이체 기록하기", fontWeight = FontWeight.Bold) }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(
  label: String,
  accounts: List<AccountEntity>,
  selected: AccountEntity?,
  disabledId: Long?,
  enabled: Boolean,
  onSelect: (AccountEntity) -> Unit,
  testTag: String
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = expanded && enabled,
    onExpandedChange = { if (enabled) expanded = it },
    modifier = Modifier.testTag(testTag)
  ) {
    OutlinedTextField(
      value = selected?.let { "${it.name} · ${it.bankName}" } ?: "계좌 선택",
      onValueChange = {},
      readOnly = true,
      enabled = enabled,
      singleLine = true,
      label = { Text(label) },
      supportingText = selected?.let { { Text("잔액 ${Formatters.formatCurrency(it.balance)}${if (it.isDebt) " (부채)" else ""}") } },
      trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
    )
    ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
      accounts.forEach { account ->
        val isDisabled = account.id == disabledId
        DropdownMenuItem(
          text = {
            Column {
              Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
              Text(
                if (isDisabled) "다른 쪽에서 선택한 계좌" else "${account.bankName} · ${Formatters.formatCurrency(account.balance)}",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          },
          leadingIcon = { Box(Modifier.size(12.dp).background(Color(account.colorHex), CircleShape)) },
          enabled = !isDisabled,
          onClick = {
            onSelect(account)
            expanded = false
          },
          contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
        )
      }
    }
  }
}
