-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — TR4021 Lab Content v2 (6 labs)
--  Replaces lab-seed.sql v1 (LAB-01, LAB-02 only).
--
--  Order: run AFTER 01-lab-phase1-helpers.sql, 02-lab-schema.sql, 04-lab-rls.sql
--         (RLS policies must exist before content insert).
--  Idempotent: ON CONFLICT DO UPDATE everywhere.
--  Spec: docs/superpowers/specs/2026-05-15-lab-content-tr4021-design.md
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

-- ── Cleanup: remove orphan v1 steps/questions for LAB-01 & LAB-02 ───────────
-- ON CONFLICT DO UPDATE only handles existing PK; rows with step_order >
-- new max would remain. Explicit DELETE ensures clean state.
DELETE FROM public.lab_steps
 WHERE lab_id IN (SELECT id FROM public.labs WHERE code IN ('LAB-01','LAB-02'))
   AND step_order > 8;  -- v2 LAB-01 + LAB-02 each have ≤ 8 steps

DELETE FROM public.lab_questions
 WHERE lab_id IN (SELECT id FROM public.labs WHERE code IN ('LAB-01','LAB-02'))
   AND (
     (phase = 'pre_lab'  AND question_order > 8) OR
     (phase = 'post_lab' AND question_order > 5)
   );

-- ════════════════════════════════════════════════════════════════════════════
--  LAB-01 — Nền tảng CAN Bus & Sniffing Traffic (v2)
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-01',
    'Nền tảng CAN Bus & Sniffing Traffic',
    $md$### Mục tiêu học tập (TR4021 LO.2, LO.6, Ch.3)
Sau khi hoàn thành lab này, sinh viên có thể:
1. Giải thích cấu trúc của 1 CAN frame (SOF, ID, DLC, data, CRC, ACK, EOF)
2. Phân biệt MS-CAN (125kbps) vs HS-CAN (500kbps) trên xe Ford
3. Sử dụng app BKDiagnostic + STM32 gateway để sniff bus passive
4. Sử dụng filter TX/RX trong Raw Monitor mới
5. Xuất file CSV với cột DIRECTION + SOURCE và phân tích traffic patterns

### Thiết bị
- Cluster Ford Ranger + BCM Ford Ranger + STM32 + MCP2515 + Android phone
- Termination 120Ω 2 đầu bus MS-CAN

### Thời lượng
5 tiết (4h): Pre-quiz 15' → Theory 20' → Hands-on 2.5h → Post-quiz 30' → Report 30'$md$,
    1, 70, true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();

-- ── LAB-01 Steps (8) ───────────────────────────────────────────────────────

-- LAB-01 Step 1 — Setup bench + safety check
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    1,
    'Setup bench + safety check',
    $md$### Mục tiêu
Sinh viên kiểm tra setup bench an toàn trước khi cấp nguồn cho hệ thống.

### Các bước
1. Kiểm tra nguồn 12V đấu đúng cực (+/−) vào BCM Ford Ranger
2. USB cable từ Android tới CP2102 cắm chắc chắn, không lỏng
3. Termination resistor 120Ω được gắn đủ ở **cả 2 đầu** bus MS-CAN
4. Ghi checklist trước khi cấp nguồn

### Lưu ý
- Đấu sai cực 12V có thể làm hỏng BCM ngay lập tức — kiểm tra kỹ bằng đồng hồ trước khi cắm
- Submit: checkbox "Safety check passed"$md$,
    'none',
    0,
    'Dùng đồng hồ VOM đo cực BCM trước khi cấp nguồn — đỏ vào (+12V), đen vào GND.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-01 Step 2 — Verify USB & STM32 boot
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    2,
    'Verify USB & STM32 boot',
    $md$### Mục tiêu
Xác nhận kết nối Android ↔ STM32 ↔ MCP2515 hoạt động bình thường.

### Các bước
1. Cắm Android vào CP2102 → mở app BKDiagnostic → vào màn hình **Diagnostic Hub**
2. Quan sát status bar trên cùng: phải hiển thị "**ONLINE**" màu xanh
3. Quan sát LED trên board STM32: nháy 3× chậm (3 nháy dài) → MCP2515 init OK

