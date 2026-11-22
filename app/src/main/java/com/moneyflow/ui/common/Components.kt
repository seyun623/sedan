package com.moneyflow.ui.common

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue

/** Shows digits with thousands separators ("1234567" -> "1,234,567") while editing raw digits. */
object ThousandsSeparatorTransformation : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val digits = text.text
    if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
    val formatted = buildString {
      digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) append(',')
        append(c)
      }
    }
    val mapping = object : OffsetMapping {
      override fun originalToTransformed(offset: Int): Int {
        val commasBefore = (0 until offset).count { i -> i > 0 && (digits.length - i) % 3 == 0 }
        return offset + commasBefore
      }

      override fun transformedToOriginal(offset: Int): Int =
        formatted.take(offset.coerceAtMost(formatted.length)).count { it != ',' }
    }
    return TransformedText(AnnotatedString(formatted), mapping)
  }
}

/** Keeps only digits, without leading zeros, capped at 13 digits (< 10조). */
fun sanitizeAmountInput(input: String): String =
  input.filter { it.isDigit() }.trimStart('0').take(13)

/**
 * KRW amount input. [value] holds raw digits ("" or "15000"); it is displayed as "15,000".
 */
@Composable
fun KrwAmountField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String = "금액",
  placeholder: String = "0",
  isError: Boolean = false,
  supportingText: String? = null
) {
  OutlinedTextField(
    value = value,
    onValueChange = { onValueChange(sanitizeAmountInput(it)) },
    modifier = modifier.fillMaxWidth(),
    label = { Text(label) },
    placeholder = { Text(placeholder) },
    suffix = { Text("원") },
    singleLine = true,
    isError = isError,
    supportingText = supportingText?.let { { Text(it) } },
    visualTransformation = ThousandsSeparatorTransformation,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
  )
}

/** A confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  destructive: Boolean = true,
  dismissLabel: String = "취소"
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, fontWeight = FontWeight.Bold) },
    text = { Text(message) },
    confirmButton = {
      TextButton(onClick = { onConfirm(); onDismiss() }) {
        Text(confirmLabel, color = if (destructive) AccentRed else TossBlue, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
  )
}

/** Empty-state block with optional call to action. */
@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  description: String? = null,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier.size(56.dp).background(Gray100, CircleShape),
      contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = Gray500, modifier = Modifier.size(28.dp)) }
    Spacer(Modifier.height(12.dp))
    Text(title, fontWeight = FontWeight.Bold, color = Gray900, textAlign = TextAlign.Center)
    if (description != null) {
      Spacer(Modifier.height(4.dp))
      Text(description, color = Gray600, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onAction,
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
        shape = RoundedCornerShape(12.dp)
      ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
    }
  }
}

/** "< 2026년 11월 >" month switcher with 48dp touch targets. */
@Composable
fun MonthSwitcher(
  label: String,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
  showToday: Boolean = false,
  onToday: () -> Unit = {}
) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onPrevious) {
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = Gray900)
    }
    Text(label, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Gray900, maxLines = 1)
    IconButton(onClick = onNext) {
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = Gray900)
    }
    if (showToday) {
      TextButton(onClick = onToday) { Text("이번 달", color = TossBlue, fontWeight = FontWeight.Bold) }
    }
  }
}

/** Numeric keypad used by the lock screen and PIN setup. */
@Composable
fun PinPad(
  onDigit: (Char) -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf(' ', '0', '<'))
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    rows.forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        row.forEach { key ->
          Box(
            modifier = Modifier
              .sizeIn(minWidth = 72.dp, minHeight = 56.dp)
              .then(
                when {
                  key == ' ' -> Modifier
                  key == '<' -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = "지우기" }
                  else -> Modifier.clickable(enabled = enabled, role = Role.Button) { onDigit(key) }
                }
              ),
            contentAlignment = Alignment.Center
          ) {
            when (key) {
              ' ' -> Unit
              '<' -> Icon(Icons.Outlined.Backspace, contentDescription = null, tint = Gray900)
              else -> Text(key.toString(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            }
          }
        }
      }
    }
  }
}

/** Row of PIN dots. */
@Composable
fun PinDots(length: Int, filled: Int, modifier: Modifier = Modifier, error: Boolean = false) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    repeat(length) { i ->
      Box(
        Modifier.size(14.dp).background(
          color = when {
            error -> AccentRed
            i < filled -> TossBlue
            else -> Gray100
          },
          shape = CircleShape
        )
      )
    }
  }
}
