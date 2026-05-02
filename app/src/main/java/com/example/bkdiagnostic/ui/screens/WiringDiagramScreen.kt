package com.example.bkdiagnostic.ui.screens

import android.content.Context
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.ui.theme.LocalAppColors

@Composable
fun WiringDiagramScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val lang = remember {
        context.getSharedPreferences("bk_settings", Context.MODE_PRIVATE)
            .getString("language", "en") ?: "en"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.screenBackground)
    ) {
        AppTopBar(
            title    = stringResource(R.string.wiring_diagram_title),
            subtitle = "Sơ đồ điện · TR4021",
            onBack   = onBack
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Software rendering: required for complex SVG on all Android devices
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.setSupportZoom(true)
                    loadUrl("file:///android_asset/wiring_diagram.html?lang=$lang")
                }
            }
        )
    }
}
