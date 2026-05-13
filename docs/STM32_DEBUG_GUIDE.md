# STM32 CAN Gateway — Debug Guide

> **Project**: BKDiagnostic
> **Hardware**: STM32F103C8T6 (Blue Pill) + MCP2515 + CP2102 USB-TTL
> **Last updated**: 2026-05-05
> **Firmware version**: v2 (Phase 7 fixes applied)

---

## 1. Tổng quan kiến trúc

```
┌──────────┐  USB-OTG   ┌──────────┐  UART  ┌────────────┐  SPI  ┌──────────┐  CAN  ┌─────────┐
│ Android  │ ◄────────► │ CP2102   │ ◄────► │ STM32F103  │ ◄───► │ MCP2515  │ ◄───► │ Vehicle │
│  Phone   │            │ USB-TTL  │ 460800 │  C8T6      │ 4.5M  │  (8MHz)  │       │ Cluster │
│          │            │  3.3V    │  8N1   │  72MHz     │       │          │       │  + BCM  │
└──────────┘            └──────────┘        └────────────┘       └──────────┘       └─────────┘
```

### Pin map STM32F103C8T6

| Pin   | Function                | Note                          |
|-------|-------------------------|-------------------------------|
| PA4   | SPI1 NSS (CS MCP2515)   | Output PP, no pull            |
| PA5   | SPI1 SCK                | 4.5 MHz                       |
| PA6   | SPI1 MISO               | 5V tolerant                   |
| PA7   | SPI1 MOSI               | —                             |
| PA9   | USART1 TX               | 460800 8N1 → CP2102 RX        |
| PA10  | USART1 RX               | ← CP2102 TX                   |
| PB0   | EXTI0 (MCP2515 INT)     | Input, pull-up, falling edge  |
| PC13  | LED (active-low)        | LED debug heartbeat           |

### Frame protocol (UART)

```
SOF(0xAA) | TYPE | LEN | PAYLOAD[LEN] | CHECKSUM (XOR) | EOF(0x55)
```

| Direction | TYPE | Name | Payload |
|-----------|------|------|---------|
| Android → STM32 | 0x10 | SEND_CAN | `[CAN_ID:4B BE][DLC:1B][DATA:8B]` (13B) |
| Android → STM32 | 0x20 | SET_BAUD | `[BAUD_KBPS:2B BE]` (2B) |
| Android → STM32 | 0x30 | PING | (none) |
| STM32 → Android | 0x01 | CAN_RX | `[CAN_ID:4B BE][DLC:1B][DATA:8B]` (13B) |
| STM32 → Android | 0x02 | ACK | `[acked_type:1B]` |
| STM32 → Android | 0x03 | ERROR | `[error_code:1B]` |
| STM32 → Android | 0x04 | STATUS | `[flags:1B]` |

### Error codes (firmware v2)

| Code | Symbol | Meaning |
|------|--------|---------|
| 0x01 | CAN_SEND_FAIL | TXREQ timeout |
| 0x02 | CAN_BAUD_FAIL | Đặt baud thất bại |
| 0x03 | BAD_FRAME | Checksum/EOF sai |
| 0x04 | UNKNOWN_TYPE | Type không nhận diện |
| 0x10 | TX_QUEUE_OVR | UART TX queue tràn → frame bị drop |
| 0x11 | BAD_LENGTH | Payload length sai cho frame type |
| 0x20 | BUS_WARNING | Error counter > 96 (rising edge) |
| 0x21 | BUS_PASSIVE | Error counter > 127 (rising edge) |
| 0x22 | BUS_OFF | BUS-OFF detected — auto-recovery |
| 0x23 | RX_BUF_OVERFLOW | MCP2515 RX buffer tràn — frame mất |
| 0x24 | BUS_RECOVERED | Phục hồi BUS-OFF thành công |

---

## 2. Phase 0 — Chuẩn bị dụng cụ

### Bắt buộc

- [ ] **STM32 ST-Link V2** + cable SWD (PA13/PA14 + 3V3/GND)
- [ ] **STM32CubeIDE** (hoặc VSCode + Cortex-Debug + arm-none-eabi-gdb)
- [ ] **2× CP2102 USB-TTL adapter** (1 cho production, 1 cho UART sniff)
- [ ] **CAN bus analyzer** (CANable USB ~$20 / PEAK PCAN / Kvaser)
- [ ] **2× 120Ω termination resistor** (đầu cuối CAN bus)
- [ ] **Multimeter** (check 3.3V/5V rails)
- [ ] **Logic analyzer** (Saleae Logic / DSLogic / cheap 8-channel)

