package com.moneyflow.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.ui.common.KrwAmountField
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer

/** Values entered in [AccountFormDialog]. */
data class AccountDraft(
  val name: String,
  val bankName: String,
  val type: AccountType,
  val accountNumber: String,
  val balance: Long,
  val colorHex: Long,
  val isDebt: Boolean,
  /** Card payment day 1..31, 0 = none. */
  val paymentDay: Int = 0
)

val AccountColorPalette: List<Long> = listOf(
  0xFF0064FF, 0xFF00B074, 0xFFF04452, 0xFFFF8A00, 0xFF7059F6,
  0xFF00A389, 0xFFE64980, 0xFF795548, 0xFF4E5968, 0xFF191F28
)

private val PopularInstitutions = listOf(
  "토스뱅크", "카카오뱅크", "KB국민", "신한", "우리", "하나", "NH농협", "IBK기업",
  "삼성카드", "현대카드", "신한카드", "삼성증권", "미래에셋", "현금"
)

private fun defaultColorFor(bank: String): Long = when (bank) {
  "토스뱅크" -> 0xFF0064FF
  "KB국민", "카카오뱅크" -> 0xFFFF8A00
  "신한", "신한카드" -> 0xFF0064FF
  "우리" -> 0xFF00A389
  "하나" -> 0xFF00B074
  "NH농협" -> 0xFF00B074
  "현대카드" -> 0xFF191F28
  "삼성카드", "삼성증권" -> 0xFF7059F6
  "현금" -> 0xFF4E5968
  else -> 0xFF0064FF
}

