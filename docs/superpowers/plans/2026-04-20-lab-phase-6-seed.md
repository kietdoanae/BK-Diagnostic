# Lab System — Phase 6 (Seed Pilot Content) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a single idempotent SQL migration (`website/lab_seed.sql`) that seeds the 2 pilot labs — LAB-01 (CAN Bus & OBD2 Fundamentals) and LAB-02 (Active Test & Dashboard Warning) — with their full content: 2 `labs` rows, 9 `lab_steps` rows (5 + 4), and 20 `lab_questions` rows (5 pre + 5 post per lab). Content in Vietnamese markdown.

**Architecture:** One flat SQL file that can be pasted into Supabase SQL Editor and rerun safely. Uses `ON CONFLICT (<unique-key>) DO UPDATE` on every insert so re-execution updates content in place rather than creating duplicates. All `lab_steps` / `lab_questions` resolve their `lab_id` via a `(SELECT id FROM public.labs WHERE code = '…')` subquery — no variables, no DO blocks — which keeps each statement standalone. Multiple-choice `options` use the jsonb **object** form `{"A": "...", "B": "..."}` that the admin `QuestionForm` (`web/src/components/admin/QuestionForm.jsx:80-84`) and the student `PreQuizRunner` (`web/src/components/lab/PreQuizRunner.jsx:66`) both use; `correct_answer` is the letter key, which `submit_pre_quiz` RPC (`website/lab_rpc.sql:175`) compares as a plain text equality.

**Tech Stack:** PostgreSQL 15 (Supabase), pl/pgSQL dollar-quoted strings (`$md$ … $md$`) for multi-line markdown (no quote-escaping hell), jsonb literals for `options`. No app code changes.

**Spec reference:** `docs/superpowers/specs/2026-04-16-lab-system-design.md` — Section 8 (Seed Content), Section 3.1–3.3 (labs / lab_steps / lab_questions schema).

**Known inconsistency (out of scope):** `web/src/components/lab/pdf/PreQuizSection.jsx:8` reads `options` as an **array** `[{key, text}, …]`, which conflicts with the admin-form object shape. The PDF component is broken for any MC question created through the admin UI today — fixing it is a separate task. This plan seeds in the **object** form (matching admin + student quiz runner) so student answering + pre-quiz grading work correctly end-to-end.

---

## File Structure

| File | Responsibility |
|---|---|
| Create: `website/lab_seed.sql` | All seed data for LAB-01 + LAB-02. Begins with a header comment stating execution order ("run AFTER `lab_rpc.sql`, independent of `lab_storage.sql`"). Wrapped in a single `BEGIN` / `COMMIT`. Ends with three read-only verification queries commented out (operator uncomments to spot-check row counts). |

No existing files are modified. Nothing in `app/`, `web/`, or `stm32/` touches this phase.

---

## Task 1: Create `lab_seed.sql` scaffold

**Files:**
- Create: `website/lab_seed.sql`

- [ ] **Step 1: Write the scaffold (header + transaction wrapper + empty sections)**

File contents:

```sql
-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Seed Content (Phase 6)
--  Seeds LAB-01 and LAB-02 with steps + pre-lab / post-lab questions.
--  Order: run AFTER lab_helpers.sql → lab_schema.sql → lab_rls.sql →
--         lab_rpc.sql (storage.sql is independent).
--  Idempotent: every INSERT uses ON CONFLICT DO UPDATE so re-running
--  the file refreshes content without duplicating rows.
--  Spec: docs/superpowers/specs/2026-04-16-lab-system-design.md §8.
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

-- ── LAB-01 row ──────────────────────────────────────────────────────────────
-- (populated in Task 2)

-- ── LAB-01 steps ────────────────────────────────────────────────────────────
-- (populated in Task 3)

-- ── LAB-01 pre-lab questions ────────────────────────────────────────────────
-- (populated in Task 4)

-- ── LAB-01 post-lab questions ───────────────────────────────────────────────
-- (populated in Task 5)

-- ── LAB-02 row ──────────────────────────────────────────────────────────────
-- (populated in Task 6)

-- ── LAB-02 steps ────────────────────────────────────────────────────────────
-- (populated in Task 7)

-- ── LAB-02 pre-lab questions ────────────────────────────────────────────────
-- (populated in Task 8)

-- ── LAB-02 post-lab questions ───────────────────────────────────────────────
-- (populated in Task 9)

COMMIT;

-- ── Verification (read-only; uncomment to run) ──────────────────────────────
-- (populated in Task 10)
```

- [ ] **Step 2: Verify file parses (syntactic smoke test, no DB hit needed)**

Run: `bash -c "grep -c 'BEGIN;' C:/Users/KIET/AndroidStudioProjects/BKDiagnostic/website/lab_seed.sql && grep -c 'COMMIT;' C:/Users/KIET/AndroidStudioProjects/BKDiagnostic/website/lab_seed.sql"`
Expected: `1` then `1`.

