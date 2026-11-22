package com.moneyflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.moneyflow.data.repository.ThemeMode

private val DarkColorScheme = darkColorScheme(
  primary = TossBlueLight,
  onPrimary = Color.White,
  primaryContainer = DarkPalette.blueContainer,
  onPrimaryContainer = Color(0xFFD6E4FF),
  secondary = AccentTeal,
  onSecondary = Color.White,
  tertiary = AccentOrange,
  background = DarkPalette.gray100,
  onBackground = DarkPalette.gray900,
  surface = DarkPalette.cardSurface,
  onSurface = DarkPalette.gray900,
  surfaceVariant = DarkPalette.gray50,
  onSurfaceVariant = DarkPalette.gray700,
  surfaceContainer = DarkPalette.cardSurface,
  surfaceContainerHigh = DarkPalette.gray50,
  surfaceContainerLow = DarkPalette.cardSurface,
  outline = DarkPalette.gray300,
  outlineVariant = DarkPalette.gray200,
  error = AccentRed,
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = TossBlue,
  onPrimary = Color.White,
  primaryContainer = LightPalette.blueContainer,
  onPrimaryContainer = TossBlueDark,
  secondary = AccentTeal,
  onSecondary = Color.White,
  tertiary = AccentOrange,
  background = LightPalette.gray100,
  onBackground = LightPalette.gray900,
  surface = LightPalette.cardSurface,
  onSurface = LightPalette.gray900,
  surfaceVariant = LightPalette.gray50,
  onSurfaceVariant = LightPalette.gray700,
  surfaceContainer = LightPalette.cardSurface,
  surfaceContainerHigh = LightPalette.gray50,
  surfaceContainerLow = LightPalette.cardSurface,
  outline = LightPalette.gray300,
  outlineVariant = LightPalette.gray200,
  error = AccentRed,
  onError = Color.White
)

@Composable
fun MoneyFlowTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit
) {
  val dark = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
  CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
    MaterialTheme(
      colorScheme = if (dark) DarkColorScheme else LightColorScheme,
      typography = Typography,
      content = content
    )
  }
}
