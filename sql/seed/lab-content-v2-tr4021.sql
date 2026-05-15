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

-- ════════════════════════════════════════════════════════════════════════════
--  LAB-03 — Mô phỏng Kim Đồng hồ & Hàm Truyền Sensor ⭐ FLAGSHIP (v2)
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-03',
    'Mô phỏng Kim Đồng hồ & Hàm Truyền Sensor',
    $md$### Mục tiêu học tập (TR4021 LO.1, LO.2, Ch.3, Ch.4.5)
Sau khi hoàn thành lab này, sinh viên có thể:
1. Hiểu nguyên lý sensor → CAN encoding: `Raw_Value = Sensor_Value × Scale_Factor`
2. Giải mã byte order (high byte / low byte) và status byte
3. Sử dụng Gauge Control mới để drive kim RPM và Speed
4. Đo hàm truyền (transfer function) thực nghiệm, so sánh với lý thuyết
5. Phân tích sai số calibration, latency, độ ổn định

### Thiết bị
- Cluster Ford Ranger + STM32 + MCP2515 + Android phone (BCM optional)

### Thời lượng
5 tiết (4h) — đây là FLAGSHIP lab, hands-on phức tạp nhất$md$,
    3, 70, true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();

-- ── LAB-03 Steps (8) ───────────────────────────────────────────────────────

-- LAB-03 Step 1 — Open Gauge Control panel
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    1,
    'Open Gauge Control panel',
    $md$### Mục tiêu
Mở panel Gauge Control mới — công cụ để drive kim RPM và Speed trên cluster Ford Ranger.

### Các bước
1. Vào màn hình **Active Test**
2. Trên top bar, nhấn icon **GAUGE** màu tím
3. Panel slide up từ dưới — chiếm ~60% chiều cao màn hình
4. Quan sát layout:
   - 2 digital display lớn: **RPM** (số đỏ) và **SPEED** (số xanh)
   - 2 slider tương ứng (RPM 0-8000, Speed 0-200 km/h)
   - Nút **START / STOP** stream + status pill
5. Submit: screenshot panel với cả 2 slider visible

### Lưu ý
- Nếu không thấy icon GAUGE → cập nhật app lên version mới nhất (≥ v2.0)
- Panel có thể vuốt xuống để đóng và mở lại nhiều lần — state không reset$md$,
    'screenshot',
    1,
    'Active Test → top bar → icon GAUGE màu tím. Sau khi panel mở, screenshot toàn màn hình.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-03 Step 2 — START stream & verify static RPM
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    2,
    'START stream & verify static RPM',
    $md$### Mục tiêu
Khởi động luồng phát frame định kỳ tới cluster và verify cluster nhận được (kim đứng yên ở 0).

### Các bước
1. Set RPM = **0**, Speed = **0** (cả 2 slider về tận trái)
2. Nhấn **START** → status pill chuyển sang "**STREAMING · 0**"
3. Quan sát kim cluster: phải ở vị trí **0** (kim không nhảy lung tung)
4. Mở Raw Monitor song song — quan sát:
   - ~10 frame/s trên CAN ID **0x201** (RPM)
   - ~10 frame/s trên CAN ID **0x300** (Speed)
   - Tổng ~20 fps TX
5. Filter source: frame đầu mỗi loại có source = **"gauge_event"**, các frame tiếp theo = **"gauge_delta"**
6. Submit: ≥ 50 raw frames TX gauge

### Lưu ý
- Nếu status pill báo lỗi → check kết nối STM32 (status bar phải ONLINE)
- Stream phải chạy liên tục — cluster timeout watchdog sẽ reset kim nếu mất frame > 500ms$md$,
    'raw_frames',
    50,
    'Filter Raw Monitor theo CAN ID = 0x201 hoặc 0x300, hoặc filter source = gauge_event/gauge_delta.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-03 Step 3 — Sweep RPM 0 → 3000 → 0
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    3,
    'Sweep RPM 0 → 3000 → 0',
    $md$### Mục tiêu
Kéo slider RPM lên xuống và quan sát kim cluster di chuyển smooth — verify control loop hoạt động.

### Các bước
1. Đảm bảo stream đang chạy (status "STREAMING")
2. Kéo slider **RPM** từ 0 lên **3000**, từng bước ~500 (dừng lại quan sát mỗi mốc)
3. Quan sát: kim cluster di chuyển **smooth, theo realtime** (không nhảy giật)
4. Verify: digital display trên app = giá trị slider
5. Kéo slider từ 3000 về **0** từ từ
6. Submit: ≥ 100 raw frames (delta logging chỉ log khi value thay đổi ≥ 50)

### Lưu ý
- Nếu kim cluster nhảy giật → có thể do bus collision hoặc tốc độ frame quá thấp
- Delta logging giảm spam log — frame chỉ đẩy lên khi thay đổi đáng kể
- Submit ≥ 100 raw frames thường tương đương với 1 full sweep up + down$md$,
    'raw_frames',
    100,
    'Kéo từ tốn, mỗi nấc 500 RPM, dừng 2-3 giây. Tổng cộng ~30 giây sweep là đủ 100 frame.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-03 Step 4 — Build calibration table
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    4,
    'Build calibration table',
    $md$### Mục tiêu
Đo sai số calibration giữa giá trị slider (app) và vị trí kim thực trên cluster — xây bảng dữ liệu để vẽ transfer function ở Step 8.

### Các bước
1. Set RPM lần lượt: **500, 1000, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 7000**
2. Mỗi giá trị: **đợi 5 giây stable** để kim cluster ổn định
3. Đo: vị trí kim cluster (chụp ảnh thước trên cluster), so với giá trị app
4. Đọc hex data từ Raw Monitor (frame CAN ID 0x201) tại thời điểm đó

| Slider RPM | Kim cluster đọc | Sai số (%) | Hex data trên Raw Monitor |
|------------|-----------------|------------|----------------------------|
| 500        | ?               | ?          | `01 07 D0 00 ...`         |
| 1000       | ?               | ?          | `01 0F A0 00 ...`         |
| 3000       | ?               | ?          | `01 2E E0 00 ...`         |
| 6000       | ?               | ?          | `01 5D C0 00 ...`         |

5. Submit: screenshot bảng + ảnh kim cluster ở **3 mốc đại diện** (ví dụ 1000, 3000, 6000)

### Lưu ý
- Verify công thức: Raw = RPM × 4 → ví dụ 3000 × 4 = 12000 = 0x2EE0 → high=0x2E, low=0xE0
- Byte[0] thường là status (0x01 = engine running)
- Sai số > 5% → có thể cluster bị lệch calibration vật lý (chấp nhận được, ghi vào báo cáo)$md$,
    'screenshot',
    1,
    'Pause Raw Monitor sau mỗi mốc để chép hex data chính xác. Chụp kim cluster bằng phone khác để khỏi che màn hình app.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-03 Step 5 — Speed sweep tương tự
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    5,
    'Speed sweep tương tự',
    $md$### Mục tiêu
Lặp lại Step 4 nhưng với slider Speed — verify hàm truyền của kênh tốc độ.

### Các bước
1. Stream vẫn đang chạy
2. Set Speed lần lượt: **20, 40, 60, 80, 100, 120, 140, 160, 180, 200 km/h**
3. Mỗi mốc: đợi 5 giây stable
4. Đo vị trí kim **Speed** trên cluster, so với app
5. Quan sát hex data Raw Monitor (CAN ID 0x300) — verify công thức Raw = Speed × 100
   - Ví dụ: 80 km/h × 100 = 8000 = 0x1F40
6. Submit: ≥ 80 raw frames

### Lưu ý
- Một số cluster Ford có max speed 240 km/h — không vượt quá để tránh overflow
- Sai số kim Speed thường nhỏ hơn RPM (cluster bevel speedometer chính xác hơn tachometer)
- Nếu kim Speed không nhúc nhích: check CAN ID 0x300 có thấy trên Raw Monitor không$md$,
    'raw_frames',
    80,
    'Sweep tương tự Step 3: kéo slider Speed từ 0 → 200 → 0, từng nấc ~20 km/h.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-03 Step 6 — Measure latency
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    6,
    'Measure latency',
    $md$### Mục tiêu
Đo 2 tham số timing quan trọng của cluster: **damping latency** (kim chậm phản hồi) và **watchdog timeout** (thời gian cluster tự reset kim khi mất frame).