### Lưu ý
- Nếu status bar đỏ "OFFLINE" → kiểm tra cable USB OTG + permission USB cho app
- Nếu LED STM32 nháy nhanh liên tục → MCP2515 init fail, kiểm tra dây SPI
- Submit: screenshot Diagnostic Hub với status ONLINE$md$,
    'screenshot',
    1,
    'Status bar ONLINE màu xanh nằm trên cùng màn hình Diagnostic Hub.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-01 Step 3 — Configure bus speed & enter Raw Monitor
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    3,
    'Configure bus speed & enter Raw Monitor',
    $md$### Mục tiêu
Cấu hình bus speed 125 kbps (MS-CAN) và mở Raw Frame Monitor sẵn sàng sniff.

### Các bước
1. Vào **Settings → CAN Bus Speed** → chọn **125 kbps** (MS-CAN cho Ford Ranger PX2)
2. Quay lại Diagnostic → mở **Raw Frame Monitor**
3. App tự gửi frame `FRAME_SET_BAUD` → STM32 reconfigure MCP2515 ở tốc độ mới

### Lưu ý
- Sai tốc độ bus → không sniff được frame nào, hoặc nhận toàn frame lỗi CRC
- HS-CAN của Ford Ranger chạy 500 kbps — KHÔNG chọn nhầm
- Submit: screenshot Raw Monitor đang ở trạng thái "RESUME" + connection ONLINE$md$,
    'screenshot',
    1,
    'Settings → CAN Bus Speed → 125 kbps. Sau đó vào Raw Monitor và screenshot.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-01 Step 4 — Passive sniff 2 phút
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    4,
    'Passive sniff 2 phút',
    $md$### Mục tiêu
Capture ≥ 200 raw frames passive (không gửi gì) trong 2 phút để quan sát traffic background.

### Các bước
1. Nhấn **RESUME** → bắt đầu capture frame
2. Đợi đủ 2 phút (instructor có thể bật/tắt đèn pha BCM để giả lập sự kiện)
3. Nhấn **STOP**
4. Filter: chọn "**▼ Response**" (chỉ RX) — lab này quan sát traffic, không gửi gì

### Lưu ý
- Nếu < 200 frame sau 2 phút → có thể bus không có thiết bị active broadcast → gọi instructor
- App tự upload raw_frames evidence khi nhấn STOP
- Submit: ≥ 200 raw frames$md$,
    'raw_frames',
    200,
    'Nhấn RESUME, đợi 2 phút, nhấn STOP. App tự upload evidence.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-01 Step 5 — Identify periodic broadcast IDs
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    5,
    'Identify periodic broadcast IDs',
    $md$### Mục tiêu
Tìm và phân loại các CAN ID xuất hiện đều đặn (periodic broadcast) trên bus.

### Các bước
1. Trong Raw Monitor đã pause (sau Step 4): tìm các CAN ID xuất hiện đều đặn
2. Quan sát cột **DELAY_MS** để tính period cho ít nhất 3 ID
3. Ghi vào bảng giấy hoặc app notes:

| CAN ID | Period (ms) | Suspected function |
|--------|-------------|---------------------|
| 0x430  | ~10         | Engine speed?       |
| 0x...  | ~...        | ...                 |

### Lưu ý
- Period điển hình trên xe: 10ms, 20ms, 50ms, 100ms, 200ms, 500ms, 1s
- Submit: screenshot bảng + Raw Monitor với 3 ID đã được khoanh tròn$md$,
    'screenshot',
    1,
    'Sort theo CAN ID, đọc cột DELAY_MS để biết period giữa 2 frame liên tiếp cùng ID.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-01 Step 6 — Compare cluster vs BCM sources
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    6,
    'Compare cluster vs BCM sources',
    $md$### Mục tiêu
Phân biệt CAN ID nào do BCM phát, ID nào do cluster phát bằng cách disconnect/reconnect BCM.

### Các bước
1. Instructor disconnect dây CAN_H đến BCM trong 30 giây
2. Sinh viên quan sát Raw Monitor: ID nào biến mất → đó là từ **BCM**
3. ID còn lại → của **cluster** (hoặc node khác)
4. Sau khi reconnect, capture thêm 30 giây nữa
5. Filter "**Both**" để thấy đầy đủ TX + RX

