package com.example.bkdiagnostic.protocol.ford

import com.example.bkdiagnostic.protocol.EcuConfig
import com.example.bkdiagnostic.protocol.ProtocolConfig
import com.example.bkdiagnostic.protocol.obd2.OBD2PidDef
import com.example.bkdiagnostic.protocol.obd2.OBD2Pids

// ════════════════════════════════════════════════════════════════════════════
//  Ford CAN ECU IDs (ISO 15765-4 — CAN 500kbps, 11-bit ID)
// ════════════════════════════════════════════════════════════════════════════
//
//  ECU           Request  Response
//  ─────────────────────────────────
//  Broadcast     0x7DF    (all)
//  Engine (PCM)  0x7E0    0x7E8
//  Transmission  0x7E1    0x7E9
//  ABS/Brakes    0x7E2    0x7EA
//  Airbag        0x7E3    0x7EB
//  Body (BCM)    0x7A0    0x7A8
//  Cluster       0x720    0x728
//  PSCM (EPS)    0x730    0x738

// ════════════════════════════════════════════════════════════════════════════
//  Ford-specific Mode 22 (UDS ReadDataByIdentifier) PIDs
//  Format request: [0x03][0x22][DID_H][DID_L][0x00 × 4]
//  Format response: [len][0x62][DID_H][DID_L][data…]
// ════════════════════════════════════════════════════════════════════════════

/** PID đặc trưng Ford dùng UDS Mode 0x22 */
object FordPids {

    val TURBO_BOOST = OBD2PidDef(
        pid = 0x1046,   // Ford DID 0x1046 — Turbocharger Boost Pressure (kPa gauge)
        mode = 0x22,
        name = "Áp suất tăng áp (Turbo Boost)", unit = "kPa",
        minBytes = 2, minValue = -100.0, maxValue = 250.0,
        decode = { b -> ((b[0].toUByte().toInt() shl 8) or b[1].toUByte().toInt()) * 0.1 - 100.0 }
    )

    val TRANS_TEMP = OBD2PidDef(
        pid = 0x1940,   // Ford DID — Transmission Fluid Temperature (°C)
        mode = 0x22,
        name = "Nhiệt độ dầu hộp số", unit = "°C",
        minBytes = 1, minValue = -40.0, maxValue = 215.0,
        decode = { b -> b[0].toUByte().toInt() - 40.0 }
    )

    val EGR_RATE = OBD2PidDef(
        pid = 0x2EF1,   // Ford DID — EGR Rate (%)
        mode = 0x22,
        name = "Tỉ lệ hồi lưu khí thải (EGR)", unit = "%",
        minBytes = 1, minValue = 0.0, maxValue = 100.0,
        decode = { b -> b[0].toUByte().toInt() * 100.0 / 255.0 }
    )

    val DPF_PRESSURE = OBD2PidDef(
        pid = 0x1030,   // Ford DID — DPF Differential Pressure (kPa) — chỉ máy diesel
        mode = 0x22,
        name = "Áp suất vi sai DPF", unit = "kPa",
        minBytes = 2, minValue = 0.0, maxValue = 100.0,
        decode = { b -> ((b[0].toUByte().toInt() shl 8) or b[1].toUByte().toInt()) * 0.01 }
    )

