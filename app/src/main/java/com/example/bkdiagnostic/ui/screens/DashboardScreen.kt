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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bkdiagnostic.ActivityLogger
import com.example.bkdiagnostic.AuthUiState
import com.example.bkdiagnostic.AuthViewModel

private data class DashboardFeature(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val colorLight: Color,
    val isEnabled: Boolean = false
)

private val features = listOf(
    DashboardFeature("diagnostics",    "Diagnostics",    Icons.Filled.Build,            Color(0xFF1565C0), Color(0xFFBBDEFB), isEnabled = true),
    DashboardFeature("service",        "Service",        Icons.Filled.Construction,     Color(0xFF2E7D32), Color(0xFFC8E6C9)),
    DashboardFeature("data_manager",   "Data Manager",   Icons.Filled.Storage,          Color(0xFF6A1B9A), Color(0xFFE1BEE7)),
    DashboardFeature("settings",       "Settings",       Icons.Filled.Tune,             Color(0xFF00695C), Color(0xFFB2DFDB), isEnabled = true),
    DashboardFeature("update",         "Update",         Icons.Filled.SystemUpdate,     Color(0xFFE65100), Color(0xFFFFE0B2)),
    DashboardFeature("remote_desktop", "Remote Desktop", Icons.Filled.DesktopWindows,   Color(0xFF37474F), Color(0xFFCFD8DC)),
    DashboardFeature("wiring_diagram", "Wiring Diagram", Icons.Filled.Cable,            Color(0xFFC62828), Color(0xFFFFCDD2), isEnabled = true),
    DashboardFeature("support",        "Support",        Icons.Filled.SupportAgent,     Color(0xFF283593), Color(0xFFC5CAE9)),
)

@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onDiagnosticsClick: () -> Unit = {},
    onWiringDiagramClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel()
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.LoggedOut) {
            authViewModel.resetState()
            onLogout()
        }
    }

    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
    val username    = userProfile?.username    ?: "User"
    val isAdmin     = userProfile?.isAdmin     ?: false
    val isModerator = userProfile?.isModerator ?: false
    val roleLabel   = when {
        isAdmin     -> "Admin"
        isModerator -> "Moderator"
        else        -> "User"
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
    val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "Good morning"
        in 12..17 -> "Good afternoon"
        else      -> "Good evening"
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF2F7))
    ) {
        // ─── Header ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0A1E6E), Color(0xFF1565C0), Color(0xFF1E88E5))
                    )
                )
        ) {
            // Decorative circles
            Box(Modifier.size(200.dp).offset((-70).dp, (-70).dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
            Box(Modifier.size(130.dp).align(Alignment.BottomEnd).offset(40.dp, 40.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
            Box(Modifier.size(70.dp).align(Alignment.TopEnd).offset((-100).dp, 20.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.03f)))

            Column(modifier = Modifier.fillMaxWidth()) {

                // ── App bar ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini brand icon
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.DirectionsCar, null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "BK Diagnostic",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color.White,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            "Intelligent vehicle diagnostics · HCMUT",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    // Logout button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        IconButton(
                            onClick = { authViewModel.logout() },
                            enabled = !isLoading,
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign out",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // ── Profile card ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp, top = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    // Subtle inner highlight on top edge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── Avatar with glow ring ─────────────────────────
                        Box(contentAlignment = Alignment.Center) {
                            // Glow halo
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                avatarAccent.copy(alpha = 0.55f),
                                                avatarAccent.copy(alpha = 0f)
                                            )
                                        )
                                    )
                            )
                            // White border ring
                            Box(
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f))
                            )
                            // Colored accent ring
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(avatarAccent.copy(alpha = 0.35f))
                            )
                            // Avatar face
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                avatarAccent.copy(alpha = 0.75f),
                                                avatarAccent
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // ── User info ────────────────────────────────────
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = greeting,
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 11.sp,
                                letterSpacing = 0.3.sp
                            )
                            Text(
                                text = username,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                letterSpacing = 0.2.sp
                            )
                            // Role + status row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Role badge
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(roleBgColor)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        roleIcon, null,
                                        tint = roleTextColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        roleLabel,
                                        color = roleTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // Online indicator
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4ADE80))
                                )
                                Text(
                                    "Online",
                                    color = Color.White.copy(alpha = 0.50f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // ── Vertical divider + session info ──────────────
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(52.dp)
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle, null,
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                "Active",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
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
            features.chunked(4).forEach { rowFeatures ->
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
                                    "wiring_diagram" -> onWiringDiagramClick()
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
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (feature.isEnabled) 1f else 0.40f)
                .clickable(enabled = feature.isEnabled) { onClick() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        .background(
                            Brush.radialGradient(
                                colors = listOf(feature.colorLight, feature.colorLight.copy(alpha = 0.3f))
                            )
                        ),
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
                    color = Color(0xFF1A1A2E),
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
                    contentDescription = "Chưa khả dụng",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
