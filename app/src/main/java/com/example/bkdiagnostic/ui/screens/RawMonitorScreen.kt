package com.example.bkdiagnostic.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.diagnostic.DiagnosticViewModel
import com.example.bkdiagnostic.diagnostic.RawFrameEntry
import com.example.bkdiagnostic.ui.components.AppTopBar
import kotlinx.coroutines.delay

// ── Màu sắc ─────────────────────────────────────────────────────────────────
private val BgTerminal  = Color(0xFF060D14)
private val BgRow       = Color(0xFF0D1A26)
private val BgRowAlt    = Color(0xFF091320)
private val BgHeader    = Color(0xFF050F1C)
private val BgBottomBar = Color(0xFF040C16)
private val ColorGreen  = Color(0xFF00E676)
private val ColorYellow = Color(0xFFFFD54F)
private val ColorGray   = Color(0xFF546E7A)
private val ColorTeal   = Color(0xFF80CBC4)
private val ColorRed    = Color(0xFFEF5350)
private val ColorBlue   = Color(0xFF42A5F5)
private val ColorOrange = Color(0xFFFF7043)
private val ColorMuted  = Color(0xFF37474F)

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
    val rawLog by viewModel.rawFrameLog.collectAsStateWithLifecycle()
    val responseCanId = viewModel.protocolConfig?.responseCanId
    val context = LocalContext.current

    var paused by remember { mutableStateOf(false) }
    var frozenLog by remember { mutableStateOf<List<RawFrameEntry>>(emptyList()) }
    val displayLog = if (paused) frozenLog else rawLog

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTerminal)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        AppTopBar(
            title = stringResource(R.string.raw_monitor_title),
            subtitle = if (paused)
                "⏸  Paused · ${displayLog.size} frames captured"
            else
                "●  Live · ${displayLog.size} frames",
            onBack = onBack
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
                val name = exportToCsv(context, displayLog)
                exportMessage = if (name != null)
                    "$strExportSuccess $name"
                else
                    strExportError
            }
        )
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
    val isEcuResponse = responseCanId != null && entry.canId == responseCanId
    val isDecoded     = entry.decoded != "—"

    val accentColor = when {
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
        // Chỉ thị màu
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (isDecoded || isEcuResponse) 1f else 0.35f))
        )
        Spacer(Modifier.width(6.dp))

        // # seq
        Text(
            text = entry.seq.toString(),
            color = ColorMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.width(W_SEQ - 11.dp)   // trừ dot + spacer
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.TableChart,
                contentDescription = null,
                tint = ColorGray.copy(alpha = 0.30f),
                modifier = Modifier.size(56.dp)
            )
            Text(stringResource(R.string.raw_monitor_empty_title), color = ColorGray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.raw_monitor_empty_message),
                color = ColorGray.copy(alpha = 0.55f),
                fontSize = 12.sp
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

private fun exportToCsv(context: Context, frames: List<RawFrameEntry>): String? {
    if (frames.isEmpty()) return null
    val ts = System.currentTimeMillis()
    val filename = "can_frames_$ts.csv"

    val sb = StringBuilder()
    sb.appendLine("SEQ,TIMESTAMP_MS,ADDRESS,CAN_FRAME_HEX,DELAY_MS,DECODED")
    frames.forEachIndexed { i, e ->
        val delay = if (i > 0) e.timestampMs - frames[i - 1].timestampMs else 0L
        val id  = "0x${e.canId.toString(16).uppercase().padStart(3, '0')}"
        val hex = e.rawBytes.joinToString(" ") {
            it.toUByte().toInt().toString(16).uppercase().padStart(2, '0')
        }
        sb.appendLine("${e.seq},${e.timestampMs},$id,\"$hex\",$delay,\"${e.decoded}\"")
    }
    val csv = sb.toString()

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
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            cv.clear()
            cv.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, cv, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            java.io.File(dir, filename).writeText(csv)
        }
        filename
    } catch (e: Exception) {
        null
    }
}
