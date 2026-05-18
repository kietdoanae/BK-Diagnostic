package com.example.bkdiagnostic.communication

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  Giao thức đóng khung nhị phân  Android ↔ STM32  (qua UART / CP2102 USB)
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  FRAME FORMAT (cả 2 chiều):
 *  ┌──────┬──────┬──────┬───────────────┬──────────┬──────┐
 *  │ 0xAA │ TYPE │ LEN  │  PAYLOAD[LEN] │ CHECKSUM │ 0x55 │
 *  └──────┴──────┴──────┴───────────────┴──────────┴──────┘
 *    1B     1B    1B      0–255 bytes     1B (XOR)    1B
 *
 *  CHECKSUM = XOR(TYPE, LEN, PAYLOAD[0] … PAYLOAD[LEN-1])
 *
 * ──────────────────────────────────────────────────────────
 *  TYPE — Android → STM32:
 *    0x10  SEND_CAN_FRAME   Gửi 1 CAN frame lên bus
 *                           PAYLOAD = [CAN_ID:4BE][DLC:1][DATA:8]  (13 bytes)
 *    0x20  SET_CAN_BAUD     Đặt tốc độ CAN bus
 *                           PAYLOAD = [BAUD_KBPS:2BE]               (2 bytes)
 *    0x30  PING             Kiểm tra kết nối
 *                           PAYLOAD = []                             (0 bytes)
 *
 *  TYPE — STM32 → Android:
 *    0x01  CAN_FRAME_RX     Nhận được 1 CAN frame từ bus
 *                           PAYLOAD = [CAN_ID:4BE][DLC:1][DATA:8]  (13 bytes)
 *    0x02  ACK              Xác nhận lệnh đã thực hiện
 *                           PAYLOAD = [CMD_TYPE:1]                  (1 byte)
 *    0x03  ERROR            Báo lỗi từ STM32
 *                           PAYLOAD = [ERROR_CODE:1]                (1 byte)
 *    0x04  STATUS           Trạng thái kết nối CAN
 *                           PAYLOAD = [STATUS_FLAGS:1]             (1 byte)
 *
 *  ERROR_CODE (firmware v2 / Phase 7):
 *    0x01 CAN_SEND_FAIL    Lỗi gửi frame lên CAN bus (TXREQ timeout)
 *    0x02 CAN_BAUD_FAIL    Lỗi đặt baud rate
 *    0x03 BAD_FRAME        Frame UART có checksum/EOF sai
 *    0x04 UNKNOWN_TYPE     Type byte không nhận diện được
 *    0x10 TX_QUEUE_OVR     UART TX queue đầy → frame bị drop
 *    0x11 BAD_LENGTH       Payload length sai cho frame type
 *    0x20 BUS_WARNING      Error counter > 96 (rising edge)
 *    0x21 BUS_PASSIVE      Error counter > 127 (rising edge)
 *    0x22 BUS_OFF          BUS-OFF detected — auto-recovery đang chạy
 *    0x23 RX_BUF_OVERFLOW  MCP2515 RX buffer tràn — frame mất trên bus
 *    0x24 BUS_RECOVERED    Phục hồi BUS-OFF thành công
 * ══════════════════════════════════════════════════════════════════════════════
 *  STM32 firmware note: MCP2515 đặt ở chế độ Normal/Listen-only mode
 *  CAN_ID big-endian → byte[0] MSB, byte[3] LSB
 *  Tốc độ UART mặc định: 460800 baud 8N1
 * ══════════════════════════════════════════════════════════════════════════════
 */
object FrameProtocol {

    const val SOF: Byte = 0xAA.toByte()  // Start of Frame
    const val EOF: Byte = 0x55.toByte()  // End of Frame

    // TYPE — Android → STM32
    const val CMD_SEND_CAN: Byte = 0x10.toByte()
    const val CMD_SET_BAUD: Byte = 0x20.toByte()
    const val CMD_PING: Byte = 0x30.toByte()

    // TYPE — STM32 → Android
    const val TYPE_CAN_RX: Byte = 0x01.toByte()
    const val TYPE_ACK: Byte = 0x02.toByte()
    const val TYPE_ERROR: Byte = 0x03.toByte()
    const val TYPE_STATUS: Byte = 0x04.toByte()

