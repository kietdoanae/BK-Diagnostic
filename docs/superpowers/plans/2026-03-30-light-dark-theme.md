# Light/Dark Theme Full Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all in-scope screens (SettingsScreen, DashboardScreen, LiveDataScreen) visually respond to the Light/Dark/System theme toggle in Settings, instead of always appearing dark.

**Architecture:** Add an `AppColors` data class with dark/light instances to `Theme.kt`, expose it via `LocalAppColors` CompositionLocal alongside the existing `LocalAppDisplaySettings`. Update each screen to read structural colors (`screenBackground`, `cardSurface`, `primaryText`, etc.) from `LocalAppColors.current` instead of hardcoded `Color(0xFF...)` literals.

**Tech Stack:** Jetpack Compose, Material3, Kotlin, Android — no new dependencies needed.

---

## File Map

| Action | Path |
|---|---|
| Modify | `app/src/main/java/com/example/bkdiagnostic/ui/theme/Color.kt` |
| Modify | `app/src/main/java/com/example/bkdiagnostic/ui/theme/Theme.kt` |
| Modify | `app/src/main/java/com/example/bkdiagnostic/ui/screens/SettingsScreen.kt` |
| Modify | `app/src/main/java/com/example/bkdiagnostic/ui/screens/LiveDataScreen.kt` |
| Modify | `app/src/main/java/com/example/bkdiagnostic/ui/screens/DashboardScreen.kt` |
| No change | `app/src/main/java/com/example/bkdiagnostic/ui/components/AppTopBar.kt` — gradient header is intentional dark-blue branding; stays dark in both modes |

---

## Task 1: Color constants + AppColors infrastructure

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/theme/Theme.kt`

- [ ] **Step 1: Add light-theme color constants to `Color.kt`**

Append these 5 lines to the end of `Color.kt`:

```kotlin
// Light theme structural colors
val LightBackground   = Color(0xFFEEF2F7)
val LightSurface      = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1A1A2E)
val LightOnSurface    = Color(0xFF1A1A2E)
val LightPrimary      = Color(0xFF1565C0)
```

- [ ] **Step 2: Replace the entire contents of `Theme.kt`**

The new file adds `AppColors`, `DarkAppColors`, `LightAppColors`, `LocalAppColors`, updates `AppLightColorScheme`, and provides both CompositionLocals in `BKDiagnosticTheme`:

```kotlin
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

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

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
```

- [ ] **Step 3: Build to verify no compile errors**

```bash
cd "C:/Users/KIET/AndroidStudioProjects/BKDiagnostic"
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/theme/Color.kt \
        app/src/main/java/com/example/bkdiagnostic/ui/theme/Theme.kt
git commit -m "feat: add AppColors token system and LocalAppColors CompositionLocal"
```

---

## Task 2: Migrate SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/screens/SettingsScreen.kt`

Add this import at the top of the file (alongside existing imports):
```kotlin
import com.example.bkdiagnostic.ui.theme.AppColors
import com.example.bkdiagnostic.ui.theme.LocalAppColors
```

- [ ] **Step 1: Scaffold + TopAppBar — read appColors, replace structural colors**

Find the `Scaffold(` block (around line 157) and replace it so it reads `appColors` from the local:

```kotlin
    val appColors = LocalAppColors.current

    Scaffold(
        snackbarHost    = { SnackbarHost(snackbarHostState) },
        containerColor  = appColors.screenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        strSettings,
                        color      = appColors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = appColors.iconTintOnTopBar
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColors.topBarBackground
                )
            )
        }
    ) { innerPadding ->
```

- [ ] **Step 2: Replace `SectionLabel` composable**

Find and replace the entire `SectionLabel` private function:

```kotlin
@Composable
private fun SectionLabel(text: String) {
    val appColors = LocalAppColors.current
    Text(
        text          = text,
        color         = appColors.sectionLabelColor,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}
```

- [ ] **Step 3: Replace `SettingsCard` composable**

```kotlin
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val appColors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = appColors.cardSurface),
        content  = { Column(content = content) }
    )
}
```

