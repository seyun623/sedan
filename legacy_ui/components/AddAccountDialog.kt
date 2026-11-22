package com.moneyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneyflow.data.model.AccountType
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAccountDialog(
  onDismiss: () -> Unit,
  onConfirm: (
    name: String,
    bankName: String,
    accountType: AccountType,
    accountNumber: String,
    balance: Long,
    colorHex: Long,
    isDebt: Boolean
  ) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var bankName by remember { mutableStateOf("토스뱅크") }
  var accountType by remember { mutableStateOf(AccountType.BANK) }
  var accountNumber by remember { mutableStateOf("") }
  var balanceText by remember { mutableStateOf("") }
  var isDebt by remember { mutableStateOf(false) }

  val popularInstitutions = listOf("토스뱅크", "카카오뱅크", "KB국민", "신한", "우리", "하나", "현대카드", "삼성증권", "미래에셋", "현금")

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface)
        .testTag("add_account_dialog"),
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "내 통장/카드 직접 등록",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Text(
              text = "개인 가계부용 계좌 등록 (사업자 인증 불필요)",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Financial Institution
        Text(text = "금융기관 / 은행", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          popularInstitutions.forEach { inst ->
            val isSelected = bankName == inst
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) TossBlue else Gray100)
                .clickable { bankName = inst }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = inst,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) Color.White else Gray900,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Account Name
        Text(text = "계좌/자산 별칭", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("account_name_input"),
          placeholder = { Text("예: 월급통장, 세이프박스, 비상금", color = Gray400) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Account Type
        Text(text = "자산 유형", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          AccountType.values().forEach { type ->
            val isSelected = accountType == type
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) TossBlue else Gray100)
                .clickable {
                  accountType = type
                  if (type == AccountType.LOAN || type == AccountType.CREDIT_CARD) {
                    isDebt = true
                  }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = type.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) Color.White else Gray900,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Initial Balance
        Text(
          text = if (isDebt) "현재 부채/결제예정 금액" else "현재 잔액",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = balanceText,
          onValueChange = { input ->
            if (input.all { it.isDigit() } && input.length <= 12) {
              balanceText = input
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("account_balance_input"),
          placeholder = { Text("0원", color = Gray400) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          suffix = { Text("원", fontWeight = FontWeight.Bold, color = Gray900) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Debt switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(text = "부채/대출로 분류", style = MaterialTheme.typography.titleSmall, color = Gray900)
            Text(text = "순자산 계산 시 차감됩니다", style = MaterialTheme.typography.bodySmall, color = Gray600)
          }
          Switch(checked = isDebt, onCheckedChange = { isDebt = it })
        }

        Spacer(modifier = Modifier.height(24.dp))

        val canSubmit = name.isNotBlank() && (balanceText.toLongOrNull() ?: -1L) >= 0L
        Button(
          onClick = {
            val bal = balanceText.toLongOrNull() ?: 0L
            val color = when (bankName) {
              "토스뱅크" -> 0xFF0064FF
              "카카오뱅크" -> 0xFFFEE500
              "KB국민" -> 0xFFFFBC00
              "신한" -> 0xFF0046FF
              "우리" -> 0xFF0083CA
              "현대카드" -> 0xFF191F28
              else -> 0xFF7059F6
            }
            onConfirm(name, bankName, accountType, accountNumber, bal, color, isDebt)
            onDismiss()
          },
          enabled = canSubmit,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("submit_account_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = TossBlue,
            contentColor = Color.White
          )
        ) {
          Text(
            text = "내 통장/카드 등록하기",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
