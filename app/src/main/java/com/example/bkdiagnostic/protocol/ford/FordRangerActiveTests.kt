package com.example.bkdiagnostic.protocol.ford

import com.example.bkdiagnostic.protocol.ActiveTestCategory
import com.example.bkdiagnostic.protocol.ActiveTestCommand
import com.example.bkdiagnostic.protocol.ActiveTestRegistry

// ════════════════════════════════════════════════════════════════════════════
//  Ford Ranger 2019 (PXIII) — BCM Active Tests
//
//  BCM CAN ID: 0x7A0 (Request), 0x7A8 (Response)
//  Protocol: UDS Service 0x2F — InputOutputControlByIdentifier
//
//  Frame format (8 bytes):
//  [0] length (số byte sau đây — thường 0x06 hoặc 0x05)
//  [1] 0x2F  (UDS service)
//  [2] DID_H (MSB của DataIdentifier)
//  [3] DID_L (LSB của DataIdentifier)
//  [4] controlParameter:
//       0x03 = shortTermAdjustment (ghi đè BCM, giữ cho đến khi release)
//       0x00 = returnControlToECU  (trả quyền điều khiển cho BCM)
//  [5] controlEnableRecord byte 0 (giá trị: 0x01=ON, 0x02=LOCK, 0x00=OFF/release)
//  [6][7] padding 0x00
//
//  ⚠️  Các DID bên dưới là giá trị tham khảo theo cấu trúc FORScan / Ford UDS.
//      Cần xác minh lại với thiết bị thực tế hoặc tài liệu kỹ thuật chính thức.
// ════════════════════════════════════════════════════════════════════════════

private const val BCM_CAN_ID = 0x7A0   // Ford BCM request CAN ID

/** Tạo frame UDS 0x2F shortTermAdjustment (bật) */
private fun on(didH: Byte, didL: Byte, value: Byte = 0x01): ByteArray =
    byteArrayOf(0x06, 0x2F, didH, didL, 0x03, value, 0x00, 0x00)

/** Tạo frame UDS 0x2F returnControlToECU (tắt/trả quyền) */
private fun off(didH: Byte, didL: Byte): ByteArray =
    byteArrayOf(0x05, 0x2F, didH, didL, 0x00, 0x00, 0x00, 0x00)

object FordRangerActiveTests {

    val COMMANDS = listOf(

        // ── Lighting ─────────────────────────────────────────────────────────

        ActiveTestCommand(
            id = "left_turn",
            name = "Đèn xi-nhan trái",
            description = "Kích hoạt xi-nhan trái qua BCM — DID 0xD002",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x02),
            dataOff = off(0xD0.toByte(), 0x02),
            isToggle = true,
            indicatorKey = "left_turn"
        ),

        ActiveTestCommand(
            id = "right_turn",
            name = "Đèn xi-nhan phải",
            description = "Kích hoạt xi-nhan phải qua BCM — DID 0xD003",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x03),
            dataOff = off(0xD0.toByte(), 0x03),
            isToggle = true,
            indicatorKey = "right_turn"
        ),

        ActiveTestCommand(
            id = "hazard",
            name = "Đèn cảnh báo nguy hiểm (Hazard)",
            description = "Bật tất cả xi-nhan đồng thời — DID 0xD004",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x04),
            dataOff = off(0xD0.toByte(), 0x04),
            isToggle = true,
            indicatorKey = "hazard"
        ),

        ActiveTestCommand(
            id = "low_beam",
            name = "Đèn cos (Low Beam)",
            description = "Bật đèn chiếu gần — DID 0xD020",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x20),
            dataOff = off(0xD0.toByte(), 0x20),
            isToggle = true,
            indicatorKey = "low_beam"
        ),

        ActiveTestCommand(
            id = "high_beam",
            name = "Đèn pha (High Beam)",
            description = "Bật đèn chiếu xa — DID 0xD021",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x21),
            dataOff = off(0xD0.toByte(), 0x21),
            isToggle = true,
            indicatorKey = "high_beam"
        ),

        ActiveTestCommand(
            id = "brake_light",
            name = "Đèn phanh (Brake Light)",
            description = "Kích hoạt đèn phanh — DID 0xD022",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x22),
            dataOff = off(0xD0.toByte(), 0x22),
            isToggle = true,
            indicatorKey = "brake_light"
        ),

        ActiveTestCommand(
            id = "reverse_light",
            name = "Đèn lùi (Reverse Light)",
            description = "Kích hoạt đèn lùi — DID 0xD023",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x23),
            dataOff = off(0xD0.toByte(), 0x23),
            isToggle = true,
            indicatorKey = "reverse_light"
        ),

        ActiveTestCommand(
            id = "interior_lamp",
            name = "Đèn trần nội thất",
            description = "Bật đèn nội thất — DID 0xD030",
            category = ActiveTestCategory.LIGHTING,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x30),
            dataOff = off(0xD0.toByte(), 0x30),
            isToggle = true,
            indicatorKey = "interior"
        ),

        // ── Horn ─────────────────────────────────────────────────────────────

        ActiveTestCommand(
            id = "horn",
            name = "Còi (Horn)",
            description = "Bóp còi 2 giây — DID 0xD010",
            category = ActiveTestCategory.HORN,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x10),
            dataOff = off(0xD0.toByte(), 0x10),
            isToggle = false,
            pulseDurationMs = 2000L,
            indicatorKey = "horn"
        ),

        // ── Locks ────────────────────────────────────────────────────────────

        ActiveTestCommand(
            id = "door_lock",
            name = "Khóa cửa",
            description = "Gửi lệnh khóa tất cả cửa — DID 0xD040",
            category = ActiveTestCategory.LOCKS,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x40, 0x01),
            dataOff = off(0xD0.toByte(), 0x40),
            isToggle = false,
            pulseDurationMs = 1000L,
            indicatorKey = "door_lock"
        ),

        ActiveTestCommand(
            id = "door_unlock",
            name = "Mở khóa cửa",
            description = "Gửi lệnh mở khóa tất cả cửa — DID 0xD041",
            category = ActiveTestCategory.LOCKS,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x41, 0x02),
            dataOff = off(0xD0.toByte(), 0x41),
            isToggle = false,
            pulseDurationMs = 1000L,
            indicatorKey = "door_lock"
        ),

        // ── Wiper ────────────────────────────────────────────────────────────

        ActiveTestCommand(
            id = "front_wiper",
            name = "Gạt mưa trước",
            description = "Kích hoạt gạt mưa trước 1 lần — DID 0xD050",
            category = ActiveTestCategory.WIPER,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x50),
            dataOff = off(0xD0.toByte(), 0x50),
            isToggle = false,
            pulseDurationMs = 3000L,
            indicatorKey = ""
        ),

        ActiveTestCommand(
            id = "rear_wiper",
            name = "Gạt mưa sau",
            description = "Kích hoạt gạt mưa sau — DID 0xD051",
            category = ActiveTestCategory.WIPER,
            requestCanId = BCM_CAN_ID,
            dataOn  = on(0xD0.toByte(), 0x51),
            dataOff = off(0xD0.toByte(), 0x51),
            isToggle = false,
            pulseDurationMs = 3000L,
            indicatorKey = ""
        ),
    )

    fun register() {
        ActiveTestRegistry.register("ford", "ranger", COMMANDS)
        ActiveTestRegistry.register("ford", "ranger_raptor", COMMANDS)
        ActiveTestRegistry.register("ford", "everest", COMMANDS)
    }
}
