# CAN Sender Feature — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Send" tab to Raw Monitor screen so users can compose, send, and log CAN frames manually with round-trip ACK delay display.

**Architecture:** `CanSenderViewModel` (new) subscribes to a new `rawFrames: SharedFlow<ParsedFrame>` exposed by `UsbSerialManager`, which emits every parsed frame including ACK and ERROR. The Send tab is added to `RawMonitorScreen` alongside the existing Monitor tab. No STM32 firmware changes needed.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow/SharedFlow, AndroidViewModel, ViewModelProvider.Factory

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `communication/UsbSerialManager.kt` | Modify | Expose `rawFrames: SharedFlow<ParsedFrame>` |
| `diagnostic/CanSendModels.kt` | Create | `SendStatus`, `CanResponseEntry`, `CanSendEntry` data models |
| `diagnostic/CanSenderViewModel.kt` | Create | Input state, validation, sendOnce(), toggleRepeat(), send log |
| `ui/screens/RawMonitorScreen.kt` | Modify | Add TabRow + `CanSendTab` composable |

---

## Task 1: Expose `rawFrames` from UsbSerialManager

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/communication/UsbSerialManager.kt:59-61` (flow declarations)
- Modify: `app/src/main/java/com/example/bkdiagnostic/communication/UsbSerialManager.kt:227-238` (`handleParsedFrame`)

- [ ] **Step 1: Add `_rawFrames` backing flow after `_canFrames` (line ~60)**

```kotlin
// After this existing line:
val canFrames: SharedFlow<CanFrame> = _canFrames.asSharedFlow()

// Add:
private val _rawFrames = MutableSharedFlow<ParsedFrame>(extraBufferCapacity = 64)
val rawFrames: SharedFlow<ParsedFrame> = _rawFrames.asSharedFlow()
```

- [ ] **Step 2: Emit every parsed frame in `handleParsedFrame`**

Replace the existing `handleParsedFrame` function (lines 227–238):

```kotlin
private suspend fun handleParsedFrame(pf: ParsedFrame) {
    _rawFrames.emit(pf)          // ← NEW: emit all frames before routing
    when (pf.type) {
        FrameProtocol.TYPE_CAN_RX -> {
            val frame = FrameProtocol.parseCanPayload(pf.payload) ?: return
            _canFrames.emit(frame)
        }
        FrameProtocol.TYPE_ERROR -> {
            // Handled by rawFrames subscribers (e.g. CanSenderViewModel)
        }
        // ACK and STATUS: handled by rawFrames subscribers
    }
}
```

- [ ] **Step 3: Verify it compiles**

```bash
cd C:/Users/KIET/AndroidStudioProjects/BKDiagnostic
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/communication/UsbSerialManager.kt
git commit -m "feat(comm): expose rawFrames SharedFlow for ACK/ERROR observation"
```

---

## Task 2: Create Data Models

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/diagnostic/CanSendModels.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.bkdiagnostic.diagnostic

enum class SendStatus { PENDING, ACK, ERROR, TIMEOUT }

data class CanResponseEntry(
    val canId: Int,
    val dataBytes: ByteArray,
    val receivedAfterMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanResponseEntry) return false
        return canId == other.canId &&
               dataBytes.contentEquals(other.dataBytes) &&
               receivedAfterMs == other.receivedAfterMs
    }
    override fun hashCode(): Int {
        var r = canId
        r = 31 * r + dataBytes.contentHashCode()
        r = 31 * r + receivedAfterMs.hashCode()
        return r
    }
}

data class CanSendEntry(
    val seq: Int,
    val timestampMs: Long,
    val canId: Int,
    val dataBytes: ByteArray,
    val dlc: Int,
    val status: SendStatus,
    val roundTripMs: Long? = null,
    val errorMsg: String? = null,
    val responses: List<CanResponseEntry> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanSendEntry) return false
        return seq == other.seq &&
               timestampMs == other.timestampMs &&
               canId == other.canId &&
               dataBytes.contentEquals(other.dataBytes) &&
               dlc == other.dlc &&
               status == other.status &&
               roundTripMs == other.roundTripMs &&
               errorMsg == other.errorMsg &&
               responses == other.responses
    }
    override fun hashCode(): Int {
        var r = seq
        r = 31 * r + timestampMs.hashCode()
        r = 31 * r + canId
        r = 31 * r + dataBytes.contentHashCode()
        r = 31 * r + dlc
        r = 31 * r + status.hashCode()
        r = 31 * r + (roundTripMs?.hashCode() ?: 0)
        r = 31 * r + (errorMsg?.hashCode() ?: 0)
        r = 31 * r + responses.hashCode()
        return r
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/diagnostic/CanSendModels.kt
git commit -m "feat(diagnostic): add CanSendEntry, CanResponseEntry, SendStatus models"
```

