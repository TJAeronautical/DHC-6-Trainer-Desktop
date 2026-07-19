package com.dhc6trainer.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Color palette
// Existing token names are preserved so every existing screen keeps compiling.
// New tokens (AccentDeep, Warning, SurfaceOverlay) are additive.
// ─────────────────────────────────────────────────────────────────────────────

internal object Dhc6DesktopColors {
    val BackgroundDeep = Color(0xFF020F1A)
    val Background = Color(0xFF061B2B)
    val SurfaceDark = Color(0xFF071F31)
    val Surface = Color(0xFF08283D)
    val SurfaceMedium = Color(0xFF0D344D)
    val SurfaceRaised = Color(0xFF103E5B)
    val SurfaceOverlay = Color(0x80051221) // 50% dark - for image overlays
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
    val AccentDeep = Color(0xFF0F5C99)   // NEW - deeper blue for pressed / gradient stops
    val AccentBlue = Color(0xFF247BFF)
    val Gold = Color(0xFFF0B429)
    val Warning = Color(0xFFFF8A00)      // NEW alias for orange-warning callouts
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

// ─────────────────────────────────────────────────────────────────────────────
// Typography scale
// ─────────────────────────────────────────────────────────────────────────────

internal object Dhc6Type {
    val DisplayXl = 56.sp
    val DisplayLg = 40.sp
    val DisplayMd = 34.sp

    val TitleLg = 24.sp
    val TitleMd = 20.sp
    val TitleSm = 18.sp

    val BodyLg = 16.sp
    val BodyMd = 14.sp
    val BodySm = 13.sp
    val BodyXs = 12.sp

    val LabelLg = 13.sp
    val LabelMd = 12.sp
    val LabelSm = 11.sp
}

// ─────────────────────────────────────────────────────────────────────────────
// Spacing scale (4-pt grid)
// ─────────────────────────────────────────────────────────────────────────────

internal object Dhc6Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// Radii
// ─────────────────────────────────────────────────────────────────────────────

internal object Dhc6Radius {
    val sm = 8.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
    val xxl = 28.dp
    val pill = 999.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable gradients
// ─────────────────────────────────────────────────────────────────────────────

internal object Dhc6Gradients {
    val AppBackground: Brush
        get() = Brush.linearGradient(
            listOf(
                Dhc6DesktopColors.BackgroundDeep,
                Dhc6DesktopColors.Background,
                Dhc6DesktopColors.BackgroundDeep,
            )
        )

    val HeroBanner: Brush
        get() = Brush.linearGradient(
            listOf(
                Color(0xFF0A2540),
                Color(0xFF061828),
                Color(0xFF0F2030),
                Color(0xFF05121E),
            )
        )

    val Accent: Brush
        get() = Brush.linearGradient(
            listOf(Dhc6DesktopColors.Accent, Dhc6DesktopColors.AccentDeep)
        )

    val AccentSoft: Brush
        get() = Brush.linearGradient(
            listOf(
                Dhc6DesktopColors.Accent.copy(alpha = 0.18f),
                Dhc6DesktopColors.AccentDeep.copy(alpha = 0.12f),
            )
        )
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
