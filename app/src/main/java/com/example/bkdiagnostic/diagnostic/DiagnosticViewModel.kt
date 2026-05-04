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
import java.util.concurrent.atomic.AtomicInteger

/** Một entry trong Raw Frame Monitor log */
data class RawFrameEntry(
    val seq: Int,
    val timestampMs: Long,
    val canId: Int,
    val rawBytes: ByteArray,
    val decoded: String
)

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

    private val _rawFrameLog = MutableStateFlow<List<RawFrameEntry>>(emptyList())
    val rawFrameLog: StateFlow<List<RawFrameEntry>> = _rawFrameLog.asStateFlow()
    private val rawSeq = AtomicInteger(0)

    // ── Nội bộ ───────────────────────────────────────────────────────────────

    /** Lưu các Deferred chờ response theo PID (key = pid code) */
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<CanFrame?>>()
    /** Deferred chờ DTC response */
    private var pendingDtcRequest: CompletableDeferred<CanFrame?>? = null

    private var liveDataJob: Job? = null

    init {
        // Lắng nghe CAN frames đến từ STM32
        viewModelScope.launch {
            usbManager.canFrames.collect { frame ->
                dispatchFrame(frame)
                addToRawLog(frame)
                // Lab: enqueue frame for evidence collection when session is active
                val labState = LabModeManager.state.value
                if (labState is LabModeState.Active) {
                    LabEvidenceRepository.enqueueRawFrame(labState.sessionId, frame)
                }
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
                        _liveData.update { it + (pid to reading) }
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

            usbManager.sendFrame(OBD2Protocol.buildDtcRequest(config.requestCanId))

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
            usbManager.sendFrame(OBD2Protocol.buildClearDtcRequest(config.requestCanId))
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
            usbManager.sendFrame(frame)
            // Lab: push active_test evidence when session is active
            val labState = LabModeManager.state.value
            if (labState is LabModeState.Active) {
                LabEvidenceRepository.pushActiveTest(labState.sessionId, canId, data)
            }
        }
    }

    fun clearRawLog() { _rawFrameLog.value = emptyList() }

    private fun addToRawLog(frame: CanFrame) {
        val decoded = protocolConfig?.let { tryDecodeFrame(frame, it) } ?: "—"
        val entry = RawFrameEntry(
            seq = rawSeq.incrementAndGet(),
            timestampMs = System.currentTimeMillis(),
            canId = frame.id,
            rawBytes = frame.effectiveData(),
            decoded = decoded
        )
        _rawFrameLog.update { current ->
            if (current.size >= 5000) current.drop(1) + entry else current + entry
        }
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
