package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Dark palette ─────────────────────────────────────────────────────────────
private val DarkBg      = Color(0xFF09090B)
private val DarkSurface = Color(0xFF111114)
private val DarkCard    = Color(0xFF1A1A1F)
private val DarkBorder  = Color(0xFF2A2A30)
private val DarkText    = Color(0xFFF4F4F5)
private val DarkMuted   = Color(0xFF8E8E96)

// ─── Light palette ────────────────────────────────────────────────────────────
private val LightBg      = Color(0xFFF8F9FA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightCard    = Color(0xFFF1F3F5)
private val LightBorder  = Color(0xFFE2E8F0)
private val LightText    = Color(0xFF0F172A)
private val LightMuted   = Color(0xFF64748B)

// ─── Shared accent ────────────────────────────────────────────────────────────
val AppRed     = Color(0xFFE11D48)
val AppRedDark = Color(0xFF7A0000)

// ─── AppColors data class ─────────────────────────────────────────────────────
@Immutable
data class AppColors(
  val bg: Color,
  val surface: Color,
  val card: Color,
  val border: Color,
  val text: Color,
  val muted: Color,
)

private val darkAppColors = AppColors(
  bg = DarkBg, surface = DarkSurface, card = DarkCard,
  border = DarkBorder, text = DarkText, muted = DarkMuted,
)

private val lightAppColors = AppColors(
  bg = LightBg, surface = LightSurface, card = LightCard,
  border = LightBorder, text = LightText, muted = LightMuted,
)

val LocalAppColors = compositionLocalOf { darkAppColors }

// ─── Convenience composable properties ───────────────────────────────────────
// These are @ReadOnlyComposable properties — usable exactly like Color vals in
// composable functions. Every existing call site (background(AppBg), color = AppText,
// etc.) will work without change.
val AppBg:      Color @ReadOnlyComposable @Composable get() = LocalAppColors.current.bg
val AppSurface: Color @ReadOnlyComposable @Composable get() = LocalAppColors.current.surface
val AppCard:    Color @ReadOnlyComposable @Composable get() = LocalAppColors.current.card
val AppBorder:  Color @ReadOnlyComposable @Composable get() = LocalAppColors.current.border
val AppText:    Color @ReadOnlyComposable @Composable get() = LocalAppColors.current.text
val AppMuted:   Color @ReadOnlyComposable @Composable get() = LocalAppColors.current.muted

// ─── Material color schemes ───────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
  primary            = AppRed,
  onPrimary          = Color.White,
  primaryContainer   = AppRedDark,
  onPrimaryContainer = Color.White,
  secondary          = AppRedDark,
  background         = DarkBg,
  onBackground       = DarkText,
  surface            = DarkSurface,
  onSurface          = DarkText,
  surfaceVariant     = DarkCard,
  onSurfaceVariant   = DarkText,
  outline            = DarkBorder,
  error              = AppRed,
)

private val LightColorScheme = lightColorScheme(
  primary            = AppRed,
  onPrimary          = Color.White,
  primaryContainer   = Color(0xFFFFDADA),
  onPrimaryContainer = Color(0xFF3B0012),
  secondary          = AppRed,
  background         = LightBg,
  onBackground       = LightText,
  surface            = LightSurface,
  onSurface          = LightText,
  surfaceVariant     = LightCard,
  onSurfaceVariant   = LightText,
  outline            = LightBorder,
  error              = AppRed,
)

// ─── Theme entry point ────────────────────────────────────────────────────────
/** @param theme  "dark" | "light" | "system" */
@Composable
fun MyApplicationTheme(
  theme: String = "dark",
  content: @Composable () -> Unit,
) {
  val isDark = when (theme) {
    "light" -> false
    "dark"  -> true
    else    -> isSystemInDarkTheme()
  }
  val colors = if (isDark) darkAppColors else lightAppColors

  CompositionLocalProvider(LocalAppColors provides colors) {
    MaterialTheme(
      colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
      typography  = Typography,
      content     = content,
    )
  }
}