- [ ] **Step 3: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): scaffold lab_seed.sql for phase 6"
```

---

## Task 2: Insert LAB-01 `labs` row

**Files:**
- Modify: `website/lab_seed.sql` (replace the `-- ── LAB-01 row ──` placeholder)

- [ ] **Step 1: Replace the LAB-01 row placeholder with an upsert**

Replace the line `-- (populated in Task 2)` under "LAB-01 row" with:

```sql
INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-01',
    'CAN Bus & OBD2 Fundamentals',
    $md$### Mục tiêu học tập
- **L.O.1**: Nắm được quy trình chẩn đoán OBD2 cơ bản (connect → capture → request → decode).
- **L.O.2**: Hiểu cấu trúc CAN frame 11-bit và cách OBD2 đóng gói Mode/PID trên HS-CAN 500 kbps.
- **L.O.3**: Đọc được raw trace và phân loại CAN ID theo tần suất xuất hiện + biến động payload.

### Nội dung
Bài thí nghiệm tập trung vào **HS-CAN 500 kbps** — loại bus chính trên xe du lịch hiện đại. Sinh viên sẽ bắt frame ở 2 trạng thái (IG-ON tĩnh và động cơ idle), kích hoạt tín hiệu bằng chân ga / phanh, và gửi 3 request OBD2 chuẩn (PID 0x05, 0x0C, 0x0D) để đối chiếu response với giá trị vật lý.$md$,
    1,
    70,
    true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-01 labs row"
```

---

## Task 3: Insert LAB-01 `lab_steps` (5 rows)

**Files:**
- Modify: `website/lab_seed.sql` (replace the `-- ── LAB-01 steps ──` placeholder)

Each INSERT resolves `lab_id` via `(SELECT id FROM public.labs WHERE code = 'LAB-01')` so the statements don't depend on a runtime variable. Uniqueness is `(lab_id, step_order)`.

- [ ] **Step 1: Replace the LAB-01 steps placeholder with 5 upserts**

Replace the line `-- (populated in Task 3)` under "LAB-01 steps" with:

```sql
-- Step 1 — IG-ON capture
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    1,
    'IG-ON: capture 50 raw frames',
    $md$### Mục tiêu
Ghi lại **50 frame CAN thô** khi bật IG-ON (chưa nổ máy).

### Các bước
1. Cắm cáp OBD2 vào cổng của xe (bảng taplo, phía tài xế).
2. Bật chìa khóa sang **IG-ON** (chưa khởi động động cơ).
3. Trên app, nhấn **Start Capture** và chờ đến khi bộ đếm đạt **≥ 50 frame**.
4. Nhấn **Submit evidence** để gửi batch lên hệ thống.

### Lưu ý
- **Không** đạp ga / phanh trong bước này.
- Nếu frame rate < 5 frame/s → kiểm tra lại kết nối cáp.$md$,
    'raw_frames',
    50,
    'Nếu app không nhận frame trong 15 s, rút cáp OBD2 ra và cắm lại, sau đó bật/tắt IG-ON lần nữa.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;

-- Step 2 — warmup
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    2,
    'Warmup: engine idle 2 phút',
    $md$### Mục tiêu
Cho động cơ chạy không tải (**idle**) **2 phút** để đạt nhiệt độ hoạt động.

### Các bước
1. Khởi động động cơ.
2. Giữ chân khỏi bàn ga, để máy chạy không tải.
3. Theo dõi đồng hồ tua (RPM) ổn định quanh **700–900 rpm**.
4. Sau 2 phút, nhấn **Next step** trên app.

### Lưu ý
- Bước này **không cần submit evidence** — app tự đánh dấu hoàn thành khi bạn chuyển sang bước sau.$md$,
    'none',
    0,
    NULL
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;

-- Step 3 — accelerator
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    3,
    'Đạp ga 3 lần × 2 s: capture 30 frame',
    $md$### Mục tiêu
Ghi lại các frame CAN liên quan đến **tín hiệu chân ga (APP)** và **tua máy (RPM)**.

### Các bước
1. Đảm bảo động cơ đang idle, phanh tay kéo, cần số **N** hoặc **P**.
2. Trên app nhấn **Start Capture**.
3. Đạp ga **vừa phải** (không kick-down), **giữ 2 giây**, rồi nhả.
4. Lặp lại **3 lần**, mỗi lần cách nhau ~3 giây.
5. Nhấn **Submit evidence** khi đã có **≥ 30 frame**.

### Lưu ý
- **Không** đạp ga sâu / kick-down — không có tải sẽ gây tua vọt gây hại.
- Nếu số frame < 30 sau 3 lần → lặp thêm 1 lần nữa.$md$,
    'raw_frames',
    30,
    'Để ý CAN ID có byte thay đổi mạnh khi bạn đạp ga — đó thường là frame chứa RPM hoặc APP.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;

