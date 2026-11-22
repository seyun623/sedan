package com.moneyflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = TossBlueLight,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF1E3A6B),
  onPrimaryContainer = Color(0xFFD6E4FF),
  secondary = AccentTeal,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFF1B4D44),
  onSecondaryContainer = Color(0xFFA5F3E2),
  tertiary = AccentOrange,
  background = DarkBackground,
  onBackground = DarkTextPrimary,
  surface = DarkSurface,
  onSurface = DarkTextPrimary,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkTextSecondary,
  outline = DarkBorder,
  error = AccentRed,
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = TossBlue,
  onPrimary = Color.White,
  primaryContainer = TossBlueContainer,
  onPrimaryContainer = TossBlueDark,
  secondary = AccentTeal,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0F7F3),
  onSecondaryContainer = Color(0xFF005043),
  tertiary = AccentOrange,
  background = Gray100,
  onBackground = Gray900,
  surface = Color.White,
  onSurface = Gray900,
  surfaceVariant = Gray50,
  onSurfaceVariant = Gray700,
  outline = Gray200,
  error = AccentRed,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
