# Ford Ranger 2017-2018 — CAN Bus Tachometer (RPM) Research

## 1. Tổng quan kiến trúc CAN trên Ford Ranger PX2 (2015-2018)

Ford Ranger 2017-2018 (T6 / PX MkII) sử dụng kiến trúc CAN đa bus:

| Bus          | Tốc độ    | Mục đích                                      |
|--------------|-----------|-----------------------------------------------|
| **HS-CAN**   | 500 kbps  | Powertrain: ECU, ABS, Transmission, EPS       |
| **MS-CAN**   | 125 kbps  | Body: BCM, Instrument Cluster, Climate, Radio  |

### Gateway Module (GWM)
- Module trung gian chuyển tiếp thông điệp giữa HS-CAN và MS-CAN.
- **Instrument Cluster** (bảng đồng hồ táp lô) kết nối trên **MS-CAN** (125 kbps).
- ECU phát thông điệp RPM trên **HS-CAN** (500 kbps).
- GWM chuyển tiếp thông điệp RPM từ HS-CAN sang MS-CAN để Instrument Cluster hiển thị.

> **Kết luận**: Để khiển kim đồng hồ tốc độ động cơ (tachometer), ta cần gửi CAN frame
> trên **MS-CAN (125 kbps)** với CAN ID tương ứng cho RPM.

---

## 2. Thông điệp CAN cho Engine RPM

### 2.1. CAN ID chính: `0x201` (Hex) — Engine Data (HS-CAN)

| Field         | Giá trị                        |
|---------------|--------------------------------|
| **CAN ID**    | `0x201` (513 decimal)          |
| **Bus**       | HS-CAN (500 kbps)             |
| **DLC**       | 8 bytes                        |
| **Chu kỳ**    | 10-20 ms (được phát liên tục)  |

#### Cấu trúc Data Bytes:

```
Byte 0: Status flags / Engine run status
Byte 1: RPM High Byte
Byte 2: RPM Low Byte
Byte 3: Engine load / Throttle position
Byte 4-7: Các thông số khác (nhiệt độ, áp suất, etc.)
```

#### Công thức tính RPM:

```
RPM = ((Byte[1] << 8) | Byte[2]) / 4
```

**Ngược lại**, để mã hóa một giá trị RPM:

```
Raw_Value = RPM × 4
Byte[1] = (Raw_Value >> 8) & 0xFF   (High Byte)
Byte[2] = Raw_Value & 0xFF          (Low Byte)
```

### 2.2. CAN ID trên MS-CAN: `0x201` hoặc `0x420`

Khi GWM chuyển tiếp từ HS-CAN sang MS-CAN, CAN ID có thể được giữ nguyên (`0x201`)
hoặc được ánh xạ sang một ID khác. Dựa trên nghiên cứu từ cộng đồng reverse engineering Ford:

- **`0x201` trên MS-CAN (125 kbps)**: Giữ nguyên ID, được instrument cluster nhận diện.
- **`0x420` trên MS-CAN (125 kbps)**: Một số variant Ford sử dụng ID này cho engine data trên MS-CAN.

> **Khuyến nghị**: Thử cả hai CAN ID (`0x201` và `0x420`) trên MS-CAN 125 kbps.
> Nếu instrument cluster không phản hồi, quét toàn bộ bus để tìm ID chính xác.

---

## 3. Ví dụ mã hóa RPM = 3000 vòng/phút

### Tính toán:

```
RPM = 3000
Raw_Value = 3000 × 4 = 12000 = 0x2EE0

Byte[1] (High) = 0x2E (46 decimal)
Byte[2] (Low)  = 0xE0 (224 decimal)
```

### CAN Frame đầy đủ:

```
CAN ID:  0x201
DLC:     8
Data:    [XX] [2E] [E0] [00] [00] [00] [00] [00]
          │    │    │
          │    │    └─ RPM Low Byte (0xE0)
          │    └─────── RPM High Byte (0x2E)
          └──────────── Status byte (0x00 = engine running, hoặc giá trị phù hợp)
```