### Các bước
**Phần A — Damping**:
1. Set RPM = **5000**, đợi kim ổn định
2. Set slider về **0** đột ngột
3. Đo thời gian từ lúc kéo slider đến khi kim cluster về 0 (~500-1000ms typical)

**Phần B — Watchdog timeout**:
1. Nhấn **START** → set RPM = **3000** → đợi 2 giây
2. Nhấn **STOP** stream (KHÔNG kéo slider về 0)
3. Đếm thời gian (giây) đến khi kim cluster **rớt về 0** do mất frame
4. Expected: 500ms - 2 giây (Ford cluster typical)

5. Submit: screenshot kèm ghi chú **"Damping = X ms, Watchdog timeout = Y giây"**

### Lưu ý
- Dùng stopwatch trên phone khác để đo chính xác, hoặc quay video rồi đếm frame
- Watchdog timeout là tính năng an toàn: nếu sensor failure, cluster tự về 0 thay vì "freeze"
- Nếu kim KHÔNG bao giờ rớt → có nguồn khác đang phát RPM lên bus (BCM hoặc ECU thực)$md$,
    'screenshot',
    1,
    'Stopwatch trên phone thứ 2, hoặc record video 60 fps rồi đếm frame để có timing chính xác.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-03 Step 7 — Edge case: RPM > maxValue
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    7,
    'Edge case: RPM > maxValue',
    $md$### Mục tiêu
Test hành vi cluster khi nhận giá trị RPM vượt thang đo (over-range) — bài học về defensive coding và safety.

### Các bước
1. **Instructor giúp** sửa file JSON config: tạm thời nâng RPM maxValue lên **10000**
2. Reload app → slider RPM giờ có range 0-10000
3. Gửi RPM = **9000** → quan sát kim cluster
4. Hành vi có thể xảy ra:
   - **Pin pegged**: kim chạm vạch max và đứng yên
   - **Wrap-around**: kim quay về 0 rồi tăng lên (overflow byte)
   - **Blink / error light**: cluster báo lỗi sensor
5. Ghi chú behavior vào notes
6. Hết edge test → **restore maxValue về 8000** (quan trọng!)
7. Submit: ≥ 20 raw frames + ghi chú behavior quan sát được

### Lưu ý
- Đây là bài học: app cần **clamp** giá trị input trước khi encode, KHÔNG để user gửi giá trị vô tội vạ
- Trên xe thực, sensor failure có thể gây giá trị bất thường — ECU phải có giới hạn hợp lý
- Nếu quên restore → student tiếp theo dùng app sẽ thấy range sai$md$,
    'raw_frames',
    20,
    'Trong quá trình edge test, mở Raw Monitor và filter CAN ID 0x201 để capture 20 frame nhanh.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-03 Step 8 — Plot transfer function
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    8,
    'Plot transfer function',
    $md$### Mục tiêu
Vẽ đồ thị **hàm truyền (transfer function)** từ dữ liệu Step 4 — visualization là deliverable chính của flagship lab này.

### Các bước
1. Mở dữ liệu Step 4 (bảng RPM slider vs Kim cluster đọc)
2. Vẽ đồ thị (vẽ tay trên giấy kẻ ô, hoặc Google Sheets / Excel / Desmos):
   - **Trục X**: Slider RPM (giá trị app gửi)
   - **Trục Y**: Kim cluster đọc thực (giá trị quan sát)
3. Nối các điểm và **fit đường thẳng** (linear regression)
4. Tính **slope** (độ dốc) của đường:
   - Slope ≈ **1.0** nếu calibration đúng (kim đúng = app)
   - Slope < 1.0 → cluster underreports (kim chỉ thấp hơn thực)
   - Slope > 1.0 → cluster overreports
5. Submit: screenshot đồ thị (kèm trục, scale, slope number)

### Lưu ý
- Đường có thể không hoàn toàn thẳng ở 2 đầu (0 và max) — hiện tượng nonlinearity
- Lý tưởng: slope = 1.0, intercept = 0, R² > 0.99
- Trong thực tế: sai số 2-5% là chấp nhận được cho cluster aftermarket bench
- Đây là phương pháp **system identification** cơ bản trong điều khiển học$md$,
    'screenshot',
    1,
    'Google Sheets: chèn 2 cột số (slider, đọc) → Insert → Chart → Scatter → Trendline với equation visible.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- ── LAB-03 Pre-quiz (7 questions) ──────────────────────────────────────────

-- LAB-03 Pre-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'pre_lab', 1, 'multiple_choice',
    'Công thức encode RPM trên Ford Ranger?',
    '{"A":"Raw = RPM / 4","B":"Raw = RPM × 4","C":"Raw = RPM × 2","D":"Raw = RPM × 8"}'::jsonb,
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

-- LAB-03 Pre-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'pre_lab', 2, 'multiple_choice',
    'Với RPM = 3000, byte high (Byte[1]) là gì? (gợi ý: 3000 × 4 = 12000 = 0x2EE0)',
    '{"A":"0x1F","B":"0x2E","C":"0x3A","D":"0x4E"}'::jsonb,
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

-- LAB-03 Pre-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'pre_lab', 3, 'multiple_choice',
    'Byte order trên Ford MS-CAN cho RPM là gì?',
    '{"A":"Little-endian","B":"Big-endian","C":"Mixed","D":"Không định nghĩa"}'::jsonb,
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

-- LAB-03 Pre-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'pre_lab', 4, 'multiple_choice',
    'Vì sao phải gửi RPM frame mỗi 100ms (chứ không phải 1 lần duy nhất)?',
    '{"A":"Để tiết kiệm bus","B":"Vì cluster timeout watchdog reset kim về 0 nếu mất frame","C":"Vì CAN protocol yêu cầu","D":"Vì RPM thay đổi liên tục"}'::jsonb,
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

-- LAB-03 Pre-Q 5
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'pre_lab', 5, 'multiple_choice',
    'Status byte (Byte[0]) trên frame RPM thường có ý nghĩa gì?',
    '{"A":"RPM × 256","B":"Engine running flag","C":"Random","D":"Checksum"}'::jsonb,
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

-- LAB-03 Pre-Q 6 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'pre_lab', 6, 'free_text',
    'Tính raw value (2 byte) để hiển thị RPM = 4500. Show your work.',
    NULL,
    '4500 × 4 = 18000 = 0x4650 → Byte[1]=0x46, Byte[2]=0x50',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-03 Pre-Q 7 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'pre_lab', 7, 'free_text',
    'Speed = 80 km/h với scale factor 100. Encode thành 2 bytes (big-endian).',
    NULL,
    '80 × 100 = 8000 = 0x1F40 → Byte[0]=0x1F, Byte[1]=0x40',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- ── LAB-03 Post-quiz (4 questions) ─────────────────────────────────────────

-- LAB-03 Post-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'post_lab', 1, 'free_text',
    'Độ dốc (slope) đường truyền của bạn là bao nhiêu? Có gần 1.0 không? Nguyên nhân lệch (nếu có)?',
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

-- LAB-03 Post-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'post_lab', 2, 'free_text',
    'Watchdog timeout đo được là bao nhiêu giây? So với expected (500ms-2s) như thế nào?',
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

-- LAB-03 Post-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'post_lab', 3, 'free_text',
    'Đoán: nếu xe thực phát RPM với scale ÷4 mà app encode ×4 → sẽ thế nào? (gợi ý: kim sẽ chỉ sai 16 lần)',
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

-- LAB-03 Post-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-03'),
    'post_lab', 4, 'image_upload',
    'Upload đồ thị transfer function (Slider RPM vs Kim cluster đọc) — kèm trục, scale, slope number.',
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
--  LAB-04 — Hệ thống Cảnh báo Dashboard — Mapping Warning Lights (v2)
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-04',
    'Hệ thống Cảnh báo Dashboard — Mapping Warning Lights',
    $md$### Mục tiêu học tập (TR4021 LO.2, LO.6)
Sau khi hoàn thành lab này, sinh viên có thể:
1. Phân loại 14 warning lights theo màu (red/amber/blue/green) và độ ưu tiên
2. Hiểu cơ chế: BCM activate via UDS vs Cluster activate via broadcast frame
3. Sử dụng Active Test JSON config để map mỗi icon → CAN data
4. Test tất cả 14 icons, document kết quả
5. Đề xuất nâng cấp config cho icon chưa hoạt động

