package com.moneyflow.ui.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyflow.ui.settings.SettingsScaffold
import com.moneyflow.ui.settings.SettingsSection
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue

/** Shown once after an update, listing what changed since the last version the user saw. */
@Composable
fun WhatsNewDialog(notes: List<ReleaseNote>, onDismiss: () -> Unit) {
  if (notes.isEmpty()) return
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("whats_new_dialog"),
    title = { Text("머니플로우 ${notes.first().version} 업데이트", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        notes.forEachIndexed { i, note ->
          if (notes.size > 1) {
            if (i > 0) Spacer(Modifier.height(12.dp))
            Text(note.version, fontWeight = FontWeight.Bold, color = TossBlue)
          }
          NoteItems(note)
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("확인", fontWeight = FontWeight.Bold) } }
  )
}

@Composable
private fun NoteItems(note: ReleaseNote) {
  note.items.forEach {
    Row(Modifier.padding(top = 6.dp)) {
      Text("• ", color = Gray900)
      Text(it, color = Gray900, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

/** Settings → 업데이트 내역 */
@Composable
fun ReleaseHistoryScreen(onBack: () -> Unit, currentVersion: String) {
  SettingsScaffold(title = "업데이트 내역", onBack = onBack) {
    ReleaseNotes.all.filter { ReleaseNotes.compare(it.version, currentVersion) <= 0 }.forEach { note ->
      SettingsSection("${note.version} · ${note.date}") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
          note.items.forEach {
            Row(Modifier.padding(vertical = 3.dp)) {
              Text("• ", color = Gray600)
              Text(it, color = Gray900, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }
  }
}
