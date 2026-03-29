package com.example.bkdiagnostic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Connection Settings ───────────────────────────────────────────────────────

data class ConnectionSettings(
    val usbBaudRate: Int       = 115200,
    val canSpeedKbps: Int      = 500,
    val autoReconnect: Boolean = false
)

// ── Diagnostics Settings ──────────────────────────────────────────────────────

data class DiagnosticsSettings(
    val pollIntervalMs: Long    = 100L,
    val responseTimeoutMs: Long = 500L,
    val useImperial: Boolean    = false,
    val autoClearDtc: Boolean   = false
)

// ── Display Settings ──────────────────────────────────────────────────────────

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class DisplaySettings(
    val themeMode: ThemeMode  = ThemeMode.DARK,
    val keepScreenOn: Boolean = false,
    /** "en" hoặc "vi". Áp dụng khi có i18n resources. */
    val language: String      = "en"
)

// ── Supabase row (combines all groups) ───────────────────────────────────────
//
//  SQL — Tạo bảng lần đầu:
//  ─────────────────────────────────────────────────────────────────────────
//  CREATE TABLE user_settings (
//    id                  UUID        PRIMARY KEY REFERENCES auth.users(id),
//    -- Connection
//    usb_baud_rate       INTEGER     DEFAULT 115200,
//    can_speed_kbps      INTEGER     DEFAULT 500,
//    auto_reconnect      BOOLEAN     DEFAULT false,
//    -- Diagnostics
//    poll_interval_ms    INTEGER     DEFAULT 100,
//    response_timeout_ms INTEGER     DEFAULT 500,
//    use_imperial        BOOLEAN     DEFAULT false,
//    auto_clear_dtc      BOOLEAN     DEFAULT false,
//    -- Display
//    theme_mode          TEXT        DEFAULT 'dark',
//    keep_screen_on      BOOLEAN     DEFAULT false,
//    language            TEXT        DEFAULT 'en',
//    updated_at          TIMESTAMPTZ DEFAULT NOW()
//  );
//  ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;
//  CREATE POLICY "Users can manage own settings" ON user_settings
//    USING (auth.uid() = id) WITH CHECK (auth.uid() = id);
//
//  SQL — Chỉ thêm cột Display (nếu bảng đã tồn tại):
//  ALTER TABLE user_settings
//    ADD COLUMN IF NOT EXISTS theme_mode    TEXT    DEFAULT 'dark',
//    ADD COLUMN IF NOT EXISTS keep_screen_on BOOLEAN DEFAULT false,
//    ADD COLUMN IF NOT EXISTS language      TEXT    DEFAULT 'en';
//  ─────────────────────────────────────────────────────────────────────────

@Serializable
internal data class UserSettingsRow(
    val id: String,
    // ── Connection ────────────────────────────────────────────────────────
    @SerialName("usb_baud_rate")       val usbBaudRate: Int       = 115200,
    @SerialName("can_speed_kbps")      val canSpeedKbps: Int      = 500,
    @SerialName("auto_reconnect")      val autoReconnect: Boolean = false,
    // ── Diagnostics ───────────────────────────────────────────────────────
    @SerialName("poll_interval_ms")    val pollIntervalMs: Long    = 100L,
    @SerialName("response_timeout_ms") val responseTimeoutMs: Long = 500L,
    @SerialName("use_imperial")        val useImperial: Boolean    = false,
    @SerialName("auto_clear_dtc")      val autoClearDtc: Boolean   = false,
    // ── Display ───────────────────────────────────────────────────────────
    @SerialName("theme_mode")          val themeMode: String       = "dark",
    @SerialName("keep_screen_on")      val keepScreenOn: Boolean   = false,
    @SerialName("language")            val language: String        = "en"
) {
    fun toConnectionSettings()  = ConnectionSettings(usbBaudRate, canSpeedKbps, autoReconnect)
    fun toDiagnosticsSettings() = DiagnosticsSettings(pollIntervalMs, responseTimeoutMs,
                                                       useImperial, autoClearDtc)
    fun toDisplaySettings()     = DisplaySettings(
        themeMode    = ThemeMode.entries.find { it.name.lowercase() == themeMode }
                       ?: ThemeMode.DARK,
        keepScreenOn = keepScreenOn,
        language     = language
    )
}

internal fun buildSettingsRow(
    userId: String,
    conn:   ConnectionSettings,
    diag:   DiagnosticsSettings,
    disp:   DisplaySettings
) = UserSettingsRow(
    id                = userId,
    usbBaudRate       = conn.usbBaudRate,
    canSpeedKbps      = conn.canSpeedKbps,
    autoReconnect     = conn.autoReconnect,
    pollIntervalMs    = diag.pollIntervalMs,
    responseTimeoutMs = diag.responseTimeoutMs,
    useImperial       = diag.useImperial,
    autoClearDtc      = diag.autoClearDtc,
    themeMode         = disp.themeMode.name.lowercase(),
    keepScreenOn      = disp.keepScreenOn,
    language          = disp.language
)
