package com.moneyflow.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import com.moneyflow.ai.AiProvider
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyflow.ui.common.ConfirmDialog
import com.moneyflow.ui.theme.AccentGreen
import com.moneyflow.ui.theme.AccentRed
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue

/** AI settings: choose a provider, register its API key, pick a model. */
@Composable
fun AiSettingsScreen(
  onBack: () -> Unit,
  viewModel: AiSettingsViewModel = hiltViewModel()
) {
  val context = LocalContext.current
  val s by viewModel.settings.collectAsStateWithLifecycle()
  val check by viewModel.check.collectAsStateWithLifecycle()
  // The key typed by the user is kept only in memory (never in saved state).
  var keyInput by remember { mutableStateOf("") }
  var urlInput by rememberSaveable(s.aiCustomBaseUrl) { mutableStateOf(s.aiCustomBaseUrl) }
  var modelQuery by rememberSaveable { mutableStateOf("") }
  var freeOnly by rememberSaveable { mutableStateOf(true) }
  val provider = s.aiProvider
  fun openUrl(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
  var showConsent by rememberSaveable { mutableStateOf(false) }
  var confirmRemove by rememberSaveable { mutableStateOf(false) }

  SettingsScaffold(title = "AI 설정", onBack = onBack) {
    if (check.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TossBlue)

    SettingsSection("AI 카테고리 추천") {
      SettingsSwitchItem(
        title = "AI 추천 사용",
        subtitle = if (s.hasAiKey) "거래 입력 화면에서 메모를 보고 카테고리를 추천해요" else "먼저 아래에서 API 키를 등록해 주세요",
        checked = s.aiEnabled && s.hasAiKey,
        enabled = s.hasAiKey,
        icon = Icons.Filled.AutoAwesome,
        onCheckedChange = { on -> if (on) showConsent = true else viewModel.setEnabled(false) },
        modifier = Modifier.testTag("ai_enabled_switch")
      )
    }

    SettingsSection("AI 서비스 선택") {
      AiProvider.entries.forEach { p ->
        SettingsRadioItem(
          title = p.label + if (s.aiKeysEncrypted.containsKey(p)) " · 키 등록됨" else "",
          subtitle = p.description,
          selected = p == provider,
          onSelect = { if (p != provider) { keyInput = ""; modelQuery = ""; viewModel.selectProvider(p) } }
        )
      }
    }

    SettingsSection("${provider.label} API 키") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (provider == AiProvider.CUSTOM) {
          OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it.trim() },
            label = { Text("API 주소 (예: https://내서버/v1)") },
            singleLine = true,
            isError = urlInput.isNotEmpty() && !urlInput.startsWith("https://"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("ai_url_input")
          )
          Spacer(Modifier.height(8.dp))
        }
        Text(
          if (s.hasAiKey) "API 키가 등록되어 있어요 (기기에 암호화되어 저장)" else "${provider.label}에서 발급한 API 키를 입력하세요.",
          style = MaterialTheme.typography.bodyMedium,
          color = if (s.hasAiKey) AccentGreen else Gray600
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = keyInput,
          onValueChange = { keyInput = it.trim() },
          label = { Text(if (s.hasAiKey) "새 API 키로 바꾸기" else "API 키") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          isError = check.error != null,
          supportingText = check.error?.let { { Text(it) } },
          modifier = Modifier.fillMaxWidth().testTag("ai_key_input")
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Button(
            onClick = { viewModel.saveKey(keyInput, urlInput); keyInput = "" },
            enabled = keyInput.length >= 10 && !check.busy,
            colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = OnBrand),
            modifier = Modifier.testTag("ai_key_save")
          ) { Text("확인 후 저장", fontWeight = FontWeight.Bold) }
          Spacer(Modifier.weight(1f))
          provider.keyUrl?.let { url -> TextButton(onClick = { openUrl(url) }) { Text("키 발급하러 가기") } }
        }
      }
      if (s.hasAiKey) {
        SettingsItem(
          title = "연결 테스트 / 모델 목록 새로고침",
          icon = Icons.Filled.Refresh,
          enabled = !check.busy,
          showChevron = false,
          onClick = viewModel::refreshModels
        )
        SettingsItem(
          title = "API 키 삭제",
          icon = Icons.Filled.Delete,
          iconTint = AccentRed,
          titleColor = AccentRed,
          showChevron = false,
          onClick = { confirmRemove = true }
        )
      }
    }

    if (s.hasAiKey) {
      SettingsSection("모델 · 현재 ${s.aiModel}") {
        if (check.models.size > 8) {
          OutlinedTextField(
            value = modelQuery,
            onValueChange = { modelQuery = it },
            label = { Text("모델 검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
          )
          if (provider != AiProvider.GEMINI) {
            SettingsSwitchItem(title = "무료 모델만 보기 (:free)", checked = freeOnly, onCheckedChange = { freeOnly = it })
          }
        }
        val models = check.models.ifEmpty { listOf(s.aiModel) }
          .filter { modelQuery.isBlank() || it.contains(modelQuery, ignoreCase = true) }
          .filter { !freeOnly || provider == AiProvider.GEMINI || check.models.size <= 8 || it.endsWith(":free") || it == s.aiModel }
          .take(40)
        models.forEach { m ->
          SettingsRadioItem(title = m, selected = m == s.aiModel, onSelect = { viewModel.setModel(m) })
        }
        if (check.models.isEmpty()) {
          Text(
            "\"연결 테스트\"를 누르면 이 키로 쓸 수 있는 모델 목록이 나와요.",
            style = MaterialTheme.typography.bodySmall,
            color = Gray600,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )
        }
      }
    }

    SettingsSection("알아 두세요") {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(
          "AI 추천을 쓰면 입력한 메모(예: '스타벅스')와 카테고리 목록이 선택한 AI 서비스(${provider.label})로 전송돼요. 금액·계좌·결제 문자 내용은 보내지 않아요.",
          "사용량을 아끼기 위해 예전에 고른 카테고리나 자주 쓰는 가맹점 규칙으로 알 수 있으면 AI를 부르지 않아요.",
          "무료 사용량과 요금 정책은 각 서비스가 정하고 바뀔 수 있어요.",
          "API 키는 서비스별로 이 휴대폰에만 암호화되어 저장되고 백업 파일에는 들어가지 않아요."
        ).forEach {
          Row(Modifier.padding(vertical = 3.dp)) {
            Text("• ", style = MaterialTheme.typography.bodySmall, color = Gray600)
            Text(it, style = MaterialTheme.typography.bodySmall, color = Gray600)
          }
        }
        provider.keyUrl?.let { url ->
          TextButton(onClick = { openUrl(url) }) {
            Text("${provider.label} 키 페이지 열기")
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
          }
        }
      }
    }
  }

  if (showConsent) {
    AlertDialog(
      onDismissRequest = { showConsent = false },
      title = { Text("AI 추천을 켤까요?", fontWeight = FontWeight.Bold) },
      text = { Text("거래를 입력할 때 메모 내용이 ${provider.label}(으)로 전송되어 카테고리를 추천받아요. 금액·계좌 정보와 결제 문자 내용은 보내지 않아요.") },
      confirmButton = {
        TextButton(onClick = { showConsent = false; viewModel.setEnabled(true) }) { Text("동의하고 켜기", fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { showConsent = false }) { Text("취소") } }
    )
  }
  if (confirmRemove) {
    ConfirmDialog(
      title = "API 키를 삭제할까요?",
      message = "AI 추천이 꺼져요. 다시 쓰려면 키를 새로 등록해야 해요.",
      confirmLabel = "삭제",
      onConfirm = viewModel::removeKey,
      onDismiss = { confirmRemove = false }
    )
  }
}
