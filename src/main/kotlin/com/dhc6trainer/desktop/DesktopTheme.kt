package com.dhc6trainer.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal object Dhc6DesktopColors {
    val BackgroundDeep = Color(0xFF020F1A)
    val Background = Color(0xFF061B2B)
    val SurfaceDark = Color(0xFF071F31)
    val Surface = Color(0xFF08283D)
    val SurfaceMedium = Color(0xFF0D344D)
    val SurfaceRaised = Color(0xFF103E5B)
    val Card = Color(0xFF09263A)
    val CardSelected = Color(0xFF0D4B6B)
    val SurfaceSelected = CardSelected
    val Overlay = Color(0xCC061421)

    val BorderSoft = Color(0xFF1F536E)
    val Border = Color(0xFF286783)
    val BorderSelected = Color(0xFF4DBBFF)
    val BorderBright = Color(0xFF64C8FF)

    val Accent = Color(0xFF4DBBFF)
    val AccentStrong = Color(0xFF1F8FDB)
    val AccentBlue = Color(0xFF247BFF)
    val Gold = Color(0xFFF0B429)
    val Green = Color(0xFF22C55E)
    val GreenDark = Color(0xFF126C3A)
    val Red = Color(0xFFC62832)
    val Orange = Color(0xFFFF8A00)

    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFE1ECF3)
    val TextMuted = Color(0xFFC3D2DC)
    val MutedText = TextMuted
    val TextSubtle = Color(0xFF8FA7B5)
}

private val Dhc6DesktopDarkColorScheme = darkColorScheme(
    primary = Dhc6DesktopColors.Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF8BD7FF),
    onSecondary = Color.White,
    tertiary = Dhc6DesktopColors.Gold,
    onTertiary = Color(0xFF06131D),
    background = Dhc6DesktopColors.BackgroundDeep,
    onBackground = Color.White,
    surface = Dhc6DesktopColors.SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = Dhc6DesktopColors.SurfaceMedium,
    onSurfaceVariant = Dhc6DesktopColors.TextSecondary,
    outline = Dhc6DesktopColors.Border,
    error = Dhc6DesktopColors.Red,
    onError = Color.White
)

@Composable
internal fun Dhc6DesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Dhc6DesktopDarkColorScheme,
        content = content
    )
}

internal typealias DesktopColors = Dhc6DesktopColors

@Composable
internal fun DesktopTheme(content: @Composable () -> Unit) {
    Dhc6DesktopTheme(content)
}
