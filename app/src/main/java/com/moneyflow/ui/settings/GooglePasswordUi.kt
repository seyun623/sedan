package com.moneyflow.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.util.GooglePasswords
import kotlinx.coroutines.launch

/** "구글에 저장된 비밀번호 불러오기": lets the user pick a password saved for this app in Google Password Manager. */
@Composable
fun GooglePasswordFetchButton(onPassword: (String) -> Unit, modifier: Modifier = Modifier) {
  val activity = LocalContext.current as? android.app.Activity ?: return
  val scope = rememberCoroutineScope()
  var note by remember { mutableStateOf<String?>(null) }
  TextButton(
    onClick = {
      scope.launch {
        note = when (val r = GooglePasswords.pick(activity)) {
          is GooglePasswords.Result.Found -> { onPassword(r.password); null }
          GooglePasswords.Result.NoneSaved -> "구글에 저장된 머니플로우 비밀번호가 없어요."
          is GooglePasswords.Result.Unavailable -> r.reason
          else -> null
        }
      }
    },
    modifier = modifier.testTag("google_password_fetch")
  ) { Text("구글에 저장된 비밀번호 불러오기") }
  note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Gray600) }
}

/** Returns a function that offers to save [password] under [name] in Google Password Manager. */
@Composable
fun rememberGooglePasswordSaver(onUnavailable: (String) -> Unit = {}): (name: String, password: String) -> Unit {
  val activity = LocalContext.current as? android.app.Activity
  val scope = rememberCoroutineScope()
  return remember(activity) {
    { name, password ->
      if (activity != null) scope.launch {
        val r = GooglePasswords.save(activity, name, password)
        if (r is GooglePasswords.Result.Unavailable) onUnavailable(r.reason)
      }
    }
  }
}