-- Step 4 — brake
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    4,
    'Đạp phanh 3 lần: capture 20 frame',
    $md$### Mục tiêu
Ghi lại frame CAN tương ứng với **tín hiệu phanh**.

### Các bước
1. Giữ động cơ idle, phanh tay kéo, cần số **N** hoặc **P**.
2. Trên app nhấn **Start Capture**.
3. Đạp phanh **dứt khoát**, giữ **1–2 giây**, rồi nhả.
4. Lặp lại **3 lần**, mỗi lần cách nhau ~3 giây.
5. Nhấn **Submit evidence** khi đã có **≥ 20 frame**.$md$,
    'raw_frames',
    20,
    'Tín hiệu phanh thường là 1 bit on/off — quan sát bit nào thay đổi giữa các lần đạp.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;

-- Step 5 — OBD2 active test
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    5,
    'OBD2 request: PID 0x05 / 0x0C / 0x0D',
    $md$### Mục tiêu
Dùng cơ chế **Active Test** để gửi **3 yêu cầu OBD2** và ghi lại response:

| PID | Ý nghĩa | Đơn vị |
|-----|---------|--------|
| **0x05** | Coolant temperature | °C |
| **0x0C** | Engine RPM | rpm |
| **0x0D** | Vehicle speed | km/h |

### Các bước
1. Mở tab **Active Test** trên app.
2. Chọn **PID 0x05**, nhấn **Send** → chờ response → nhấn **Capture**.
3. Lặp lại cho **PID 0x0C** và **PID 0x0D**.
4. Đảm bảo app hiển thị **3/3** response.
5. Nhấn **Submit evidence**.

### Lưu ý
- Response OBD2 từ ECU chính thường dùng CAN ID **0x7E8**.
- Byte đầu data = PCI (chứa chiều dài dữ liệu).$md$,
    'active_test',
    3,
    'Nếu không thấy response trong 2 s, gửi lại — ECU có thể bận xử lý lệnh khác.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-01 5 steps"
```

---

## Task 4: Insert LAB-01 pre-lab questions (5 MC)

**Files:**
- Modify: `website/lab_seed.sql`

All 5 are `multiple_choice`. `options` is a jsonb object `{"A": "...", "B": "...", …}`; `correct_answer` is the letter string.

- [ ] **Step 1: Replace the LAB-01 pre-lab placeholder with 5 upserts**

Replace the line `-- (populated in Task 4)` under "LAB-01 pre-lab questions" with:

```sql
-- Q1 — HS-CAN speed
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 1, 'multiple_choice',
    'Tốc độ của bus HS-CAN (High-Speed CAN) trên xe du lịch hiện đại là bao nhiêu?',
    '{"A":"125 kbps","B":"250 kbps","C":"500 kbps","D":"1 Mbps"}'::jsonb,
    'C', 1,
    'HS = High Speed; "H" gợi ý tốc độ cao nhất trong CAN 2.0, nhưng không phải 1 Mbps.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q2 — OBD2 functional request CAN ID
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 2, 'multiple_choice',
    'CAN ID chuẩn cho **functional request** trên OBD2 (gửi đến tất cả ECU) là bao nhiêu?',
    '{"A":"0x7DF","B":"0x7E0","C":"0x18DB33F1","D":"0x123"}'::jsonb,
    'A', 1,
    '0x7E0–0x7E7 là *physical* request (gửi riêng cho 1 ECU). Functional là broadcast.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q3 — Request frame byte layout
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 3, 'multiple_choice',
    'Cấu trúc **8 byte data** của một OBD2 Mode 01 request frame (single frame ISO-15765-2) là gì?',
    '{"A":"[PCI=0x02 | Mode=0x01 | PID | 0x00 × 5]","B":"[DLC | Mode | PID | 5 byte data]","C":"[Mode | PID | CRC | 5 byte padding]","D":"[ID | DLC | 8 byte data]"}'::jsonb,
    'A', 1,
    'PCI byte đầu = số byte hữu ích phía sau (ở đây Mode+PID = 2 byte → PCI = 0x02).'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q4 — 11-bit vs 29-bit on passenger cars
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 4, 'multiple_choice',
    'OBD2 trên phần lớn xe du lịch hiện đại dùng khung ID 11-bit (CAN 2.0A) hay 29-bit (CAN 2.0B)?',
    '{"A":"11-bit (CAN 2.0A)","B":"29-bit (CAN 2.0B)","C":"Cả hai đồng thời","D":"Chỉ 29-bit khi tốc độ > 500 kbps"}'::jsonb,
    'A', 1,
    'Xe tải nặng (SAE J1939) mới bắt buộc 29-bit; xe du lịch OBD2 mặc định 11-bit.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q5 — Where is DLC encoded?
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'pre_lab', 5, 'multiple_choice',
    'DLC (Data Length Code) của một CAN frame 8 byte nằm ở đâu?',
    '{"A":"Không nằm trong 8 byte data — DLC là trường riêng trong header của frame","B":"Byte 0 (byte đầu data)","C":"Byte 7 (byte cuối data)","D":"Byte 1"}'::jsonb,
    'A', 1,
    'Đừng nhầm DLC (frame-level) với PCI (byte đầu của single-frame ISO-TP).'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-01 5 pre-lab MC questions"
