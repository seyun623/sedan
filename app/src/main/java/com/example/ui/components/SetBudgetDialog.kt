package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FinanceCategories
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentRed
import com.example.ui.theme.Gray100
import com.example.ui.theme.Gray200
import com.example.ui.theme.Gray400
import com.example.ui.theme.Gray500
import com.example.ui.theme.Gray600
import com.example.ui.theme.Gray700
import com.example.ui.theme.Gray800
import com.example.ui.theme.Gray900
import com.example.ui.theme.TossBlue
import com.example.util.Formatters
import java.util.Calendar

/**
 * High-polish budget-setting dialog allowing users to define a monthly spending limit
 * and category-specific allowances with quick preset chips and daily spending recommendation.
 */
@Composable
fun SetBudgetDialog(
  currentTotalBudget: Long,
  currentCategoryBudgets: Map<String, Long>,
  monthDisplay: String,
  onDismiss: () -> Unit,
  onConfirm: (totalBudget: Long, categoryLimits: Map<String, Long>) -> Unit
) {
  var totalBudgetText by remember {
    mutableStateOf(if (currentTotalBudget > 0) currentTotalBudget.toString() else "2000000")
  }

  val categoryBudgetsState = remember {
    mutableStateMapOf<String, String>().apply {
      FinanceCategories.expenseCategories.forEach { (cat, _) ->
        val existing = currentCategoryBudgets[cat] ?: 0L
        put(cat, if (existing > 0) existing.toString() else "")
      }
    }
  }

  val parsedTotal = totalBudgetText.toLongOrNull() ?: 0L

  // Days remaining in the current month calculation
  val (daysInMonth, daysRemaining) = remember {
    val cal = Calendar.getInstance()
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val remaining = (totalDays - today + 1).coerceAtLeast(1)
    totalDays to remaining
  }

  val dailyRecommended = if (parsedTotal > 0) parsedTotal / daysRemaining else 0L

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = Color.White,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .testTag("set_budget_dialog"),
      tonalElevation = 6.dp
    ) {
      Column(modifier = Modifier.padding(22.dp)) {
        // 1. Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "$monthDisplay 지출 한도 설정",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Black,
              color = Gray900
            )
            Text(
              text = "월간 총 예산 및 일일 권장 지출액을 관리하세요",
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
              .testTag("close_budget_dialog_btn")
          ) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray700, modifier = Modifier.size(18.dp))
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Total Monthly Budget Input & Korean formatting preview
        Text(
          text = "이번 달 총 지출 한도",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = Gray800
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
          value = totalBudgetText,
          onValueChange = { input ->
            val digits = input.filter { it.isDigit() }
            if (digits.length <= 11) {
              totalBudgetText = digits
            }
          },
          placeholder = { Text("예: 2,000,000", color = Gray400) },
          trailingIcon = {
            Text(
              text = "원",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = TossBlue,
              modifier = Modifier.padding(end = 12.dp)
            )
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TossBlue,
            unfocusedBorderColor = Gray200
          ),
          textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Black,
            color = Gray900
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("total_budget_input")
        )

        // Korean readable amount preview
        if (parsedTotal > 0) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "약 ${Formatters.formatKoreanUnitCurrency(parsedTotal)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = TossBlue
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Quick Preset Chips (100만, 150만, 200만, 250만, 300만)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val presets = listOf(
            1_000_000L to "100만",
            1_500_000L to "150만",
            2_000_000L to "200만",
            2_500_000L to "250만",
            3_000_000L to "300만"
          )
          presets.forEach { (amt, label) ->
            val isSelected = parsedTotal == amt
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) TossBlue else Gray100)
                .clickable { totalBudgetText = amt.toString() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("preset_budget_btn_$amt"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White else Gray800,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
              )
            }
          }

          // Increment chips: +10만, +50만
          val increments = listOf(100_000L to "+10만", 500_000L to "+50만")
          increments.forEach { (delta, label) ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Gray100)
                .clickable {
                  val curr = totalBudgetText.toLongOrNull() ?: 0L
                  totalBudgetText = (curr + delta).toString()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Gray800,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Daily Spending Allowance Insight Card
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Gray100.copy(alpha = 0.8f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "하루 권장 지출액",
                style = MaterialTheme.typography.labelSmall,
                color = Gray600
              )
              Text(
                text = "이번 달 남은 ${daysRemaining}일 기준",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Gray500
              )
            }

            Text(
              text = "약 ${Formatters.formatCurrency(dailyRecommended)}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = if (parsedTotal > 0) TossBlue else Gray600
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Category-specific Budget Limits Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "카테고리별 지출 한도 (선택)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Gray800
          )

          // Auto-distribute helper button
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(TossBlue.copy(alpha = 0.08f))
              .clickable {
                if (parsedTotal > 0) {
                  // Recommend distribution: 식비 30%, 생활/마트 15%, 카페 10%, 쇼핑 15%, 교통 10%, 주거/통신 15%
                  categoryBudgetsState["식비"] = (parsedTotal * 0.30).toLong().toString()
                  categoryBudgetsState["생활/마트"] = (parsedTotal * 0.15).toLong().toString()
                  categoryBudgetsState["카페/간식"] = (parsedTotal * 0.10).toLong().toString()
                  categoryBudgetsState["쇼핑"] = (parsedTotal * 0.15).toLong().toString()
                  categoryBudgetsState["교통"] = (parsedTotal * 0.10).toLong().toString()
                  categoryBudgetsState["주거/통신"] = (parsedTotal * 0.15).toLong().toString()
                }
              }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("auto_allocate_budget_btn")
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TossBlue, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "자동 추천 분배",
              style = MaterialTheme.typography.labelSmall,
              fontSize = 11.sp,
              color = TossBlue,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category Budget Inputs List
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(FinanceCategories.expenseCategories) { (cat, _) ->
            val icon = CategoryIconHelper.getIconForCategory(cat)
            val catColor = Color(CategoryIconHelper.getColorForCategory(cat))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Gray100.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = cat,
                    tint = catColor,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = cat,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = Gray900
                )
              }

              Column(horizontalAlignment = Alignment.End) {
                OutlinedTextField(
                  value = categoryBudgetsState[cat] ?: "",
                  onValueChange = { newText ->
                    categoryBudgetsState[cat] = newText.filter { ch -> ch.isDigit() }
                  },
                  placeholder = { Text("한도 금액", fontSize = 12.sp, color = Gray400) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  shape = RoundedCornerShape(8.dp),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TossBlue,
                    unfocusedBorderColor = Gray200
                  ),
                  textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Gray900
                  ),
                  modifier = Modifier
                    .width(135.dp)
                    .testTag("cat_budget_input_$cat")
                )

                // Quick increment chips: +5만, +10만, 해제
                Row(
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier.padding(top = 3.dp)
                ) {
                  listOf(50000L to "+5만", 100000L to "+10만").forEach { (delta, lbl) ->
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Gray200.copy(alpha = 0.7f))
                        .clickable {
                          val curr = categoryBudgetsState[cat]?.toLongOrNull() ?: 0L
                          categoryBudgetsState[cat] = (curr + delta).toString()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = lbl,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Gray700
                      )
                    }
                  }
                  if (!categoryBudgetsState[cat].isNullOrEmpty()) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentRed.copy(alpha = 0.1f))
                        .clickable { categoryBudgetsState[cat] = "" }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = "해제",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = AccentRed
                      )
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 6. Save Button
        Button(
          onClick = {
            val total = if (parsedTotal > 0) parsedTotal else currentTotalBudget
            val resultMap = mutableMapOf<String, Long>()
            categoryBudgetsState.forEach { (cat, textVal) ->
              val amt = textVal.toLongOrNull()
              if (amt != null && amt > 0) {
                resultMap[cat] = amt
              }
            }
            onConfirm(total, resultMap)
          },
          enabled = parsedTotal > 0L,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("save_budget_btn"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TossBlue)
        ) {
          Text(
            text = if (parsedTotal > 0) "${Formatters.formatCurrency(parsedTotal)} 한도 설정 완료" else "한도 금액을 입력해주세요",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
      }
    }
  }
}
