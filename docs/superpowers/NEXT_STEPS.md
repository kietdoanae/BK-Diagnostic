# Lab System — Next Steps (Phase-by-Phase Prompts)

**Spec:** `docs/superpowers/specs/2026-04-16-lab-system-design.md`

Use one `/clear`-ed session per prompt below. Each phase has 2 prompts: **Plan** (writes an implementation plan) and **Execute** (implements that plan). Run them in order; do not skip phases.

**Workflow for every phase:**
1. `/clear` the current session.
2. Paste the `Plan` prompt for the phase → Claude produces a plan file under `docs/superpowers/plans/`.
3. You review the plan → tell Claude to adjust if needed.
4. `/clear` again.
5. Paste the `Execute` prompt → Claude implements the plan, commits changes.
6. You verify → move on to the next phase.

**Golden rule:** If a session's context fills up mid-phase, `/clear` and paste the `Execute` prompt again — Claude will read the plan file and resume.

---

## Phase 1 — Foundation (DB, RLS, RPC, Storage)

### Plan
```
Đọc docs/superpowers/specs/2026-04-16-lab-system-design.md, đặc biệt
Section 3 (Database Schema) và Section 3.11-3.13 (RLS, RPC, evidence
handling).

Dùng skill superpowers:writing-plans để viết implementation plan cho
Phase 1 (Foundation) của spec. Plan cần cover:

- Tạo SQL migration file(s) trong website/ (theo convention cũ như
  activity_logs.sql, export_records.sql)
- 10 tables: labs, lab_steps, lab_questions, lab_groups,
  lab_group_members (+ constraints), lab_sessions, lab_evidence,
  lab_pre_quiz_submissions, lab_post_submissions, lab_reports
- RLS policies cho mỗi bảng (student vs admin/moderator)
- RPC functions: start_lab_session, validate_lab_code, submit_pre_quiz,
  set_current_step, end_current_step, expire_old_sessions
- Trigger gán step_id cho lab_evidence khi insert
- Storage buckets: lab-reports, lab-images (private, RLS)
- Verification SQL queries để test RLS hoạt động đúng

Ghi plan vào docs/superpowers/plans/YYYY-MM-DD-lab-phase-1-foundation.md
```

### Execute
```
Đọc docs/superpowers/plans/<phase-1-plan-file>.md và thực thi toàn bộ.
Ưu tiên: không phá vỡ schema hiện có (activity_logs, export_records).
Sau khi xong: dump toàn bộ SQL đã tạo thành 1 migration thứ tự,
commit theo style user (no Co-Authored-By Claude).
```

---

## Phase 2 — App Lab Mode

### Plan
```
Đọc docs/superpowers/specs/2026-04-16-lab-system-design.md Section 4
(App Changes). Phase 1 đã xong (DB + RPC sẵn sàng).

Dùng skill superpowers:writing-plans để viết implementation plan cho
Phase 2 (App Android Lab Mode). Plan cần cover:

- Tạo LabModeManager singleton (StateFlow<LabModeState>)
- Tạo LabEvidenceRepository (insert lab_evidence qua Supabase client,
  batch raw frames 2 giây/100 frame)
- Tạo LabModeScreen (Compose) — nhập mã 6 số, validate qua RPC
  validate_lab_code, hiển thị active state
- Tạo LabModeBanner (persistent overlay khi active)
- Sửa DiagnosticScreen: thêm card "Lab Mode" vào Hub
- Sửa MainActivity: wrap root với LabModeBanner
- Sửa RawMonitorScreen.uploadExportToStorage: gắn lab_session_id
- Sửa DiagnosticViewModel.sendActiveTestCommand: push active_test evidence
- Test checklist (manual, không unit test cho Compose)

Ghi plan vào docs/superpowers/plans/YYYY-MM-DD-lab-phase-2-app.md
```

### Execute
```
Đọc docs/superpowers/plans/<phase-2-plan-file>.md và thực thi.
Giữ nguyên logic hiện tại của Raw Monitor và Active Test — chỉ thêm
tagging khi Lab Mode active. App vẫn hoạt động bình thường khi không
trong Lab Mode.
Commit theo style user.
```

---

## Phase 3 — Web Admin CRUD

### Plan
```
Đọc docs/superpowers/specs/2026-04-16-lab-system-design.md Section 6
(Admin Flow).

Dùng skill superpowers:writing-plans để viết implementation plan cho
Phase 3 (Web Admin CRUD). Plan cần cover:

- 4 tab mới trong AdminPage.jsx: Labs, Groups, Sessions, Submissions
- Components dưới web/src/components/admin/ và web/src/pages/admin/
  (xem list trong Section 6.2 của spec)
- CRUD form với Ant Design (giữ nhất quán với UI hiện tại)
- Drag-drop reorder cho questions/steps
- MSSV autocomplete cho thêm thành viên nhóm
- CSV bulk import cho groups
- Bulk download ZIP cho PDFs ở tab Submissions
- Markdown editor component tái sử dụng
- services/labApi.js với mọi API call cần thiết

Ghi plan vào docs/superpowers/plans/YYYY-MM-DD-lab-phase-3-admin.md
```

### Execute
```
Đọc docs/superpowers/plans/<phase-3-plan-file>.md và thực thi.
Giữ style UI giống AdminPage.jsx hiện tại (Ant Design). Không
breakage tab Users và Activity Logs cũ.
Commit theo style user.
```

---

## Phase 4 — Web Student Flow

