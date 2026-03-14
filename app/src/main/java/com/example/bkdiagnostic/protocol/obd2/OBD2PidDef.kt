package com.example.bkdiagnostic.protocol.obd2

/**
 * Định nghĩa 1 OBD2 PID (Parameter ID).
 *
 * @param pid       Mã PID (ví dụ: 0x0C cho RPM)
 * @param mode      OBD2 Mode (mặc định 0x01 = Current Data)
 * @param name      Tên hiển thị tiếng Việt
 * @param unit      Đơn vị (rpm, km/h, °C, %, kPa…)
 * @param minBytes  Số byte dữ liệu tối thiểu cần có trong response
 * @param minValue  Giá trị nhỏ nhất (dùng cho gauge/progress bar)
 * @param maxValue  Giá trị lớn nhất
 * @param decode    Hàm chuyển đổi byte thô → giá trị số thực
 */
data class OBD2PidDef(
    val pid: Int,
    val mode: Int = 0x01,
    val name: String,
    val unit: String,
    val minBytes: Int,
    val minValue: Double = 0.0,
    val maxValue: Double = 100.0,
    val decode: (ByteArray) -> Double
)

/**
 * Kết quả đọc được của 1 PID.
 */
data class SensorReading(
    val pid: OBD2PidDef,
    val value: Double,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /** Hiển thị giá trị + đơn vị, làm tròn hợp lý */
    fun formatted(): String {
        val rounded = when {
            pid.unit == "rpm" -> "%.0f".format(value)
            pid.unit == "%" -> "%.1f".format(value)
            else -> "%.1f".format(value)
        }
        return "$rounded ${pid.unit}"
    }
}

/**
 * Mã lỗi OBD2 (Diagnostic Trouble Code).
 * Mỗi DTC là 2 byte trong response Mode 03.
 */
data class DtcCode(val raw: Int) {
    /** Ký tự phân loại: P (Powertrain), C (Chassis), B (Body), U (Network) */
    val category: Char = when ((raw shr 14) and 0x03) {
        0 -> 'P'; 1 -> 'C'; 2 -> 'B'; else -> 'U'
    }
    /** Mã đầy đủ dạng chuỗi, ví dụ: "P0300" */
    val code: String = "%s%04X".format(category, raw and 0x3FFF)

    override fun toString() = code
}

// ════════════════════════════════════════════════════════════════════════════
//  Thư viện PID chuẩn OBD2 (ISO 15031-5 / SAE J1979) — Mode 01
// ════════════════════════════════════════════════════════════════════════════

object OBD2Pids {

    // ── Động cơ / Engine ─────────────────────────────────────────────────────

    val ENGINE_LOAD = OBD2PidDef(
        pid = 0x04, name = "Tải động cơ", unit = "%",
        minBytes = 1, minValue = 0.0, maxValue = 100.0,
        decode = { b -> b[0].toUByte().toInt() * 100.0 / 255.0 }
    )
    val COOLANT_TEMP = OBD2PidDef(
        pid = 0x05, name = "Nhiệt độ nước làm mát", unit = "°C",
        minBytes = 1, minValue = -40.0, maxValue = 215.0,
        decode = { b -> b[0].toUByte().toInt() - 40.0 }
    )
    val ENGINE_RPM = OBD2PidDef(
        pid = 0x0C, name = "Tốc độ động cơ", unit = "rpm",
        minBytes = 2, minValue = 0.0, maxValue = 8000.0,
        decode = { b -> ((b[0].toUByte().toInt() shl 8) or b[1].toUByte().toInt()) / 4.0 }
    )
    val INTAKE_MAP = OBD2PidDef(
        pid = 0x0B, name = "Áp suất đường nạp (MAP)", unit = "kPa",
        minBytes = 1, minValue = 0.0, maxValue = 255.0,
        decode = { b -> b[0].toUByte().toDouble() }
    )
    val INTAKE_AIR_TEMP = OBD2PidDef(
        pid = 0x0F, name = "Nhiệt độ khí nạp", unit = "°C",
        minBytes = 1, minValue = -40.0, maxValue = 215.0,
        decode = { b -> b[0].toUByte().toInt() - 40.0 }
    )
    val MAF_RATE = OBD2PidDef(
        pid = 0x10, name = "Lưu lượng khí nạp (MAF)", unit = "g/s",
        minBytes = 2, minValue = 0.0, maxValue = 655.35,
        decode = { b -> ((b[0].toUByte().toInt() shl 8) or b[1].toUByte().toInt()) / 100.0 }
    )
    val OIL_TEMP = OBD2PidDef(
        pid = 0x5C, name = "Nhiệt độ dầu động cơ", unit = "°C",
        minBytes = 1, minValue = -40.0, maxValue = 215.0,
        decode = { b -> b[0].toUByte().toInt() - 40.0 }
    )
    val FUEL_RATE = OBD2PidDef(
        pid = 0x5E, name = "Mức tiêu thụ nhiên liệu", unit = "L/h",
        minBytes = 2, minValue = 0.0, maxValue = 3276.75,
        decode = { b -> ((b[0].toUByte().toInt() shl 8) or b[1].toUByte().toInt()) / 20.0 }
    )
    val ENGINE_RUNTIME = OBD2PidDef(
        pid = 0x1F, name = "Thời gian chạy động cơ", unit = "s",
        minBytes = 2, minValue = 0.0, maxValue = 65535.0,
        decode = { b -> ((b[0].toUByte().toInt() shl 8) or b[1].toUByte().toInt()).toDouble() }
    )