### Bảng tra nhanh các RPM thường dùng:

| RPM    | Raw (×4)  | Byte[1] | Byte[2] | Hex         |
|--------|-----------|---------|---------|-------------|
| 800    | 3200      | 0x0C    | 0x80    | `0C 80`     |
| 1000   | 4000      | 0x0F    | 0xA0    | `0F A0`     |
| 1500   | 6000      | 0x17    | 0x70    | `17 70`     |
| 2000   | 8000      | 0x1F    | 0x40    | `1F 40`     |
| 2500   | 10000     | 0x27    | 0x10    | `27 10`     |
| **3000** | **12000** | **0x2E** | **0xE0** | **`2E E0`** |
| 3500   | 14000     | 0x36    | 0xB0    | `36 B0`     |
| 4000   | 16000     | 0x3E    | 0x80    | `3E 80`     |
| 5000   | 20000     | 0x4E    | 0x20    | `4E 20`     |
| 6000   | 24000     | 0x5D    | 0xC0    | `5D C0`     |

---

## 4. Lưu ý quan trọng khi gửi CAN frame giả lập

### 4.1. Chu kỳ gửi (Message Period)
- ECU gốc phát thông điệp `0x201` mỗi **10-20 ms**.
- Instrument cluster có thể có **timeout watchdog** (thường 500ms - 2s).
- **Phải gửi liên tục** với chu kỳ ≤ 500ms để kim không reset về 0.
- Khuyến nghị: gửi mỗi **50-100 ms**.

### 4.2. Bus Speed
- Instrument Cluster trên Ford Ranger PX2 nhận trên **MS-CAN (125 kbps)**.
- Nếu gửi trên HS-CAN (500 kbps), cần đảm bảo GWM hoạt động để chuyển tiếp.
- **Cách tiếp cận trực tiếp**: Gửi trên MS-CAN 125 kbps → Instrument Cluster.

### 4.3. Các thông điệp phụ trợ
Để instrument cluster không hiển thị lỗi hoặc bỏ qua dữ liệu, có thể cần gửi thêm:

| CAN ID   | Mô tả                          | Bus     | Tần suất  |
|----------|--------------------------------|---------|-----------|
| `0x201`  | Engine RPM data                | MS-CAN  | 50-100 ms |
| `0x420`  | Engine data (alt)              | MS-CAN  | 100 ms    |
| `0x430`  | Engine coolant temperature     | MS-CAN  | 1000 ms   |
| `0x300`  | Vehicle speed                  | MS-CAN  | 50-100 ms |

### 4.4. Heartbeat / Keep-alive
- Instrument cluster có thể yêu cầu một số message ID nhất định phải xuất hiện liên tục.
- Nếu chỉ gửi `0x201`, cluster có thể chuyển sang chế độ "limp mode" hoặc hiển thị lỗi.
- Nên gửi kèm ít nhất **vehicle speed (`0x300`)** và **coolant temp (`0x430`)**.

---

## 5. Phương pháp tìm CAN ID chính xác (nếu khác dự kiến)

Nếu các CAN ID trên không hoạt động, sử dụng phương pháp sniffing:

### 5.1. Quét MS-CAN (125 kbps)
1. Kết nối MCP2515 vào MS-CAN bus (lưu ý: cần tách dây MS-CAN Hi/Lo).
2. Chạy ở chế độ **listen-only** (không gửi ACK, không phá bus).
3. Ghi lại tất cả CAN ID xuất hiện.
4. Khi khởi động xe, tìm CAN ID có data bytes thay đổi theo RPM.