- [ ] **Step 4: Replace `SettingsDivider` composable**

```kotlin
@Composable
private fun SettingsDivider() {
    val appColors = LocalAppColors.current
    HorizontalDivider(
        modifier  = Modifier.padding(start = 56.dp),
        color     = appColors.dividerColor,
        thickness = 0.5.dp
    )
}
```

- [ ] **Step 5: Replace `SettingsRow` composable**

Change the default `labelColor` from `Color.White` to `Color.Unspecified`, and resolve it inside using `appColors`:

```kotlin
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    label: String,
    labelColor: Color = Color.Unspecified,
    trailingText: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)?
) {
    val appColors = LocalAppColors.current
    val resolvedLabelColor = if (labelColor == Color.Unspecified) appColors.primaryText else labelColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text       = label,
            color      = resolvedLabelColor,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier   = Modifier.weight(1f)
        )

        if (trailingText != null) {
            Text(
                text     = trailingText,
                color    = appColors.secondaryText,
                fontSize = 14.sp,
                maxLines = 1
            )
            if (showChevron) Spacer(Modifier.width(4.dp))
        }

        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint     = appColors.secondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
```

- [ ] **Step 6: Replace `SettingsToggleRow` composable**

```kotlin
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconBg: Color,
    label: String,
    subLabel: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val appColors = LocalAppColors.current
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = appColors.primaryText, fontSize = 15.sp)
            if (subLabel != null) {
                Text(
                    subLabel,
                    color    = appColors.secondaryText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor    = Color.White,
                checkedTrackColor    = Color(0xFF1565C0),
                uncheckedThumbColor  = appColors.secondaryText,
                uncheckedTrackColor  = appColors.dividerColor,
                uncheckedBorderColor = appColors.dividerColor
            )
        )
    }
}
```

- [ ] **Step 7: Replace `ProfileHeaderCard` composable**

```kotlin
@Composable
private fun ProfileHeaderCard(
    username: String,
    email: String,
    isAdmin: Boolean,
    isModerator: Boolean
) {
    val appColors = LocalAppColors.current
    val initials = username.take(2).uppercase()
    val avatarColors = remember(username) {
        val palette = listOf(
            listOf(Color(0xFF1565C0), Color(0xFF0D47A1)),
            listOf(Color(0xFF2E7D32), Color(0xFF1B5E20)),
            listOf(Color(0xFF6A1B9A), Color(0xFF4A148C)),
            listOf(Color(0xFF00695C), Color(0xFF004D40)),
            listOf(Color(0xFFE65100), Color(0xFFBF360C)),
            listOf(Color(0xFFC62828), Color(0xFFB71C1C)),
            listOf(Color(0xFF283593), Color(0xFF1A237E)),
        )
        palette[username.hashCode().and(0x7FFFFFFF) % palette.size]
    }
    val (badgeColor, badgeText) = when {
        isAdmin     -> Color(0xFFE53935) to "Admin"
        isModerator -> Color(0xFFFFA000) to "Moderator"
        else        -> Color(0xFF43A047) to "User"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = appColors.cardSurface)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier          = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(avatarColors)),
                contentAlignment  = Alignment.Center
            ) {
                Text(
                    text       = initials,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 24.sp
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = username,
                    color      = appColors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
                if (email.isNotEmpty()) {
                    Text(
                        text     = email,
                        color    = appColors.secondaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text       = badgeText,
                        color      = badgeColor,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 8: Replace the Sign-Out confirm dialog block**

Find the `if (showSignOutConfirm)` block and replace it:

```kotlin
    if (showSignOutConfirm) {
        val appColorsDialog = LocalAppColors.current
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor   = appColorsDialog.cardSurface,
            title  = { Text(strBtnSignOut, color = appColorsDialog.primaryText, fontWeight = FontWeight.SemiBold) },
            text   = { Text(strSignOutConfirm, color = appColorsDialog.secondaryText) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    authViewModel.logout()
                }) {
                    Text(strBtnSignOut, color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(strCancel, color = appColorsDialog.iconTintOnTopBar)
                }
            }
        )
    }