### Lưu ý
- Chỉ instructor được tháo dây — sinh viên KHÔNG tự tháo (an toàn bus)
- Submit: raw frames (≥100) + ghi chú ID nào thuộc BCM, ID nào thuộc cluster$md$,
    'raw_frames',
    100,
    'Khi BCM disconnect: ID của BCM sẽ ngừng xuất hiện trong stream. Ghi lại các ID đó.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-01 Step 7 — Export CSV & verify columns
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    7,
    'Export CSV & verify columns',
    $md$### Mục tiêu
Xuất raw frames ra file CSV và verify 9 cột chuẩn của format mới (có DIRECTION + SOURCE).

### Các bước
1. Trong Raw Monitor: nhấn **EXPORT .CSV** → file lưu vào thư mục Downloads
2. Mở file bằng Excel hoặc Google Sheets trên điện thoại
3. Verify đủ 9 cột:
   1. SEQ
   2. TIME
   3. TIMESTAMP_MS
   4. **DIRECTION** (TX / RX)
   5. **SOURCE** (gateway_rx / user_tx / gauge_event / ...)
   6. ADDRESS
   7. CAN_FRAME_HEX
   8. DELAY_MS
   9. DECODED

### Lưu ý
- Nếu thiếu cột DIRECTION hoặc SOURCE → app cũ, cần update lên v2
- Submit: screenshot Excel/Sheets show CSV với 9 cột rõ ràng$md$,
    'screenshot',
    1,
    'Mở file CSV từ Downloads bằng Sheets app, screenshot ngang để thấy đủ 9 cột.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-01 Step 8 — Summary table
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    8,
    'Summary table',
    $md$### Mục tiêu
Tổng hợp kết quả sniff thành 1 bảng summary để báo cáo.

### Các bước
Trên giấy hoặc app notes, vẽ bảng tổng kết với cột:

| CAN ID | Period (ms) | Source (BCM/Cluster) | Suspected function |
|--------|-------------|----------------------|---------------------|
| 0xXXX  | 10          | BCM                  | Engine speed       |
| ...    | ...         | ...                  | ...                |

Liệt kê ít nhất 5 CAN ID đã quan sát được trong Step 4-6.

### Lưu ý
- Có thể chụp ảnh bảng viết tay hoặc gõ text trong app notes
- Submit: screenshot bảng tổng kết$md$,
    'screenshot',
    1,
    'Chụp ảnh bảng viết tay hoặc gõ trong Notes app cũng được, miễn đủ 4 cột và ≥ 5 dòng.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- ── LAB-01 Pre-quiz (5 questions) ──────────────────────────────────────────

-- LAB-01 Pre-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 1, 'multiple_choice',
    'CAN frame chuẩn 11-bit có tối đa bao nhiêu CAN ID unique?',
    '{"A":"256","B":"1024","C":"2048","D":"4096"}'::jsonb,
    'C',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-01 Pre-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 2, 'multiple_choice',
    'Trên Ford Ranger PX2, Instrument Cluster nằm trên bus nào?',
    '{"A":"HS-CAN 500k","B":"MS-CAN 125k","C":"LIN bus","D":"FlexRay"}'::jsonb,
    'B',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-01 Pre-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 3, 'multiple_choice',
    'Termination resistor 120Ω cần đặt ở đâu?',
    '{"A":"1 đầu bus","B":"Cả 2 đầu bus","C":"Giữa bus","D":"Không cần"}'::jsonb,
    'B',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-01 Pre-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 4, 'multiple_choice',
    'DLC = 5 nghĩa là gì?',
    '{"A":"5 bit data","B":"5 byte data","C":"5 frame liên tiếp","D":"Frame ID = 5"}'::jsonb,
    'B',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-01 Pre-Q 5 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 5, 'free_text',
    'Trong cơ chế arbitration của CAN, frame có CAN ID **thấp hơn** thắng hay thua? Giải thích 1-2 câu.',
    NULL,
    'thắng — vì bit 0 dominant, ID thấp hơn có nhiều bit 0 ở vị trí cao hơn nên thắng arbitration',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- ── LAB-01 Post-quiz (3 questions) ─────────────────────────────────────────

