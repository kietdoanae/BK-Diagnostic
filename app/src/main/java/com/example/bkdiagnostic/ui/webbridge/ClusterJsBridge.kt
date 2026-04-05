package com.example.bkdiagnostic.ui.webbridge

import android.webkit.JavascriptInterface

/**
 * JavascriptInterface đăng ký dưới tên "Android" vào WebView.
 * JS gọi: Android.sendExternalCommand("left_turn", true)
 * onCommand chạy trên thread JS — caller phải dispatch sang main thread nếu cần.
 */
class ClusterJsBridge(
    private val onCommand: (key: String, isOn: Boolean) -> Unit
) {
    @JavascriptInterface
    fun sendExternalCommand(key: String, isOn: Boolean) {
        onCommand(key, isOn)
    }
}