### 5.2. Brute-force CAN ID
1. Duyệt tất cả CAN ID từ `0x000` đến `0x7FF` trên MS-CAN.
2. Gửi frame với DLC=8, data bytes chứa RPM = 3000.
3. Quan sát kim đồng hồ có di chuyển hay không.
4. Gửi mỗi ID với interval 100ms, chờ 2 giây trước khi chuyển ID tiếp theo.

### 5.3. Kiểm tra Ford-specific CAN database
- Ford sử dụng định dạng **SAE J1939** cho một số message (đặc biệt trên diesel).
- Ford Ranger 2017-2018 diesel (3.2L TDCi) có thể dùng J1939 PGN cho RPM.
- **PGN 61444 (0xF004)** — Electronic Engine Controller: RPM tại Byte 3-4, resolution 0.125 rpm/bit.

---

## 6. Wiring & Physical Connection

### 6.1. OBD-II Port Pinout

```
OBD-II Connector (J1962 Female, viewed from front):
┌─────────────────────────────────┐
│  1   2   3   4   5   6   7   8  │
│  9  10  11  12  13  14  15  16  │
└─────────────────────────────────┘

Pin 6:  HS-CAN High (500 kbps)
Pin 14: HS-CAN Low  (500 kbps)
Pin 3:  MS-CAN High (125 kbps) — Ford proprietary
Pin 11: MS-CAN Low  (125 kbps) — Ford proprietary
Pin 16: Battery +12V
Pin 4:  Chassis Ground
Pin 5:  Signal Ground
```

### 6.2. Kết nối MCP2515 với OBD-II

**Nếu gửi trên MS-CAN (125 kbps) — Khuyến nghị:**
```
MCP2515 CAN_H ──→ OBD-II Pin 3  (MS-CAN High)
MCP2515 CAN_L ──→ OBD-II Pin 11 (MS-CAN Low)
MCP2515 GND   ──→ OBD-II Pin 4  (Chassis Ground)
```

**Nếu gửi trên HS-CAN (500 kbps):**
```
MCP2515 CAN_H ──→ OBD-II Pin 6  (HS-CAN High)
MCP2515 CAN_L ──→ OBD-II Pin 14 (HS-CAN Low)
MCP2515 GND   ──→ OBD-II Pin 4  (Chassis Ground)
```

### 6.3. ⚠️ Cảnh báo an toàn
- **Không gửi trên HS-CAN khi xe đang chạy** — có thể can thiệp vào ABS, EPS, hộp số.
- **Luôn ưu tiên MS-CAN** cho việc giả lập tín hiệu táp lô.
- Khi test, nên để xe ở trạng thái **khởi động nhưng không chạy** (key ON, engine OFF hoặc idle).
- Sử dụng **120Ω termination resistor** trên MCP2515 nếu bus yêu cầu.

---

## 7. Tóm tắt: Gửi RPM = 3000 trên Ford Ranger 2017-2018

### Cách nhanh nhất:

```
Bus:       MS-CAN (125 kbps)
CAN ID:    0x201
DLC:       8
Data:      00 2E E0 00 00 00 00 00
Interval:  100 ms (gửi liên tục)
```

### Nếu không hoạt động, thử:
1. Đổi CAN ID thành `0x420` trên MS-CAN 125 kbps.
2. Thử trên HS-CAN 500 kbps với cùng data.
3. Gửi thêm heartbeat messages (vehicle speed, coolant temp).
4. Quét/sniff bus để tìm ID chính xác.

---

## 8. Tham khảo

- Ford Ranger PX2 CAN Bus Database (community reverse engineering)
- SAE J1939 Standard — PGN 61444 Engine RPM
- OBD-II PID `0x0C` — Engine RPM (nếu dùng OBD-II thay vì raw CAN)
- MCP2515 Datasheet — Microchip Technology
- Ford MS-CAN / HS-CAN Architecture Documentation

---

*Tài liệu này được tạo cho dự án BKDiagnostic — STM32 + MCP2515 CAN Gateway.*
*Cập nhật lần cuối: 2026-05-04*