-- LAB-01 Post-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 1, 'free_text',
    'Bao nhiêu unique CAN ID bạn quan sát được trong 2 phút sniff? Phân loại theo period (≤50ms / 50-200ms / >200ms).',
    NULL,
    NULL,
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-01 Post-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 2, 'free_text',
    'Khi ngắt BCM khỏi bus, traffic giảm bao nhiêu %? Frame rate trước-sau là bao nhiêu?',
    NULL,
    NULL,
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-01 Post-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 3, 'image_upload',
    'Upload 1 screenshot bạn tâm đắc nhất trong quá trình lab + giải thích 2-3 câu',
    NULL,
    NULL,
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- ════════════════════════════════════════════════════════════════════════════
--  LAB-02 — BCM Active Test — Điều khiển Cơ cấu Chấp hành (v2)
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-02',
    'BCM Active Test — Điều khiển Cơ cấu Chấp hành',
    $md$### Mục tiêu học tập (TR4021 LO.2, LO.6)
Sau khi hoàn thành lab này, sinh viên có thể:
1. Hiểu UDS (Unified Diagnostic Services) Service 0x2F — InputOutputControlByIdentifier
2. Đọc/ghi BCM CAN ID (request 0x7A0, response 0x7A8)
3. Thực hiện active test 8 chức năng BCM: đèn pha, cos, xi-nhan, hazard, còi, khoá cửa, gạt mưa
4. Phân tích cấu trúc UDS frame (length, service, DID, control parameter, data)
5. Quan sát phản hồi BCM (Positive vs Negative Response)

### Thiết bị
- Cluster Ford Ranger + BCM Ford Ranger + STM32 + MCP2515 + Android phone

### Thời lượng
5 tiết (4h): Pre-quiz 15' → Theory 20' → Hands-on 2.5h → Post-quiz 30' → Report 30'$md$,
    2, 70, true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();

-- ── LAB-02 Steps (8) ───────────────────────────────────────────────────────

-- LAB-02 Step 1 — Setup bench + verify BCM responds
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    1,
    'Setup bench + verify BCM responds',
    $md$### Mục tiêu
Đảm bảo BCM được cấp nguồn đúng và phản hồi trên CAN bus trước khi gửi active test.

### Các bước
1. Cấp nguồn 12V cho BCM (đúng cực!), key on
2. Vào **Raw Monitor**, quan sát heartbeat từ BCM trên CAN ID 0x7A0/0x7A8 region
3. Confirm: thấy traffic BCM định kỳ (chứng tỏ BCM đang chạy)
4. Submit: screenshot Raw Monitor với BCM traffic rõ ràng

### Lưu ý
- Đấu sai cực 12V có thể làm hỏng BCM ngay lập tức — kiểm tra kỹ
- Nếu không thấy BCM traffic: check fuse, check key position, check CAN H/L wiring$md$,
    'screenshot',
    1,
    'Filter Raw Monitor theo CAN ID = 0x7A0 hoặc 0x7A8 để dễ nhận biết BCM traffic.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-02 Step 2 — Send first UDS command — High Beam ON
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    2,
    'Send first UDS command — High Beam ON',
    $md$### Mục tiêu
Gửi lệnh active test đầu tiên qua UDS Service 0x2F để bật đèn pha (high beam) và quan sát phản hồi BCM.

### Các bước
1. Vào màn hình **Active Test** → nhấn icon "Đèn pha" (high_beam)
2. Quan sát: đèn pha bật trên cluster + bóng đèn vật lý sáng
3. Trong Raw Monitor: thấy frame TX `0x7A0 06 2F D0 21 03 01 00 00`
4. Sau 2 giây: frame TX OFF `0x7A0 05 2F D0 21 00 00 00 00`
5. Cũng có frame RX `0x7A8 ...` (BCM positive response)
6. Submit: app auto-record `active_test` evidence

### Lưu ý
- Phân tích cấu trúc frame: `06` = length, `2F` = service, `D0 21` = DID, `03` = controlParameter (shortTermAdjustment), `01` = data ON
- Frame OFF dùng controlParameter `00` (returnControlToECU)$md$,
    'active_test',
    1,
    'Mở 2 tab song song: Active Test + Raw Monitor (filter "Both") để thấy cả TX request lẫn RX response.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-02 Step 3 — Send 4 more lighting commands
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    3,
    'Send 4 more lighting commands',
    $md$### Mục tiêu
