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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.ui.theme.Gray100
import com.example.ui.theme.Gray400
import com.example.ui.theme.Gray600
import com.example.ui.theme.Gray900
import com.example.ui.theme.TossBlue
import java.util.Calendar

@Composable
fun AddGoalDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, targetAmount: Long, currentAmount: Long, targetDate: Long, iconEmoji: String) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var targetAmountText by remember { mutableStateOf("") }
  var currentAmountText by remember { mutableStateOf("") }
  var selectedEmoji by remember { mutableStateOf("🎯") }

  val emojis = listOf("🎯", "✈️", "🚗", "💻", "💍", "🏠", "🛡️", "💰")

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface)
        .testTag("add_goal_dialog"),
      tonalElevation = 6.dp
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "저축 목표 만들기",
            style = MaterialTheme.typography.titleLarge,
            color = Gray900
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Gray600)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Emoji icons
        Text(text = "아이콘 선택", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          emojis.forEach { emoji ->
            val isSelected = selectedEmoji == emoji
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) TossBlue.copy(alpha = 0.15f) else Gray100)
                .clickable { selectedEmoji = emoji },
              contentAlignment = Alignment.Center
            ) {
              Text(text = emoji, style = MaterialTheme.typography.titleMedium)
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title
        Text(text = "목표 이름", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("goal_title_input"),
          placeholder = { Text("예: 2027년 내 집 마련, 신차 구매", color = Gray400) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Target Amount
        Text(text = "목표 금액", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = targetAmountText,
          onValueChange = { input ->
            if (input.all { it.isDigit() } && input.length <= 12) targetAmountText = input
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("goal_target_amount_input"),
          placeholder = { Text("0원", color = Gray400) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          suffix = { Text("원", fontWeight = FontWeight.Bold, color = Gray900) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Current Saved Amount
        Text(text = "현재 모은 금액", style = MaterialTheme.typography.labelMedium, color = Gray600)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = currentAmountText,
          onValueChange = { input ->
            if (input.all { it.isDigit() } && input.length <= 12) currentAmountText = input
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("goal_current_amount_input"),
          placeholder = { Text("0원 (없으면 0)", color = Gray400) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          suffix = { Text("원", fontWeight = FontWeight.Bold, color = Gray900) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        val targetAmt = targetAmountText.toLongOrNull() ?: 0L
        val canSubmit = title.isNotBlank() && targetAmt > 0
        Button(
          onClick = {
            val currAmt = currentAmountText.toLongOrNull() ?: 0L
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, 6) }
            onConfirm(title, targetAmt, currAmt, cal.timeInMillis, selectedEmoji)
            onDismiss()
          },
          enabled = canSubmit,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("submit_goal_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = TossBlue,
            contentColor = Color.White
          )
        ) {
          Text(
            text = "목표 시작하기",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