---

## Task 3: Create CanSenderViewModel

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/diagnostic/CanSenderViewModel.kt`

- [ ] **Step 1: Create the ViewModel file**

```kotlin
package com.example.bkdiagnostic.diagnostic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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

    val inputError: StateFlow<String?> = kotlinx.coroutines.flow.combine(
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
            // Pad data to 8 bytes for CanFrame
            val padded = ByteArray(8)
            bytes.copyInto(padded, 0, 0, minOf(bytes.size, 8))
            usb.sendFrame(CanFrame(canId, bytes.size, padded))
            // Timeout after 2 s if no ACK/ERROR received
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

// ── Module-level pure functions (private, used by ViewModel) ──────────────────

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
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/diagnostic/CanSenderViewModel.kt
git commit -m "feat(diagnostic): add CanSenderViewModel with send/repeat/log logic"
```

---

## Task 4: Update RawMonitorScreen with Send Tab

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/screens/RawMonitorScreen.kt`

- [ ] **Step 1: Add imports at the top of the file**

After the existing imports, add:

```kotlin
import android.app.Application
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bkdiagnostic.diagnostic.CanResponseEntry
import com.example.bkdiagnostic.diagnostic.CanSendEntry
import com.example.bkdiagnostic.diagnostic.CanSenderViewModel
import com.example.bkdiagnostic.diagnostic.SendStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

- [ ] **Step 2: Instantiate `CanSenderViewModel` and add tab state inside `RawMonitorScreen`**

At the top of `RawMonitorScreen` composable body, before existing state collection, add:

```kotlin
val application = LocalContext.current.applicationContext as Application
val senderVm: CanSenderViewModel = viewModel(factory = CanSenderViewModel.Factory(application))
var selectedTab by rememberSaveable { mutableIntStateOf(0) }
```

- [ ] **Step 3: Wrap existing content with Column + TabRow**

The existing content in `RawMonitorScreen` starts after the top bar. Wrap it with a Column and add a `TabRow` above it:

```kotlin
// Add TabRow at the top of the screen body, before the existing Column/Box content:
TabRow(selectedTabIndex = selectedTab) {
    Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Monitor") }
    )
    Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Send") }
    )
}

