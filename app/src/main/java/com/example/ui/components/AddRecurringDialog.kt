package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AccountEntity
import com.example.data.model.FinanceCategories
import com.example.data.model.TransactionType
import com.example.ui.theme.AccentRed
import com.example.ui.theme.Gray100
import com.example.ui.theme.Gray200
import com.example.ui.theme.Gray500
import com.example.ui.theme.Gray600
import com.example.ui.theme.Gray900
import com.example.ui.theme.TossBlue

@Composable
fun AddRecurringDialog(
  accounts: List<AccountEntity>,
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    type: TransactionType,
    amount: Long,
    category: String,
    dayOfMonth: Int,
    accountId: Long,
    accountName: String,
    memo: String
  ) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var amountText by remember { mutableStateOf("") }
  var type by remember { mutableStateOf(TransactionType.EXPENSE) }
  var selectedCategory by remember { mutableStateOf("주거/통신") }
  var dayOfMonth by remember { mutableIntStateOf(10) }
  var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
  var memo by remember { mutableStateOf("") }

  val categories = if (type == TransactionType.EXPENSE) {
    FinanceCategories.expenseCategories.map { it.first }
  } else {
    FinanceCategories.incomeCategories.map { it.first }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("add_recurring_dialog")
    ) {
      Column(
        modifier = Modifier
          .padding(22.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "정기 고정 거래 등록",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Black,
              color = Gray900
            )
            Text(
              text = "매월 지정일에 자동으로 가계부에 기록됩니다",
              style = MaterialTheme.typography.bodySmall,
              color = Gray600
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Type Selector (지출 vs 수입)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gray100)
            .padding(4.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (type == TransactionType.EXPENSE) Color.White else Color.Transparent)
              .clickable {
                type = TransactionType.EXPENSE
                selectedCategory = "주거/통신"
              }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "정기 지출 (월세, 구독 등)",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (type == TransactionType.EXPENSE) FontWeight.Bold else FontWeight.Medium,
              color = if (type == TransactionType.EXPENSE) AccentRed else Gray600
            )
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (type == TransactionType.INCOME) Color.White else Color.Transparent)
              .clickable {
                type = TransactionType.INCOME
                selectedCategory = "급여"
              }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "정기 수입 (급여, 용돈 등)",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (type == TransactionType.INCOME) FontWeight.Bold else FontWeight.Medium,
              color = if (type == TransactionType.INCOME) TossBlue else Gray600
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Text(
              text = "거래명",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = title,
              onValueChange = { title = it },
              placeholder = { Text("예: 월세 및 관리비, 넷플릭스, 매월 급여") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TossBlue,
                unfocusedBorderColor = Gray200
              ),
              modifier = Modifier.fillMaxWidth().testTag("recurring_title_input")
            )
          }

          item {
            Text(
              text = "금액 (원)",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = amountText,
              onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
              placeholder = { Text("예: 550000") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TossBlue,
                unfocusedBorderColor = Gray200
              ),
              modifier = Modifier.fillMaxWidth().testTag("recurring_amount_input")
            )
          }

          item {
            Text(
              text = "매월 실행일",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items((1..31).toList()) { day ->
                val isSelected = dayOfMonth == day
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) TossBlue else Gray100)
                    .clickable { dayOfMonth = day },
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "${day}일",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Gray900
                  )
                }
              }
            }
          }

          item {
            Text(
              text = "카테고리",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) TossBlue else Gray100)
                    .clickable { selectedCategory = cat }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                  Text(
                    text = cat,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Gray900
                  )
                }
              }
            }
          }

          item {
            Text(
              text = "출금/입금 계좌",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(accounts) { acc ->
                val isSelected = selectedAccount?.id == acc.id
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) TossBlue.copy(alpha = 0.15f) else Gray100)
                    .clickable { selectedAccount = acc }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                  Text(
                    text = "${acc.name} (${acc.bankName})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) TossBlue else Gray900
                  )
                }
              }
            }
          }

          item {
            Text(
              text = "메모 (선택)",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = memo,
              onValueChange = { memo = it },
              placeholder = { Text("추가 메모 입력") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TossBlue,
                unfocusedBorderColor = Gray200
              ),
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
          onClick = {
            val amt = amountText.toLongOrNull() ?: 0L
            val acc = selectedAccount
            if (title.isNotBlank() && amt > 0 && acc != null) {
              onConfirm(
                title,
                type,
                amt,
                selectedCategory,
                dayOfMonth,
                acc.id,
                acc.name,
                memo
              )
            }
          },
          enabled = title.isNotBlank() && (amountText.toLongOrNull() ?: 0L) > 0L && selectedAccount != null,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("submit_recurring_btn"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue)
        ) {
          Text(
            text = "정기 거래 등록",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }
    }
  }
}
