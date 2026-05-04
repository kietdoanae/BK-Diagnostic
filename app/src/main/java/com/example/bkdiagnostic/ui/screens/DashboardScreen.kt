package com.example.bkdiagnostic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bkdiagnostic.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bkdiagnostic.ActivityLogger
import com.example.bkdiagnostic.AuthUiState
import com.example.bkdiagnostic.AuthViewModel
import com.example.bkdiagnostic.ui.theme.LocalAppColors

private data class DashboardFeature(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val colorLight: Color,
    val isEnabled: Boolean = false
)


@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onDiagnosticsClick: () -> Unit = {},
    onLabModeClick: () -> Unit = {},
    onFaultHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel()
) {
    // Lấy profile sớm để biết quyền truy cập Lab Mode khi build features list
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
    val canAccessLab = userProfile?.canAccessLabMode ?: false

    // Đọc strings trong composable context (nhanh), sau đó remember list để
    // tránh tạo 8 DashboardFeature objects mới trên mỗi recomposition.
    // Language switch gọi recreate() nên không cần lo cache stale.
    val sDiagnostics   = stringResource(R.string.feature_diagnostics)
    val sLabMode       = stringResource(R.string.feature_lab_mode)
    val sDataManager   = stringResource(R.string.feature_data_manager)
    val sSettings      = stringResource(R.string.feature_settings)
    val sUpdate        = stringResource(R.string.feature_update)
    val sRemoteDesktop = stringResource(R.string.feature_remote_desktop)
    val sFaultHistory  = stringResource(R.string.feature_fault_history)
    val sSupport       = stringResource(R.string.feature_support)

    val features = remember(sDiagnostics, sLabMode, sDataManager, sSettings,
                            sUpdate, sRemoteDesktop, sFaultHistory, sSupport, canAccessLab) {
        listOf(
            DashboardFeature("diagnostics",    sDiagnostics,   Icons.Filled.Build,          Color(0xFF1565C0), Color(0xFFBBDEFB), isEnabled = true),
            DashboardFeature("lab_mode",       sLabMode,       Icons.Filled.Science,        Color(0xFFF59E0B), Color(0xFFFEF3C7), isEnabled = canAccessLab),
            DashboardFeature("data_manager",   sDataManager,   Icons.Filled.Storage,        Color(0xFF6A1B9A), Color(0xFFE1BEE7)),
            DashboardFeature("settings",       sSettings,      Icons.Filled.Tune,           Color(0xFF00695C), Color(0xFFB2DFDB), isEnabled = true),
            DashboardFeature("update",         sUpdate,        Icons.Filled.SystemUpdate,   Color(0xFFE65100), Color(0xFFFFE0B2)),
            DashboardFeature("remote_desktop", sRemoteDesktop, Icons.Filled.DesktopWindows, Color(0xFF37474F), Color(0xFFCFD8DC)),
            DashboardFeature("fault_history",  sFaultHistory,  Icons.Filled.History,        Color(0xFFC62828), Color(0xFFFFCDD2)),
            DashboardFeature("support",        sSupport,       Icons.Filled.SupportAgent,   Color(0xFF283593), Color(0xFFC5CAE9)),
        )
    }
    val featureRows = remember(features) { features.chunked(4) }

    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.LoggedOut) {
            authViewModel.resetState()
            onLogout()
        }
    }

    val username    = userProfile?.username    ?: "User"
    val isAdmin     = userProfile?.isAdmin     ?: false
    val isModerator = userProfile?.isModerator ?: false
    val isInstructor = userProfile?.isInstructor ?: false
    val isStudent    = userProfile?.isStudent    ?: false
    val roleLabel   = when {
        isAdmin      -> "Admin"
        isModerator  -> "Moderator"
        isInstructor -> "Instructor"
        isStudent    -> "Student"
        else         -> "User"
    }
    val roleIcon = when {
        isAdmin     -> Icons.Filled.Security
        isModerator -> Icons.Filled.Shield
        else        -> Icons.Filled.Person
    }
    val roleBgColor = when {
        isAdmin     -> Color(0xFFFFB300)
        isModerator -> Color(0xFF42A5F5)
        else        -> Color.White.copy(alpha = 0.22f)
    }
    val roleTextColor = when {
        isAdmin -> Color(0xFF1A1A2E)
        else    -> Color.White
    }
    val initials = username.take(2).uppercase()
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11  -> stringResource(R.string.greeting_morning)
        in 12..17 -> stringResource(R.string.greeting_afternoon)
        else      -> stringResource(R.string.greeting_evening)
    }

    // Deterministic avatar accent colour based on username hash
    val avatarAccent = remember(username) {
        val palette = listOf(
            Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
            Color(0xFF00695C), Color(0xFFE65100), Color(0xFFC62828),
            Color(0xFF283593), Color(0xFF37474F)
        )
        palette[username.hashCode().and(0x7FFFFFFF) % palette.size]
    }

    val appColors = LocalAppColors.current

    // Memoize static gradients — shader compilation chỉ xảy ra 1 lần
    val headerBrush = remember {
        Brush.linearGradient(colors = listOf(Color(0xFF0A1E6E), Color(0xFF1565C0), Color(0xFF1E88E5)))
    }
    val avatarBrush = remember(avatarAccent) {
        Brush.linearGradient(listOf(avatarAccent.copy(alpha = 0.8f), avatarAccent))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
        // ─── Header ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBrush)
        ) {
            // Decorative circles
            Box(Modifier.size(200.dp).offset((-70).dp, (-70).dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
            Box(Modifier.size(130.dp).align(Alignment.BottomEnd).offset(40.dp, 40.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
            Box(Modifier.size(70.dp).align(Alignment.TopEnd).offset((-100).dp, 20.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.03f)))

            // ── Compact top bar: brand | greeting+user | avatar + logout ───────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Brand icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar, null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Brand name + tagline
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        stringResource(R.string.app_tagline),
                        fontSize = 9.5.sp,
                        color = Color.White.copy(alpha = 0.50f)
                    )
                }

                // Greeting + username (compact, right-aligned)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$greeting,",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        // Role badge (mini)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(roleBgColor)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(roleIcon, null, tint = roleTextColor, modifier = Modifier.size(9.dp))
                            Text(roleLabel, color = roleTextColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Avatar (compact)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(avatarBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Logout button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    IconButton(
                        onClick = { authViewModel.logout() },
                        enabled = !isLoading,
                        modifier = Modifier.size(38.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.dashboard_btn_sign_out),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // ─── Feature grid (fills remaining screen evenly) ────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            featureRows.forEach { rowFeatures ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowFeatures.forEach { feature ->
                        FeatureCard(
                            feature = feature,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = {
                                ActivityLogger.featureOpen(feature.title)
                                when (feature.id) {
                                    "diagnostics"    -> onDiagnosticsClick()
                                    "lab_mode"       -> onLabModeClick()
                                    "settings"       -> onSettingsClick()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: DashboardFeature,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val iconBgBrush = remember(feature.colorLight) {
        Brush.radialGradient(colors = listOf(feature.colorLight, feature.colorLight.copy(alpha = 0.3f)))
    }
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (feature.isEnabled) 1f else 0.40f)
                .clickable(enabled = feature.isEnabled) { onClick() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.cardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (feature.isEnabled) 4.dp else 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(iconBgBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = feature.title,
                        tint = feature.color,
                        modifier = Modifier.size(70.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = feature.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = appColors.primaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        // Lock badge — top-right corner when feature is not yet available
        if (!feature.isEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF455A64).copy(alpha = 0.80f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.feature_not_available),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
