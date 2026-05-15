# Lab Content Design — TR4021 Automotive Maintenance & Diagnostic Techniques

> **Date**: 2026-05-15
> **Author**: BKDiagnostic team
> **Status**: Draft for review
> **Course**: TR4021 — Kỹ thuật Chẩn đoán và Bảo dưỡng Ô tô (3 credits, 30 lab hours)
> **Replaces seed**: `sql/seed/lab-seed.sql` (LAB-01 v1, LAB-02 v1) → 6 labs v2

---

## 1. Scope & Goals

Thiết kế bộ **6 bài thí nghiệm** cho học phần TR4021, sử dụng nền tảng BKDiagnostic (Android + Web + STM32 CAN gateway) và bench thí nghiệm gồm:
- Cluster Ford Ranger T6 (đồng hồ táp lô tách riêng)
- BCM Ford Ranger (Body Control Module)
- STM32F103C8T6 + MCP2515 (CAN gateway)
- Android phone với app BKDiagnostic
- Web platform (giảng viên + sinh viên)

### Mapping với TR4021 Learning Outcomes

| TR4021 LO | Mô tả | Lab phủ |
|-----------|-------|---------|
| L.O.1 | Vẽ quy trình chẩn đoán + bảo dưỡng | LAB-03, 06 |
| L.O.2 | Thực hiện chẩn đoán + bảo dưỡng cơ bản | LAB-01, 02, 03, 04, 06 |
| L.O.3 | Hợp tác nhóm | Mọi lab (group submission) |
| L.O.4 | Thiết kế báo cáo | Mọi lab (PDF report) |
| L.O.5 | Tuân thủ an toàn | LAB-05, 06 |
| L.O.6 | Sử dụng thiết bị chuyên dụng đúng kỹ thuật | Mọi lab |

### Mapping với TR4021 Course Content (Chapters)

| Chapter | Topic | Lab phủ |
|---------|-------|---------|
| Ch.3 | Lý thuyết chẩn đoán — thông số đặc trưng, tiêu chuẩn | LAB-01, 03, 05 |
| Ch.4.5 | Thiết bị chẩn đoán thông dụng | LAB-03, 06 |
| Ch.5 | Bảo dưỡng kỹ thuật (gián tiếp qua diagnostic-first approach) | LAB-04, 06 |
| Ch.6 | An toàn xưởng — 3S/5S | LAB-05, 06 |

### Time budget

- **30 tiết lab** TR4021 chia thành **6 buổi × 5 tiết** (≈ 4h thực hành / buổi)
- Mỗi buổi cấu trúc 5 phase: Pre-quiz (15') → Theory recap (20') → Hands-on (2.5h) → Post-quiz (30') → Report (30')

---

## 2. Equipment & Setup

### 2.1 Bench setup duy nhất (chia sẻ giữa nhóm)

```
        ┌────────────────────────────────────────────┐
        │            Bench thí nghiệm                │
        │                                            │
        │   ┌──────────┐         ┌──────────┐        │
        │   │ Cluster  │◄───CAN─►│   BCM    │        │
        │   │  Ford    │  MS-CAN │  Ford    │        │
        │   │  Ranger  │ 125kbps │  Ranger  │        │
        │   └────┬─────┘         └────┬─────┘        │
        │        │                    │              │
        │        └──────────┬─────────┘              │
        │                   │                        │
        │             ┌─────┴──────┐                 │
        │             │  MCP2515   │                 │
        │             │  +120Ω term│                 │
        │             └─────┬──────┘                 │
        │                   │ SPI                    │
        │             ┌─────┴──────┐                 │
        │             │ STM32F103  │                 │
        │             │   C8T6     │                 │
        │             └─────┬──────┘                 │
        │                   │ UART 460800            │
        │             ┌─────┴──────┐                 │
        │             │  CP2102    │                 │
        │             │  USB-TTL   │                 │
        │             └─────┬──────┘                 │
        │                   │ USB-OTG                │
        └───────────────────┼────────────────────────┘
                            │
                     ┌──────┴──────┐
                     │  Android    │
                     │  phone      │
                     │  BKDiag app │
                     └─────────────┘
                            │ WiFi
                            ▼
                     ┌─────────────┐
                     │  Supabase   │
                     │  + Vercel   │
                     └─────────────┘
```

### 2.2 Group rotation logic (1 bench, 6-8 SV/nhóm)

- Mỗi lớp ~30-40 SV → chia 4-6 nhóm
- 1 bench → các nhóm xoay tour mỗi buổi
- Trong nhóm: **leader chính** giữ Android phone + chịu trách nhiệm session code
- Các thành viên khác:
  - 1 SV ghi chú timestamp + observation
  - 1 SV chụp screenshot / record video làm evidence bổ sung
  - 2-4 SV còn lại quan sát + thảo luận, đề xuất hành động tiếp theo

### 2.3 Safety regulations (TR4021 Ch.6, L.O.5)

Mọi lab có Pre-quiz **bắt buộc** câu hỏi an toàn:
- ESD precautions (tay tiếp đất trước khi chạm board STM32)
- Không cấp nguồn ngược cực BCM (12V chỉ vào đúng pin VBat)
- Không hot-plug USB khi đang stream gauge
- Không kéo CAN_H/CAN_L khi BCM đang lock cửa (cluster có thể vào limp mode)

---

## 3. Common framework

### 3.1 Cấu trúc 5 phase mỗi lab