### Thiết bị
- Cluster Ford Ranger + BCM Ford Ranger + STM32 + MCP2515 + Android phone
- File config `assets/can_config/ford_ranger_dashboard.json`

### Thời lượng
5 tiết (4h)$md$,
    4, 70, true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();

-- ── LAB-04 Steps (7) ───────────────────────────────────────────────────────

-- LAB-04 Step 1 — Inventory 14 warning icons
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    1,
    'Inventory 14 warning icons',
    $md$### Mục tiêu
Tổng quan toàn bộ 14 warning icons có sẵn trên màn hình Active Test trước khi đi sâu vào từng cái.

### Các bước
1. Mở app → vào màn hình **Active Test**
2. Cuộn xuống khu vực **Warning Lights** — đếm đủ 14 icon:
   high_beam, lamp_on, seat_belt, tire, battery, engine_chk, airbag, engine, overheat, abs, brake, stability, oil, fuel
3. Quan sát màu sắc icon: đỏ (critical), vàng (warning), xanh dương (info), xanh lá (status)
4. Submit: screenshot Active Test screen với cả 14 icons rõ ràng trong khung hình

### Lưu ý
- Nếu thiếu icon nào → app version chưa đủ mới, cập nhật lên ≥ v2.0
- Nếu icon hiển thị không đúng màu mong đợi → đó là feedback cho config JSON sau này$md$,
    'screenshot',
    1,
    'Active Test → khu vực Warning Lights. Đảm bảo screenshot show đủ 14 icons.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-04 Step 2 — Identify configured vs unconfigured
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    2,
    'Identify configured vs unconfigured icons',
    $md$### Mục tiêu
Phân biệt icon đã có CAN config (clickable) và icon chưa có config (disabled).

### Các bước
1. Trên Active Test screen, quan sát độ sáng (alpha) của từng icon:
   - **Sáng 65%** → đã có CAN config (`canId ≠ 0x000`) → có thể bấm thử
   - **Mờ 15%** → chưa config → không bấm được (`canId = 0x000`)
2. Đếm:
   - Bao nhiêu icon **configured** (sáng 65%)?
   - Bao nhiêu icon **unconfigured** (mờ 15%)?
3. Ghi chú lại danh sách từng loại (vd: configured = high_beam, lamp_on, ...)
4. Submit: screenshot + 1 dòng ghi chú ngắn "configured: N, unconfigured: M"

### Lưu ý
- Đếm số phải khớp tổng = 14
- Nếu tất cả icons đều mờ → file `ford_ranger_dashboard.json` không load được, kiểm tra log app$md$,
    'screenshot',
    1,
    'So sánh độ sáng icon — 65% là configured, 15% là chưa. Đếm và screenshot.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-04 Step 3 — Test configured icons
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    3,
    'Test configured icons (≥ 4)',
    $md$### Mục tiêu
Xác nhận từng icon đã configured thực sự bật được đèn tương ứng trên cluster.

### Các bước
1. Chọn ≥ 4 icon đã configured (vd: high_beam, lamp_on, seat_belt, battery)
2. Với mỗi icon:
   - Nhấn icon → quan sát đèn cluster bật lên
   - Đèn sẽ tự tắt sau ~2 giây (pulse mode)
   - App tự tạo 1 **active_test** evidence record
3. Lặp cho đủ ≥ 4 icon khác nhau
4. Submit: ≥ 4 active_test entries (mỗi entry tương ứng 1 icon)

### Lưu ý
- Nếu đèn cluster KHÔNG bật khi nhấn icon → ghi chú lại, có thể canData trong config sai
- Một số icon (như engine_chk) cần BCM gửi qua UDS — đảm bảo BCM đã được kết nối$md$,
    'active_test',
    4,
    'Nhấn lần lượt ≥ 4 icon đã configured. Quan sát đèn cluster pulse 2 giây.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-04 Step 4 — Reverse engineer unconfigured icons
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    4,
    'Reverse engineer 1 unconfigured icon',
    $md$### Mục tiêu
Tìm ra CAN ID + data cần thiết để bật 1 icon hiện chưa configured.

### Các bước
1. Chọn 1 icon chưa configured (vd: `fuel` hoặc `airbag`)
2. Mở **CanSender** screen — chuẩn bị thử nhiều CAN ID/data
3. Áp dụng 1 trong 3 phương pháp:
   - **Phương pháp 1** — Probe BCM DID:
     gửi UDS request `0x7A0 03 22 D0 XX 00 00 00 00` (Read DID `0xD0XX`) với `XX` quét từ 60 → 9F
     → xem BCM trả gì (positive response = DID tồn tại)
   - **Phương pháp 2** — Tra cứu cộng đồng:
     search forum FORScan / Ford Wiki cho danh sách DID dashboard
   - **Phương pháp 3** — Brute force write:
     gửi `0x7A0 06 2F D0 XX 03 01 00 00` với `XX` quét → quan sát đèn nào sáng
4. Ghi chú DID/CAN ID tìm được + chứng cứ (frame phản hồi)
5. Submit: ≥ 100 raw frames trong quá trình thử + ghi chú DID tìm được

### Lưu ý
- ⚠️ Brute force write có thể gây phản ứng phụ — chỉ làm khi xe ở chế độ bench, không gắn vào xe thật
- Nếu BCM trả negative response `7F 22 31` (request out of range) → DID không tồn tại, thử XX khác
- Lưu lại log Raw Monitor để Step 5 dùng làm reference$md$,
    'raw_frames',
    100,
    'CanSender → quét DID 0xD060-0xD09F. Lưu ≥ 100 frame từ Raw Monitor làm evidence.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-04 Step 5 — Update JSON config
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    5,
    'Update JSON config for new icon',
    $md$### Mục tiêu
Tích hợp DID/CAN data vừa reverse engineer được vào file config dashboard.

### Các bước
1. Lấy file `ford_ranger_dashboard.json`:
   - Cách 1: từ Settings của app → Export config
   - Cách 2: ADB pull `assets/can_config/ford_ranger_dashboard.json` (instructor hỗ trợ)
2. Mở file bằng editor → tìm entry icon vừa reverse engineer (vd: `"fuel"`)
3. Cập nhật các trường:
   - `canId`: thay `0x000` → CAN ID đã tìm (vd: `0x7A0`)
   - `canDataOn`: 8 byte hex bật đèn (vd: `06 2F D0 7A 03 01 00 00`)
   - `canDataOff`: 8 byte hex tắt đèn (vd: `05 2F D0 7A 00 00 00 00`)
4. Save file → reload app (hoặc Import config từ Settings)
5. Quay lại Active Test screen → verify icon từ mờ 15% chuyển sang sáng 65%
6. Submit: screenshot Active Test screen với icon vừa update đã active

### Lưu ý
- JSON phải parse được — kiểm tra dấu phẩy, ngoặc trước khi save
- Nếu reload xong icon vẫn mờ → log app sẽ báo parse error, dùng để debug$md$,
    'screenshot',
    1,
    'Sửa entry trong ford_ranger_dashboard.json, reload app, screenshot icon đã sáng 65%.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-04 Step 6 — Test newly configured icon
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    6,
    'Test newly configured icon',
    $md$### Mục tiêu
Xác nhận icon mới configured thực sự bật được đèn cluster — chứng minh reverse engineering thành công.

### Các bước
1. Trên Active Test screen, nhấn icon vừa update config
2. Quan sát đèn cluster tương ứng có pulse lên ~2 giây không
3. Nếu OK → 1 active_test evidence được tạo
4. Nếu KHÔNG OK → xem lại Step 4 (CAN data có thể sai byte), Step 5 (JSON parse có thể lỗi)
5. Submit: 1 active_test evidence

### Lưu ý
- Đèn cluster phải bật rõ ràng — nếu chỉ chớp 1 lần rất nhanh có thể là frame chưa đủ duration
- Có thể nhấn icon nhiều lần để confirm tính ổn định trước khi submit$md$,
    'active_test',
    1,
    'Nhấn icon mới configured. Đèn cluster phải pulse 2 giây.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-04 Step 7 — Document final mapping
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    7,
    'Document final mapping table',
    $md$### Mục tiêu
Tổng kết toàn bộ 14 icons với CAN data + nguồn activate dưới dạng bảng tham chiếu.

### Các bước
1. Mở Google Sheets / Excel / Notion → tạo bảng theo template:

