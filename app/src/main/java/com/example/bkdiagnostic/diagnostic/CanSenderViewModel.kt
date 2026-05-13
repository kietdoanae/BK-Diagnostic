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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CanSenderViewModel(
    private val usb: UsbSerialManager
) : ViewModel() {

    // ── Input state ────────────────────────────────────────────────────────────
    private val _canIdInput = MutableStateFlow("")
    val canIdInput: StateFlow<String> = _canIdInput.asStateFlow()

    private val _dataBytesInput = MutableStateFlow("")
    val dataBytesInput: StateFlow<String> = _dataBytesInput.asStateFlow()

    private val _intervalMs = MutableStateFlow("500")
    val intervalMs: StateFlow<String> = _intervalMs.asStateFlow()

    val isRepeating = MutableStateFlow(false)

    fun onCanIdChanged(value: String) { _canIdInput.value = value }
    fun onDataBytesChanged(value: String) { _dataBytesInput.value = value }
    fun onIntervalChanged(value: String) { _intervalMs.value = value }

    // ── Derived state ──────────────────────────────────────────────────────────
    val dlcPreview: StateFlow<Int> = _dataBytesInput
        .map { parseBytes(it).size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val inputError: StateFlow<String?> = combine(
        _canIdInput, _dataBytesInput
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
    // Fix 1: atomic pending state — seq + sentAt updated together
    private val pendingState = AtomicReference<Pair<Int, Long>?>(null)
    // Fix 1: atomic last-acked state — ackedSeq + ackedAt updated together
    private val lastAckedState = AtomicReference<Pair<Int, Long>?>(null)
    private var repeatJob: Job? = null

    // ── Frame response collector ───────────────────────────────────────────────
    init {
        viewModelScope.launch {
            usb.rawFrames.collect { pf ->
                val now = System.currentTimeMillis()
                when (pf.type) {
                    FrameProtocol.TYPE_ACK -> {
                        val state = pendingState.get() ?: return@collect
                        val (seq, sentAt) = state
                        updateEntry(seq) { it.copy(status = SendStatus.ACK, roundTripMs = now - sentAt) }
                        lastAckedState.set(Pair(seq, now))
                        pendingState.set(null)
                    }
                    FrameProtocol.TYPE_ERROR -> {
                        val state = pendingState.get() ?: return@collect
                        val (seq, sentAt) = state
                        val errCode = pf.payload.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                        val msg = when (errCode) {
                            1 -> "CAN_SEND_FAIL"
                            3 -> "TX_TIMEOUT"
                            4 -> "BAD_FRAME"
                            else -> "ERROR(0x${errCode.toString(16).uppercase()})"
                        }
                        updateEntry(seq) { it.copy(status = SendStatus.ERROR, roundTripMs = now - sentAt, errorMsg = msg) }
                        pendingState.set(null)
                    }
                    FrameProtocol.TYPE_CAN_RX -> {
                        val frame = FrameProtocol.parseCanPayload(pf.payload) ?: return@collect
                        val resp = CanResponseEntry(
                            canId = frame.id,
                            dataBytes = frame.effectiveData(),
                            receivedAfterMs = now - (pendingState.get()?.second ?: now)
                        )
                        val pending = pendingState.get()
                        lastAckedState.get()?.let { (ackedSeq, ackedAt) ->
                            when {
                                pending != null -> appendResponse(pending.first, resp)
                                (now - ackedAt) < 2000L -> appendResponse(ackedSeq, resp)
                            }
                        } ?: run {
                            if (pending != null) appendResponse(pending.first, resp)
                        }
                    }
                }
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun sendOnce() {
        if (validateInputs(_canIdInput.value, _dataBytesInput.value) != null) return
        val canId = parseCanId(_canIdInput.value) ?: return
        val bytes = parseBytes(_dataBytesInput.value)

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
        pendingState.set(Pair(s, now))

        // Fix 3: try/finally so pendingState is always cleared on throw or cancellation
        viewModelScope.launch {
            try {
                val padded = bytes.copyOf(8)
                val frame = CanFrame(canId, bytes.size, padded)
                // Log TX vào UnifiedRawFrameStore — đồng bộ với Monitor tab + CSV + Supabase
                UnifiedRawFrameStore.addTx(
                    frame,
                    source = "manual",
                    decoded = "Manual send CAN 0x%03X".format(canId)
                )
                usb.sendFrame(frame)
                delay(2000)
            } finally {
                val state = pendingState.get()
                if (state?.first == s) {
                    updateEntry(s) { it.copy(status = SendStatus.TIMEOUT) }
                    pendingState.compareAndSet(state, null)
                }
            }
        }
    }

    fun toggleRepeat() {
        if (isRepeating.value) {
            repeatJob?.cancel()
            isRepeating.value = false
        } else {
            if (validateInputs(_canIdInput.value, _dataBytesInput.value) != null) return
            val interval = _intervalMs.value.toLongOrNull()?.coerceAtLeast(50L) ?: 500L
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

    // Fix 2: use update() for atomic read-modify-write on the log
    private fun addEntry(entry: CanSendEntry) {
        _sendLog.update { current ->
            if (current.size >= 100) current.drop(1) + entry else current + entry
        }
    }

    private fun updateEntry(seq: Int, transform: (CanSendEntry) -> CanSendEntry) {
        _sendLog.update { current ->
            current.map { if (it.seq == seq) transform(it) else it }
        }
    }

    private fun appendResponse(seq: Int, response: CanResponseEntry) {
        _sendLog.update { current ->
            current.map { if (it.seq == seq) it.copy(responses = it.responses + response) else it }
        }
    }
}

// ── Factory ────────────────────────────────────────────────────────────────────

class CanSenderViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val usb = (application as BKDiagnosticApp).usbSerialManager
        @Suppress("UNCHECKED_CAST")
        return CanSenderViewModel(usb) as T
    }
}

// ── Module-level pure functions ───────────────────────────────────────────────

internal fun parseCanId(input: String): Int? =
    input.trim().toIntOrNull(16)?.takeIf { it in 0..0x7FF }

internal fun parseBytes(input: String): ByteArray {
    if (input.isBlank()) return ByteArray(0)
    return input.trim()
        .split("\\s+".toRegex())
        .filter { it.isNotEmpty() }
        .mapNotNull { it.toIntOrNull(16)?.and(0xFF)?.toByte() }
        .toByteArray()
}

internal fun validateInputs(canId: String, data: String): String? {
    val id = canId.trim()
    val idInt = id.toIntOrNull(16) ?: return "CAN ID không hợp lệ (ví dụ: 7DF)"
    if (idInt > 0x7FF) return "CAN ID vượt quá 11-bit (max 7FF)"
    if (data.isNotBlank()) {
        val tokens = data.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.forEachIndexed { i, t ->
            if (!t.matches(Regex("[0-9A-Fa-f]{2}")))
                return "Byte không hợp lệ tại vị trí ${i + 1}"
        }
        if (tokens.size > 8) return "Tối đa 8 bytes (DLC ≤ 8)"
    }
    return null
}