    // Minimum frame size: SOF + TYPE + LEN + CHECKSUM + EOF = 5 bytes
    const val MIN_FRAME_SIZE = 5

    // ── Error codes (must match firmware comm_layer.h) ─────────────────────
    object ErrorCode {
        const val CAN_SEND_FAIL    = 0x01
        const val CAN_BAUD_FAIL    = 0x02
        const val BAD_FRAME        = 0x03
        const val UNKNOWN_TYPE     = 0x04
        const val TX_QUEUE_OVR     = 0x10
        const val BAD_LENGTH       = 0x11
        const val BUS_WARNING      = 0x20
        const val BUS_PASSIVE      = 0x21
        const val BUS_OFF          = 0x22
        const val RX_BUF_OVERFLOW  = 0x23
        const val BUS_RECOVERED    = 0x24

        /** Map error code → human-readable Vietnamese message */
        fun describe(code: Int): String = when (code) {
            CAN_SEND_FAIL    -> "Gửi CAN frame thất bại (TXREQ timeout)"
            CAN_BAUD_FAIL    -> "Đặt CAN baud rate thất bại"
            BAD_FRAME        -> "Frame UART sai checksum/EOF"
            UNKNOWN_TYPE     -> "Frame type không nhận diện được"
            TX_QUEUE_OVR     -> "UART TX queue tràn — frame bị drop"
            BAD_LENGTH       -> "Payload length sai cho frame type"
            BUS_WARNING      -> "CAN bus warning (error counter > 96)"
            BUS_PASSIVE      -> "CAN bus error-passive (error counter > 127)"
            BUS_OFF          -> "CAN BUS-OFF — đang phục hồi tự động"
            RX_BUF_OVERFLOW  -> "MCP2515 RX buffer tràn — frame mất trên bus"
            BUS_RECOVERED    -> "Phục hồi BUS-OFF thành công"
            else             -> "Lỗi không xác định (0x${code.toString(16).uppercase()})"
        }

        /** True for errors that warrant a toast/snackbar (vs silent log) */
        fun isCritical(code: Int): Boolean = when (code) {
            BUS_OFF, BUS_PASSIVE, RX_BUF_OVERFLOW -> true
            else -> false
        }
    }

    // ── Encode ─────────────────────────────────────────────────────────────

    /** Đóng gói lệnh gửi CAN frame lên STM32 */
    fun encodeSendCan(frame: CanFrame): ByteArray {
        val payload = buildCanPayload(frame)
        return buildFrame(CMD_SEND_CAN, payload)
    }

    /** Đóng gói lệnh đặt tốc độ CAN (ví dụ: 500 cho 500kbps) */
    fun encodeSetBaud(baudKbps: Int): ByteArray {
        val payload = byteArrayOf(
            ((baudKbps shr 8) and 0xFF).toByte(),
            (baudKbps and 0xFF).toByte()
        )
        return buildFrame(CMD_SET_BAUD, payload)
    }

    /** Đóng gói lệnh ping */
    fun encodePing(): ByteArray = buildFrame(CMD_PING, ByteArray(0))

    // ── Decode ─────────────────────────────────────────────────────────────

    /**
     * Parser đúng — đọc từng byte và dựng frame khi đủ.
     * Sử dụng state machine đơn giản.
     *
     * Nhận `(ByteArray, Int)` để tránh tạo ByteArray mới khi chỉ cần một phần
     * của buffer đọc từ USB (giảm GC pressure trong vòng lặp đọc liên tục).
     */
    class StreamParser {
        private enum class State { WAIT_SOF, WAIT_TYPE, WAIT_LEN, PAYLOAD, CHECKSUM, WAIT_EOF }

        private var state = State.WAIT_SOF
        private var type: Byte = 0
        private var expectedLen = 0
        // Fixed buffer thay vì MutableList<Byte> — tránh autoboxing per-byte.
        // Trên CAN bus đầy tải ~1000 frame/s, giảm ~13000 boxing alloc/s.
        private val payloadBuf = ByteArray(255)
        private var payloadIdx = 0
        // Running XOR thay vì duyệt lại payload trong verifyChecksum
        private var runningXor = 0
        private var checksum: Byte = 0

        // Thống kê để debug
        var totalParsed = 0; private set
        var totalDropped = 0; private set

