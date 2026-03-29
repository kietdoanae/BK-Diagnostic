package com.example.bkdiagnostic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bkdiagnostic.communication.UsbSerialManager
import com.example.bkdiagnostic.DiagnosticsSettings
import com.example.bkdiagnostic.diagnostic.DiagnosticViewModel
import com.example.bkdiagnostic.ui.components.AppTopBar

// ─────────────────────────────────────────────────────────────────────────────
//  Màn hình Hub Chẩn đoán — quản lý sub-screens bằng internal state
//  DiagnosticViewModel được chia sẻ cho tất cả sub-views
// ─────────────────────────────────────────────────────────────────────────────

private enum class DiagView { HUB, LIVE_DATA, RAW_MONITOR, ACTIVE_TEST }

@Composable
fun DiagnosticScreen(
    brandId: String,
    modelId: String,
    onBack: () -> Unit,
    application: android.app.Application,
    isAdmin: Boolean = false,
    diagSettings: DiagnosticsSettings = DiagnosticsSettings()
) {
    val viewModel: DiagnosticViewModel = viewModel(
        factory = DiagnosticViewModel.Factory(application, brandId, modelId, diagSettings)
    )

    var currentView by remember { mutableStateOf(DiagView.HUB) }

    // Nút back hệ thống → về hub (nếu đang ở sub-view) hoặc ra ngoài (từ hub)
    BackHandler(enabled = currentView != DiagView.HUB) {
        currentView = DiagView.HUB
    }

    when (currentView) {
        DiagView.HUB ->
            DiagnosticHub(
                viewModel = viewModel,
                onBack = onBack,
                onNavigate = { currentView = it },
                isAdmin = isAdmin
            )
        DiagView.LIVE_DATA ->
            LiveDataScreen(
                viewModel = viewModel,
                onBack = { currentView = DiagView.HUB }
            )
        DiagView.RAW_MONITOR ->
            RawMonitorScreen(
                viewModel = viewModel,
                onBack = { currentView = DiagView.HUB }
            )
        DiagView.ACTIVE_TEST ->
            ActiveTestScreen(
                viewModel = viewModel,
                onBack = { currentView = DiagView.HUB }
            )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Hub — màn hình menu chọn chức năng
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DiagnosticHub(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit,
    onNavigate: (DiagView) -> Unit,
    isAdmin: Boolean = false
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val dtcList by viewModel.dtcList.collectAsStateWithLifecycle()
    val isDtcLoading by viewModel.isDtcLoading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F4F9))
        ) {
            // TopBar nằm ngoài padding của Scaffold để không bị đẩy xuống bởi status bar inset
            DiagnosticTopBar(config = viewModel.protocolConfig, onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {

            ConnectionStatusBar(
                state = connectionState,
                onConnect = { viewModel.connectUsb() },
                onDisconnect = { viewModel.disconnectUsb() }
            )

            Spacer(Modifier.height(12.dp))

            val isConnected = connectionState is UsbSerialManager.ConnectionState.Connected

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Live Data
                DiagnosticFunctionCard(
                    icon = Icons.Default.Speed,
                    title = "Live Data",
                    description = "RPM, speed, engine temp, load & more",
                    accentColor = Color(0xFF1565C0),
                    enabled = isConnected,
                    actionIcon = Icons.Default.Speed,
                    actionLabel = "View",
                    onClick = { onNavigate(DiagView.LIVE_DATA) }
                )

                // 2. Đọc DTC
                DiagnosticFunctionCard(
                    icon = Icons.Default.Warning,
                    title = "Read Fault Codes (DTC)",
                    description = when {
                        isDtcLoading -> "Scanning fault codes…"
                        dtcList.isEmpty() -> "Scan all ECUs for fault codes"
                        else -> "${dtcList.size} fault code(s): ${dtcList.take(3).joinToString(", ")}"
                    },
                    accentColor = Color(0xFFE65100),
                    enabled = isConnected,
                    actionIcon = Icons.Default.FindReplace,
                    actionLabel = if (isDtcLoading) "..." else "Scan",
                    onClick = { viewModel.readDtcs() }
                )

                // 3. Xóa DTC
                DiagnosticFunctionCard(
                    icon = Icons.Default.Delete,
                    title = "Clear Fault Codes",
                    description = if (dtcList.isEmpty()) "Clear DTCs and reset Check Engine light"
                    else "Clear ${dtcList.size} fault code(s) found",
                    accentColor = Color(0xFFC62828),
                    enabled = isConnected && dtcList.isNotEmpty(),
                    actionIcon = Icons.Default.Delete,
                    actionLabel = "Clear",
                    onClick = { viewModel.clearDtcs() }
                )

                // Raw Frame Monitor — chỉ hiện với Admin
                if (isAdmin) {
                    DiagnosticFunctionCard(
                        icon = Icons.Default.BugReport,
                        title = "Raw Frame Monitor",
                        description = "Inspect raw CAN bytes alongside decoded values",
                        accentColor = Color(0xFF37474F),
                        enabled = true,
                        actionIcon = Icons.Default.BugReport,
                        actionLabel = "Open",
                        onClick = { onNavigate(DiagView.RAW_MONITOR) }
                    )
                }

                // 4. Active Test — Kích hoạt cơ cấu chấp hành
                DiagnosticFunctionCard(
                    icon = Icons.Default.DirectionsCar,
                    title = "Active Test",
                    description = "Kích hoạt đèn, còi, khóa cửa & các cơ cấu chấp hành qua BCM",
                    accentColor = Color(0xFF7C3AED),
                    enabled = true,
                    actionIcon = Icons.Default.DirectionsCar,
                    actionLabel = "Open",
                    onClick = { onNavigate(DiagView.ACTIVE_TEST) }
                )

                // 5–7. Placeholder (sắp có)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComingSoonCard(Modifier.weight(1f), Icons.Default.Info, "ECU & VIN Info")
                    ComingSoonCard(Modifier.weight(1f), Icons.Default.BrokenImage, "Data Graph")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComingSoonCard(Modifier.weight(1f), Icons.Default.Cable, "Sensor Config")
                    ComingSoonCard(Modifier.weight(1f), Icons.Default.BrokenImage, "Data Logger")
                }
            }
        }   // end inner Column (bottom padding)
    }       // end outer Column
    }       // end Scaffold
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-composables (dùng trong Hub)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DiagnosticTopBar(
    config: com.example.bkdiagnostic.protocol.ProtocolConfig?,
    onBack: () -> Unit
) {
    AppTopBar(
        title = config?.displayName ?: "Vehicle Diagnostics",
        subtitle = "Select diagnostic function",
        onBack = onBack
    )
}

