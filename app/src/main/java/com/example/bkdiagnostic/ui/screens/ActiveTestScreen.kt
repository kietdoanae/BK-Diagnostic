package com.example.bkdiagnostic.ui.screens

import android.graphics.Paint as NativePaint
import android.graphics.Typeface as NativeTypeface
import com.example.bkdiagnostic.ActivityLogger
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoorBack
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.communication.UsbSerialManager
import com.example.bkdiagnostic.diagnostic.DiagnosticViewModel
import com.example.bkdiagnostic.protocol.ActiveTestCategory
import com.example.bkdiagnostic.protocol.ActiveTestCommand
import com.example.bkdiagnostic.protocol.ActiveTestRegistry
import com.example.bkdiagnostic.ui.components.AppTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  Active Test Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActiveTestScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isConnected = connectionState is UsbSerialManager.ConnectionState.Connected

    val commands = remember { ActiveTestRegistry.get(viewModel.brandId, viewModel.modelId) }
    val activeStates = remember { mutableStateMapOf<String, Boolean>() }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            activeStates.filter { it.value }.forEach { (id, _) ->
                commands.find { it.id == id }?.let { cmd ->
                    viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
                }
            }
        }
    }

    fun toggle(cmd: ActiveTestCommand) {
        if (!isConnected) {
            scope.launch { snackbarHost.showSnackbar("OBD2 device not connected!") }
        }
        // Always allow UI simulation even without connection
        val isOn = activeStates[cmd.id] == true
        if (isOn) {
            if (isConnected) viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
            activeStates[cmd.id] = false
        } else {
            ActivityLogger.activeTest(viewModel.brandId, viewModel.modelId, cmd.name)
            if (isConnected) viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOn)
            activeStates[cmd.id] = true
            if (!cmd.isToggle) {
                scope.launch {
                    delay(cmd.pulseDurationMs)
                    if (isConnected) viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
                    activeStates[cmd.id] = false
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08080D))
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            AppTopBar(
                title = "Active Test",
                subtitle = viewModel.protocolConfig?.displayName ?: "Actuator Control",
                onBack = onBack
            )

            // ── Ford Ranger 2019 Cluster (top portion) ────────────────────
            FordRangerCluster2019(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f),
                activeStates = activeStates,
                isConnected = isConnected
            )

            // ── Divider ───────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF1F2937))
            )

            // ── Control Panel (bottom portion, no scroll needed) ──────────
            ActiveTestControlPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f),
                commands = commands,
                activeStates = activeStates,
                isConnected = isConnected,
                onToggle = ::toggle
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Ford Ranger 2019 Instrument Cluster
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FordRangerCluster2019(
    modifier: Modifier,
    activeStates: Map<String, Boolean>,
    isConnected: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkPhase by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(380, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "blink_phase"
    )

    val leftOn  = activeStates["left_turn"]  == true || activeStates["hazard"] == true
    val rightOn = activeStates["right_turn"] == true || activeStates["hazard"] == true
    val highBeam  = activeStates["high_beam"]    == true
    val lowBeam   = activeStates["low_beam"]     == true
    val brakeOn   = activeStates["brake_light"]  == true
    val reverseOn = activeStates["reverse_light"]== true
    val doorOn    = activeStates["door_lock"]    == true || activeStates["door_unlock"] == true
    val hazardOn  = activeStates["hazard"]       == true

    val leftAlpha  = if (leftOn)  blinkPhase else 0.08f
    val rightAlpha = if (rightOn) blinkPhase else 0.08f

    Box(
        modifier = modifier
            .background(Color(0xFF06060A))
    ) {
        // ── Canvas: gauges + warning lights ──────────────────────────────
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            drawFordRangerCluster(
                highBeam = highBeam,
                lowBeam = lowBeam,
                brakeOn = brakeOn,
                reverseOn = reverseOn,
                doorOn = doorOn,
                hazardBlink = hazardOn && blinkPhase > 0.5f
            )
        }

        // ── Left turn signal (animated, top-left) ────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Text(
                    "◀",
                    color = Color(0xFF22C55E).copy(alpha = leftAlpha),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(2.dp))
            }
        }

        // ── Right turn signal (animated, top-right) ──────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Spacer(Modifier.width(2.dp))
                Text(
                    "▶",
                    color = Color(0xFF22C55E).copy(alpha = rightAlpha),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Center digital overlay ────────────────────────────────────────
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gear selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("P", "R", "N", "D").forEach { gear ->
                    Text(
                        gear,
                        color = if (gear == "D") Color.White else Color(0xFF4B5563),
                        fontSize = if (gear == "D") 16.sp else 13.sp,
                        fontWeight = if (gear == "D") FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            // Digital speed
            Text(
                "0",
                color = Color(0xFF60A5FA),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "km/h",
                color = Color(0xFF6B7280),
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            // Status
            Text(
                if (isConnected) "● LIVE" else "○ SIM",
                color = if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // ── High beam indicator (top center) ─────────────────────────────
        if (highBeam) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFF3B82F6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("H", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Canvas drawing — entire cluster
// ────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawFordRangerCluster(
    highBeam: Boolean,
    lowBeam: Boolean,
    brakeOn: Boolean,
    reverseOn: Boolean,
    doorOn: Boolean,
    hazardBlink: Boolean
) {
    val w = size.width
    val h = size.height

    // Main gauge radius: fit both gauges with center gap
    val gaugeR = minOf(w * 0.19f, h * 0.44f)

    // Gauge centers
    val lx = w * 0.225f
    val ly = h * 0.56f
    val rx = w * 0.775f
    val ry = h * 0.56f

    // ── Background gradient ───────────────────────────────────────────────
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF14141E),
                1.0f to Color(0xFF06060A)
            ),
            center = Offset(w / 2, h / 2),
            radius = w * 0.55f
        )
    )

    // ── RPM gauge (left) ─────────────────────────────────────────────────
    drawFordGauge(
        cx = lx, cy = ly, radius = gaugeR,
        value = 0f, minValue = 0f, maxValue = 6f,
        startAngle = 150f, sweep = 240f,
        labels = listOf("0", "1", "2", "3", "4", "5", "6"),
        accentColor = Color(0xFF2563EB),
        dangerFrom = 5.2f,
        unitLabel = "RPM ×1000"
    )

    // ── Speed gauge (right) ──────────────────────────────────────────────
    drawFordGauge(
        cx = rx, cy = ry, radius = gaugeR,
        value = 0f, minValue = 0f, maxValue = 220f,
        startAngle = 150f, sweep = 240f,
        labels = listOf("0", "40", "80", "120", "160", "200", ""),
        accentColor = Color(0xFF2563EB),
        dangerFrom = 190f,
        unitLabel = "km/h"
    )

    // ── Fuel sub-gauge (bottom of RPM) ───────────────────────────────────
    drawSubGauge(
        cx = lx, cy = ly, radius = gaugeR * 0.50f,
        value = 0.2f,
        startAngle = 195f, sweep = 150f,
        activeColor = Color(0xFFF59E0B),
        labelLeft = "E", labelRight = "F"
    )

    // ── Temp sub-gauge (bottom of Speed) ─────────────────────────────────
    drawSubGauge(
        cx = rx, cy = ry, radius = gaugeR * 0.50f,
        value = 0.45f,
        startAngle = 195f, sweep = 150f,
        activeColor = Color(0xFF22C55E),
        labelLeft = "C", labelRight = "H"
    )

    // ── Center warning lights ─────────────────────────────────────────────
    drawWarningLightsPanel(
        cx = w * 0.5f,
        topY = h * 0.06f,
        highBeam = highBeam,
        lowBeam = lowBeam,
        brakeOn = brakeOn,
        reverseOn = reverseOn,
        doorOn = doorOn,
        hazardBlink = hazardBlink,
        dotSize = minOf(w * 0.028f, h * 0.065f)
    )

    // ── "RANGER" watermark ────────────────────────────────────────────────
    drawIntoCanvas { canvas ->
        val paint = NativePaint().apply {
            isAntiAlias = true
            color = 0x14FFFFFF
            textSize = h * 0.09f
            textAlign = NativePaint.Align.CENTER
            typeface = NativeTypeface.create(NativeTypeface.SANS_SERIF, NativeTypeface.BOLD)
            letterSpacing = 0.25f
        }
        canvas.nativeCanvas.drawText("RANGER", w / 2, h * 0.18f, paint)
    }

    // ── Odometer bar ─────────────────────────────────────────────────────
    val barLeft = w * 0.36f
    val barRight = w * 0.64f
    val barY = h * 0.90f
    val barH = h * 0.065f
    drawRoundRect(
        color = Color(0xFF111118),
        topLeft = Offset(barLeft, barY - barH / 2),
        size = Size(barRight - barLeft, barH),
        cornerRadius = CornerRadius(barH / 2),
        style = Stroke(width = 1.5f)
    )
    drawIntoCanvas { canvas ->
        val odoPaint = NativePaint().apply {
            isAntiAlias = true
            color = 0xFF9CA3AF.toInt()
            textSize = h * 0.068f
            textAlign = NativePaint.Align.CENTER
            typeface = NativeTypeface.MONOSPACE
        }
        canvas.nativeCanvas.drawText("ODO  0000000 km", w / 2, barY + odoPaint.textSize * 0.36f, odoPaint)
    }
}

// ── Individual gauge drawing ──────────────────────────────────────────────

private fun DrawScope.drawFordGauge(
    cx: Float, cy: Float, radius: Float,
    value: Float, minValue: Float, maxValue: Float,
    startAngle: Float, sweep: Float,
    labels: List<String>,
    accentColor: Color,
    dangerFrom: Float,
    unitLabel: String
) {
    val trackStroke = radius * 0.095f
    val tl = Offset(cx - radius, cy - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val dangerFraction = ((dangerFrom - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    // Outer chrome ring
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                Color(0xFF2A2A3A), Color(0xFF3D3D50),
                Color(0xFF555568), Color(0xFF3D3D50), Color(0xFF2A2A3A)
            ),
            center = Offset(cx, cy)
        ),
        radius = radius + trackStroke * 0.9f,
        center = Offset(cx, cy),
        style = Stroke(width = trackStroke * 0.6f)
    )

    // Gauge face background (subtle radial)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF16162A),
                0.8f to Color(0xFF0E0E18),
                1.0f to Color(0xFF0A0A12)
            ),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )

    // Track arc (dark)
    drawArc(
        color = Color(0xFF1A1A2E),
        startAngle = startAngle, sweepAngle = sweep,
        useCenter = false, topLeft = tl, size = arcSize,
        style = Stroke(width = trackStroke, cap = StrokeCap.Round)
    )

    // Danger zone marker (dim red background)
    val dangerStartAngle = startAngle + sweep * dangerFraction
    val dangerSweepAngle = sweep * (1f - dangerFraction)
    drawArc(
        color = Color(0x25EF4444),
        startAngle = dangerStartAngle, sweepAngle = dangerSweepAngle,
        useCenter = false, topLeft = tl, size = arcSize,
        style = Stroke(width = trackStroke, cap = StrokeCap.Round)
    )

    // Active fill (blue up to current value)
    val valueSweep = fraction * sweep
    if (valueSweep > 0f) {
        val normalSweep = (sweep * dangerFraction).coerceAtMost(valueSweep)
        drawArc(
            brush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0f to accentColor.copy(alpha = 0.4f),
                    (normalSweep / 360f) to accentColor
                ),
                center = Offset(cx, cy)
            ),
            startAngle = startAngle, sweepAngle = normalSweep,
            useCenter = false, topLeft = tl, size = arcSize,
            style = Stroke(width = trackStroke, cap = StrokeCap.Round)
        )
        if (valueSweep > normalSweep) {
            drawArc(
                color = Color(0xFFEF4444),
                startAngle = startAngle + normalSweep,
                sweepAngle = valueSweep - normalSweep,
                useCenter = false, topLeft = tl, size = arcSize,
                style = Stroke(width = trackStroke, cap = StrokeCap.Round)
            )
        }
    }

    // Major ticks + minor ticks
    val numIntervals = (labels.size - 1).coerceAtLeast(1)
    val minorPerInterval = 4
    val totalMinorSteps = numIntervals * (minorPerInterval + 1)

    for (step in 0..totalMinorSteps) {
        val stepFraction = step.toFloat() / totalMinorSteps
        val angleDeg = startAngle + stepFraction * sweep
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        val isMajor = (step % (minorPerInterval + 1) == 0)
        val innerR = if (isMajor) radius * 0.78f else radius * 0.85f
        val outerR = radius * 0.94f
        val strokeW = if (isMajor) 2.5f else 1.2f
        val alpha = if (isMajor) 0.9f else 0.45f

        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(cx + innerR * cosA, cy + innerR * sinA),
            end   = Offset(cx + outerR * cosA, cy + outerR * sinA),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }

    // Needle
    val needleAngleRad = Math.toRadians((startAngle + fraction * sweep).toDouble())
    val needleLen = radius * 0.72f
    val tipX = cx + needleLen * cos(needleAngleRad).toFloat()
    val tipY = cy + needleLen * sin(needleAngleRad).toFloat()
    // Shadow
    drawLine(
        color = Color.Black.copy(alpha = 0.5f),
        start = Offset(cx + 2f, cy + 2f),
        end   = Offset(tipX + 2f, tipY + 2f),
        strokeWidth = 5f, cap = StrokeCap.Round
    )
    // Body
    drawLine(
        color = Color(0xFFE5E7EB),
        start = Offset(cx, cy),
        end   = Offset(tipX, tipY),
        strokeWidth = 3.5f, cap = StrokeCap.Round
    )
    // Red tip accent (like real cluster)
    val tipStartX = cx + needleLen * 0.65f * cos(needleAngleRad).toFloat()
    val tipStartY = cy + needleLen * 0.65f * sin(needleAngleRad).toFloat()
    drawLine(
        color = Color(0xFFFF4444),
        start = Offset(tipStartX, tipStartY),
        end   = Offset(tipX, tipY),
        strokeWidth = 3.5f, cap = StrokeCap.Round
    )
    // Center hub
    drawCircle(Color(0xFF0C0C18), radius = radius * 0.085f, center = Offset(cx, cy))
    drawCircle(Color(0xFF374151), radius = radius * 0.085f, center = Offset(cx, cy), style = Stroke(width = 2f))
    drawCircle(Color(0xFFD1D5DB), radius = radius * 0.032f, center = Offset(cx, cy))

    // Labels
    drawIntoCanvas { canvas ->
        val labelRadius = radius * 1.14f
        val labelPaint = NativePaint().apply {
            isAntiAlias = true
            color = 0xFFDDDDDD.toInt()
            textSize = radius * 0.21f
            textAlign = NativePaint.Align.CENTER
            typeface = NativeTypeface.create(NativeTypeface.SANS_SERIF, NativeTypeface.BOLD)
        }
        labels.forEachIndexed { i, label ->
            if (label.isEmpty()) return@forEachIndexed
            val lf = i.toFloat() / (labels.size - 1).coerceAtLeast(1)
            val angleDeg = startAngle + lf * sweep
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val lx2 = cx + labelRadius * cos(angleRad).toFloat()
            val ly2 = cy + labelRadius * sin(angleRad).toFloat()
            canvas.nativeCanvas.drawText(label, lx2, ly2 + labelPaint.textSize * 0.36f, labelPaint)
        }

        // Unit label below gauge
        val unitPaint = NativePaint().apply {
            isAntiAlias = true
            color = 0xFF9CA3AF.toInt()
            textSize = radius * 0.17f
            textAlign = NativePaint.Align.CENTER
            typeface = NativeTypeface.create(NativeTypeface.SANS_SERIF, NativeTypeface.NORMAL)
            letterSpacing = 0.1f
        }
        canvas.nativeCanvas.drawText(unitLabel, cx, cy + radius * 1.35f, unitPaint)
    }
}