```

---

## Task 5: Insert LAB-01 post-lab questions (4 free-text + 1 image-upload)

**Files:**
- Modify: `website/lab_seed.sql`

Question order follows the spec exactly: #1–#3 free-text, **#4 image-upload** (flowchart), #5 free-text. `options` and `correct_answer` are `NULL` for free-text and image-upload (auto-credit in `submit_pre_quiz` — but post-lab is graded manually regardless).

- [ ] **Step 1: Replace the LAB-01 post-lab placeholder with 5 upserts**

Replace the line `-- (populated in Task 5)` under "LAB-01 post-lab questions" with:

```sql
-- Q1 — free_text: identify RPM ID
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 1, 'free_text',
    'Dùng evidence của **Bước 3 (đạp ga)** — hãy chỉ ra CAN ID nào mang thông tin RPM. Giải thích cách bạn xác định (biến động byte, tần suất, đối chiếu với PID 0x0C).',
    NULL, NULL, 1,
    'So sánh trace Bước 3 (đạp ga) với trace Bước 2 (idle): ID có payload đổi nhiều khi đạp ga ⇒ ứng viên.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q2 — free_text: decode PID 0x0D response
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 2, 'free_text',
    'Giải mã **từng byte** của 1 response OBD2 PID **0x0D** (vehicle speed) mà nhóm bạn bắt được ở Bước 5. Giải thích ý nghĩa byte PCI, Mode response, PID echo, và byte dữ liệu.',
    NULL, NULL, 1,
    'Response Mode 01 = 0x41 (= 0x40 | mode). Byte data cho 0x0D là **1 byte**, đơn vị km/h trực tiếp.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q3 — free_text: compare frame rate
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 3, 'free_text',
    'So sánh **frame rate (frame/giây)** giữa trạng thái IG-ON (Bước 1) và engine idle (Bước 2). Vì sao có chênh lệch?',
    NULL, NULL, 1,
    'Khi động cơ chạy, nhiều ECU (ECM, TCM, ABS…) phát thêm chu kỳ 10/20/50 ms ⇒ rate tăng mạnh.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q4 — image_upload: OBD2 diagnostic flowchart
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 4, 'image_upload',
    'Vẽ và upload **sơ đồ quy trình chẩn đoán OBD2 cơ bản** (connect → scan DTC → live data → active test → clear → verify). Thể hiện các nhánh quyết định. *Chuẩn đầu ra L.O.1.*',
    NULL, NULL, 2,
    'Dùng công cụ vẽ (draw.io, Figma, giấy + camera) — file PNG/JPG ≤ 5 MB.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q5 — free_text: safety precautions
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-01'),
    'post_lab', 5, 'free_text',
    'Nêu các biện pháp an toàn cần tuân thủ **trước khi cắm thiết bị chẩn đoán vào cổng OBD2**.',
    NULL, NULL, 1,
    'Gợi ý: tắt IG, kiểm tra điện áp cáp, chèn bánh, tránh nguồn cao áp trên xe hybrid/EV.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-01 5 post-lab questions"
```

---

## Task 6: Insert LAB-02 `labs` row

**Files:**
- Modify: `website/lab_seed.sql`

- [ ] **Step 1: Replace the LAB-02 row placeholder with an upsert**

Replace the line `-- (populated in Task 6)` under "LAB-02 row" with:

```sql
INSERT INTO public.labs (code, title, description, order_index, pre_quiz_pass_threshold, is_published)
VALUES (
    'LAB-02',
    'Active Test & Dashboard Warning',
    $md$### Mục tiêu học tập
- **L.O.1**: Hiểu và áp dụng **quy trình Active Test** trong chẩn đoán actuator.
- **L.O.3**: Phân loại **đèn cảnh báo taplo** theo vùng màu (RED / BLUE / YELLOW / GREEN) và ngữ nghĩa (ECE R121).
- **L.O.5**: Thực hành **an toàn 3S/5S** trước khi kích hoạt actuator trên xe thực.

### Nội dung
Sinh viên thao tác với màn hình **Active Test** của app, kích hoạt từng đèn trong 4 vùng của cluster, đối chiếu ảnh thực tế và hoàn tất chuỗi "all-lights ≤ 10 s". Bài tập nhấn mạnh **an toàn** — active test **không bao giờ** được chạy khi xe đang di chuyển hoặc chưa chèn bánh.$md$,
    2,
    70,
    true
)
ON CONFLICT (code) DO UPDATE SET
    title                   = EXCLUDED.title,
    description             = EXCLUDED.description,
    order_index             = EXCLUDED.order_index,
    pre_quiz_pass_threshold = EXCLUDED.pre_quiz_pass_threshold,
    is_published            = EXCLUDED.is_published,
    updated_at              = now();
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-02 labs row"
```

---

## Task 7: Insert LAB-02 `lab_steps` (4 rows)

**Files:**
- Modify: `website/lab_seed.sql`

- [ ] **Step 1: Replace the LAB-02 steps placeholder with 4 upserts**

Replace the line `-- (populated in Task 7)` under "LAB-02 steps" with:

```sql
-- Step 1 — open cluster screen
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    1,
    'Mở Dashboard Cluster: verify 4 vùng hiển thị',
    $md$### Mục tiêu
