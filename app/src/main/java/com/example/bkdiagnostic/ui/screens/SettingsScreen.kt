package com.example.bkdiagnostic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bkdiagnostic.AuthUiState
import com.example.bkdiagnostic.AuthViewModel
import com.example.bkdiagnostic.ConnectionSettings
import com.example.bkdiagnostic.DiagnosticsSettings
import com.example.bkdiagnostic.DisplaySettings
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.SettingsViewModel
import com.example.bkdiagnostic.ThemeMode
import com.example.bkdiagnostic.supabaseClient
import com.example.bkdiagnostic.ui.theme.AppColors
import com.example.bkdiagnostic.ui.theme.LocalAppColors
import io.github.jan.supabase.auth.auth

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val uiState          by authViewModel.uiState.collectAsStateWithLifecycle()
    val userProfile      by authViewModel.userProfile.collectAsStateWithLifecycle()
    val connSettings      by settingsViewModel.connectionSettings.collectAsStateWithLifecycle()
    val diagSettings      by settingsViewModel.diagnosticsSettings.collectAsStateWithLifecycle()
    val dispSettings      by settingsViewModel.displaySettings.collectAsStateWithLifecycle()
    val connSettingsError by settingsViewModel.error.collectAsStateWithLifecycle()

    val username    = userProfile?.username    ?: "User"
    val isAdmin     = userProfile?.isAdmin     ?: false
    val isModerator = userProfile?.isModerator ?: false
    val isLoading   = uiState is AuthUiState.Loading

    var userEmail by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        userEmail = supabaseClient.auth.currentUserOrNull()?.email ?: ""
    }

    // Dialog visibility — account
    var showEditUsername   by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    // Dialog visibility — connection
    var showCanPicker   by remember { mutableStateOf(false) }

    // Dialog visibility — diagnostics
    var showPollPicker    by remember { mutableStateOf(false) }
    var showTimeoutPicker by remember { mutableStateOf(false) }

    // Dialog visibility — display
    var showThemePicker    by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val strUsernameUpdated  = stringResource(R.string.settings_toast_username_updated)
    val strPasswordChanged  = stringResource(R.string.settings_toast_password_changed)

    LaunchedEffect(uiState) {
        val msg = when (val s = uiState) {
            is AuthUiState.ProfileUpdated  -> strUsernameUpdated
            is AuthUiState.PasswordUpdated -> strPasswordChanged
            is AuthUiState.Error           -> s.message
            is AuthUiState.LoggedOut       -> { authViewModel.resetState(); onLogout(); null }
            else                           -> null
        }
        if (msg != null) {
            authViewModel.resetState()
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(connSettingsError) {
        connSettingsError?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.clearError()
        }
    }

    val strSettings         = stringResource(R.string.settings_title)
    val strAccount          = stringResource(R.string.settings_section_account)
    val strUsername         = stringResource(R.string.settings_label_username)
    val strEmail            = stringResource(R.string.settings_label_email)
    val strSecurity         = stringResource(R.string.settings_section_security)
    val strChangePassword   = stringResource(R.string.settings_label_change_password)
    val strConnection       = stringResource(R.string.settings_section_connection)
    val strCanSpeed         = stringResource(R.string.settings_label_can_speed)
    val strKbps             = stringResource(R.string.settings_unit_kbps)
    val strAutoReconnect    = stringResource(R.string.settings_label_auto_reconnect)
    val strAutoReconnectSub = stringResource(R.string.settings_sublabel_auto_reconnect)
    val strDiagnostics      = stringResource(R.string.settings_section_diagnostics)
    val strRefreshRate      = stringResource(R.string.settings_label_refresh_rate)
    val strMs               = stringResource(R.string.settings_unit_ms)
    val strTimeout          = stringResource(R.string.settings_label_timeout)
    val strImperial         = stringResource(R.string.settings_label_imperial)
    val strImperialSub      = stringResource(R.string.settings_sublabel_imperial)
    val strAutoClearDtc     = stringResource(R.string.settings_label_auto_clear_dtc)
    val strAutoClearDtcSub  = stringResource(R.string.settings_sublabel_auto_clear_dtc)
    val strDisplay          = stringResource(R.string.settings_section_display)
    val strTheme            = stringResource(R.string.settings_label_theme)
    val strDark             = stringResource(R.string.settings_theme_dark)
    val strLight            = stringResource(R.string.settings_theme_light)
    val strSystem           = stringResource(R.string.settings_theme_system)
    val strKeepScreenOn     = stringResource(R.string.settings_label_keep_screen_on)
    val strKeepScreenOnSub  = stringResource(R.string.settings_sublabel_keep_screen_on)
    val strLanguage         = stringResource(R.string.settings_label_language)
    val strLangVi           = stringResource(R.string.settings_lang_vi)
    val strLangEn           = stringResource(R.string.settings_lang_en)
    val strSession          = stringResource(R.string.settings_section_session)
    val strSignOut          = stringResource(R.string.settings_label_sign_out)
    val strSignOutConfirm   = stringResource(R.string.settings_signout_confirm)
    val strBtnSignOut       = stringResource(R.string.settings_btn_sign_out)
    val strCancel           = stringResource(R.string.btn_cancel)
    val strPickerTheme      = stringResource(R.string.settings_picker_theme)
    val strSystemDefault    = stringResource(R.string.settings_theme_system_default)
    val strPickerLanguage   = stringResource(R.string.settings_picker_language)
    val strLangNote         = stringResource(R.string.settings_lang_localization_note)
    val strPickerRefresh    = stringResource(R.string.settings_picker_refresh_rate)
    val strPickerTimeout    = stringResource(R.string.settings_picker_timeout)
    val strPickerCan        = stringResource(R.string.settings_picker_can)

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

        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {

            // ── Profile header ───────────────────────────────────────────────
            item {
                ProfileHeaderCard(
                    username    = username,
                    email       = userEmail,
                    isAdmin     = isAdmin,
                    isModerator = isModerator
                )
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Account ─────────────────────────────────────────────
            item {
                SectionLabel(strAccount)
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.Person,
                        iconBg       = Color(0xFF1565C0),
                        label        = strUsername,
                        trailingText = username,
                        showChevron  = true,
                        onClick      = { showEditUsername = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon         = Icons.Filled.Email,
                        iconBg       = Color(0xFF2E7D32),
                        label        = strEmail,
                        trailingText = userEmail.ifEmpty { "—" },
                        showChevron  = false,
                        onClick      = null
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Security ────────────────────────────────────────────
            item {
                SectionLabel(strSecurity)
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon        = Icons.Filled.Lock,
                        iconBg      = Color(0xFF6A1B9A),
                        label       = strChangePassword,
                        showChevron = true,
                        onClick     = { showChangePassword = true }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Connection ──────────────────────────────────────────
            item {
                SectionLabel(strConnection)
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.NetworkCheck,
                        iconBg       = Color(0xFF1565C0),
                        label        = strCanSpeed,
                        trailingText = "${connSettings.canSpeedKbps} $strKbps",
                        showChevron  = true,
                        onClick      = { showCanPicker = true }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon    = Icons.Filled.Autorenew,
                        iconBg  = Color(0xFF37474F),
                        label   = strAutoReconnect,
                        subLabel = strAutoReconnectSub,
                        checked = connSettings.autoReconnect,
                        onCheckedChange = { enabled ->
                            settingsViewModel.saveConnectionSettings(
                                connSettings.copy(autoReconnect = enabled)
                            )
                        }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Diagnostics ─────────────────────────────────────────
            item {
                SectionLabel(strDiagnostics)
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.Refresh,
                        iconBg       = Color(0xFF00695C),
                        label        = strRefreshRate,
                        trailingText = "${diagSettings.pollIntervalMs} $strMs",
                        showChevron  = true,
                        onClick      = { showPollPicker = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon         = Icons.Filled.Timer,
                        iconBg       = Color(0xFF283593),
                        label        = strTimeout,
                        trailingText = "${diagSettings.responseTimeoutMs} $strMs",
                        showChevron  = true,
                        onClick      = { showTimeoutPicker = true }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon    = Icons.Filled.Language,
                        iconBg  = Color(0xFF6A1B9A),
                        label   = strImperial,
                        subLabel = strImperialSub,
                        checked = diagSettings.useImperial,
                        onCheckedChange = { enabled ->
                            settingsViewModel.saveDiagnosticsSettings(
                                diagSettings.copy(useImperial = enabled)
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon     = Icons.Filled.DeleteSweep,
                        iconBg   = Color(0xFFC62828),
                        label    = strAutoClearDtc,
                        subLabel = strAutoClearDtcSub,
                        checked  = diagSettings.autoClearDtc,
                        onCheckedChange = { enabled ->
                            settingsViewModel.saveDiagnosticsSettings(
                                diagSettings.copy(autoClearDtc = enabled)
                            )
                        }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Display ─────────────────────────────────────────────
            item {
                SectionLabel(strDisplay)
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.DarkMode,
                        iconBg       = Color(0xFF37474F),
                        label        = strTheme,
                        trailingText = when (dispSettings.themeMode) {
                            ThemeMode.DARK   -> strDark
                            ThemeMode.LIGHT  -> strLight
                            ThemeMode.SYSTEM -> strSystem
                        },
                        showChevron  = true,
                        onClick      = { showThemePicker = true }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon     = Icons.Outlined.Lightbulb,
                        iconBg   = Color(0xFFE65100),
                        label    = strKeepScreenOn,
                        subLabel = strKeepScreenOnSub,
                        checked  = dispSettings.keepScreenOn,
                        onCheckedChange = { enabled ->
                            settingsViewModel.saveDisplaySettings(
                                dispSettings.copy(keepScreenOn = enabled)
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon         = Icons.Filled.Translate,
                        iconBg       = Color(0xFF00695C),
                        label        = strLanguage,
                        trailingText = if (dispSettings.language == "vi") strLangVi else strLangEn,
                        showChevron  = true,
                        onClick      = { showLanguagePicker = true }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Session ─────────────────────────────────────────────
            item {
                SectionLabel(strSession)
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon        = Icons.AutoMirrored.Filled.Logout,
                        iconBg      = Color(0xFFC62828),
                        label       = strSignOut,
                        labelColor  = Color(0xFFFF5252),
                        showChevron = false,
                        onClick     = { showSignOutConfirm = true }
                    )
                }
            }
        }
    }

    // ── Edit Username Dialog ──────────────────────────────────────────────────
    if (showEditUsername) {
        EditUsernameDialog(
            currentUsername = username,
            isLoading       = isLoading,
            onDismiss       = { showEditUsername = false },
            onConfirm       = { newName ->
                authViewModel.updateUsername(newName)
                showEditUsername = false
            }
        )
    }

    // ── Change Password Dialog ────────────────────────────────────────────────
    if (showChangePassword) {
        ChangePasswordDialog(
            isLoading = isLoading,
            onDismiss = { showChangePassword = false },
            onConfirm = { newPwd ->
                authViewModel.updatePassword(newPwd)
                showChangePassword = false
            }
        )
    }

    // ── Theme Picker ──────────────────────────────────────────────────────────
    if (showThemePicker) {
        RadioPickerDialog(
            title    = strPickerTheme,
            options  = ThemeMode.entries,
            selected = dispSettings.themeMode,
            label    = { when (it) {
                ThemeMode.DARK   -> strDark
                ThemeMode.LIGHT  -> strLight
                ThemeMode.SYSTEM -> strSystemDefault
            }},
            onSelect  = { mode ->
                settingsViewModel.saveDisplaySettings(dispSettings.copy(themeMode = mode))
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false }
        )
    }

    // ── Language Picker ───────────────────────────────────────────────────────
    if (showLanguagePicker) {
        RadioPickerDialog(
            title    = strPickerLanguage,
            options  = listOf("en", "vi"),
            selected = dispSettings.language,
            label    = { if (it == "vi") strLangVi else strLangEn },
            subNote  = strLangNote,
            onSelect  = { lang ->
                settingsViewModel.saveDisplaySettings(dispSettings.copy(language = lang))
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    // ── Live Data Refresh Rate Picker ─────────────────────────────────────────
    if (showPollPicker) {
        RadioPickerDialog(
            title    = strPickerRefresh,
            options  = listOf(100L, 250L, 500L, 1000L),
            selected = diagSettings.pollIntervalMs,
            label    = { "$it $strMs" },
            onSelect = { ms ->
                settingsViewModel.saveDiagnosticsSettings(diagSettings.copy(pollIntervalMs = ms))
                showPollPicker = false
            },
            onDismiss = { showPollPicker = false }
        )
    }

    // ── ECU Response Timeout Picker ───────────────────────────────────────────
    if (showTimeoutPicker) {
        RadioPickerDialog(
            title    = strPickerTimeout,
            options  = listOf(250L, 500L, 1000L, 2000L),
            selected = diagSettings.responseTimeoutMs,
            label    = { "$it $strMs" },
            onSelect = { ms ->
                settingsViewModel.saveDiagnosticsSettings(diagSettings.copy(responseTimeoutMs = ms))
                showTimeoutPicker = false
            },
            onDismiss = { showTimeoutPicker = false }
        )
    }

    // ── CAN Bus Speed Picker ──────────────────────────────────────────────────
    if (showCanPicker) {
        RadioPickerDialog(
            title    = strPickerCan,
            options  = listOf(125, 250, 500, 1000),
            selected = connSettings.canSpeedKbps,
            label    = { "$it $strKbps" },
            onSelect = { kbps ->
                settingsViewModel.saveConnectionSettings(connSettings.copy(canSpeedKbps = kbps))
                showCanPicker = false
            },
            onDismiss = { showCanPicker = false }
        )
    }

    // ── Sign Out Confirm ──────────────────────────────────────────────────────
    if (showSignOutConfirm) {
        val appColors = LocalAppColors.current
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor   = appColors.cardSurface,
            title  = { Text(strBtnSignOut, color = appColors.primaryText, fontWeight = FontWeight.SemiBold) },
            text   = { Text(strSignOutConfirm, color = appColors.secondaryText) },
            confirmButton = {
                TextButton(onClick = { showSignOutConfirm = false; authViewModel.logout() }) {
                    Text(strBtnSignOut, color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(strCancel, color = appColors.iconTintOnTopBar)
                }
            }
        )
    }
}

// ── Profile Header Card ───────────────────────────────────────────────────────

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
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(avatarColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(username, color = appColors.primaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (email.isNotEmpty()) {
                    Text(email, color = appColors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.15f)) {
                    Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

// ── Section Label ─────────────────────────────────────────────────────────────

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

// ── Settings Card ─────────────────────────────────────────────────────────────

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

// ── Divider ───────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDivider() {
    val appColors = LocalAppColors.current
    HorizontalDivider(
        modifier  = Modifier.padding(start = 56.dp),
        color     = appColors.dividerColor,
        thickness = 0.5.dp
    )
}

// ── Row ───────────────────────────────────────────────────────────────────────

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

// ── Edit Username Dialog ──────────────────────────────────────────────────────

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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = appColors.iconTintOnTopBar)
                } else {
                    Text(strSave, color = appColors.iconTintOnTopBar, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strCancel, color = appColors.secondaryText) }
        }
    )
}

// ── Change Password Dialog ────────────────────────────────────────────────────

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
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon         = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null, tint = appColors.secondaryText)
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
                    visualTransformation = if (showConf) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon         = {
                        IconButton(onClick = { showConf = !showConf }) {
                            Icon(if (showConf) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null, tint = appColors.secondaryText)
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = appColors.iconTintOnTopBar)
                } else {
                    Text(strSave, color = appColors.iconTintOnTopBar, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strCancel, color = appColors.secondaryText) }
        }
    )
}

// ── Toggle Row ────────────────────────────────────────────────────────────────

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

// ── Radio Picker Dialog ───────────────────────────────────────────────────────

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
                    Text(subNote, color = appColors.secondaryText, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                options.forEach { option ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { onSelect(option) }
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
            TextButton(onClick = onDismiss) { Text(strCancel, color = appColors.secondaryText) }
        }
    )
}

// ── Shared TextField colors ───────────────────────────────────────────────────

@Composable
private fun textFieldColors() = LocalAppColors.current.run {
    OutlinedTextFieldDefaults.colors(
        focusedTextColor     = primaryText,
        unfocusedTextColor   = primaryText,
        focusedBorderColor   = iconTintOnTopBar,
        unfocusedBorderColor = dividerColor,
        focusedLabelColor    = iconTintOnTopBar,
        unfocusedLabelColor  = secondaryText,
        cursorColor          = iconTintOnTopBar
    )
}
