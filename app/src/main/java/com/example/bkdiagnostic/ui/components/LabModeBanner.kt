package com.example.bkdiagnostic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bkdiagnostic.lab.LabModeState

private val BannerBg   = Color(0xFFF59E0B)
private val BannerText = Color.White

/**
 * Persistent 32dp banner shown at the top of every screen when Lab Mode is active.
 * Tap → [onManage] callback (navigates to LabModeScreen).
 */
@Composable
fun LabModeBanner(
    state:    LabModeState,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state !is LabModeState.Active) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(BannerBg)
            .clickable(onClick = onManage)
            .statusBarsPadding()          // stay below the system status bar
            .padding(horizontal = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Science,
            contentDescription = null,
            tint     = BannerText,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text       = "LAB MODE ACTIVE · ${state.labTitle} · ${state.sessionCode} · Tap to manage",
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = BannerText,
            maxLines   = 1
        )
    }
}