Kiểm tra màn hình **Active Test → Dashboard Cluster** hiển thị đủ 4 vùng đèn:

| Vùng | Ý nghĩa |
|------|---------|
| **RED** | Nguy hiểm — cấm chạy tiếp |
| **YELLOW** | Cảnh báo — đưa xe đi kiểm tra |
| **GREEN** | Thông tin / hoạt động bình thường |
| **BLUE** | Đèn pha cao / thông báo khác |

### Các bước
1. Kết nối OBD2, xác nhận phiên lab đang **ACTIVE**.
2. Mở tab **Active Test** → chọn **Dashboard Cluster**.
3. Khi toàn bộ cluster hiển thị, chụp ảnh màn hình điện thoại.
4. Nhấn **Upload screenshot** để gửi evidence.$md$,
    'screenshot',
    1,
    'Android: Power + Volume Down để chụp màn hình.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;

-- Step 2 — 4-zone tap
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    2,
    'Kích hoạt 1 đèn / 4 vùng: quan sát nháy',
    $md$### Mục tiêu
Kích hoạt **4 đèn báo** (mỗi vùng RED / BLUE / YELLOW / GREEN chọn **1 đèn bất kỳ**) và ghi lại phản hồi qua Active Test.

### Các bước
1. Ở màn hình Dashboard Cluster, chạm **1 icon** trong vùng **RED** → quan sát đèn nháy trên cluster và trên xe thực → nhấn **Capture**.
2. Lặp cho vùng **BLUE**.
3. Lặp cho vùng **YELLOW**.
4. Lặp cho vùng **GREEN**.
5. Nhấn **Submit evidence** khi đếm đạt **4/4**.

### Lưu ý
- Mỗi lần chạm, app gửi **1 active-test command** qua CAN.
- **Không** chạm lặp cùng 1 icon nhiều lần — ECU có thể rate-limit.$md$,
    'active_test',
    4,
    'Nếu đèn không nháy: xác nhận IG-ON nhưng động cơ TẮT — nhiều ECU chỉ cho phép active-test ở trạng thái này.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;

-- Step 3 — physical photo
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    3,
    'Chụp ảnh thực tế cluster với đèn đang kích hoạt',
    $md$### Mục tiêu
Chụp ảnh **thực tế** của bảng đồng hồ xe khi các đèn cảnh báo đang được bật bởi Active Test (từ Bước 2).

### Các bước
1. Giữ nguyên trạng thái phiên Active Test.
2. Đứng ở vị trí ghế tài, chụp rõ toàn bộ cluster (tốt nhất khi đèn đang sáng / nháy).
3. Nhấn **Upload screenshot** để gửi file ảnh.$md$,
    'screenshot',
    1,
    'Tắt flash để tránh ảnh bị lóa trên mặt đồng hồ.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;