@Composable
private fun ConnectionStatusBar(
    state: UsbSerialManager.ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val (icon, label, bgColor, textColor) = when (state) {
        is UsbSerialManager.ConnectionState.Connected ->
            Quadruple(Icons.Default.CheckCircle, "Connected: ${state.deviceName}",
                Color(0xFFE8F5E9), Color(0xFF2E7D32))
        is UsbSerialManager.ConnectionState.Searching,
        is UsbSerialManager.ConnectionState.AwaitingPermission ->
            Quadruple(Icons.Default.FindReplace, "Searching for USB device…",
                Color(0xFFFFF8E1), Color(0xFFF57F17))
        is UsbSerialManager.ConnectionState.Error ->
            Quadruple(Icons.Default.Error, "Error: ${state.message}",
                Color(0xFFFFEBEE), Color(0xFFC62828))
        UsbSerialManager.ConnectionState.Disconnected ->
            Quadruple(Icons.Default.LinkOff, "No OBD2 device connected",
                Color(0xFFF5F5F5), Color(0xFF757575))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = textColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
        when (state) {
            is UsbSerialManager.ConnectionState.Disconnected,
            is UsbSerialManager.ConnectionState.Error -> {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text("Connect", fontSize = 12.sp, color = Color.White) }
            }
            is UsbSerialManager.ConnectionState.Connected -> {
                Button(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text("Disconnect", fontSize = 12.sp, color = Color.White) }
            }
            else -> {}
        }
    }
}

@Composable
private fun DiagnosticFunctionCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    enabled: Boolean,
    actionIcon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1A1A2E))
                Spacer(Modifier.height(3.dp))
                Text(description, fontSize = 12.sp, color = Color(0xFF666680), lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(actionIcon, null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(actionLabel, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ComingSoonCard(modifier: Modifier, icon: ImageVector, title: String) {
    Card(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = Color(0xFF9E9EC8), modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 10.sp, color = Color(0xFF9E9EC8), lineHeight = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
