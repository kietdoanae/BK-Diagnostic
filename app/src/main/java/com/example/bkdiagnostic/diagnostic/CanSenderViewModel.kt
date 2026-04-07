package com.example.bkdiagnostic.diagnostic

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bkdiagnostic.BKDiagnosticApp
import com.example.bkdiagnostic.communication.CanFrame
import com.example.bkdiagnostic.communication.FrameProtocol
import com.example.bkdiagnostic.communication.UsbSerialManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class CanSenderViewModel(
    private val usb: UsbSerialManager
) : ViewModel() {

    // ── Input state ────────────────────────────────────────────────────────────
    val canIdInput = MutableStateFlow("")
    val dataBytesInput = MutableStateFlow("")
    val intervalMs = MutableStateFlow("500")
    val isRepeating = MutableStateFlow(false)

    // ── Derived state ──────────────────────────────────────────────────────────
    val dlcPreview: StateFlow<Int> = dataBytesInput
        .map { parseBytes(it).size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val inputError: StateFlow<String?> = combine(
        canIdInput, dataBytesInput
    ) { id, data -> validateInputs(id, data) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val usbConnected: StateFlow<Boolean> = usb.connectionState
        .map { it is UsbSerialManager.ConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Send log ───────────────────────────────────────────────────────────────
    private val _sendLog = MutableStateFlow<List<CanSendEntry>>(emptyList())
    val sendLog: StateFlow<List<CanSendEntry>> = _sendLog.asStateFlow()

    // ── Internal tracking ──────────────────────────────────────────────────────
    private val seqCounter = AtomicInteger(0)
    @Volatile private var pendingSeq = -1
    @Volatile private var pendingSentAt = 0L
    private var repeatJob: Job? = null

    // ── Frame response collector ───────────────────────────────────────────────
    init {
        viewModelScope.launch {
            usb.rawFrames.collect { pf ->
                val now = System.currentTimeMillis()
                when (pf.type) {
                    FrameProtocol.TYPE_ACK -> {
                        val s = pendingSeq
                        if (s >= 0) {
                            updateEntry(s) { it.copy(status = SendStatus.ACK, roundTripMs = now - pendingSentAt) }
                            pendingSeq = -1
                        }
                    }
                    FrameProtocol.TYPE_ERROR -> {
                        val s = pendingSeq
                        if (s >= 0) {
                            val errCode = pf.payload.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                            val msg = when (errCode) {
                                1 -> "CAN_SEND_FAIL"
                                3 -> "TX_TIMEOUT"
                                4 -> "BAD_FRAME"
                                else -> "ERROR(0x${errCode.toString(16).uppercase()})"
                            }
                            updateEntry(s) { it.copy(status = SendStatus.ERROR, roundTripMs = now - pendingSentAt, errorMsg = msg) }
                            pendingSeq = -1
                        }
                    }
                    FrameProtocol.TYPE_CAN_RX -> {
                        val s = pendingSeq
                        if (s >= 0) {
                            val frame = FrameProtocol.parseCanPayload(pf.payload) ?: return@collect
                            val resp = CanResponseEntry(
                                canId = frame.id,
                                dataBytes = frame.effectiveData(),
                                receivedAfterMs = now - pendingSentAt
                            )
                            appendResponse(s, resp)
                        }
                    }
                }
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun sendOnce() {
        if (validateInputs(canIdInput.value, dataBytesInput.value) != null) return
        val canId = parseCanId(canIdInput.value) ?: return
        val bytes = parseBytes(dataBytesInput.value)

        val s = seqCounter.incrementAndGet()
        val now = System.currentTimeMillis()
        val entry = CanSendEntry(
            seq = s,
            timestampMs = now,
            canId = canId,
            dataBytes = bytes,
            dlc = bytes.size,
            status = SendStatus.PENDING
        )
        addEntry(entry)
        pendingSeq = s
        pendingSentAt = now

        viewModelScope.launch {
            val padded = ByteArray(8)
            bytes.copyInto(padded, 0, 0, minOf(bytes.size, 8))
            usb.sendFrame(CanFrame(canId, bytes.size, padded))
            delay(2000)
            if (pendingSeq == s) {
                updateEntry(s) { it.copy(status = SendStatus.TIMEOUT) }
                pendingSeq = -1
            }
        }
    }

    fun toggleRepeat() {
        if (isRepeating.value) {
            repeatJob?.cancel()
            isRepeating.value = false
        } else {
            if (validateInputs(canIdInput.value, dataBytesInput.value) != null) return
            val interval = intervalMs.value.toLongOrNull()?.coerceAtLeast(50L) ?: 500L
            isRepeating.value = true
            repeatJob = viewModelScope.launch {
                while (isActive) {
                    sendOnce()
                    delay(interval)
                }
            }
        }
    }

    fun clearLog() { _sendLog.value = emptyList() }

    override fun onCleared() {
        repeatJob?.cancel()
        super.onCleared()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun addEntry(entry: CanSendEntry) {
        val current = _sendLog.value
        _sendLog.value = if (current.size >= 100) current.drop(1) + entry else current + entry
    }

    private fun updateEntry(s: Int, transform: (CanSendEntry) -> CanSendEntry) {
        _sendLog.value = _sendLog.value.map { if (it.seq == s) transform(it) else it }
    }

    private fun appendResponse(s: Int, resp: CanResponseEntry) {
        _sendLog.value = _sendLog.value.map {
            if (it.seq == s) it.copy(responses = it.responses + resp) else it
        }
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val usb = (application as BKDiagnosticApp).usbSerialManager
            @Suppress("UNCHECKED_CAST")
            return CanSenderViewModel(usb) as T
        }
    }
}

// ── Module-level pure functions ───────────────────────────────────────────────

private fun parseCanId(input: String): Int? =
    input.trim().toIntOrNull(16)?.takeIf { it in 0..0x7FF }

private fun parseBytes(input: String): ByteArray {
    if (input.isBlank()) return ByteArray(0)
    return input.trim()
        .split("\\s+".toRegex())
        .filter { it.isNotEmpty() }
        .mapNotNull { it.toIntOrNull(16)?.and(0xFF)?.toByte() }
        .toByteArray()
}

private fun validateInputs(canId: String, data: String): String? {
    val id = canId.trim()
    if (id.isBlank() || id.toIntOrNull(16) == null)
        return "CAN ID không hợp lệ (ví dụ: 7DF)"
    if (id.toInt(16) > 0x7FF)
        return "CAN ID vượt quá 11-bit (max 7FF)"
    if (data.isNotBlank()) {
        val tokens = data.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.forEachIndexed { i, t ->
            if (!t.matches("[0-9A-Fa-f]{1,2}".toRegex()))
                return "Byte không hợp lệ tại vị trí ${i + 1}"
        }
        if (tokens.size > 8) return "Tối đa 8 bytes (DLC ≤ 8)"
    }
    return null
}