-- Step 4 — all-lights sequence
INSERT INTO public.lab_steps (lab_id, step_order, title, instruction, evidence_type, required_count, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    4,
    'All-lights: 14 active-test trong 10 s',
    $md$### Mục tiêu
Thực hiện **all-lights sequence**: chạm **lần lượt 14 đèn** trong cluster, hoàn tất trong **≤ 10 giây**.

### Các bước
1. Chuẩn bị: 2 thành viên nhóm — 1 người chạm, 1 người bấm giờ.
2. Bấm **Start all-lights** trên app (bộ đếm 10 s bắt đầu).
3. Chạm **lần lượt** 14 icon (thứ tự tự do).
4. Nhấn **Submit evidence** khi đủ 14 active-test.

### Lưu ý
- Nếu quá 10 giây → bộ đếm sẽ reset và phải chạy lại từ đầu.
- **Tuyệt đối không** dùng chế độ này khi xe đang chạy.$md$,
    'active_test',
    14,
    'Phân công trước: ai chạm đèn nào — tiết kiệm thời gian và tránh chồng chéo.'
)
ON CONFLICT (lab_id, step_order) DO UPDATE SET
    title = EXCLUDED.title, instruction = EXCLUDED.instruction,
    evidence_type = EXCLUDED.evidence_type, required_count = EXCLUDED.required_count,
    hint = EXCLUDED.hint;
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-02 4 steps"
```

---

## Task 8: Insert LAB-02 pre-lab questions (5 MC)

**Files:**
- Modify: `website/lab_seed.sql`

- [ ] **Step 1: Replace the LAB-02 pre-lab placeholder with 5 upserts**

Replace the line `-- (populated in Task 8)` under "LAB-02 pre-lab questions" with:

```sql
-- Q1 — Active Test vs Live Data
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 1, 'multiple_choice',
    'Khác biệt giữa **Active Test** và **Live Data** trong chẩn đoán OBD2 là gì?',
    '{"A":"Active Test yêu cầu ECU kích hoạt actuator; Live Data chỉ đọc PID hiện tại","B":"Active Test chỉ đọc dữ liệu; Live Data gửi lệnh","C":"Cả hai giống nhau, khác tên gọi","D":"Active Test dùng CAN 29-bit; Live Data dùng 11-bit"}'::jsonb,
    'A', 1,
    '"Active" = chủ động điều khiển actuator; "Live" = chỉ stream dữ liệu thời gian thực.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q2 — ECE R121 mandatory lights
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 2, 'multiple_choice',
    'Theo **ECE R121**, các đèn cảnh báo nào là bắt buộc trên cluster?',
    '{"A":"Chỉ đèn check-engine","B":"Đèn báo phanh, ABS, đai an toàn, túi khí, check-engine, mức nhiên liệu thấp","C":"Chỉ các đèn liên quan động cơ","D":"Không có đèn nào là bắt buộc"}'::jsonb,
    'B', 1,
    'R121 chuẩn hoá biểu tượng + màu sắc của các đèn an toàn cơ bản.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q3 — when to run Active Test in a diagnostic flow
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 3, 'multiple_choice',
    'Trong quy trình chẩn đoán, khi nào phù hợp để chạy **Active Test**?',
    '{"A":"Ngay khi kết nối OBD2, trước mọi bước khác","B":"Sau khi đọc DTC và xác định sơ bộ hệ thống nghi ngờ, phương tiện ở trạng thái an toàn","C":"Trong lúc động cơ đang tăng tốc","D":"Sau khi xóa DTC, khi chưa khởi động lại xe"}'::jsonb,
    'B', 1,
    'Active Test là bước *khoanh vùng actuator*, chỉ có ý nghĩa **sau** khi đã có DTC + hypothesis.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q4 — risk running while engine is on
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 4, 'multiple_choice',
    'Rủi ro khi chạy Active Test trong lúc **động cơ đang hoạt động** là gì?',
    '{"A":"Không có rủi ro","B":"Có thể gây hành vi bất ngờ của hệ thống (phanh, đèn, quạt), dẫn đến tai nạn","C":"Chỉ làm chậm ECU","D":"Chỉ ảnh hưởng đến điện áp ắc-quy"}'::jsonb,
    'B', 1,
    'Active test = ghi đè lệnh lên actuator — nguy hiểm khi xe đang chuyển động.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q5 — 3S/5S preparation
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'pre_lab', 5, 'multiple_choice',
    'Chuẩn bị **3S/5S workshop** trước khi chạy Active Test bao gồm các bước nào?',
    '{"A":"Không cần gì, cắm OBD2 là đủ","B":"Chèn bánh, kéo phanh tay, tắt thiết bị điện không cần thiết, khu vực làm việc gọn gàng và có khoảng trống quan sát","C":"Chỉ cần khởi động động cơ","D":"Chỉ cần mở cốp xe"}'::jsonb,
    'B', 1,
    '3S/5S: Sàng lọc — Sắp xếp — Sạch sẽ (+ Săn sóc + Sẵn sàng).'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-02 5 pre-lab MC questions"
