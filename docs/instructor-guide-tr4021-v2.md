# TR4021 Instructor Guide — Lab Content v2

> **Course**: TR4021 — Kỹ thuật Chẩn đoán và Bảo dưỡng Ô tô (HCMUT, 3 credits, 30 lab hours)
> **Spec**: `docs/superpowers/specs/2026-05-15-lab-content-tr4021-design.md`
> **Seed**: `sql/seed/lab-content-v2-tr4021.sql`
> **Updated**: 2026-05-15

## Overview

6 labs designed for TR4021 (30 lab hours). Mỗi lab = **5 tiết (~4h)** chia thành 5 phase:
Pre-quiz 15' → Theory recap 20' → Hands-on 2.5h → Post-quiz 30' → Report 30'.

Tổng nội dung trên DB sau khi seed:
- **6 labs** (LAB-01 đến LAB-06, đều `is_published = true`, threshold 70%)
- **44 lab_steps** với 4 loại evidence: `none`, `raw_frames`, `active_test`, `screenshot`
- **61 lab_questions**: 38 pre-quiz + 23 post-quiz

Mapping với TR4021 Learning Outcomes:

| LO | Mô tả | Lab phủ |
|----|-------|---------|
| L.O.1 | Vẽ quy trình chẩn đoán + bảo dưỡng | LAB-03, 06 |
| L.O.2 | Thực hiện chẩn đoán + bảo dưỡng cơ bản | LAB-01, 02, 03, 04, 06 |
| L.O.3 | Hợp tác nhóm | Mọi lab (group submission) |
| L.O.4 | Thiết kế báo cáo | Mọi lab (PDF report) |
| L.O.5 | Tuân thủ an toàn | LAB-05, 06 |
| L.O.6 | Sử dụng thiết bị chuyên dụng đúng kỹ thuật | Mọi lab |

---

## Setup checklist trước mỗi buổi

