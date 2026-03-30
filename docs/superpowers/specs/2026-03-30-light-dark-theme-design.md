# Light/Dark Theme — Full Support Design
**Date:** 2026-03-30
**Status:** Approved

## Problem

All screens use hardcoded `Color(0xFF...)` values (e.g. `Color(0xFF0A0E1A)` for backgrounds, `Color.White` for text). When the user switches to Light mode in Settings, only Material3 default components change — custom screens remain dark because they don't read from the theme.

## Approach: Hybrid (Material3 tokens + custom AppColors)

Two complementary mechanisms:

1. **Material3 `lightColorScheme`** — update `AppLightColorScheme` in `Theme.kt` with correct light values for `background`, `surface`, `onBackground`, `onSurface`, `primary`, `onPrimary`. Handles Material3 components automatically.

2. **`AppColors` data class + `LocalAppColors`** — new CompositionLocal (same pattern as existing `LocalAppDisplaySettings`) for colors that have no Material3 equivalent. Screens read `LocalAppColors.current.X`.

## AppColors Token Table

| Token | Dark value | Light value |
|---|---|---|
| `screenBackground` | `0xFF0A0E1A` | `0xFFEEF2F7` |
| `cardSurface` | `0xFF111827` | `0xFFFFFFFF` |
| `topBarBackground` | `0xFF0A0E1A` | `0xFFFFFFFF` |
| `sectionLabelColor` | `0xFF8899AA` | `0xFF6B7280` |
| `primaryText` | `Color.White` | `0xFF1A1A2E` |
| `secondaryText` | `White @ 60%` | `0xFF6B7280` |
| `dividerColor` | `White @ 8%` | `0xFFE0E0E0` |
| `iconTintOnTopBar` | `0xFF5BC8F5` | `0xFF1565C0` |
| `headerGradient` | `[0xFF0A1E6E, 0xFF1565C0, 0xFF1E88E5]` | same (brand element, always dark blue) |

## Material3 Light Scheme Update (`Color.kt` + `Theme.kt`)

Add to `Color.kt`:
```
val LightBackground   = Color(0xFFEEF2F7)
val LightSurface      = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1A1A2E)
val LightOnSurface    = Color(0xFF1A1A2E)
val LightPrimary      = Color(0xFF1565C0)
```

Update `AppLightColorScheme` in `Theme.kt`:
```kotlin
private val AppLightColorScheme = lightColorScheme(
    primary       = LightPrimary,
    background    = LightBackground,
    surface       = LightSurface,
    onBackground  = LightOnBackground,
    onSurface     = LightOnSurface,
    secondary     = PurpleGrey40,
    tertiary      = Pink40
)
```

## Infrastructure Changes (`Theme.kt`)

Add after existing `LocalAppDisplaySettings`:

```kotlin
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
    cardSurface       = Color(0xFF111827),
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

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
```

Update `BKDiagnosticTheme` to provide `LocalAppColors`:
```kotlin
val appColors = if (useDarkTheme) DarkAppColors else LightAppColors
CompositionLocalProvider(
    LocalAppDisplaySettings provides displaySettings,
    LocalAppColors provides appColors
) { ... }
```

## Screens to Update

### In scope (use AppColors tokens):

| Screen | Key changes |
|---|---|
| `SettingsScreen` | `containerColor`, TopAppBar bg, SettingsCard bg, section labels, text colors, dividers |
| `DashboardScreen` | Column background, feature card bg (`CardDefaults`), activity card bg |
| `LiveDataScreen` | Scaffold background, card surfaces, text colors |
| `AppTopBar` | Background + icon tint |

### Out of scope (intentionally dark, do not change):

| Screen | Reason |
|---|---|
| `ActiveTestScreen` | Instrument cluster UI — must stay dark for readability |
| `DiagnosticScreen` | Already uses light card colors (`0xFFF1F4F9`, `0xFFF0F4FF`) — visually fine in both modes |
| `BrandSelectionScreen` | Already uses `Color.White` / `0xFFEEF2F7` — already light |
| `LoginScreen`, `RegisterScreen`, `ForgotPasswordScreen`, `ResetPasswordScreen` | Auth screens — verify separately |
| `RawMonitorScreen`, `WiringDiagramScreen` | Data-density screens — keep as-is |

## Migration Pattern (per screen)

Replace structural hardcoded colors:
```kotlin
// Before
containerColor = Color(0xFF0A0E1A)

// After
val appColors = LocalAppColors.current
containerColor = appColors.screenBackground
```

Decorative colors (icon badge colors like `Color(0xFF1565C0)` for Person icon, `Color(0xFF2E7D32)` for Email icon) — **stay hardcoded**. These are intentional brand/semantic colors.

## Testing Checklist

- [ ] Toggle Dark → Light → System in Settings: all 4 in-scope screens update visually
- [ ] Text is readable in both modes (no white-on-white or dark-on-dark)
- [ ] Gradient header on DashboardScreen remains dark blue in both modes
- [ ] ActiveTestScreen, DiagnosticScreen, BrandSelectionScreen unaffected
- [ ] System mode follows device setting correctly
