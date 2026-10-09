package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppBg = Color(0xFF09090B)
val AppSurface = Color(0xFF111114)
val AppCard = Color(0xFF1A1A1F)
val AppBorder = Color(0xFF2A2A30)
val AppRed = Color(0xFFFF1A1A)
val AppRedDark = Color(0xFF7A0000)
val AppText = Color(0xFFF4F4F5)
val AppMuted = Color(0xFF8E8E96)

private val AppColorScheme =
  darkColorScheme(
    primary = AppRed,
    onPrimary = Color.White,
    primaryContainer = AppRedDark,
    onPrimaryContainer = Color.White,
    secondary = AppRedDark,
    background = AppBg,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppCard,
    onSurfaceVariant = AppText,
    outline = AppBorder,
    error = AppRed,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = AppColorScheme, typography = Typography, content = content)
}
