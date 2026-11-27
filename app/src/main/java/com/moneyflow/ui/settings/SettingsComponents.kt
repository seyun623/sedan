package com.moneyflow.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyflow.ui.theme.CardSurface
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray500
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.OnBrand
import com.moneyflow.ui.theme.TossBlue

/** Scaffold with a back-arrow top bar used by all settings screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
  title: String,
  onBack: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  Scaffold(
    containerColor = Gray100,
    // The nav host's scaffold already applies the system bar insets.
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기") }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Gray100,
          titleContentColor = Gray900,
          navigationIconContentColor = Gray900
        )
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      content = content
    )
  }
}

/** A titled card grouping settings rows. */
@Composable
internal fun SettingsSection(title: String?, content: @Composable ColumnScope.() -> Unit) {
  Column {
    if (title != null) {
      Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = Gray600,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
      )
    }
    Surface(shape = RoundedCornerShape(20.dp), color = CardSurface, modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(vertical = 4.dp), content = content)
    }
  }
}

/** Clickable navigation / action row. */
@Composable
internal fun SettingsItem(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  iconTint: Color = TossBlue,
  trailing: String? = null,
  trailingColor: Color = Gray600,
  showChevron: Boolean = true,
  enabled: Boolean = true,
  titleColor: Color = Gray900,
  onClick: (() -> Unit)? = null
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = if (enabled) iconTint else Gray500, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(14.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) titleColor else Gray500,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray600)
    }
    if (trailing != null) {
      Spacer(Modifier.width(8.dp))
      Text(trailing, style = MaterialTheme.typography.bodyMedium, color = trailingColor, maxLines = 1)
    }
    if (showChevron && onClick != null) {
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Gray500)
    }
  }
}

/** Row with a switch; the whole row toggles. */
@Composable
internal fun SettingsSwitchItem(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  enabled: Boolean = true
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = if (enabled) TossBlue else Gray500, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(14.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) Gray900 else Gray500,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray600)
    }
    Spacer(Modifier.width(8.dp))
    Switch(
      checked = checked,
      onCheckedChange = null,
      enabled = enabled,
      colors = SwitchDefaults.colors(checkedTrackColor = TossBlue, checkedThumbColor = OnBrand)
    )
  }
}

/** Single-choice row (radio). */
@Composable
internal fun SettingsRadioItem(title: String, selected: Boolean, onSelect: () -> Unit, subtitle: String? = null) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
      .padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = Gray900, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray600)
    }
    RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = TossBlue))
  }
}
