package com.example.bkdiagnostic.ui.screens

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.ActivityLogger
import com.example.bkdiagnostic.R
import com.example.bkdiagnostic.communication.UsbSerialManager
import com.example.bkdiagnostic.diagnostic.DiagnosticViewModel
import com.example.bkdiagnostic.protocol.ActiveTestCommand
import com.example.bkdiagnostic.protocol.ActiveTestRegistry
import com.example.bkdiagnostic.ui.components.AppTopBar
import com.example.bkdiagnostic.ui.webbridge.ClusterJsBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Active Test Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActiveTestScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isConnected = connectionState is UsbSerialManager.ConnectionState.Connected

    val commands = remember { ActiveTestRegistry.get(viewModel.brandId, viewModel.modelId) }
    val activeStates = remember { mutableStateMapOf<String, Boolean>() }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val strErrorNotConnected = stringResource(R.string.active_test_error_not_connected)
    val strActuatorControl   = stringResource(R.string.active_test_subtitle)
    val strTitle             = stringResource(R.string.active_test_title)

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun syncIndicator(key: String, isOn: Boolean) {
        val js = "window.cluster.setIndicator('$key', $isOn)"
        val wv = webViewRef.value ?: return
        wv.post { wv.evaluateJavascript(js, null) }
    }

    fun toggle(cmd: ActiveTestCommand) {
        if (!isConnected) {
            scope.launch { snackbarHost.showSnackbar(strErrorNotConnected) }
            return
        }
        val isOn = activeStates[cmd.id] == true
        if (isOn) {
            viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
            activeStates[cmd.id] = false
            if (cmd.indicatorKey.isNotEmpty()) syncIndicator(cmd.indicatorKey, false)
        } else {
            ActivityLogger.activeTest(viewModel.brandId, viewModel.modelId, cmd.name)
            viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOn)
            activeStates[cmd.id] = true
            if (cmd.indicatorKey.isNotEmpty()) syncIndicator(cmd.indicatorKey, true)
            if (!cmd.isToggle) {
                scope.launch {
                    delay(cmd.pulseDurationMs)
                    val stillConnected = viewModel.connectionState.value is UsbSerialManager.ConnectionState.Connected
                    if (stillConnected) viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
                    activeStates[cmd.id] = false
                    if (cmd.indicatorKey.isNotEmpty()) syncIndicator(cmd.indicatorKey, false)
                }
            }
        }
    }

    fun handleClusterCommand(key: String, isOn: Boolean) {
        if (!key.matches(Regex("^[A-Za-z0-9_-]+$"))) return
        val cmd = commands.find { it.id == key }
            ?: commands.find { it.indicatorKey == key }
        if (cmd != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                toggle(cmd)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeStates.filter { it.value }.forEach { (id, _) ->
                commands.find { it.id == id }?.let { cmd ->
                    viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08080D))
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            AppTopBar(
                title = strTitle,
                subtitle = viewModel.protocolConfig?.displayName ?: strActuatorControl,
                onBack = onBack
            )

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    @SuppressLint("SetJavaScriptEnabled")
                    val wv = WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess   = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort   = true
                        settings.loadWithOverviewMode = true
                        addJavascriptInterface(
                            ClusterJsBridge { key, isOn ->
                                handleClusterCommand(key, isOn)
                            },
                            "Android"
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.postDelayed({
                                    view.evaluateJavascript("window.cluster.powerOn()", null)
                                }, 300)
                            }
                        }
                        loadUrl("file:///android_asset/cluster.html")
                    }
                    webViewRef.value = wv
                    wv
                }
            )
        }
    }
}
