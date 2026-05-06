package com.solanadistributionmarketdemo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

class DemoColorPalette(
    val isLight: Boolean,
    val Background: Color,
    val Surface: Color,
    val SurfaceElevated: Color,
    val SurfaceMuted: Color,
    val Border: Color,
    val BorderStrong: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextDim: Color,
    val AccentLong: Color,
    val AccentShort: Color,
    val AccentCrowd: Color,
    val AccentYou: Color,
    val OnAccent: Color,
    val AccentWarn: Color,
    val AccentChain: Color,
)

val LightPalette = DemoColorPalette(
    isLight = true,
    Background = Color(0xFFFAFAF7),
    Surface = Color(0xFFFFFFFF),
    SurfaceElevated = Color(0xFFF2F3F5),
    SurfaceMuted = Color(0xFFE7E9ED),
    Border = Color(0x140F1419),
    BorderStrong = Color(0x290F1419),
    TextPrimary = Color(0xFF0F1419),
    TextSecondary = Color(0xFF475467),
    TextDim = Color(0xFF98A2B3),
    AccentLong = Color(0xFF16A34A),
    AccentShort = Color(0xFFDC2626),
    AccentCrowd = Color(0xFF2563EB),
    AccentYou = Color(0xFF0F1419),
    OnAccent = Color(0xFFFFFFFF),
    AccentWarn = Color(0xFFD97706),
    AccentChain = Color(0xFF7C3AED),
)

val DarkPalette = DemoColorPalette(
    isLight = false,
    Background = Color(0xFF0A0D12),
    Surface = Color(0xFF12161D),
    SurfaceElevated = Color(0xFF181D26),
    SurfaceMuted = Color(0xFF1E2330),
    Border = Color(0x14FFFFFF),
    BorderStrong = Color(0x29FFFFFF),
    TextPrimary = Color(0xFFEDEFF3),
    TextSecondary = Color(0xFF98A2B3),
    TextDim = Color(0xFF606A7B),
    AccentLong = Color(0xFF22D38C),
    AccentShort = Color(0xFFFF5C5C),
    AccentCrowd = Color(0xFF4F8DFF),
    AccentYou = Color(0xFFB8FF66),
    OnAccent = Color(0xFF0A0D12),
    AccentWarn = Color(0xFFFFB547),
    AccentChain = Color(0xFF9D7CFF),
)

val LocalDemoColors = staticCompositionLocalOf { LightPalette }

val DemoColors: DemoColorPalette
    @Composable @ReadOnlyComposable
    get() = LocalDemoColors.current

enum class ThemeMode(val label: String) { Light("Light"), Dark("Dark") }

private val ui = FontFamily.SansSerif
val NumericFamily = FontFamily.Monospace

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun ui(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Int = size + 4, letterSpacing: Double = 0.0) =
    TextStyle(
        fontFamily = ui,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = tightLineHeight,
    )

private val DemoTypography = Typography(
    displayLarge = ui(36, FontWeight.Bold, 40, -0.5),
    displayMedium = ui(30, FontWeight.Bold, 34, -0.4),
    displaySmall = ui(24, FontWeight.SemiBold, 28, -0.3),
    headlineLarge = ui(22, FontWeight.SemiBold, 26),
    headlineMedium = ui(19, FontWeight.SemiBold, 24),
    headlineSmall = ui(17, FontWeight.SemiBold, 22),
    titleLarge = ui(17, FontWeight.SemiBold, 22),
    titleMedium = ui(15, FontWeight.SemiBold, 20),
    titleSmall = ui(13, FontWeight.SemiBold, 18, 0.1),
    bodyLarge = ui(15, FontWeight.Normal, 22),
    bodyMedium = ui(14, FontWeight.Normal, 20),
    bodySmall = ui(12, FontWeight.Normal, 17, 0.1),
    labelLarge = ui(13, FontWeight.Medium, 18, 0.2),
    labelMedium = ui(11, FontWeight.Medium, 15, 0.4),
    labelSmall = ui(10, FontWeight.Medium, 14, 0.6),
)

private fun lightScheme(p: DemoColorPalette) = lightColorScheme(
    primary = p.AccentYou, onPrimary = p.OnAccent,
    secondary = p.AccentCrowd, onSecondary = p.OnAccent,
    tertiary = p.AccentLong, onTertiary = p.OnAccent,
    background = p.Background, onBackground = p.TextPrimary,
    surface = p.Surface, onSurface = p.TextPrimary,
    surfaceVariant = p.SurfaceMuted, onSurfaceVariant = p.TextSecondary,
    outline = p.BorderStrong, outlineVariant = p.Border,
    error = p.AccentShort, onError = p.OnAccent,
)

private fun darkScheme(p: DemoColorPalette) = darkColorScheme(
    primary = p.AccentYou, onPrimary = p.OnAccent,
    secondary = p.AccentCrowd, onSecondary = p.OnAccent,
    tertiary = p.AccentLong, onTertiary = p.OnAccent,
    background = p.Background, onBackground = p.TextPrimary,
    surface = p.Surface, onSurface = p.TextPrimary,
    surfaceVariant = p.SurfaceMuted, onSurfaceVariant = p.TextSecondary,
    outline = p.BorderStrong, outlineVariant = p.Border,
    error = p.AccentShort, onError = p.OnAccent,
)

@Composable
fun DemoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val palette = if (mode == ThemeMode.Light) LightPalette else DarkPalette
    val scheme = if (palette.isLight) lightScheme(palette) else darkScheme(palette)
    androidx.compose.runtime.CompositionLocalProvider(LocalDemoColors provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DemoTypography,
            content = content,
        )
    }
}
