package com.example.bkdiagnostic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.bkdiagnostic.DisplaySettings
import com.example.bkdiagnostic.ThemeMode

// ── Color schemes ─────────────────────────────────────────────────────────────

private val AppDarkColorScheme = darkColorScheme(
    primary   = Purple80,
    secondary = PurpleGrey80,
    tertiary  = Pink80
)

private val AppLightColorScheme = lightColorScheme(
    primary      = LightPrimary,
    background   = LightBackground,
    surface      = LightSurface,
    onBackground = LightOnBackground,
    onSurface    = LightOnSurface,
    secondary    = PurpleGrey40,
    tertiary     = Pink40
)

// ── AppColors — custom tokens not covered by Material3 ────────────────────────

data class AppColors(
    val screenBackground: Color,
    val cardSurface: Color,
    val topBarBackground: Color,
    val sectionLabelColor: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val dividerColor: Color,
    val iconTintOnTopBar: Color,
    val headerGradient: List<Color>
)

private val DarkAppColors = AppColors(
    screenBackground  = Color(0xFF0A0E1A),
    cardSurface       = Color(0xFF141B2D),
    topBarBackground  = Color(0xFF0A0E1A),
    sectionLabelColor = Color(0xFF8899AA),
    primaryText       = Color.White,
    secondaryText     = Color.White.copy(alpha = 0.60f),
    dividerColor      = Color.White.copy(alpha = 0.08f),
    iconTintOnTopBar  = Color(0xFF5BC8F5),
    headerGradient    = listOf(Color(0xFF0A1E6E), Color(0xFF1565C0), Color(0xFF1E88E5))
)

private val LightAppColors = AppColors(
    screenBackground  = Color(0xFFEEF2F7),
    cardSurface       = Color(0xFFFFFFFF),
    topBarBackground  = Color(0xFFFFFFFF),
    sectionLabelColor = Color(0xFF6B7280),
    primaryText       = Color(0xFF1A1A2E),
    secondaryText     = Color(0xFF6B7280),
    dividerColor      = Color(0xFFE0E0E0),
    iconTintOnTopBar  = Color(0xFF1565C0),
    headerGradient    = listOf(Color(0xFF0A1E6E), Color(0xFF1565C0), Color(0xFF1E88E5))
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

// ── CompositionLocal for display settings ─────────────────────────────────────

val LocalAppDisplaySettings = staticCompositionLocalOf { DisplaySettings() }

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun BKDiagnosticTheme(
    displaySettings: DisplaySettings = DisplaySettings(),
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (displaySettings.themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) AppDarkColorScheme else AppLightColorScheme
    val appColors   = if (useDarkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(
        LocalAppDisplaySettings provides displaySettings,
        LocalAppColors          provides appColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
