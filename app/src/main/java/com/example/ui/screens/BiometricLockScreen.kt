package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentRed
import com.example.ui.theme.Gray100
import com.example.ui.theme.Gray200
import com.example.ui.theme.Gray400
import com.example.ui.theme.Gray600
import com.example.ui.theme.Gray800
import com.example.ui.theme.Gray900
import com.example.ui.theme.TossBlue
import com.example.util.BiometricAuthHelper
import com.example.util.BiometricStatus

@Composable
fun BiometricLockScreen(
  onUnlockSuccess: () -> Unit,
  onVerifyPin: (String) -> Boolean,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity

  val biometricStatus = remember {
    BiometricAuthHelper.checkBiometricAvailability(context)
  }

  var isPinMode by remember { mutableStateOf(!biometricStatus.isAvailable) }
  var pinInput by remember { mutableStateOf("") }
  var pinErrorMsg by remember { mutableStateOf<String?>(null) }
  var authFeedbackMsg by remember { mutableStateOf<String?>(null) }

  fun triggerBiometricPrompt() {
    if (activity != null && biometricStatus.isAvailable) {
      authFeedbackMsg = null
      BiometricAuthHelper.showBiometricPrompt(
        activity = activity,
        title = "머니플로우 금융 보안 인증",
        subtitle = "지문 또는 얼굴 인식으로 잠금을 해제하세요",
        description = "안전한 가계부 금융 데이터 조회를 위해 본인 인증이 필요합니다",
        negativeButtonText = "간편 비밀번호 입력",
        onSuccess = {
          onUnlockSuccess()
        },
        onError = { errorCode, errString ->
          authFeedbackMsg = errString.toString()
          if (errorCode == 13 || errorCode == 10) { // Negative button clicked or canceled
            isPinMode = true
          }
        },
        onFailed = {
          authFeedbackMsg = "생체 인식에 실패했습니다. 다시 시도해주세요."
        }
      )
    } else {
      isPinMode = true
    }
  }

  // Auto-launch BiometricPrompt on screen entry if biometric hardware is ready
  LaunchedEffect(Unit) {
    if (biometricStatus.isAvailable) {
      triggerBiometricPrompt()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .testTag("biometric_lock_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // 1. Header & App Branding
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 20.dp)
      ) {
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                colors = listOf(TossBlue.copy(alpha = 0.2f), TossBlue.copy(alpha = 0.05f))
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isPinMode) Icons.Default.Pin else Icons.Default.Fingerprint,
            contentDescription = "보안 인증",
            tint = TossBlue,
            modifier = Modifier.size(44.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "머니플로우 금융 보안",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Black,
          color = Gray900
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = if (isPinMode) {
            "설정된 4자리 간편 비밀번호를 입력해주세요"
          } else {
            "소중한 자산 및 가계부 내역 보호를 위해\n생체 인증(지문/얼굴)으로 잠금을 해제하세요"
          },
          style = MaterialTheme.typography.bodyMedium,
          color = Gray600,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Biometric sensor availability status badge
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (biometricStatus.isAvailable) AccentGreen.copy(alpha = 0.12f) else AccentOrange.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = if (biometricStatus.isAvailable) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (biometricStatus.isAvailable) AccentGreen else AccentOrange,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = biometricStatus.description,
            style = MaterialTheme.typography.labelSmall,
            color = if (biometricStatus.isAvailable) AccentGreen else AccentOrange,
            fontWeight = FontWeight.SemiBold
          )
        }

        if (authFeedbackMsg != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = authFeedbackMsg!!,
            style = MaterialTheme.typography.bodySmall,
            color = AccentRed,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
          )
        }
      }

      // 2. Middle Interaction: Biometric Prompt button OR PIN dots & keypad
      if (!isPinMode) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(vertical = 24.dp)
        ) {
          Card(
            modifier = Modifier
              .size(120.dp)
              .clip(CircleShape)
              .clickable { triggerBiometricPrompt() }
              .testTag("biometric_auth_button"),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = TossBlue),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
          ) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.Fingerprint,
                contentDescription = "생체 인증 실행",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          Text(
            text = "아이콘을 터치하여 지문/얼굴 인식",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TossBlue
          )
          Text(
            text = "Android BiometricPrompt API 활성화됨",
            style = MaterialTheme.typography.labelSmall,
            color = Gray600
          )
        }
      } else {
        // PIN Entry Mode
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth()
        ) {
          // 4 Dots
          Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            for (i in 0 until 4) {
              val isFilled = i < pinInput.length
              Box(
                modifier = Modifier
                  .size(18.dp)
                  .clip(CircleShape)
                  .background(if (isFilled) TossBlue else Gray200)
                  .border(2.dp, if (isFilled) TossBlue else Gray400, CircleShape)
              )
            }
          }

          if (pinErrorMsg != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = pinErrorMsg!!,
              style = MaterialTheme.typography.bodySmall,
              color = AccentRed,
              fontWeight = FontWeight.Bold
            )
          } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "초기 비밀번호: 1234",
              style = MaterialTheme.typography.labelSmall,
              color = Gray400
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // 3x4 Keypad
          PinKeypad(
            onDigitClick = { digit ->
              if (pinInput.length < 4) {
                pinInput += digit
                pinErrorMsg = null
                if (pinInput.length == 4) {
                  val isValid = onVerifyPin(pinInput)
                  if (isValid) {
                    onUnlockSuccess()
                  } else {
                    pinErrorMsg = "비밀번호가 일치하지 않습니다 (기본: 1234)"
                    pinInput = ""
                  }
                }
              }
            },
            onDeleteClick = {
              if (pinInput.isNotEmpty()) {
                pinInput = pinInput.dropLast(1)
                pinErrorMsg = null
              }
            }
          )
        }
      }

      // 3. Bottom Action Toggle
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (!isPinMode) {
          OutlinedButton(
            onClick = { isPinMode = true },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("fallback_pin_button"),
            shape = RoundedCornerShape(14.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Default.Pin, contentDescription = null, tint = TossBlue, modifier = Modifier.size(18.dp))
              Text("간편 비밀번호(PIN)로 해제", fontWeight = FontWeight.Bold, color = TossBlue)
            }
          }
        } else if (biometricStatus.isAvailable) {
          Button(
            onClick = {
              isPinMode = false
              triggerBiometricPrompt()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("switch_to_biometric_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TossBlue)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
              Text("생체 인증으로 돌아가기", fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PinKeypad(
  onDigitClick: (String) -> Unit,
  onDeleteClick: () -> Unit
) {
  val rows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "DEL")
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    rows.forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        row.forEach { item ->
          when (item) {
            "" -> {
              Spacer(modifier = Modifier.size(64.dp))
            }
            "DEL" -> {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .clickable { onDeleteClick() }
                  .testTag("keypad_del"),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Backspace, contentDescription = "삭제", tint = Gray800, modifier = Modifier.size(24.dp))
              }
            }
            else -> {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(Gray100)
                  .clickable { onDigitClick(item) }
                  .testTag("keypad_$item"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = item,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = Gray900
                )
              }
            }
          }
        }
      }
    }
  }
}