```

---

## Task 9: Insert LAB-02 post-lab questions (4 free-text + 1 image-upload)

**Files:**
- Modify: `website/lab_seed.sql`

Question order per spec: #1 free, #2 free, **#3 image-upload** (flowchart), #4 free, #5 free.

- [ ] **Step 1: Replace the LAB-02 post-lab placeholder with 5 upserts**

Replace the line `-- (populated in Task 9)` under "LAB-02 post-lab questions" with:

```sql
-- Q1 — 5 warning lights analysis
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 1, 'free_text',
    'Chọn **5 đèn báo** nhóm đã kích hoạt ở Bước 2. Với mỗi đèn, trả lời: (a) Điều kiện thực tế khi nào đèn sáng? (b) Mức độ nghiêm trọng (có thể chạy tiếp hay không?) (c) Hành động chẩn đoán kế tiếp?',
    NULL, NULL, 2,
    'Tham khảo ECE R121 + sổ tay hướng dẫn xe để xác định ngữ nghĩa chính xác.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q2 — Engine-Check vs Oil-Pressure
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 2, 'free_text',
    'Phân biệt **đèn Check-Engine (MIL)** và **đèn Oil-Pressure** về: biểu tượng (hình trực quan), vùng màu, mức độ nguy cấp, và quy trình xử lý.',
    NULL, NULL, 1,
    'Oil-Pressure: tắt máy ngay; Check-Engine: có thể chạy về xưởng nếu không nhấp nháy.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q3 — image_upload: multi-light handling flowchart
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 3, 'image_upload',
    'Vẽ và upload **sơ đồ xử lý khi nhiều đèn cảnh báo sáng đồng thời** (ưu tiên theo vùng RED > YELLOW, quyết định dừng xe / đi tiếp, hướng chẩn đoán). *Chuẩn đầu ra L.O.1.*',
    NULL, NULL, 2,
    'Thể hiện nhánh quyết định: có đèn RED → dừng xe; chỉ YELLOW → đánh giá + đi tiếp có điều kiện.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q4 — 3S/5S safety precautions (L.O.5)
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 4, 'free_text',
    'Liệt kê **3 biện pháp an toàn theo 3S/5S** cần thực hiện trước khi chạy Active Test trên xe thực. Giải thích lý do cho từng biện pháp. *Chuẩn đầu ra L.O.5.*',
    NULL, NULL, 1,
    'Gợi ý: chèn bánh, phanh tay, số N/P, tắt phụ tải, khu vực thoáng để quan sát.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;

-- Q5 — Active Test is not a substitute
INSERT INTO public.lab_questions (lab_id, phase, question_order, question_type, question_text, options, correct_answer, points, hint)
VALUES (
    (SELECT id FROM public.labs WHERE code = 'LAB-02'),
    'post_lab', 5, 'free_text',
    'Vì sao **Active Test** không bao giờ nên được dùng thay thế cho **chẩn đoán actuator** (đo điện áp, đo dòng, kiểm tra cơ khí)?',
    NULL, NULL, 1,
    'Active test chỉ chứng minh ECU có thể gửi lệnh; không phân biệt được lỗi cơ khí vs lỗi mạch điện.'
)
ON CONFLICT (lab_id, phase, question_order) DO UPDATE SET
    question_type = EXCLUDED.question_type, question_text = EXCLUDED.question_text,
    options = EXCLUDED.options, correct_answer = EXCLUDED.correct_answer,
    points = EXCLUDED.points, hint = EXCLUDED.hint;
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): seed LAB-02 5 post-lab questions"
```

---

## Task 10: Add verification queries

**Files:**
- Modify: `website/lab_seed.sql` (replace the `-- ── Verification` placeholder at end of file)

- [ ] **Step 1: Replace the verification placeholder with 3 sanity-check queries**

Replace the line `-- (populated in Task 10)` under "Verification" with:

```sql
-- 1. Row counts per lab (expected: LAB-01 → 5 steps, 10 questions; LAB-02 → 4 steps, 10 questions)
-- SELECT l.code,
--        (SELECT count(*) FROM public.lab_steps     s WHERE s.lab_id = l.id) AS steps,
--        (SELECT count(*) FROM public.lab_questions q WHERE q.lab_id = l.id AND q.phase = 'pre_lab')  AS pre_q,
--        (SELECT count(*) FROM public.lab_questions q WHERE q.lab_id = l.id AND q.phase = 'post_lab') AS post_q
-- FROM   public.labs l
-- WHERE  l.code IN ('LAB-01','LAB-02')
-- ORDER  BY l.code;

-- 2. Each MC pre-lab question must have options + correct_answer and the correct_answer must be a key in options
-- SELECT l.code, q.phase, q.question_order, q.correct_answer,
--        (q.options ? q.correct_answer) AS correct_in_options
-- FROM   public.lab_questions q
-- JOIN   public.labs l ON l.id = q.lab_id
-- WHERE  q.question_type = 'multiple_choice'
-- ORDER  BY l.code, q.phase, q.question_order;

-- 3. Steps summary (expected evidence_type + required_count match §8 of spec)
-- SELECT l.code, s.step_order, s.title, s.evidence_type, s.required_count
-- FROM   public.lab_steps s
-- JOIN   public.labs l ON l.id = s.lab_id
-- WHERE  l.code IN ('LAB-01','LAB-02')
-- ORDER  BY l.code, s.step_order;
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_seed.sql
git commit -m "feat(db/lab): add phase-6 seed verification queries"
```

---

## Task 11: Run the seed in Supabase + verify

**Files:** none (manual DB step)

- [ ] **Step 1: Execute `lab_seed.sql` in the Supabase SQL Editor**

Open Supabase Dashboard → **SQL Editor** → paste the entire contents of `website/lab_seed.sql` → **Run**.

Expected: statement succeeds, no errors. If `ON CONFLICT` complains about a missing unique index, verify that `lab_schema.sql` has been applied (the 3 unique keys used are `labs.code`, `lab_steps(lab_id, step_order)`, `lab_questions(lab_id, phase, question_order)`).

- [ ] **Step 2: Run verification query #1 (row counts)**

In a new SQL Editor tab, paste (no leading `--`):

```sql
SELECT l.code,
       (SELECT count(*) FROM public.lab_steps     s WHERE s.lab_id = l.id) AS steps,
       (SELECT count(*) FROM public.lab_questions q WHERE q.lab_id = l.id AND q.phase = 'pre_lab')  AS pre_q,
       (SELECT count(*) FROM public.lab_questions q WHERE q.lab_id = l.id AND q.phase = 'post_lab') AS post_q