```

- [ ] **Step 9: Replace `EditUsernameDialog` composable**

```kotlin
@Composable
private fun EditUsernameDialog(
    currentUsername: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(currentUsername) }
    var error by remember { mutableStateOf<String?>(null) }
    val appColors = LocalAppColors.current

    val strTitle         = stringResource(R.string.settings_dialog_edit_username)
    val strLabelUsername = stringResource(R.string.settings_label_username)
    val strErrorEmpty    = stringResource(R.string.settings_error_username_empty)
    val strErrorShort    = stringResource(R.string.settings_error_username_short)
    val strSave          = stringResource(R.string.btn_save)
    val strCancel        = stringResource(R.string.btn_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = appColors.cardSurface,
        title  = { Text(strTitle, color = appColors.primaryText, fontWeight = FontWeight.SemiBold) },
        text   = {
            Column {
                OutlinedTextField(
                    value           = value,
                    onValueChange   = { value = it; error = null },
                    label           = { Text(strLabelUsername) },
                    singleLine      = true,
                    isError         = error != null,
                    supportingText  = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    colors          = textFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    val trimmed = value.trim()
                    when {
                        trimmed.isEmpty()          -> error = strErrorEmpty
                        trimmed.length < 3         -> error = strErrorShort
                        trimmed == currentUsername -> onDismiss()
                        else                       -> onConfirm(trimmed)
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = appColors.iconTintOnTopBar
                    )
                } else {
                    Text(strSave, color = appColors.iconTintOnTopBar, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strCancel, color = appColors.secondaryText)
            }
        }
    )
}
```

- [ ] **Step 10: Replace `ChangePasswordDialog` composable**

```kotlin
@Composable
private fun ChangePasswordDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPwd     by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var showNew    by remember { mutableStateOf(false) }
    var showConf   by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf<String?>(null) }
    val appColors  = LocalAppColors.current

    val strTitle         = stringResource(R.string.settings_dialog_change_password)
    val strHintNewPwd    = stringResource(R.string.settings_hint_new_password)
    val strHintConfPwd   = stringResource(R.string.settings_hint_confirm_password)
    val strErrorShort    = stringResource(R.string.settings_error_password_short)
    val strErrorMismatch = stringResource(R.string.settings_error_password_mismatch)
    val strSave          = stringResource(R.string.btn_save)
    val strCancel        = stringResource(R.string.btn_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = appColors.cardSurface,
        title  = { Text(strTitle, color = appColors.primaryText, fontWeight = FontWeight.SemiBold) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value                = newPwd,
                    onValueChange        = { newPwd = it; error = null },
                    label                = { Text(strHintNewPwd) },
                    singleLine           = true,
                    visualTransformation = if (showNew) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon         = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = appColors.secondaryText
                            )
                        }
                    },
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value                = confirmPwd,
                    onValueChange        = { confirmPwd = it; error = null },
                    label                = { Text(strHintConfPwd) },
                    singleLine           = true,
                    isError              = error != null,
                    supportingText       = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    visualTransformation = if (showConf) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon         = {
                        IconButton(onClick = { showConf = !showConf }) {
                            Icon(
                                if (showConf) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = appColors.secondaryText
                            )
                        }
                    },
                    colors = textFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    when {
                        newPwd.length < 6    -> error = strErrorShort
                        newPwd != confirmPwd -> error = strErrorMismatch
                        else                 -> onConfirm(newPwd)
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = appColors.iconTintOnTopBar
                    )
                } else {
                    Text(strSave, color = appColors.iconTintOnTopBar, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strCancel, color = appColors.secondaryText)
            }
        }
    )
}
```

- [ ] **Step 11: Replace `RadioPickerDialog` composable**

```kotlin
@Composable
private fun <T> RadioPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    subNote: String? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val strCancel = stringResource(R.string.btn_cancel)
    val appColors = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = appColors.cardSurface,
        title  = { Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold) },
        text   = {
            Column {
                if (subNote != null) {
                    Text(
                        subNote,
                        color    = appColors.secondaryText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                options.forEach { option ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick  = { onSelect(option) },
                            colors   = RadioButtonDefaults.colors(
                                selectedColor   = appColors.iconTintOnTopBar,
                                unselectedColor = appColors.secondaryText
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label(option),
                            color      = if (option == selected) appColors.iconTintOnTopBar else appColors.primaryText,
                            fontSize   = 15.sp,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strCancel, color = appColors.secondaryText)
            }
        }
    )
}
```

- [ ] **Step 12: Replace `textFieldColors()` composable**

The existing function (line ~1022) uses 7 fields. Replace the entire function:

```kotlin
@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor     = LocalAppColors.current.primaryText,
    unfocusedTextColor   = LocalAppColors.current.primaryText,
    focusedBorderColor   = LocalAppColors.current.iconTintOnTopBar,
    unfocusedBorderColor = LocalAppColors.current.dividerColor,
    focusedLabelColor    = LocalAppColors.current.iconTintOnTopBar,
    unfocusedLabelColor  = LocalAppColors.current.secondaryText,
    cursorColor          = LocalAppColors.current.iconTintOnTopBar
)
```

- [ ] **Step 13: Build to verify**

```bash
cd "C:/Users/KIET/AndroidStudioProjects/BKDiagnostic"
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/SettingsScreen.kt
git commit -m "feat: migrate SettingsScreen structural colors to AppColors tokens"
```

---

## Task 3: Migrate LiveDataScreen

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/screens/LiveDataScreen.kt`