Test 4 chức năng chiếu sáng còn lại của BCM để quen với pattern active test.

### Các bước
Lần lượt nhấn các icon trên màn hình Active Test:
1. **lamp_on** (low beam — đèn cos)
2. **left_turn** (xi-nhan trái)
3. **right_turn** (xi-nhan phải)
4. **hazard** (đèn cảnh báo — cả 2 xi-nhan nhấp nháy)

Mỗi lệnh:
- Quan sát đèn vật lý bật/tắt
- Quan sát cluster indicator (mũi tên xi-nhan, biểu tượng cos)
- Mỗi command tạo 1 evidence `active_test`

### Lưu ý
- Submit: tổng 4 active_test entries (1 cho mỗi chức năng)
- Nếu đèn không sáng: kiểm tra dây nguồn của bóng + fuse, không phải lỗi UDS$md$,
    'active_test',
    4,
    'Test xi-nhan trái rồi phải liền nhau → so sánh DID byte để hiểu encoding scheme.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-02 Step 4 — Horn pulse test
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    4,
    'Horn pulse test',
    $md$### Mục tiêu
Test còi (horn) — actuator có tải lớn, cần observation kỹ.

### Các bước
1. Nhấn icon **Horn** trên Active Test screen
2. Nếu chưa có icon: vào **CanSender** thủ công, gửi `0x7A0 06 2F D0 10 03 01 00 00`
3. Còi sẽ kêu khoảng 2 giây rồi tự off
4. Submit: 1 evidence `active_test`

### Lưu ý
- ⚠️ **Bịt tai!** Còi xe rất to ở khoảng cách gần
- Không gửi liên tục >5s — gây nóng cuộn solenoid$md$,
    'active_test',
    1,
    'DID 0x10 = horn channel. Nếu CanSender báo lỗi format: thử HEX uppercase, không có space.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-02 Step 5 — Door lock/unlock
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    5,
    'Door lock/unlock',
    $md$### Mục tiêu
Test motor khóa cửa qua UDS — actuator cơ khí, dễ phân biệt âm thanh "click".

### Các bước
1. **Door lock**: gửi `0x7A0 06 2F D0 40 03 01 00 00`
2. Đợi 2 giây
3. **Door unlock**: gửi `0x7A0 06 2F D0 41 03 02 00 00`
4. Quan sát motor khoá cửa kêu **click** ở mỗi lệnh
5. Submit: 2 evidence `active_test`

### Lưu ý
- DID 0x40 = lock channel, 0x41 = unlock channel
- Data byte phân biệt lock (0x01) vs unlock (0x02)
- Nếu không nghe click: kiểm tra solenoid motor có cấp nguồn không$md$,
    'active_test',
    2,
    'Gửi lock → unlock liên tiếp để nghe rõ 2 click khác hướng.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-02 Step 6 — Wiper pulse
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    6,
    'Wiper pulse',
    $md$### Mục tiêu
Test gạt mưa trước và sau (2 channel khác nhau).

### Các bước
1. **Front wiper**: gửi `0x7A0 06 2F D0 50 03 01 00 00` — gạt khoảng 3 giây
2. **Rear wiper**: gửi `0x7A0 06 2F D0 51 03 01 00 00`
3. Quan sát motor gạt mưa quay 1 chu kỳ
4. Submit: 2 evidence `active_test`

### Lưu ý
- DID 0x50 = front wiper, 0x51 = rear wiper
- Nếu không có nước trên kính → gạt khô có thể xước kính (test ngắn, không lặp lại nhiều)$md$,
    'active_test',
    2,
    'Trên bench không có kính chắn gió: chỉ cần quan sát cần gạt động là OK.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-02 Step 7 — Negative response observation
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    7,
    'Negative response observation',
    $md$### Mục tiêu
Cố tình gửi request sai để quan sát BCM phản hồi Negative Response Code (NRC).

### Các bước
1. Gửi 1 frame với DID không tồn tại: `0x7A0 06 2F FF FF 03 01 00 00`
2. Quan sát Raw Monitor: BCM trả `0x7A8 03 7F 2F 31 00 00 00 00`
   - `7F` = Negative Response indicator
   - `2F` = service đã reject
   - `31` = NRC RequestOutOfRange
