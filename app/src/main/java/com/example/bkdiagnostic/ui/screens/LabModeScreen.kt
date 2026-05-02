package com.example.bkdiagnostic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

private val LabAmber = Color(0xFFF59E0B)
private val LabAmberLight = Color(0xFFFEF3C7)

@Composable
fun LabModeScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") brandId: String? = null,
    @Suppress("UNUSED_PARAMETER") modelId: String? = null
) {
    // brandId/modelId nhận từ flow Dashboard → Brand → Model → Lab.
    // Hiện tại LabModeManager chưa cần brand/model context (gắn theo session
    // code 6 chữ số), nhưng truyền sẵn để mở rộng sau (vd: filter steps theo xe).
    val state by LabModeManager.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is LabModeState.Inactive -> LabModeEntryPanel(onBack = onBack)
        is LabModeState.Active   -> LabModeActivePanel(active = s, onBack = onBack)
    }
}

// ── Entry panel — 6-digit code input ─────────────────────────────────────────

@Composable
private fun LabModeEntryPanel(onBack: () -> Unit) {
    var code       by remember { mutableStateOf("") }
    var isLoading  by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    val scope      = rememberCoroutineScope()
    val appColors  = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
        AppTopBar(
            title    = "Lab Mode",
            subtitle = "TR4021 Diagnostics Lab",
            onBack   = onBack
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.cardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Hero icon with amber glow
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(LabAmber.copy(alpha = 0.10f))
                        )
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(LabAmberLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = LabAmber,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Text(
                        text       = "Enter Session Code",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = appColors.primaryText
                    )
                    Text(
                        text      = "Ask your group leader for the 6-digit code",
                        fontSize  = 13.5.sp,
                        color     = appColors.secondaryText,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value         = code,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { code = it; errorMsg = null } },
                        label         = { Text("6-digit code") },
                        leadingIcon   = { Icon(Icons.Default.Code, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine    = true,
                        isError       = errorMsg != null,
                        supportingText = errorMsg?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        shape         = RoundedCornerShape(14.dp),
                        modifier      = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (code.length != 6) { errorMsg = "Code must be exactly 6 digits"; return@Button }
                            isLoading = true
                            scope.launch {
                                val result = LabModeManager.activate(code)
                                isLoading = false
                                result.onFailure { e ->
                                    errorMsg = e.message?.takeIf { it.isNotBlank() }
                                        ?: "Invalid or expired code. Check with your group leader."
                                }
                                // On success LabModeManager.state switches to Active → recompose shows ActivePanel
                            }
                        },
                        enabled  = code.length == 6 && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape  = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LabAmber,
                            disabledContainerColor = LabAmber.copy(alpha = 0.30f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Activate Lab Mode",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Active panel — session info + exit ───────────────────────────────────────

@Composable
private fun LabModeActivePanel(active: LabModeState.Active, onBack: () -> Unit) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
        AppTopBar(
            title    = "Lab Mode",
            subtitle = active.labTitle,
            onBack   = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero status card
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LabAmberLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(LabAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "LAB MODE ACTIVE",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF92400E),
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                active.labTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }
            }

            // Info pills
            InfoPill(icon = Icons.Default.Group,    label = "GROUP",   value = active.groupName)
            InfoPill(icon = Icons.Default.Code,     label = "CODE",    value = active.sessionCode)
            InfoPill(icon = Icons.Default.Schedule, label = "EXPIRES", value = active.expiresAt.take(19).replace("T", " "))

            // Info note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = appColors.cardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = appColors.sectionLabelColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text     = "CAN frames và active-test commands sẽ được gắn session này và upload vào bảng evidence của lab.",
                        fontSize = 12.5.sp,
                        color    = appColors.secondaryText,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick  = { LabModeManager.deactivate(); onBack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC62828).copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Exit Lab Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
private fun InfoPill(
    icon: ImageVector,
    label: String,
    value: String
) {
    val appColors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = appColors.cardSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LabAmber.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LabAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.sectionLabelColor,
                    letterSpacing = 1.sp
                )
                Text(
                    value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.primaryText
                )
            }
        }
    }
}
