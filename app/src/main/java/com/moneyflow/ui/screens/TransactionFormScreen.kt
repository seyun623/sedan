package com.moneyflow.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import com.moneyflow.ui.components.CategoryIconHelper
import java.util.Calendar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.model.TransactionEntity
import com.moneyflow.data.model.TransactionType
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentOrange
import com.moneyflow.ui.theme.AccentPurple
import com.moneyflow.ui.theme.AccentPurpleLight
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray200
import com.moneyflow.ui.theme.Gray300
import com.moneyflow.ui.theme.Gray400
import com.moneyflow.ui.theme.Gray50
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray700
import com.moneyflow.ui.theme.Gray800
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.ui.theme.TossBlueContainer
import com.moneyflow.ui.viewmodel.CategorySuggestionUiState
import com.moneyflow.util.Formatters

private data class QuickTemplate(
  val label: String,
  val amount: Long,
  val category: String,
  val memo: String,
  val paymentMethod: String
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
  accounts: List<AccountEntity>,
  editingTransaction: TransactionEntity? = null,
  categorySuggestionState: CategorySuggestionUiState = CategorySuggestionUiState.Idle,
  onRequestCategorySuggestion: (keyword: String, type: TransactionType, debounceMs: Long) -> Unit = { _, _, _ -> },
  onResetCategorySuggestion: () -> Unit = {},
  onBack: () -> Unit,
  onSaveNew: (
    type: TransactionType,
    amount: Long,
    category: String,
    account: AccountEntity,
    toAccount: AccountEntity?,
    timestamp: Long,
    memo: String,
    paymentMethod: String,
    isExcludedFromBudget: Boolean,
    closeAfterSave: Boolean
  ) -> Unit,
  onUpdateExisting: (
    id: Long,
    type: TransactionType,
    amount: Long,
    category: String,
    account: AccountEntity,
    toAccount: AccountEntity?,
    timestamp: Long,
    memo: String,
    paymentMethod: String,
    isExcludedFromBudget: Boolean
  ) -> Unit,
  onDeleteExisting: (TransactionEntity) -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler {
    onResetCategorySuggestion()
    onBack()
  }

  val isEditMode = editingTransaction != null

  var selectedType by remember(editingTransaction) {
    mutableStateOf(editingTransaction?.type ?: TransactionType.EXPENSE)
  }
  var amountText by remember(editingTransaction) {
    mutableStateOf(editingTransaction?.amount?.toString() ?: "")
  }
  var memoText by remember(editingTransaction) {
    mutableStateOf(editingTransaction?.memo ?: "")
  }
  var selectedAccount by remember(editingTransaction, accounts) {
    mutableStateOf(
      accounts.firstOrNull { it.id == editingTransaction?.accountId }
        ?: accounts.firstOrNull()
    )
  }
  var selectedToAccount by remember(editingTransaction, accounts) {
    mutableStateOf(
      accounts.firstOrNull { it.id == editingTransaction?.toAccountId }
        ?: accounts.firstOrNull { it.id != selectedAccount?.id }
    )
  }
  var selectedPaymentMethod by remember(editingTransaction) {
    mutableStateOf(editingTransaction?.paymentMethod ?: "체크카드")
  }
  var isExcludedFromBudget by remember(editingTransaction) {
    mutableStateOf(editingTransaction?.isExcludedFromBudget ?: false)
  }

  var selectedTimestamp by remember(editingTransaction) {
    mutableStateOf(editingTransaction?.timestamp ?: System.currentTimeMillis())
  }
  var showDatePickerDialog by remember { mutableStateOf(false) }
  val datePickerState = rememberDatePickerState(
    initialSelectedDateMillis = selectedTimestamp
  )

  val categories = when (selectedType) {
    TransactionType.EXPENSE -> FinanceCategories.expenseCategories.map { it.first }
    TransactionType.INCOME -> FinanceCategories.incomeCategories.map { it.first }
    TransactionType.TRANSFER -> FinanceCategories.transferCategories.map { it.first }
  }

  var selectedCategory by remember(selectedType, editingTransaction) {
    val initial = editingTransaction?.category
    mutableStateOf(
      if (initial != null && initial in categories) initial
      else categories.firstOrNull() ?: "기타"
    )
  }

  // Automatically apply Gemini AI recommended category when suggestion succeeds
  LaunchedEffect(categorySuggestionState) {
    if (categorySuggestionState is CategorySuggestionUiState.Success) {
      val rec = categorySuggestionState.recommendation
      if (rec.recommendedCategory in categories) {
        selectedCategory = rec.recommendedCategory
      }
      selectedPaymentMethod = rec.suggestedPaymentMethod
    }
  }

  val quickTemplates = remember(selectedType) {
    when (selectedType) {
      TransactionType.EXPENSE -> listOf(
        QuickTemplate("💵 현금 결제", 10000L, "식비", "자동연동 미지원 현금 결제", "현금"),
        QuickTemplate("👥 더치페이 N빵", 15000L, "식비", "모임 N빵 송금 정산", "계좌이체"),
        QuickTemplate("☕ 스타벅스", 4500L, "카페/간식", "스타벅스 아이스 아메리카노", "체크카드"),
        QuickTemplate("🍱 점심 식사", 10000L, "식비", "직장인 점심 식사", "체크카드"),
        QuickTemplate("🎁 모바일쿠폰 차액", 3200L, "카페/간식", "기프티콘 결제 차액", "간편결제"),
        QuickTemplate("🚇 대중교통", 1500L, "교통", "지하철/버스 교통카드", "체크카드"),
        QuickTemplate("🛒 편의점/마트", 8500L, "생활/마트", "편의점 생필품 구매", "체크카드")
      )
      TransactionType.INCOME -> listOf(
        QuickTemplate("👥 더치페이 회수", 15000L, "용돈", "모임 정산 금액 받음", "계좌이체"),
        QuickTemplate("🥕 당근 중고거래", 35000L, "부수입/중고거래", "당근마켓 현금/직거래 입금", "현금"),
        QuickTemplate("💼 월급 입금", 3500000L, "급여", "정기 급여 입금", "계좌이체"),
        QuickTemplate("🎁 축의금/용돈", 50000L, "용돈", "경조사비/용돈 수령", "현금"),
        QuickTemplate("📈 파킹통장 이자", 18500L, "금융소득/배당", "파킹통장 월 이자", "계좌이체")
      )
      TransactionType.TRANSFER -> listOf(
        QuickTemplate("🏦 비상금 저축", 300000L, "저축/투자이체", "세이프박스 비상금 이체", "계좌이체"),
        QuickTemplate("💳 카드대금 선결제", 200000L, "카드대금납부", "신용카드 사용금액 선결제", "계좌이체")
      )
    }
  }

  val paymentMethods = listOf("체크카드", "신용카드", "계좌이체", "현금", "간편결제")
  val activeColor = when (selectedType) {
    TransactionType.EXPENSE -> AccentRed
    TransactionType.INCOME -> AccentGreen
    TransactionType.TRANSFER -> TossBlue
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("transaction_form_screen")
  ) {
    // 1. Top App Bar
    Surface(
      color = Color.White,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("form_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = Gray900)
          }
          Column {
            Text(
              text = if (isEditMode) "내역 상세 및 수정" else "지출 · 수입 내역 입력",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = Gray900
            )
            Text(
              text = if (isEditMode) "내용을 수정하면 계좌 잔액도 자동 재계산됩니다" else "빠른 프리셋 또는 직접 입력으로 가계부를 기록하세요",
              style = MaterialTheme.typography.labelSmall,
              color = Gray600
            )
          }
        }

        if (isEditMode && editingTransaction != null) {
          IconButton(
            onClick = {
              onDeleteExisting(editingTransaction)
              onBack()
            },
            modifier = Modifier.testTag("form_delete_button")
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = "내역 삭제",
              tint = AccentRed
            )
          }
        }
      }
    }

    // 2. Scrollable Form Body
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Type Selector Tabs (지출 / 수입 / 이체)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        TabRow(
          selectedTabIndex = selectedType.ordinal,
          containerColor = Color.White,
          contentColor = activeColor,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
        ) {
          TransactionType.values().forEach { type ->
            val selected = selectedType == type
            val tabColor = when (type) {
              TransactionType.EXPENSE -> AccentRed
              TransactionType.INCOME -> AccentGreen
              TransactionType.TRANSFER -> TossBlue
            }
            Tab(
              selected = selected,
              onClick = { selectedType = type },
              modifier = Modifier.testTag("form_type_tab_${type.name.lowercase()}"),
              text = {
                Text(
                  text = type.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                  color = if (selected) tabColor else Gray600
                )
              }
            )
          }
        }
      }

      // Quick Presets Row (자주 쓰는 내역 원터치 자동입력)
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "원터치 자동입력",
                tint = TossBlue,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "원터치 빠른 입력 프리셋",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Gray900
              )
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = AccentOrange.copy(alpha = 0.12f)
            ) {
              Text(
                text = "자동 연동 보완",
                style = MaterialTheme.typography.labelSmall,
                color = AccentOrange,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            quickTemplates.forEachIndexed { idx, tpl ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(Gray100)
                  .clickable {
                    amountText = tpl.amount.toString()
                    selectedCategory = tpl.category
                    memoText = tpl.memo
                    selectedPaymentMethod = tpl.paymentMethod
                  }
                  .padding(horizontal = 12.dp, vertical = 8.dp)
                  .testTag("preset_chip_$idx")
              ) {
                Column {
                  Text(
                    text = tpl.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900
                  )
                  Text(
                    text = Formatters.formatCurrency(tpl.amount),
                    style = MaterialTheme.typography.labelSmall,
                    color = TossBlue
                  )
                }
              }
            }
          }
        }
      }

      // Amount Input Card with Korean Hangul unit preview
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${selectedType.title} 금액",
              style = MaterialTheme.typography.labelMedium,
              color = Gray600,
              fontWeight = FontWeight.SemiBold
            )
            val parsedAmount = amountText.toLongOrNull() ?: 0L
            Text(
              text = if (parsedAmount > 0) Formatters.formatKoreanUnitCurrency(parsedAmount) else "금액을 입력해주세요",
              style = MaterialTheme.typography.labelMedium,
              color = activeColor,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
              val digitsOnly = input.filter { it.isDigit() }
              if (digitsOnly.length <= 11) {
                amountText = digitsOnly
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("form_amount_input"),
            placeholder = { Text("0", color = Gray400, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            textStyle = MaterialTheme.typography.headlineSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              color = Gray900
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 10.dp)
              ) {
                if (amountText.isNotEmpty()) {
                  IconButton(
                    onClick = { amountText = "" },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Clear, contentDescription = "금액 지우기", tint = Gray400)
                  }
                }
                Text(
                  text = "원",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
              }
            }
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Fast Increment Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(
              1000L to "+1천",
              5000L to "+5천",
              10000L to "+1만",
              50000L to "+5만",
              100000L to "+10만"
            ).forEach { (inc, label) ->
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Gray100)
                  .clickable {
                    val current = amountText.toLongOrNull() ?: 0L
                    amountText = (current + inc).toString()
                  }
                  .padding(vertical = 8.dp)
                  .testTag("amount_add_$inc"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
              }
            }
          }
        }
      }

      // Memo, Gemini AI Category Suggestion & Date Card
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "거래처 / 메모 키워드",
              style = MaterialTheme.typography.labelMedium,
              color = Gray600,
              fontWeight = FontWeight.SemiBold
            )
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AccentPurpleLight)
                .clickable {
                  if (memoText.trim().length >= 2) {
                    onRequestCategorySuggestion(memoText, selectedType, 0L)
                  }
                }
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag("ai_suggest_category_button")
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Default.AutoAwesome,
                  contentDescription = "AI 카테고리 자동 추천",
                  tint = AccentPurple,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Gemini AI 카테고리 추천",
                  style = MaterialTheme.typography.labelSmall,
                  color = AccentPurple,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          OutlinedTextField(
            value = memoText,
            onValueChange = { newText ->
              memoText = newText
              onRequestCategorySuggestion(newText, selectedType, 800L)
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("form_memo_input"),
            placeholder = { Text("예: 카카오택시, 스타벅스, 올리브영, 약국 (입력 시 AI 자동 분류)", color = Gray400) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
              if (memoText.isNotEmpty()) {
                IconButton(
                  onClick = {
                    memoText = ""
                    onResetCategorySuggestion()
                  },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.Clear, contentDescription = "메모 지우기", tint = Gray400)
                }
              }
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Sample AI Keyword Test Chips
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "AI 분류 예시:",
              style = MaterialTheme.typography.labelSmall,
              color = Gray600
            )
            listOf(
              "카카오택시 야간호출",
              "스타벅스 리저브",
              "올리브영 스킨케어",
              "종로약국 감기약",
              "CGV 아이맥스 관람",
              "쿠팡 로켓프레시"
            ).forEach { sampleKeyword ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(Gray100)
                  .clickable {
                    memoText = sampleKeyword
                    onRequestCategorySuggestion(sampleKeyword, selectedType, 0L)
                  }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = sampleKeyword,
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray900
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Gemini AI Category Suggestion Result Panel
          when (val aiState = categorySuggestionState) {
            is CategorySuggestionUiState.Idle -> {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(Gray100.copy(alpha = 0.7f))
                  .padding(10.dp)
                  .testTag("ai_suggestion_idle_box")
              ) {
                Text(
                  text = "💡 거래처나 지출 내용을 입력하면 Gemini AI가 키워드를 분석하여 가장 알맞은 소비 카테고리를 자동 제안합니다.",
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray600
                )
              }
            }
            is CategorySuggestionUiState.Loading -> {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(AccentPurpleLight)
                  .padding(12.dp)
                  .testTag("ai_suggestion_loading_box"),
                verticalAlignment = Alignment.CenterVertically
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = AccentPurple
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Gemini AI가 '${aiState.keyword}' 키워드에 맞는 카테고리를 분석 중입니다...",
                  style = MaterialTheme.typography.labelMedium,
                  color = AccentPurple,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
            is CategorySuggestionUiState.Success -> {
              val rec = aiState.recommendation
              val recColor = Color(FinanceCategories.getColorForCategory(rec.recommendedCategory))
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .border(1.dp, AccentPurple.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                  .background(AccentPurpleLight.copy(alpha = 0.55f))
                  .padding(12.dp)
                  .testTag("ai_suggestion_success_box")
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.AutoAwesome,
                      contentDescription = "AI 추천 완료",
                      tint = AccentPurple,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Gemini AI 자동 분류 완료",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = AccentPurple
                    )
                  }
                  Text(
                    text = "정확도 ${rec.confidence}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentPurple,
                    fontWeight = FontWeight.Bold
                  )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  // Primary recommended category chip
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(10.dp))
                      .background(recColor)
                      .clickable { selectedCategory = rec.recommendedCategory }
                      .padding(horizontal = 12.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = "추천: ${rec.recommendedCategory} ✓",
                      style = MaterialTheme.typography.labelMedium,
                      color = Color.White,
                      fontWeight = FontWeight.Bold
                    )
                  }

                  // Optional 2nd choice category chip
                  if (!rec.alternativeCategory.isNullOrBlank()) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, Gray200, RoundedCornerShape(10.dp))
                        .clickable { selectedCategory = rec.alternativeCategory }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                      Text(
                        text = "대안: ${rec.alternativeCategory}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray900,
                        fontWeight = FontWeight.Medium
                      )
                    }
                  }

                  // Suggested payment method badge
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(10.dp))
                      .background(TossBlueContainer)
                      .clickable { selectedPaymentMethod = rec.suggestedPaymentMethod }
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = rec.suggestedPaymentMethod,
                      style = MaterialTheme.typography.labelSmall,
                      color = TossBlue,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                  text = rec.reason,
                  style = MaterialTheme.typography.bodySmall,
                  color = Gray900
                )
              }
            }
            is CategorySuggestionUiState.Error -> {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(AccentRed.copy(alpha = 0.08f))
                  .padding(12.dp)
                  .testTag("ai_suggestion_error_box"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = aiState.message,
                  style = MaterialTheme.typography.labelSmall,
                  color = AccentRed,
                  modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentRed)
                    .clickable {
                      onRequestCategorySuggestion(memoText, selectedType, 0L)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                  Text(
                    text = "재시도",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Material 3 DatePickerDialog
          if (showDatePickerDialog) {
            DatePickerDialog(
              onDismissRequest = { showDatePickerDialog = false },
              confirmButton = {
                TextButton(
                  onClick = {
                    datePickerState.selectedDateMillis?.let { pickedMillis ->
                      val pickedCal = Calendar.getInstance().apply { timeInMillis = pickedMillis }
                      val currentCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                      pickedCal.set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
                      pickedCal.set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
                      pickedCal.set(Calendar.SECOND, currentCal.get(Calendar.SECOND))
                      selectedTimestamp = pickedCal.timeInMillis
                    }
                    showDatePickerDialog = false
                  },
                  modifier = Modifier.testTag("date_picker_confirm_button")
                ) {
                  Text("선택 완료", color = TossBlue, fontWeight = FontWeight.Bold)
                }
              },
              dismissButton = {
                TextButton(
                  onClick = { showDatePickerDialog = false },
                  modifier = Modifier.testTag("date_picker_dismiss_button")
                ) {
                  Text("취소", color = Gray600)
                }
              }
            ) {
              DatePicker(
                state = datePickerState,
                modifier = Modifier.testTag("material3_date_picker")
              )
            }
          }

          // Date selection row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.CalendarToday,
                contentDescription = "거래 일시",
                tint = TossBlue,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Column {
                Text(
                  text = "거래 일시 (자동 연동 보완)",
                  style = MaterialTheme.typography.labelMedium,
                  color = Gray700,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "현금·미연동 지출 날짜 직접 지정",
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray500
                )
              }
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = TossBlue.copy(alpha = 0.1f),
              modifier = Modifier.clickable { showDatePickerDialog = true }
            ) {
              Text(
                text = "${Formatters.formatDayHeader(selectedTimestamp)} ${Formatters.formatTime(selectedTimestamp)}",
                style = MaterialTheme.typography.labelMedium,
                color = TossBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Quick Date Chips & Direct Calendar Picker Trigger
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val targetCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
            val isSameDay = { daysAgo: Int ->
              val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
              c.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                c.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
            }

            listOf(
              0 to "오늘",
              1 to "어제",
              2 to "그제"
            ).forEach { (offset, label) ->
              val isSelected = isSameDay(offset)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) TossBlue else Gray100)
                  .clickable {
                    val cal = Calendar.getInstance().apply {
                      add(Calendar.DAY_OF_YEAR, -offset)
                      set(Calendar.HOUR_OF_DAY, targetCal.get(Calendar.HOUR_OF_DAY))
                      set(Calendar.MINUTE, targetCal.get(Calendar.MINUTE))
                    }
                    selectedTimestamp = cal.timeInMillis
                  }
                  .padding(vertical = 8.dp)
                  .testTag("date_chip_$offset"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isSelected) Color.White else Gray900,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
              }
            }

            val isCustomDate = !isSameDay(0) && !isSameDay(1) && !isSameDay(2)
            Box(
              modifier = Modifier
                .weight(1.3f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isCustomDate) TossBlue else Gray100)
                .clickable { showDatePickerDialog = true }
                .padding(vertical = 8.dp)
                .testTag("open_date_picker_button"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CalendarMonth,
                  contentDescription = "달력 직접 선택",
                  tint = if (isCustomDate) Color.White else TossBlue,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (isCustomDate) "달력 선택됨" else "달력 선택 📅",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isCustomDate) Color.White else Gray900,
                  fontWeight = if (isCustomDate) FontWeight.Bold else FontWeight.Medium
                )
              }
            }
          }
        }
      }

      // Category Selection Card with CategoryIconHelper
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "카테고리 선택",
                style = MaterialTheme.typography.titleSmall,
                color = Gray900,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "(${categories.size}개)",
                style = MaterialTheme.typography.labelSmall,
                color = Gray500
              )
            }
            Text(
              text = "선택: $selectedCategory",
              style = MaterialTheme.typography.labelMedium,
              color = Color(CategoryIconHelper.getColorForCategory(selectedCategory)),
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            categories.forEach { cat ->
              val isSelected = selectedCategory == cat
              val catColor = Color(CategoryIconHelper.getColorForCategory(cat))
              val catIcon = CategoryIconHelper.getIconForCategory(cat)

              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) catColor.copy(alpha = 0.15f) else Gray100,
                border = androidx.compose.foundation.BorderStroke(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color = if (isSelected) catColor else Color.Transparent
                ),
                modifier = Modifier
                  .clickable { selectedCategory = cat }
                  .testTag("form_category_$cat")
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(24.dp)
                      .clip(CircleShape)
                      .background(if (isSelected) catColor else Gray200),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = catIcon,
                      contentDescription = cat,
                      tint = if (isSelected) Color.White else Gray700,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = cat,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) catColor else Gray800,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                  )
                }
              }
            }
          }
        }
      }

      // Account & Payment Method Card
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = if (selectedType == TransactionType.TRANSFER) "출금 계좌 선택" else "연동 계좌 / 카드 선택",
            style = MaterialTheme.typography.labelMedium,
            color = Gray600,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { acc ->
              val isSelected = selectedAccount?.id == acc.id
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) TossBlue else Gray200,
                    shape = RoundedCornerShape(12.dp)
                  )
                  .background(if (isSelected) TossBlue.copy(alpha = 0.06f) else Color.White)
                  .clickable { selectedAccount = acc }
                  .padding(horizontal = 12.dp, vertical = 10.dp)
                  .testTag("form_account_${acc.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .clip(CircleShape)
                      .background(Color(acc.colorHex))
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(
                      text = acc.name,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      color = Gray900
                    )
                    Text(
                      text = "${acc.bankName} · 잔액 ${Formatters.formatCurrency(acc.balance)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = Gray600
                    )
                  }
                }
                if (isSelected) {
                  Icon(
                    Icons.Default.Check,
                    contentDescription = "선택됨",
                    tint = TossBlue,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }

          if (selectedType == TransactionType.TRANSFER) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = "입금 계좌 선택",
              style = MaterialTheme.typography.labelMedium,
              color = Gray600,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = "결제 수단",
              style = MaterialTheme.typography.labelMedium,
              color = Gray600,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              paymentMethods.forEach { method ->
                val isSelected = selectedPaymentMethod == method
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) TossBlue else Gray100)
                    .clickable { selectedPaymentMethod = method }
                    .padding(vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = method,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White else Gray900,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                  )
                }
              }
            }
          }

          if (selectedType == TransactionType.EXPENSE) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "이번 달 예산에서 제외",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = Gray900
                )
                Text(
                  text = "경조사비, 회사 대납금 등 예외 지출일 때 켜주세요",
                  style = MaterialTheme.typography.labelSmall,
                  color = Gray600
                )
              }
              Switch(
                checked = isExcludedFromBudget,
                onCheckedChange = { isExcludedFromBudget = it },
                modifier = Modifier.testTag("form_budget_exclude_switch")
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }

    // 3. Bottom Sticky Action Bar
    val currentAmount = amountText.toLongOrNull() ?: 0L
    val isFormValid = currentAmount > 0L && selectedAccount != null

    Surface(
      color = Color.White,
      tonalElevation = 8.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (!isEditMode) {
          // Save & Continue Entering Button
          OutlinedButton(
            onClick = {
              val acc = selectedAccount ?: return@OutlinedButton
              onSaveNew(
                selectedType,
                currentAmount,
                selectedCategory,
                acc,
                if (selectedType == TransactionType.TRANSFER) selectedToAccount else null,
                selectedTimestamp,
                memoText,
                selectedPaymentMethod,
                isExcludedFromBudget,
                false // Keep form open for rapid consecutive entry
              )
              amountText = ""
              memoText = ""
            },
            enabled = isFormValid,
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("form_save_and_continue_button"),
            shape = RoundedCornerShape(14.dp)
          ) {
            Text(
              text = "저장 후 계속 입력",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = if (isFormValid) TossBlue else Gray400
            )
          }
        }

        // Primary Save / Update Button
        Button(
          onClick = {
            val acc = selectedAccount ?: return@Button
            if (isEditMode && editingTransaction != null) {
              onUpdateExisting(
                editingTransaction.id,
                selectedType,
                currentAmount,
                selectedCategory,
                acc,
                if (selectedType == TransactionType.TRANSFER) selectedToAccount else null,
                selectedTimestamp,
                memoText,
                selectedPaymentMethod,
                isExcludedFromBudget
              )
              onBack()
            } else {
              onSaveNew(
                selectedType,
                currentAmount,
                selectedCategory,
                acc,
                if (selectedType == TransactionType.TRANSFER) selectedToAccount else null,
                selectedTimestamp,
                memoText,
                selectedPaymentMethod,
                isExcludedFromBudget,
                true
              )
            }
          },
          enabled = isFormValid,
          modifier = Modifier
            .weight(1.3f)
            .height(52.dp)
            .testTag("form_submit_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = activeColor,
            contentColor = Color.White
          )
        ) {
          Text(
            text = if (isEditMode) "수정 내용 저장" else "${selectedType.title} 내역 저장하기",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
