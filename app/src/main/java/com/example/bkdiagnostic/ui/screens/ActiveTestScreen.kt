package com.example.bkdiagnostic.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Constants ─────────────────────────────────────────────────────────────────

/** Native aspect ratio of drawable-nodpi/dashboard_cluster.png (2816 × 1536) */
private const val DASHBOARD_RATIO = 2816f / 1536f

/** Icon diameter as a fraction of the displayed image width */
private const val ICON_SIZE_FRAC = 0.038f

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
 * @param canId    CAN arbitration ID  — TODO: assign before shipping.
 * @param canData  8-byte CAN payload  — TODO: assign before shipping.
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
) {
    // ByteArray breaks default equals/hashCode — override to compare by id only
    override fun equals(other: Any?) = other is DashboardWarningIcon && id == other.id
    override fun hashCode() = id.hashCode()
}

// ── Icon catalogue ────────────────────────────────────────────────────────────
//
// Positions are visual estimates based on the empty zones in dashboard_cluster.png.
// Tune xFrac / yFrac on a real device after first build; the rest of the code
// is coordinate-system agnostic.
//
// Colour key:
//   Red   0xFFE53935 — critical  (engine, brakes, airbag, oil, battery…)
//   Amber 0xFFFFA726 — caution   (ABS, check-engine, stability, tyre, fuel…)
//   Blue  0xFF42A5F5 — info      (high-beam active)
//   Green 0xFF66BB6A — info      (lamp on)

private val WARNING_ICONS: List<DashboardWarningIcon> = listOf(

    // ── Left zone — under multimedia panel ───────────────────────────────────
    DashboardWarningIcon(
        id = "lamp_on", label = "Đèn bật",
        asset = "LampOnLight-01.svg",  color = Color(0xFF66BB6A),
        xFrac = 0.09f, yFrac = 0.80f,
    ),
    DashboardWarningIcon(
        id = "high_beam", label = "Đèn pha",
        asset = "HighBeamLight-01.svg", color = Color(0xFF42A5F5),
        xFrac = 0.17f, yFrac = 0.80f,
    ),
    DashboardWarningIcon(
        id = "seat_belt", label = "Dây an toàn",
        asset = "SeatBelt-01.svg", color = Color(0xFFE53935),
        xFrac = 0.25f, yFrac = 0.80f,
    ),

    // ── Centre-left — below speedometer ──────────────────────────────────────
    DashboardWarningIcon(
        id = "abs", label = "ABS",
        asset = "ABS-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.34f, yFrac = 0.77f,
    ),
    DashboardWarningIcon(
        id = "brake", label = "Cảnh báo phanh",
        asset = "BrakeWarning-01.svg", color = Color(0xFFE53935),
        xFrac = 0.42f, yFrac = 0.77f,
    ),

    // ── Centre-right — below speedometer ─────────────────────────────────────
    DashboardWarningIcon(
        id = "airbag", label = "Túi khí",
        asset = "AirBag-01.svg", color = Color(0xFFE53935),
        xFrac = 0.56f, yFrac = 0.77f,
    ),
    DashboardWarningIcon(
        id = "engine_chk", label = "Check Engine",
        asset = "Engine-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.64f, yFrac = 0.77f,
    ),

    // ── Centre row 2 — between speedometer and PRNDL ─────────────────────────
    DashboardWarningIcon(
        id = "battery", label = "Pin / Alternator",
        asset = "Battery-01.svg", color = Color(0xFFE53935),
        xFrac = 0.38f, yFrac = 0.87f,
    ),
    DashboardWarningIcon(
        id = "engine", label = "Động cơ",
        asset = "Engine.svg", color = Color(0xFFFFA726),
        xFrac = 0.46f, yFrac = 0.87f,
    ),
    DashboardWarningIcon(
        id = "overheat", label = "Quá nhiệt động cơ",
        asset = "EngineOverheat-01.svg", color = Color(0xFFE53935),
        xFrac = 0.54f, yFrac = 0.87f,
    ),
    DashboardWarningIcon(
        id = "oil", label = "Áp suất dầu",
        asset = "OilPressureWarning-01.svg", color = Color(0xFFE53935),
        xFrac = 0.62f, yFrac = 0.87f,
    ),

    // ── Right zone — under RPM / ODO panel ───────────────────────────────────
    DashboardWarningIcon(
        id = "stability", label = "Ổn định thân xe",
        asset = "StabilityandTractionControl-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.74f, yFrac = 0.80f,
    ),
    DashboardWarningIcon(
        id = "tire", label = "Áp suất lốp",
        asset = "TireLowPressure-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.82f, yFrac = 0.80f,
    ),
    DashboardWarningIcon(
        id = "fuel", label = "Mức nhiên liệu",
        asset = "Fuel-01.svg", color = Color(0xFFFFA726),
        xFrac = 0.90f, yFrac = 0.80f,
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ActiveTestScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isConnected = connectionState is UsbSerialManager.ConnectionState.Connected
    val message     by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    val scope        = rememberCoroutineScope()
    val context      = LocalContext.current

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

                // Warning icons
                WARNING_ICONS.forEach { icon ->
                    val firing = firingIcons[icon.id] == true

                    // When firing: blink between full-bright and near-black (real lamp-test feel)
                    // When idle:   dim to 65% so the dashboard art stays readable
                    // When offline: 25% — clearly inactive
                    val displayAlpha = when {
                        firing       -> blinkBrightness
                        !isConnected -> 0.25f
                        else         -> 0.65f
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
                            .clickable(enabled = isConnected && !firing) {
                                scope.launch {
                                    firingIcons[icon.id] = true
                                    viewModel.sendActiveTestCommand(icon.canId, icon.canData)
                                    delay(2000) // ~11 blink cycles @ 180 ms each
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
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text       = "Active Test",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 18.sp,
                    modifier   = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )

                Spacer(Modifier.weight(1f))

                // USB connection pill
                val (dotColor, connLabel) = when (connectionState) {
                    is UsbSerialManager.ConnectionState.Connected ->
                        Color(0xFF4CAF50) to "Đã kết nối"
                    is UsbSerialManager.ConnectionState.Searching,
                    is UsbSerialManager.ConnectionState.AwaitingPermission ->
                        Color(0xFFFFA726) to "Đang kết nối…"
                    else ->
                        Color(0xFFEF5350) to "Chưa kết nối"
                }
                Row(
                    modifier              = Modifier
                        .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                    Text(connLabel, color = Color.White, fontSize = 13.sp)
                }
            }

            // ── Bottom hint — shown only when USB is disconnected ────────
            if (!isConnected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text     = "Kết nối USB để kích hoạt cảnh báo",
                        color    = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
