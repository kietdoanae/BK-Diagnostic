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
import com.example.bkdiagnostic.SettingsViewModel
import com.example.bkdiagnostic.ThemeMode
import com.example.bkdiagnostic.supabaseClient
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
    var showBaudPicker  by remember { mutableStateOf(false) }
    var showCanPicker   by remember { mutableStateOf(false) }

    // Dialog visibility — diagnostics
    var showPollPicker    by remember { mutableStateOf(false) }
    var showTimeoutPicker by remember { mutableStateOf(false) }

    // Dialog visibility — display
    var showThemePicker    by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        val msg = when (val s = uiState) {
            is AuthUiState.ProfileUpdated  -> "Username updated successfully"
            is AuthUiState.PasswordUpdated -> "Password changed successfully"
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

    Scaffold(
        snackbarHost    = { SnackbarHost(snackbarHostState) },
        containerColor  = Color(0xFF0A0E1A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF5BC8F5)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0E1A)
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
                SectionLabel("ACCOUNT")
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.Person,
                        iconBg       = Color(0xFF1565C0),
                        label        = "Username",
                        trailingText = username,
                        showChevron  = true,
                        onClick      = { showEditUsername = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon         = Icons.Filled.Email,
                        iconBg       = Color(0xFF2E7D32),
                        label        = "Email",
                        trailingText = userEmail.ifEmpty { "—" },
                        showChevron  = false,
                        onClick      = null
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Security ────────────────────────────────────────────
            item {
                SectionLabel("SECURITY")
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon        = Icons.Filled.Lock,
                        iconBg      = Color(0xFF6A1B9A),
                        label       = "Change Password",
                        showChevron = true,
                        onClick     = { showChangePassword = true }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Connection ──────────────────────────────────────────
            item {
                SectionLabel("CONNECTION")
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.Speed,
                        iconBg       = Color(0xFF00695C),
                        label        = "USB Baud Rate",
                        trailingText = connSettings.usbBaudRate.toString(),
                        showChevron  = true,
                        onClick      = { showBaudPicker = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon         = Icons.Filled.NetworkCheck,
                        iconBg       = Color(0xFF1565C0),
                        label        = "CAN Bus Speed",
                        trailingText = "${connSettings.canSpeedKbps} kbps",
                        showChevron  = true,
                        onClick      = { showCanPicker = true }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon    = Icons.Filled.Autorenew,
                        iconBg  = Color(0xFF37474F),
                        label   = "Auto-reconnect",
                        subLabel = "Reconnect on transient disconnect",
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
                SectionLabel("DIAGNOSTICS")
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.Refresh,
                        iconBg       = Color(0xFF00695C),
                        label        = "Live Data Refresh Rate",
                        trailingText = "${diagSettings.pollIntervalMs} ms",
                        showChevron  = true,
                        onClick      = { showPollPicker = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon         = Icons.Filled.Timer,
                        iconBg       = Color(0xFF283593),
                        label        = "ECU Response Timeout",
                        trailingText = "${diagSettings.responseTimeoutMs} ms",
                        showChevron  = true,
                        onClick      = { showTimeoutPicker = true }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon    = Icons.Filled.Language,
                        iconBg  = Color(0xFF6A1B9A),
                        label   = "Use Imperial Units",
                        subLabel = "mph, °F, psi instead of km/h, °C, kPa",
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
                        label    = "Auto-clear DTC",
                        subLabel = "Clear codes automatically after reading",
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
                SectionLabel("DISPLAY")
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon         = Icons.Filled.DarkMode,
                        iconBg       = Color(0xFF37474F),
                        label        = "Theme",
                        trailingText = when (dispSettings.themeMode) {
                            ThemeMode.DARK   -> "Dark"
                            ThemeMode.LIGHT  -> "Light"
                            ThemeMode.SYSTEM -> "System"
                        },
                        showChevron  = true,
                        onClick      = { showThemePicker = true }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon     = Icons.Outlined.Lightbulb,
                        iconBg   = Color(0xFFE65100),
                        label    = "Keep Screen On",
                        subLabel = "Prevent screen timeout during diagnostics",
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
                        label        = "Language",
                        trailingText = if (dispSettings.language == "vi") "Tiếng Việt" else "English",
                        showChevron  = true,
                        onClick      = { showLanguagePicker = true }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Section: Session ─────────────────────────────────────────────
            item {
                SectionLabel("SESSION")
                Spacer(Modifier.height(6.dp))
                SettingsCard {
                    SettingsRow(
                        icon        = Icons.AutoMirrored.Filled.Logout,
                        iconBg      = Color(0xFFC62828),
                        label       = "Sign Out",
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
            title    = "Theme",
            options  = ThemeMode.entries,
            selected = dispSettings.themeMode,
            label    = { when (it) {
                ThemeMode.DARK   -> "Dark"
                ThemeMode.LIGHT  -> "Light"
                ThemeMode.SYSTEM -> "System default"
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
            title    = "Language",
            options  = listOf("en", "vi"),
            selected = dispSettings.language,
            label    = { if (it == "vi") "Tiếng Việt" else "English" },
            subNote  = "Full localization coming in a future update",
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
            title    = "Live Data Refresh Rate",
            options  = listOf(100L, 250L, 500L, 1000L),
            selected = diagSettings.pollIntervalMs,
            label    = { "$it ms" },
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
            title    = "ECU Response Timeout",
            options  = listOf(250L, 500L, 1000L, 2000L),
            selected = diagSettings.responseTimeoutMs,
            label    = { "$it ms" },
            onSelect = { ms ->
                settingsViewModel.saveDiagnosticsSettings(diagSettings.copy(responseTimeoutMs = ms))
                showTimeoutPicker = false
            },
            onDismiss = { showTimeoutPicker = false }
        )
    }

    // ── USB Baud Rate Picker ──────────────────────────────────────────────────
    if (showBaudPicker) {
        RadioPickerDialog(
            title    = "USB Baud Rate",
            options  = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600),
            selected = connSettings.usbBaudRate,
            label    = { it.toString() },
            onSelect = { baud ->
                settingsViewModel.saveConnectionSettings(connSettings.copy(usbBaudRate = baud))
                showBaudPicker = false
            },
            onDismiss = { showBaudPicker = false }
        )
    }

    // ── CAN Bus Speed Picker ──────────────────────────────────────────────────
    if (showCanPicker) {
        RadioPickerDialog(
            title    = "CAN Bus Speed",
            options  = listOf(125, 250, 500, 1000),
            selected = connSettings.canSpeedKbps,
            label    = { "$it kbps" },
            onSelect = { kbps ->
                settingsViewModel.saveConnectionSettings(connSettings.copy(canSpeedKbps = kbps))
                showCanPicker = false
            },
            onDismiss = { showCanPicker = false }
        )
    }

    // ── Sign Out Confirm ──────────────────────────────────────────────────────
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor   = Color(0xFF141B2D),
            title  = { Text("Sign Out", color = Color.White, fontWeight = FontWeight.SemiBold) },
            text   = { Text("Are you sure you want to sign out?", color = Color(0xFF8B9AB8)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    authViewModel.logout()
                }) {
                    Text("Sign Out", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel", color = Color(0xFF5BC8F5))
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
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF141B2D))
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
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
                if (email.isNotEmpty()) {
                    Text(
                        text     = email,
                        color    = Color(0xFF8B9AB8),
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

// ── Section Label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        color         = Color(0xFF5A6B8A),
        fontSize      = 11.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

// ── Settings Card ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
        content  = { Column(content = content) }
    )
}

// ── Divider ───────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 56.dp),
        color     = Color(0xFF1E2A40),
        thickness = 0.5.dp
    )
}

// ── Row ───────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    label: String,
    labelColor: Color = Color.White,
    trailingText: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)?
) {
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
                imageVector    = icon,
                contentDescription = null,
                tint           = Color.White,
                modifier       = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text       = label,
            color      = labelColor,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier   = Modifier.weight(1f)
        )

        if (trailingText != null) {
            Text(
                text     = trailingText,
                color    = Color(0xFF5A6B8A),
                fontSize = 14.sp,
                maxLines = 1
            )
            if (showChevron) Spacer(Modifier.width(4.dp))
        }

        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint     = Color(0xFF3A4B6A),
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF141B2D),
        title  = { Text("Edit Username", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text   = {
            Column {
                OutlinedTextField(
                    value           = value,
                    onValueChange   = { value = it; error = null },
                    label           = { Text("Username") },
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
                        trimmed.isEmpty()       -> error = "Username cannot be empty"
                        trimmed.length < 3      -> error = "At least 3 characters required"
                        trimmed == currentUsername -> onDismiss()
                        else                    -> onConfirm(trimmed)
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = Color(0xFF5BC8F5)
                    )
                } else {
                    Text("Save", color = Color(0xFF5BC8F5), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B9AB8))
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF141B2D),
        title  = { Text("Change Password", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value               = newPwd,
                    onValueChange       = { newPwd = it; error = null },
                    label               = { Text("New Password") },
                    singleLine          = true,
                    visualTransformation = if (showNew) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon        = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF5A6B8A)
                            )
                        }
                    },
                    colors              = textFieldColors()
                )
                OutlinedTextField(
                    value               = confirmPwd,
                    onValueChange       = { confirmPwd = it; error = null },
                    label               = { Text("Confirm Password") },
                    singleLine          = true,
                    isError             = error != null,
                    supportingText      = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    visualTransformation = if (showConf) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon        = {
                        IconButton(onClick = { showConf = !showConf }) {
                            Icon(
                                if (showConf) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF5A6B8A)
                            )
                        }
                    },
                    colors              = textFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    when {
                        newPwd.length < 6       -> error = "At least 6 characters required"
                        newPwd != confirmPwd    -> error = "Passwords do not match"
                        else                    -> onConfirm(newPwd)
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = Color(0xFF5BC8F5)
                    )
                } else {
                    Text("Save", color = Color(0xFF5BC8F5), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B9AB8))
            }
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
            Text(label, color = Color.White, fontSize = 15.sp)
            if (subLabel != null) {
                Text(subLabel, color = Color(0xFF5A6B8A), fontSize = 11.sp,
                    modifier = Modifier.padding(top = 1.dp))
            }
        }

        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor       = Color.White,
                checkedTrackColor       = Color(0xFF1565C0),
                uncheckedThumbColor     = Color(0xFF5A6B8A),
                uncheckedTrackColor     = Color(0xFF1E2A40),
                uncheckedBorderColor    = Color(0xFF2A3A5A)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF141B2D),
        title  = { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold) },
        text   = {
            Column {
                if (subNote != null) {
                    Text(
                        subNote,
                        color    = Color(0xFF5A6B8A),
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
                                selectedColor   = Color(0xFF5BC8F5),
                                unselectedColor = Color(0xFF3A4B6A)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label(option),
                            color    = if (option == selected) Color(0xFF5BC8F5) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B9AB8))
            }
        }
    )
}

// ── Shared TextField colors ───────────────────────────────────────────────────

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor    = Color.White,
    unfocusedTextColor  = Color.White,
    focusedBorderColor  = Color(0xFF5BC8F5),
    unfocusedBorderColor = Color(0xFF2A3A5A),
    focusedLabelColor   = Color(0xFF5BC8F5),
    unfocusedLabelColor = Color(0xFF5A6B8A),
    cursorColor         = Color(0xFF5BC8F5)
)
