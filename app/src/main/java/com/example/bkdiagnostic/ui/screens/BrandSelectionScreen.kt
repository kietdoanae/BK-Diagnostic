package com.example.bkdiagnostic.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import com.example.bkdiagnostic.ActivityLogger
import com.example.bkdiagnostic.R
import androidx.compose.ui.text.style.TextAlign
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.ui.components.AppTopBarChip

data class CarBrand(
    val id: String,
    val name: String,
    @StringRes val originRes: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    @field:DrawableRes val logoRes: Int
)

/** Brand IDs đã hỗ trợ đầy đủ — các hãng khác hiện "Under Development" */
private val availableBrandIds: Set<String> = setOf("ford")

val carBrands = listOf(
    CarBrand("toyota",     "Toyota",     R.string.brand_origin_japan,       Color(0xFFCC0000), Color(0xFF8B0000), R.drawable.brand_toyota),
    CarBrand("honda",      "Honda",      R.string.brand_origin_japan,       Color(0xFFE40012), Color(0xFF9C0010), R.drawable.brand_honda),
    CarBrand("suzuki",     "Suzuki",     R.string.brand_origin_japan,       Color(0xFF1A5EA8), Color(0xFF0D3D6E), R.drawable.brand_suzuki),
    CarBrand("mazda",      "Mazda",      R.string.brand_origin_japan,       Color(0xFF910000), Color(0xFF5C0000), R.drawable.brand_mazda),
    CarBrand("hyundai",    "Hyundai",    R.string.brand_origin_south_korea, Color(0xFF002C5F), Color(0xFF004A9F), R.drawable.brand_hyundai),
    CarBrand("kia",        "KIA",        R.string.brand_origin_south_korea, Color(0xFF05141F), Color(0xFFBB162B), R.drawable.brand_kia),
    CarBrand("mitsubishi", "Mitsubishi", R.string.brand_origin_japan,       Color(0xFFE4002B), Color(0xFF9C001D), R.drawable.brand_mitsubishi),
    CarBrand("ford",       "Ford",       R.string.brand_origin_usa,         Color(0xFF003087), Color(0xFF0055C8), R.drawable.brand_ford),
)

@Composable
fun BrandSelectionScreen(
    onBrandSelected: (CarBrand) -> Unit,
    onBack: () -> Unit
) {
    var comingSoonBrand by remember { mutableStateOf<CarBrand?>(null) }

    // ── Coming Soon dialog ────────────────────────────────────────────────────
    comingSoonBrand?.let { brand ->
        AlertDialog(
            onDismissRequest = { comingSoonBrand = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(brand.primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = brand.primaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = brand.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color(0xFF1A1A2E)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF3CD)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Construction,
                                contentDescription = null,
                                tint = Color(0xFF856404),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.label_under_development),
                                color = Color(0xFF856404),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Text(
                        text = "${brand.name} ${stringResource(R.string.brand_coming_soon_message)}",
                        color = Color(0xFF6B6B80),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { comingSoonBrand = null },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brand.primaryColor)
                ) {
                    Text(stringResource(R.string.btn_got_it), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF2F7))
    ) {
        // ─── Top bar ─────────────────────────────────────────────────────────
        AppTopBar(
            title = stringResource(R.string.brand_selection_title),
            subtitle = stringResource(R.string.brand_selection_subtitle),
            onBack = onBack,
            trailingContent = {
                AppTopBarChip {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.brand_selection_breadcrumb),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        )

        // ─── Brand grid (4×2, fills remaining screen) ────────────────────────
        val brandRows = remember { carBrands.chunked(4) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            brandRows.forEach { rowBrands ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowBrands.forEach { brand ->
                        BrandCard(
                            brand = brand,
                            isAvailable = brand.id in availableBrandIds,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = {
                                if (brand.id in availableBrandIds) {
                                    ActivityLogger.brandSelected(brand.name)
                                    onBrandSelected(brand)
                                } else {
                                    ActivityLogger.brandComingSoon(brand.name)
                                    comingSoonBrand = brand
                                }
                            }
                        )
                    }
                    // Fill empty slots if last row has < 4 items
                    repeat(4 - rowBrands.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandCard(
    brand: CarBrand,
    isAvailable: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accentColor = if (isAvailable) brand.primaryColor else Color(0xFF9E9E9E)
    val accentSecondary = if (isAvailable) brand.secondaryColor else Color(0xFFBDBDBD)

    // Memoize brushes — shader compilation khá tốn kém nếu tạo mới mỗi recomposition
    val topBarBrush = remember(accentColor, accentSecondary) {
        Brush.horizontalGradient(colors = listOf(accentColor, accentSecondary))
    }
    val logoBgBrush = remember(accentColor, isAvailable) {
        Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = if (isAvailable) 0.07f else 0.03f),
                Color(0xFFF8FAFF)
            )
        )
    }
    val separatorBrush = remember(accentColor, accentSecondary) {
        Brush.horizontalGradient(
            colors = listOf(accentColor.copy(alpha = 0.4f), accentSecondary.copy(alpha = 0.1f))
        )
    }
    val leftBarBrush = remember(accentColor, accentSecondary) {
        Brush.verticalGradient(colors = listOf(accentColor, accentSecondary))
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isAvailable) 5.dp else 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top gradient accent bar ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(topBarBrush)
            )

            // ── Logo section ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(logoBgBrush),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = brand.logoRes,
                    contentDescription = "${brand.name} logo",
                    modifier = Modifier
                        .fillMaxSize(0.72f)
                        .padding(12.dp)
                        .then(if (!isAvailable) Modifier.alpha(0.40f) else Modifier),
                    contentScale = ContentScale.Fit
                )

                // Coming soon badge — top left
                if (!isAvailable) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF3CD)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Construction,
                                contentDescription = null,
                                tint = Color(0xFF856404),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = stringResource(R.string.label_coming_soon),
                                color = Color(0xFF856404),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Bottom gradient separator ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(separatorBrush)
            )

            // ── Bottom info row ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isAvailable) Color.White else Color(0xFFF5F5F5))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(leftBarBrush)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = brand.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = if (isAvailable) Color(0xFF1A1A2E) else Color(0xFF9E9E9E)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF9A9AB0),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = stringResource(brand.originRes),
                            fontSize = 11.sp,
                            color = Color(0xFF9A9AB0)
                        )
                    }
                }

                // Arrow / Lock button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAvailable) Icons.AutoMirrored.Filled.ArrowForward
                                      else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