| Icon | Color | canId | canDataOn | canDataOff | Status | Source |
|------|-------|-------|-----------|------------|--------|--------|
| high_beam | Blue | 0x7A0 | 06 2F D0 21 03 01 00 00 | 05 2F D0 21 00 00 00 00 | ✓ Works | UDS BCM |
| lamp_on | Green | 0x7A0 | ... | ... | ✓ Works | UDS BCM |
| ... | ... | ... | ... | ... | ... | ... |

2. Điền đủ 14 dòng — kể cả icon vẫn chưa hoạt động (Status = ✗ TODO, Source = ?)
3. Cột **Source** phân biệt: `UDS BCM` (gửi qua BCM) vs `Cluster broadcast` (frame định kỳ từ cluster) vs `?` (chưa rõ)
4. Submit: screenshot bảng đầy đủ (zoom đọc rõ)

### Lưu ý
- Bảng này là deliverable chính của lab — instructor sẽ chấm dựa trên độ đầy đủ và tính chính xác
- Lưu lại bảng để dùng cho LAB-05 (chẩn đoán lỗi) và LAB-06 (capstone)$md$,
    'screenshot',
    1,
    'Sheet 14 dòng × 7 cột. Screenshot zoom đọc rõ từng ô.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- ── LAB-04 Pre-quiz (5 questions) ──────────────────────────────────────────

-- LAB-04 Pre-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'pre_lab', 1, 'multiple_choice',
    'Màu nào dùng cho cảnh báo CRITICAL trên dashboard?',
    '{"A":"Xanh lá","B":"Vàng","C":"Đỏ","D":"Xanh dương"}'::jsonb,
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

-- LAB-04 Pre-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'pre_lab', 2, 'multiple_choice',
    'Đèn pha (High Beam) trên cluster Ford thường có màu gì?',
    '{"A":"Đỏ","B":"Vàng","C":"Xanh dương","D":"Trắng"}'::jsonb,
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

-- LAB-04 Pre-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'pre_lab', 3, 'multiple_choice',
    'File config dashboard ở đâu trong project?',
    '{"A":"/data/local","B":"assets/can_config/ford_ranger_dashboard.json","C":"Supabase","D":"Hardcoded trong Kotlin"}'::jsonb,
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

-- LAB-04 Pre-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'pre_lab', 4, 'multiple_choice',
    'Khi `canId = 0x000`, icon trên app hiển thị thế nào?',
    '{"A":"Sáng đầy đủ","B":"Mờ 65%","C":"Mờ 15% + không bấm được","D":"Hidden"}'::jsonb,
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

-- LAB-04 Pre-Q 5 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'pre_lab', 5, 'free_text',
    'Liệt kê 3 đèn cảnh báo MÀU ĐỎ trên dashboard Ford Ranger (vd: airbag, brake, oil...)',
    NULL,
    'airbag, brake warning, oil pressure, seat belt, battery, engine overheat — bất kỳ 3 trong số này',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- ── LAB-04 Post-quiz (4 questions) ─────────────────────────────────────────

-- LAB-04 Post-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'post_lab', 1, 'free_text',
    'Trong 14 icons, bao nhiêu hoạt động được sau khi config? Bao nhiêu cần BCM, bao nhiêu activate trực tiếp từ cluster broadcast?',
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

-- LAB-04 Post-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'post_lab', 2, 'free_text',
    'Trong các icon bạn reverse engineer, DID nào tìm thấy? List ra ≥ 2 DID mới.',
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

-- LAB-04 Post-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'post_lab', 3, 'free_text',
    'Đề xuất 2 cảnh báo còn THIẾU trên cluster của bạn mà xe Ford Ranger thực có (vd: lane assist, hill start assist...)',
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

-- LAB-04 Post-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-04'),
    'post_lab', 4, 'image_upload',
    'Upload screenshot bảng mapping cuối cùng (14 icons × 7 cột: Icon, Color, canId, canDataOn, canDataOff, Status, Source).',
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
--  LAB-05 — Chẩn đoán Lỗi CAN Bus & Auto-Recovery (v2)
--  ⚠️ Lab có rủi ro phần cứng — yêu cầu GV giám sát chặt mọi bước thao tác bus
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-05',
    'Chẩn đoán Lỗi CAN Bus & Auto-Recovery',
    $md$### Mục tiêu học tập (TR4021 LO.5, LO.6, Ch.3)
Sau khi hoàn thành lab này, sinh viên có thể:
1. Hiểu CAN error states: warning → passive → BUS-OFF
2. Đọc MCP2515 EFLG register + TEC/REC counters
3. Trigger BUS-OFF intentional & quan sát STM32 auto-recovery
4. Diagnose root cause khi bus có lỗi (termination mismatch, baud mismatch, short circuit)
5. Tuân thủ an toàn — lab này có rủi ro phần cứng nếu sai

### ⚠️ AN TOÀN
**LAB này yêu cầu GIẢNG VIÊN GIÁM SÁT CHẶT** vì có thao tác phần cứng:
- Tháo termination resistor khi bus đang hoạt động
- Có thể gây stress cho cluster/BCM nếu không cẩn thận

### Thiết bị
- Cluster Ford Ranger + BCM Ford Ranger + STM32 + MCP2515 + Android phone
- ST-Link debugger (optional, để đọc register MCP2515)

### Thời lượng
5 tiết (4h)$md$,
    5, 70, true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();

-- ── LAB-05 Steps (7) ───────────────────────────────────────────────────────

-- LAB-05 Step 1 — Baseline measurement
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    1,
    'Baseline measurement — bus khỏe mạnh',
    $md$### Mục tiêu
Xác lập baseline để so sánh với trạng thái lỗi ở các bước sau — bus hoạt động bình thường, không có ERROR frame, TEC/REC = 0.

### Các bước
1. Đảm bảo setup chuẩn: 2 termination 120Ω ở 2 đầu bus, baud = 125k (MS-CAN Ford Ranger)
2. Bật cluster + BCM + STM32, kết nối app qua BLE
3. Mở **Raw Monitor** — quan sát traffic ổn định ~30-60 frame/s, **không** có frame TYPE 0x03 (ERROR)
4. Kiểm tra status bar app: ONLINE (màu xanh), không có toast/snackbar cảnh báo
5. Đọc TEC/REC qua debugger (nếu có ST-Link) hoặc tính năng status — phải = 0
6. Submit: screenshot Raw Monitor + status bar ONLINE rõ ràng

### Lưu ý an toàn
- Bước này KHÔNG can thiệp phần cứng — chỉ quan sát
- Nếu thấy ERROR frame ngay từ baseline → kiểm tra wiring trước khi tiếp tục
- Đây là tham chiếu để so sánh trong các bước tháo termination phía sau$md$,
    'screenshot',
    1,
    'Raw Monitor traffic đều, status bar ONLINE xanh, không ERROR frame.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-05 Step 2 — Trigger BUS-OFF by removing 1 termination
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    2,
    'Trigger BUS-OFF: tháo 1 termination resistor',
    $md$### Mục tiêu
Cố ý đẩy bus vào trạng thái BUS-OFF bằng cách tháo 1 termination 120Ω, quan sát chuỗi chuyển trạng thái warning → passive → BUS-OFF và STM32 auto-recovery.

### ⚠️ CẢNH BÁO AN TOÀN
**⚠️ Cần GV giám sát chặt — tháo termination khi bus đang hoạt động có thể gây stress cho cluster nếu không cẩn thận.**
- Chỉ tháo termination trong < 5 giây, KHÔNG để lâu
- Re-attach NGAY sau khi đã ghi nhận đủ ERROR frame
- Tuyệt đối KHÔNG thực hiện khi xe gắn trên xe thật — chỉ bench setup

### Các bước
1. Bật Raw Monitor + bật log recording (để bắt frame TYPE 0x03)
2. Báo GV sẵn sàng → GV thực hiện: tháo 1 đầu termination 120Ω
3. Trong 1-2 giây, quan sát chuỗi:
   - STM32 detect CRC errors → TEC tăng nhanh
   - Toast/snackbar lần lượt: **ERROR(0x20) BUS_WARNING** → **ERROR(0x21) BUS_PASSIVE** → **ERROR(0x22) BUS_OFF**
   - Sau ~100ms: **ERROR(0x24) BUS_RECOVERED** (STM32 auto-recovery)
