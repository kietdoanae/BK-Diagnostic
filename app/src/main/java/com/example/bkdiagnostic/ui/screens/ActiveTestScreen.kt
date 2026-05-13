package com.example.bkdiagnostic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.svg.SvgDecoder
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.communication.UsbSerialManager
import com.example.bkdiagnostic.diagnostic.DiagnosticViewModel
import com.example.bkdiagnostic.protocol.DashboardCanConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Constants ─────────────────────────────────────────────────────────────────

/** Native aspect ratio of drawable-nodpi/dashboard_cluster.png (2816 × 1536) */
private const val DASHBOARD_RATIO = 2816f / 1536f

/** Icon diameter as a fraction of the displayed image width */
private const val ICON_SIZE_FRAC = 0.019f

// ── Data model ────────────────────────────────────────────────────────────────

/**
 * One warning indicator overlaid on the cluster image.
 *
 * @param id       Unique key used for animation state.
 * @param label    Human-readable name shown in tooltips / logs.
 * @param asset    Filename inside   app/src/main/assets/warning_icons/.
 * @param color    SVG tint colour (matches real-world lamp colour).
 * @param xFrac    Icon centre X as a fraction of the displayed image width  (0 = left).
 * @param yFrac    Icon centre Y as a fraction of the displayed image height (0 = top).
 * @param canId    CAN arbitration ID  — loaded from assets/can_config/{brand}_{model}_dashboard.json
 * @param canData  8-byte CAN payload  — loaded from JSON config.
 * @param canDataOff  (optional) 8-byte OFF payload — loaded from JSON config.
 */
data class DashboardWarningIcon(
    val id: String,
    val label: String,
    val asset: String,
    val color: Color,
    val xFrac: Float,
    val yFrac: Float,
    val canId: Int = 0x000,
    val canData: ByteArray = ByteArray(8),
    val canDataOff: ByteArray? = null,
) {
    // ByteArray breaks default equals/hashCode — override to compare by id only
    override fun equals(other: Any?) = other is DashboardWarningIcon && id == other.id
    override fun hashCode() = id.hashCode()
}

// ── Icon catalogue ────────────────────────────────────────────────────────────
//
// 4 zones based on annotated dashboard_cluster.png (2816 × 1536 px):
//
//   🔴 RED    │ top-LEFT  bezel │ Đèn chiếu sáng   │ x:0.11–0.46  y:0.09–0.26
//   🔵 BLUE   │ top-RIGHT bezel │ An toàn           │ x:0.47–0.93  y:0.08–0.26
//   🟡 YELLOW │ bottom-LEFT oval│ Động cơ / Túi khí │ x:0.07–0.34  y:0.60–0.74
//   🟢 GREEN  │ bottom-RIGHT oval│ Hệ thống hỗ trợ  │ x:0.63–0.93  y:0.60–0.74
//
// xFrac / yFrac = icon CENTRE as fraction of displayed image (0,0 = top-left).
// Fine-tune on device by adjusting only these two values per icon.
//
// Colour key (SVG tint):
//   0xFFE53935 Red   — critical  (airbag, oil, brake, battery, overheat…)
//   0xFFFFA726 Amber — caution   (ABS, check-engine, stability, tyre, fuel…)
//   0xFF42A5F5 Blue  — info      (high-beam)
//   0xFF66BB6A Green — info      (lamp on / cos)

