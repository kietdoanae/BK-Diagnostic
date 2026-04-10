package com.example.bkdiagnostic.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bkdiagnostic.ActivityLogger
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.ui.components.AppTopBarChip

data class CarModel(
    val id: String,
    val name: String,
    @StringRes val categoryRes: Int,
    val years: String,
    @field:DrawableRes val imageRes: Int
)

// ── Ford model list ──────────────────────────────────────────────────────────
val fordModels = listOf(
    CarModel("ranger",        "Ford Ranger",       R.string.model_category_pickup,       "2022 – 2024", R.drawable.ford_ranger),
    CarModel("ranger_raptor", "Ranger Raptor",     R.string.model_category_pickup,       "2022 – 2024", R.drawable.ford_ranger_raptor),
    CarModel("everest",       "Ford Everest",      R.string.model_category_suv_7seat,    "2022 – 2024", R.drawable.ford_everest),
    CarModel("territory",     "Ford Territory",    R.string.model_category_suv_5seat,    "2021 – 2024", R.drawable.ford_territory),
    CarModel("transit",       "Ford Transit",      R.string.model_category_minivan,      "2022 – 2024", R.drawable.ford_transit),
    CarModel("bronco",        "Ford Bronco",       R.string.model_category_suv,          "2021 – 2024", R.drawable.ford_bronco),
    CarModel("mustang",       "Ford Mustang",      R.string.model_category_coupe,        "2022 – 2024", R.drawable.ford_mustang),
    CarModel("explorer",      "Ford Explorer",     R.string.model_category_suv_7seat,    "2022 – 2024", R.drawable.ford_explorer),
    CarModel("escape",        "Ford Escape",       R.string.model_category_crossover,    "2022 – 2024", R.drawable.ford_escape),
    CarModel("ecosport",      "Ford EcoSport",     R.string.model_category_crossover,    "2020 – 2023", R.drawable.ford_ecosport),
    CarModel("f150",          "Ford F-150",        R.string.model_category_pickup,       "2022 – 2024", R.drawable.ford_f150),
    CarModel("mach_e",        "Mustang Mach-E",    R.string.model_category_electric_suv, "2022 – 2024", R.drawable.ford_mach_e),
)

private val brandModels: Map<String, List<CarModel>> = mapOf(
    "ford" to fordModels
)

/** Model IDs đã hỗ trợ đầy đủ — các model khác sẽ hiện dialog "Under Development" */
private val availableModelIds: Set<String> = setOf("ranger")

@Composable
fun ModelSelectionScreen(
    brandId: String,
    onModelSelected: (CarModel) -> Unit,
    onBack: () -> Unit
) {
    val brand = remember(brandId) { carBrands.find { it.id == brandId } }
    val models = remember(brandId) { brandModels[brandId] ?: emptyList() }

    var comingSoonModel by remember { mutableStateOf<CarModel?>(null) }
    val brandColor = brand?.primaryColor ?: Color(0xFF003087)

    // ── Coming Soon dialog ────────────────────────────────────────────────────
    comingSoonModel?.let { model ->
        AlertDialog(
            onDismissRequest = { comingSoonModel = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(brandColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = brandColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = model.name,
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
                        text = "${model.name} ${stringResource(R.string.model_coming_soon_message)}",
                        color = Color(0xFF6B6B80),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { comingSoonModel = null },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
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
            title = stringResource(R.string.model_selection_title),
            subtitle = stringResource(R.string.model_selection_subtitle),
            onBack = onBack,
            trailingContent = {
                AppTopBarChip {
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
                        text = stringResource(R.string.model_selection_breadcrumb),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        )

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
                val categoryLabel = stringResource(model.categoryRes)
                ModelCard(
                    model = model,
                    categoryLabel = categoryLabel,
                    brandColor = brandColor,
                    isAvailable = model.id in availableModelIds,
                    onClick = {
                        if (model.id in availableModelIds) {
                            ActivityLogger.modelSelected(
                                brandName = brand?.name ?: brandId,
                                modelName = model.name,
                                category = categoryLabel,
                                years = model.years
                            )
                            onModelSelected(model)
                        } else {
                            comingSoonModel = model
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: CarModel,
    categoryLabel: String,
    brandColor: Color,
    isAvailable: Boolean = true,
    onClick: () -> Unit
) {
    val cardColor = if (isAvailable) brandColor else Color(0xFF9E9E9E)

    // Memoize brushes — tránh tạo mới shader mỗi recomposition
    val imageBgBrush = remember(cardColor, isAvailable) {
        Brush.radialGradient(
            colors = listOf(
                cardColor.copy(alpha = if (isAvailable) 0.08f else 0.04f),
                Color(0xFFF0F4FF)
            )
        )
    }
    val accentLineBrush = remember(cardColor) {
        Brush.horizontalGradient(
            colors = listOf(cardColor, cardColor.copy(alpha = 0.2f))
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isAvailable) 4.dp else 1.dp)
    ) {
        Column {
            // ── Car image (16:9 ratio) ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(imageBgBrush)
            ) {
                Image(
                    painter = painterResource(id = model.imageRes),
                    contentDescription = model.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .then(if (!isAvailable) Modifier.alpha(0.45f) else Modifier),
                    contentScale = ContentScale.Fit
                )

                // Year badge — top right
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = cardColor.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = model.years,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // Coming soon overlay badge — top left
                if (!isAvailable) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF3CD)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Construction,
                                contentDescription = null,
                                tint = Color(0xFF856404),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = stringResource(R.string.label_coming_soon),
                                color = Color(0xFF856404),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Top accent line ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accentLineBrush)
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
                        color = if (isAvailable) Color(0xFF1A1A2E) else Color(0xFF9E9E9E),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Category chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = cardColor.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = categoryLabel,
                            color = cardColor,
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
                        .background(cardColor.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAvailable) Icons.AutoMirrored.Filled.ArrowForward
                                      else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = cardColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