private fun DrawScope.drawSubGauge(
    cx: Float, cy: Float, radius: Float,
    value: Float,               // 0..1 fraction
    startAngle: Float, sweep: Float,
    activeColor: Color,
    labelLeft: String, labelRight: String
) {
    val trackStroke = radius * 0.14f
    val tl = Offset(cx - radius, cy - radius)
    val arcSize = Size(radius * 2, radius * 2)

    // Track
    drawArc(
        color = Color(0xFF1A1A2A),
        startAngle = startAngle, sweepAngle = sweep,
        useCenter = false, topLeft = tl, size = arcSize,
        style = Stroke(width = trackStroke, cap = StrokeCap.Round)
    )
    // Fill
    val valueSweep = value.coerceIn(0f, 1f) * sweep
    if (valueSweep > 0f) {
        drawArc(
            color = activeColor.copy(alpha = 0.85f),
            startAngle = startAngle, sweepAngle = valueSweep,
            useCenter = false, topLeft = tl, size = arcSize,
            style = Stroke(width = trackStroke, cap = StrokeCap.Round)
        )
    }

    // Needle
    val needleAngleRad = Math.toRadians((startAngle + value * sweep).toDouble())
    val nLen = radius * 0.80f
    drawLine(
        color = activeColor,
        start = Offset(cx, cy),
        end   = Offset(cx + nLen * cos(needleAngleRad).toFloat(), cy + nLen * sin(needleAngleRad).toFloat()),
        strokeWidth = 2f, cap = StrokeCap.Round
    )
    drawCircle(Color(0xFF0C0C18), radius = radius * 0.16f, center = Offset(cx, cy))
    drawCircle(activeColor.copy(alpha = 0.7f), radius = radius * 0.08f, center = Offset(cx, cy))

    // E / F labels
    drawIntoCanvas { canvas ->
        val paint = NativePaint().apply {
            isAntiAlias = true
            color = 0xFF9CA3AF.toInt()
            textSize = radius * 0.30f
            textAlign = NativePaint.Align.CENTER
            typeface = NativeTypeface.create(NativeTypeface.SANS_SERIF, NativeTypeface.BOLD)
        }
        val labelR = radius * 1.18f
        val la = Math.toRadians(startAngle.toDouble())
        val ra = Math.toRadians((startAngle + sweep).toDouble())
        canvas.nativeCanvas.drawText(labelLeft,
            cx + labelR * cos(la).toFloat(), cy + labelR * sin(la).toFloat() + paint.textSize * 0.4f, paint)
        canvas.nativeCanvas.drawText(labelRight,
            cx + labelR * cos(ra).toFloat(), cy + labelR * sin(ra).toFloat() + paint.textSize * 0.4f, paint)
    }
}

