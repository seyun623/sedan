package com.moneyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(
  accounts: List<AccountEntity>,
  onDismiss: () -> Unit,
  onConfirm: (
    type: TransactionType,
    amount: Long,
    category: String,
    account: AccountEntity,
    toAccount: AccountEntity?,
    memo: String,
    paymentMethod: String
  ) -> Unit
) {
  var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
  var amountText by remember { mutableStateOf("") }
  var memoText by remember { mutableStateOf("") }
  var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
  var selectedToAccount by remember { mutableStateOf(accounts.getOrNull(1)) }
  var selectedPaymentMethod by remember { mutableStateOf("체크카드") }

  val categories = when (selectedType) {
    TransactionType.EXPENSE -> FinanceCategories.expenseCategories.map { it.first }
    TransactionType.INCOME -> FinanceCategories.incomeCategories.map { it.first }
    TransactionType.TRANSFER -> FinanceCategories.transferCategories.map { it.first }
  }
  var selectedCategory by remember(selectedType) { mutableStateOf(categories.firstOrNull() ?: "기타") }

  val paymentMethods = listOf("체크카드", "신용카드", "계좌이체", "현금", "간편결제")

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface)
        .testTag("add_transaction_dialog"),
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Top Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "내역 추가",
            style = MaterialTheme.typography.titleLarge,
            color = Gray900
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Type Tabs
        TabRow(
          selectedTabIndex = selectedType.ordinal,
          containerColor = Gray100,
          contentColor = TossBlue,
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .height(44.dp)
        ) {
          TransactionType.values().forEach { type ->
            Tab(
              selected = selectedType == type,
              onClick = { selectedType = type },
              text = {
                Text(
                  text = type.title,
                  fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal,
                  color = if (selectedType == type) {
                    when (type) {
                      TransactionType.EXPENSE -> AccentRed
                      TransactionType.INCOME -> AccentGreen
                      TransactionType.TRANSFER -> TossBlue
                    }
                  } else Gray600
                )
              }
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Amount Display & Input
        Text(
          text = "금액",
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
            .testTag("transaction_amount_input"),
          placeholder = { Text("0원", color = Gray400) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          suffix = { Text("원", fontWeight = FontWeight.Bold, color = Gray900) }
        )

        // Quick amount buttons
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(
            10000L to "+1만",
            50000L to "+5만",
            100000L to "+10만",
            500000L to "+50만"
          ).forEach { (amt, label) ->
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Gray100)
                .clickable {
                  val curr = amountText.toLongOrNull() ?: 0L
                  amountText = (curr + amt).toString()
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

        // Category Selection
        Text(
          text = "카테고리",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          categories.forEach { cat ->
            val isSelected = selectedCategory == cat
            val colorLong = FinanceCategories.getColorForCategory(cat)
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) Color(colorLong) else Gray100)
                .clickable { selectedCategory = cat }
                .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text(
                text = cat,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) Color.White else Gray900,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account Selector
        Text(
          text = if (selectedType == TransactionType.TRANSFER) "출금 계좌" else "사용 계좌 / 카드",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          accounts.forEach { acc ->
            val isSelected = selectedAccount?.id == acc.id
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) TossBlue else Gray200,
                  shape = RoundedCornerShape(12.dp)
                )
                .background(if (isSelected) TossBlue.copy(alpha = 0.08f) else Color.White)
                .clickable { selectedAccount = acc }
                .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
              Text(
                text = acc.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) TossBlue else Gray900,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        // To Account if Transfer
        if (selectedType == TransactionType.TRANSFER) {
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "입금 계좌",
            style = MaterialTheme.typography.labelMedium,
            color = Gray600
          )
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            accounts.filter { it.id != selectedAccount?.id }.forEach { acc ->
              val isSelected = selectedToAccount?.id == acc.id
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) AccentGreen else Gray200,
                    shape = RoundedCornerShape(12.dp)
                  )
                  .background(if (isSelected) AccentGreen.copy(alpha = 0.08f) else Color.White)
                  .clickable { selectedToAccount = acc }
                  .padding(horizontal = 10.dp, vertical = 8.dp)
              ) {
                Text(
                  text = acc.name,
                  style = MaterialTheme.typography.bodySmall,
                  color = if (isSelected) AccentGreen else Gray900,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              }
            }
          }
        } else {
          // Payment Method Selector
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "결제 수단",
            style = MaterialTheme.typography.labelMedium,
            color = Gray600
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            paymentMethods.take(4).forEach { method ->
              val isSelected = selectedPaymentMethod == method
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) TossBlue else Gray100)
                .clickable { selectedPaymentMethod = method }
                .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = method,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isSelected) Color.White else Gray900,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Memo
        Text(
          text = "메모 / 거래처",
          style = MaterialTheme.typography.labelMedium,
          color = Gray600
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = memoText,
          onValueChange = { memoText = it },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_memo_input"),
          placeholder = { Text("예: 스타벅스, 배달의민족, 월급", color = Gray400) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Confirm Button
        val isValid = (amountText.toLongOrNull() ?: 0L) > 0 && selectedAccount != null
        Button(
          onClick = {
            val amt = amountText.toLongOrNull() ?: 0L
            val acc = selectedAccount ?: return@Button
            onConfirm(
              selectedType,
              amt,
              selectedCategory,
              acc,
              if (selectedType == TransactionType.TRANSFER) selectedToAccount else null,
              memoText,
              selectedPaymentMethod
            )
            onDismiss()
          },
          enabled = isValid,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("submit_transaction_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = TossBlue,
            contentColor = Color.White
          )
        ) {
          Text(
            text = "저장하기",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
