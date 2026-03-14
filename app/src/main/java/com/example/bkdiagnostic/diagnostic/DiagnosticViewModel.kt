package com.example.bkdiagnostic.diagnostic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bkdiagnostic.BKDiagnosticApp
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class DiagnosticViewModel(
    application: Application,
    val brandId: String,
    val modelId: String
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

    // ── Nội bộ ───────────────────────────────────────────────────────────────

    /** Lưu các Deferred chờ response theo PID (key = pid code) */
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<CanFrame?>>()
    /** Deferred chờ DTC response */
    private var pendingDtcRequest: CompletableDeferred<CanFrame?>? = null

    private var liveDataJob: Job? = null

    init {
        // Lắng nghe CAN frames đến từ STM32
        viewModelScope.launch {
            usbManager.canFrames.collect { frame -> dispatchFrame(frame) }
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

        _isLiveDataRunning.value = true
        liveDataJob = viewModelScope.launch(Dispatchers.IO) {
            // Thông báo STM32 tốc độ CAN bus
            usbManager.setCanBaud(config.canBaudKbps)
            delay(300)

            while (isActive) {
                for (pid in config.livePids) {
                    if (!isActive) break
                    val reading = requestPid(pid, config)
                    if (reading != null) {
                        _liveData.value = _liveData.value + (pid to reading)
                    }
                    delay(config.pollIntervalMs)
                }
            }
        }
    }

    fun stopLiveData() {
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

        val response = withTimeoutOrNull(config.responseTimeoutMs) { deferred.await() }
        pendingRequests.remove(pid.pid)

        return response?.let {
            OBD2Protocol.decodeLiveData(it, config.responseCanId)
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
                _message.value = if (dtcs.isNullOrEmpty()) "Không có mã lỗi nào."
                else "Tìm thấy ${dtcs.size} mã lỗi."
            } else {
                _message.value = "Không nhận được phản hồi từ ECU."
            }

            _isDtcLoading.value = false
        }
    }

    fun clearDtcs() {
        val config = protocolConfig ?: return
        viewModelScope.launch(Dispatchers.IO) {
            usbManager.sendFrame(OBD2Protocol.buildClearDtcRequest(config.requestCanId))
            delay(500)
            _dtcList.value = emptyList()
            _message.value = "Đã gửi lệnh xóa mã lỗi."
        }
    }

    fun clearMessage() { _message.value = null }

    // ════════════════════════════════════════════════════════════════════════
    //  Frame dispatcher — route CAN frame đến đúng handler
    // ════════════════════════════════════════════════════════════════════════

    private fun dispatchFrame(frame: CanFrame) {
        val config = protocolConfig ?: return
        if (frame.id != config.responseCanId) return

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
            0x44 -> _message.value = "Xóa mã lỗi thành công!"
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
        private val modelId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            DiagnosticViewModel(application, brandId, modelId) as T
    }
}
