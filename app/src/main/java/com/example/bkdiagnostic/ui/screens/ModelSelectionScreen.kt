package com.example.bkdiagnostic.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

data class CarModel(
    val id: String,
    val name: String,
    val category: String,
    val years: String,
    @field:DrawableRes val imageRes: Int
)

// ── Ford model list ──────────────────────────────────────────────────────────
val fordModels = listOf(
    CarModel("ranger",        "Ford Ranger",       "Bán tải",   "2022 – 2024", R.drawable.ford_ranger),
    CarModel("ranger_raptor", "Ranger Raptor",     "Bán tải",   "2022 – 2024", R.drawable.ford_ranger_raptor),
    CarModel("everest",       "Ford Everest",      "SUV 7 chỗ", "2022 – 2024", R.drawable.ford_everest),
    CarModel("territory",     "Ford Territory",    "SUV 5 chỗ", "2021 – 2024", R.drawable.ford_territory),
    CarModel("transit",       "Ford Transit",      "Minivan",   "2022 – 2024", R.drawable.ford_transit),
    CarModel("bronco",        "Ford Bronco",       "SUV",       "2021 – 2024", R.drawable.ford_bronco),
    CarModel("mustang",       "Ford Mustang",      "Coupe",     "2022 – 2024", R.drawable.ford_mustang),
    CarModel("explorer",      "Ford Explorer",     "SUV 7 chỗ", "2022 – 2024", R.drawable.ford_explorer),
    CarModel("escape",        "Ford Escape",       "Crossover", "2022 – 2024", R.drawable.ford_escape),
    CarModel("ecosport",      "Ford EcoSport",     "Crossover", "2020 – 2023", R.drawable.ford_ecosport),
    CarModel("f150",          "Ford F-150",        "Bán tải",   "2022 – 2024", R.drawable.ford_f150),
    CarModel("mach_e",        "Mustang Mach-E",    "SUV Điện",  "2022 – 2024", R.drawable.ford_mach_e),
)

private val brandModels: Map<String, List<CarModel>> = mapOf(
    "ford" to fordModels
)

@Composable
fun ModelSelectionScreen(
    brandId: String,
    onModelSelected: (CarModel) -> Unit,
    onBack: () -> Unit
) {
    val brand = carBrands.find { it.id == brandId }
    val models = brandModels[brandId] ?: emptyList()

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
                        text = "MODEL SELECTION",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Chọn model xe để bắt đầu chẩn đoán",
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
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = brand?.name ?: brandId,
                            color = Color.White.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Model",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ─── Model grid (LazyVerticalGrid 4 cols) ────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(models) { model ->
                ModelCard(
                    model = model,
                    brandColor = brand?.primaryColor ?: Color(0xFF003087),
                    onClick = { onModelSelected(model) }
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: CarModel,
    brandColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // ── Car image (16:9 ratio) ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                brandColor.copy(alpha = 0.08f),
                                Color(0xFFF0F4FF)
                            )
                        )
                    )
            ) {
                Image(
                    painter = painterResource(id = model.imageRes),
                    contentDescription = model.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                // Year badge — top right
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = brandColor.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = model.years,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // ── Top accent line ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(brandColor, brandColor.copy(alpha = 0.2f))
                        )
                    )
            )

            // ── Info section ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Color(0xFF1A1A2E),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Category chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = brandColor.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = model.category,
                            color = brandColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                // Arrow
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(brandColor.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = brandColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