4. Re-attach termination NGAY khi đã thấy đủ 4 ERROR frame
5. Submit: ≥ 30 raw frames trong log, phải chứa cả 4 ERROR frame nêu trên

### Lưu ý an toàn
- Nếu sau 5 giây vẫn không thấy BUS_OFF → re-attach termination NGAY, kiểm tra setup
- Nếu cluster lock/limp mode → GV cycle power BCM + cluster
- Ghi nhận thời điểm chính xác để Step 7 (recovery time measurement) đối chiếu$md$,
    'raw_frames',
    30,
    'GV tháo 120Ω < 5s, app sẽ hiện 4 ERROR (0x20→0x21→0x22→0x24). Re-attach ngay.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-05 Step 3 — Read EFLG sequence
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    3,
    'Đọc EFLG sequence qua debugger',
    $md$### Mục tiêu
Kiểm chứng giá trị EFLG register + TEC/REC tại từng trạng thái lỗi để hiểu cơ chế bên trong MCP2515.

### Các bước
1. GV connect ST-Link debugger vào STM32, mở watch window theo dõi `EFLG`, `TEC`, `REC` của MCP2515 (qua SPI read)
2. Ghi lại giá trị tại 5 thời điểm:

| Thời điểm | EFLG (hex) | TEC | REC |
|-----------|-----------|-----|-----|
| Bình thường | 0x00 | 0 | 0 |
| Warning | 0x05 (EWARN + TXWAR) | > 96 | … |
| Passive | 0x15 (+TXEP) | > 128 | … |
| BUS-OFF | 0x25 (+TXBO) | 255 | … |
| Sau recovery | 0x00 | 0 | 0 |

3. Re-trigger BUS-OFF nhẹ (Step 2 quy trình) để capture các giá trị giữa
4. Submit: screenshot debugger watch window (hoặc ảnh ghi tay nếu không có ST-Link)

### Lưu ý an toàn
- Step này lặp lại thao tác tháo termination → áp dụng lại CẢNH BÁO Step 2
- Đọc debugger phải đồng bộ với app toast — dùng pause/breakpoint nếu cần
- Nếu không có ST-Link: ghi tay 5 dòng dựa trên log app + sách MCP2515 datasheet$md$,
    'screenshot',
    1,
    'ST-Link watch EFLG/TEC/REC. Capture 5 thời điểm: normal, warning, passive, bus-off, recovered.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-05 Step 4 — Trigger RX overflow
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    4,
    'Trigger RX buffer overflow',
    $md$### Mục tiêu
Gây RX overflow để quan sát error code 0x23 RX_BUF_OVERFLOW (khác BUS-OFF — đây là lỗi buffer phía controller).

### Các bước
1. GV chuyển cluster sang mode "flooding" — gửi traffic dày đặc (vd: 200+ frame/s)
   - Hoặc dùng CAN generator riêng (Kvaser/PCAN) blast frame trên cùng bus
2. Trong app: tạm dừng `Protocol_PollCanRx()` — GV có thể inject "delay" giả lập để STM32 không kịp đọc RXBUF
3. Sau ~500ms: MCP2515 set RX0OVR/RX1OVR flag → STM32 gửi **ERROR(0x23) RX_BUF_OVERFLOW**
4. Quan sát toast/snackbar trên app + frame TYPE 0x03 code 0x23 trong Raw Monitor
5. Tháo cluster khỏi flooding mode → bus normal trở lại
6. Submit: ≥ 50 raw frames trong log, chứa ít nhất 1 ERROR(0x23)

### Lưu ý an toàn
- Flooding traffic không gây hư phần cứng, nhưng có thể làm cluster hiển thị bất thường tạm thời
- Không kéo dài flooding > 30 giây — sẽ làm log/RAM phía app phình to
- Nếu STM32 reset đột ngột → kiểm tra power supply (flooding tăng dòng tiêu thụ)$md$,
    'raw_frames',
    50,
    'Cluster flooding + pause RX poll. ERROR(0x23) RX_BUF_OVERFLOW xuất hiện sau ~500ms.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-05 Step 5 — Baud mismatch experiment
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    5,
    'Baud mismatch experiment',
    $md$### Mục tiêu
Quan sát hậu quả khi baud rate STM32 sai lệch với cluster — minh chứng tại sao baud setting phải khớp.

### Các bước
1. Mở **Settings** → đổi CAN baud từ 125k sang **500k** (sai với cluster MS-CAN 125k)
2. App gửi FRAME_SET_BAUD xuống STM32 → STM32 cấu hình lại MCP2515 với baud mới
3. Quan sát Raw Monitor sau khi đổi:
   - Traffic ngừng hoàn toàn, hoặc
   - CRC errors liên tục (ERROR frame TYPE 0x03)
4. Đợi 5-10 giây để ghi nhận đủ frame chứng cứ
5. Đổi baud về **125k** → traffic khôi phục bình thường
6. Submit: ≥ 20 raw frames bao gồm cả giai đoạn trước & sau mismatch

### Lưu ý an toàn
- Baud mismatch KHÔNG hư phần cứng — chỉ làm bus mất giao tiếp
- Sau test phải đổi LẠI 125k trước khi kết thúc lab — nếu để 500k thì lab sau sẽ không chạy được
- Nếu sau khi đổi về 125k mà bus vẫn lỗi → reset STM32 (power cycle)$md$,
    'raw_frames',
    20,
    'Settings → baud 500k → quan sát loạn → đổi về 125k → khôi phục. Lưu ≥ 20 frame.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-05 Step 6 — Length validation test
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    6,
    'Length & type validation test',
    $md$### Mục tiêu
Test cơ chế validation của STM32 firmware — bắt frame sai LEN, sai TYPE phía host trước khi đẩy lên CAN bus.

### Các bước
1. Mở **CanSender** screen
2. Thử case 1 — sai LEN:
   - Compose 1 frame với LEN = 5 nhưng chỉ điền 5 byte data (thay vì 8)
   - Send → STM32 phải trả về **ERROR(0x11) BAD_LENGTH**
   - App tạo 1 active_test evidence với error response
3. Thử case 2 — unknown TYPE:
   - Compose 1 frame với TYPE byte = **0xFF** (không thuộc protocol)
   - Send → STM32 phải trả về **ERROR(0x04) UNKNOWN_TYPE**
   - App tạo active_test evidence thứ 2
4. Submit: 2 active_test entries (mỗi entry tương ứng 1 case lỗi)

### Lưu ý an toàn
- Step này KHÔNG can thiệp phần cứng — hoàn toàn ở tầng protocol phía host ↔ STM32
- Frame sai bị STM32 reject TRƯỚC KHI gửi lên CAN → cluster không bị ảnh hưởng
- Nếu STM32 không trả error → firmware có thể chưa enable validation, báo GV$md$,
    'active_test',
    2,
    'CanSender 2 case: LEN sai (0x11), TYPE 0xFF (0x04). Submit 2 active_test với error response.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-05 Step 7 — Recovery time measurement
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    7,
    'Đo thời gian auto-recovery (BUS-OFF → BUS_RECOVERED)',
    $md$### Mục tiêu
Đo định lượng thời gian STM32 phát hiện BUS-OFF + auto-recover + reload baud, đối chiếu với spec ~100ms.

### Các bước
1. Chuẩn bị đồng hồ điện tử bấm giờ (smartphone stopwatch OK)
2. Đảm bảo bus đang chạy normal
3. Quy trình đo:
   1. Bấm **START** trên đồng hồ
   2. GV tháo 1 termination → bus vào BUS-OFF
   3. Chờ STM32 auto-recovery
   4. GV re-attach termination
   5. Bấm **STOP** khi thấy snackbar **BUS_RECOVERED** trên app
4. Đo total time — target **< 500ms** (spec ~100ms phát hiện + reload baud)
5. Lặp 3 lần, tính trung bình
6. Submit: screenshot đồng hồ (thời gian đo) + screenshot Raw Monitor có chuỗi 4 ERROR frame (0x20 → 0x21 → 0x22 → 0x24)

### Lưu ý an toàn
- Áp dụng lại CẢNH BÁO Step 2: tháo termination < 5 giây, re-attach NGAY
- Nếu recovery time > 1 giây → có thể firmware Phase 7 chưa enable, báo GV
- Sau khi đo xong, đảm bảo bus về trạng thái baseline trước khi tắt thiết bị$md$,
    'screenshot',
    1,
    'Stopwatch + Raw Monitor. Target < 500ms. Submit 2 screenshot: timer + log 4 ERROR frame.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- ── LAB-05 Pre-quiz (6 questions) ──────────────────────────────────────────

