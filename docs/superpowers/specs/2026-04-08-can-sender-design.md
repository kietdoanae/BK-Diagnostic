# CAN Sender Feature — Design Spec
**Date:** 2026-04-08
**Status:** Approved

---

## Overview

Add a "Send" tab inside the existing Raw CAN Monitor screen. Users can manually compose and send CAN frames to the vehicle bus, with optional repeat/interval sending. A send log displays each sent frame alongside its ACK/ERROR/TIMEOUT status, round-trip delay, and any CAN response frames received within a 2-second window.

The STM32 firmware requires **no changes** — the existing `FRAME_SEND_CAN (0x10)` command and `ACK (0x02)` / `ERROR (0x03)` / `CAN_RX (0x01)` responses are already implemented.

---

## 1. Architecture

**Pattern:** New `CanSenderViewModel` (Hướng 2) — separate from `DiagnosticViewModel` to avoid mixing OBD2 logic with manual send logic.

```
Tab "Send" UI
    └── CanSenderViewModel
            ├── UsbSerialManager (shared, injected)
            │       └── parsedFrames: Flow<ParsedFrame>
            └── sendLog: StateFlow<List<CanSendEntry>>
```

`UsbSerialManager` is already a shared singleton — `CanSenderViewModel` subscribes to its `parsedFrames` flow to intercept ACK, ERROR, and CAN_RX responses.

---

## 2. Data Models

### CanSendEntry
```kotlin
data class CanSendEntry(
    val seq: Int,
    val timestampMs: Long,
    val canId: Int,
    val dataBytes: ByteArray,
    val dlc: Int,                             // auto-calculated from dataBytes.size
    val status: SendStatus,
    val roundTripMs: Long?,                   // null while PENDING or on TIMEOUT
    val errorMsg: String? = null,
    val responses: List<CanResponseEntry> = emptyList()
)

data class CanResponseEntry(
    val canId: Int,
    val dataBytes: ByteArray,
    val receivedAfterMs: Long                 // ms since parent frame was sent
)

enum class SendStatus { PENDING, ACK, ERROR, TIMEOUT }
```

**Send log capacity:** 100 entries. When full, drop the oldest entry (`drop(1)`).

---

## 3. CanSenderViewModel

**File:** `app/src/main/java/com/example/bkdiagnostic/diagnostic/CanSenderViewModel.kt`

### State
```kotlin
val canIdInput      : MutableStateFlow<String>   // raw hex input, e.g. "7DF"
val dataBytesInput  : MutableStateFlow<String>   // e.g. "02 01 0C 00 00 00 00 00"
val dlcPreview      : StateFlow<Int>             // derived: number of valid bytes parsed
val intervalMs      : MutableStateFlow<String>   // repeat interval, default "500"
val isRepeating     : MutableStateFlow<Boolean>
val sendLog         : MutableStateFlow<List<CanSendEntry>>
val inputError      : MutableStateFlow<String?>  // null = valid
val usbConnected    : StateFlow<Boolean>         // mirrors UsbSerialManager.isConnected
```

### Send flow (`sendOnce()`)
1. Parse and validate `canIdInput` and `dataBytesInput` — return early with `inputError` if invalid.
2. Build `CanFrame(id, dlc, data)`.
3. Append `CanSendEntry(status=PENDING)` to `sendLog`.
4. Record `sentAt = System.currentTimeMillis()`.
5. Call `usb.sendFrame(frame)`.
6. Launch `awaitAckWithTimeout(seq, sentAt)` coroutine with 2 s deadline.

### ACK correlation (`init` collector)
```kotlin
usb.parsedFrames.collect { frame ->
    when (frame.type) {
        FRAME_ACK    -> markAck(frame, roundTrip = now - sentAt)
        FRAME_ERROR  -> markError(frame, roundTrip = now - sentAt)
        FRAME_CAN_RX -> attachResponse(frame, receivedAfterMs = now - sentAt)
    }
}
```
- `markAck` / `markError` update the **youngest PENDING entry**.
- `attachResponse` appends a `CanResponseEntry` to the youngest PENDING or most recent ACK entry (within 2 s window).
- After 2 s with no ACK/ERROR, `awaitAckWithTimeout` marks the entry TIMEOUT.