Add this import at the top (alongside existing imports):
```kotlin
import com.example.bkdiagnostic.ui.theme.LocalAppColors
```

- [ ] **Step 1: Remove file-level color constants and add module-level doc comment**

Delete these 5 lines from the top of the file (after the package line, before the imports):

```kotlin
private val BgMain   = Color(0xFF0D1B2A)
private val BgCard   = Color(0xFF152233)
private val BgCard2  = Color(0xFF1A2B3E)
private val TextMain = Color(0xFFECEFF1)
private val TextSub  = Color(0xFF90A4AE)
```

Keep `GreenOk`, `YellowWarn`, `RedCrit`, `BlueIdle` — these are status-semantic colors, not theme colors.

- [ ] **Step 2: Update `LiveDataScreen` composable — background**

Find in `LiveDataScreen`:
```kotlin
    Column(
        Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
```

Replace with:
```kotlin
    val appColors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
```

- [ ] **Step 3: Update `LiveDataControlBar` — background + text**

Find in `LiveDataControlBar`:
```kotlin
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard)
            .padding(horizontal = 16.dp, vertical = 8.dp),
```

Replace with:
```kotlin
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.cardSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
```

In the same function, replace `TextSub` references:
```kotlin
    } else TextSub
```
→
```kotlin
    } else appColors.secondaryText
```

And in the `Text` composables inside this function:
- `color = if (isRunning) GreenOk else TextSub` → `color = if (isRunning) GreenOk else appColors.secondaryText`
- `color = TextSub` (update rate text) → `color = appColors.secondaryText`

- [ ] **Step 4: Update `ArcGauge` — card background + text colors**

Find in `ArcGauge`:
```kotlin
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard),
```

Replace with:
```kotlin
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.cardSurface),
```

Find the large value `Text` in `ArcGauge`:
```kotlin
            Text(
                text = if (value != null) formatSensorValue(value, pidDef) else "--",
                color = TextMain,
```
Replace `color = TextMain` → `color = appColors.primaryText`

Find the PID name `Text` in `ArcGauge`:
```kotlin
            Text(
                text = pidDef.name,
                color = TextSub,
```
Replace `color = TextSub` → `color = appColors.secondaryText`

- [ ] **Step 5: Update `SecondaryMetricCard` — card background + text colors**

Find:
```kotlin
        modifier = Modifier
            .width(100.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard2)
```

Replace with:
```kotlin
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.cardSurface)
```

In the same function replace:
- `Text(pid.unit, color = TextSub` → `color = appColors.secondaryText`
- `Text(pid.name, color = TextSub` → `color = appColors.secondaryText`
- `Color.White.copy(alpha = 0.1f)` (progress bar track) → `appColors.dividerColor`

