package com.example.bkdiagnostic.protocol

/**
 * Danh mục nhóm actuator test.
 */
enum class ActiveTestCategory(val label: String) {
    LIGHTING("Đèn"),
    HORN("Còi"),
    LOCKS("Khóa cửa"),
    WIPER("Gạt mưa"),
    OTHER("Khác")
}

/**
 * Định nghĩa 1 lệnh Active Test (kích hoạt cơ cấu chấp hành).
 *
 * @param id            ID duy nhất của test (dùng để so sánh trạng thái)
 * @param name          Tên hiển thị tiếng Việt
 * @param description   Mô tả ngắn
 * @param category      Nhóm chức năng
 * @param requestCanId  CAN ID gửi lệnh đến ECU (thường là BCM)
 * @param dataOn        8 byte CAN data để BẬT actuator
 * @param dataOff       8 byte CAN data để TẮT actuator (hoặc trả quyền điều khiển)
 * @param isToggle      true = giữ ON đến khi nhấn lại; false = pulse (ON rồi tự OFF sau [pulseDurationMs])
 * @param pulseDurationMs Nếu isToggle=false: thời gian ON trước khi tự gửi lệnh OFF
 * @param indicatorKey  Key ánh xạ đến indicator trên dashboard sim ("left_turn", "right_turn", "hazard", ...)
 */
data class ActiveTestCommand(
    val id: String,
    val name: String,
    val description: String,
    val category: ActiveTestCategory,
    val requestCanId: Int,
    val dataOn: ByteArray,
    val dataOff: ByteArray,
    val isToggle: Boolean = true,
    val pulseDurationMs: Long = 2000L,
    val indicatorKey: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActiveTestCommand) return false
        return id == other.id
    }
    override fun hashCode() = id.hashCode()
}

/**
 * Registry ánh xạ (brandId, modelId) → danh sách [ActiveTestCommand].
 * Mỗi model xe có thể có bộ active test riêng.
 */
object ActiveTestRegistry {
    private val registry = mutableMapOf<String, List<ActiveTestCommand>>()

    fun register(brandId: String, modelId: String, tests: List<ActiveTestCommand>) {
        registry["${brandId.lowercase()}/${modelId.lowercase()}"] = tests
    }

    fun get(brandId: String, modelId: String): List<ActiveTestCommand> =
        registry["${brandId.lowercase()}/${modelId.lowercase()}"] ?: emptyList()
}