        private val _ready = mutableListOf<ParsedFrame>()

        /**
         * Nạp [len] byte đầu tiên của [buf], trả về danh sách frame hoàn chỉnh.
         * Mỗi frame parse được cấp phát 1 ByteArray (payload). Không boxing per-byte.
         */
        fun feed(buf: ByteArray, len: Int = buf.size): List<ParsedFrame> {
            _ready.clear()
            for (i in 0 until len) processByte(buf[i])
            return _ready.toList()
        }

        private fun processByte(b: Byte) {
            when (state) {
                State.WAIT_SOF -> if (b == SOF) state = State.WAIT_TYPE
                State.WAIT_TYPE -> {
                    type = b
                    runningXor = b.toUByte().toInt()
                    state = State.WAIT_LEN
                }
                State.WAIT_LEN -> {
                    expectedLen = b.toUByte().toInt()
                    runningXor = runningXor xor expectedLen
                    payloadIdx = 0
                    state = if (expectedLen == 0) State.CHECKSUM else State.PAYLOAD
                }
                State.PAYLOAD -> {
                    payloadBuf[payloadIdx++] = b
                    runningXor = runningXor xor b.toUByte().toInt()
                    if (payloadIdx == expectedLen) state = State.CHECKSUM
                }
                State.CHECKSUM -> {
                    checksum = b
                    state = State.WAIT_EOF
                }
                State.WAIT_EOF -> {
                    state = State.WAIT_SOF
                    val checksumOk = runningXor.toByte() == checksum
                    if (b == EOF && checksumOk) {
                        // Cấp phát payload mới đúng kích thước cho frame kết quả
                        val payload = if (expectedLen == 0) EMPTY_BYTES
                                      else payloadBuf.copyOf(expectedLen)
                        _ready.add(ParsedFrame(type, payload))
                        totalParsed++
                    } else {
                        // Frame không hợp lệ (EOF sai hoặc checksum lỗi) — log để debug
                        totalDropped++
                        android.util.Log.w(
                            "FrameProtocol",
                            "Dropped frame: EOF=${b == EOF} " +
                            "checksumOk=$checksumOk " +
                            "type=0x${type.toUByte().toString(16)} " +
                            "total_dropped=$totalDropped"
                        )
                    }
                }
            }
        }

        fun reset() {
            state = State.WAIT_SOF
            payloadIdx = 0
            runningXor = 0
        }

        private companion object {
            private val EMPTY_BYTES = ByteArray(0)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun buildFrame(type: Byte, payload: ByteArray): ByteArray {
        var xor = type.toUByte().toInt() xor payload.size
        payload.forEach { xor = xor xor it.toUByte().toInt() }
        return byteArrayOf(SOF, type, payload.size.toByte()) + payload +
                byteArrayOf(xor.toByte(), EOF)
    }

    private fun buildCanPayload(frame: CanFrame): ByteArray {
        val buf = ByteArray(13)
        buf[0] = ((frame.id shr 24) and 0xFF).toByte()
        buf[1] = ((frame.id shr 16) and 0xFF).toByte()
        buf[2] = ((frame.id shr 8) and 0xFF).toByte()
        buf[3] = (frame.id and 0xFF).toByte()
        buf[4] = frame.dlc.toByte()
        frame.data.copyInto(buf, destinationOffset = 5, endIndex = 8)
        return buf
    }

    /** Parse CAN_FRAME_RX payload (13 bytes) thành [CanFrame] */
    fun parseCanPayload(payload: ByteArray): CanFrame? {
        if (payload.size < 13) return null
        val id = ((payload[0].toUByte().toInt() shl 24) or
                (payload[1].toUByte().toInt() shl 16) or
                (payload[2].toUByte().toInt() shl 8) or
                payload[3].toUByte().toInt())
        val dlc = payload[4].toUByte().toInt().coerceIn(0, 8)
        val data = payload.copyOfRange(5, 13)
        return CanFrame(id, dlc, data)
    }
}

/** Một frame đã được parse ra khỏi luồng byte */
data class ParsedFrame(val type: Byte, val payload: ByteArray) {
    override fun equals(other: Any?) = other is ParsedFrame &&
            type == other.type && payload.contentEquals(other.payload)
    override fun hashCode() = 31 * type + payload.contentHashCode()
}
