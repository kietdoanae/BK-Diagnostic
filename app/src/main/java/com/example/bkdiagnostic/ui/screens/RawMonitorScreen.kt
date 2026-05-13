package com.example.bkdiagnostic.ui.screens

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.diagnostic.CanSendEntry
import com.example.bkdiagnostic.diagnostic.CanSenderViewModel
import com.example.bkdiagnostic.diagnostic.CanSenderViewModelFactory
import com.example.bkdiagnostic.diagnostic.DiagnosticViewModel
import com.example.bkdiagnostic.diagnostic.Direction
import com.example.bkdiagnostic.diagnostic.RawFrameEntry
import com.example.bkdiagnostic.diagnostic.SendStatus
import com.example.bkdiagnostic.supabaseClient
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.lab.LabEvidenceRepository
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Màu sắc ─────────────────────────────────────────────────────────────────
// Dark "engineering terminal" look — phù hợp với hex bytes và monospace.
// Giữ tone đen-xanh đậm cho data area (đọc dễ trên ánh đèn workshop),
// thêm tone xanh navy cho chrome (top bar / tabs / stats) cho hiện đại hơn.
private val BgTerminal  = Color(0xFF060D14)   // background data area
private val BgRow       = Color(0xFF0D1A26)
private val BgRowAlt    = Color(0xFF091320)
private val BgHeader    = Color(0xFF0E1E33)   // chrome chính (navy đậm)
private val BgChrome    = Color(0xFF12243C)   // chrome thứ cấp (stats/tabs)
private val BgBottomBar = Color(0xFF0A1626)   // action bar
private val ColorGreen  = Color(0xFF00E676)
private val ColorYellow = Color(0xFFFFD54F)
private val ColorGray   = Color(0xFF607D8B)
private val ColorTeal   = Color(0xFF80CBC4)
private val ColorRed    = Color(0xFFEF5350)
private val ColorBlue   = Color(0xFF42A5F5)
private val ColorOrange = Color(0xFFFF7043)
private val ColorMuted  = Color(0xFF37474F)
private val ColorAccent = Color(0xFF5BC8F5)   // accent cho selected tab + stat values

// ── Column widths (chia sẻ giữa header và data rows) ────────────────────────
private val W_SEQ     = 48.dp
private val W_ADDR    = 78.dp
private val W_DELAY   = 86.dp
private val W_DECODED = 210.dp

// ════════════════════════════════════════════════════════════════════════════
//  RawMonitorScreen
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun RawMonitorScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit
) {
    val canSenderVm: CanSenderViewModel = viewModel(
        factory = CanSenderViewModelFactory(LocalContext.current.applicationContext as Application)
    )

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTerminal)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        AppTopBar(
            title = stringResource(R.string.raw_monitor_title),
            subtitle = null,
            onBack = onBack
        )

        // ── Modern segmented tab row ────────────────────────────────────────
        ModernTabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // ── Tab content ───────────────────────────────────────────────────────
        when (selectedTab) {
            0 -> MonitorTabContent(viewModel = viewModel)
            1 -> CanSendTab(vm = canSenderVm)
        }
    }
}

// ── Tab segmented control ───────────────────────────────────────────────────

@Composable
private fun ModernTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        Triple("Monitor",  Icons.Filled.Visibility, 0),
        Triple("Gửi CAN",  Icons.AutoMirrored.Filled.Send, 1)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgHeader)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (label, icon, idx) ->
            val selected = selectedTab == idx
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = if (selected) ColorAccent.copy(alpha = 0.18f) else BgChrome,
                shape = RoundedCornerShape(10.dp),
                border = if (selected)
                    androidx.compose.foundation.BorderStroke(1.dp, ColorAccent.copy(alpha = 0.5f))
                else null,
                onClick = { onTabSelected(idx) }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) ColorAccent else ColorGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = if (selected) ColorAccent else ColorGray,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Monitor tab (existing content)
// ════════════════════════════════════════════════════════════════════════════