private val WARNING_ICONS: List<DashboardWarningIcon> = listOf(

    // ════════════════════════════════════════════════════════════════════════
    // 🔴 RED — Đèn chiếu sáng  (top-LEFT bezel)
    // Row y=0.29 | x: 0.28, 0.32  (spacing 0.04)
    // Add fog light here when SVG is ready: xFrac=0.24f, yFrac=0.29f
    // ════════════════════════════════════════════════════════════════════════
    DashboardWarningIcon(
        id = "high_beam", label = "Đèn pha (High Beam)",
        asset = "HighBeamLight-01.svg", color = Color(0xFF42A5F5),
        xFrac = 0.28f, yFrac = 0.29f,
    ),
    DashboardWarningIcon(
        id = "lamp_on", label = "Đèn cos / Đèn bật",
        asset = "LampOnLight-01.svg", color = Color(0xFF66BB6A),
        xFrac = 0.32f, yFrac = 0.29f,
    ),

    // ════════════════════════════════════════════════════════════════════════
    // 🔵 BLUE — An toàn  (top-RIGHT bezel)
    // Row y=0.29 | x: 0.56, 0.68, 0.72
    // ════════════════════════════════════════════════════════════════════════
    DashboardWarningIcon(
        id = "seat_belt", label = "Dây an toàn (Seatbelt)",
        asset = "SeatBelt-01.svg", color = Color(0xFFE53935),
        xFrac = 0.56f, yFrac = 0.29f,
    ),
    DashboardWarningIcon(
        id = "tire", label = "Áp suất lốp (TPMS)",
        asset = "TireLowPressure-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.68f, yFrac = 0.29f,
    ),
    DashboardWarningIcon(
        id = "battery", label = "Ắc quy / Alternator",
        asset = "Battery-01.svg", color = Color(0xFFE53935),
        xFrac = 0.72f, yFrac = 0.29f,
    ),

    // ════════════════════════════════════════════════════════════════════════
    // 🟡 YELLOW — Động cơ & Túi khí  (bottom-LEFT oval)
    // Row y=0.72 | x: 0.17, 0.21, 0.25, 0.29  (spacing 0.04)
    // ════════════════════════════════════════════════════════════════════════
    DashboardWarningIcon(
        id = "engine_chk", label = "Check Engine",
        asset = "Engine-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.17f, yFrac = 0.72f,
    ),
    DashboardWarningIcon(
        id = "airbag", label = "Túi khí (Airbag)",
        asset = "AirBag-01.svg", color = Color(0xFFE53935),
        xFrac = 0.21f, yFrac = 0.72f,
    ),
    DashboardWarningIcon(
        id = "engine", label = "Lỗi động cơ",
        asset = "Engine.svg", color = Color(0xFFFFA726),
        xFrac = 0.25f, yFrac = 0.72f,
    ),
    DashboardWarningIcon(
        id = "overheat", label = "Quá nhiệt động cơ",
        asset = "EngineOverheat-01.svg", color = Color(0xFFE53935),
        xFrac = 0.29f, yFrac = 0.72f,
    ),

    // ════════════════════════════════════════════════════════════════════════
    // 🟢 GREEN — Hệ thống hỗ trợ  (bottom-RIGHT oval)
    // Row y=0.71 | x: 0.71, 0.75, 0.79, 0.83, 0.87  (spacing 0.04)
    // Add 4x4 / HDC / Diff-lock here when SVG is ready
    // ════════════════════════════════════════════════════════════════════════
    DashboardWarningIcon(
        id = "abs", label = "ABS",
        asset = "ABS-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.71f, yFrac = 0.71f,
    ),
    DashboardWarningIcon(
        id = "brake", label = "Cảnh báo phanh",
        asset = "BrakeWarning-01.svg", color = Color(0xFFE53935),
        xFrac = 0.75f, yFrac = 0.71f,
    ),
    DashboardWarningIcon(
        id = "stability", label = "Ổn định thân xe (ESC)",
        asset = "StabilityandTractionControl-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.79f, yFrac = 0.71f,
    ),
    DashboardWarningIcon(
        id = "oil", label = "Áp suất dầu",
        asset = "OilPressureWarning-01.svg", color = Color(0xFFE53935),
        xFrac = 0.83f, yFrac = 0.71f,
    ),
    DashboardWarningIcon(
        id = "fuel", label = "Mức nhiên liệu",
        asset = "Fuel-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.87f, yFrac = 0.71f,
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ActiveTestScreen(
    viewModel: DiagnosticViewModel,
    brandId: String = "ford",
    modelId: String = "ranger",
    onBack: () -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isConnected = connectionState is UsbSerialManager.ConnectionState.Connected
    val message     by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    val scope        = rememberCoroutineScope()
    val context      = LocalContext.current

    // ── Load CAN config từ JSON, merge với icon layout + gauge config ──
    val dashboardConfig = remember(brandId, modelId) {
        DashboardCanConfig.loadFull(context, brandId, modelId)
    }
    val icons = remember(dashboardConfig) {
        WARNING_ICONS.map { icon ->
            val entry = dashboardConfig.icons[icon.id]
            if (entry != null) {
                icon.copy(
                    canId      = entry.canId,
                    canData    = entry.canData,
                    canDataOff = entry.canDataOff
                )
            } else icon
        }
    }
    val gaugeConfig = dashboardConfig.gauges

    // ── Gauge panel state ──────────────────────────────────────────────
    var gaugePanelOpen by remember { mutableStateOf(false) }
    val gaugeStreamActive by viewModel.gaugeStreamActive.collectAsStateWithLifecycle()
    val gaugeRpmValue by viewModel.gaugeRpm.collectAsStateWithLifecycle()
    val gaugeSpeedValue by viewModel.gaugeSpeed.collectAsStateWithLifecycle()
    val gaugeFrameCount by viewModel.gaugeFrameCount.collectAsStateWithLifecycle()

    // ImageLoader with SVG decoder — one instance per screen session
    val svgLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // id → true while the icon's blink sequence is running
    val firingIcons = remember { mutableStateMapOf<String, Boolean>() }

    // One shared InfiniteTransition drives all blinking icons simultaneously
    val blinkTransition = rememberInfiniteTransition(label = "warning_blink")
    val blinkBrightness by blinkTransition.animateFloat(
        initialValue   = 1.00f,
        targetValue    = 0.05f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blink_brightness",
    )

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = Color.Black,
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {

            // ── Dashboard image + icon overlay ───────────────────────────
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

                // maxWidth / maxHeight are in Dp — use .value for arithmetic
                val bw = maxWidth.value
                val bh = maxHeight.value

                // Compute the actual rendered size under ContentScale.Fit
                val imgW: Float
                val imgH: Float
                if (bw / bh > DASHBOARD_RATIO) {
                    imgH = bh; imgW = bh * DASHBOARD_RATIO
                } else {
                    imgW = bw; imgH = bw / DASHBOARD_RATIO
                }
                // Offset of the image's top-left corner inside the Box (in dp)
                val imgX = (bw - imgW) / 2f
                val imgY = (bh - imgH) / 2f

                val iconSz = (imgW * ICON_SIZE_FRAC).dp

                // Cluster background
                Image(
                    painter            = painterResource(id = R.drawable.dashboard_cluster),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Fit,
                )

                // Warning icons (merged with CAN config from JSON)
                icons.forEach { icon ->
                    val firing = firingIcons[icon.id] == true
                    val hasCanConfig = icon.canId != 0

                    // When firing: blink between full-bright and near-black (real lamp-test feel)
                    // When idle + configured: 65% so the dashboard art stays readable
                    // When idle + NOT configured (canId=0x000): 15% — clearly unconfigured
                    // When offline: 25% — clearly inactive
                    val displayAlpha = when {
                        firing           -> blinkBrightness
                        !isConnected     -> 0.25f
                        !hasCanConfig    -> 0.15f
                        else             -> 0.65f
                    }
                    // Glow radius pulses with the blink brightness
                    val glowAlpha = if (firing) blinkBrightness * 0.55f else 0f

                    // Centre position of this icon on screen (dp)
                    val cx = (imgX + icon.xFrac * imgW).dp
                    val cy = (imgY + icon.yFrac * imgH).dp

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = cx - iconSz / 2, y = cy - iconSz / 2)
                            .size(iconSz)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        icon.color.copy(alpha = glowAlpha),
                                        Color.Transparent,
                                    ),
                                ),
                                shape = CircleShape,
                            )
                            .alpha(displayAlpha)
                            .clickable(enabled = isConnected && hasCanConfig && !firing) {
                                scope.launch {
                                    firingIcons[icon.id] = true
                                    // Gửi frame ON
                                    viewModel.sendActiveTestCommand(icon.canId, icon.canData)
                                    delay(2000) // ~11 blink cycles @ 180 ms each
                                    // Gửi frame OFF nếu có
                                    icon.canDataOff?.let { offData ->
                                        viewModel.sendActiveTestCommand(icon.canId, offData)
                                    }
                                    firingIcons[icon.id] = false
                                }
                            },
                    ) {
                        AsyncImage(
                            model              = "file:///android_asset/warning_icons/${icon.asset}",
                            contentDescription = icon.label,
                            imageLoader        = svgLoader,
                            modifier           = Modifier.fillMaxSize(),
                            colorFilter        = ColorFilter.tint(icon.color, BlendMode.SrcIn),
                        )
                    }
                }
            }

            // ── Transparent HUD — top bar ────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Title + subtitle pill
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF7C3AED), CircleShape)
                    )
                    Text(
                        text = "ACTIVE TEST",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                // ── GAUGE toggle pill ────────────────────────────────────
                if (gaugeConfig.hasAny) {
                    val gaugeBg = if (gaugePanelOpen)
                        Color(0xFF7C3AED).copy(alpha = 0.85f)
                    else
                        Color.Black.copy(alpha = 0.55f)
                    val gaugeTint = if (gaugeStreamActive) Color(0xFF4CAF50) else Color.White
                    Row(
                        modifier = Modifier
                            .background(gaugeBg, RoundedCornerShape(12.dp))
                            .clickable { gaugePanelOpen = !gaugePanelOpen }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Gauge Control",
                            tint = gaugeTint,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "GAUGE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (gaugeStreamActive) {
                            Box(Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // USB connection pill
                val (dotColor, connLabel) = when (connectionState) {
                    is UsbSerialManager.ConnectionState.Connected ->
                        Color(0xFF4CAF50) to "ONLINE"
                    is UsbSerialManager.ConnectionState.Searching,
                    is UsbSerialManager.ConnectionState.AwaitingPermission ->
                        Color(0xFFFFA726) to "CONNECTING"
                    else ->
                        Color(0xFFEF5350) to "OFFLINE"
                }
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                    Text(
                        connLabel,
                        color = dotColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // ── Bottom hint — shown only when USB is disconnected & gauge panel closed ──
            if (!isConnected && !gaugePanelOpen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(8.dp).background(Color(0xFFEF5350), CircleShape))
                    Text(
                        text     = "Kết nối USB để kích hoạt cảnh báo",
                        color    = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Gauge Control Panel (slides up from bottom) ──────────────
            AnimatedVisibility(
                visible = gaugePanelOpen,
                enter   = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit    = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                GaugeControlPanel(
                    gaugeConfig    = gaugeConfig,
                    rpmValue       = gaugeRpmValue,
                    speedValue     = gaugeSpeedValue,
                    isStreaming    = gaugeStreamActive,
                    isConnected    = isConnected,
                    frameCount     = gaugeFrameCount,
                    onRpmChange    = viewModel::updateGaugeRpm,
                    onSpeedChange  = viewModel::updateGaugeSpeed,
                    onStartStream  = {
                        viewModel.startGaugeStream(
                            rpmCanId      = gaugeConfig.rpm?.canId,
                            rpmScale      = gaugeConfig.rpm?.scaleFactor ?: 1,
                            rpmStatusByte = gaugeConfig.rpm?.statusByte ?: 0,
                            speedCanId    = gaugeConfig.speed?.canId,
                            speedScale    = gaugeConfig.speed?.scaleFactor ?: 1,
                            intervalMs    = gaugeConfig.intervalMs,
                        )
                    },
                    onStopStream  = {
                        viewModel.stopGaugeStream(
                            rpmCanId   = gaugeConfig.rpm?.canId,
                            speedCanId = gaugeConfig.speed?.canId,
                        )
                    },
                    onClose       = { gaugePanelOpen = false },
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Gauge Control Panel — bottom sheet với RPM & Speed sliders
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun GaugeControlPanel(
    gaugeConfig: DashboardCanConfig.GaugeConfig,
    rpmValue: Int,
    speedValue: Int,
    isStreaming: Boolean,
    isConnected: Boolean,
    frameCount: Int,
    onRpmChange: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xCC0B0F1E),
                        Color(0xEE0B0F1E),
                    )
                ),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ── Header: title + Start/Stop button + Close ────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "GAUGE CONTROL",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                letterSpacing = 1.4.sp,
            )
            if (isStreaming) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF4CAF50).copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
                    Text(
                        "STREAMING · $frameCount",
                        color = Color(0xFF66BB6A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))

            // Start / Stop button
            val btnEnabled = isConnected
            val btnBg = when {
                !btnEnabled  -> Color.White.copy(alpha = 0.08f)
                isStreaming  -> Color(0xFFEF5350)
                else         -> Color(0xFF4CAF50)
            }
            val btnLabel = if (isStreaming) "STOP" else "START"
            val btnIcon  = if (isStreaming) Icons.Filled.Stop else Icons.Filled.PlayArrow
            Row(
                modifier = Modifier
                    .background(btnBg, RoundedCornerShape(10.dp))
                    .clickable(enabled = btnEnabled) {
                        if (isStreaming) onStopStream() else onStartStream()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = btnIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = btnLabel,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp,
                )
            }

            // Close
            IconButton(
                onClick  = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape),
            ) {
                Text("×", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Big digital display: RPM + Speed ─────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            gaugeConfig.rpm?.let {
                DigitalGaugeDisplay(
                    value      = rpmValue,
                    unit       = "RPM",
                    accent     = Color(0xFFEF5350),
                    icon       = Icons.Filled.Speed,
                    modifier   = Modifier.weight(1f),
                )
            }
            gaugeConfig.speed?.let {
                DigitalGaugeDisplay(
                    value      = speedValue,
                    unit       = "km/h",
                    accent     = Color(0xFF42A5F5),
                    icon       = Icons.Filled.Bolt,
                    modifier   = Modifier.weight(1f),
                )
            }
        }

        // ── RPM slider ───────────────────────────────────────────────────
        gaugeConfig.rpm?.let { rpm ->
            GaugeSlider(
                label       = "RPM",
                value       = rpmValue,
                maxValue    = rpm.maxValue,
                unit        = "rpm",
                accent      = Color(0xFFEF5350),
                canIdHex    = "0x%03X".format(rpm.canId),
                onChange    = onRpmChange,
            )
        }

        // ── Speed slider ─────────────────────────────────────────────────
        gaugeConfig.speed?.let { spd ->
            GaugeSlider(
                label       = "SPEED",
                value       = speedValue,
                maxValue    = spd.maxValue,
                unit        = "km/h",
                accent      = Color(0xFF42A5F5),
                canIdHex    = "0x%03X".format(spd.canId),
                onChange    = onSpeedChange,
            )
        }

        if (!isConnected) {
            Text(
                text = "⚠  Kết nối USB để bắt đầu stream",
                color = Color(0xFFFFA726),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DigitalGaugeDisplay(
    value: Int,
    unit: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.18f),
                        accent.copy(alpha = 0.05f),
                    )
                ),
                shape = RoundedCornerShape(14.dp),
            )
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.30f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%,d".format(value),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = unit,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}

@Composable
private fun GaugeSlider(
    label: String,
    value: Int,
    maxValue: Int,
    unit: String,
    accent: Color,
    canIdHex: String,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "CAN $canIdHex",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            // Inline value bubble
            Row(
                modifier = Modifier
                    .background(accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "%,d".format(value),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = unit,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..maxValue.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor       = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )

        // Min / Max scale labels
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text  = "0",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text  = "%,d".format(maxValue),
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
