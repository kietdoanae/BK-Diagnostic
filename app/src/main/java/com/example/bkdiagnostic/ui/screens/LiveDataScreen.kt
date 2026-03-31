package com.example.bkdiagnostic.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.communication.UsbSerialManager
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.diagnostic.DiagnosticViewModel
import com.example.bkdiagnostic.protocol.obd2.OBD2PidDef
import com.example.bkdiagnostic.protocol.obd2.OBD2Pids
import com.example.bkdiagnostic.protocol.obd2.SensorReading
import com.example.bkdiagnostic.ui.theme.LocalAppColors

// ════════════════════════════════════════════════════════════════════════════
//  Màn hình Dữ liệu Thời gian Thực
//  - Arc gauge cho RPM và Speed
//  - Metric cards cho các thông số quan trọng
//  - Danh sách cuộn toàn bộ thông số
// ════════════════════════════════════════════════════════════════════════════

private val GreenOk  = Color(0xFF4CAF50)
private val YellowWarn = Color(0xFFFFB300)
private val RedCrit  = Color(0xFFEF5350)
private val BlueIdle = Color(0xFF42A5F5)

@Composable
fun LiveDataScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current
    val liveData by viewModel.liveData.collectAsStateWithLifecycle()
    val isRunning by viewModel.isLiveDataRunning.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val updateRate by viewModel.updateRate.collectAsStateWithLifecycle()
    val config = viewModel.protocolConfig

    // Tự khởi động/dừng theo vòng đời màn hình
    DisposableEffect(Unit) {
        viewModel.startLiveData()
        onDispose { viewModel.stopLiveData() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
        // ── Top Bar ────────────────────────────────────────────────────────
        LiveDataTopBar(
            vehicleName = config?.displayName ?: "${viewModel.brandId} ${viewModel.modelId}",
            connectionState = connectionState,
            onBack = onBack
        )

        // ── Control Bar ────────────────────────────────────────────────────
        LiveDataControlBar(
            isRunning = isRunning,
            updateRate = updateRate,
            isConnected = connectionState is UsbSerialManager.ConnectionState.Connected,
            onToggle = {
                if (isRunning) viewModel.stopLiveData() else viewModel.startLiveData()
            }
        )

        // ── Scrollable content ─────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Priority Gauges — RPM & Speed
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArcGauge(
                        reading = liveData[OBD2Pids.ENGINE_RPM],
                        pidDef = OBD2Pids.ENGINE_RPM,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ArcGauge(
                        reading = liveData[OBD2Pids.VEHICLE_SPEED],
                        pidDef = OBD2Pids.VEHICLE_SPEED,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // 2. Secondary metric cards (horizontal scroll)
            item {
                val secondaryPids = listOf(
                    OBD2Pids.COOLANT_TEMP, OBD2Pids.ENGINE_LOAD,
                    OBD2Pids.THROTTLE_POS, OBD2Pids.CONTROL_VOLTAGE,
                    OBD2Pids.OIL_TEMP, OBD2Pids.FUEL_LEVEL, OBD2Pids.FUEL_RATE
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(secondaryPids) { pid ->
                        SecondaryMetricCard(pid = pid, reading = liveData[pid])
                    }
                }
            }

            // 3. Tiêu đề "Tất cả thông số"
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        Modifier.weight(1f),
                        color = appColors.dividerColor
                    )
                    Text(
                        "  ${stringResource(R.string.live_data_all_parameters)}  ",
                        color = appColors.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(
                        Modifier.weight(1f),
                        color = appColors.dividerColor
                    )
                }
            }

            // 4. All PIDs
            val allPids = config?.livePids ?: OBD2Pids.ALL
            items(allPids, key = { it.pid }) { pid ->
                ParameterRow(pid = pid, reading = liveData[pid])
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Top Bar
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LiveDataTopBar(
    vehicleName: String,
    connectionState: UsbSerialManager.ConnectionState,
    onBack: () -> Unit
) {
    val dotColor = when (connectionState) {
        is UsbSerialManager.ConnectionState.Connected              -> GreenOk
        is UsbSerialManager.ConnectionState.Searching,
        is UsbSerialManager.ConnectionState.AwaitingPermission     -> YellowWarn
        else                                                       -> RedCrit
    }
    val statusLabel = when (connectionState) {
        is UsbSerialManager.ConnectionState.Connected              -> stringResource(R.string.live_data_status_connected)
        is UsbSerialManager.ConnectionState.Searching              -> stringResource(R.string.live_data_status_searching)
        is UsbSerialManager.ConnectionState.AwaitingPermission     -> stringResource(R.string.live_data_status_awaiting)
        else                                                       -> stringResource(R.string.live_data_status_disconnected)
    }
    AppTopBar(
        title = stringResource(R.string.live_data_title),
        subtitle = vehicleName,
        onBack = onBack,
        trailingContent = {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = statusLabel,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Control Bar
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LiveDataControlBar(
    isRunning: Boolean,
    updateRate: Float,
    isConnected: Boolean,
    onToggle: () -> Unit
) {
    val appColors = LocalAppColors.current
    val scanDot = if (isRunning) {
        val inf = rememberInfiniteTransition(label = "scan")
        val alpha by inf.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(600, easing = LinearEasing), RepeatMode.Reverse
            ), label = "dot_alpha"
        )
        GreenOk.copy(alpha = alpha)
    } else appColors.secondaryText

    val strRunning = stringResource(R.string.live_data_running)
    val strStopped = stringResource(R.string.live_data_stopped)
    val strStop    = stringResource(R.string.live_data_btn_stop)
    val strStart   = stringResource(R.string.live_data_btn_start)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.cardSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(scanDot)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isRunning) strRunning else strStopped,
                color = if (isRunning) GreenOk else appColors.secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isRunning && updateRate > 0f) {
                Text(
                    "%.1f updates/sec".format(updateRate),
                    color = appColors.secondaryText,
                    fontSize = 11.sp
                )
            }
        }
        androidx.compose.material3.Button(
            onClick = onToggle,
            enabled = isConnected || isRunning,
            shape = RoundedCornerShape(8.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFF7B1FA2) else Color(0xFF1565C0),
                disabledContainerColor = Color(0xFF2D3748)
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isRunning) strStop else strStart,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Arc Gauge — đồng hồ đo hình cung tròn (270° sweep)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArcGauge(
    reading: SensorReading?,
    pidDef: OBD2PidDef,
    modifier: Modifier = Modifier
) {
    val value = reading?.value
    val fraction = if (value != null) {
        ((value - pidDef.minValue) / (pidDef.maxValue - pidDef.minValue))
            .toFloat().coerceIn(0f, 1f)
    } else 0f

    val appColors = LocalAppColors.current
    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "arc_${pidDef.pid}"
    )
    val arcColor = gaugeColor(animFraction, pidDef)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.cardSurface),
        contentAlignment = Alignment.Center
    ) {
        // Canvas vẽ cung tròn
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            val strokeW = size.minDimension * 0.085f
            val pad = strokeW / 2 + 4f
            val arcTopLeft = Offset(pad, pad)
            val arcSize = Size(size.width - 2 * pad, size.height - 2 * pad)

            // Vùng xanh / vàng / đỏ ở background (3 zone)
            val zoneColors = listOf(
                Color(0xFF1B5E20).copy(alpha = 0.4f),  // 0–60% green zone
                Color(0xFFE65100).copy(alpha = 0.3f),  // 60–80% yellow zone
                Color(0xFFB71C1C).copy(alpha = 0.3f)   // 80–100% red zone
            )
            val zones = listOf(0f to 162f, 162f to 216f, 216f to 270f)
            zones.forEachIndexed { i, (start, sweep) ->
                drawArc(
                    color = zoneColors[i],
                    startAngle = 135f + start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = strokeW * 0.4f, cap = StrokeCap.Butt)
                )
            }

            // Background track
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 135f, sweepAngle = 270f,
                useCenter = false,
                topLeft = arcTopLeft, size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Value arc (colored)
            if (animFraction > 0.001f) {
                drawArc(
                    color = arcColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animFraction,
                    useCenter = false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                // Điểm sáng ở đầu cung
                val endAngleRad = Math.toRadians((135f + 270f * animFraction).toDouble())
                val cx = size.width / 2
                val cy = size.height / 2
                val r = (size.minDimension - 2 * pad) / 2
                val dotX = (cx + r * Math.cos(endAngleRad)).toFloat()
                val dotY = (cy + r * Math.sin(endAngleRad)).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = strokeW * 0.45f,
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Text ở giữa gauge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp) // đẩy lên một chút khỏi vùng gap
        ) {
            // Icon nhỏ
            Icon(
                imageVector = pidIcon(pidDef),
                contentDescription = null,
                tint = arcColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            // Giá trị lớn
            Text(
                text = if (value != null) formatSensorValue(value, pidDef) else "--",
                color = appColors.primaryText,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            // Đơn vị
            Text(
                text = pidDef.unit,
                color = arcColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            // Tên PID
            Text(
                text = pidDef.name,
                color = appColors.secondaryText,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Secondary Metric Card — thẻ nhỏ cho các thông số phụ
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SecondaryMetricCard(
    pid: OBD2PidDef,
    reading: SensorReading?
) {
    val value = reading?.value
    val fraction = if (value != null) {
        ((value - pid.minValue) / (pid.maxValue - pid.minValue)).toFloat().coerceIn(0f, 1f)
    } else 0f
    val appColors = LocalAppColors.current
    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(300),
        label = "card_${pid.pid}"
    )
    val color = readingColor(value, pid, appColors.secondaryText)

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.cardSurface)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon + unit row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    pidIcon(pid), contentDescription = null,
                    tint = color, modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(pid.unit, color = appColors.secondaryText, fontSize = 10.sp)
            }
            // Value
            Text(
                text = if (value != null) formatSensorValue(value, pid) else "--",
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Mini progress bar
            Column {
                Text(
                    pid.name,
                    color = appColors.secondaryText,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(appColors.dividerColor)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animFraction)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Parameter Row — hàng trong danh sách tất cả thông số
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ParameterRow(pid: OBD2PidDef, reading: SensorReading?) {
    val value = reading?.value
    val fraction = if (value != null) {
        ((value - pid.minValue) / (pid.maxValue - pid.minValue)).toFloat().coerceIn(0f, 1f)
    } else 0f
    val appColors = LocalAppColors.current
    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(300),
        label = "row_${pid.pid}"
    )
    val color = readingColor(value, pid, appColors.secondaryText)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appColors.cardSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(pidIcon(pid), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            // Name
            Text(
                pid.name, color = appColors.primaryText, fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            // Value + unit
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (value != null) formatSensorValue(value, pid) else "--",
                    color = color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(pid.unit, color = appColors.secondaryText, fontSize = 10.sp)
            }
        }
        // Progress bar
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(appColors.dividerColor)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.6f), color)
                        )
                    )
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helper functions
// ════════════════════════════════════════════════════════════════════════════

/** Màu arc gauge theo PID + fraction */
private fun gaugeColor(fraction: Float, pid: OBD2PidDef): Color = when (pid.pid) {
    OBD2Pids.ENGINE_RPM.pid -> when {
        fraction < 0.44f -> BlueIdle        // 0–3500 rpm
        fraction < 0.75f -> YellowWarn      // 3500–6000 rpm
        else -> RedCrit                     // 6000+ rpm
    }
    OBD2Pids.VEHICLE_SPEED.pid -> when {
        fraction < 0.28f -> GreenOk         // 0–60 km/h
        fraction < 0.47f -> YellowWarn      // 60–100 km/h
        else -> RedCrit                     // 100+ km/h
    }
    OBD2Pids.COOLANT_TEMP.pid -> when {
        fraction < 0.45f -> BlueIdle
        fraction < 0.72f -> GreenOk
        fraction < 0.84f -> YellowWarn
        else -> RedCrit
    }
    OBD2Pids.OIL_TEMP.pid -> when {
        fraction < 0.33f -> BlueIdle
        fraction < 0.70f -> GreenOk
        fraction < 0.85f -> YellowWarn
        else -> RedCrit
    }
    OBD2Pids.CONTROL_VOLTAGE.pid -> when {
        fraction < 0.18f -> RedCrit
        fraction < 0.38f -> YellowWarn
        fraction < 0.85f -> GreenOk
        else -> YellowWarn
    }
    else -> when {
        fraction < 0.60f -> BlueIdle
        fraction < 0.80f -> YellowWarn
        else -> RedCrit
    }
}

/** Màu giá trị dùng cho cards và rows */
private fun readingColor(value: Double?, pid: OBD2PidDef, fallback: Color = BlueIdle): Color {
    if (value == null) return fallback
    val fraction = ((value - pid.minValue) / (pid.maxValue - pid.minValue)).toFloat().coerceIn(0f, 1f)
    return gaugeColor(fraction, pid)
}

/** Format giá trị hiển thị */
private fun formatSensorValue(value: Double, pid: OBD2PidDef): String = when (pid.unit) {
    "rpm"  -> "%,.0f".format(value)
    "°C"   -> "%.0f".format(value)
    "%"    -> "%.1f".format(value)
    "V"    -> "%.2f".format(value)
    "km/h" -> "%.0f".format(value)
    "kPa"  -> "%.1f".format(value)
    "g/s"  -> "%.1f".format(value)
    "L/h"  -> "%.2f".format(value)
    else   -> "%.1f".format(value)
}

/** Icon tương ứng từng PID */
private fun pidIcon(pid: OBD2PidDef): ImageVector = when (pid.pid) {
    OBD2Pids.ENGINE_RPM.pid        -> Icons.Default.Speed
    OBD2Pids.VEHICLE_SPEED.pid     -> Icons.Default.DirectionsCar
    OBD2Pids.COOLANT_TEMP.pid      -> Icons.Default.Water
    OBD2Pids.OIL_TEMP.pid          -> Icons.Default.Thermostat
    OBD2Pids.INTAKE_AIR_TEMP.pid   -> Icons.Default.Thermostat
    OBD2Pids.AMBIENT_TEMP.pid      -> Icons.Default.Thermostat
    OBD2Pids.ENGINE_LOAD.pid       -> Icons.Default.Tune
    OBD2Pids.THROTTLE_POS.pid      -> Icons.Default.Tune
    OBD2Pids.FUEL_LEVEL.pid        -> Icons.Default.LocalGasStation
    OBD2Pids.FUEL_RATE.pid         -> Icons.Default.LocalGasStation
    OBD2Pids.CONTROL_VOLTAGE.pid   -> Icons.Default.BatteryFull
    OBD2Pids.MAF_RATE.pid          -> Icons.Default.Air
    OBD2Pids.INTAKE_MAP.pid        -> Icons.Default.Compress
    OBD2Pids.BARO_PRESSURE.pid     -> Icons.Default.Compress
    else                           -> Icons.Default.Speed
}