- [ ] **Step 6: Update `ParameterRow` — card background + text colors**

Find:
```kotlin
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard2)
```

Replace with:
```kotlin
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appColors.cardSurface)
```

In the same function replace:
- `Text(pid.name, color = TextMain` → `color = appColors.primaryText`
- `Text(pid.unit, color = TextSub` → `color = appColors.secondaryText`
- `Color.White.copy(alpha = 0.07f)` (progress bar track) → `appColors.dividerColor`

Also update the divider text row in `LiveDataScreen` (the "Tất cả thông số" section):
```kotlin
                    HorizontalDivider(
                        Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.1f)
                    )
```
→ replace both occurrences with `color = appColors.dividerColor`

And the section label:
```kotlin
                    Text(
                        "  ${stringResource(R.string.live_data_all_parameters)}  ",
                        color = TextSub,
```
→ `color = appColors.secondaryText`

- [ ] **Step 7: Build to verify**

```bash
cd "C:/Users/KIET/AndroidStudioProjects/BKDiagnostic"
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If you get `Unresolved reference: BgMain/BgCard/BgCard2/TextMain/TextSub`, you missed a usage — search the file for remaining references and replace them.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/LiveDataScreen.kt
git commit -m "feat: migrate LiveDataScreen color constants to AppColors tokens"
```

---

## Task 4: Migrate DashboardScreen

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/screens/DashboardScreen.kt`

Add this import at the top (alongside existing imports):
```kotlin
import com.example.bkdiagnostic.ui.theme.LocalAppColors
```

- [ ] **Step 1: Update main Column background**

Find in `DashboardScreen` (around line 111):
```kotlin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF2F7))
    ) {
```

Replace with:
```kotlin
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
```

- [ ] **Step 2: Update `FeatureCard` — card background + feature title text**

Find in `FeatureCard` (around line 304):
```kotlin
            colors = CardDefaults.cardColors(containerColor = Color.White),
```
Replace with:
```kotlin
    val appColors = LocalAppColors.current
```
(Add this line at the very start of the `FeatureCard` composable body, before `Box(modifier = modifier) {`)

Then replace:
```kotlin
            colors = CardDefaults.cardColors(containerColor = Color.White),
```
→
```kotlin
            colors = CardDefaults.cardColors(containerColor = appColors.cardSurface),
```

And the feature title:
```kotlin
                Text(
                    text = feature.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1A1A2E),
```
→
```kotlin
                Text(
                    text = feature.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = appColors.primaryText,
```

- [ ] **Step 3: Build to verify**

```bash
cd "C:/Users/KIET/AndroidStudioProjects/BKDiagnostic"
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/DashboardScreen.kt
git commit -m "feat: migrate DashboardScreen background and card colors to AppColors tokens"
```

---

## Task 5: Verify theme toggle end-to-end

- [ ] **Step 1: Install debug build on device/emulator**

```bash
cd "C:/Users/KIET/AndroidStudioProjects/BKDiagnostic"
./gradlew installDebug
```

- [ ] **Step 2: Visual checklist — Dark mode (default)**

Open app and confirm:
- Dashboard: dark navy background, white cards with colored icons
- Settings: dark navy background, dark cards, white text
- LiveData: dark background, dark cards, white text

- [ ] **Step 3: Switch to Light mode**

Go to Settings → Display → Theme → Light. Confirm:
- Dashboard: light gray (`#EEF2F7`) background, white cards, dark text
- Settings: white background, white cards, dark text, section labels in gray
- LiveData: light gray background, white cards, dark text

- [ ] **Step 4: Switch to System mode**

Set device to Dark → confirm app goes dark. Set device to Light → confirm app goes light.

- [ ] **Step 5: Confirm unaffected screens**

Navigate to:
- ActiveTestScreen — still fully dark (instrument cluster)
- BrandSelectionScreen — still light (unchanged)
- DiagnosticScreen — card colors unchanged

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "feat: complete light/dark theme full support"
```