    val DPF_REGEN_STATUS = OBD2PidDef(
        pid = 0x103F,   // Ford DID — DPF Regeneration Status
        mode = 0x22,
        name = "Trạng thái tái sinh DPF", unit = "",
        minBytes = 1, minValue = 0.0, maxValue = 3.0,
        decode = { b -> b[0].toUByte().toDouble() }
        // 0=Idle, 1=Active, 2=Requested, 3=Inhibited
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Cấu hình từng model Ford
// ════════════════════════════════════════════════════════════════════════════

object FordConfigs {

    private val fordEcuMap = mapOf(
        "engine"       to EcuConfig("Engine / PCM",      requestCanId = 0x7E0, responseCanId = 0x7E8),
        "transmission" to EcuConfig("Hộp số (TCM)",       requestCanId = 0x7E1, responseCanId = 0x7E9),
        "abs"          to EcuConfig("Hệ thống phanh ABS", requestCanId = 0x7E2, responseCanId = 0x7EA),
        "airbag"       to EcuConfig("Túi khí (RCM)",      requestCanId = 0x7E3, responseCanId = 0x7EB),
        "bcm"          to EcuConfig("Thân xe (BCM)",      requestCanId = 0x7A0, responseCanId = 0x7A8),
    )

    // ── Ford Ranger (Wildtrak / XLT / Raptor) ─────────────────────────────

    val FORD_RANGER = ProtocolConfig(
        brandId = "ford", modelId = "ranger",
        displayName = "Ford Ranger",
        canBaudKbps = 500,
        requestCanId = 0x7DF,
        responseCanId = 0x7E8,
        livePids = listOf(
            // OBD2 chuẩn
            OBD2Pids.ENGINE_RPM,
            OBD2Pids.VEHICLE_SPEED,
            OBD2Pids.COOLANT_TEMP,
            OBD2Pids.ENGINE_LOAD,
            OBD2Pids.THROTTLE_POS,
            OBD2Pids.INTAKE_MAP,
            OBD2Pids.INTAKE_AIR_TEMP,
            OBD2Pids.MAF_RATE,
            OBD2Pids.FUEL_LEVEL,
            OBD2Pids.FUEL_RATE,
            OBD2Pids.OIL_TEMP,
            OBD2Pids.CONTROL_VOLTAGE,
            OBD2Pids.AMBIENT_TEMP,
            OBD2Pids.BARO_PRESSURE,
            // Ford đặc trưng (diesel 2.0 EcoBlue)
            FordPids.TURBO_BOOST,
            FordPids.TRANS_TEMP,
            FordPids.EGR_RATE,
            FordPids.DPF_PRESSURE,
            FordPids.DPF_REGEN_STATUS,
        ),
        ecuMap = fordEcuMap
    )

    val FORD_RANGER_RAPTOR = ProtocolConfig(
        brandId = "ford", modelId = "ranger_raptor",
        displayName = "Ford Ranger Raptor",
        canBaudKbps = 500,
        requestCanId = 0x7DF,
        responseCanId = 0x7E8,
        livePids = FORD_RANGER.livePids,    // Cùng platform T6
        ecuMap = fordEcuMap
    )

    val FORD_EVEREST = ProtocolConfig(
        brandId = "ford", modelId = "everest",
        displayName = "Ford Everest",
        canBaudKbps = 500,
        requestCanId = 0x7DF,
        responseCanId = 0x7E8,
        livePids = FORD_RANGER.livePids,    // Cùng platform T6
        ecuMap = fordEcuMap
    )

    val FORD_TERRITORY = ProtocolConfig(
        brandId = "ford", modelId = "territory",
        displayName = "Ford Territory",
        canBaudKbps = 500,
        requestCanId = 0x7DF,
        responseCanId = 0x7E8,
        livePids = listOf(
            OBD2Pids.ENGINE_RPM, OBD2Pids.VEHICLE_SPEED,
            OBD2Pids.COOLANT_TEMP, OBD2Pids.ENGINE_LOAD,
            OBD2Pids.THROTTLE_POS, OBD2Pids.FUEL_LEVEL,
            OBD2Pids.CONTROL_VOLTAGE, OBD2Pids.AMBIENT_TEMP,
        ),
        ecuMap = fordEcuMap
    )

    val FORD_TRANSIT = ProtocolConfig(
        brandId = "ford", modelId = "transit",
        displayName = "Ford Transit",
        canBaudKbps = 500,
        requestCanId = 0x7DF,
        responseCanId = 0x7E8,
        livePids = FORD_RANGER.livePids,
        ecuMap = fordEcuMap
    )

    val FORD_MUSTANG = ProtocolConfig(
        brandId = "ford", modelId = "mustang",
        displayName = "Ford Mustang",
        canBaudKbps = 500,
        requestCanId = 0x7DF,
        responseCanId = 0x7E8,
        livePids = listOf(
            OBD2Pids.ENGINE_RPM, OBD2Pids.VEHICLE_SPEED,
            OBD2Pids.COOLANT_TEMP, OBD2Pids.ENGINE_LOAD,
            OBD2Pids.THROTTLE_POS, OBD2Pids.MAF_RATE,
            OBD2Pids.FUEL_LEVEL, OBD2Pids.FUEL_RATE,
            OBD2Pids.OIL_TEMP, OBD2Pids.CONTROL_VOLTAGE,
            OBD2Pids.INTAKE_MAP, OBD2Pids.AMBIENT_TEMP,
        ),
        ecuMap = fordEcuMap
    )

    // Các model còn lại dùng config mặc định OBD2 chuẩn
    val FORD_EXPLORER = ProtocolConfig(
        brandId = "ford", modelId = "explorer",
        displayName = "Ford Explorer",
        canBaudKbps = 500, requestCanId = 0x7DF, responseCanId = 0x7E8,
        livePids = OBD2Pids.DASHBOARD_PIDS,
        ecuMap = fordEcuMap
    )
    val FORD_BRONCO = ProtocolConfig(
        brandId = "ford", modelId = "bronco",
        displayName = "Ford Bronco",
        canBaudKbps = 500, requestCanId = 0x7DF, responseCanId = 0x7E8,
        livePids = OBD2Pids.DASHBOARD_PIDS,
        ecuMap = fordEcuMap
    )
    val FORD_ESCAPE = ProtocolConfig(
        brandId = "ford", modelId = "escape",
        displayName = "Ford Escape / Kuga",
        canBaudKbps = 500, requestCanId = 0x7DF, responseCanId = 0x7E8,
        livePids = OBD2Pids.DASHBOARD_PIDS,
        ecuMap = fordEcuMap
    )
    val FORD_ECOSPORT = ProtocolConfig(
        brandId = "ford", modelId = "ecosport",
        displayName = "Ford EcoSport",
        canBaudKbps = 500, requestCanId = 0x7DF, responseCanId = 0x7E8,
        livePids = OBD2Pids.DASHBOARD_PIDS,
        ecuMap = fordEcuMap
    )
    val FORD_F150 = ProtocolConfig(
        brandId = "ford", modelId = "f150",
        displayName = "Ford F-150",
        canBaudKbps = 500, requestCanId = 0x7DF, responseCanId = 0x7E8,
        livePids = OBD2Pids.DASHBOARD_PIDS,
        ecuMap = fordEcuMap
    )
    val FORD_MACH_E = ProtocolConfig(
        brandId = "ford", modelId = "mach_e",
        displayName = "Ford Mustang Mach-E",
        canBaudKbps = 500, requestCanId = 0x7DF, responseCanId = 0x7E8,
        livePids = listOf(
            OBD2Pids.VEHICLE_SPEED, OBD2Pids.CONTROL_VOLTAGE,
            OBD2Pids.AMBIENT_TEMP, OBD2Pids.THROTTLE_POS,
            // BEV: không có RPM cổ máy đốt trong
        ),
        ecuMap = fordEcuMap
    )
}