FROM   public.labs l
WHERE  l.code IN ('LAB-01','LAB-02')
ORDER  BY l.code;
```

Expected output:

| code | steps | pre_q | post_q |
|------|-------|-------|--------|
| LAB-01 | 5 | 5 | 5 |
| LAB-02 | 4 | 5 | 5 |

- [ ] **Step 3: Run verification query #2 (MC correctness)**

```sql
SELECT l.code, q.phase, q.question_order, q.correct_answer,
       (q.options ? q.correct_answer) AS correct_in_options
FROM   public.lab_questions q
JOIN   public.labs l ON l.id = q.lab_id
WHERE  q.question_type = 'multiple_choice'
ORDER  BY l.code, q.phase, q.question_order;
```

Expected: 10 rows, **every** `correct_in_options` = `true`. If any row is `false`, the seed has a letter mismatch — fix in `lab_seed.sql`, re-run.

- [ ] **Step 4: Run verification query #3 (evidence types vs spec §8)**

```sql
SELECT l.code, s.step_order, s.title, s.evidence_type, s.required_count
FROM   public.lab_steps s
JOIN   public.labs l ON l.id = s.lab_id
WHERE  l.code IN ('LAB-01','LAB-02')
ORDER  BY l.code, s.step_order;
```

Expected rows (match spec §8.1 and §8.2 exactly):

| code | step_order | evidence_type | required_count |
|------|------------|---------------|----------------|
| LAB-01 | 1 | raw_frames  | 50 |
| LAB-01 | 2 | none        | 0  |
| LAB-01 | 3 | raw_frames  | 30 |
| LAB-01 | 4 | raw_frames  | 20 |
| LAB-01 | 5 | active_test | 3  |
| LAB-02 | 1 | screenshot  | 1  |
| LAB-02 | 2 | active_test | 4  |
| LAB-02 | 3 | screenshot  | 1  |
| LAB-02 | 4 | active_test | 14 |

- [ ] **Step 5: Idempotency check — re-run `lab_seed.sql` once more**

Paste + Run the whole `lab_seed.sql` again. Expected: no error, no duplicate rows. Re-run query #1; counts must still be `(5, 5, 5)` and `(4, 5, 5)`.

- [ ] **Step 6: Smoke-test in the web admin UI**

Open `web/` app → admin → **Labs** tab. Expected: LAB-01 and LAB-02 appear in the list, each has 5 (or 4) steps, and opening their Questions tab shows 5 pre-lab + 5 post-lab questions with correct text, options (for MC), and hints.

- [ ] **Step 7: Final commit (mark phase complete)**

If no changes were needed in Step 2/3/4: skip this step (Tasks 1–10 already landed). If the verification forced a fix:

```bash
git add website/lab_seed.sql
git commit -m "fix(db/lab): correct seed content after verification"
```

---

## Self-Review Notes

- **Spec §8 coverage.** All 9 steps and all 20 questions from §8.1 and §8.2 are represented; `evidence_type` and `required_count` match the `(type, required=N)` annotations literally (Task 11 Step 4 table).
- **Question order quirks.** Spec §8.1 puts the image-upload question at **#4** (not last). Spec §8.2 puts the image-upload at **#3**. Tasks 5 and 9 follow the spec exactly.
- **Options shape.** Jsonb object `{"A":"…","B":"…"}` matches both `QuestionForm` (write path: `web/src/components/admin/QuestionForm.jsx:80-84`) and `PreQuizRunner` (read path: `web/src/components/lab/PreQuizRunner.jsx:66`). `correct_answer` = letter key matches the text-equality check in `submit_pre_quiz` (`website/lab_rpc.sql:175`). The stale PDF-side `PreQuizSection` array-shape mismatch is noted in the plan header; fixing it is a separate task.
- **Idempotency.** Every INSERT uses `ON CONFLICT (<unique-key>) DO UPDATE` against a real unique constraint/index defined in `lab_schema.sql`. Re-running the file updates, never duplicates.
- **No placeholders.** Every question body, option, hint, and instruction is fully written — no TODO / TBD strings remain.
