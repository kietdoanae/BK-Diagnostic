package com.example.bkdiagnostic.communication

/**
 * Đại diện cho một CAN frame nhận được từ STM32 hoặc cần gửi lên CAN bus.
 *
 * @param id   CAN ID (11-bit tiêu chuẩn 0x000–0x7FF hoặc 29-bit mở rộng)
 * @param dlc  Data Length Code — số byte dữ liệu hợp lệ (0–8)
 * @param data Mảng đúng 8 byte; byte từ chỉ số [dlc] trở đi được bỏ qua (padding 0x00)
 */
data class CanFrame(
    val id: Int,
    val dlc: Int,
    val data: ByteArray = ByteArray(8)
) {
    /** Lấy [dlc] byte dữ liệu có nghĩa */
    fun effectiveData(): ByteArray = data.copyOf(dlc)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFrame) return false
        return id == other.id && dlc == other.dlc && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + dlc
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String {
        val hex = data.take(dlc).joinToString(" ") { "%02X".format(it) }
        return "CAN[id=0x%03X dlc=$dlc data=$hex]".format(id)
    }
}
