package com.moneyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray300
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters

/**
 * Modern Toss-styled Floating Action Button to initiate adding transactions.
 */
@Composable
fun AddTransactionFloatingActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isExtended: Boolean = false
) {
  if (isExtended) {
    ExtendedFloatingActionButton(
      onClick = onClick,
      containerColor = TossBlue,
      contentColor = Color.White,
      shape = RoundedCornerShape(16.dp),
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
      icon = { Icon(Icons.Default.Add, contentDescription = null) },
      text = { Text("내역 추가", fontWeight = FontWeight.Bold) },
      modifier = modifier.testTag("fab_add_transaction")
    )
  } else {
    FloatingActionButton(
      onClick = onClick,
      containerColor = TossBlue,
      contentColor = Color.White,
      shape = CircleShape,
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
      modifier = modifier.testTag("fab_add_transaction")
    ) {
      Icon(Icons.Default.Add, contentDescription = "거래 내역 추가")
    }
  }
}

/**
 * Interactive Modal Bottom Sheet allowing users to manually register new
 * Income or Expense transactions into the Room Database.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionBottomSheet(
  accounts: List<AccountEntity>,
  onDismiss: () -> Unit,
  onSave: (
    type: TransactionType,
    amount: Long,
    category: String,
    account: AccountEntity,
    timestamp: Long,
    memo: String,
    paymentMethod: String,
    isExcludedFromBudget: Boolean
  ) -> Unit,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
  var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
  var amountText by remember { mutableStateOf("") }
  var memoText by remember { mutableStateOf("") }

  // Default Categories
  val defaultExpenseCat = FinanceCategories.expenseCategories.first().first
  val defaultIncomeCat = FinanceCategories.incomeCategories.first().first
  var selectedCategory by remember { mutableStateOf(defaultExpenseCat) }

  // Default Account
  val defaultAccount = remember(accounts) {
    accounts.firstOrNull { !it.isDebt } ?: accounts.firstOrNull() ?: AccountEntity(
      id = 1L,
      name = "기본 통장",
      bankName = "토스뱅크",
      accountType = com.moneyflow.data.model.AccountType.BANK,
      accountNumber = "1000-01",
      balance = 0L
    )
  }
  var selectedAccount by remember { mutableStateOf(defaultAccount) }

  // Payment method
  val paymentMethods = remember { listOf("체크카드", "신용카드", "계좌이체", "현금", "간편결제") }
  var selectedPaymentMethod by remember { mutableStateOf("체크카드") }

  // Budget exclusion toggle
  var isExcludedFromBudget by remember { mutableStateOf(false) }

  val scrollState = rememberScrollState()
  val numericAmount = amountText.toLongOrNull() ?: 0L
  val canSubmit = numericAmount > 0L && selectedCategory.isNotBlank()

  val activeThemeColor by animateColorAsState(
    targetValue = if (transactionType == TransactionType.INCOME) AccentGreen else AccentRed,
    label = "theme_color"
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color.White,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle = null,
    modifier = Modifier.testTag("add_transaction_bottom_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .imePadding()
        .navigationBarsPadding()
        .verticalScroll(scrollState)
        .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
      // 1. Top Drag Indicator & Header
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Gray300)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = if (transactionType == TransactionType.INCOME) "수입 내역 등록" else "지출 내역 등록",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Gray900
          )
          Text(
            text = "Room Database에 안전하게 기록됩니다",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
          )
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Gray100)
            .testTag("close_bottom_sheet_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "닫기",
            tint = Gray700,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 2. Transaction Type Segmented Switcher (지출 / 수입)
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Gray100,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Expense Tab
          val isExpense = transactionType == TransactionType.EXPENSE
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(if (isExpense) Color.White else Color.Transparent)
              .clickable {
                transactionType = TransactionType.EXPENSE
                selectedCategory = defaultExpenseCat
              }
              .padding(vertical = 10.dp)
              .testTag("type_tab_expense"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.TrendingDown,
                contentDescription = null,
                tint = if (isExpense) AccentRed else Gray500,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "지출",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                color = if (isExpense) AccentRed else Gray600
              )
            }
          }

          // Income Tab
          val isIncome = transactionType == TransactionType.INCOME
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(if (isIncome) Color.White else Color.Transparent)
              .clickable {
                transactionType = TransactionType.INCOME
                selectedCategory = defaultIncomeCat
              }
              .padding(vertical = 10.dp)
              .testTag("type_tab_income"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = if (isIncome) AccentGreen else Gray500,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "수입",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                color = if (isIncome) AccentGreen else Gray600
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // 3. Amount Input with Quick Presets
      Text(
        text = "금액",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gray700
      )
      Spacer(modifier = Modifier.height(6.dp))

      OutlinedTextField(
        value = amountText,
        onValueChange = { input ->
          val digits = input.filter { it.isDigit() }
          if (digits.length <= 11) {
            amountText = digits
          }
        },
        placeholder = {
          Text(
            text = "0원",
            style = MaterialTheme.typography.headlineMedium,
            color = Gray400,
            fontWeight = FontWeight.Bold
          )
        },
        trailingIcon = {
          if (amountText.isNotEmpty()) {
            IconButton(onClick = { amountText = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "금액 초기화", tint = Gray500)
            }
          }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
          fontWeight = FontWeight.Black,
          color = activeThemeColor
        ),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = activeThemeColor,
          unfocusedBorderColor = Gray200
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("amount_input_field")
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Quick Amount Addition Chips (+1만, +5만, +10만, +50만, +100만)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val quickAmounts = listOf(
          "+1만" to 10_000L,
          "+5만" to 50_000L,
          "+10만" to 100_000L,
          "+50만" to 500_000L,
          "+100만" to 1_000_000L
        )
        quickAmounts.forEach { (label, delta) ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(Gray100)
              .clickable {
                val current = amountText.toLongOrNull() ?: 0L
                amountText = (current + delta).toString()
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("quick_add_${delta}_btn"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = Gray800
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Gray100)
            .clickable { amountText = "" }
            .padding(horizontal = 10.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "초기화",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Gray600
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 4. Memo / Merchant Name
      Text(
        text = if (transactionType == TransactionType.INCOME) "수입처 / 내용" else "사용처 / 내용",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gray700
      )
      Spacer(modifier = Modifier.height(6.dp))

      OutlinedTextField(
        value = memoText,
        onValueChange = { memoText = it },
        placeholder = {
          Text(
            text = if (transactionType == TransactionType.INCOME) "예: 10월 급여, 부수입, 용돈" else "예: 스타벅스 강남점, 배달의민족, 마트 장보기",
            color = Gray400,
            fontSize = 14.sp
          )
        },
        trailingIcon = {
          if (memoText.isNotEmpty()) {
            IconButton(onClick = { memoText = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "내용 초기화", tint = Gray500)
            }
          }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = TossBlue,
          unfocusedBorderColor = Gray200
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("memo_input_field")
      )

      Spacer(modifier = Modifier.height(18.dp))

      // 5. Category Selection with CategoryIconHelper
      Text(
        text = "카테고리 선택",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gray700
      )
      Spacer(modifier = Modifier.height(8.dp))

      val categoryList = remember(transactionType) {
        if (transactionType == TransactionType.INCOME) {
          FinanceCategories.incomeCategories.map { it.first }
        } else {
          FinanceCategories.expenseCategories.map { it.first }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        categoryList.forEach { catName ->
          val isSelected = selectedCategory == catName
          val catColor = Color(CategoryIconHelper.getColorForCategory(catName))
          val icon = CategoryIconHelper.getIconForCategory(catName)

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) catColor.copy(alpha = 0.15f) else Gray100,
            border = androidx.compose.foundation.BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) catColor else Color.Transparent
            ),
            modifier = Modifier
              .clickable { selectedCategory = catName }
              .testTag("category_item_$catName")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(26.dp)
                  .clip(CircleShape)
                  .background(if (isSelected) catColor else Gray200),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = catName,
                  tint = if (isSelected) Color.White else Gray700,
                  modifier = Modifier.size(15.dp)
                )
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = catName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) catColor else Gray800
              )
              if (isSelected) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = catColor,
                  modifier = Modifier.size(14.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // 6. Account Selection (from Room DB)
      Text(
        text = if (transactionType == TransactionType.INCOME) "입금 계좌 선택" else "출금 계좌 / 결제 카드",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gray700
      )
      Spacer(modifier = Modifier.height(8.dp))

      if (accounts.isNotEmpty()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          accounts.forEach { acc ->
            val isSelected = selectedAccount.id == acc.id
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (isSelected) TossBlue.copy(alpha = 0.1f) else Color.White,
              border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isSelected) TossBlue else Gray200
              ),
              modifier = Modifier
                .clickable { selectedAccount = acc }
                .testTag("account_item_${acc.id}")
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              ) {
                Text(
                  text = acc.name,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) TossBlue else Gray900
                )
                Text(
                  text = "${acc.bankName} · 잔액 ${Formatters.formatCurrency(acc.balance)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 11.sp,
                  color = Gray600
                )
              }
            }
          }
        }
      } else {
        Text(
          text = "등록된 계좌가 없습니다 (기본 통장으로 저장됩니다)",
          style = MaterialTheme.typography.bodySmall,
          color = Gray600
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 7. Payment Method (체크카드, 신용카드, 현금 등)
      Text(
        text = "결제 수단",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gray700
      )
      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        paymentMethods.forEach { method ->
          val isSelected = selectedPaymentMethod == method
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) Gray900 else Gray100)
              .clickable { selectedPaymentMethod = method }
              .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = method,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) Color.White else Gray800
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 8. "예산에서 제외" Toggle (Mainly for Expense)
      if (transactionType == TransactionType.EXPENSE) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gray100.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "월간 총 예산 계산에서 제외",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = Gray900
            )
            Text(
              text = "비정기 대형 지출 등 예산 통계에 미포함",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              color = Gray600
            )
          }

          Switch(
            checked = isExcludedFromBudget,
            onCheckedChange = { isExcludedFromBudget = it },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = TossBlue
            )
          )
        }

        Spacer(modifier = Modifier.height(20.dp))
      }

      // 9. Submit Button
      Button(
        onClick = {
          if (canSubmit) {
            onSave(
              transactionType,
              numericAmount,
              selectedCategory,
              selectedAccount,
              System.currentTimeMillis(),
              memoText.ifBlank { selectedCategory },
              selectedPaymentMethod,
              isExcludedFromBudget
            )
            onDismiss()
          }
        },
        enabled = canSubmit,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = activeThemeColor,
          disabledContainerColor = Gray200
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("submit_transaction_btn")
      ) {
        Icon(
          imageVector = if (transactionType == TransactionType.INCOME) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (canSubmit) {
            "${transactionType.title} ${Formatters.formatCurrency(numericAmount)} 등록하기"
          } else {
            "금액을 입력해주세요"
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
