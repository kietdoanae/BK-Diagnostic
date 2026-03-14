package com.example.bkdiagnostic.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bkdiagnostic.R

data class CarBrand(
    val id: String,
    val name: String,
    val origin: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    @field:DrawableRes val logoRes: Int
)

val carBrands = listOf(
    CarBrand("toyota",      "Toyota",      "Nhật Bản", Color(0xFFCC0000), Color(0xFF8B0000), R.drawable.brand_toyota),
    CarBrand("honda",       "Honda",       "Nhật Bản", Color(0xFFE40012), Color(0xFF9C0010), R.drawable.brand_honda),
    CarBrand("suzuki",      "Suzuki",      "Nhật Bản", Color(0xFF1A5EA8), Color(0xFF0D3D6E), R.drawable.brand_suzuki),
    CarBrand("mazda",       "Mazda",       "Nhật Bản", Color(0xFF910000), Color(0xFF5C0000), R.drawable.brand_mazda),
    CarBrand("hyundai",     "Hyundai",     "Hàn Quốc", Color(0xFF002C5F), Color(0xFF004A9F), R.drawable.brand_hyundai),
    CarBrand("kia",         "KIA",         "Hàn Quốc", Color(0xFF05141F), Color(0xFFBB162B), R.drawable.brand_kia),
    CarBrand("mitsubishi",  "Mitsubishi",  "Nhật Bản", Color(0xFFE4002B), Color(0xFF9C001D), R.drawable.brand_mitsubishi),
    CarBrand("ford",        "Ford",        "Hoa Kỳ",   Color(0xFF003087), Color(0xFF0055C8), R.drawable.brand_ford),
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
                                text = "Đang phát triển",
                                color = Color(0xFF856404),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Text(
                        text = "Hãng xe ${brand.name} đang trong giai đoạn phát triển. Vui lòng quay lại sau!",
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
                    Text("Đã hiểu", fontWeight = FontWeight.SemiBold)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0A1E6E), Color(0xFF1565C0), Color(0xFF1E88E5))
                    )
                )
        ) {
            // Decorative circles
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .offset(x = (-35).dp, y = (-35).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CAR BRAND SELECTION",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Chọn hãng xe cần chẩn đoán",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                // Breadcrumb chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Car Brand",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ─── Brand grid (4×2, fills remaining screen) ────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            carBrands.chunked(4).forEach { rowBrands ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowBrands.forEach { brand ->
                        BrandCard(
                            brand = brand,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = {
                                if (brand.id == "ford") onBrandSelected(brand)
                                else comingSoonBrand = brand
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top gradient accent bar ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(brand.primaryColor, brand.secondaryColor)
                        )
                    )
            )

            // ── Logo section ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                brand.primaryColor.copy(alpha = 0.07f),
                                Color(0xFFF8FAFF)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = brand.logoRes),
                    contentDescription = "${brand.name} logo",
                    modifier = Modifier
                        .fillMaxSize(0.72f)
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // ── Bottom gradient separator ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                brand.primaryColor.copy(alpha = 0.4f),
                                brand.secondaryColor.copy(alpha = 0.1f)
                            )
                        )
                    )
            )

            // ── Bottom info row ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(brand.primaryColor, brand.secondaryColor)
                            )
                        )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = brand.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A2E)
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
                            text = brand.origin,
                            fontSize = 11.sp,
                            color = Color(0xFF9A9AB0)
                        )
                    }
                }

                // Arrow button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(brand.primaryColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = brand.primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