/** Filter chiều cho Monitor tab + CSV export */
private enum class DirFilter { BOTH, TX, RX }

@Composable
private fun MonitorTabContent(viewModel: DiagnosticViewModel) {
    val rawLog by viewModel.rawFrameLog.collectAsStateWithLifecycle()
    val responseCanId = viewModel.protocolConfig?.responseCanId
    val context = LocalContext.current

    var paused by remember { mutableStateOf(false) }
    var frozenLog by remember { mutableStateOf<List<RawFrameEntry>>(emptyList()) }
    var dirFilter by remember { mutableStateOf(DirFilter.BOTH) }

    // Lấy data đang hiển thị: pause → frozen, không pause → live
    val sourceLog = if (paused) frozenLog else rawLog
    // Áp filter direction (chỉ ảnh hưởng hiển thị + export, NOT Supabase upload)
    val displayLog = remember(sourceLog, dirFilter) {
        when (dirFilter) {
            DirFilter.BOTH -> sourceLog
            DirFilter.TX   -> sourceLog.filter { it.direction == Direction.TX }
            DirFilter.RX   -> sourceLog.filter { it.direction == Direction.RX }
        }
    }
    // Count statistics riêng cho TX / RX (luôn tính trên sourceLog full)
    val (txCount, rxCount) = remember(sourceLog) {
        var tx = 0; var rx = 0
        sourceLog.forEach { if (it.direction == Direction.TX) tx++ else rx++ }
        tx to rx
    }

    var exportMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Resolve strings needed inside non-composable lambdas
    val strExportSuccess = stringResource(R.string.raw_monitor_export_success)
    val strExportError   = stringResource(R.string.raw_monitor_export_error)

    // Auto-scroll xuống cuối khi có frame mới (chỉ khi đang live)
    LaunchedEffect(rawLog.size, paused) {
        if (!paused && rawLog.isNotEmpty()) {
            listState.animateScrollToItem(rawLog.size - 1)
        }
    }

    // Tự ẩn toast sau 3 giây
    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            delay(3000)
            exportMessage = null
        }
    }

    // Tính frame rate trong 2 giây gần nhất
    val nowMs = remember(displayLog) { System.currentTimeMillis() }
    val recentRate = remember(displayLog, paused) {
        if (displayLog.size < 2) 0
        else {
            val cutoff = nowMs - 2000
            displayLog.count { it.timestampMs >= cutoff } / 2
        }
    }
    val uniqueIds = remember(displayLog) { displayLog.distinctBy { it.canId }.size }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Stats strip ──────────────────────────────────────────────────────
        StatsStrip(
            paused = paused,
            frameCount = displayLog.size,
            framesPerSec = recentRate,
            uniqueIds = uniqueIds
        )

        // ── Direction filter segmented control ───────────────────────────────
        DirectionFilterBar(
            current  = dirFilter,
            txCount  = txCount,
            rxCount  = rxCount,
            onChange = { dirFilter = it }
        )

        // ── Header cột cố định ───────────────────────────────────────────────
        FrameTableHeader()

        // ── Danh sách frame + empty state ────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (displayLog.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    itemsIndexed(
                        items = displayLog,
                        key = { _, entry -> entry.seq }
                    ) { index, entry ->
                        val delayMs = if (index > 0)
                            entry.timestampMs - displayLog[index - 1].timestampMs
                        else 0L
                        RawFrameRow(
                            entry = entry,
                            delayMs = delayMs,
                            responseCanId = responseCanId,
                            isEven = index % 2 == 0
                        )
                    }
                }
            }

            // Toast thông báo export
            exportMessage?.let { msg ->
                val isSuccess = msg.startsWith("✓")
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = if (isSuccess) Color(0xFF1B5E20) else Color(0xFF7F0000),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
                    )
                }
            }
        }

        // ── Bottom action bar ─────────────────────────────────────────────────
        BottomActionBar(
            paused = paused,
            frameCount = displayLog.size,
            onTogglePause = {
                if (!paused) frozenLog = rawLog
                paused = !paused
            },
            onClear = {
                viewModel.clearRawLog()
                if (paused) frozenLog = emptyList()
            },
            onExport = {
                val result = exportToCsv(context, displayLog, viewModel.brandId, viewModel.modelId)
                if (result == null) {
                    exportMessage = strExportError
                } else {
                    // Bước 1: báo lưu cục bộ thành công, đang tải lên
                    exportMessage = "$strExportSuccess ${result.filename} · Đang tải lên…"
                    // Upload lên Supabase Storage + ghi record — cập nhật toast sau khi xong
                    CoroutineScope(Dispatchers.IO).launch {
                        val labState     = LabModeManager.state.value
                        val labSessionId = (labState as? LabModeState.Active)?.sessionId
                        val uploaded = uploadExportToStorage(
                            filename     = result.filename,
                            bytes        = result.bytes,
                            brandId      = viewModel.brandId,
                            modelId      = viewModel.modelId,
                            displayName  = viewModel.protocolConfig?.displayName
                                           ?: "${viewModel.brandId} ${viewModel.modelId}",
                            frameCount   = displayLog.size,
                            labSessionId = labSessionId
                        )
                        // Cập nhật toast từ background thread (Compose State là thread-safe)
                        exportMessage = if (uploaded)
                            "☁ ${result.filename} · Đã đồng bộ"
                        else
                            "⚠ Lưu OK · Tải lên thất bại (xem Logcat)"
                        // Also push the exported frames as lab evidence batch
                        if (labSessionId != null) {
                            LabEvidenceRepository.pushRawFrameBatch(labSessionId, displayLog)
                        }
                    }
                }
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Direction filter bar — TX / RX / Both
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DirectionFilterBar(
    current: DirFilter,
    txCount: Int,
    rxCount: Int,
    onChange: (DirFilter) -> Unit,
) {
    val options = listOf(
        Triple(DirFilter.BOTH, "Both",          txCount + rxCount),
        Triple(DirFilter.TX,   "▲ Request",     txCount),
        Triple(DirFilter.RX,   "▼ Response",    rxCount),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgChrome)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FILTER",
            color = ColorMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.width(4.dp))
        options.forEach { (value, label, count) ->
            val selected = current == value
            val accent = when (value) {
                DirFilter.TX -> ColorBlue
                DirFilter.RX -> ColorGreen
                DirFilter.BOTH -> ColorAccent
            }
            Surface(
                modifier = Modifier.height(28.dp),
                color = if (selected) accent.copy(alpha = 0.20f) else BgRow,
                shape = RoundedCornerShape(7.dp),
                border = if (selected)
                    androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.6f))
                else null,
                onClick = { onChange(value) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) accent else ColorGray,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "$count",
                        color = if (selected) accent.copy(alpha = 0.9f) else ColorMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CAN Sender tab
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun CanSendTab(vm: CanSenderViewModel) {
    val canIdInput by vm.canIdInput.collectAsState()
    val dataBytesInput by vm.dataBytesInput.collectAsState()
    val intervalMs by vm.intervalMs.collectAsState()
    val isRepeating by vm.isRepeating.collectAsState()
    val dlcPreview by vm.dlcPreview.collectAsState()
    val inputError by vm.inputError.collectAsState()
    val usbConnected by vm.usbConnected.collectAsState()
    val sendLog by vm.sendLog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTerminal)
    ) {
        // USB connection indicator (compact strip ngay sau tabs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgChrome)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = if (usbConnected) ColorGreen else ColorRed,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (usbConnected) "USB CONNECTED" else "USB DISCONNECTED",
                color = if (usbConnected) ColorGreen else ColorRed,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            if (isRepeating) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ColorOrange.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "REPEATING @ ${intervalMs}ms",
                        color = ColorOrange,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Input fields section ──────────────────────────────────────────────
        SectionLabel(text = "FRAME COMPOSER")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = BgRow),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // CAN ID field
                OutlinedTextField(
                    value = canIdInput,
                    onValueChange = { vm.onCanIdChanged(it) },
                    label = { Text("CAN ID (hex)") },
                    trailingIcon = { Text("/ 7FF", fontSize = 12.sp, color = ColorGray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Data bytes field
                OutlinedTextField(
                    value = dataBytesInput,
                    onValueChange = { vm.onDataBytesChanged(it) },
                    label = { Text("Data (hex, cách nhau bởi dấu cách)") },
                    placeholder = { Text("02 01 0C", color = ColorGray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // DLC preview
                Text(
                    text = "DLC: $dlcPreview",
                    color = ColorTeal,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Error message
                if (inputError != null) {
                    Text(
                        text = inputError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                // Buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gửi 1 lần
                    Button(
                        onClick = { vm.sendOnce() },
                        enabled = inputError == null && usbConnected,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorBlue.copy(alpha = 0.15f),
                            contentColor = ColorBlue,
                            disabledContainerColor = Color.White.copy(alpha = 0.04f),
                            disabledContentColor = ColorGray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Gửi 1 lần", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Toggle repeat
                    val repeatEnabled = isRepeating || (inputError == null && usbConnected)
                    Button(
                        onClick = { vm.toggleRepeat() },
                        enabled = repeatEnabled,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRepeating) ColorRed.copy(alpha = 0.15f) else ColorGreen.copy(alpha = 0.15f),
                            contentColor = if (isRepeating) ColorRed else ColorGreen,
                            disabledContainerColor = Color.White.copy(alpha = 0.04f),
                            disabledContentColor = ColorGray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isRepeating) "Dừng" else "Lặp lại",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Xóa log
                    Button(
                        onClick = { vm.clearLog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorOrange.copy(alpha = 0.15f),
                            contentColor = ColorOrange
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Xóa log", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Interval field (shown only when not repeating)
                if (!isRepeating) {
                    OutlinedTextField(
                        value = intervalMs,
                        onValueChange = { vm.onIntervalChanged(it) },
                        label = { Text("Interval (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Send log section ──────────────────────────────────────────────────
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SEND LOG",
                color = ColorAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ColorAccent.copy(alpha = 0.14f)
            ) {
                Text(
                    text = sendLog.size.toString(),
                    color = ColorAccent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorMuted.copy(alpha = 0.30f))
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(
                items = sendLog,
                key = { it.seq }
            ) { entry ->
                CanSendEntryRow(entry = entry)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section label
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = ColorAccent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  CanSendEntryRow
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun CanSendEntryRow(entry: CanSendEntry) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeStr = sdf.format(Date(entry.timestampMs))
    val idStr = "ID: ${entry.canId.toString(16).uppercase().padStart(3, '0')}"
    val dataStr = "[${entry.dlc}] ${entry.dataBytes.joinToString(" ") { "%02X".format(it) }}"

    val (statusColor, statusText) = when (entry.status) {
        SendStatus.PENDING -> ColorGray to "PENDING"
        SendStatus.ACK     -> ColorGreen to "ACK ${entry.roundTripMs}ms"
        SendStatus.ERROR   -> ColorRed to "ERROR: ${entry.errorMsg}"
        SendStatus.TIMEOUT -> ColorOrange to "TIMEOUT"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgRow, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Main send row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = timeStr,
                color = ColorMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = idStr,
                color = ColorTeal,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = dataStr,
                color = Color(0xFF78909C),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusColor.copy(alpha = 0.18f)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Response sub-rows
        entry.responses.forEach { resp ->
            val respIdHex = resp.canId.toString(16).uppercase().padStart(3, '0')
            val respDataStr = resp.dataBytes.joinToString(" ") { "%02X".format(it) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 3.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "↳ RESPONSE ID: $respIdHex [${resp.dataBytes.size}] $respDataStr (+${resp.receivedAfterMs}ms)",
                    color = ColorYellow,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Stats strip — frame count / rate / unique IDs / status
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatsStrip(
    paused: Boolean,
    frameCount: Int,
    framesPerSec: Int,
    uniqueIds: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgChrome)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatPill(
            icon  = Icons.Filled.History,
            label = "FRAMES",
            value = frameCount.toString(),
            color = ColorAccent,
            modifier = Modifier.weight(1f)
        )
        StatPill(
            icon  = Icons.Filled.Timeline,
            label = "RATE",
            value = if (paused) "—" else "$framesPerSec/s",
            color = if (paused) ColorGray else ColorGreen,
            modifier = Modifier.weight(1f)
        )
        StatPill(
            icon  = Icons.Filled.Cable,
            label = "IDs",
            value = uniqueIds.toString(),
            color = ColorTeal,
            modifier = Modifier.weight(1f)
        )
        // Live/Paused indicator
        Surface(
            modifier = Modifier
                .weight(0.9f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (paused) ColorOrange.copy(alpha = 0.16f) else ColorGreen.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                (if (paused) ColorOrange else ColorGreen).copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = if (paused) ColorOrange else ColorGreen,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (paused) "PAUSED" else "LIVE",
                    color = if (paused) ColorOrange else ColorGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = BgRow
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    color = ColorGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = value,
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Header cột cố định
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FrameTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgHeader)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // weight() phải gọi trong RowScope — truyền Modifier từ đây xuống HeaderCell
        HeaderCell(text = stringResource(R.string.raw_monitor_col_seq),       modifier = Modifier.width(W_SEQ),    align = TextAlign.End)
        Spacer(Modifier.width(12.dp))
        HeaderCell(text = stringResource(R.string.raw_monitor_col_address),   modifier = Modifier.width(W_ADDR))
        HeaderCell(text = stringResource(R.string.raw_monitor_col_can_frame), modifier = Modifier.weight(1f))
        HeaderCell(text = stringResource(R.string.raw_monitor_col_delay),     modifier = Modifier.width(W_DELAY),  align = TextAlign.End)
        Spacer(Modifier.width(14.dp))
        HeaderCell(text = stringResource(R.string.raw_monitor_col_decoded),   modifier = Modifier.width(W_DECODED))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ColorMuted.copy(alpha = 0.30f))
    )
}

@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        color = ColorGray,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.2.sp,
        textAlign = align,
        modifier = modifier
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Một hàng dữ liệu
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun RawFrameRow(
    entry: RawFrameEntry,
    delayMs: Long,
    responseCanId: Int?,
    isEven: Boolean
) {
    val isTx          = entry.direction == Direction.TX
    val isEcuResponse = responseCanId != null && entry.canId == responseCanId && !isTx
    val isDecoded     = entry.decoded.isNotEmpty() && entry.decoded != "—"

    // Màu chỉ thị direction + status
    val accentColor = when {
        isTx          -> ColorBlue        // TX = xanh
        isDecoded     -> ColorGreen
        isEcuResponse -> ColorYellow
        else          -> ColorGray
    }

    val hexStr = entry.rawBytes.joinToString(" ") {
        it.toUByte().toInt().toString(16).uppercase().padStart(2, '0')
    }
    val idStr    = "0x${entry.canId.toString(16).uppercase().padStart(3, '0')}"
    val delayStr = formatDelay(delayMs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEven) BgRow else BgRowAlt)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chỉ thị màu (direction)
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (isDecoded || isEcuResponse || isTx) 1f else 0.35f))
        )
        Spacer(Modifier.width(4.dp))

        // TX/RX badge
        Text(
            text = if (isTx) "▲" else "▼",
            color = accentColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(10.dp)
        )

        // # seq
        Text(
            text = entry.seq.toString(),
            color = ColorMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.width(W_SEQ - 21.dp)   // trừ dot + spacer + badge
        )
        Spacer(Modifier.width(12.dp))

        // ADDRESS
        Text(
            text = idStr,
            color = if (isEcuResponse) ColorTeal else Color(0xFF607D8B),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(W_ADDR)
        )

        // CAN FRAME
        Text(
            text = hexStr,
            color = Color(0xFF78909C),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )

        // DELAY
        Text(
            text = delayStr,
            color = when {
                delayMs == 0L -> ColorMuted
                delayMs > 500 -> ColorOrange.copy(alpha = 0.85f)
                else          -> Color(0xFF455A64)
            },
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.width(W_DELAY)
        )
        Spacer(Modifier.width(14.dp))

        // DECODED
        Text(
            text = if (isDecoded) entry.decoded else "",
            color = accentColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(W_DECODED)
        )
    }
}

private fun formatDelay(ms: Long): String = when {
    ms <= 0L  -> "—"
    ms < 1000 -> "${ms} ms"
    else      -> "${"%.1f".format(ms / 1000.0)} s"
}

// ════════════════════════════════════════════════════════════════════════════
//  Empty state
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Icon with subtle glow ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(ColorAccent.copy(alpha = 0.06f))
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(ColorAccent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.TableChart,
                        contentDescription = null,
                        tint = ColorAccent.copy(alpha = 0.65f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.raw_monitor_empty_title),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.raw_monitor_empty_message),
                color = ColorGray,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Bottom action bar
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun BottomActionBar(
    paused: Boolean,
    frameCount: Int,
    onTogglePause: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ColorMuted.copy(alpha = 0.20f))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBottomBar)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── STOP / RESUME ────────────────────────────────────────────────────
        ActionButton(
            label = if (paused) stringResource(R.string.raw_monitor_btn_resume) else stringResource(R.string.raw_monitor_btn_stop),
            icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Stop,
            color = if (paused) ColorGreen else ColorRed,
            modifier = Modifier.weight(1.8f),
            onClick = onTogglePause
        )

        // ── CLEAR ────────────────────────────────────────────────────────────
        ActionButton(
            label = stringResource(R.string.raw_monitor_btn_clear),
            icon = Icons.Filled.Delete,
            color = ColorOrange,
            modifier = Modifier.weight(1f),
            onClick = onClear,
            enabled = frameCount > 0
        )

        // ── EXPORT ───────────────────────────────────────────────────────────
        ActionButton(
            label = stringResource(R.string.raw_monitor_btn_export),
            icon = Icons.Filled.FileDownload,
            color = ColorBlue,
            modifier = Modifier.weight(1.4f),
            onClick = onExport,
            enabled = frameCount > 0
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color,
            disabledContainerColor = Color.White.copy(alpha = 0.04f),
            disabledContentColor = ColorGray
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Export to CSV
// ════════════════════════════════════════════════════════════════════════════

private data class ExportResult(val filename: String, val bytes: ByteArray)

private fun exportToCsv(
    context: Context,
    frames: List<RawFrameEntry>,
    brandId: String = "",
    modelId: String = ""
): ExportResult? {
    if (frames.isEmpty()) return null
    val sdfFilename = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    val datePart = sdfFilename.format(Date())
    // CAN_FORD_RANGER_2026-04-09_14-30-22.csv
    val brandPart = brandId.uppercase().ifBlank { "UNKNOWN" }
    val modelPart = modelId.uppercase().ifBlank { "UNKNOWN" }
    val filename = "CAN_${brandPart}_${modelPart}_${datePart}.csv"

    // Định dạng timestamp thành chuỗi đọc được — dùng Locale.US tránh lỗi AM/PM một số locale
    val sdfExport = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    val sb = StringBuilder()
    // UTF-8 BOM — bắt buộc để Excel trên Windows nhận đúng encoding UTF-8
    sb.append('\uFEFF')
    // Phase 2: thêm DIRECTION + SOURCE để phân biệt TX/RX và phân loại nguồn
    sb.appendLine("SEQ,TIME,TIMESTAMP_MS,DIRECTION,SOURCE,ADDRESS,CAN_FRAME_HEX,DELAY_MS,DECODED")
    frames.forEachIndexed { i, e ->
        val delay = if (i > 0) e.timestampMs - frames[i - 1].timestampMs else 0L
        val id    = "0x${e.canId.toString(16).uppercase().padStart(3, '0')}"
        val hex   = e.rawBytes.joinToString(" ") {
            it.toUByte().toInt().toString(16).uppercase().padStart(2, '0')
        }
        val timeStr = sdfExport.format(Date(e.timestampMs))
        val dir     = e.direction.name           // TX hoặc RX
        val srcEsc  = e.source.replace("\"", "\"\"")
        // Bọc TIME, SOURCE và DECODED trong dấu nháy kép; escape dấu nháy kép bên trong
        val decodedEscaped = e.decoded.replace("\"", "\"\"")
        sb.appendLine("${e.seq},\"$timeStr\",${e.timestampMs},$dir,\"$srcEsc\",$id,\"$hex\",$delay,\"$decodedEscaped\"")
    }
    // Xuất UTF-8 — BOM ở đầu đã giúp Excel nhận đúng
    val csvBytes = sb.toString().toByteArray(Charsets.UTF_8)

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv
            ) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(csvBytes) }
            cv.clear()
            cv.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, cv, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            java.io.File(dir, filename).writeBytes(csvBytes)
        }
        ExportResult(filename, csvBytes)
    } catch (e: Exception) {
        null
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Upload to Supabase Storage + ghi export_records
// ════════════════════════════════════════════════════════════════════════════

@kotlinx.serialization.Serializable
private data class ExportRecord(
    @kotlinx.serialization.SerialName("user_id")         val userId:       String,
    val username:      String,
    val filename:      String,
    @kotlinx.serialization.SerialName("brand_id")        val brandId:      String,
    @kotlinx.serialization.SerialName("model_id")        val modelId:      String,
    @kotlinx.serialization.SerialName("display_name")    val displayName:  String,
    @kotlinx.serialization.SerialName("frame_count")     val frameCount:   Int,
    @kotlinx.serialization.SerialName("file_size_bytes") val fileSizeBytes: Long,
    @kotlinx.serialization.SerialName("storage_path")    val storagePath:  String
)

/** Trả về true nếu cả upload lẫn insert record đều thành công. */
private suspend fun uploadExportToStorage(
    filename:     String,
    bytes:        ByteArray,
    brandId:      String,
    modelId:      String,
    displayName:  String,
    frameCount:   Int,
    labSessionId: String? = null
): Boolean {
    val user = supabaseClient.auth.currentUserOrNull()
    if (user == null) {
        android.util.Log.w("RawMonitor", "Upload skipped: no active session")
        return false
    }
    val path = "${user.id}/$filename"

    return try {
        // 1. Upload file — khai báo content type rõ ràng để khớp whitelist bucket
        android.util.Log.d("RawMonitor", "Uploading $filename (${bytes.size} bytes) → $path")
        supabaseClient.storage.from("exports").upload(path, bytes) {
            upsert = true          // tránh lỗi nếu file cùng tên đã tồn tại
            contentType = io.ktor.http.ContentType.parse("text/csv")
        }
        android.util.Log.d("RawMonitor", "✓ Uploaded $filename to Storage")

        // 2. Lấy username từ JWT metadata
        val meta = user.userMetadata?.toString() ?: ""
        val username = Regex(""""username"\s*:\s*"([^"]+)"""")
            .find(meta)?.groupValues?.get(1)
            ?: user.email?.substringBefore("@")
            ?: "unknown"

        // 3. Insert record vào bảng export_records
        android.util.Log.d("RawMonitor", "Inserting export_record for user=$username file=$filename")
        supabaseClient.postgrest["export_records"].insert(
            ExportRecord(
                userId        = user.id,
                username      = username,
                filename      = filename,
                brandId       = brandId,
                modelId       = modelId,
                displayName   = displayName,
                frameCount    = frameCount,
                fileSizeBytes = bytes.size.toLong(),
                storagePath   = path
            )
        )
        android.util.Log.d("RawMonitor", "✓ Saved export_record: $displayName / $filename")
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e   // không nuốt CancellationException
    } catch (e: Exception) {
        android.util.Log.e("RawMonitor", "✗ Export upload/record failed: ${e.message}", e)
        false
    }
}
