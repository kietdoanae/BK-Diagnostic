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
     * Encoding template-based:
     *   raw                       = round(value × scaleFactor)        // big-endian 16-bit
     *   frame                     = template.copyOf()                 // 8 byte cố định
     *   frame[highByteIndex]      = (raw shr 8) and 0xFF              // byte cao
     *   frame[lowByteIndex]       =  raw        and 0xFF              // byte thấp
     *
     * Vd Ford Ranger:
     *   RPM   (0x201): scaleFactor=0.5, template="00 00 00 00 00 00 00 00", hi=3, lo=4
     *                 7000 rpm → raw=3500=0x0DAC → byte3=0x0D, byte4=0xAC
     *   Speed (0x202): scaleFactor=100, template="00 00 00 00 F0 00 00 00", hi=6, lo=7
     *                 100 km/h → raw=10000=0x2710 → byte6=0x27, byte7=0x10
     */
    data class GaugeEntry(
        val canId: Int,
        val maxValue: Int,
        val scaleFactor: Float,
        val template: ByteArray = ByteArray(8),
        val highByteIndex: Int = 0,
        val lowByteIndex: Int = 1,
        val label: String = ""
    ) {
        /**
         * Encode 1 giá trị (RPM hoặc km/h) thành 8-byte CAN payload theo template.
         */
        fun encode(value: Int): ByteArray {
            val raw = (value.coerceAtLeast(0) * scaleFactor).toInt().coerceIn(0, 0xFFFF)
            val data = template.copyOf()
            if (highByteIndex in 0..7) data[highByteIndex] = ((raw shr 8) and 0xFF).toByte()
            if (lowByteIndex in 0..7)  data[lowByteIndex]  = ( raw         and 0xFF).toByte()
            return data
        }

        override fun equals(other: Any?) = other is GaugeEntry && canId == other.canId
        override fun hashCode() = canId
    }

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
     * 1 frame trong chuỗi đánh thức cluster.  Mỗi frame là 1 CAN payload
     * sẽ được gửi tuần tự sau khi USB-CAN kết nối thành công.
     */
    data class WakeUpFrame(
        val canId: Int,
        val canData: ByteArray,
        val label: String = ""
    )

    /**
     * Chuỗi đánh thức cụm đồng hồ. List rỗng = không gửi frame nào (fallback
     * mặc định sẽ do caller xử lý).
     */
    data class WakeUpSequence(
        val frames: List<WakeUpFrame>,
        val intervalMs: Long = 30L,
    ) {
        val isEmpty: Boolean get() = frames.isEmpty()
    }

    /**
     * Toàn bộ config dashboard cho 1 model: icons + gauges + wake-up sequence.
     */
    data class DashboardConfig(
        val icons: Map<String, IconCanEntry>,
        val gauges: GaugeConfig,
        val wakeUpSequence: WakeUpSequence = WakeUpSequence(emptyList())
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
        val wakeUp = parseWakeUpSequence(root.optJSONObject("wakeUpSequence"))
        return DashboardConfig(icons, gauges, wakeUp)
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

    private fun parseWakeUpSequence(obj: JSONObject?): WakeUpSequence {
        if (obj == null) return WakeUpSequence(emptyList())
        val intervalMs = obj.optLong("intervalMs", 30L)
        val arr = obj.optJSONArray("frames") ?: return WakeUpSequence(emptyList(), intervalMs)
        val frames = mutableListOf<WakeUpFrame>()
        for (i in 0 until arr.length()) {
            try {
                val e = arr.getJSONObject(i)
                val canId = parseHexInt(e.getString("canId"))
                if (canId == 0) continue
                val canData = parseHexBytes(e.getString("canData"))
                val label = e.optString("_label", "")
                frames += WakeUpFrame(canId, canData, label)
            } catch (ex: Exception) {
                Log.w(TAG, "Lỗi parse wakeUpSequence[$i]: ${ex.message}")
            }
        }
        Log.d(TAG, "Loaded wakeUpSequence: ${frames.size} frame(s), interval=${intervalMs}ms")
        return WakeUpSequence(frames, intervalMs)
    }

    private fun parseGaugeEntry(obj: JSONObject?): GaugeEntry? {
        if (obj == null) return null
        return try {
            val canId = parseHexInt(obj.getString("canId"))
            if (canId == 0) return null

            // scaleFactor: hỗ trợ Float (vd 0.5 cho RPM/2) hoặc Int (vd 100 cho speed*100)
            val scaleFactor = obj.optDouble("scaleFactor", 1.0).toFloat()

            // Template: 8-byte payload base. Mặc định all-zero nếu không khai báo.
            val template = obj.optString("template", "").let { s ->
                if (s.isBlank()) ByteArray(8) else parseHexBytes(s)
            }

            // Vị trí byte chứa MSB/LSB của raw value. Mặc định byte 0,1 (legacy).
            val highByteIndex = obj.optInt("highByteIndex", 0).coerceIn(0, 7)
            val lowByteIndex  = obj.optInt("lowByteIndex",  1).coerceIn(0, 7)

            // Legacy: statusByte chèn vào template tại byte 0 nếu được khai báo
            //         (giữ backward-compat với config cũ chưa có template).
            val statusByteHex = obj.optString("statusByte", "")
            if (statusByteHex.isNotBlank()) {
                template[0] = parseHexInt(statusByteHex).toByte()
            }

            GaugeEntry(
                canId          = canId,
                maxValue       = obj.optInt("maxValue", 8000),
                scaleFactor    = scaleFactor,
                template       = template,
                highByteIndex  = highByteIndex,
                lowByteIndex   = lowByteIndex,
                label          = obj.optString("_label", "")
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