### Nên có

- [ ] **Oscilloscope** ≥ 50MHz (đo bit timing chính xác)
- [ ] **2× module MCP2515** (test loopback giữa 2 node)
- [ ] **Bench DC power supply** 12V (cho cluster + BCM)

### Software tools

- [ ] **Termite** hoặc **PuTTY** (UART terminal)
- [ ] **Saleae Logic** (decode SPI/UART/CAN)
- [ ] **CANable + cangaroo / Savvy CAN** (CAN sniffer GUI)
- [ ] **st-info** (`pacman -S stlink-tools` hoặc `brew install stlink`)

---

## 3. Phase 1 — Sanity Checks

**Mục tiêu**: Firmware boot đúng, peripheral phản hồi.
**Thời gian**: ~30 phút
**Status**: ☐ Pending

### 1.1 LED Heartbeat
- [ ] LD1 (PC13) nháy 3 lần slow trong `App_Init()` → confirm boot OK
- [ ] Nếu LED đứng yên → kiểm tra HSE 8MHz crystal Y1 (đo MCO PA8 = 8MHz qua oscilloscope)
- [ ] Nếu LED nháy 2 lần nhanh + pause → MCP2515 init failed → sang 1.4

### 1.2 UART Echo Test
**Bypass parser**:
```c
// Tạm thời trong App_Run() — comment lại sau khi xong
HAL_UART_Receive_IT(&huart1, &echo_byte, 1);
HAL_UART_Transmit(&huart1, &echo_byte, 1, 10);
```
- [ ] Mở Termite @ 460800 8N1, gõ "ABC" → nhận lại "ABC"
- [ ] Sai ký tự → BRR error (kỳ vọng `BRR = 0x9C` cho 460800 @ 72MHz APB2)

### 1.3 SPI Loopback
- [ ] Short MOSI (PA7) ↔ MISO (PA6)
- [ ] Gửi `0x55 0xAA` qua `HAL_SPI_TransmitReceive`, đọc lại đúng
- [ ] Logic analyzer chạm SCK: đo 4.5 MHz (giả định prescaler=16, APB2=72MHz)

### 1.4 MCP2515 CANSTAT Read
```c
// Sau MCP2515_Reset(), trước SetMode:
uint8_t stat = MCP2515_ReadRegister(MCP2515_REG_CANSTAT);
// Kỳ vọng: stat & 0xE0 == 0x80 (CONFIG mode)
```
- [ ] Đọc `0x80` → SPI OK
- [ ] Đọc `0xFF` → SPI dây đứt hoặc CS sai
- [ ] Đọc `0x00` → MCP2515 không reset đúng → check VCC 5V, crystal 8MHz, RES pin

### Checkpoint Phase 1
- [ ] LED nháy ổn định
- [ ] UART echo đúng tất cả ký tự
- [ ] CANSTAT = 0x80 sau Reset

---

## 4. Phase 2 — USB Protocol Integrity

**Mục tiêu**: Verify framing protocol Android ↔ STM32.
**Thời gian**: ~30 phút
**Status**: ☐ Pending

### 2.1 PING Round-Trip
- [ ] Android: `viewModel.ping()` (hoặc gửi raw `AA 30 00 30 55`)
- [ ] STM32 phản hồi `AA 02 01 30 33 55` (ACK with acked_type=0x30)
- [ ] Logic analyzer trên TX/RX UART đo độ trễ < 5ms

### 2.2 Bad Frame Stress
| Test | Frame gửi | Kỳ vọng |
|------|-----------|---------|
| Bad SOF | `BB 30 00 30 55` | STM32 ignore, không crash |
| Bad checksum | `AA 30 00 FF 55` | STM32 → ERROR(0x03 BAD_FRAME), counter `s_bad_rx_frames++` |
| Bad EOF | `AA 30 00 30 AA` | STM32 → ERROR(0x03 BAD_FRAME) |
| LEN=255 đúng | `AA 10 FF [255B] CK 55` | Parser nhận đủ, dispatch sẽ trả ERR_BAD_LENGTH (vì SEND_CAN cần LEN=13) |
| Wrong LEN cho SEND_CAN | `AA 10 05 ... CK 55` | STM32 → ERROR(0x11 BAD_LENGTH) |
| Wrong LEN cho SET_BAUD | `AA 20 03 ... CK 55` | STM32 → ERROR(0x11 BAD_LENGTH) |
| Timeout giữa frame | `AA 30` rồi dừng 5s | STM32 không crash (state vẫn WAIT_LEN, frame mới SOF reset state) |

