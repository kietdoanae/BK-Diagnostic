package com.example.bkdiagnostic.protocol.obd2

import com.example.bkdiagnostic.communication.CanFrame

/**
 * Xây dựng OBD2 CAN request frame và giải mã CAN response frame.
 *
 * Chuẩn: ISO 15765-4 (OBD2 over CAN)
 *
 * ── Request CAN frame ──────────────────────────────────────────────────────
 *   CAN ID  : [requestCanId]  (thường 0x7DF broadcast hoặc 0x7E0 cho Engine ECU)
 *   DLC     : 8
 *   DATA    : [0x02][MODE][PID][0x00][0x00][0x00][0x00][0x00]
 *
 * ── Response CAN frame ────────────────────────────────────────────────────
 *   CAN ID  : [responseCanId]  (thường 0x7E8 từ Engine ECU)
 *   DATA[0] : số byte theo sau (length)
 *   DATA[1] : MODE + 0x40   (ví dụ: Mode 01 → 0x41)
 *   DATA[2] : PID
 *   DATA[3..] : dữ liệu
 *
 * ── Mode 03 — Đọc DTC ────────────────────────────────────────────────────
 *   Request : [0x01][0x03][0x00 × 6]
 *   Response: [len][0x43][numDTCs][DTC_H][DTC_L] × numDTCs
 *
 * ── Mode 04 — Xóa DTC ────────────────────────────────────────────────────
 *   Request : [0x01][0x04][0x00 × 6]
 */
object OBD2Protocol {

    // ── Build request ───────────────────────────────────────────────────────

    /**
     * Tạo CAN frame để đọc 1 PID dữ liệu thời gian thực.
     * Hỗ trợ:
     *   - Mode 01 (OBD2 Standard): [0x02][0x01][PID]
     *   - Mode 22 (UDS ReadDataByIdentifier): [0x03][0x22][DID_H][DID_L]
     */
    fun buildLiveDataRequest(pid: OBD2PidDef, requestCanId: Int): CanFrame {
        val data = ByteArray(8)
        if (pid.mode == 0x22) {
            // UDS Mode 22 — 2-byte DID (Data Identifier)
            data[0] = 0x03
            data[1] = 0x22
            data[2] = ((pid.pid shr 8) and 0xFF).toByte()   // DID high byte
            data[3] = (pid.pid and 0xFF).toByte()            // DID low byte
        } else {
            // OBD2 Mode 01..09 — 1-byte PID
            data[0] = 0x02
            data[1] = pid.mode.toByte()
            data[2] = pid.pid.toByte()
        }
        return CanFrame(id = requestCanId, dlc = 8, data = data)
    }

    /** Tạo CAN frame để đọc DTC (Mode 03) */
    fun buildDtcRequest(requestCanId: Int): CanFrame {
        val data = ByteArray(8)
        data[0] = 0x01  // 1 byte PCI
        data[1] = 0x03  // Mode 03
        return CanFrame(id = requestCanId, dlc = 8, data = data)
    }

    /** Tạo CAN frame để xóa DTC (Mode 04) */
    fun buildClearDtcRequest(requestCanId: Int): CanFrame {
        val data = ByteArray(8)
        data[0] = 0x01
        data[1] = 0x04
        return CanFrame(id = requestCanId, dlc = 8, data = data)
    }

    /** Tạo CAN frame để đọc thông tin xe (Mode 09, VIN = PID 0x02) */
    fun buildVinRequest(requestCanId: Int): CanFrame {
        val data = ByteArray(8)
        data[0] = 0x02
        data[1] = 0x09
        data[2] = 0x02  // VIN
        return CanFrame(id = requestCanId, dlc = 8, data = data)
    }

    // ── Decode response ─────────────────────────────────────────────────────

    /**
     * Thử giải mã 1 CAN frame nhận được thành [SensorReading].
     * Trả về null nếu frame không phải là OBD2 response hợp lệ.
     *
     * @param frame          CAN frame nhận từ ECU
     * @param expectedCanId  CAN ID mong đợi (ví dụ: 0x7E8)
     */
    /**
     * Giải mã CAN response → [SensorReading].
     * Hỗ trợ Mode 01 (0x41) và Mode 22 (0x62).
     *
     * @param knownPids  Danh sách PID của xe (từ ProtocolConfig.livePids).
     *                   Tra cứu ở đây trước; fallback sang OBD2Pids.ALL nếu không tìm thấy.
     */
    fun decodeLiveData(
        frame: CanFrame,
        expectedCanId: Int,
        knownPids: List<OBD2PidDef> = emptyList()
    ): SensorReading? {
        if (frame.id != expectedCanId) return null
        val d = frame.data
        if (d.size < 4) return null

        fun findPid(mode: Int, pid: Int): OBD2PidDef? =
            knownPids.firstOrNull { it.mode == mode && it.pid == pid }
                ?: OBD2Pids.byModeAndPid(mode, pid)

        return when (d[1].toUByte().toInt()) {
            // ── Mode 01 response: 0x41 ─────────────────────────────────────
            0x41 -> {
                val responsePid = d[2].toUByte().toInt()
                val pid = findPid(0x01, responsePid) ?: return null
                val dataStart = 3
                if (frame.dlc - dataStart < pid.minBytes) return null
                val valueBytes = d.copyOfRange(dataStart, (dataStart + pid.minBytes).coerceAtMost(d.size))
                runCatching { SensorReading(pid = pid, value = pid.decode(valueBytes)) }.getOrNull()
            }

            // ── Mode 22 (UDS) response: 0x62 ──────────────────────────────
            0x62 -> {
                if (d.size < 5) return null
                val did = (d[2].toUByte().toInt() shl 8) or d[3].toUByte().toInt()
                val pid = findPid(0x22, did) ?: return null
                val dataStart = 4
                if (frame.dlc - dataStart < pid.minBytes) return null
                val valueBytes = d.copyOfRange(dataStart, (dataStart + pid.minBytes).coerceAtMost(d.size))
                runCatching { SensorReading(pid = pid, value = pid.decode(valueBytes)) }.getOrNull()
            }

            else -> null
        }
    }

    /**
     * Giải mã Mode 03 DTC response.
     * Xử lý single-frame (≤ 6 DTC trong 1 frame).
     * Multi-frame (ISO 15765-4 transport) sẽ bổ sung sau.
     */
    fun decodeDtcResponse(frame: CanFrame, expectedCanId: Int): List<DtcCode>? {
        if (frame.id != expectedCanId) return null
        val d = frame.data
        if (d[1].toUByte().toInt() != 0x43) return null  // Mode 03 response = 0x43

        val numDtcs = d[2].toUByte().toInt()
        val dtcs = mutableListOf<DtcCode>()
        var i = 3
        repeat(numDtcs) {
            if (i + 1 < d.size) {
                val raw = (d[i].toUByte().toInt() shl 8) or d[i + 1].toUByte().toInt()
                if (raw != 0) dtcs.add(DtcCode(raw))
                i += 2
            }
        }
        return dtcs
    }

    /**
     * Kiểm tra xem frame có phải là ACK xóa DTC thành công không.
     */
    fun isClearDtcAck(frame: CanFrame, expectedCanId: Int): Boolean {
        if (frame.id != expectedCanId) return false
        return frame.data[1].toUByte().toInt() == 0x44  // Mode 04 response = 0x44
    }
}
