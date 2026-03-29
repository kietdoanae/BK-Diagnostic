package com.example.bkdiagnostic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bkdiagnostic.communication.UsbSerialManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val usbManager: UsbSerialManager =
        (application as BKDiagnosticApp).usbSerialManager

    // ── States ───────────────────────────────────────────────────────────────

    private val _connectionSettings  = MutableStateFlow(ConnectionSettings())
    val connectionSettings: StateFlow<ConnectionSettings> = _connectionSettings.asStateFlow()

    private val _diagnosticsSettings = MutableStateFlow(DiagnosticsSettings())
    val diagnosticsSettings: StateFlow<DiagnosticsSettings> = _diagnosticsSettings.asStateFlow()

    private val _displaySettings     = MutableStateFlow(DisplaySettings())
    val displaySettings: StateFlow<DisplaySettings> = _displaySettings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { loadSettings() }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    private suspend fun loadSettings() {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return
        _isLoading.value = true
        val row = runCatching {
            supabaseClient.postgrest["user_settings"]
                .select { filter { eq("id", userId) }; limit(1) }
                .decodeList<UserSettingsRow>()
                .firstOrNull()
        }.getOrNull()

        val conn = row?.toConnectionSettings()  ?: ConnectionSettings()
        val diag = row?.toDiagnosticsSettings() ?: DiagnosticsSettings()
        val disp = row?.toDisplaySettings()     ?: DisplaySettings()

        _connectionSettings.value  = conn
        _diagnosticsSettings.value = diag
        _displaySettings.value     = disp
        applyToUsbManager(conn)
        _isLoading.value = false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun upsert(userId: String) {
        supabaseClient.postgrest["user_settings"].upsert(
            buildSettingsRow(userId, _connectionSettings.value,
                             _diagnosticsSettings.value, _displaySettings.value)
        )
    }

    // ── Save — Connection ────────────────────────────────────────────────────

    fun saveConnectionSettings(settings: ConnectionSettings) {
        viewModelScope.launch {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@launch
            _isLoading.value = true
            val result = runCatching { _connectionSettings.value = settings; upsert(userId) }
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to save settings"
            } else {
                applyToUsbManager(settings)
            }
            _isLoading.value = false
        }
    }

    // ── Save — Diagnostics ───────────────────────────────────────────────────

    fun saveDiagnosticsSettings(settings: DiagnosticsSettings) {
        viewModelScope.launch {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@launch
            _isLoading.value = true
            val result = runCatching { _diagnosticsSettings.value = settings; upsert(userId) }
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to save settings"
            }
            _isLoading.value = false
        }
    }

    // ── Save — Display ───────────────────────────────────────────────────────

    fun saveDisplaySettings(settings: DisplaySettings) {
        viewModelScope.launch {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@launch
            _isLoading.value = true
            val result = runCatching { _displaySettings.value = settings; upsert(userId) }
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to save settings"
            }
            _isLoading.value = false
        }
    }

    fun clearError() { _error.value = null }

    // ── Apply to hardware ────────────────────────────────────────────────────

    private fun applyToUsbManager(settings: ConnectionSettings) {
        usbManager.preferredBaudRate = settings.usbBaudRate
        usbManager.preferredCanKbps  = settings.canSpeedKbps
        usbManager.autoReconnect     = settings.autoReconnect
    }
}
