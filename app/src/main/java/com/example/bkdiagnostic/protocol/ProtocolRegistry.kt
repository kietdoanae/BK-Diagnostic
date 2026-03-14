package com.example.bkdiagnostic.protocol

import com.example.bkdiagnostic.protocol.ford.FordConfigs

/**
 * Registry ánh xạ (brandId, modelId) → [ProtocolConfig].
 *
 * Cách dùng:
 *   val config = ProtocolRegistry.get("ford", "ranger")
 *
 * Khi thêm hãng xe mới:
 *   1. Tạo file protocol/[brand]/[Brand]Protocol.kt tương tự FordProtocol.kt
 *   2. Đăng ký trong [registry] bên dưới
 */
object ProtocolRegistry {

    private val registry: Map<String, ProtocolConfig> = buildMap {
        // ── Ford ─────────────────────────────────────────────────────────────
        register(FordConfigs.FORD_RANGER)
        register(FordConfigs.FORD_RANGER_RAPTOR)
        register(FordConfigs.FORD_EVEREST)
        register(FordConfigs.FORD_TERRITORY)
        register(FordConfigs.FORD_TRANSIT)
        register(FordConfigs.FORD_MUSTANG)
        register(FordConfigs.FORD_EXPLORER)
        register(FordConfigs.FORD_BRONCO)
        register(FordConfigs.FORD_ESCAPE)
        register(FordConfigs.FORD_ECOSPORT)
        register(FordConfigs.FORD_F150)
        register(FordConfigs.FORD_MACH_E)

        // ── Toyota, Honda, Hyundai, KIA… (bổ sung sau) ───────────────────────
        // register(ToyotaConfigs.TOYOTA_FORTUNER)
    }

    private fun MutableMap<String, ProtocolConfig>.register(config: ProtocolConfig) {
        put(key(config.brandId, config.modelId), config)
    }

    private fun key(brandId: String, modelId: String) =
        "${brandId.lowercase()}/${modelId.lowercase()}"

    /**
     * Lấy [ProtocolConfig] cho xe đã chọn.
     * Trả về null nếu model chưa được cấu hình.
     */
    fun get(brandId: String, modelId: String): ProtocolConfig? =
        registry[key(brandId, modelId)]

    /** Lấy tất cả config của 1 hãng xe */
    fun getByBrand(brandId: String): List<ProtocolConfig> =
        registry.values.filter { it.brandId == brandId.lowercase() }
}
