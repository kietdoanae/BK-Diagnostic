package com.example.bkdiagnostic

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Dùng để phát navigation event từ các hàm non-Composable
 * (ví dụ: xử lý deep link trong MainActivity) đến NavHost.
 */
object NavigationManager {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun navigate(route: String) {
        _events.tryEmit(route)
    }
}
