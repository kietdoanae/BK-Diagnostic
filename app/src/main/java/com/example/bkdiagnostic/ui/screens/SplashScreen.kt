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
import android.util.Log
import com.example.bkdiagnostic.BuildConfig
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.supabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        Log.d("BKDiag/Splash", "Splash started")
        Log.d("BKDiag/Splash", "SUPABASE_URL = ${BuildConfig.SUPABASE_URL}")
        Log.d("BKDiag/Splash", "SUPABASE_KEY prefix = ${BuildConfig.SUPABASE_KEY.take(20)}...")

        // Chạy song song: animation + session check trên IO thread
        // Có timeout 5s để tránh trường hợp Supabase init hang vô tận → splash kẹt mãi.
        val sessionDeferred = async(Dispatchers.IO) {
            withTimeoutOrNull(5000L) {
                runCatching { supabaseClient.auth.currentSessionOrNull() }
                    .onFailure { Log.e("BKDiag/Splash", "Session check failed", it) }
                    .getOrNull()
            }
        }

        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700)
        )

        val session = sessionDeferred.await()
        if (session != null) {
            Log.d("BKDiag/Splash", "Session found → main")
            onNavigateToMain()
        } else {
            Log.d("BKDiag/Splash", "No session (or timeout) → login")
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
