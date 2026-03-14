package com.example.bkdiagnostic.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.supabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Chạy song song: animation + session check trên IO thread
        // → không block Main Thread, tổng thời gian = max(animation, session_check) ≈ 700ms
        val sessionDeferred = async(Dispatchers.IO) {
            supabaseClient.auth.currentSessionOrNull()
        }

        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700)
        )

        // Animation xong (~700ms), session check thường đã hoàn tất
        if (sessionDeferred.await() != null) {
            onNavigateToMain()
        } else {
            onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "BK Logo",
            modifier = Modifier
                .size(200.dp)
                .scale(scale.value)
        )
    }
}
