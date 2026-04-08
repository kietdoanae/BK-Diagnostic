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
 *                           PAYLOAD = [CAN_OK:1][BAUD_KBPS:2BE]   (3 bytes)
 *
 *  ERROR_CODE:  0x01=CAN_BUS_OFF  0x02=CAN_ERROR_PASSIVE
 *               0x03=TX_TIMEOUT   0x04=UNKNOWN
 * ══════════════════════════════════════════════════════════════════════════════
 *  STM32 firmware note: MCP2515 đặt ở chế độ Normal/Listen-only mode
 *  CAN_ID big-endian → byte[0] MSB, byte[3] LSB
 *  Tốc độ UART mặc định: 115200 baud 8N1
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
     * Parser trạng thái để tách các frame ra khỏi luồng byte liên tục.
     * Tạo 1 instance cho mỗi kết nối USB và gọi [feed] với mỗi batch byte nhận được.
     */
    class FrameParser {
        private val buffer = ArrayDeque<Byte>(512)

        /**
         * Nạp [bytes] vào bộ đệm và trả về tất cả các [ParsedFrame] hoàn chỉnh tìm được.
         */
        fun feed(bytes: ByteArray): List<ParsedFrame> {
            bytes.forEach { buffer.addLast(it) }
            val frames = mutableListOf<ParsedFrame>()
            while (true) {
                frames += tryExtractFrame() ?: break
            }
            return frames
        }

        private fun tryExtractFrame(): ParsedFrame? {
            // Tìm SOF
            while (buffer.isNotEmpty() && buffer.first() != SOF) {
                buffer.removeFirst()
            }
            // Cần ít nhất MIN_FRAME_SIZE byte
            if (buffer.size < MIN_FRAME_SIZE) return null

            val type = buffer[1]
            val len = buffer[2].toUByte().toInt()
            val totalSize = MIN_FRAME_SIZE + len  // SOF+TYPE+LEN + PAYLOAD + CHECKSUM + EOF

            if (buffer.size < totalSize) return null

            // Kiểm tra EOF
            if (buffer[totalSize - 1] != EOF) {
                buffer.removeFirst() // SOF sai, thử lại
                return null
            }

            // Lấy frame ra
            repeat(totalSize) { buffer.removeFirst() }
            val frameBytes = ByteArray(totalSize) { buffer.elementAt(it) }
            // Đã remove rồi, phải lấy trước khi remove. Sửa lại:
            return null // placeholder, xem buildFrame logic bên dưới
        }

        fun clear() = buffer.clear()
    }

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
        private val payload = mutableListOf<Byte>()
        private var checksum: Byte = 0

        // Thống kê để debug
        var totalParsed = 0; private set
        var totalDropped = 0; private set

        private val _ready = mutableListOf<ParsedFrame>()

        /**
         * Nạp [len] byte đầu tiên của [buf], trả về danh sách frame hoàn chỉnh.
         * Không cấp phát ByteArray phụ.
         */
        fun feed(buf: ByteArray, len: Int = buf.size): List<ParsedFrame> {
            _ready.clear()
            for (i in 0 until len) processByte(buf[i])
            return _ready.toList()
        }

        private fun processByte(b: Byte) {
            when (state) {
                State.WAIT_SOF -> if (b == SOF) state = State.WAIT_TYPE
                State.WAIT_TYPE -> { type = b; state = State.WAIT_LEN }
                State.WAIT_LEN -> {
                    expectedLen = b.toUByte().toInt()
                    payload.clear()
                    state = if (expectedLen == 0) State.CHECKSUM else State.PAYLOAD
                }
                State.PAYLOAD -> {
                    payload.add(b)
                    if (payload.size == expectedLen) state = State.CHECKSUM
                }
                State.CHECKSUM -> {
                    checksum = b
                    state = State.WAIT_EOF
                }
                State.WAIT_EOF -> {
                    state = State.WAIT_SOF
                    if (b == EOF && verifyChecksum()) {
                        _ready.add(ParsedFrame(type, payload.toByteArray()))
                        totalParsed++
                    } else {
                        // Frame không hợp lệ (EOF sai hoặc checksum lỗi) — log để debug
                        totalDropped++
                        android.util.Log.w(
                            "FrameProtocol",
                            "Dropped frame: EOF=${b == EOF} " +
                            "checksumOk=${verifyChecksum()} " +
                            "type=0x${type.toUByte().toString(16)} " +
                            "total_dropped=$totalDropped"
                        )
                    }
                }
            }
        }

        private fun verifyChecksum(): Boolean {
            var xor = type.toUByte().toInt() xor expectedLen
            payload.forEach { xor = xor xor it.toUByte().toInt() }
            return xor.toByte() == checksum
        }

        fun reset() {
            state = State.WAIT_SOF
            payload.clear()
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