-- LAB-05 Pre-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'pre_lab', 1, 'multiple_choice',
    'Khi TEC > 96, MCP2515 vào state nào?',
    '{"A":"Normal","B":"Warning","C":"Passive","D":"BUS-OFF"}'::jsonb,
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

-- LAB-05 Pre-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'pre_lab', 2, 'multiple_choice',
    'Khi TEC = 255, state là?',
    '{"A":"Warning","B":"Passive","C":"BUS-OFF","D":"Sleep"}'::jsonb,
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

-- LAB-05 Pre-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'pre_lab', 3, 'multiple_choice',
    'Termination resistor 120Ω đặt sai (chỉ 1 đầu hoặc cả 2 đầu nối tiếp 60Ω) → hậu quả?',
    '{"A":"OK bình thường","B":"Bus reflection → CRC errors → BUS-OFF","C":"Tăng baud rate","D":"Không có hậu quả"}'::jsonb,
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

-- LAB-05 Pre-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'pre_lab', 4, 'multiple_choice',
    'STM32 Phase 7 firmware xử lý BUS-OFF như thế nào?',
    '{"A":"Ignore","B":"Reset chip toàn bộ","C":"Reset MCP2515, reload baud, return NORMAL","D":"Tăng baud lên"}'::jsonb,
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

-- LAB-05 Pre-Q 5
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'pre_lab', 5, 'multiple_choice',
    'Error code 0x22 từ STM32 nghĩa là gì?',
    '{"A":"Bad frame","B":"BUS-OFF","C":"Unknown type","D":"TX overflow"}'::jsonb,
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

-- LAB-05 Pre-Q 6 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'pre_lab', 6, 'free_text',
    'Khi nào MCP2515 tự reset error counter về 0? Giải thích 1-2 câu.',
    NULL,
    'Sau khi 128 lần x 11-bit recessive bits liên tiếp được nhận thành công trên bus — đây là điều kiện ''bus idle'' để MCP2515 reset BUS-OFF.',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- ── LAB-05 Post-quiz (4 questions) ─────────────────────────────────────────

-- LAB-05 Post-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'post_lab', 1, 'free_text',
    'Sequence các ERROR codes bạn quan sát được khi tháo termination?',
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

-- LAB-05 Post-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'post_lab', 2, 'free_text',
    'Recovery time đo được bao nhiêu ms? So với spec 100ms + reload?',
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

-- LAB-05 Post-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'post_lab', 3, 'free_text',
    'Nếu cluster vào limp mode do BUS-OFF lặp lại nhiều lần, bạn xử lý thế nào?',
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

-- LAB-05 Post-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-05'),
    'post_lab', 4, 'image_upload',
    'Upload screenshot Raw Monitor có chứa cả 4 error frames trong 1 frame (chuỗi 0x20 → 0x21 → 0x22 → 0x24).',
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
--  LAB-06 — Quy trình Chẩn đoán Tổng hợp — Case Study (v2 — CAPSTONE)
--  Vận dụng LAB-01 → LAB-05. GV inject fault, SV diagnose theo 3S/5S workflow.
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-06',
    'Quy trình Chẩn đoán Tổng hợp — Case Study',
    $md$### Mục tiêu học tập (TR4021 LO.1 → LO.6, Ch.4.5) — CAPSTONE
Đây là bài **CAPSTONE** tổng hợp toàn bộ kiến thức LAB-01 đến LAB-05. Sau khi hoàn thành, sinh viên có thể:
1. Vận dụng quy trình chẩn đoán 3S/5S (theo TR4021 Ch.5)
2. Diagnose 3 fault scenarios do GV inject
3. Document root cause + proposed fix + verification
4. Viết PDF report đầy đủ theo template TR4021
5. Trình bày group findings (oral, 5 phút/nhóm)

### Tiền điều kiện
- ✅ Đã hoàn thành LAB-01 đến LAB-05
- ✅ Đã pass tất cả pre-quiz trước đó

### Thiết bị
- Cluster + BCM + STM32 + Android + Web platform
- Multimeter (đo điện áp BCM)
- File JSON config (instructor sẽ inject fault)

### Thời lượng
5 tiết (4h): Pre-quiz 15' → Hands-on 3h (3 case studies + free-form) → Group presentation 30' → Report 30'$md$,
    6, 70, true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();

-- ── LAB-06 Steps (6) ───────────────────────────────────────────────────────

-- LAB-06 Step 1 — Initial bench check
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    1,
    'Initial bench check — baseline trước khi GV inject fault',
    $md$### Mục tiêu
Xác nhận bench hoạt động bình thường **trước khi** GV inject fault — đây là baseline để so sánh và đảm bảo lỗi sau này thực sự do fault, không phải do setup sẵn có vấn đề.

### Các bước
1. Bật cluster + BCM + STM32, kết nối app qua BLE
2. Mở **Raw Monitor** → quan sát traffic ổn định ~30-60 frame/s, không có ERROR frame (TYPE 0x03)
3. Mở **Gauge Control** → kéo thử RPM=2000, Speed=60 → quan sát kim cluster lên đúng
4. Mở **Active Test** → bật/tắt 1 đèn (vd low_beam) → quan sát BCM phản hồi PR (Positive Response)
5. Status bar app: ONLINE (xanh), không toast/snackbar cảnh báo
6. Submit: screenshot Raw Monitor với traffic ổn định + status bar ONLINE rõ ràng

### Lưu ý
- Bước này CHƯA có fault — chỉ là baseline
- Nếu baseline đã lỗi (vd kim không lên, BCM không phản hồi) → báo GV NGAY, không tiếp tục
- Sau khi GV xác nhận baseline OK → mới chuyển sang Step 2 (GV bắt đầu inject fault)$md$,
    'screenshot',
    1,
    'Raw Monitor traffic đều + status bar ONLINE + kim cluster lên đúng khi test. Chưa có fault.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-06 Step 2 — Case Study #1: Kim RPM không lên
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    2,
    'Case Study #1 — Kim RPM không lên (JSON config fault)',
    $md$### Triệu chứng (SV nhìn thấy)
Mở **Gauge Control** → kéo RPM=3000 → kim RPM trên cluster **không lên** (vẫn nằm ở 0 hoặc idle). Speed, fuel, các gauge khác vẫn hoạt động bình thường.

### GV inject (INSTRUCTOR ONLY — SV không thấy phần này)
> Sửa `ford_ranger_dashboard.json`: đổi `gauges.rpm.canId` từ `0x201` (đúng) thành `0x205` (sai). Restart app để load config mới. Các gauge khác giữ nguyên.

### Nhiệm vụ SV (theo 3S workflow)
1. **SENSE**: Mở **Raw Monitor**, filter TX
   - Kéo RPM=3000 → quan sát frame TX vừa gửi
   - Ghi nhận CAN ID frame RPM = **0x205** (in hex)
2. **SOLVE**:
   - Đối chiếu tài liệu Ford Ranger MS-CAN (bảng CAN ID từ LAB-04) → đúng phải là **0x201** cho RPM
   - Hypothesis: file JSON config bị sai canId
3. **SHOW**:
   - Mở file `ford_ranger_dashboard.json` (Settings → Edit Dashboard JSON)
   - Sửa `gauges.rpm.canId` từ `0x205` → `0x201`
   - Restart app (hoặc reload JSON)
   - Verify: kéo RPM=3000 → kim lên đúng vị trí 3000 RPM
4. Submit:
   - ≥ 50 raw frames (gồm cả before fix với 0x205 và after fix với 0x201)
   - 1 screenshot cluster với kim RPM lên đúng (after fix)

### Tiêu chí pass
- Raw frames có đủ 2 giai đoạn (trước & sau fix)
- Screenshot final state: kim RPM ở vị trí 3000 (± 100)
- Diagnostic note trong post-quiz mô tả đúng root cause (JSON canId sai)

### Hints
- Nếu không nhớ CAN ID đúng → xem bảng từ LAB-04 (DTC + Live Data) hoặc spec sheet Ford Ranger
- Nếu sửa JSON xong vẫn không lên → kiểm tra app có reload config chưa (restart hẳn app)
- Đây là lỗi "config-level", không phải "wiring" — không cần chạm phần cứng$md$,
    'raw_frames',
    50,
    'Raw Monitor → ID RPM sai (0x205). Đúng là 0x201 (xem LAB-04 table). Sửa JSON → restart → verify.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-06 Step 3 — Case Study #2: Cluster câm sau 5 phút
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    3,
    'Case Study #2 — Cluster câm sau 5 phút (termination fault)',
    $md$### Triệu chứng (SV nhìn thấy)