// Existing monitor content — wrap in: if (selectedTab == 0) { ... }
// New send content:
if (selectedTab == 1) {
    CanSendTab(vm = senderVm)
}
```

- [ ] **Step 4: Add `CanSendTab` composable**

Add this new private composable at the bottom of the file:

```kotlin
@Composable
private fun CanSendTab(vm: CanSenderViewModel) {
    val canIdInput by vm.canIdInput.collectAsStateWithLifecycle()
    val dataBytesInput by vm.dataBytesInput.collectAsStateWithLifecycle()
    val dlc by vm.dlcPreview.collectAsStateWithLifecycle()
    val intervalMs by vm.intervalMs.collectAsStateWithLifecycle()
    val isRepeating by vm.isRepeating.collectAsStateWithLifecycle()
    val sendLog by vm.sendLog.collectAsStateWithLifecycle()
    val inputError by vm.inputError.collectAsStateWithLifecycle()
    val usbConnected by vm.usbConnected.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // ── USB disconnected banner ────────────────────────────────────────────
        if (!usbConnected) {
            Text(
                text = "Chưa kết nối USB",
                color = Color(0xFFFF9800),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // ── CAN ID + DLC row ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = canIdInput,
                onValueChange = { vm.canIdInput.value = it.uppercase().take(3) },
                label = { Text("CAN ID (hex)", fontSize = 11.sp) },
                prefix = { Text("0x") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
                isError = inputError != null && canIdInput.isNotBlank(),
                enabled = !isRepeating
            )
            OutlinedTextField(
                value = dlc.toString(),
                onValueChange = {},
                label = { Text("DLC", fontSize = 11.sp) },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.width(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Data bytes field ──────────────────────────────────────────────────
        OutlinedTextField(
            value = dataBytesInput,
            onValueChange = { vm.dataBytesInput.value = it },
            label = { Text("Data Bytes (hex, space-separated)", fontSize = 11.sp) },
            placeholder = { Text("02 01 0C 00 00 00 00 00", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
            isError = inputError != null,
            enabled = !isRepeating
        )

        // ── Validation error ──────────────────────────────────────────────────
        if (inputError != null) {
            Text(
                text = inputError!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Send buttons row ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.sendOnce() },
                enabled = usbConnected && inputError == null && !isRepeating,
                modifier = Modifier.weight(1f)
            ) {
                Text("▶ Gửi 1 lần", fontSize = 13.sp)
            }
            Button(
                onClick = { vm.toggleRepeat() },
                enabled = usbConnected && (isRepeating || inputError == null),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRepeating) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isRepeating) "■ Stop" else "⟳ Repeat", fontSize = 13.sp)
            }
        }

        // ── Interval field (shown when repeating or before starting repeat) ───
        AnimatedVisibility(visible = isRepeating) {
            OutlinedTextField(
                value = intervalMs,
                onValueChange = { vm.intervalMs.value = it.filter { c -> c.isDigit() } },
                label = { Text("Interval (ms)", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // ── Send log header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SEND LOG", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.clearLog() }) {
                Text("Xóa", fontSize = 11.sp)
            }
        }

        // ── Log entries ───────────────────────────────────────────────────────
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sendLog.asReversed(), key = { it.seq }) { entry ->
                CanSendEntryRow(entry = entry)
            }
        }
    }
}

@Composable
private fun CanSendEntryRow(entry: CanSendEntry) {
    val (statusColor, statusIcon, statusText) = when (entry.status) {
        SendStatus.PENDING  -> Triple(Color.Gray, "…", "PENDING")
        SendStatus.ACK      -> Triple(Color(0xFF4CAF50), "✓", "ACK ${entry.roundTripMs}ms")
        SendStatus.ERROR    -> Triple(Color(0xFFF44336), "✗", entry.errorMsg ?: "ERROR")
        SendStatus.TIMEOUT  -> Triple(Color(0xFFFF9800), "⏱", "TIMEOUT")
    }
    val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        .format(Date(entry.timestampMs))
    val hexData = entry.dataBytes.joinToString(" ") { "%02X".format(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        // Sent row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("↑ ", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "0x%03X".format(entry.canId),
                        color = statusColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        statusIcon,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    hexData,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$timeStr · DLC=${entry.dlc}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                statusText,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Response sub-rows (indented)
        entry.responses.forEach { resp ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 2.dp)
                    .background(Color(0xFF1A2A3A), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("↓ ", color = Color(0xFF64B5F6), fontSize = 10.sp)
                Text(
                    "0x%03X".format(resp.canId),
                    color = Color(0xFF64B5F6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    resp.dataBytes.joinToString(" ") { "%02X".format(it) },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF90CAF9),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "[RSP ${resp.receivedAfterMs}ms]",
                    color = Color(0xFF64B5F6),
                    fontSize = 10.sp
                )
            }
        }
    }
}
```

- [ ] **Step 5: Add missing imports for composables used**

Check for any missing imports after paste. Common ones needed:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
```

- [ ] **Step 6: Build the full project**

```bash
./gradlew assembleDebug 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. Fix any import errors reported by the compiler — the pattern is to look at the error line number, check what's missing, and add the import.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/RawMonitorScreen.kt
git commit -m "feat(ui): add Send tab to Raw Monitor with CanSendTab composable"
```

---

## Task 5: Final push

- [ ] **Step 1: Verify full build one more time**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Push to GitHub**

```bash
git push origin main
```