    // ── Xe / Vehicle ─────────────────────────────────────────────────────────

    val VEHICLE_SPEED = OBD2PidDef(
        pid = 0x0D, name = "Tốc độ xe", unit = "km/h",
        minBytes = 1, minValue = 0.0, maxValue = 255.0,
        decode = { b -> b[0].toUByte().toDouble() }
    )
    val THROTTLE_POS = OBD2PidDef(
        pid = 0x11, name = "Vị trí bướm ga", unit = "%",
        minBytes = 1, minValue = 0.0, maxValue = 100.0,
        decode = { b -> b[0].toUByte().toInt() * 100.0 / 255.0 }
    )
    val FUEL_LEVEL = OBD2PidDef(
        pid = 0x2F, name = "Mức nhiên liệu", unit = "%",
        minBytes = 1, minValue = 0.0, maxValue = 100.0,
        decode = { b -> b[0].toUByte().toInt() * 100.0 / 255.0 }
    )

    // ── Môi trường / Environment ──────────────────────────────────────────────

    val BARO_PRESSURE = OBD2PidDef(
        pid = 0x33, name = "Áp suất khí quyển", unit = "kPa",
        minBytes = 1, minValue = 0.0, maxValue = 255.0,
        decode = { b -> b[0].toUByte().toDouble() }
    )
    val AMBIENT_TEMP = OBD2PidDef(
        pid = 0x46, name = "Nhiệt độ môi trường", unit = "°C",
        minBytes = 1, minValue = -40.0, maxValue = 215.0,
        decode = { b -> b[0].toUByte().toInt() - 40.0 }
    )

    // ── Điện / Electrical ────────────────────────────────────────────────────

    val CONTROL_VOLTAGE = OBD2PidDef(
        pid = 0x42, name = "Điện áp ắc quy", unit = "V",
        minBytes = 2, minValue = 0.0, maxValue = 65.535,
        decode = { b -> ((b[0].toUByte().toInt() shl 8) or b[1].toUByte().toInt()) / 1000.0 }
    )

    // ── Tập hợp theo nhóm ────────────────────────────────────────────────────

    /** Tất cả PID chuẩn OBD2 Mode 01 trong thư viện này */
    val ALL: List<OBD2PidDef> = listOf(
        ENGINE_LOAD, COOLANT_TEMP, INTAKE_MAP, ENGINE_RPM,
        VEHICLE_SPEED, INTAKE_AIR_TEMP, MAF_RATE, THROTTLE_POS,
        ENGINE_RUNTIME, FUEL_LEVEL, BARO_PRESSURE, CONTROL_VOLTAGE,
        AMBIENT_TEMP, OIL_TEMP, FUEL_RATE
    )

    /** PID hiển thị ưu tiên trên dashboard live data */
    val DASHBOARD_PIDS: List<OBD2PidDef> = listOf(
        ENGINE_RPM, VEHICLE_SPEED, COOLANT_TEMP, ENGINE_LOAD,
        THROTTLE_POS, FUEL_LEVEL, CONTROL_VOLTAGE, OIL_TEMP
    )

    /** Tra cứu theo mã PID (tự xác định mode từ khoảng giá trị) */
    fun byPid(pid: Int): OBD2PidDef? = ALL.firstOrNull { it.pid == pid }

    /** Tra cứu theo mode + pid (dùng cho Mode 22 UDS nơi PID là 2-byte DID) */
    fun byModeAndPid(mode: Int, pid: Int): OBD2PidDef? =
        ALL.firstOrNull { it.mode == mode && it.pid == pid }
}
