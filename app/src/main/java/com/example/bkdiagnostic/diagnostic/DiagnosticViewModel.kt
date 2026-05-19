package com.example.bkdiagnostic.diagnostic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bkdiagnostic.ActivityLogger
import com.example.bkdiagnostic.BKDiagnosticApp
import com.example.bkdiagnostic.lab.LabEvidenceRepository
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
import com.example.bkdiagnostic.DiagnosticsSettings
import com.example.bkdiagnostic.communication.CanFrame
import com.example.bkdiagnostic.communication.UsbSerialManager
import com.example.bkdiagnostic.protocol.DashboardCanConfig
import com.example.bkdiagnostic.protocol.ProtocolConfig
import com.example.bkdiagnostic.protocol.ProtocolRegistry
import com.example.bkdiagnostic.protocol.obd2.DtcCode
import com.example.bkdiagnostic.protocol.obd2.OBD2PidDef
import com.example.bkdiagnostic.protocol.obd2.OBD2Protocol
import com.example.bkdiagnostic.protocol.obd2.SensorReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Một entry trong Raw Frame Monitor log (unified TX + RX).
 *
 *  @param direction TX = Android gửi ra, RX = nhận về từ bus
 *  @param source    Phân loại nguồn TX/RX:
 *                     - RX: "bus"
 *                     - TX: "live_data" / "dtc_read" / "dtc_clear" / "active_test"
 *                            / "manual" / "gauge_event" / "gauge_delta"
 */
data class RawFrameEntry(
    val seq: Int,
    val timestampMs: Long,
    val direction: Direction,
    val canId: Int,
    val rawBytes: ByteArray,
    val decoded: String,
    val source: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawFrameEntry) return false
        return seq == other.seq
    }
    override fun hashCode(): Int = seq
}