Bench chạy bình thường ~5 phút. Đột nhiên cluster "câm" (kim đứng yên, đèn check engine sáng), app spam toast/snackbar liên tục về ERROR. Raw Monitor cho thấy chuỗi 4 ERROR frame xuất hiện liên tiếp.

### GV inject (INSTRUCTOR ONLY — SV không thấy phần này)
> Tháo lỏng (gần như hờ) 1 đầu termination resistor 120Ω ở cluster end. Tiếp xúc kém → sau vài phút bus sẽ vào BUS-OFF do CRC errors tăng dần.

### Nhiệm vụ SV (theo 5S workflow)
1. **SORT — liệt kê hiện tượng**:
   - Cluster câm, kim đứng yên
   - App spam ERROR toast
   - Raw Monitor: chuỗi 4 ERROR frame **(0x20 → 0x21 → 0x22 → 0x24)** lặp lại
2. **SET IN ORDER — priority**:
   - Ưu tiên 1: hiểu các ERROR code (xem LAB-05) → BUS_WARNING → PASSIVE → BUS_OFF → RECOVERED
   - Ưu tiên 2: root cause nào gây CRC errors? (termination, baud mismatch, noise, broken transceiver)
3. **SHINE — clean inspection**:
   - Kiểm tra vật lý từng đoạn dây: connector lỏng? termination resistor cố định chắc chưa?
   - Tìm thấy: 1 termination ở cluster end **bị tháo lỏng** (gần như hờ)
4. **STANDARDIZE — fix theo procedure**:
   - Re-attach termination chắc chắn (2 đầu, mỗi đầu 120Ω song song = 60Ω tổng)
   - Restart STM32 (power cycle) để reset error counters
   - Quan sát Raw Monitor: ERROR frame ngừng xuất hiện, traffic trở lại bình thường
5. **SUSTAIN — verify + document**:
   - Chạy bench 5 phút nữa → không có ERROR frame mới → fault đã được sửa
   - Ghi note: "Termination cluster end bị lỏng, đã re-attach"
6. Submit: ≥ 80 raw frames bao gồm cả 4 ERROR frame (giai đoạn lỗi) + traffic ổn định (sau khi fix)

### Tiêu chí pass
- Log chứa đủ 4 ERROR frame (0x20, 0x21, 0x22, 0x24)
- Log có giai đoạn sau fix (traffic ổn định ≥ 1 phút, không ERROR)
- Diagnostic note trong post-quiz mô tả đúng root cause (termination lỏng)

### Hints
- 4 ERROR frame là chữ ký rất rõ của BUS-OFF event → ôn lại LAB-05
- Termination phải **chắc** ở cả 2 đầu — chỉ cần 1 đầu lỏng đã đủ gây CRC errors
- Nếu re-attach xong vẫn lỗi → kiểm tra cả connector D-Sub/Molex có oxy hóa không$md$,
    'raw_frames',
    80,
    '4 ERROR frame (0x20→0x21→0x22→0x24) = chữ ký BUS-OFF. Kiểm tra termination physical, re-attach.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-06 Step 4 — Case Study #3: Đèn pha không bật được
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    4,
    'Case Study #3 — Đèn pha không bật được (NRC + voltage fault)',
    $md$### Triệu chứng (SV nhìn thấy)
Vào **Active Test** → nhấn nút "High Beam ON" → đèn pha **không sáng**. App hiện error "NRC 0x22 ConditionsNotCorrect". Các đèn khác (low beam, signal) cũng có thể bị tương tự.

### GV inject (INSTRUCTOR ONLY — SV không thấy phần này)
> Hạ điện áp cấp cho BCM xuống 8V (thay vì 12V chuẩn) — bằng cách dùng bench power supply điều chỉnh, hoặc kẹp diode/điện trở nối tiếp đường nguồn. BCM sẽ từ chối lệnh active test với NRC 0x22 (ConditionsNotCorrect — under-voltage).

### Nhiệm vụ SV (theo 3S workflow)
1. **SENSE**:
   - Active Test → nhấn High Beam ON → đèn không sáng
   - Mở Raw Monitor filter "Both" (TX+RX) → quan sát:
     - TX: `0x7A0 06 2F D0 21 03 01 ...` (UDS RoutineControl request)
     - RX: `0x7A8 03 7F 2F 22` ← **Negative Response Code (NRC)!** Byte 0x7F là negative response, 0x22 là sub-code
2. **SOLVE**:
   - Tra cứu NRC table (UDS ISO 14229) → `0x22 = ConditionsNotCorrect`
   - Conditions not correct thường do: under-voltage, sai precondition, over-temp, ignition state
   - Hypothesis #1: điện áp BCM thấp
3. **SHOW**:
   - Dùng multimeter đo điện áp cấp cho BCM tại connector chính
   - Phát hiện: **8V** (thay vì 12V chuẩn)
   - Tìm nguyên nhân: bench power supply để 8V, hoặc kẹp diode trên đường nguồn
   - Khôi phục về 12V → test lại Active Test High Beam → đèn sáng, RX phản hồi `0x7A8 03 71 01 D0 21 ...` (Positive Response)
4. Submit:
   - **2 active_test entries**:
     - Entry 1: before fix — request `0x7A0 06 2F D0 21 ...`, response `0x7A8 03 7F 2F 22` (NRC)
     - Entry 2: after fix — request giống, response `0x7A8 03 71 01 D0 21 ...` (Positive Response)

### Tiêu chí pass
- 2 active_test evidence đủ (1 NRC + 1 Positive Response)
- Diagnostic note đúng root cause (under-voltage 8V → fix 12V)
- Hiểu được NRC 0x22 nghĩa là gì (ConditionsNotCorrect)

### Hints
- NRC luôn nằm trong byte thứ 4 của response, sau prefix `0x7F SID_ECHO`
- NRC 0x22 = một trong các code phổ biến nhất → cần thuộc lòng (xem bảng NRC LAB-03)
- Đây là lỗi "physical-level" (điện áp) — KHÔNG sửa được bằng JSON config
- Nếu không có bench power điều chỉnh → GV có thể chỉ rút bớt 1 đường nguồn parallel để hạ áp$md$,
    'active_test',
    2,
    'NRC 0x22 ConditionsNotCorrect = under-voltage. Đo BCM bằng multimeter → 8V → khôi phục 12V.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-06 Step 5 — Free-form diagnostic
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    5,
    'Free-form diagnostic — GV inject fault tự do (5S workflow)',
    $md$### Mục tiêu
Áp dụng **đầy đủ 5S workflow** vào 1 fault mà GV inject **chưa tiết lộ trước** — đây là bài đánh giá năng lực chẩn đoán độc lập của nhóm.

### GV inject (INSTRUCTOR ONLY)
> Chọn 1 trong các fault sau (đa dạng giữa các nhóm để tránh copy):
> - Baud rate sai (đổi STM32 sang 500k trong khi cluster 125k)
> - Đảo CAN-H ↔ CAN-L (đảo 2 dây tín hiệu)
> - Sửa DID trong JSON từ `0xF187` thành `0xF999` (DID không tồn tại → NRC 0x31 RequestOutOfRange)
> - Tháo SPI MISO giữa STM32 và MCP2515 (UART vẫn OK nhưng không có CAN traffic)
> - Sửa byte 0 của RPM frame trong gauge config (multiplier sai → kim lên gấp đôi)

### Nhiệm vụ SV — áp dụng 5S workflow

#### 1. SORT — Liệt kê hiện tượng
- Quan sát app: status bar màu gì? Có toast nào không? Gauge có lên không?
- Quan sát cluster: kim hoạt động bình thường không? Đèn check engine?
- Liệt kê **tất cả** triệu chứng vào notebook (1 dòng/triệu chứng)

#### 2. SET IN ORDER — Priority
- Sắp xếp triệu chứng theo độ nghiêm trọng: cái nào critical (mất giao tiếp hoàn toàn), cái nào partial (chỉ 1 chức năng), cái nào cosmetic
- Đặt hypothesis ban đầu (ít nhất 3 hypothesis khác nhau)

