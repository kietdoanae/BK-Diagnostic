package com.example.bkdiagnostic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.bkdiagnostic.DisplaySettings
import com.example.bkdiagnostic.ThemeMode

// ── Color schemes ─────────────────────────────────────────────────────────────

private val AppDarkColorScheme = darkColorScheme(
    primary   = Purple80,
    secondary = PurpleGrey80,
    tertiary  = Pink80
)

private val AppLightColorScheme = lightColorScheme(
    primary   = Purple40,
    secondary = PurpleGrey40,
    tertiary  = Pink40
)

// ── CompositionLocal — allows any composable to read current display settings ─

/**
 * Provides [DisplaySettings] (themeMode, keepScreenOn, language) to the
 * entire composable tree. Read via `LocalAppDisplaySettings.current`.
 *
 * Note: full light-theme support for custom-colored screens (DashboardScreen,
 * SettingsScreen, etc.) requires migrating hardcoded Color(...) values to
 * MaterialTheme.colorScheme tokens — planned for a future update.
 * Currently, light mode affects Material3 default components only.
 */
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

    CompositionLocalProvider(LocalAppDisplaySettings provides displaySettings) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