class DiagnosticViewModel(
    application: Application,
    val brandId: String,
    val modelId: String,
    val diagSettings: DiagnosticsSettings = DiagnosticsSettings()
) : AndroidViewModel(application) {

    // ── Protocol config ──────────────────────────────────────────────────────

    val protocolConfig: ProtocolConfig? = ProtocolRegistry.get(brandId, modelId)

    // ── USB Serial Manager (singleton từ Application) ────────────────────────

    private val usbManager: UsbSerialManager =
        (application as BKDiagnosticApp).usbSerialManager

    val connectionState = usbManager.connectionState

    // ── Live Data ────────────────────────────────────────────────────────────
    // Use ConcurrentHashMap to avoid creating a new immutable Map on every PID reading.
    // Emit snapshots only when UI needs to observe.
    private val _liveDataMap = ConcurrentHashMap<OBD2PidDef, SensorReading>()
    private val _liveData = MutableStateFlow<Map<OBD2PidDef, SensorReading>>(emptyMap())
    val liveData: StateFlow<Map<OBD2PidDef, SensorReading>> = _liveData.asStateFlow()

    private val _isLiveDataRunning = MutableStateFlow(false)
    val isLiveDataRunning: StateFlow<Boolean> = _isLiveDataRunning.asStateFlow()

    // ── DTCs ─────────────────────────────────────────────────────────────────

    private val _dtcList = MutableStateFlow<List<DtcCode>>(emptyList())
    val dtcList: StateFlow<List<DtcCode>> = _dtcList.asStateFlow()

    private val _isDtcLoading = MutableStateFlow(false)
    val isDtcLoading: StateFlow<Boolean> = _isDtcLoading.asStateFlow()

    // ── Thông báo ─────────────────────────────────────────────────────────────

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ── Update rate (số reading/giây) ────────────────────────────────────────

    private val _updateRate = MutableStateFlow(0f)
    val updateRate: StateFlow<Float> = _updateRate.asStateFlow()
    private val rateTimestamps = ArrayDeque<Long>()

    // ── Raw Frame Monitor ────────────────────────────────────────────────────
    // Delegate to UnifiedRawFrameStore singleton (shared with CanSenderViewModel,
    // CSV export, and Supabase upload). See UnifiedRawFrameStore.kt for ring
    // buffer (20,000 entries) and gauge delta logging.
    val rawFrameLog: StateFlow<List<RawFrameEntry>> = UnifiedRawFrameStore.flow

    // ── Nội bộ ───────────────────────────────────────────────────────────────

    /** Lưu các Deferred chờ response theo PID (key = pid code) */
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<CanFrame?>>()
    /** Deferred chờ DTC response */
    private var pendingDtcRequest: CompletableDeferred<CanFrame?>? = null

    private var liveDataJob: Job? = null

    init {
        // Lắng nghe CAN frames đến từ STM32. addToRawLog() ghi vào
        // UnifiedRawFrameStore, store sẽ tự forward sang LabEvidence khi
        // có lab session active.
        viewModelScope.launch {
            usbManager.canFrames.collect { frame ->
                dispatchFrame(frame)
                addToRawLog(frame)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  USB Connection
    // ════════════════════════════════════════════════════════════════════════

    fun connectUsb() = usbManager.connect()

    fun disconnectUsb() {
        stopLiveData()
        usbManager.disconnect()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Live Data — đọc liên tục từng PID theo vòng lặp
    // ════════════════════════════════════════════════════════════════════════

    fun startLiveData() {
        val config = protocolConfig ?: return
        if (_isLiveDataRunning.value) return

        ActivityLogger.liveDataStart(brandId, modelId)
        _isLiveDataRunning.value = true
        liveDataJob = viewModelScope.launch(Dispatchers.IO) {
            // Thông báo STM32 tốc độ CAN bus
            usbManager.setCanBaud(config.canBaudKbps)
            delay(300)

            // Dùng pollIntervalMs từ settings nếu user đã cấu hình, fallback về protocol default
            val pollMs = diagSettings.pollIntervalMs

            while (isActive) {
                for (pid in config.livePids) {
                    if (!isActive) break
                    val reading = requestPid(pid, config)
                    if (reading != null) {
                        _liveDataMap[pid] = reading
                        _liveData.value = _liveDataMap.toMap()
                    }
                    delay(pollMs)
                }
            }
        }
    }

    fun stopLiveData() {
        if (_isLiveDataRunning.value) ActivityLogger.liveDataStop(brandId, modelId)
        liveDataJob?.cancel()
        liveDataJob = null
        _isLiveDataRunning.value = false
    }

    /** Gửi request 1 PID và chờ response (có timeout) */
    private suspend fun requestPid(pid: OBD2PidDef, config: ProtocolConfig): SensorReading? {
        val deferred = CompletableDeferred<CanFrame?>()
        pendingRequests[pid.pid] = deferred

        val requestFrame = OBD2Protocol.buildLiveDataRequest(pid, config.requestCanId)
        // Log TX trước khi gửi để Raw Monitor thấy chiều "request"
        logTxFrame(requestFrame, source = "live_data",
            decoded = "Live Data request: ${pid.name} (PID=0x%02X)".format(pid.pid))
        usbManager.sendFrame(requestFrame)

        val response = withTimeoutOrNull(diagSettings.responseTimeoutMs) { deferred.await() }
        pendingRequests.remove(pid.pid)

        return response?.let {
            OBD2Protocol.decodeLiveData(it, config.responseCanId, config.livePids)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DTC — đọc mã lỗi
    // ════════════════════════════════════════════════════════════════════════

    fun readDtcs() {
        val config = protocolConfig ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isDtcLoading.value = true
            _dtcList.value = emptyList()

            val deferred = CompletableDeferred<CanFrame?>()
            pendingDtcRequest = deferred

            val dtcReq = OBD2Protocol.buildDtcRequest(config.requestCanId)
            logTxFrame(dtcReq, source = "dtc_read", decoded = "DTC read request (Mode 03)")
            usbManager.sendFrame(dtcReq)

            val response = withTimeoutOrNull(1500L) { deferred.await() }
            pendingDtcRequest = null

            if (response != null) {
                val dtcs = OBD2Protocol.decodeDtcResponse(response, config.responseCanId)
                _dtcList.value = dtcs ?: emptyList()
                ActivityLogger.dtcRead(brandId, modelId, dtcs?.size ?: 0)
                if (dtcs.isNullOrEmpty()) {
                    _message.value = "There is no error code."
                } else {
                    _message.value = "Found ${dtcs.size} error codes."
                    if (diagSettings.autoClearDtc) {
                        delay(300)
                        clearDtcs()
                    }
                }
            } else {
                _message.value = "No response from the ECU."
            }

            _isDtcLoading.value = false
        }
    }

    fun clearDtcs() {
        val config = protocolConfig ?: return
        ActivityLogger.dtcClear(brandId, modelId)
        viewModelScope.launch(Dispatchers.IO) {
            val clearReq = OBD2Protocol.buildClearDtcRequest(config.requestCanId)
            logTxFrame(clearReq, source = "dtc_clear", decoded = "DTC clear request (Mode 04)")
            usbManager.sendFrame(clearReq)
            delay(500)
            _dtcList.value = emptyList()
            _message.value = "Sent command to clear the error code."
        }
    }

    fun clearMessage() { _message.value = null }

    // ════════════════════════════════════════════════════════════════════════
    //  Active Test — gửi lệnh kích hoạt cơ cấu chấp hành
    // ════════════════════════════════════════════════════════════════════════

    fun sendActiveTestCommand(canId: Int, data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val frame = CanFrame(
                id  = canId,
                dlc = data.size.coerceAtMost(8),
                data = ByteArray(8).also { buf -> data.copyInto(buf) }
            )
            // Log TX vào unified store trước khi gửi
            logTxFrame(frame, source = "active_test",
                decoded = "Active Test: CAN 0x%03X".format(canId))
            usbManager.sendFrame(frame)
            // Lab: push active_test evidence when session is active
            val labState = LabModeManager.state.value
            if (labState is LabModeState.Active) {
                LabEvidenceRepository.pushActiveTest(labState.sessionId, canId, data)
            }
        }
    }

    /**
     * Gửi 1 frame "đánh thức" cụm đồng hồ ngay sau khi USB-CAN kết nối thành công.
     *
     *   CAN ID: 0x3B3
     *   Data  : 44 88 C0 0C E6 00 03 3A
     *
     * Cluster Ford Ranger sau khi mất nguồn / từ trạng thái sleep cần 1 frame
     * "kick" trên 0x3B3 để các module nội bộ vào trạng thái Active, nếu không
     * các lệnh Active Test / gauge stream phía sau sẽ bị ignore.
     *
     * Hàm này idempotent — gọi nhiều lần không gây tác hại; nhưng caller nên
     * gọi đúng 1 lần khi state chuyển sang Connected (xem LaunchedEffect trong
     * [ActiveTestScreen]).
     */
    fun wakeUpCluster() {
        val data = byteArrayOf(
            0x44.toByte(), 0x88.toByte(), 0xC0.toByte(), 0x0C.toByte(),
            0xE6.toByte(), 0x00.toByte(), 0x03.toByte(), 0x3A.toByte()
        )
        viewModelScope.launch(Dispatchers.IO) {
            val frame = CanFrame(id = 0x3B3, dlc = 8, data = data)
            logTxFrame(frame, source = "active_test",
                decoded = "Cluster Wake-Up: CAN 0x3B3")
            usbManager.sendFrame(frame)
            _message.value = "Đồng hồ đã được đánh thức!"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Gauge Streaming — gửi CAN frame liên tục để điều khiển kim đồng hồ
    //  (RPM, Speed). Cluster cần stream liên tục mỗi 50-100ms để kim không
    //  reset về 0 do timeout watchdog.
    // ════════════════════════════════════════════════════════════════════════

    /** True khi luồng gửi gauge đang chạy */
    private val _gaugeStreamActive = MutableStateFlow(false)
    val gaugeStreamActive: StateFlow<Boolean> = _gaugeStreamActive.asStateFlow()

    /** Giá trị RPM hiện tại (0..maxValue) — UI cập nhật khi kéo slider */
    val gaugeRpm = MutableStateFlow(0)

    /** Giá trị Speed hiện tại (km/h) — UI cập nhật khi kéo slider */
    val gaugeSpeed = MutableStateFlow(0)

    /** Số frame đã gửi trong session streaming hiện tại — debug counter */
    private val _gaugeFrameCount = MutableStateFlow(0)
    val gaugeFrameCount: StateFlow<Int> = _gaugeFrameCount.asStateFlow()

    private var gaugeStreamJob: Job? = null

    /**
     * Bắt đầu stream RPM + Speed liên tục theo chu kỳ [intervalMs].
     * Mỗi frame encode bằng [DashboardCanConfig.GaugeEntry.encode] — vị trí byte
     * & scaleFactor được lấy hoàn toàn từ JSON config.
     *
     * Gọi [updateGaugeRpm] / [updateGaugeSpeed] để thay đổi giá trị real-time.
     */
    fun startGaugeStream(
        rpm: DashboardCanConfig.GaugeEntry?,
        speed: DashboardCanConfig.GaugeEntry?,
        intervalMs: Long = 100L,
    ) {
        if (_gaugeStreamActive.value) return
        if (rpm == null && speed == null) {
            _message.value = "Chưa cấu hình CAN ID cho RPM/Speed"
            return
        }
        _gaugeStreamActive.value = true
        _gaugeFrameCount.value = 0
        // Reset delta tracking — START event sẽ được log như force-event
        UnifiedRawFrameStore.resetGaugeDelta()
        gaugeStreamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var firstTick = true
                while (isActive) {
                    val rpmVal = gaugeRpm.value
                    val speedVal = gaugeSpeed.value

                    // ── RPM frame ─ encode theo template trong config ──
                    if (rpm != null) {
                        val rpmFrame = CanFrame(id = rpm.canId, dlc = 8, data = rpm.encode(rpmVal))
                        UnifiedRawFrameStore.addGaugeFrame(
                            rpmFrame, GaugeKind.RPM, currentValue = rpmVal,
                            force = firstTick
                        )
                        usbManager.sendFrame(rpmFrame)
                    }

                    // ── Speed frame ─ encode theo template trong config ──
                    if (speed != null) {
                        val speedFrame = CanFrame(id = speed.canId, dlc = 8, data = speed.encode(speedVal))
                        UnifiedRawFrameStore.addGaugeFrame(
                            speedFrame, GaugeKind.SPEED, currentValue = speedVal,
                            force = firstTick
                        )
                        usbManager.sendFrame(speedFrame)
                    }

                    firstTick = false
                    _gaugeFrameCount.value = _gaugeFrameCount.value + 1
                    delay(intervalMs)
                }
            } finally {
                _gaugeStreamActive.value = false
            }
        }
    }

    /**
     * Dừng stream và gửi 1 frame zero để cluster reset kim về 0.
     */
    fun stopGaugeStream(
        rpmCanId: Int? = null,
        speedCanId: Int? = null,
    ) {
        gaugeStreamJob?.cancel()
        gaugeStreamJob = null
        _gaugeStreamActive.value = false
        // Gửi frame "tắt" (zero) để cluster reset kim ngay lập tức, log như STOP event
        viewModelScope.launch(Dispatchers.IO) {
            if (rpmCanId != null) {
                val zero = CanFrame(id = rpmCanId, dlc = 8, data = ByteArray(8))
                UnifiedRawFrameStore.addGaugeFrame(zero, GaugeKind.RPM, currentValue = 0, force = true)
                usbManager.sendFrame(zero)
            }
            if (speedCanId != null) {
                val zero = CanFrame(id = speedCanId, dlc = 8, data = ByteArray(8))
                UnifiedRawFrameStore.addGaugeFrame(zero, GaugeKind.SPEED, currentValue = 0, force = true)
                usbManager.sendFrame(zero)
            }
        }
        gaugeRpm.value = 0
        gaugeSpeed.value = 0
        UnifiedRawFrameStore.resetGaugeDelta()
    }

    fun updateGaugeRpm(value: Int) { gaugeRpm.value = value.coerceAtLeast(0) }
    fun updateGaugeSpeed(value: Int) { gaugeSpeed.value = value.coerceAtLeast(0) }

    fun clearRawLog() {
        UnifiedRawFrameStore.clear()
    }

    /** Ghi RX frame vào unified store (gọi từ usbManager.canFrames collector). */
    private fun addToRawLog(frame: CanFrame) {
        val decoded = protocolConfig?.let { tryDecodeFrame(frame, it) } ?: "—"
        UnifiedRawFrameStore.addRx(frame, decoded)
    }

    /**
     * Ghi TX frame vào unified store. Gọi từ requestPid / readDtcs / clearDtcs
     * / sendActiveTestCommand / gauge stream.
     */
    private fun logTxFrame(frame: CanFrame, source: String, decoded: String) {
        UnifiedRawFrameStore.addTx(frame, source, decoded)
    }

    /** Cố giải mã frame thành chuỗi mô tả, trả về "—" nếu không nhận dạng được */
    private fun tryDecodeFrame(frame: CanFrame, config: com.example.bkdiagnostic.protocol.ProtocolConfig): String {
        val reading = OBD2Protocol.decodeLiveData(frame, config.responseCanId, config.livePids)
        if (reading != null) return "${reading.pid.name} = ${reading.formatted()}"

        val data = frame.effectiveData()
        if (data.size < 2) return "—"
        return when (data[1].toUByte().toInt()) {
            0x41 -> if (data.size >= 3)
                "Mode 01, PID=0x${data[2].toUByte().toInt().toString(16).uppercase().padStart(2, '0')}"
                else "Mode 01 response"
            0x62 -> if (data.size >= 4) {
                val did = (data[2].toUByte().toInt() shl 8) or data[3].toUByte().toInt()
                "Mode 22, DID=0x${did.toString(16).uppercase().padStart(4, '0')}"
            } else "Mode 22 response"
            0x43 -> "DTC Response (Mode 03)"
            0x44 -> "DTC cleared successfully (Mode 04)"
            0x7F -> "Negative response (NRC)"
            else -> "—"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Frame dispatcher — route CAN frame đến đúng handler
    // ════════════════════════════════════════════════════════════════════════

    private fun dispatchFrame(frame: CanFrame) {
        val config = protocolConfig ?: return
        if (frame.id != config.responseCanId) return
        if (frame.data.size < 3) return  // Need at least [len][mode][data]

        val byte1 = frame.data[1].toUByte().toInt()
        when (byte1) {
            // Mode 01 live data response (0x41)
            0x41 -> {
                val pid = frame.data[2].toUByte().toInt()
                pendingRequests[pid]?.complete(frame)
                trackUpdateRate()
            }

            // Mode 22 (UDS) live data response (0x62)
            0x62 -> {
                val did = (frame.data[2].toUByte().toInt() shl 8) or frame.data[3].toUByte().toInt()
                pendingRequests[did]?.complete(frame)
                trackUpdateRate()
            }

            // Mode 03 DTC response (0x43)
            0x43 -> pendingDtcRequest?.complete(frame)

            // Mode 04 clear DTC ACK (0x44)
            0x44 -> _message.value = "Error code cleared successfully!"
        }
    }

    private fun trackUpdateRate() {
        val now = System.currentTimeMillis()
        rateTimestamps.addLast(now)
        while (rateTimestamps.isNotEmpty() && now - rateTimestamps.first() > 3000L)
            rateTimestamps.removeFirst()
        _updateRate.value = rateTimestamps.size / 3f
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveData()
        gaugeStreamJob?.cancel()
        gaugeStreamJob = null
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Factory
    // ════════════════════════════════════════════════════════════════════════

    class Factory(
        private val application: Application,
        private val brandId: String,
        private val modelId: String,
        private val diagSettings: DiagnosticsSettings = DiagnosticsSettings()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            DiagnosticViewModel(application, brandId, modelId, diagSettings) as T
    }
}