/**
 * Add / edit account form. Pass [initial] to edit an existing account (balance becomes a
 * "잔액 보정" field). All form state survives rotation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountFormDialog(
  initial: AccountEntity?,
  onDismiss: () -> Unit,
  onSubmit: (AccountDraft) -> Unit
) {
  val isEdit = initial != null
  var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
  var bankName by rememberSaveable { mutableStateOf(initial?.bankName ?: "") }
  var type by rememberSaveable { mutableStateOf(initial?.accountType ?: AccountType.BANK) }
  var accountNumber by rememberSaveable { mutableStateOf(initial?.accountNumber ?: "") }
  var balanceText by rememberSaveable {
    mutableStateOf(initial?.balance?.let { if (it == 0L) "" else kotlin.math.abs(it).toString() } ?: "")
  }
  var negativeBalance by rememberSaveable { mutableStateOf((initial?.balance ?: 0L) < 0L) }
  var isDebt by rememberSaveable { mutableStateOf(initial?.isDebt ?: false) }
  var paymentDayText by rememberSaveable { mutableStateOf(initial?.paymentDay?.takeIf { it > 0 }?.toString() ?: "") }
  var colorHex by rememberSaveable { mutableStateOf(initial?.colorHex ?: 0xFF0064FF) }
  var colorTouched by rememberSaveable { mutableStateOf(isEdit) }
  var submitted by rememberSaveable { mutableStateOf(false) }

  val nameError = submitted && name.isBlank()
  val bankError = submitted && bankName.isBlank()

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.92f).testTag("account_form_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = CardSurface
    ) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              if (isEdit) "계좌 정보 수정" else "계좌 추가",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Text(
              if (isEdit) "잔액을 바꾸면 현재 잔액이 그 금액으로 보정돼요" else "통장, 카드, 현금 등 관리할 자산을 등록해요",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
          IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600) }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(30) },
          label = { Text("계좌 별칭") },
          placeholder = { Text("예: 월급통장, 비상금") },
          singleLine = true,
          isError = nameError,
          supportingText = if (nameError) ({ Text("별칭을 입력해 주세요") }) else null,
          modifier = Modifier.fillMaxWidth().testTag("account_name_input")
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = bankName,
          onValueChange = {
            bankName = it.take(20)
            if (!colorTouched) colorHex = defaultColorFor(bankName)
          },
          label = { Text("금융기관") },
          singleLine = true,
          isError = bankError,
          supportingText = if (bankError) ({ Text("금융기관을 입력하거나 선택해 주세요") }) else null,
          modifier = Modifier.fillMaxWidth().testTag("account_bank_input")
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PopularInstitutions.forEach { inst ->
            FilterChip(
              selected = bankName == inst,
              onClick = {
                bankName = inst
                if (!colorTouched) colorHex = defaultColorFor(inst)
                if (inst == "현금") type = AccountType.CASH
              },
              label = { Text(inst) },
              colors = chipColors()
            )
          }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("자산 유형")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          AccountType.entries.forEach { t ->
            FilterChip(
              selected = type == t,
              onClick = {
                type = t
                isDebt = t == AccountType.LOAN || t == AccountType.CREDIT_CARD
              },
              label = { Text(t.displayName) },
              colors = chipColors()
            )
          }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = accountNumber,
          onValueChange = { input -> accountNumber = input.filter { it.isDigit() || it == '-' }.take(30) },
          label = { Text("계좌/카드 번호 (선택)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        KrwAmountField(
          value = balanceText,
          onValueChange = { balanceText = it },
          label = when {
            isEdit && isDebt -> "현재 부채 금액 보정"
            isEdit -> "현재 잔액 보정"
            isDebt -> "현재 부채/결제 예정 금액"
            else -> "현재 잔액"
          },
          modifier = Modifier.testTag("account_balance_input")
        )
        if (!isDebt && (negativeBalance || (initial?.balance ?: 0L) < 0L)) {
          SwitchRow(
            title = "마이너스 잔액",
            subtitle = "마이너스 통장처럼 잔액이 0보다 작을 때 켜 주세요",
            checked = negativeBalance,
            onCheckedChange = { negativeBalance = it }
          )
        }

        if (type == AccountType.CREDIT_CARD || type == AccountType.LOAN) {
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = paymentDayText,
            onValueChange = { paymentDayText = it.filter(Char::isDigit).take(2) },
            label = { Text(if (type == AccountType.LOAN) "상환일 (선택, 1~31)" else "카드 결제일 (선택, 1~31)") },
            supportingText = { Text("입력하면 3일 전에 알려 드려요") },
            isError = paymentDayText.isNotEmpty() && paymentDayText.toInt() !in 1..31,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("account_payment_day")
          )
        }

        SwitchRow(
          title = "부채로 분류",
          subtitle = if (isEdit) "거래 내역이 있는 계좌는 바꿀 수 없어요" else "순자산 계산 시 차감돼요",
          checked = isDebt,
          onCheckedChange = { isDebt = it }
        )

        Spacer(Modifier.height(8.dp))
        SectionLabel("색상")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          AccountColorPalette.forEach { c ->
            val selected = c == colorHex
            Box(
              modifier = Modifier
                .size(48.dp)
                .semantics { this.selected = selected }
                .clickable(role = Role.RadioButton, onClickLabel = "색상 선택") {
                  colorHex = c
                  colorTouched = true
                },
              contentAlignment = Alignment.Center
            ) {
              Box(
                Modifier
                  .size(32.dp)
                  .background(Color(c), CircleShape)
                  .then(if (selected) Modifier.border(2.dp, Gray900, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
              ) {
                if (selected) Icon(Icons.Default.Check, contentDescription = "선택됨", tint = OnBrand, modifier = Modifier.size(18.dp))
              }
            }
          }
        }

        Spacer(Modifier.height(20.dp))
        Button(
          onClick = {
            submitted = true
            if (name.isBlank() || bankName.isBlank()) return@Button
            val raw = balanceText.toLongOrNull() ?: 0L
            val balance = if (!isDebt && negativeBalance) -raw else raw
            onSubmit(
              AccountDraft(
                name = name.trim(),
                bankName = bankName.trim(),
                type = type,
                accountNumber = accountNumber.trim(),
                balance = balance,
                colorHex = colorHex,
                isDebt = isDebt,
                paymentDay = paymentDayText.toIntOrNull()?.takeIf { it in 1..31 && (type == AccountType.CREDIT_CARD || type == AccountType.LOAN) } ?: 0
              )
            )
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("submit_account_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand)
        ) { Text(if (isEdit) "저장하기" else "계좌 등록하기", fontWeight = FontWeight.Bold) }
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(text, style = MaterialTheme.typography.labelMedium, color = Gray600, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = Gray900, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray600)
    }
    Switch(
      checked = checked,
      onCheckedChange = null,
      colors = SwitchDefaults.colors(checkedTrackColor = TossBlue, checkedThumbColor = OnBrand)
    )
  }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
  selectedContainerColor = TossBlueContainer,
  selectedLabelColor = TossBlue
)