private fun DrawScope.drawWarningLightsPanel(
    cx: Float, topY: Float,
    highBeam: Boolean, lowBeam: Boolean,
    brakeOn: Boolean, reverseOn: Boolean,
    doorOn: Boolean, hazardBlink: Boolean,
    dotSize: Float
) {
    data class WLight(val label: String, val color: Int, val active: Boolean)

    val gap = dotSize * 1.9f
    val row1 = listOf(
        WLight("⚠",  0xFFFBBF24.toInt(), hazardBlink),    // hazard
        WLight("E",  0xFFFBBF24.toInt(), false),            // check engine
        WLight("🔋", 0xFFEF4444.toInt(), false),            // battery
        WLight("○",  0xFFEF4444.toInt(), false),            // oil pressure
        WLight("H",  0xFF3B82F6.toInt(), highBeam),         // high beam
    )
    val row2 = listOf(
        WLight("🚗", 0xFFFBBF24.toInt(), false),            // airbag
        WLight("◻",  0xFFFBBF24.toInt(), doorOn),           // door ajar
        WLight("ABS",0xFFFBBF24.toInt(), false),            // ABS
        WLight("⭕",  0xFF22C55E.toInt(), lowBeam),          // low beam
        WLight("BR", 0xFFEF4444.toInt(), brakeOn),          // brake
    )

    val totalW = gap * (row1.size - 1)
    val startX = cx - totalW / 2

    drawIntoCanvas { canvas ->
        val paint = NativePaint().apply {
            isAntiAlias = true
            textAlign = NativePaint.Align.CENTER
            textSize = dotSize * 0.72f
            typeface = NativeTypeface.DEFAULT_BOLD
        }

        fun drawLight(x: Float, y: Float, light: WLight) {
            val alpha = if (light.active) 0xFF else 0x22
            val fillColor = (light.color and 0x00FFFFFF) or (alpha shl 24)
            val borderColor = (light.color and 0x00FFFFFF) or (0x88 shl 24)
            val circlePaint = NativePaint().apply {
                isAntiAlias = true
                color = fillColor
                style = NativePaint.Style.FILL
            }
            val borderPaint = NativePaint().apply {
                isAntiAlias = true
                color = borderColor
                style = NativePaint.Style.STROKE
                strokeWidth = dotSize * 0.12f
            }
            canvas.nativeCanvas.drawCircle(x, y, dotSize * 0.45f, circlePaint)
            canvas.nativeCanvas.drawCircle(x, y, dotSize * 0.45f, borderPaint)
            paint.color = if (light.active) 0xFF111111.toInt() else (light.color and 0x00FFFFFF) or (0x55 shl 24)
            canvas.nativeCanvas.drawText(
                light.label.take(2), x, y + paint.textSize * 0.36f, paint
            )
        }

        val row1Y = topY + dotSize * 0.6f
        val row2Y = topY + dotSize * 1.9f
        row1.forEachIndexed { i, light ->
            drawLight(startX + gap * i, row1Y, light)
        }
        row2.forEachIndexed { i, light ->
            drawLight(startX + gap * i, row2Y, light)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Active Test Control Panel — Category tabs + compact 3-col grid
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActiveTestControlPanel(
    modifier: Modifier,
    commands: List<ActiveTestCommand>,
    activeStates: Map<String, Boolean>,
    isConnected: Boolean,
    onToggle: (ActiveTestCommand) -> Unit
) {
    val availableCategories = remember(commands) {
        ActiveTestCategory.entries.filter { cat -> commands.any { it.category == cat } }
    }
    var selectedCategory by remember { mutableStateOf(availableCategories.firstOrNull() ?: ActiveTestCategory.LIGHTING) }
    val filtered = commands.filter { it.category == selectedCategory }

    Column(
        modifier = modifier.background(Color(0xFF0D0D12))
    ) {
        // ── Category Tab Row ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111118))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableCategories.forEach { cat ->
                val isSelected = cat == selectedCategory
                val count = commands.count { it.category == cat }
                val activeCount = commands.count { it.category == cat && activeStates[it.id] == true }
                CategoryTab(
                    label = cat.label,
                    count = count,
                    activeCount = activeCount,
                    isSelected = isSelected,
                    color = categoryColor(cat),
                    onClick = { selectedCategory = cat }
                )
            }
        }

        // ── Commands grid (3 columns, compact) ────────────────────────────
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không có lệnh nào", color = Color(0xFF6B7280))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { cmd ->
                    CompactCommandButton(
                        command = cmd,
                        isActive = activeStates[cmd.id] == true,
                        isConnected = isConnected,
                        onToggle = { onToggle(cmd) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTab(
    label: String,
    count: Int,
    activeCount: Int,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(200), label = "tab_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) color else Color(0xFF2A2A3A),
        animationSpec = tween(200), label = "tab_border"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                label,
                color = if (isSelected) color else Color(0xFF9CA3AF),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(activeCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompactCommandButton(
    command: ActiveTestCommand,
    isActive: Boolean,
    isConnected: Boolean,
    onToggle: () -> Unit
) {
    val accent = categoryColor(command.category)
    val bgColor by animateColorAsState(
        targetValue = if (isActive) accent.copy(alpha = 0.18f) else Color(0xFF14141C),
        animationSpec = tween(250), label = "btn_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) accent else Color(0xFF222230),
        animationSpec = tween(250), label = "btn_border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) accent.copy(alpha = 0.25f)
                        else Color(0xFF0A0A12)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = commandIcon(command.id),
                    contentDescription = null,
                    tint = if (isActive) accent else Color(0xFF6B7280),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Short name (max 2 lines)
            Text(
                text = command.name.replace("Đèn ", "").replace(" (", "\n("),
                color = if (isActive) Color.White else Color(0xFFD1D5DB),
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2
            )

            // Status pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) Color(0xFF22C55E)
                        else Color(0xFF1F2937)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (!command.isToggle) "●" else if (isActive) "ON" else "OFF",
                    color = if (isActive) Color.White else Color(0xFF6B7280),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun commandIcon(id: String): ImageVector = when {
    id.contains("turn") || id == "hazard"           -> Icons.Default.Warning
    id.contains("beam") || id.contains("light") ||
    id == "interior_lamp"                           -> Icons.Default.Lightbulb
    id == "horn"                                    -> Icons.Default.VolumeUp
    id.contains("unlock")                           -> Icons.Default.DoorBack
    id.contains("lock")                             -> Icons.Default.DoorFront
    id.contains("wiper")                            -> Icons.Default.WaterDrop
    else                                            -> Icons.Default.FlashlightOn
}

private fun categoryColor(category: ActiveTestCategory): Color = when (category) {
    ActiveTestCategory.LIGHTING -> Color(0xFFF59E0B)
    ActiveTestCategory.HORN     -> Color(0xFF3B82F6)
    ActiveTestCategory.LOCKS    -> Color(0xFF8B5CF6)
    ActiveTestCategory.WIPER    -> Color(0xFF06B6D4)
    ActiveTestCategory.OTHER    -> Color(0xFF6B7280)
}