### Plan
```
Đọc docs/superpowers/specs/2026-04-16-lab-system-design.md Section 5
(Student Flow).

Dùng skill superpowers:writing-plans để viết implementation plan cho
Phase 4 (Web Student Flow). Plan cần cover:

- 6 route mới: /labs, /labs/:id, /labs/:id/session/:sid,
  /labs/:id/session/:sid/post, /labs/:id/session/:sid/report, /my-reports
- Components dưới web/src/components/lab/ và web/src/pages/
  (xem list trong Section 5.7)
- Hooks: useLabSession, useLiveEvidence (Supabase Realtime),
  useLabQuiz
- State machine hiển thị lab status trong /labs
- Pre-lab quiz runner (1 câu/trang, progress bar)
- Practice dashboard split layout (left: step list, right: step detail)
- Realtime evidence counter
- Post-lab form với auto-save draft 10s
- Evidence inline viewer cho post-lab
- Pre-lab gating logic (leader start, member submit)

Ghi plan vào docs/superpowers/plans/YYYY-MM-DD-lab-phase-4-student.md
```

### Execute
```
Đọc docs/superpowers/plans/<phase-4-plan-file>.md và thực thi.
Báo lỗi rõ ràng ở UI (Ant Design Alert) nếu user không được
gán nhóm / chưa pass pre-lab / session hết hạn.
Commit theo style user.
```

---

## Phase 5 — PDF Template

### Plan
```
Đọc docs/superpowers/specs/2026-04-16-lab-system-design.md Section 7
(PDF Report Template).

Dùng skill superpowers:writing-plans để viết implementation plan cho
Phase 5 (PDF Template). Plan cần cover:

- Component LabReportPdfTemplate.jsx với 6 section:
  Cover, Objectives+LO, Pre-quiz result, Practice summary,
  Post-lab analysis, Declaration
- Style: Times New Roman 12pt body, Arial 10pt table, A4 20mm margin
- Data fetching helper (gom mọi data cần cho PDF từ 1 lab_report
  đã submit)
- SHA-256 content hash tính client-side, lưu vào lab_reports
- Filename: TR4021_LAB{NN}_{MSSV}_{HọTên}_{YYYY-MM-DD}.pdf (strip dấu
  tiếng Việt)
- Upload Blob vào lab-reports bucket + insert lab_reports
- Print preview trong iframe
- Test cross-browser (Chromium + Firefox)

Ghi plan vào docs/superpowers/plans/YYYY-MM-DD-lab-phase-5-pdf.md
```

### Execute
```
Đọc docs/superpowers/plans/<phase-5-plan-file>.md và thực thi.
Dùng html2pdf.js hoặc jsPDF — chọn cái cho rendering tốt hơn với
table và ảnh embed base64. Test với 1 session mẫu đã có data.
Commit theo style user.
```

---

## Phase 6 — Seed Pilot Content

### Plan
```
Đọc docs/superpowers/specs/2026-04-16-lab-system-design.md Section 8
(Seed Content).

Dùng skill superpowers:writing-plans để viết implementation plan cho
Phase 6 (Seed Pilot Content). Plan cần cover:

- SQL seed file với 2 lab đầy đủ (LAB-01, LAB-02):
  - labs rows
  - lab_steps rows (5 cho LAB-01, 4 cho LAB-02)
  - lab_questions rows (5 pre + 5 post mỗi lab)
- Options & correct_answer cho MC pre-lab
- Markdown instruction cho mỗi step (VN, rõ ràng)
- Hint cho mỗi question nếu cần

Ghi plan vào docs/superpowers/plans/YYYY-MM-DD-lab-phase-6-seed.md
```

### Execute
```
Đọc docs/superpowers/plans/<phase-6-plan-file>.md và thực thi.
Seed bằng SQL script có thể chạy lại nhiều lần (upsert by code).
Commit theo style user.
```

---

## Phase 7 — Pilot Run

### Plan
```
Đọc toàn bộ spec và các plan đã chạy.

Dùng skill superpowers:writing-plans để viết end-to-end test plan
(Phase 7 Pilot Run). Plan cần cover:

- Tạo test accounts: 1 admin, 5 student với MSSV giả
- Gán 5 student vào 1 group cho LAB-01
- Checklist E2E step-by-step:
  1. Login student → vào /labs → pre-lab quiz → pass
  2. Leader start practice → copy mã
  3. Mở app → Lab Mode → nhập mã → xác nhận active
  4. Raw Monitor capture → check evidence đến web realtime
  5. Active Test fire → check evidence
  6. End practice → submit post-lab cá nhân
  7. Generate PDF → download → verify format + hash
  8. Admin tab Submissions → review PDF
- Regression: verify Raw Monitor, Active Test hoạt động ngoài Lab Mode
- Bug log template

Ghi plan vào docs/superpowers/plans/YYYY-MM-DD-lab-phase-7-pilot.md
```

### Execute
```
Đọc docs/superpowers/plans/<phase-7-plan-file>.md và chạy từng
bước. Ghi mọi lỗi phát hiện được vào bug log. Với mỗi bug tìm được,
đề xuất fix cụ thể (file + dòng + thay đổi) — không tự fix trong
phiên này, để user quyết định priority.
```

---

## Tips

- Mỗi phase ~ 1-2 giờ session với Claude. Không tham nhồi 2 phase vào 1 session.
- Nếu plan viết ra có >10 task, chia nhỏ plan thành 2 file trước khi execute.
- Sau mỗi phase, `git log --oneline -10` để kiểm tra commit sạch.
- Spec là canonical — nếu thấy xung đột giữa plan và spec, spec thắng.
