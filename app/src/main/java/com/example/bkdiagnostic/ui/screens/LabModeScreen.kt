package com.example.bkdiagnostic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
import com.example.bkdiagnostic.ui.components.AppTopBar
import kotlinx.coroutines.launch

private val LabAmber = Color(0xFFF59E0B)
private val LabAmberLight = Color(0xFFFEF3C7)

@Composable
fun LabModeScreen(onBack: () -> Unit) {
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

    Scaffold(
        topBar = {
            AppTopBar(
                title    = "Lab Mode",
                subtitle = "TR4021 Diagnostics Lab",
                onBack   = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F4F9))
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint     = LabAmber,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text       = "Enter Session Code",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF1A1A2E)
            )
            Text(
                text      = "Ask your group leader for the 6-digit code",
                fontSize  = 13.sp,
                color     = Color(0xFF666680),
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value         = code,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { code = it; errorMsg = null } },
                label         = { Text("6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                isError       = errorMsg != null,
                supportingText = errorMsg?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
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
                    .height(50.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LabAmber)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Activate Lab Mode", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Active panel — session info + exit ───────────────────────────────────────

@Composable
private fun LabModeActivePanel(active: LabModeState.Active, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppTopBar(
                title    = "Lab Mode",
                subtitle = active.labTitle,
                onBack   = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F4F9))
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LabAmberLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Science, null, tint = LabAmber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("LAB MODE ACTIVE", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF92400E))
                    }
                    InfoRow(label = "Lab",     value = active.labTitle)
                    InfoRow(label = "Group",   value = active.groupName)
                    InfoRow(label = "Code",    value = active.sessionCode)
                    InfoRow(label = "Expires", value = active.expiresAt.take(19).replace("T", " "))
                }
            }

            Text(
                text     = "CAN frames and active-test commands are now being tagged with this session and uploaded to the lab evidence table.",
                fontSize = 12.sp,
                color    = Color(0xFF666680),
                lineHeight = 18.sp
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick  = { LabModeManager.deactivate(); onBack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
            ) {
                Text("Exit Lab Mode", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text("$label: ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
        Text(value, fontSize = 13.sp, color = Color(0xFF78350F))
    }
}
