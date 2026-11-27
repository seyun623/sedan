package com.moneyflow.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---- Brand & accent colors (same in light and dark) ------------------------------------------
val TossBlue = Color(0xFF0064FF)
val TossBlueLight = Color(0xFF3182F6)
val TossBlueDark = Color(0xFF0048B3)
val AccentGreen = Color(0xFF00B074)
val AccentRed = Color(0xFFF04452)
val AccentOrange = Color(0xFFFF8A00)
val AccentPurple = Color(0xFF7059F6)
val AccentTeal = Color(0xFF00A389)

/** Text/icon color on top of brand-colored (blue/red/green) fills. */
val OnBrand = Color.White

// ---- Theme-aware neutrals ----------------------------------------------------------------------
/**
 * Neutral scale. In light mode Gray900 is near-black text and Gray100 the page background;
 * in dark mode the scale is inverted so the same names keep their meaning
 * (Gray900 = primary text, Gray600 = secondary text, Gray200 = borders, Gray100 = page background).
 */
@Immutable
data class MoneyFlowPalette(
  val gray900: Color,
  val gray800: Color,
  val gray700: Color,
  val gray600: Color,
  val gray500: Color,
  val gray400: Color,
  val gray300: Color,
  val gray200: Color,
  val gray100: Color,
  val gray50: Color,
  /** Card / sheet / bar background (white in light mode). */
  val cardSurface: Color,
  val blueContainer: Color,
  val greenContainer: Color,
  val redContainer: Color,
  val orangeContainer: Color,
  val purpleContainer: Color,
  val isDark: Boolean
)

val LightPalette = MoneyFlowPalette(
  gray900 = Color(0xFF191F28),
  gray800 = Color(0xFF333D4B),
  gray700 = Color(0xFF4E5968),
  gray600 = Color(0xFF6B7684),
  gray500 = Color(0xFF8B95A1),
  gray400 = Color(0xFFB0B8C1),
  gray300 = Color(0xFFD1D6DB),
  gray200 = Color(0xFFE5E8EB),
  gray100 = Color(0xFFF2F4F6),
  gray50 = Color(0xFFF9FAFB),
  cardSurface = Color.White,
  blueContainer = Color(0xFFE8F3FF),
  greenContainer = Color(0xFFE6F7F1),
  redContainer = Color(0xFFFEECEE),
  orangeContainer = Color(0xFFFFF3E6),
  purpleContainer = Color(0xFFF1EEFF),
  isDark = false
)

val DarkPalette = MoneyFlowPalette(
  gray900 = Color(0xFFF2F4F6),
  gray800 = Color(0xFFE0E3E7),
  gray700 = Color(0xFFC5CBD2),
  gray600 = Color(0xFFA2AAB4),
  gray500 = Color(0xFF858E99),
  gray400 = Color(0xFF68717C),
  gray300 = Color(0xFF4A515B),
  gray200 = Color(0xFF353A43),
  gray100 = Color(0xFF111317),
  gray50 = Color(0xFF22252C),
  cardSurface = Color(0xFF1C1E24),
  blueContainer = Color(0xFF14284A),
  greenContainer = Color(0xFF10352A),
  redContainer = Color(0xFF3D1A1E),
  orangeContainer = Color(0xFF3B2A12),
  purpleContainer = Color(0xFF2A2347),
  isDark = true
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val Gray900: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray900
val Gray800: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray800
val Gray700: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray700
val Gray600: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray600
val Gray500: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray500
val Gray400: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray400
val Gray300: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray300
val Gray200: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray200
val Gray100: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray100
val Gray50: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gray50
val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cardSurface
val TossBlueContainer: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.blueContainer
val AccentGreenLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.greenContainer
val AccentRedLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.redContainer
val AccentOrangeLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.orangeContainer
val AccentPurpleLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.purpleContainer

/** Green / red for text (amounts): darker in light mode so small text stays readable (≥4.5:1). */
val AccentGreenText: Color @Composable @ReadOnlyComposable get() = if (LocalPalette.current.isDark) AccentGreen else Color(0xFF00805A)
val AccentRedText: Color @Composable @ReadOnlyComposable get() = if (LocalPalette.current.isDark) AccentRed else Color(0xFFD22D3B)

/**
 * Blue for text and icons. The brand blue is too dark to read on dark-mode cards (~3.4:1), so a
 * lighter blue is used there (≥4.5:1). Filled blue buttons keep [TossBlue] with white text.
 */
val TossBlueText: Color @Composable @ReadOnlyComposable get() = if (LocalPalette.current.isDark) Color(0xFF5B9BFF) else TossBlue