```
┌─────────────────────────────────────────────────────────┐
│  Phase 1: PRE-LAB QUIZ           15-20 min              │
│  • 5-8 questions (MC + free_text)                       │
│  • Threshold 70%                                        │
│  • Max 3 attempts, app gate                             │
│  • State: NOT_ASSIGNED → PRE_LAB_PENDING → PRE_LAB_PASSED│
├─────────────────────────────────────────────────────────┤
│  Phase 2: THEORY RECAP           20 min                 │
│  • Instructor demo bench setup                          │
│  • Nhắc kiến thức trọng tâm                              │
│  • Q&A walk-through                                     │
├─────────────────────────────────────────────────────────┤
│  Phase 3: HANDS-ON PRACTICE      2.5h                   │
│  • 6-8 steps tuần tự                                    │
│  • Mỗi step có evidence requirement                     │
│  • Leader nhập 6-digit session code → Lab Mode active   │
│  • Evidence tự động upload qua UnifiedRawFrameStore     │
│  • State: PRE_LAB_PASSED → PRACTICE_ACTIVE              │
├─────────────────────────────────────────────────────────┤
│  Phase 4: POST-LAB Q&A           30 min                 │
│  • 3-5 reflection questions (free_text + image_upload)  │
│  • Auto-save mỗi 5s                                     │
│  • Submit final → State: PRACTICE_DONE_POST_PENDING     │
├─────────────────────────────────────────────────────────┤
│  Phase 5: PDF REPORT             30 min (có thể ở nhà)  │
│  • Auto-generate từ template                            │
│  • 7 sections: cover, objectives, pre-quiz, practice,   │
│    post-lab, declaration, footer                        │
│  • SHA-256 hash + upload Supabase                       │
│  • State: PRACTICE_DONE_POST_PENDING → COMPLETED        │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Evidence types

| Type | Mô tả | Required count typical |
|------|-------|------------------------|
| `raw_frame` | CAN frames captured (TX + RX) trong khoảng thời gian step active | 50-200 frames |
| `active_test` | UDS command sent kèm cluster response | 1-3 per step |
| `screenshot` | SV upload ảnh chụp hiện trạng (đèn cluster bật, kim chỉ đúng giá trị, error message) | 1-2 per step |
| `none` | Step thuần lý thuyết — không cần evidence | 0 |

### 3.3 Rubric chuẩn (mỗi lab)

| Criteria | Trọng số | Cách chấm |
|----------|---------|-----------|
| Pre-quiz score | 20% | Auto từ DB |
| Practice completeness | 40% | Đủ N evidence cho mọi step → 100%, thiếu → trừ tỉ lệ |
| Post-quiz quality | 25% | Manual grade by instructor |
| Report format & content | 15% | Manual grade theo template |

Tổng điểm 100, **threshold pass = 50** (theo quy chế HCMUT).

---

## 4. Per-lab specifications

### LAB-01 — Nền tảng CAN Bus & Sniffing

**Code**: `LAB-01`
**Title (VN)**: Nền tảng CAN Bus & Sniffing Traffic
**Title (EN)**: CAN Bus Foundations & Bus Sniffing
**Duration**: 5 tiết (4h)
**Difficulty**: Foundation
**LO mapping**: L.O.2, L.O.6, Ch.3
**Status**: Improved from LAB-01 v1

#### 4.1.1 Objectives

Sau khi hoàn thành lab này, sinh viên có thể:
1. Giải thích cấu trúc của 1 CAN frame (SOF, ID, DLC, data, CRC, ACK, EOF)
2. Phân biệt MS-CAN (125kbps) vs HS-CAN (500kbps) trên xe Ford
3. Sử dụng app BKDiagnostic + STM32 gateway để sniff bus passive
4. Sử dụng filter TX/RX trong Raw Monitor mới
5. Xuất file CSV với cột DIRECTION + SOURCE và phân tích traffic patterns

#### 4.1.2 Pre-quiz (5 câu, threshold 70%)

| # | Type | Question | Options | Correct | Points |
|---|------|----------|---------|---------|--------|
| 1 | MC | CAN frame chuẩn 11-bit có tối đa bao nhiêu CAN ID unique? | A) 256 / B) 1024 / C) 2048 / D) 4096 | C (2^11 = 2048) | 1 |
| 2 | MC | Trên Ford Ranger PX2, Instrument Cluster nằm trên bus nào? | A) HS-CAN 500k / B) MS-CAN 125k / C) LIN bus / D) FlexRay | B | 1 |
| 3 | MC | Termination resistor 120Ω cần đặt ở đâu? | A) 1 đầu bus / B) Cả 2 đầu bus / C) Giữa bus / D) Không cần | B | 1 |
| 4 | MC | DLC = 5 nghĩa là gì? | A) 5 bit data / B) 5 byte data / C) 5 frame liên tiếp / D) Frame ID = 5 | B | 1 |
| 5 | free_text | Trong cơ chế arbitration của CAN, frame có CAN ID **thấp hơn** thắng hay thua? Giải thích 1-2 câu. | (free) | "thắng — vì bit 0 dominant, ID thấp hơn có nhiều bit 0 ở vị trí cao hơn nên thắng arbitration" | 2 |

**Pass condition**: ≥ 70% → 4.2/6 điểm

#### 4.1.3 Hands-on steps (8 steps)

**Step 1** — Setup bench + safety check (15 min, evidence: `none`)
- SV kiểm tra: nguồn 12V đúng cực BCM, USB cable chắc chắn, termination 120Ω 2 đầu
- Ghi checklist trước khi cấp nguồn
- Submit: checkbox "Safety check passed"

**Step 2** — Verify USB & STM32 boot (10 min, evidence: `screenshot`, required=1)
- Cắm Android vào CP2102 → mở app → Diagnostic Hub
- Quan sát status bar: phải hiện "ONLINE" màu xanh
- LED trên STM32 nháy 3× chậm (3 nháy dài) = MCP2515 init OK
- Submit: screenshot Diagnostic Hub với status ONLINE

**Step 3** — Configure bus speed & enter Raw Monitor (10 min, evidence: `screenshot`, required=1)
- Settings → CAN Bus Speed → chọn **125 kbps** (MS-CAN)
- Quay lại Diagnostic → Raw Frame Monitor
- App tự gửi FRAME_SET_BAUD → STM32 reconfigure MCP2515
- Submit: screenshot Raw Monitor đang ở state "RESUME" + connection ONLINE

**Step 4** — Passive sniff 2 phút (15 min, evidence: `raw_frame`, required=200)
- Nhấn RESUME → bắt đầu capture
- Đợi 2 phút (instructor có thể bật/tắt đèn pha BCM giả lập sự kiện)
- Nhấn STOP
- Filter: chọn "**▼ Response**" (chỉ RX) — vì lab này quan sát traffic, không gửi gì
- Submit: app tự upload ≥ 200 raw frames

**Step 5** — Identify periodic broadcast IDs (20 min, evidence: `screenshot`, required=1)
- Trong Raw Monitor đã pause: tìm các CAN ID xuất hiện đều đặn
- Tính period (DELAY column) cho ít nhất 3 ID
- Ghi vào bảng: ID — period — chức năng đoán (vd: 0x430 = engine speed, period 10ms?)
- Submit: screenshot bảng + Raw Monitor với 3 ID đã được khoanh

**Step 6** — Compare cluster vs BCM sources (15 min, evidence: `raw_frame`, required=100)
- Instructor disconnect dây CAN_H đến BCM trong 30 giây
- SV quan sát: ID nào biến mất → đó là từ BCM
- Sau khi reconnect, capture 30s nữa
- Filter "Both" để thấy đầy đủ
- Submit: raw frames + ghi chú ID nào thuộc BCM, ID nào thuộc cluster

**Step 7** — Export CSV & verify columns (15 min, evidence: `screenshot`, required=1)
- Nhấn EXPORT .CSV → file lưu Downloads
- Mở bằng Excel/Sheets điện thoại
- Verify có 9 cột: SEQ, TIME, TIMESTAMP_MS, **DIRECTION**, **SOURCE**, ADDRESS, CAN_FRAME_HEX, DELAY_MS, DECODED
- Submit: screenshot Excel showing CSV với 9 cột rõ ràng

**Step 8** — Summary table (20 min, evidence: `screenshot`, required=1)
- Trên giấy hoặc app notes, vẽ bảng tổng kết:

| CAN ID | Period (ms) | Source (BCM/Cluster) | Suspected function |
|--------|-------------|----------------------|---------------------|
| 0xXXX  | 10          | BCM                  | Engine speed       |
| ...    | ...         | ...                  | ...                |

- Submit: screenshot bảng tổng kết (chụp tay viết hoặc text)

**Total time**: ~2h hands-on + 30' setup + 30' export → fit 4h budget

#### 4.1.4 Post-quiz (3 câu)

| # | Type | Question |
|---|------|----------|
| 1 | free_text | Bao nhiêu unique CAN ID bạn quan sát được trong 2 phút sniff? Phân loại theo period (≤50ms / 50-200ms / >200ms). |
| 2 | free_text | Khi ngắt BCM khỏi bus, traffic giảm bao nhiêu %? Frame rate trước-sau là bao nhiêu? |
| 3 | image_upload | Upload 1 screenshot bạn tâm đắc nhất trong quá trình lab + giải thích 2-3 câu |

#### 4.1.5 Rubric

- Pre-quiz: auto từ DB (20%)
- Practice: ≥ 200 raw frames captured + đủ screenshots + đúng bảng tổng kết (40%)
- Post-quiz: 3 câu chất lượng (25%)
- Report: template + đầy đủ section (15%)

---

### LAB-02 — BCM Active Test — Actuator Control

**Code**: `LAB-02`
**Title (VN)**: Điều khiển Cơ cấu Chấp hành qua BCM Active Test
**Title (EN)**: BCM Active Test — Actuator Control
**Duration**: 5 tiết (4h)
**Difficulty**: Foundation → Intermediate
**LO mapping**: L.O.2, L.O.6
**Status**: Improved from LAB-02 v1

#### 4.2.1 Objectives

1. Hiểu UDS (Unified Diagnostic Services) Service `0x2F` — InputOutputControlByIdentifier
2. Đọc/ghi BCM CAN ID (request 0x7A0, response 0x7A8)
3. Thực hiện active test 8 chức năng BCM: đèn pha, cos, xi-nhan, hazard, còi, khoá cửa, gạt mưa
4. Phân tích cấu trúc UDS frame (length, service, DID, control parameter, data)
5. Quan sát phản hồi BCM (Positive vs Negative Response)

#### 4.2.2 Pre-quiz (6 câu, threshold 70%)

| # | Type | Question | Correct |
|---|------|----------|---------|
| 1 | MC | UDS Service 0x2F dùng để làm gì? — A) Đọc DTC / B) Xoá DTC / C) Điều khiển input/output / D) Update firmware | C |
| 2 | MC | Cấu trúc 1 frame UDS shortTermAdjustment "ON": A) `06 2F DID_H DID_L 03 01 00 00` / B) `02 01 00 00 00 00 00 00` / C) `05 2F 00 00 00 00 00 00` / D) `08 2F DID_H DID_L 04 01 02 03` | A |
| 3 | MC | controlParameter `0x03` nghĩa là gì? — A) Return control to ECU / B) shortTermAdjustment / C) reset / D) freeze | B |
| 4 | MC | BCM request ID Ford Ranger là gì? — A) 0x7DF / B) 0x7E0 / C) 0x7A0 / D) 0x7E8 | C |
| 5 | MC | Negative Response Code (NRC) byte đầu là gì? — A) 0x6F / B) 0x7F / C) 0x5F / D) 0x4F | B |
| 6 | free_text | Giải thích vì sao phải có frame "OFF" (returnControlToECU) sau khi xong active test? (1-2 câu) | "Để trả quyền điều khiển về BCM, tránh actuator bị stuck ở trạng thái test, gây nguy hiểm khi sử dụng thực" |

**Pass condition**: ≥ 70%

#### 4.2.3 Hands-on steps (8 steps)

**Step 1** — Setup bench + verify BCM responds (15 min, evidence: `screenshot`, required=1)
- Cấp nguồn 12V cho BCM (đúng cực!), key on
- Vào Raw Monitor, observe heartbeat từ BCM trên CAN ID 0x7A0/7A8 region
- Submit: screenshot Raw Monitor với BCM traffic rõ ràng

**Step 2** — Send first UDS command — High Beam ON (20 min, evidence: `active_test`, required=1)
- Vào Active Test screen → nhấn icon "Đèn pha" (high_beam)
- Quan sát: đèn pha bật trên cluster + bóng đèn vật lý sáng
- Trong Raw Monitor: thấy frame TX `0x7A0 06 2F D0 21 03 01 00 00`
- Sau 2 giây: frame TX OFF `0x7A0 05 2F D0 21 00 00 00 00`
- Cũng có frame RX `0x7A8 ...` (BCM positive response)
- Submit: app auto-record active_test evidence

**Step 3** — Send 4 more lighting commands (30 min, evidence: `active_test`, required=4)
- Lần lượt nhấn: lamp_on (low beam), left_turn, right_turn, hazard
- Quan sát đèn vật lý + cluster indicator
- Mỗi command: 1 evidence active_test
- Submit: 4 active_test entries

**Step 4** — Horn pulse test (10 min, evidence: `active_test`, required=1)
- Nhấn icon Horn (CanSender thủ công nếu chưa có icon — gửi `0x7A0 06 2F D0 10 03 01 00 00`)
- Cảnh báo: bịt tai! Còi 2 giây.
- Submit: 1 active_test entry

**Step 5** — Door lock/unlock (15 min, evidence: `active_test`, required=2)
- Door lock: `0x7A0 06 2F D0 40 03 01 00 00`
- Wait 2s
- Door unlock: `0x7A0 06 2F D0 41 03 02 00 00`
- Quan sát motor khoá cửa kêu click
- Submit: 2 active_test entries

**Step 6** — Wiper pulse (10 min, evidence: `active_test`, required=2)
- Front wiper: `0x7A0 06 2F D0 50 03 01 00 00` (3 giây)
- Rear wiper: `0x7A0 06 2F D0 51 03 01 00 00`
- Submit: 2 active_test entries

**Step 7** — Negative response observation (20 min, evidence: `raw_frame`, required=10)
- Gửi 1 frame có DID sai: `0x7A0 06 2F FF FF 03 01 00 00` (DID không tồn tại)
- Quan sát Raw Monitor: BCM trả `0x7A8 03 7F 2F 31 00 00 00 00` — NRC 0x31 RequestOutOfRange
- Filter "Both" để thấy cả TX và RX
- Submit: ≥ 10 raw frames bao gồm NRC response

**Step 8** — Generate timing report (30 min, evidence: `screenshot`, required=1)
- Export CSV
- Mở Excel, tính:
  - Latency trung bình giữa TX command và RX response (DELAY_MS column với filter TX/RX)
  - Số request thành công vs số NRC
- Submit: screenshot Excel với bảng tính

**Total**: ~2.5h hands-on

#### 4.2.4 Post-quiz (4 câu)

1. Liệt kê 8 chức năng đã test + CAN data ON tương ứng (bảng)
2. Latency trung bình bạn đo được là bao nhiêu? Có ổn định không?
3. Trường hợp NRC bạn quan sát được: NRC code là gì? Ý nghĩa?
4. Upload 1 video clip ngắn (10-30s) record màn hình app + đèn vật lý cùng sáng — chứng minh active test thành công

---

### LAB-03 — Gauge Simulation & Transfer Function ⭐ FLAGSHIP

**Code**: `LAB-03`
**Title (VN)**: Mô phỏng Kim Đồng hồ & Hàm Truyền Sensor
**Title (EN)**: Gauge Simulation & Transfer Function
**Duration**: 5 tiết (4h)
**Difficulty**: Intermediate
**LO mapping**: L.O.1, L.O.2, Ch.3, Ch.4.5
**Status**: NEW (uses Gauge Control feature)

#### 4.3.1 Objectives

1. Hiểu nguyên lý sensor → CAN encoding: `Raw_Value = Sensor_Value × Scale_Factor`
2. Giải mã byte order (high byte / low byte) và status byte
3. Sử dụng Gauge Control mới để drive kim RPM và Speed
4. Đo hàm truyền (transfer function) thực nghiệm, so sánh với lý thuyết
5. Phân tích sai số calibration, latency, độ ổn định

#### 4.3.2 Pre-quiz (7 câu, threshold 70%)

| # | Q | Correct |
|---|---|---------|
| 1 | MC: Công thức encode RPM trên Ford Ranger? A) Raw = RPM / 4 / B) Raw = RPM × 4 / C) Raw = RPM × 2 / D) Raw = RPM × 8 | B |
| 2 | MC: Với RPM = 3000, byte high (Byte[1]) là gì? A) 0x1F / B) 0x2E / C) 0x3A / D) 0x4E | B (3000×4=12000=0x2EE0) |
| 3 | MC: Byte order trên Ford MS-CAN cho RPM là gì? A) Little-endian / B) Big-endian / C) Mixed / D) Không định nghĩa | B |
| 4 | MC: Vì sao phải gửi RPM frame mỗi 100ms (chứ không phải 1 lần)? A) Để tiết kiệm bus / B) Vì cluster timeout watchdog reset kim về 0 / C) Vì CAN protocol yêu cầu / D) Vì RPM thay đổi liên tục | B |
| 5 | MC: Status byte (Byte[0]) thường có ý nghĩa gì? A) RPM × 256 / B) Engine running flag / C) Random / D) Checksum | B |
| 6 | free_text: Tính raw value để hiển thị RPM = 4500. Show your work. | 4500 × 4 = 18000 = 0x4650 → Byte[1]=0x46, Byte[2]=0x50 |
| 7 | free_text: Speed = 80 km/h với scale 100. Encode 2 bytes. | 80 × 100 = 8000 = 0x1F40 → Byte[0]=0x1F, Byte[1]=0x40 |

#### 4.3.3 Hands-on steps (8 steps)

**Step 1** — Open Gauge Control panel (10 min, evidence: `screenshot`, required=1)
- Vào Active Test → nhấn icon "GAUGE" tím trên top bar
- Panel slide up từ dưới
- Quan sát 2 digital display lớn (RPM đỏ, SPEED xanh)
- Submit: screenshot panel với 2 slider visible

**Step 2** — START stream & verify static RPM (15 min, evidence: `raw_frame`, required=50)
- Set RPM = 0, Speed = 0
- Nhấn START → status pill chuyển sang "STREAMING · 0"
- Quan sát kim cluster: phải ở vị trí 0
- Quan sát Raw Monitor: thấy 20 fps (10 RPM frame + 10 Speed frame mỗi giây) trên CAN ID `0x201` + `0x300`
- Submit: ≥ 50 raw frames TX với source = "gauge_event" (frame đầu) + "gauge_delta" (sau)

**Step 3** — Sweep RPM 0 → 3000 → 0 (20 min, evidence: `raw_frame`, required=100)
- Kéo slider RPM từ 0 lên 3000, từng bước ~500
- Quan sát kim cluster moving up smooth
- Verify digital display = giá trị slider
- Kéo về 0 từ từ
- Submit: ≥ 100 raw frames (delta logging: chỉ log khi value thay đổi ≥ 50)

**Step 4** — Build calibration table (30 min, evidence: `screenshot`, required=1)
- Set RPM lần lượt: 500, 1000, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 7000
- Mỗi giá trị: đợi 5 giây stable
- Đo: vị trí kim (chụp ảnh thước trên cluster), so với giá trị app

| Slider RPM | Kim cluster đọc | Sai số (%) | Hex data trên Raw Monitor |
|-----------|-----------------|------------|----------------------------|
| 500       | ?               | ?          | 01 07 D0 00 ...           |
| 1000      | ?               | ?          | 01 0F A0 00 ...           |
| 3000      | ?               | ?          | 01 2E E0 00 ...           |
| 6000      | ?               | ?          | 01 5D C0 00 ...           |

- Submit: screenshot bảng + ảnh kim cluster ở 3 mốc đại diện

**Step 5** — Speed sweep tương tự (20 min, evidence: `raw_frame`, required=80)
- Tương tự Step 4 nhưng với Speed slider
- Mốc: 20, 40, 60, 80, 100, 120, 140, 160, 180, 200 km/h
- Đo vị trí kim Speed
- Submit: ≥ 80 raw frames

**Step 6** — Measure latency (20 min, evidence: `screenshot`, required=1)
- Set RPM = 5000 rồi STOP đột ngột → đo thời gian từ STOP đến kim về 0
- Cluster Ford Ranger thường có damping ~500-1000ms
- Cluster có watchdog timeout: nếu không nhận frame mới trong ~500ms-2s → kim reset về 0
- Test: nhấn START → set RPM=3000 → đợi 2 giây → STOP stream (không reset) → đếm giây đến khi kim rớt
- Submit: screenshot kèm ghi chú "Watchdog timeout = X giây"

**Step 7** — Edge case: RPM > maxValue (15 min, evidence: `raw_frame`, required=20)
- Sửa file JSON config: tạm thời nâng RPM maxValue lên 10000 (instructor giúp)
- Gửi RPM = 9000 → kim có thể vượt thang đo
- Quan sát hành vi cluster: pin pegged ở max? blink? error?
- Hết edge test → restore maxValue về 8000
- Submit: ≥ 20 raw frames + ghi chú behavior

**Step 8** — Plot transfer function (30 min, evidence: `screenshot`, required=1)
- Dùng dữ liệu từ Step 4 → vẽ đồ thị:
  - Trục X: Slider RPM (app)
  - Trục Y: Kim cluster thực đọc
- Tính độ dốc đường thẳng (slope) ≈ 1.0 nếu calibration đúng
- Submit: screenshot đồ thị (vẽ tay hoặc dùng app như Sheets)

**Total**: ~3h hands-on (flagship lab — phức tạp nhất)

#### 4.3.4 Post-quiz (4 câu)

1. Độ dốc đường truyền của bạn là bao nhiêu? Có gần 1.0 không? Nguyên nhân lệch?
2. Watchdog timeout đo được là bao nhiêu giây? So với expected (500ms-2s) như thế nào?
3. Đoán: nếu xe thực phát RPM với scale ÷4 mà app encode ×4 → sẽ thế nào? (gợi ý: kim sẽ chỉ sai 16 lần)
4. Upload đồ thị transfer function (image_upload)

---

### LAB-04 — Hệ thống Cảnh báo Dashboard

**Code**: `LAB-04`
**Title (VN)**: Hệ thống Cảnh báo Dashboard — Mapping Warning Lights
**Title (EN)**: Dashboard Warning System Mapping
**Duration**: 5 tiết (4h)
**Difficulty**: Intermediate
**LO mapping**: L.O.2, L.O.6
**Status**: NEW (uses Active Test JSON config feature)

#### 4.4.1 Objectives

1. Phân loại 14 warning lights theo màu (red/amber/blue/green) và độ ưu tiên
2. Hiểu cơ chế: BCM activate via UDS vs Cluster activate via broadcast frame
3. Sử dụng Active Test JSON config để map mỗi icon → CAN data
4. Test tất cả 14 icons, document kết quả
5. Đề xuất nâng cấp config cho icon chưa hoạt động

#### 4.4.2 Pre-quiz (5 câu)

| # | Q | Correct |
|---|---|---------|
| 1 | MC: Màu nào dùng cho cảnh báo CRITICAL? A) Xanh / B) Vàng / C) Đỏ / D) Xanh dương | C |
| 2 | MC: Đèn pha (High Beam) trên cluster Ford thường có màu gì? A) Đỏ / B) Vàng / C) Xanh dương / D) Trắng | C |
| 3 | MC: File config dashboard ở đâu? A) /data/local / B) assets/can_config/ford_ranger_dashboard.json / C) Supabase / D) Hardcoded trong Kotlin | B |
| 4 | MC: Khi `canId = 0x000`, icon trên app hiển thị thế nào? A) Sáng đầy đủ / B) Mờ 65% / C) Mờ 15% + không bấm được / D) Hidden | C |
| 5 | free_text: Liệt kê 3 đèn cảnh báo MÀU ĐỎ trên dashboard Ford Ranger (vd: airbag, brake, oil...) |  "airbag, brake warning, oil pressure, seat belt, battery, engine overheat" — bất kỳ 3 trong số này |

#### 4.4.3 Hands-on steps (7 steps)

**Step 1** — Inventory 14 warning icons (15 min, evidence: `screenshot`, required=1)
- Mở Active Test screen
- List 14 icons hiện có: high_beam, lamp_on, seat_belt, tire, battery, engine_chk, airbag, engine, overheat, abs, brake, stability, oil, fuel
- Submit: screenshot Active Test screen với 14 icons rõ ràng

**Step 2** — Identify configured vs unconfigured (10 min, evidence: `screenshot`, required=1)
- Icons sáng 65% = đã có CAN config (canId ≠ 0x000)
- Icons mờ 15% = chưa config
- Đếm: bao nhiêu configured, bao nhiêu chưa?
- Submit: screenshot + ghi chú count

**Step 3** — Test configured icons (40 min, evidence: `active_test`, required=≥4)
- Nhấn lần lượt các icon đã configured (vd: high_beam, lamp_on)
- Quan sát đèn cluster bật lên 2 giây rồi tắt
- Mỗi icon: 1 active_test evidence
- Submit: ≥ 4 active_test entries

**Step 4** — Reverse engineer unconfigured icons (60 min, evidence: `raw_frame`, required=100)
- Chọn 1 icon chưa configured (vd: "fuel" hoặc "airbag")
- Mở CanSender → thử các CAN ID/data khác nhau
- Phương pháp 1: gửi UDS request `0x7A0 03 22 D0 XX 00 00 00 00` (Read DID 0xD0XX) với XX = 60-9F → xem BCM trả gì
- Phương pháp 2: search forum / FORScan DID list
- Phương pháp 3: brute force `0x7A0 06 2F D0 XX 03 01 00 00` với XX trong range → đèn nào sáng?
- Submit: ≥ 100 raw frames + ghi chú tìm được DID nào

**Step 5** — Update JSON config (30 min, evidence: `screenshot`, required=1)
- Mở `ford_ranger_dashboard.json` (instructor cho download từ Settings hoặc qua ADB)
- Sửa entry icon vừa reverse engineer được
- Save → reload app → verify icon từ mờ 15% chuyển sáng 65%
- Submit: screenshot Active Test screen với icon đã active

**Step 6** — Test newly configured icon (15 min, evidence: `active_test`, required=1)
- Nhấn icon vừa config → quan sát đèn cluster
- Submit: active_test evidence

**Step 7** — Document final mapping (30 min, evidence: `screenshot`, required=1)
- Tổng kết bảng:

| Icon | Color | canId | canData ON | canDataOff | Status | Source |
|------|-------|-------|------------|------------|--------|--------|
| high_beam | Blue | 0x7A0 | 06 2F D0 21 03 01 00 00 | 05 2F D0 21 00 00 00 00 | ✓ Works | UDS BCM |
| ... | ... | ... | ... | ... | ... | ... |

- Submit: screenshot bảng

**Total**: ~3.5h

#### 4.4.4 Post-quiz (4 câu)

1. Trong 14 icons, bao nhiêu hoạt động được sau khi config? Bao nhiêu cần BCM, bao nhiêu activate trực tiếp từ cluster broadcast?
2. Trong các icon bạn reverse engineer, DID nào tìm thấy? List ra ≥ 2 DID mới.
3. Đề xuất 2 cảnh báo còn THIẾU trên cluster của bạn mà xe Ford Ranger thực có (vd: lane assist, hill start assist...)
4. Upload screenshot bảng mapping cuối cùng (image_upload)

---

### LAB-05 — Chẩn đoán Lỗi Bus & Phục hồi

**Code**: `LAB-05`
**Title (VN)**: Chẩn đoán Lỗi CAN Bus & Auto-Recovery
**Title (EN)**: Bus Error Diagnostics & Recovery
**Duration**: 5 tiết (4h)
**Difficulty**: Intermediate → Advanced
**LO mapping**: L.O.5, L.O.6, Ch.3
**Status**: NEW (uses STM32 Phase 7 error reporting)

#### 4.5.1 Objectives

1. Hiểu CAN error states: warning → passive → BUS-OFF
2. Đọc MCP2515 EFLG register + TEC/REC counters
3. Trigger BUS-OFF intentional & quan sát STM32 auto-recovery
4. Diagnose root cause khi bus có lỗi (termination mismatch, baud mismatch, short circuit)
5. Tuân thủ an toàn — lab này có rủi ro phần cứng nếu sai

#### 4.5.2 Pre-quiz (6 câu)

| # | Q | Correct |
|---|---|---------|
| 1 | MC: Khi TEC > 96, MCP2515 vào state nào? A) Normal / B) Warning / C) Passive / D) BUS-OFF | B |
| 2 | MC: Khi TEC = 255, state là? A) Warning / B) Passive / C) BUS-OFF / D) Sleep | C |
| 3 | MC: Termination resistor 120Ω đặt sai (chỉ 1 đầu hoặc cả 2 đầu nối tiếp 60Ω) → hậu quả? A) OK bình thường / B) Bus reflection → CRC errors → BUS-OFF | B |
| 4 | MC: STM32 Phase 7 firmware xử lý BUS-OFF như thế nào? A) Ignore / B) Reset chip toàn bộ / C) Reset MCP2515, reload baud, return NORMAL / D) Tăng baud lên | C |
| 5 | MC: Error code 0x22 từ STM32 nghĩa là gì? A) Bad frame / B) BUS-OFF / C) Unknown type / D) TX overflow | B |
| 6 | free_text: Khi nào MCP2515 tự reset error counter về 0? Giải thích 1-2 câu. | "Sau khi 128 lần x 11-bit recessive bits liên tiếp được nhận thành công trên bus — đây là điều kiện 'bus idle' để MCP2515 reset BUS-OFF." |

#### 4.5.3 Hands-on steps (7 steps) — ⚠️ Cần GV giám sát chặt

**Step 1** — Baseline measurement (15 min, evidence: `screenshot`, required=1)
- Setup chuẩn: termination 120Ω 2 đầu, baud 125k khớp cluster
- Quan sát Raw Monitor: traffic ổn định, không có ERROR frame (TYPE 0x03)
- Đọc TEC/REC = 0 (xem qua debugger hoặc tính năng status nếu app expose)
- Submit: screenshot Raw Monitor + status bar ONLINE màu xanh

**Step 2** — Trigger BUS-OFF: remove 1 termination (20 min, evidence: `raw_frame`, required=30)
- ⚠️ GV giám sát: tháo 1 đầu 120Ω
- Trong vòng 1-2 giây: STM32 phát hiện CRC errors → TEC tăng → ERROR(0x20 BUS_WARNING) → ERROR(0x21 BUS_PASSIVE) → ERROR(0x22 BUS_OFF)
- App hiện toast/snackbar lần lượt 3 error
- Sau ~100ms: STM32 auto-recovery → ERROR(0x24 BUS_RECOVERED)
- Re-attach termination
- Submit: ≥ 30 raw frames + 4 ERROR frames trong log

**Step 3** — Read EFLG sequence (20 min, evidence: `screenshot`, required=1)
- Yêu cầu GV connect ST-Link debugger → đọc giá trị EFLG, TEC, REC tại các thời điểm:
  - Bình thường: EFLG=0x00, TEC=0, REC=0
  - Lúc Warning: EFLG=0x05 (EWARN + TXWAR)
  - Lúc Passive: EFLG=0x15 (+TXEP)
  - Lúc BUS-OFF: EFLG=0x25 (+TXBO)
  - Sau recovery: EFLG=0x00, TEC=0, REC=0
- Submit: screenshot debugger watch window (hoặc ghi tay nếu không có debugger)

**Step 4** — Trigger RX overflow (20 min, evidence: `raw_frame`, required=50)
- GV chuyển instrument cluster sang mode "flooding" (gửi traffic dày đặc) — hoặc dùng generator riêng
- App tạm dừng `Protocol_PollCanRx()` (instructor có thể inject "delay" giả lập)
- Sau ~500ms: MCP2515 RX0OVR/RX1OVR set → STM32 gửi ERROR(0x23 RX_BUF_OVERFLOW)
- Submit: ≥ 50 raw frames + ERROR(0x23) trong log

**Step 5** — Baud mismatch experiment (15 min, evidence: `raw_frame`, required=20)
- Settings → đổi CAN baud sang 500k (sai với cluster MS-CAN 125k)
- Submit FRAME_SET_BAUD → app gửi
- Quan sát: traffic ngừng hoàn toàn, hoặc CRC errors liên tục
- Đổi về 125k → khôi phục
- Submit: ≥ 20 raw frames trước/sau mismatch

**Step 6** — Length validation test (15 min, evidence: `active_test`, required=2)
- Dùng CanSender thử gửi 1 frame với LEN sai (5 bytes thay vì 8)
- Quan sát: app/STM32 trả ERROR(0x11 BAD_LENGTH)
- Tương tự: gửi unknown TYPE byte (vd: 0xFF) → ERROR(0x04 UNKNOWN_TYPE)
- Submit: 2 active_test entries với error response

**Step 7** — Recovery time measurement (15 min, evidence: `screenshot`, required=1)
- Quy trình:
  1. Bus đang chạy normal
  2. Bấm nút START timer trên đồng hồ điện tử
  3. Tháo 1 termination → STM32 vào BUS-OFF
  4. STM32 auto-recover
  5. Re-attach termination
  6. Bấm STOP khi thấy "BUS_RECOVERED" trên app
- Đo total time: target < 500ms
- Submit: screenshot timer + screenshot Raw Monitor với chuỗi 4 ERROR frames

**Total**: ~2h hands-on (an toàn cao, ít step nhưng quan trọng)

#### 4.5.4 Post-quiz (4 câu)

1. Sequence các ERROR codes bạn quan sát được khi tháo termination?
2. Recovery time đo được bao nhiêu ms? So với spec 100ms + reload?
3. Nếu cluster vào limp mode do BUS-OFF lặp lại nhiều lần, bạn xử lý thế nào?
4. Upload screenshot Raw Monitor có chứa cả 4 error frames trong 1 frame (image_upload)

---

### LAB-06 — Quy trình Chẩn đoán Tổng hợp (Capstone)

**Code**: `LAB-06`
**Title (VN)**: Quy trình Chẩn đoán Tổng hợp — Case Study
**Title (EN)**: Integrated Diagnostic Workflow — Capstone
**Duration**: 5 tiết (4h)
**Difficulty**: Advanced (vận dụng tổng hợp)
**LO mapping**: L.O.1 → L.O.6, Ch.4.5
**Status**: NEW (capstone)

#### 4.6.1 Objectives

1. Vận dụng quy trình chẩn đoán 3S/5S (theo TR4021 Ch.5)
2. Diagnose 3 fault scenarios do GV inject
3. Document root cause + proposed fix + verification
4. Viết PDF report đầy đủ theo template TR4021
5. Trình bày group findings (oral, 5 phút/nhóm)

#### 4.6.2 Pre-quiz (8 câu, threshold 70%)

| # | Q | Correct |
|---|---|---------|
| 1 | MC: 3S là gì? — A) Sort, Set, Shine / B) Safety, Security, Speed / C) Sense, Solve, Show / D) Standardize, Sustain | A |
| 2 | MC: 5S thêm gì so với 3S? — A) Speed, Setup / B) Standardize, Sustain / C) Safety, Security / D) Sensor, System | B |
| 3 | MC: Bước đầu tiên trong fault tree analysis là gì? — A) Fix the fault / B) Identify root cause / C) Define the problem clearly / D) Test hypothesis | C |
| 4 | MC: Sau khi fix fault, bước nào BẮT BUỘC? — A) Document / B) Verify the fix works / C) Send report / D) Both A and B | D |
| 5 | MC: Khi không biết root cause, kỹ thuật nào hữu ích? — A) Replace random parts / B) Binary search (isolate half by half) / C) Throw the device away / D) Wait | B |
| 6 | free_text: Liệt kê 4 công cụ trên app BKDiagnostic giúp diagnose bus issue. | Raw Monitor (TX/RX filter), CanSender, Active Test, Gauge Control, Live Data, Settings (baud config), Bus error log... |
| 7 | free_text: Khi STM32 báo ERROR(0x10 TX_QUEUE_OVR), nguyên nhân khả nghi là gì? | Bus quá đông, UART throughput không đủ — cần giảm rate gửi từ Android hoặc nâng UART baud |
| 8 | free_text: 1-2 câu giải thích tại sao "đọc DTC" KHÔNG đủ để chẩn đoán đầy đủ? | Vì DTC chỉ là triệu chứng, cần xác định root cause qua live data, schematic, history. Một DTC có thể có nhiều nguyên nhân. |

#### 4.6.3 Hands-on steps (6 steps — case studies)

**Step 1** — Initial bench check (15 min, evidence: `screenshot`, required=1)
- Verify bench OK trước khi GV inject fault
- Submit: screenshot baseline

**Step 2** — Case Study #1: "Kim RPM không lên" (30 min, evidence: `raw_frame` + `screenshot`, required=50+1)
- GV inject: sửa `ford_ranger_dashboard.json` gauges.rpm.canId từ `0x201` thành `0x205` (sai)
- SV nhiệm vụ:
  1. Mở Gauge Control → kéo RPM=3000 → kim không lên
  2. Mở Raw Monitor → quan sát frame TX với CAN ID `0x205` (sai)
  3. Đối chiếu tài liệu/spec → biết đúng là `0x201`
  4. Sửa JSON → restart app → verify kim lên đúng
- Submit: ≥ 50 raw frames (before + after fix) + screenshot final state với kim lên đúng

**Step 3** — Case Study #2: "Cluster câm sau 5 phút" (30 min, evidence: `raw_frame`, required=80)
- GV inject: termination resistor chỉ 1 đầu (rút 1 cái khỏi cluster end)
- SV nhiệm vụ:
  1. Bench chạy bình thường ~5 phút
  2. Đột nhiên: STM32 báo ERROR(0x20→0x21→0x22→0x24) liên tiếp
  3. Kiểm tra Raw Monitor: thấy chuỗi BUS_WARNING → PASSIVE → BUS_OFF → RECOVERED
  4. Đoán root cause: bus error → có thể termination, baud, hoặc nhiễu
  5. Kiểm tra vật lý: tìm thấy 1 termination tháo lỏng
  6. Re-attach → bus ổn định lại
- Submit: ≥ 80 raw frames bao gồm cả 4 ERROR frames + ghi chú diagnostic steps

**Step 4** — Case Study #3: "Đèn pha không bật được" (30 min, evidence: `active_test` + `raw_frame`, required=2+30)
- GV inject: BCM được cấp nguồn yếu (8V thay vì 12V) — hoặc DID trong JSON config bị sửa sai
- SV nhiệm vụ:
  1. Active Test → nhấn high_beam → đèn không sáng
  2. Raw Monitor filter "Both": thấy TX `0x7A0 06 2F D0 21...` nhưng RX `0x7A8 03 7F 2F XX` (NRC!)
  3. Đọc NRC code: vd 0x22 ConditionsNotCorrect (= điện áp thấp)
  4. Đo điện áp BCM bằng multimeter → phát hiện 8V
  5. Sửa nguồn → test lại → đèn sáng
- Submit: 2 active_test entries (before fix với NRC, after fix successful) + ≥ 30 raw frames

**Step 5** — Free-form diagnostic (45 min, evidence: tất cả types, required=auto)
- GV inject 1 fault tự do (chưa tiết lộ trước)
- SV apply 5S workflow:
  1. **Sort**: liệt kê hiện tượng (Sort by symptoms)
  2. **Set in order**: theo độ ưu tiên (Set priority order)
  3. **Shine**: clean check (Shine — clean inspection)
  4. **Standardize**: theo quy trình chuẩn (Standardize procedure)
  5. **Sustain**: verify + document (Sustain — keep working)
- 45 phút để diagnose + fix
- Submit: all evidence types

**Step 6** — Group presentation (30 min, evidence: `screenshot`, required=1)
- Mỗi nhóm chuẩn bị slide 8-12 trang (theo L.O.4)
- Trình bày 5 phút trước cả lớp:
  - 3 case studies + root cause + fix
  - Lessons learned
- Submit: screenshot slide cuối (summary slide)

**Total**: ~3h hands-on + 30' presentation

#### 4.6.4 Post-quiz (5 câu — reflection)

1. Trong 3 case studies, case nào khó nhất? Giải thích.
2. Vẽ flowchart 5S workflow bạn đã áp dụng (image_upload — chụp ảnh)
3. Nếu xe thực có triệu chứng "kim đồng hồ giật giật mỗi 30s", bạn sẽ check gì đầu tiên?
4. Đề xuất 1 improvement cho app BKDiagnostic giúp diagnose dễ hơn.
5. Self-assessment: bạn đạt LO nào trong 6 LO của TR4021 sau lab này? (checkbox)

---

## 5. Cross-cutting concerns

### 5.1 Group submission policy

- 1 nhóm 6-8 SV → submit chung 1 PDF report nhưng MỖI SV phải:
  - Pass pre-quiz cá nhân (không share answer)
  - Sign-off declaration section trong PDF (digital signature: full name + MSSV + date)
- Leader chịu trách nhiệm session code + final submit

### 5.2 Bench rotation schedule

Với 4-6 nhóm và 6 labs:
- Tuần 1: Nhóm A làm LAB-01, Nhóm B làm theory exercises song song
- Tuần 2: Nhóm A làm LAB-02, Nhóm B làm LAB-01, ...
- Tuần 6: tất cả hoàn thành 6 lab

### 5.3 Đánh giá thiếu evidence

Nếu nhóm không hoàn thành đủ N evidence cho 1 step:
- Step bị mark "incomplete" trong DB (auto-detect via `required_count`)
- PDF report sẽ có warning section "X steps incomplete"
- Trừ điểm Practice (40%) theo tỉ lệ steps done

### 5.4 Late submission

- Đúng quy chế TR4021: trừ 2 điểm/ngày trễ
- App tự đóng lab session sau **3 giờ** từ start (theo schema hiện có)
- Nếu hết session mà chưa xong: SV phải xin GV reset session

### 5.5 Cluster + BCM safety

⚠️ Mỗi lab phải có **safety briefing 5 phút** đầu giờ:
- Không cấp ngược cực BCM (12V vào đúng VBat pin)
- ESD: chạm tay vào ground trước khi handle board
- Không kéo CAN dây khi đang active test
- Không stream gauge khi USB lỏng (có thể partial frame → cluster confused)

---

## 6. Implementation plan

### 6.1 SQL seed file

Tạo file mới: `sql/seed/lab-content-v2-tr4021.sql`

Content:
- DELETE 2 lab cũ (LAB-01 v1, LAB-02 v1) — backup trước nếu cần
- INSERT 6 labs với metadata (code, title_vi, title_en, duration_minutes=240, threshold_percent=70)
- INSERT lab_steps cho mỗi lab (7-8 steps each)
- INSERT lab_questions: pre_lab (5-8 per lab) + post_lab (3-5 per lab)
- INSERT validation triggers nếu cần

Total INSERTs ước tính:
- 6 labs
- ~45 steps
- ~38 pre-quiz questions + ~24 post-quiz questions

### 6.2 PDF template update

Sau khi nhận template mẫu từ user:
- Update `web/src/components/lab/pdf/LabReportPdfTemplate.jsx`
- Update sub-components: CoverSection, ObjectivesSection, PreQuizSection, PracticeSummarySection, PostLabSection, DeclarationSection, footer
- Test render 1 mẫu PDF cho LAB-03 để verify

### 6.3 No code changes needed (mostly)

Spec này CHỦ YẾU là **content** — chèn vào DB qua SQL seed. Không cần thay đổi:
- App Android (UI đã đủ feature)
- Web admin (CRUD đã có)
- Web student (state machine đã có)
- Supabase schema (đã đủ table)

Trừ khi:
- PDF template style thay đổi (sau khi user cung cấp mẫu)
- Cần thêm UI cho 1 vài edge case (vd: image upload trong post-quiz đã có, nhưng cần test thumbnail)

### 6.4 Rollout plan

1. **Tuần 1**: Apply SQL seed → DB có 6 lab + content
2. **Tuần 1**: Test bằng admin account → tạo 1 lab session test
3. **Tuần 1**: Test bằng student account → flow đầy đủ pre-quiz → practice → post-quiz → PDF
4. **Tuần 2**: Update PDF template theo mẫu user cung cấp
5. **Tuần 2-3**: Pilot với 1 nhóm 6-8 SV thật → feedback iteration
6. **Tuần 4+**: Roll out cho toàn lớp TR4021

### 6.5 Effort estimate

| Task | Effort |
|------|--------|
| Write SQL seed (45 steps + 62 questions) | 6-8h |
| QA SQL seed (test in dev DB) | 2h |
| Test student flow end-to-end | 3h |
| Update PDF template (after user provides) | 4-6h |
| Documentation (instructor guide, student guide) | 4h |
| **Total** | **20-25h** |

---

## 7. Future enhancements (out of scope for v2)

- **LAB-07**: Diagnostic via OBD2 standard (Mode 01/03/04/22) — cần ECU
- **LAB-08**: Wiring + circuit diagnosis — cần multimeter integration
- **LAB-09**: CAN FD / Ethernet diagnostics — cần newer hardware
- **Group leaderboard**: Compare across groups
- **AI tutor**: Real-time hints khi SV stuck > 5 phút
- **VR/AR overlay**: 3D dashboard hiển thị frame flow

---

## 8. References

- TR4021 syllabus (DCMH.TR4021.2.1, HK223, August 2025)
- BKDiagnostic Lab System Design (`docs/superpowers/specs/2026-04-16-lab-system-design.md`)
- STM32 Debug Guide (`docs/STM32_DEBUG_GUIDE.md`)
- Ford Ranger CAN Tachometer Guide (`docs/ford-ranger-can-tachometer-guide.md`)
- ISO 14229-1 (UDS Service Specification)
- SAE J1979 (OBD-II PIDs)
- MCP2515 Datasheet (DS21801)

---

*Document version: 1.0 — Draft for review*
*Author: BKDiagnostic team — kiet.doanae@gmail.com*
