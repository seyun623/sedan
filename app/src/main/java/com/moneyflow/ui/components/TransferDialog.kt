package com.moneyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

@Composable
fun TransferDialog(
  accounts: List<AccountEntity>,
  initialFromAccount: AccountEntity? = null,
  onDismiss: () -> Unit,
  onTransfer: (from: AccountEntity, to: AccountEntity, amount: Long, memo: String) -> Unit
) {
  val nonDebtAccounts = accounts.filter { !it.isDebt }
  var fromAccount by remember { mutableStateOf(initialFromAccount ?: nonDebtAccounts.firstOrNull()) }
  var toAccount by remember {
    mutableStateOf(accounts.firstOrNull { it.id != fromAccount?.id })
  }
  var amountText by remember { mutableStateOf("") }
  var memoText by remember { mutableStateOf("") }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface)
        .testTag("transfer_dialog"),
      tonalElevation = 6.dp
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "내 통장 간 이동",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Text(
              text = "내 등록 통장 간 잔액 이동 기록",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // From -> To Card visual
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Gray100)
            .padding(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // From Account summary
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "출금", style = MaterialTheme.typography.labelSmall, color = Gray600)
              Text(
                text = fromAccount?.name ?: "계좌 선택",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
              Text(
                text = "잔액 ${Formatters.formatCurrency(fromAccount?.balance ?: 0L)}",
                style = MaterialTheme.typography.bodySmall,
                color = TossBlue
              )
            }

            Icon(
              Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "송금 방향",
              tint = Gray400,
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            // To Account summary
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "입금", style = MaterialTheme.typography.labelSmall, color = Gray600)
              Text(
                text = toAccount?.name ?: "계좌 선택",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
              Text(
                text = toAccount?.bankName ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Select Destination Account
        Text(
          text = "받는 계좌 선택",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          accounts.filter { it.id != fromAccount?.id }.take(3).forEach { acc ->
            val isSelected = toAccount?.id == acc.id
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) TossBlue else Gray200,
                  shape = RoundedCornerShape(10.dp)
                )
                .background(if (isSelected) TossBlue.copy(alpha = 0.08f) else Color.White)
                .clickable { toAccount = acc }
                .padding(vertical = 8.dp, horizontal = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = acc.name.take(7),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TossBlue else Gray900
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Amount input
        Text(
          text = "보낼 금액",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = amountText,
          onValueChange = { input ->
            if (input.all { it.isDigit() } && input.length <= 11) {
              amountText = input
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("transfer_amount_input"),
          placeholder = { Text("얼마를 보낼까요?", color = Gray400) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          suffix = { Text("원", fontWeight = FontWeight.Bold, color = Gray900) }
        )

        // Fast Quick Amount buttons
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(
            10000L to "+1만",
            50000L to "+5만",
            100000L to "+10만",
            (fromAccount?.balance ?: 0L) to "전액"
          ).forEach { (amt, label) ->
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Gray100)
                .clickable {
                  if (label == "전액") {
                    amountText = (fromAccount?.balance ?: 0L).coerceAtLeast(0L).toString()
                  } else {
                    val curr = amountText.toLongOrNull() ?: 0L
                    amountText = (curr + amt).toString()
                  }
                }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Gray900,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Memo
        Text(
          text = "받는 분에게 표기할 메모 (선택)",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = memoText,
          onValueChange = { memoText = it },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("transfer_memo_input"),
          placeholder = { Text("예: 축의금, 모임회비, 용돈", color = Gray400) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        val currentAmount = amountText.toLongOrNull() ?: 0L
        val canTransfer = currentAmount > 0 &&
          fromAccount != null &&
          toAccount != null &&
          currentAmount <= (fromAccount?.balance ?: 0L)

        Button(
          onClick = {
            val f = fromAccount ?: return@Button
            val t = toAccount ?: return@Button
            onTransfer(f, t, currentAmount, memoText)
            onDismiss()
          },
          enabled = canTransfer,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("execute_transfer_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = TossBlue,
            contentColor = Color.White
          )
        ) {
          Text(
            text = if (currentAmount > (fromAccount?.balance ?: 0L)) "잔액이 부족합니다" else "잔액 이동 기록하기",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