### 2.3 UART Sniff với CP2102 #2
- [ ] Cắm RX của sniffer #2 vào TX line (PA9 ↔ CP2102 #1 RX)
- [ ] GND chung
- [ ] **KHÔNG** nối VCC giữa 2 CP2102
- [ ] Mở 2 terminal:
  - Terminal 1: Android log (Logcat) — xem byte raw
  - Terminal 2: Termite (CP2102 #2) @ 460800 8N1 RX-only
- [ ] Gửi PING từ Android → bytes phải match 100%

### 2.4 Baud Stress
- [ ] Android flood 1000 PING trong 1 giây = ~5 KB/s
- [ ] STM32 phải reply đủ 1000 ACK
- [ ] Đếm `s_bad_rx_frames` qua debugger — phải = 0

### Checkpoint Phase 2
- [ ] PING ACK ổn định
- [ ] Tất cả bad frame test → đúng error code
- [ ] Sniff bytes match Android log
- [ ] Stress 1000 PING = 0 drop

---

## 5. Phase 3 — MCP2515 Self-Test

**Mục tiêu**: Cô lập firmware khỏi CAN bus thực, verify driver MCP2515.
**Thời gian**: ~2 giờ
**Status**: ☐ Pending

### 3.1 Loopback Mode (internal)
**Code test thêm vào App_Init() tạm thời**:
```c
MCP2515_SetMode(MCP2515_MODE_LOOPBACK);  // 0x40
HAL_Delay(10);

CAN_Frame_t tx = { .id = 0x123, .dlc = 8, .data = {1,2,3,4,5,6,7,8} };
MCP2515_SendFrame(&tx);
HAL_Delay(10);

CAN_Frame_t rx;
if (MCP2515_RxAvailable() && MCP2515_ReceiveFrame(&rx) == MCP2515_OK) {
    // Verify: rx.id == 0x123, rx.dlc == 8, data match
    // Toggle LED to indicate pass
}
```
- [ ] Test ở 125 / 250 / 500 / 1000 kbps — đều phải pass
- [ ] Fail bất kỳ baud nào → CNF1/2/3 sai → tính lại

### 3.2 Đo Baud Rate Thực
**Logic analyzer** chạm chân **TXCAN của MCP2515** (chân 2, output trước transceiver):

| Baud   | Expected 1-bit width |
|--------|----------------------|
| 125 k  | 8.0 µs               |
| 250 k  | 4.0 µs               |
| 500 k  | 2.0 µs               |
| 1 M    | 1.0 µs               |

- [ ] Tolerance: ±0.5%
- [ ] Sai > 0.5% → check crystal Y1 fake, BRP sai

### 3.3 Two-Node Bench Test
**Setup**:
- Node A: STM32 + MCP2515 #1
- Node B: Arduino với MCP_CAN library + MCP2515 #2 (hoặc CANable analyzer)
- Nối: H↔H, L↔L, GND↔GND, **120Ω termination 2 đầu**

**Test 2 chiều**:
- [ ] Node A → Node B: Android command STM32 send `0x123 [01 02 03 04 05 06 07 08]` → Arduino Serial print đúng
- [ ] Node B → Node A: Arduino send `0x456 [AA BB CC DD]` → Android raw monitor thấy frame

### Checkpoint Phase 3
- [ ] Loopback pass ở cả 4 baud rate
- [ ] Bit timing đo logic analyzer trong tolerance
- [ ] Two-node test 2 chiều OK

---

## 6. Phase 4 — Bridge Stress Test

**Mục tiêu**: Verify behavior khi traffic dày.
**Thời gian**: ~2 giờ
**Status**: ☐ Pending

### 4.1 Gauge Streaming Test
- [ ] Android Active Test → mở Gauge Control panel
- [ ] Set RPM = 3000, Speed = 80, nhấn START
- [ ] CANable analyzer đo:
  - 10 frame `0x201` + 10 frame `0x300` mỗi giây = **20 fps**
  - Gap giữa frame ≤ 110 ms
  - Byte order RPM đúng: `[01][2E][E0][00][00][00][00][00]`

### 4.2 TX Queue Overflow Detection
**Inject high traffic to trigger overflow**:
- [ ] CAN analyzer flood STM32 với **2000 frame/s @ 500 kbps** trong 30s
- [ ] STM32 forward về Android → so sánh:
  - Count Android `RawMonitor` vs analyzer log
  - Nếu < → TX queue overflow đã xảy ra
- [ ] Phase 7 fix: **STM32 phải gửi `ERROR(0x10 TX_QUEUE_OVR)` cứ 200ms một lần** trong khi flood
- [ ] Kiểm tra Android log thấy error này

### 4.3 Bidirectional Stress
- [ ] Android: gauge stream chạy liên tục
- [ ] CAN bus: analyzer flood 500 frame/s
- [ ] Đo trong 60s:
  - PING RTT < 50ms → CPU không treo
  - LED heartbeat vẫn nháy (nếu có code heartbeat trong main loop)
  - `s_bad_rx_frames` không tăng

### 4.4 Long-Run Stability (overnight)
- [ ] Để gauge stream chạy 8 tiếng
- [ ] Sáng hôm sau check:
  - Stream vẫn còn active (Android UI cập nhật)
  - Không có error nào trong logcat
  - `s_dropped_frames` cumulative thấp (< 10)

### Checkpoint Phase 4
- [ ] Gauge streaming đúng tần số
- [ ] TX overflow được report đúng
- [ ] 60s stress không treo
- [ ] 8 tiếng overnight stable

---

## 7. Phase 5 — Bus Error Handling

**Mục tiêu**: Verify Phase 7 fixes hoạt động đúng.
**Thời gian**: ~3 giờ
**Status**: ☐ Pending

### 5.1 BUS-OFF Recovery Test
**Tạo điều kiện BUS-OFF**:
- [ ] Để STM32 đang TX (gauge stream đang chạy)
- [ ] **Rút dây CAN_H** đột ngột → MCP2515 không nhận ACK → TEC tăng nhanh → BUS-OFF

**Kỳ vọng**:
- [ ] Trong 200ms, Android nhận `ERROR(0x22 BUS_OFF)`
- [ ] Trong 500ms tiếp theo (sau 100ms recovery delay + reset), nhận `ERROR(0x24 BUS_RECOVERED)`
- [ ] Cắm lại CAN_H → gauge stream tự động hoạt động lại
- [ ] Đọc EFLG qua debugger sau recovery: TXBO=0

### 5.2 Bus Warning / Passive Edges
**Cách tạo error counter cao**:
- [ ] Set MCP2515 baud = 500k nhưng **dây ngắn không termination** → reflection → CRC error
- [ ] Hoặc: chỉnh baud STM32 = 250k, baud cluster = 500k → mọi frame fail ACK

**Kỳ vọng**:
- [ ] TEC tăng dần → khi qua 96 → STM32 gửi `ERROR(0x20 BUS_WARNING)` (1 lần duy nhất, rising edge)
- [ ] Tiếp tục tăng > 127 → `ERROR(0x21 BUS_PASSIVE)` (1 lần)
- [ ] Tiếp tục → 255 → `ERROR(0x22 BUS_OFF)` + auto-recovery

### 5.3 RX Buffer Overflow
**Trigger**:
- [ ] Tạm comment dòng `Protocol_PollCanRx()` trong App_Run() → STM32 không drain RX buffer
- [ ] Flood bus với 10 frame trong 1 giây
- [ ] Sau 200ms, STM32 sẽ phát hiện EFLG.RX0OVR / RX1OVR và gửi `ERROR(0x23 RX_BUF_OVERFLOW)`
- [ ] Verify EFLG bits được clear sau khi report (không spam liên tục)

### 5.4 Length Validation
- [ ] Android gửi malformed SEND_CAN với LEN=5: `AA 10 05 ... 55`
- [ ] STM32 → `ERROR(0x11 BAD_LENGTH)` ngay
- [ ] Android gửi SET_BAUD với LEN=4: `AA 20 04 ... 55`
- [ ] STM32 → `ERROR(0x11 BAD_LENGTH)`

### Checkpoint Phase 5
- [ ] BUS-OFF auto-recovery thành công
- [ ] Warning/Passive rising edges báo đúng (chỉ 1 lần mỗi event)
- [ ] RX overflow detect và clear đúng
- [ ] BAD_LENGTH cho frame sai size

---

## 8. Phase 6 — Real Vehicle Integration

**Mục tiêu**: Verify trên Ford Ranger thực (BCM + Cluster).
**Thời gian**: ~4 giờ
**Status**: ☐ Pending

### 6.1 Bench Setup
- [ ] BCM + Cluster cấp 12V từ ắc quy hoặc bench supply
- [ ] STM32 vào MS-CAN qua OBD-II:
  - Pin 3 (MS-CAN H) ↔ MCP2515 CAN_H
  - Pin 11 (MS-CAN L) ↔ MCP2515 CAN_L
  - Pin 4 (GND) ↔ MCP2515 GND
- [ ] **Termination**: Cluster đã có 1 đầu, thêm 120Ω ở STM32 đầu kia
- [ ] STM32 baud = **125 kbps** (MS-CAN Ford)

### 6.2 Listen-Only Sniff (5 phút)
**Trước khi gửi gì** — sniff bus passive:
```c
MCP2515_SetMode(MCP2515_MODE_LISTEN);  // 0x60
```
- [ ] Android Raw Monitor → log tất cả frame
- [ ] Export CSV sau 5 phút
- [ ] Phân tích:
  - Có ID `0x201` không? Pattern data thay đổi không?
  - Có `0x420`, `0x430`, `0x300` không?
  - BCM heartbeat ID nào (xuất hiện đều mỗi 100-500ms)?

### 6.3 Active Test Warning Icons
- [ ] Vào Active Test trong app
- [ ] Nhấn icon `high_beam` (CAN ID `0x7A0`, UDS DID 0xD021)
- [ ] Verify đèn pha trên cluster sáng 2 giây rồi tắt
- [ ] Sniff bus: thấy frame Android gửi VÀ phản hồi BCM `0x7A8`
- [ ] Nếu BCM trả Negative Response (data[1]=0x7F): xem byte 3 (NRC):
  - 0x11 ServiceNotSupported
  - 0x22 ConditionsNotCorrect (cần Diagnostic Session 0x10 0x03 trước)
  - 0x31 RequestOutOfRange (DID sai)

### 6.4 Gauge Streaming
- [ ] Set RPM = 3000, START
- [ ] Kim đồng hồ phải lên đúng 3000 và **giữ nguyên**
- [ ] Kim nhảy 1 cái rồi về 0 → interval > 500ms hoặc CAN ID sai
- [ ] Kim không nhúc nhích → CAN ID sai → thử `0x420`, hoặc brute force range trong file JSON

### 6.5 Long-Run on Vehicle
- [ ] Để gauge stream chạy 30 phút
- [ ] Verify:
  - Cluster không reset
  - Không có error frame
  - BCM không vào limp mode

### Checkpoint Phase 6
- [ ] Sniff thấy traffic bus thực
- [ ] Active Test ít nhất 1 icon hoạt động
- [ ] Gauge streaming kim ổn định 30 phút

---

## 9. Phase 7 — Firmware Improvements ✅ DONE

**Status**: ✅ Code đã được áp dụng trong v2 firmware

### Đã implement
- [x] **TX queue overflow notification** — `comm_layer.c` track `s_dropped_frames`, report mỗi 200ms
- [x] **Length validation** — `protocol_layer.c` check `frame->len` exact match cho mỗi frame type
- [x] **EFLG polling** — `Protocol_PeriodicHealthCheck()` đọc EFLG/TEC/REC mỗi 200ms
- [x] **BUS-OFF auto-recovery** — `recover_from_bus_off()` reset MCP2515, reload baud, return NORMAL
- [x] **Rising-edge detection** — chỉ báo BUS_WARNING/PASSIVE 1 lần mỗi event
- [x] **RX buffer overflow detect+clear** — RX0OVR/RX1OVR bits

### Files đã modify
- `stm32/Core/Inc/comm_layer.h` — error codes mới + diagnostic counters
- `stm32/Core/Src/comm_layer.c` — counters + accessors
- `stm32/Core/Inc/mcp2515.h` — EFLG bit defines + new APIs
- `stm32/Core/Src/mcp2515.c` — `MCP2515_ReadEflg/Tec/Rec`, `ClearEflgBits`
- `stm32/Core/Inc/protocol_layer.h` — `Protocol_PeriodicHealthCheck()` declaration
- `stm32/Core/Src/protocol_layer.c` — health check + recovery logic
- `stm32/Core/Src/main_app.c` — call `Protocol_PeriodicHealthCheck()` mỗi loop
- `app/.../FrameProtocol.kt` — `ErrorCode` object + `describe()/isCritical()`

### Còn lại (optional, để v3)
- [ ] **Ring buffer cho CAN RX** — buffer 32 frame, drain khi UART TX queue có chỗ
- [ ] **Detailed stats request** — Android có thể request `FRAME_STATS` để nhận `CommDiagCounters_t`
- [ ] **CAN ID filter** — config từ Android để giảm traffic UART
- [ ] **Native USB CDC** — chuyển sang PA11/PA12 cho throughput 1MB/s
- [ ] **Watchdog** — IWDG với timeout 5s, reset chip nếu treo

---

## 10. Quick Reference

### Watch variables (debugger)

```
COMM LAYER:
  s_rx_state         // RX_WAIT_SOF=0 / TYPE=1 / LEN=2 / PAYLOAD=3 / CK=4 / EOF=5
  s_rx_idx           // 0..s_rx_frame.len-1
  s_tx_head, s_tx_tail  // 0..7 (queue pointers)
  s_tx_busy          // 0/1
  s_dropped_frames   // cumulative count
  s_bad_rx_frames    // cumulative count
  s_pending_error    // 0xFF = none, else ERR_xxx

PROTOCOL LAYER:
  s_current_baud_kbps  // 125/250/500/1000
  s_last_eflg          // last seen EFLG bits
  s_last_health_tick   // ms tick of last health check

MCP2515 (read on demand via debugger):
  CANSTAT  (0x0E)  // mode bits 7:5
  CANCTRL  (0x0F)  // operation control
  CANINTF  (0x2C)  // interrupt flags
  EFLG     (0x2D)  // error flags ← KEY for Phase 5
  TEC      (0x1C)  // transmit error counter
  REC      (0x1D)  // receive error counter
  TXB0CTRL (0x30)  // bit 3 = TXREQ
```

### SPI command quick decode (logic analyzer)

| First byte | Meaning |
|------------|---------|
| `0xC0` | Reset |
| `0x03 <addr>` | Read register |
| `0x02 <addr> <data>` | Write register |
| `0x05 <addr> <mask> <data>` | Bit modify |
| `0x90` | Read RX0 burst |
| `0x94` | Read RX1 burst |
| `0x40` | Load TX0 burst |
| `0x81` | RTS TX0 |
| `0xA0` | Read status |

### Calculation: UART throughput

```
460800 baud / 10 (8N1 framing) = 46080 byte/s
1 CAN_RX frame UART encoded = 17 byte (SOF+TYPE+LEN+13B payload+CK+EOF)
Max forward rate = 46080 / 17 ≈ 2710 frame/s
```

CAN bus 125 kbps full-load ≈ 1000 frame/s 8B → **không bottleneck**.
CAN bus 500 kbps full-load ≈ 4000 frame/s 8B → **UART là bottleneck**.

### Timing budget (main loop)

| Operation | Time |
|-----------|------|
| `Comm_ProcessPendingError()` | < 50 µs |
| `Protocol_DispatchFrame()` SEND_CAN | ~200 µs (SPI burst) |
| `Protocol_PollCanRx()` 1 frame | ~150 µs |
| `Protocol_PeriodicHealthCheck()` (every 200ms) | ~100 µs |
| **Worst-case loop** | < 1 ms |

→ Loop frequency > 1 kHz, đủ cho gauge streaming 100ms interval.

---

## 11. Troubleshooting Cheatsheet

| Triệu chứng | Nguyên nhân khả nghi | Phase |
|-------------|---------------------|-------|
| LED không nháy | Clock chưa lock / firmware corrupt | 1.1 |
| UART echo sai ký tự | BRR error / clock sai | 1.2 |
| CANSTAT = 0xFF | SPI dây đứt / CS sai | 1.4 |
| MCP2515 stuck CONFIG | Crystal 8MHz fake / no termination | 3.2 |
| Loopback fail @ 125k OK 500k fail | CNF sai cho 500k | 3.1 |
| Frame mất khi flood | TX queue overflow | 4.2 |
| Kim nhảy rồi về 0 | Stream interval > 500ms | 6.4 |
| Kim đứng yên | CAN ID sai | 6.4 |
| BCM Negative Response 0x22 | Cần Diagnostic Session trước | 6.3 |
| Gateway "câm" sau 1 lúc | BUS-OFF chưa được handle | 5.1 (đã fix v2) |
| Random ERROR(0x03) | Noise UART / cable dài | 2.3 |

---

## 12. Test log template

Copy + paste vào notebook để track tiến độ:

```
=== Session: ____________________ Date: __________
Hardware: BluePill v__, MCP2515 v__, CP2102 v__

[Phase 1.1] LED heartbeat:        ☐ Pass  ☐ Fail  Note: __
[Phase 1.2] UART echo:            ☐ Pass  ☐ Fail  Note: __
[Phase 1.3] SPI loopback:         ☐ Pass  ☐ Fail  Note: __
[Phase 1.4] CANSTAT read:         ☐ Pass  ☐ Fail  Note: __

[Phase 2.1] PING round-trip:      ☐ Pass  ☐ Fail  RTT: __ ms
[Phase 2.2] Bad frame stress:     ☐ Pass  ☐ Fail
[Phase 2.3] UART sniff match:     ☐ Pass  ☐ Fail
[Phase 2.4] 1000 PING flood:      ☐ Pass  ☐ Fail  Drops: __

[Phase 3.1] Loopback 125k:        ☐ Pass  ☐ Fail
[Phase 3.1] Loopback 250k:        ☐ Pass  ☐ Fail
[Phase 3.1] Loopback 500k:        ☐ Pass  ☐ Fail
[Phase 3.1] Loopback 1M:          ☐ Pass  ☐ Fail
[Phase 3.2] Bit timing 500k:      ☐ Pass  ☐ Fail  Measured: __ µs
[Phase 3.3] Two-node A→B:         ☐ Pass  ☐ Fail
[Phase 3.3] Two-node B→A:         ☐ Pass  ☐ Fail

[Phase 4.1] Gauge stream 20fps:   ☐ Pass  ☐ Fail
[Phase 4.2] TX overflow report:   ☐ Pass  ☐ Fail
[Phase 4.3] 60s bidir stress:     ☐ Pass  ☐ Fail
[Phase 4.4] 8h overnight:         ☐ Pass  ☐ Fail  Drops: __

[Phase 5.1] BUS-OFF + recovery:   ☐ Pass  ☐ Fail  Recovery time: __ ms
[Phase 5.2] Warning rising edge:  ☐ Pass  ☐ Fail
[Phase 5.3] RX buf overflow:      ☐ Pass  ☐ Fail
[Phase 5.4] BAD_LENGTH:           ☐ Pass  ☐ Fail

[Phase 6.1] Bench 12V on:         ☐ Pass  ☐ Fail
[Phase 6.2] Listen-only sniff:    ☐ Pass  ☐ Fail  IDs found: ____
[Phase 6.3] Active Test high_beam: ☐ Pass  ☐ Fail
[Phase 6.4] Gauge RPM=3000:       ☐ Pass  ☐ Fail
[Phase 6.5] 30min stable:         ☐ Pass  ☐ Fail

OVERALL: ☐ READY FOR PRODUCTION   ☐ NEEDS FIXES: ____________
```

---

## 13. Tham khảo

- **MCP2515 Datasheet** — Microchip DS21801 (bit timing calculator)
- **STM32F103 Reference Manual** — RM0008 (USART, SPI, EXTI)
- **STM32F103C8T6 Datasheet** — DS5319 (pin map, electrical)
- **CAN Bus Protocol** — ISO 11898-1 (BUS-OFF, error states)
- **OBD-II Standard** — SAE J1979 (mode 01/03/04/22)
- **Ford UDS** — ISO 14229-1 (service 0x2F InputOutputControlByIdentifier)

---

*Document version: 1.0*
*Maintained by: BKDiagnostic team — kiet.doanae@gmail.com*
