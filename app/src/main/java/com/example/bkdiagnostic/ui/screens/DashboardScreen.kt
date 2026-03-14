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
import com.example.bkdiagnostic.AuthUiState
import com.example.bkdiagnostic.AuthViewModel

private data class DashboardFeature(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val colorLight: Color
)

private val features = listOf(
    DashboardFeature("diagnostics",    "Diagnostics",       Icons.Filled.Build,               Color(0xFF1565C0), Color(0xFFBBDEFB)),
    DashboardFeature("service",        "Service",           Icons.Filled.Construction,         Color(0xFF2E7D32), Color(0xFFC8E6C9)),
    DashboardFeature("data_manager",   "Data Manager",      Icons.Filled.Storage,              Color(0xFF6A1B9A), Color(0xFFE1BEE7)),
    DashboardFeature("settings",       "Settings",          Icons.Filled.Tune,                 Color(0xFF00695C), Color(0xFFB2DFDB)),
    DashboardFeature("update",         "Update",            Icons.Filled.SystemUpdate,         Color(0xFFE65100), Color(0xFFFFE0B2)),
    DashboardFeature("remote_desktop", "Remote Desktop",    Icons.Filled.DesktopWindows,       Color(0xFF37474F), Color(0xFFCFD8DC)),
    DashboardFeature("oem_auth",       "OEM Authorization", Icons.Filled.AdminPanelSettings,   Color(0xFFC62828), Color(0xFFFFCDD2)),
    DashboardFeature("support",        "Support",           Icons.Filled.SupportAgent,         Color(0xFF283593), Color(0xFFC5CAE9)),
)

@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onDiagnosticsClick: () -> Unit = {},
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
    val username = userProfile?.username ?: "Người dùng"
    val isAdmin = userProfile?.isAdmin ?: false
    val initials = username.take(2).uppercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF2F7))
    ) {
        // ─── Top bar ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0A1E6E), Color(0xFF1565C0), Color(0xFF1E88E5))
                    )
                )
        ) {
            // Decorative circles (background layer)
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .offset(x = (-35).dp, y = (-35).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 25.dp, y = 25.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-120).dp, y = (-10).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            )

            // Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── App branding ──
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BK Diagnostic",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Hệ thống chẩn đoán xe thông minh được thực hiện bởi nhóm sinh viên trường Đại học Bách Khoa - Đại học Quốc Gia Thành phố Hồ Chí Minh",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                // ── User pill ──
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = Color.White.copy(alpha = 0.13f)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Avatar with gradient
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF90CAF9), Color(0xFF1565C0))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }

                        // Name + role
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = username,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            // Role badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isAdmin) Color(0xFFFFB300)
                                        else Color(0xFF42A5F5).copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAdmin) Icons.Filled.Security
                                                      else Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = if (isAdmin) Color(0xFF1A1A2E) else Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isAdmin) "Admin" else "User",
                                        color = if (isAdmin) Color(0xFF1A1A2E) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.28f))
                        )

                        // Logout button
                        IconButton(
                            onClick = { authViewModel.logout() },
                            enabled = !isLoading,
                            modifier = Modifier.size(42.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Đăng xuất",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
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
                                when (feature.id) {
                                    "diagnostics" -> onDiagnosticsClick()
                                    else -> { /* TODO */ }
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
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon box with gradient background
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
}
