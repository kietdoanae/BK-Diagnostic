package com.example.bkdiagnostic.protocol

import com.example.bkdiagnostic.protocol.obd2.OBD2PidDef

/**
 * Cấu hình protocol cho từng hãng xe / model xe.
 *
 * @param brandId          ID hãng xe (trùng với BrandSelectionScreen)
 * @param modelId          ID model xe
 * @param displayName      Tên hiển thị ("Ford Ranger 2.0 Bi-Turbo")
 * @param canBaudKbps      Tốc độ CAN bus (thường 500 kbps cho OBD2)
 * @param requestCanId     CAN ID để gửi request (0x7DF = broadcast, 0x7E0 = Engine ECU trực tiếp)
 * @param responseCanId    CAN ID nhận response từ Engine ECU (thường 0x7E8)
 * @param livePids         Danh sách PID hỗ trợ đọc dữ liệu thời gian thực
 * @param ecuMap           Map tên ECU → CAN ID request (để chẩn đoán nhiều ECU)
 * @param pollIntervalMs   Khoảng thời gian giữa 2 lần request cùng 1 PID
 * @param responseTimeoutMs Thời gian tối đa chờ response từ ECU
 */
data class ProtocolConfig(
    val brandId: String,
    val modelId: String,
    val displayName: String,
    val canBaudKbps: Int = 500,
    val requestCanId: Int = 0x7DF,
    val responseCanId: Int = 0x7E8,
    val livePids: List<OBD2PidDef>,
    val ecuMap: Map<String, EcuConfig> = emptyMap(),
    val pollIntervalMs: Long = 100L,
    val responseTimeoutMs: Long = 500L
)

/**
 * Thông tin 1 ECU trong hệ thống xe.
 *
 * @param name          Tên ECU ("Engine", "Transmission", "ABS"…)
 * @param requestCanId  CAN ID để gửi request đến ECU này
 * @param responseCanId CAN ID ECU này sẽ trả về
 */
data class EcuConfig(
    val name: String,
    val requestCanId: Int,
    val responseCanId: Int
)