3. Filter "Both" để thấy cả TX request lẫn RX negative response
4. Submit: ≥ 10 raw frames bao gồm NRC response

### Lưu ý
- Đây là bài học quan trọng: ECU **không** im lặng khi từ chối, mà trả NRC
- NRC list chuẩn ISO 14229-1: 0x12 SubFunctionNotSupported, 0x13 InvalidFormat, 0x22 ConditionsNotCorrect, 0x31 RequestOutOfRange, 0x33 SecurityAccessDenied$md$,
    'raw_frames',
    10,
    'Filter Raw Monitor theo CAN ID 0x7A8 only để cô lập response BCM, đếm đủ 10 frame.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-02 Step 8 — Generate timing report
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    8,
    'Generate timing report',
    $md$### Mục tiêu
Xuất CSV log của toàn bộ session và phân tích timing/success rate trong Excel.

### Các bước
1. Trong Raw Monitor → **Export CSV**
2. Mở Excel:
   - Filter cột DIRECTION = TX và DIRECTION = RX
   - Tính **latency trung bình** giữa TX command và RX response (DELAY_MS column)
   - Đếm số request thành công (positive response) vs số NRC
3. Tạo bảng tổng kết
4. Submit: screenshot Excel với bảng tính + công thức rõ ràng

### Lưu ý
- Nếu CSV không có DELAY_MS: tự compute = `TIMESTAMP[n+1] - TIMESTAMP[n]` cho cặp request/response
- Latency thường < 50ms trên bench LAB$md$,
    'screenshot',
    1,
    'Dùng pivot table trong Excel để group theo CAN ID + DIRECTION, dễ tính latency average.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- ── LAB-02 Pre-quiz (6 questions) ──────────────────────────────────────────

-- LAB-02 Pre-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 1, 'multiple_choice',
    'UDS Service 0x2F dùng để làm gì?',
    '{"A":"Đọc DTC","B":"Xoá DTC","C":"Điều khiển input/output","D":"Update firmware"}'::jsonb,
    'C',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Pre-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 2, 'multiple_choice',
    'Cấu trúc 1 frame UDS shortTermAdjustment "ON" là gì?',
    '{"A":"06 2F DID_H DID_L 03 01 00 00","B":"02 01 00 00 00 00 00 00","C":"05 2F 00 00 00 00 00 00","D":"08 2F DID_H DID_L 04 01 02 03"}'::jsonb,
    'A',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Pre-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 3, 'multiple_choice',
    'controlParameter 0x03 nghĩa là gì?',
    '{"A":"Return control to ECU","B":"shortTermAdjustment","C":"reset","D":"freeze"}'::jsonb,
    'B',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Pre-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 4, 'multiple_choice',
    'BCM request ID Ford Ranger là gì?',
    '{"A":"0x7DF","B":"0x7E0","C":"0x7A0","D":"0x7E8"}'::jsonb,
    'C',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Pre-Q 5
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 5, 'multiple_choice',
    'Negative Response Code (NRC) byte đầu là gì?',
    '{"A":"0x6F","B":"0x7F","C":"0x5F","D":"0x4F"}'::jsonb,
    'B',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Pre-Q 6 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 6, 'free_text',
    'Giải thích vì sao phải có frame "OFF" (returnControlToECU) sau khi xong active test? (1-2 câu)',
    NULL,
    'Để trả quyền điều khiển về BCM, tránh actuator bị stuck ở trạng thái test, gây nguy hiểm khi sử dụng thực',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- ── LAB-02 Post-quiz (4 questions) ─────────────────────────────────────────

-- LAB-02 Post-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 1, 'free_text',
    'Liệt kê 8 chức năng đã test + CAN data ON tương ứng (bảng)',
    NULL,
    NULL,
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Post-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 2, 'free_text',
    'Latency trung bình bạn đo được là bao nhiêu? Có ổn định không?',
    NULL,
    NULL,
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Post-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 3, 'free_text',
    'Trường hợp NRC bạn quan sát được: NRC code là gì? Ý nghĩa?',
    NULL,
    NULL,
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-02 Post-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 4, 'image_upload',
    'Upload 1 video clip ngắn (10-30s) record màn hình app + đèn vật lý cùng sáng — chứng minh active test thành công',
    NULL,
    NULL,
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;