- [ ] Cluster Ford Ranger cấp nguồn + boot screen hiển thị
- [ ] BCM Ford Ranger cấp nguồn 12V (kiểm tra đúng cực VBat)
- [ ] Termination 120Ω 2 đầu CAN bus
- [ ] STM32 LED nháy 3× chậm khi cấp nguồn (= MCP2515 init OK)
- [ ] Android phone charged ≥ 80% + app BKDiagnostic installed mới nhất
- [ ] Session code generated qua **Web Admin → Teach → Sessions → New**
- [ ] In sẵn 1 bản printout của session code + lab description cho group leader
- [ ] Multimeter sẵn sàng (đặc biệt cho LAB-05, LAB-06 case study #3)
- [ ] (Optional) ST-Link debugger cho LAB-05 step 3 (đọc EFLG register)

---

## Per-lab teaching notes

### LAB-01 — Nền tảng CAN Bus & Sniffing

**Theory recap (20 min)**:
- Vẽ CAN frame structure trên bảng (SOF, ID 11-bit, RTR, DLC, data 0-8B, CRC, ACK, EOF)
- Giải thích MS-CAN vs HS-CAN: 125k vs 500k, ECU nào nằm trên bus nào
- Demo bài Raw Monitor đã pre-record (30s traffic cluster + BCM) để SV biết "healthy sniff" trông thế nào

**Common pitfalls**:
- ⚠️ SV set baud 500k (HS-CAN) thay vì 125k → traffic silent → tưởng STM32 hỏng
- ⚠️ Termination chỉ 1 đầu, hoặc 2 đầu nối tiếp 60Ω → reflection, CRC errors
- ⚠️ SV quên switch filter sang "▼ Response" ở Step 4 → bị nhiễu thông tin

**Phân tích step quan trọng**:
- Step 4 (Passive sniff 2 phút): cần ≥ 200 raw frames. Nếu frame rate thấp, kiểm tra connection STM32 và CP2102.
- Step 6 (Compare cluster vs BCM): GV CHỦ ĐỘNG rút dây CAN_H đến BCM trong 30s để SV thấy ID nào biến mất. **KHÔNG để SV tự rút** vì có thể short circuit.

**Time budget thực tế**: 3.5-4h (Step 4-6 lâu nhất).

---

### LAB-02 — BCM Active Test

**Theory recap (20 min)**:
- Vẽ cấu trúc UDS frame: `[length][0x2F][DID_H][DID_L][controlParam][value][0x00][0x00]`
- Giải thích Service 0x2F vs các service khác (0x10, 0x22, 0x2E, 0x3E)
- Demo: nhấn high_beam icon, chỉ ra frame TX 0x7A0 + response 0x7A8

**Safety briefing**:
- ⚠️ Còi Step 4: **báo trước cả lớp**, bịt tai. Còi 2 giây.
- ⚠️ Door lock Step 5: motor cửa kêu cứng. Có thể gây giật mình.
- ⚠️ Không cho SV đứng gần BCM khi gửi command (lo xảy ra trường hợp HV cao trong relay)

**Common pitfalls**:
- ⚠️ SV không đợi frame OFF → actuator stuck ON. App đã auto-gửi OFF sau 2s nhưng nếu Lab Mode timeout có thể bỏ qua.
- ⚠️ BCM thiếu 12V → mọi command trả NRC 0x22 ConditionsNotCorrect. **Kiểm tra điện áp BCM bằng multimeter trước khi bắt đầu**.
- ⚠️ Step 7 (NRC test): GV demo cách gửi DID sai (0xFF 0xFF). SV thấy frame `0x7A8 03 7F 2F 31...` = NRC 0x31 RequestOutOfRange.

---

### LAB-03 — Gauge Simulation (FLAGSHIP)

**Allocate full 4h** — đây là lab phức tạp nhất, đừng rush.

**Theory recap (30 min — dài hơn lab khác)**:
- Vẽ encoding formula `Raw = Value × scaleFactor` trên bảng
- Tính ví dụ cụ thể: RPM 3000 → Raw 12000 → 0x2EE0 → [0x2E, 0xE0]
- Giải thích byte order (Big-endian Ford), status byte = 0x01 (engine running flag)
- Giải thích watchdog timeout: 500ms - 2s tùy cluster

**Pre-requisite SV phải biết**:
- Hex ↔ decimal conversion
- Big-endian vs little-endian
- Cơ bản về sensor signals (analog → digital → CAN)

**Step quan trọng**:
- Step 4 (Calibration table): GV cung cấp template Excel/Sheets. Bảng có 4 cột: slider RPM / kim cluster đọc / sai số (%) / hex data Raw Monitor.
- Step 6 (Latency): SV dùng đồng hồ điện tử trên điện thoại để đo thời gian. Hoặc nếu có camera slow-motion thì record.
- Step 7 (Edge case): GV PHẢI restore JSON config maxValue về 8000 sau khi xong, tránh state pollution giữa các nhóm.

**Calibration template gợi ý**:

| Slider RPM | Kim cluster đọc | Sai số (%) | Hex data |
|-----------|-----------------|-----------|----------|
| 500       | ?               | ?         | 01 07 D0 00 ... |
| 1000      | ?               | ?         | 01 0F A0 00 ... |
| 3000      | ?               | ?         | 01 2E E0 00 ... |
| 6000      | ?               | ?         | 01 5D C0 00 ... |

Sau khi xong → SV vẽ đồ thị transfer function (trục X = slider, trục Y = kim đọc). Slope kỳ vọng ≈ 1.0.

**Common pitfalls**:
- ⚠️ SV quên nhấn START → frame không gửi → kim đứng yên ở 0. **Always check status pill = "STREAMING"** trước khi đo.
- ⚠️ SV đọc giá trị kim cluster sai (do parallax). Nhắc đứng vuông góc khi đọc.
- ⚠️ Step 7: nếu maxValue = 10000 nhưng cluster thực max scale 8000, kim có thể bị pegged hoặc reset. Document hành vi này.

---

### LAB-04 — Dashboard Warning System

**Theory recap (20 min)**:
- Phân loại warning lights theo màu (red/amber/blue/green) và độ ưu tiên
- Giải thích cơ chế kích hoạt: BCM (UDS) vs cluster broadcast frame
- Show file `assets/can_config/ford_ranger_dashboard.json` — cấu trúc, cách edit

**Reverse engineering hint sheet**:

GV chuẩn bị trước, phát cho SV ở Step 4:
- DID range Ford BCM: **0xD000 - 0xDFFF**
- Lighting: 0xD020-0xD03F (high_beam=0xD021, lamp_on=0xD020)
- Horn: 0xD010
- Door locks: 0xD040-0xD04F
- Wipers: 0xD050-0xD05F
- Warning indicators có thể nằm: 0xD060-0xD09F

**JSON config editing trên Android**:

Cách 1 (recommend): Dùng ADB
```bash
adb pull /data/data/com.example.bkdiagnostic/files/can_config/ford_ranger_dashboard.json /tmp/
# Edit /tmp/ford_ranger_dashboard.json
adb push /tmp/ford_ranger_dashboard.json /data/data/com.example.bkdiagnostic/files/can_config/
# Restart app
```

Cách 2: In-app Settings → Lab Config Editor (nếu đã implement). Nếu chưa có UI, GV làm cho SV và SV chỉ thấy kết quả.

**Common pitfalls**:
- ⚠️ JSON syntax error → app crash khi reload. Backup config gốc trước khi edit.
- ⚠️ canId = 0x000 → icon disabled. SV có thể tưởng app bug.

---

### LAB-05 — Bus Error Diagnostics & Recovery

⚠️ **SAFETY CRITICAL** — GV phải giám sát chặt mọi step có thao tác phần cứng.

**Theory recap (25 min)**:
- Vẽ CAN error state machine: NORMAL → Warning (TEC > 96) → Passive (TEC > 127) → BUS-OFF (TEC = 255)
- Giải thích EFLG bits: EWARN, RXWAR, TXWAR, RXEP, TXEP, TXBO, RX0OVR, RX1OVR
- Demo STM32 Phase 7 auto-recovery flow

**Hardware safety procedure**:
1. **Trước khi rút termination (Step 2)**:
   - Đảm bảo cluster đang IDLE (không có active test đang chạy)
   - SV đeo wristband ground (chống ESD)
   - Chỉ rút 1 đầu, không cả 2
2. **Trong khi BUS-OFF**:
   - Quan sát LED STM32 — không nháy nhanh = OK
   - Nếu LED nhấp nháy 2× nhanh + pause = MCP2515 init failed → cấp nguồn lại
3. **Sau recovery**:
   - Re-attach termination CHẶT trước khi chạy bất kỳ test nào tiếp theo
   - Đọc EFLG để confirm TEC = 0

**Recovery time targets**:
- Total time (rút termination → BUS_RECOVERED toast): **< 500ms**
- Nếu > 1s → check firmware version (must be Phase 7 v2)
- Nếu không recover được → MCP2515 có thể đã hỏng vĩnh viễn

**Common pitfalls**:
- ⚠️ SV rút CẢ 2 termination → bus open → không recover được. Phải để 1 termination giữ bus reference.
- ⚠️ Step 4 (RX overflow): cần dùng external CAN generator hoặc inject delay vào firmware. GV chuẩn bị trước.
- ⚠️ Step 5 (baud mismatch): SV phải restore baud về 125k SAU khi xong, nếu không lab sau (LAB-06) sẽ hỏng.

---

### LAB-06 — Diagnostic Capstone

**Allocate full 4h + 30 min cho presentation**.

**3 case studies — Instructor PREP TIME**: ~1h trước buổi lab

**Case Study #1 — "Kim RPM không lên"**:
- Inject: edit `ford_ranger_dashboard.json`, change `gauges.rpm.canId` từ `0x201` → `0x205`
- Save backup gốc trước
- SV phải detect TX frame ID sai, sửa lại JSON, verify kim lên đúng
- Restore time: ~5 phút

**Case Study #2 — "Cluster câm sau 5 phút"**:
- Inject: tháo 1 termination resistor (lỏng vài mm, vẫn touch nhưng poor connection)
- Bench chạy normal ~5 phút → eventually CRC errors → BUS_WARNING → BUS_OFF
- SV phải nhận ra chuỗi 4 error frames, kiểm tra vật lý, tìm termination lỏng
- Restore time: ~3 phút (re-attach termination)

**Case Study #3 — "Đèn pha không bật được"**:
- Inject Option A: cấp 8V (thấp hơn 12V) cho BCM bằng bench supply variable voltage
- Inject Option B: edit JSON, sửa DID high_beam thành 0xD0FF (DID không tồn tại) → BCM trả NRC 0x31
- SV phải dùng multimeter (Option A) hoặc đọc Raw Monitor để thấy NRC (Option B)
- Restore time: ~2 phút

**Step 5 (Free-form, 45 min)**:
- GV inject 1 fault TỰ DO (không tiết lộ trước)
- Đề xuất scenarios:
  - "Active Test gửi nhưng không có response" → BCM mất nguồn
  - "Random frame loss" → CAN_L lỏng
  - "Gauge stream OK nhưng cluster freeze" → cluster watchdog stale
- SV apply 5S workflow, document trong app notes

**Step 6 — Group presentation**:
- Mỗi nhóm chuẩn bị slide deck **8-12 slides** (theo TR4021 LO.4)
- 5 phút trình bày + 2 phút Q&A
- Format gợi ý:
  - Slide 1: Tên nhóm + thành viên
  - Slide 2: Tổng quan 3 case studies
  - Slide 3-5: Case #1 (triệu chứng → diagnosis → fix → verify)
  - Slide 6-8: Case #2
  - Slide 9-11: Case #3
  - Slide 12: Lessons learned

---

## Grading rubric reference

Mỗi lab tổng điểm 100, threshold pass = 50 (theo quy chế HCMUT).

| Criteria | Trọng số | Cách chấm |
|----------|---------|-----------|
| Pre-quiz score | 20% | Auto từ DB (cần ≥ 70% mới qua được) |
| Practice completeness | 40% | Đếm evidence: đủ N → 100%, thiếu → trừ tỉ lệ |
| Post-quiz quality | 25% | Manual grade by instructor (free_text + image) |
| Report format & content | 15% | Manual grade theo PDF template |

Late submission: trừ 2 điểm/ngày (theo TR4021 quy chế).

---

## Session reset policy

App tự đóng lab session sau **3 giờ** từ start (theo schema).

Nếu nhóm cần extension:
1. Group leader báo GV
2. GV vào **Web Admin → Teach → Sessions**
3. Tìm session ID
4. Click "Reset Session" (extend thêm 3h)

**Lưu ý**: Reset session sẽ KHÔNG xoá evidence đã upload, chỉ extend thời gian.

---

## Troubleshooting

| Triệu chứng | Nguyên nhân khả nghi | Khắc phục |
|-------------|---------------------|-----------|
| App không kết nối STM32 | USB cable lỏng / CP2102 driver | Cắm lại USB, restart app |
| LED STM32 không nháy | Crystal 8MHz fake / nguồn yếu | Check MCO PA8 = 8MHz, cấp 5V đủ |
| MCP2515 CANSTAT = 0xFF | SPI dây đứt / CS sai | Kiểm tra PA4 (CS), MOSI/MISO/SCK |
| Bus traffic silent ở 125k | Termination missing / baud sai | Check 120Ω 2 đầu + Settings → CAN Baud |
| Kim cluster không lên ở LAB-03 | Stream interval > 500ms / CAN ID sai | Check JSON gauges.rpm.canId = 0x201 |
| BCM không phản hồi LAB-02 | Điện áp BCM < 11V | Đo multimeter, cấp 12V đúng |
| Lab session expired | App tự reset sau 3h | GV reset qua admin |

Tài liệu tham khảo chi tiết hơn:
- `docs/STM32_DEBUG_GUIDE.md` — Debug firmware STM32 (7-phase plan)
- `docs/ford-ranger-can-tachometer-guide.md` — CAN encoding chi tiết cho cluster

---

## Pilot rollout checklist

**Tuần 1**:
- [ ] Apply seed: chạy `sql/seed/lab-content-v2-tr4021.sql` trên Supabase
- [ ] Run verify: chạy `sql/seed/lab-content-v2-tr4021-verify.sql` → confirm 6/44/61
- [ ] Test admin flow: login admin, vào Teach → thấy 6 labs
- [ ] Test student flow: tạo 1 student test, làm thử LAB-01 (pre-quiz → session → post-quiz)

**Tuần 2**:
- [ ] Pilot LAB-01 với 1 nhóm 6-8 SV
- [ ] Feedback iteration (sửa pre-quiz nếu quá khó/dễ)
- [ ] PDF template update (sau khi user cung cấp template mẫu)

**Tuần 3-6**:
- [ ] Roll out LAB-02 → LAB-06 lần lượt
- [ ] Mỗi tuần: 1 nhóm/buổi (4-6 nhóm/lớp)

**Cuối học kỳ**:
- [ ] Tổng kết feedback từ SV (survey)
- [ ] Đánh giá điểm phân bố — có lab nào cả lớp fail không?
- [ ] Update content v3 cho học kỳ sau

---

*Tài liệu này có thể được edit khi feedback từ pilot phase. Version: v1.0 (2026-05-15)*
*Liên hệ: kiet.doanae@gmail.com*