#### 3. SHINE — Clean inspection
- Kiểm tra vật lý: wiring, connector, termination, power supply
- Kiểm tra config: JSON file, baud setting, BLE pairing
- Dùng **binary search**: chia bench thành 2 nửa, test từng nửa để cô lập vấn đề

#### 4. STANDARDIZE — Procedure
- Khi tìm ra root cause → fix theo quy trình chuẩn (không "thử đại")
- Document từng thay đổi (vd: "Tôi đổi baud từ 500k → 125k tại Settings → Save")

#### 5. SUSTAIN — Verify + document
- Sau khi fix → verify bằng cách reproduce triệu chứng ban đầu (phải không còn lỗi)
- Document đầy đủ: triệu chứng + hypothesis + cách diagnose + fix + verify

### Submit
- ≥ 50 raw frames (gồm cả giai đoạn lỗi và sau fix)
- Diagnostic notebook (chụp ảnh hoặc ghi text trong post-quiz) — phải show được đủ 5 bước S

### Tiêu chí pass
- Diagnose đúng root cause (GV chấm)
- Fix thành công (verify lại không còn triệu chứng)
- Notebook có đầy đủ 5 bước S, rõ ràng, có thể tái sử dụng làm SOP cho lần sau

### Hints
- KHÔNG vội "thử đại" — luôn đặt hypothesis trước khi thay đổi gì
- Nếu bí 30 phút → xin "hint" từ GV (sẽ trừ điểm nhẹ, nhưng tốt hơn là không xong)
- Binary search là kỹ thuật mạnh nhất: nếu không biết lỗi đâu, chia bench làm 2, test mỗi nửa$md$,
    'raw_frames',
    50,
    '5S workflow: Sort → Set in order → Shine → Standardize → Sustain. Đừng "thử đại" — đặt hypothesis trước.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- LAB-06 Step 6 — Group presentation
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    6,
    'Group presentation — trình bày findings (5 phút/nhóm)',
    $md$### Mục tiêu (TR4021 LO.4 — Communication)
Mỗi nhóm chuẩn bị slide và trình bày findings của 4 case studies (Step 2, 3, 4, 5) trước cả lớp. Đây là phần đánh giá kỹ năng truyền đạt — KHÔNG chỉ làm được mà phải **giải thích được**.

### Yêu cầu slide
- **8-12 slide** (theo TR4021 LO.4 guideline)
- Suggested outline:
  1. Title slide (tên nhóm + members + ngày)
  2. Bench setup overview (1 ảnh)
  3. Case Study #1 (Kim RPM) — triệu chứng → diagnose → root cause → fix
  4. Case Study #2 (Cluster câm) — như trên
  5. Case Study #3 (Đèn pha) — như trên
  6. Free-form case — như trên
  7. Lessons learned (3-5 bullet point quan trọng nhất)
  8. **Summary slide** — tổng kết 4 case + recommendation cho future labs
  9-12. Backup slides (raw frames, screenshots, voltage measurement, etc.)

### Trình bày
- Thời gian: **5 phút/nhóm** (4 phút trình bày + 1 phút Q&A)
- Tất cả member trong nhóm phải nói (không 1 người làm hết)
- Ngôn ngữ: tiếng Việt hoặc tiếng Anh đều OK

### Đánh giá
GV chấm theo rubric:
- **Nội dung** (40%): chính xác về root cause, đầy đủ 4 case
- **Cấu trúc** (20%): logic, dễ theo dõi
- **Truyền đạt** (20%): rõ ràng, không đọc slide
- **Q&A** (20%): trả lời được câu hỏi của GV/lớp

### Submit
- 1 screenshot của **summary slide** (slide tổng kết cuối — slide 8 trong outline)
- File PDF/PPTX full deck nộp riêng cho GV (không upload vào hệ thống)

### Hints
- Đừng "đọc" slide — slide chỉ là gợi ý, nói bằng lời của mình
- Practice trước ít nhất 1 lần với đồng đội (timing 5 phút!)
- Q&A thường hỏi về root cause "tại sao bạn lại nghĩ là X" → chuẩn bị reasoning rõ$md$,
    'screenshot',
    1,
    '8-12 slide. 5 phút/nhóm. Submit screenshot summary slide. PDF full deck gửi riêng cho GV.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title          = EXCLUDED.title,
    instruction    = EXCLUDED.instruction,
    evidence_type  = EXCLUDED.evidence_type,
    required_count = EXCLUDED.required_count,
    hint           = EXCLUDED.hint;

-- ── LAB-06 Pre-quiz (8 questions) ──────────────────────────────────────────

-- LAB-06 Pre-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 1, 'multiple_choice',
    '3S là gì?',
    '{"A":"Sort, Set, Shine","B":"Safety, Security, Speed","C":"Sense, Solve, Show","D":"Standardize, Sustain"}'::jsonb,
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

-- LAB-06 Pre-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 2, 'multiple_choice',
    '5S thêm gì so với 3S?',
    '{"A":"Speed, Setup","B":"Standardize, Sustain","C":"Safety, Security","D":"Sensor, System"}'::jsonb,
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

-- LAB-06 Pre-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 3, 'multiple_choice',
    'Bước đầu tiên trong fault tree analysis là gì?',
    '{"A":"Fix the fault","B":"Identify root cause","C":"Define the problem clearly","D":"Test hypothesis"}'::jsonb,
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

-- LAB-06 Pre-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 4, 'multiple_choice',
    'Sau khi fix fault, bước nào BẮT BUỘC?',
    '{"A":"Document","B":"Verify the fix works","C":"Send report","D":"Both A and B"}'::jsonb,
    'D',
    1, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-06 Pre-Q 5
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 5, 'multiple_choice',
    'Khi không biết root cause, kỹ thuật nào hữu ích?',
    '{"A":"Replace random parts","B":"Binary search (isolate half by half)","C":"Throw the device away","D":"Wait"}'::jsonb,
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

-- LAB-06 Pre-Q 6 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 6, 'free_text',
    'Liệt kê 4 công cụ trên app BKDiagnostic giúp diagnose bus issue.',
    NULL,
    'Raw Monitor (TX/RX filter), CanSender, Active Test, Gauge Control, Live Data, Settings (baud config), Bus error log — bất kỳ 4 trong số này.',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-06 Pre-Q 7 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 7, 'free_text',
    'Khi STM32 báo ERROR(0x10 TX_QUEUE_OVR), nguyên nhân khả nghi là gì?',
    NULL,
    'Bus quá đông, UART throughput không đủ — cần giảm rate gửi từ Android hoặc nâng UART baud.',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-06 Pre-Q 8 (free_text)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'pre_lab', 8, 'free_text',
    '1-2 câu giải thích tại sao "đọc DTC" KHÔNG đủ để chẩn đoán đầy đủ?',
    NULL,
    'Vì DTC chỉ là triệu chứng, cần xác định root cause qua live data, schematic, history. Một DTC có thể có nhiều nguyên nhân.',
    2, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- ── LAB-06 Post-quiz (5 questions — reflection) ────────────────────────────

-- LAB-06 Post-Q 1
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'post_lab', 1, 'free_text',
    'Trong 3 case studies, case nào khó nhất? Giải thích.',
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

-- LAB-06 Post-Q 2
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'post_lab', 2, 'image_upload',
    'Vẽ flowchart 5S workflow bạn đã áp dụng (chụp ảnh).',
    NULL,
    NULL,
    3, NULL
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type  = EXCLUDED.question_type,
    question_text  = EXCLUDED.question_text,
    options        = EXCLUDED.options,
    correct_answer = EXCLUDED.correct_answer,
    points         = EXCLUDED.points,
    hint           = EXCLUDED.hint;

-- LAB-06 Post-Q 3
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'post_lab', 3, 'free_text',
    'Nếu xe thực có triệu chứng "kim đồng hồ giật giật mỗi 30s", bạn sẽ check gì đầu tiên?',
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

-- LAB-06 Post-Q 4
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'post_lab', 4, 'free_text',
    'Đề xuất 1 improvement cho app BKDiagnostic giúp diagnose dễ hơn.',
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

-- LAB-06 Post-Q 5
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-06'),
    'post_lab', 5, 'free_text',
    'Self-assessment: bạn đạt LO nào trong 6 LO của TR4021 sau lab này? (checkbox — liệt kê các LO đạt được)',
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