### Repeat sending (`toggleRepeat()`)
```kotlin
fun toggleRepeat() {
    if (isRepeating.value) {
        repeatJob?.cancel()
        isRepeating.value = false
    } else {
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
```
- Minimum interval: **50 ms** (clamped silently).
- `repeatJob` is cancelled in `onCleared()`.

### Factory
```kotlin
class CanSenderViewModelFactory(private val usb: UsbSerialManager) : ViewModelProvider.Factory
```

---

## 4. UI — RawMonitorScreen Changes

**File:** `app/src/main/java/com/example/bkdiagnostic/ui/screens/RawMonitorScreen.kt`

### Tab bar
Add `TabRow` with two tabs at the top of `RawMonitorScreen`:
- **Monitor** — existing frame log (unchanged)
- **Send** — new `CanSendTab` composable

### CanSendTab composable layout (top → bottom)

```
CAN ID field (hex)              DLC preview (read-only)
Data Bytes field (full width, hex space-separated)
inputError text (red, below data field)
─────────────────────────────────────────────────────
[▶ Gửi 1 lần]   [⟳ Repeat / ■ Stop]
Interval: [___] ms   ← visible only when repeat active or about to start
─────────────────────────────────────────────────────
SEND LOG header                              [Xóa]
LazyColumn of CanSendEntry items
```

### CanSendEntry row appearance

| Condition | Icon | Color |
|-----------|------|-------|
| PENDING | … | Gray |
| ACK | ↑ ✓ | Green |
| ERROR | ↑ ✗ | Red |
| TIMEOUT | ↑ ⏱ | Orange |
| RESPONSE sub-row | ↓ [RSP] | Blue (indented 16 dp) |

Each entry shows: CAN ID · data bytes (hex) · status badge · round-trip ms · timestamp.  
Each `CanResponseEntry` sub-row shows: CAN ID · data bytes · `receivedAfterMs`.

### Disabled state
When `usbConnected == false`:
- Both Send buttons are disabled (alpha 0.4).
- Show a `"Chưa kết nối USB"` banner above the form.

---

## 5. Input Validation

Validation runs on every keystroke (derived `StateFlow`).

| Rule | Error |
|------|-------|
| CAN ID empty or non-hex | "CAN ID không hợp lệ (ví dụ: 7DF)" |
| CAN ID > 0x7FF | "CAN ID vượt quá 11-bit (max 7FF)" |
| Any data token not 2 hex chars | "Byte không hợp lệ tại vị trí N" |
| More than 8 bytes | "Tối đa 8 bytes (DLC ≤ 8)" |
| Empty data | Valid — DLC = 0 is legal in CAN |

DLC is auto-calculated as `dataBytes.size` and displayed read-only next to CAN ID.

---

## 6. Protocol (No Firmware Changes Required)

| Direction | Type | Purpose |
|-----------|------|---------|
| Android → STM32 | `0x10 FRAME_SEND_CAN` | Send CAN frame (already implemented) |
| STM32 → Android | `0x02 FRAME_ACK` | Confirms TX queued to MCP2515 |
| STM32 → Android | `0x03 FRAME_ERROR` | TX failed (CAN_SEND_FAIL, TX_TIMEOUT, BAD_FRAME) |
| STM32 → Android | `0x01 FRAME_CAN_RX` | Vehicle ECU response frame |

Round-trip delay = time from `usb.sendFrame()` call to receipt of `FRAME_ACK` or `FRAME_ERROR`.

---

## 7. Files Changed / Created

| File | Change |
|------|--------|
| `diagnostic/CanSenderViewModel.kt` | **New** |
| `ui/screens/RawMonitorScreen.kt` | Add TabRow + CanSendTab composable |
| No STM32 changes | — |
