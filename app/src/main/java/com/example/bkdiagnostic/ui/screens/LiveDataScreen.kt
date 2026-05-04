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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
//  Modern Dashboard Style LiveData Screen
//  - Glassmorphism cards with subtle gradients
//  - Dual arc gauges (RPM + Speed) with glow effect
//  - 2-column metric grid with color-coded indicators
//  - Clean parameter list with animated progress bars
// ════════════════════════════════════════════════════════════════════════════

private val GreenOk    = Color(0xFF00E676)
private val YellowWarn = Color(0xFFFFAB00)
private val RedCrit    = Color(0xFFFF1744)
private val BlueIdle   = Color(0xFF448AFF)

// Glassmorphism surface colors
private val GlassBg     = Color(0xFF1A1D2E)
private val GlassCard   = Color(0xFF242840)
private val GlassBorder = Color(0xFF3A3F5C)
private val GlassAccent = Color(0xFF6C63FF)

@Composable
fun LiveDataScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit
) {
    val liveData by viewModel.liveData.collectAsStateWithLifecycle()
    val isRunning by viewModel.isLiveDataRunning.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val updateRate by viewModel.updateRate.collectAsStateWithLifecycle()
    val config = viewModel.protocolConfig

    DisposableEffect(Unit) {
        viewModel.startLiveData()
        onDispose { viewModel.stopLiveData() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GlassBg, Color(0xFF0D1020))))
    ) {
        // ── Top Bar ────────────────────────────────────────────────────────
        LiveDataTopBar(
            vehicleName = config?.displayName ?: "${viewModel.brandId} ${viewModel.modelId}",
            connectionState = connectionState,
            onBack = onBack
        )

        // ── Scrollable Dashboard ───────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Control Bar + Status
            item {
                LiveDataControlBar(
                    isRunning = isRunning,
                    updateRate = updateRate,
                    isConnected = connectionState is UsbSerialManager.ConnectionState.Connected,
                    onToggle = {
                        if (isRunning) viewModel.stopLiveData() else viewModel.startLiveData()
                    }
                )
            }

            // 2. Dual Gauges — RPM & Speed
            item {
                Row(
                    Modifier.fillMaxWidth().height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

            // 3. Metric Grid — 2 columns
            item {
                val gridPids = listOf(
                    OBD2Pids.COOLANT_TEMP, OBD2Pids.ENGINE_LOAD,
                    OBD2Pids.THROTTLE_POS, OBD2Pids.CONTROL_VOLTAGE,
                    OBD2Pids.OIL_TEMP, OBD2Pids.FUEL_LEVEL
                )
                MetricGrid(pids = gridPids, liveData = liveData)
            }

            // 4. Section divider
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = GlassBorder)
                    Text(
                        "  ${stringResource(R.string.live_data_all_parameters)}  ",
                        color = Color(0xFF6B7294),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                    HorizontalDivider(Modifier.weight(1f), color = GlassBorder)
                }
            }

            // 5. All parameters list
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
        is UsbSerialManager.ConnectionState.Connected          -> GreenOk
        is UsbSerialManager.ConnectionState.Searching,
        is UsbSerialManager.ConnectionState.AwaitingPermission -> YellowWarn
        else                                                   -> RedCrit
    }
    val statusLabel = when (connectionState) {
        is UsbSerialManager.ConnectionState.Connected          -> stringResource(R.string.live_data_status_connected)
        is UsbSerialManager.ConnectionState.Searching          -> stringResource(R.string.live_data_status_searching)
        is UsbSerialManager.ConnectionState.AwaitingPermission -> stringResource(R.string.live_data_status_awaiting)
        else                                                   -> stringResource(R.string.live_data_status_disconnected)
    }
    AppTopBar(
        title = stringResource(R.string.live_data_title),
        subtitle = vehicleName,
        onBack = onBack,
        trailingContent = {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pulsing dot when connected
                    if (connectionState is UsbSerialManager.ConnectionState.Connected) {
                        val inf = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by inf.animateFloat(
                            initialValue = 0.4f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(800, easing = LinearEasing), RepeatMode.Reverse
                            ), label = "pulse_alpha"
                        )
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(dotColor.copy(alpha = pulseAlpha))
                        )
                    } else {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(dotColor)
                        )
                    }
                    Text(statusLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Control Bar — sleek status strip
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LiveDataControlBar(
    isRunning: Boolean,
    updateRate: Float,
    isConnected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        val statusColor = if (isRunning) GreenOk else Color(0xFF6B7294)
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(statusColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isRunning) stringResource(R.string.live_data_running) else stringResource(R.string.live_data_stopped),
                color = if (isRunning) GreenOk else Color(0xFF6B7294),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isRunning && updateRate > 0f) {
                Text(
                    "%.1f updates/sec".format(updateRate),
                    color = Color(0xFF6B7294),
                    fontSize = 11.sp
                )
            }
        }
        // Play/Pause button
        androidx.compose.material3.Button(
            onClick = onToggle,
            enabled = isConnected || isRunning,
            shape = RoundedCornerShape(10.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFF7B1FA2) else GlassAccent,
                disabledContainerColor = Color(0xFF2D3148)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isRunning) stringResource(R.string.live_data_btn_stop) else stringResource(R.string.live_data_btn_start),
                color = Color.White, fontSize = 13.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Arc Gauge — modern dashboard style with glow
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

    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "arc_${pidDef.pid}"
    )
    val arcColor = gaugeColor(animFraction, pidDef)

    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(GlassCard),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize().padding(14.dp)
        ) {
            val strokeW = size.minDimension * 0.09f
            val pad = strokeW / 2 + 4f
            val arcTopLeft = Offset(pad, pad)
            val arcSize = Size(size.width - 2 * pad, size.height - 2 * pad)

            // Zone arcs (subtle background)
            val zoneColors = listOf(
                Color(0xFF00E676).copy(alpha = 0.12f),
                Color(0xFFFFAB00).copy(alpha = 0.10f),
                Color(0xFFFF1744).copy(alpha = 0.10f)
            )
            val zones = listOf(0f to 162f, 162f to 216f, 216f to 270f)
            zones.forEachIndexed { i, (start, sweep) ->
                drawArc(zoneColors[i], 135f + start, sweep, false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(strokeW * 0.35f, cap = StrokeCap.Butt))
            }

            // Background track
            drawArc(Color.White.copy(alpha = 0.06f), 135f, 270f, false,
                topLeft = arcTopLeft, size = arcSize,
                style = Stroke(strokeW, cap = StrokeCap.Round))

            // Value arc with glow
            if (animFraction > 0.001f) {
                // Glow layer
                drawArc(arcColor.copy(alpha = 0.3f), 135f, 270f * animFraction, false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(strokeW * 1.6f, cap = StrokeCap.Round))
                // Main arc
                drawArc(arcColor, 135f, 270f * animFraction, false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(strokeW, cap = StrokeCap.Round))
                // Endpoint dot
                val endAngleRad = Math.toRadians((135f + 270f * animFraction).toDouble())
                val cx = size.width / 2
                val cy = size.height / 2
                val r = (size.minDimension - 2 * pad) / 2
                val dotX = (cx + r * Math.cos(endAngleRad)).toFloat()
                val dotY = (cy + r * Math.sin(endAngleRad)).toFloat()
                drawCircle(Color.White.copy(alpha = 0.9f), strokeW * 0.45f, Offset(dotX, dotY))
                drawCircle(arcColor, strokeW * 0.3f, Offset(dotX, dotY))
            }
        }

        // Center text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 18.dp)
        ) {
            Icon(pidIcon(pidDef), null, tint = arcColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (value != null) formatSensorValue(value, pidDef) else "--",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(pidDef.unit, color = arcColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(pidDef.name, color = Color(0xFF6B7294), fontSize = 9.sp,
                textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Metric Grid — 2-column grid for secondary parameters
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MetricGrid(pids: List<OBD2PidDef>, liveData: Map<OBD2PidDef, SensorReading>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pids.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { pid ->
                    MetricCard(
                        pid = pid,
                        reading = liveData[pid],
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty cell if odd number
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCard(pid: OBD2PidDef, reading: SensorReading?, modifier: Modifier = Modifier) {
    val value = reading?.value
    val fraction = if (value != null) {
        ((value - pid.minValue) / (pid.maxValue - pid.minValue)).toFloat().coerceIn(0f, 1f)
    } else 0f
    val animFraction by animateFloatAsState(fraction, tween(350), label = "mc_${pid.pid}")
    val color = readingColor(value, pid, Color(0xFF6B7294))

    Row(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassCard)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(pidIcon(pid), null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        // Data
        Column(Modifier.weight(1f)) {
            Text(pid.name, color = Color(0xFF6B7294), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (value != null) formatSensorValue(value, pid) else "--",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                Text(pid.unit, color = color, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(4.dp))
            // Mini progress bar
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(animFraction).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Parameter Row — clean list item
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ParameterRow(pid: OBD2PidDef, reading: SensorReading?) {
    val value = reading?.value
    val fraction = if (value != null) {
        ((value - pid.minValue) / (pid.maxValue - pid.minValue)).toFloat().coerceIn(0f, 1f)
    } else 0f
    val animFraction by animateFloatAsState(fraction, tween(350), label = "row_${pid.pid}")
    val color = readingColor(value, pid, Color(0xFF6B7294))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassCard)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(pidIcon(pid), null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(pid.name, color = Color(0xFFB0B6D0), fontSize = 12.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (value != null) formatSensorValue(value, pid) else "--",
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(pid.unit, color = Color(0xFF6B7294), fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.05f))) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(animFraction).clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color)))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════════════

private fun gaugeColor(fraction: Float, pid: OBD2PidDef): Color = when (pid.pid) {
    OBD2Pids.ENGINE_RPM.pid -> when {
        fraction < 0.44f -> BlueIdle
        fraction < 0.75f -> YellowWarn
        else -> RedCrit
    }
    OBD2Pids.VEHICLE_SPEED.pid -> when {
        fraction < 0.28f -> GreenOk
        fraction < 0.47f -> YellowWarn
        else -> RedCrit
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

private fun readingColor(value: Double?, pid: OBD2PidDef, fallback: Color = BlueIdle): Color {
    if (value == null) return fallback
    val fraction = ((value - pid.minValue) / (pid.maxValue - pid.minValue)).toFloat().coerceIn(0f, 1f)
    return gaugeColor(fraction, pid)
}

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