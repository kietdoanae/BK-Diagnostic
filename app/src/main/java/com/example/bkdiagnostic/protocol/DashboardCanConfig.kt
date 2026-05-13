package com.example.bkdiagnostic.protocol

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Đọc cấu hình CAN cho các biểu tượng cảnh báo trên dashboard (cụm đồng hồ)
 * từ file JSON trong assets/can_config/.
 *
 * File naming convention:  {brandId}_{modelId}_dashboard.json
 * Ví dụ: ford_ranger_dashboard.json
 */
object DashboardCanConfig {

    private const val TAG = "DashboardCanConfig"

    /**
     * CAN config cho 1 icon: frame ON (bắt buộc) + frame OFF (tùy chọn).
     */
    data class IconCanEntry(
        val canId: Int,
        val canData: ByteArray,
        val canDataOff: ByteArray? = null
    ) {
        override fun equals(other: Any?) = other is IconCanEntry && canId == other.canId
        override fun hashCode() = canId
    }

    /**
     * Cấu hình streaming cho 1 đồng hồ kim (RPM/Speed).
     *
     * Encoding:
     *  - RPM:   Raw = value × scaleFactor → Byte[1]=High, Byte[2]=Low. Byte[0]=statusByte.
     *  - Speed: Raw = value × scaleFactor → Byte[0]=High, Byte[1]=Low.
     */
    data class GaugeEntry(
        val canId: Int,
        val maxValue: Int,
        val scaleFactor: Int,
        val statusByte: Byte = 0x00,
        val label: String = ""
    )

    /**
     * Cấu hình tổng cho gauge streaming.
     */
    data class GaugeConfig(
        val rpm: GaugeEntry?,
        val speed: GaugeEntry?,
        val intervalMs: Long = 100L
    ) {
        val hasAny: Boolean get() = rpm != null || speed != null
    }

    /**
     * Toàn bộ config dashboard cho 1 model: icons + gauges.
     */
    data class DashboardConfig(
        val icons: Map<String, IconCanEntry>,
        val gauges: GaugeConfig
    )

    /**
     * Load cấu hình CAN cho dashboard icons từ file JSON trong assets.
     *
     * @return Map<iconId, IconCanEntry> — chỉ chứa các icon có canId != 0x000.
     *         Trả về empty map nếu file không tồn tại hoặc parse lỗi.
     */
    fun load(context: Context, brandId: String, modelId: String): Map<String, IconCanEntry> {
        return loadFull(context, brandId, modelId).icons
    }

    /**
     * Load toàn bộ config (icons + gauges) cho 1 model.
     */
    fun loadFull(context: Context, brandId: String, modelId: String): DashboardConfig {
        val filename = "can_config/${brandId.lowercase()}_${modelId.lowercase()}_dashboard.json"
        return try {
            val json = context.assets.open(filename).bufferedReader().use { it.readText() }
            parseFull(json)
        } catch (e: Exception) {
            Log.w(TAG, "Không tìm thấy hoặc không đọc được config: $filename", e)
            DashboardConfig(emptyMap(), GaugeConfig(null, null))
        }
    }

    /**
     * Parse JSON text → DashboardConfig.
     */
    internal fun parseFull(json: String): DashboardConfig {
        val root = JSONObject(json)
        val icons = parseIcons(root.optJSONObject("icons"))
        val gauges = parseGauges(root.optJSONObject("gauges"))
        return DashboardConfig(icons, gauges)
    }

    /**
     * Backward-compat: parse chỉ phần icons.
     */
    internal fun parse(json: String): Map<String, IconCanEntry> = parseFull(json).icons

    private fun parseIcons(obj: JSONObject?): Map<String, IconCanEntry> {
        val result = mutableMapOf<String, IconCanEntry>()
        if (obj == null) return result

        for (key in obj.keys()) {
            if (key.startsWith("_")) continue  // bỏ qua các trường _doc, _format, ...
            try {
                val entry = obj.getJSONObject(key)
                val canId = parseHexInt(entry.getString("canId"))
                if (canId == 0) continue  // chưa cấu hình — bỏ qua

                val canData = parseHexBytes(entry.getString("canData"))
                val canDataOff = entry.optString("canDataOff", "").let { s ->
                    if (s.isBlank()) null else parseHexBytes(s)
                }
                result[key] = IconCanEntry(canId, canData, canDataOff)
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi parse icon '$key': ${e.message}")
            }
        }
        Log.d(TAG, "Loaded ${result.size} icon CAN entries")
        return result
    }

    private fun parseGauges(obj: JSONObject?): GaugeConfig {
        if (obj == null) return GaugeConfig(null, null)
        val rpm = parseGaugeEntry(obj.optJSONObject("rpm"))
        val speed = parseGaugeEntry(obj.optJSONObject("speed"))
        val intervalMs = obj.optLong("intervalMs", 100L)
        Log.d(TAG, "Loaded gauges: rpm=${rpm != null}, speed=${speed != null}, interval=${intervalMs}ms")
        return GaugeConfig(rpm, speed, intervalMs)
    }

    private fun parseGaugeEntry(obj: JSONObject?): GaugeEntry? {
        if (obj == null) return null
        return try {
            val canId = parseHexInt(obj.getString("canId"))
            if (canId == 0) return null
            GaugeEntry(
                canId = canId,
                maxValue = obj.optInt("maxValue", 8000),
                scaleFactor = obj.optInt("scaleFactor", 1),
                statusByte = parseHexInt(obj.optString("statusByte", "0x00")).toByte(),
                label = obj.optString("_label", "")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi parse gauge entry: ${e.message}")
            null
        }
    }

    // ── Hex parsing helpers ──────────────────────────────────────────────────

    /**
     * Parse hex string → Int.  Chấp nhận: "0x7A0", "7A0", "0x07A0".
     */
    private fun parseHexInt(hex: String): Int {
        val clean = hex.trim().removePrefix("0x").removePrefix("0X")
        return clean.toIntOrNull(16) ?: 0
    }

    /**
     * Parse hex byte string → ByteArray(8).
     * Chấp nhận: "06 2F D0 21 03 01 00 00" (space-separated)
     *            hoặc "062FD021030100 00" (compact)
     * Luôn trả về mảng 8 byte (pad 0x00 nếu thiếu).
     */
    private fun parseHexBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val bytes = if (clean.contains(' ')) {
            // Space-separated format: "06 2F D0 21 03 01 00 00"
            clean.split("\\s+".toRegex()).map { it.toInt(16).toByte() }
        } else {
            // Compact format: "062FD02103010000"
            clean.chunked(2).map { it.toInt(16).toByte() }
        }
        // Pad hoặc truncate đến 8 byte
        return ByteArray(8).also { buf ->
            bytes.take(8).toByteArray().copyInto(buf)
        }
    }
}
