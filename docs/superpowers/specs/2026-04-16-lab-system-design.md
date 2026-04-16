# Lab System Design — TR4021 Automotive Diagnostics Lab Experiments

**Date:** 2026-04-16
**Course:** TR4021 — Kỹ thuật Chẩn đoán và Bảo dưỡng Ô tô
**Status:** Approved design, ready for implementation planning

---

## 1. Goal

Build an end-to-end lab experiment system inside the existing BKDiagnostic ecosystem (Android app + React web + Supabase + STM32 hardware) that covers 2 pilot experiments mapped to TR4021 course learning outcomes, with individual PDF reports per student for submission to instructors.

### Pilot experiments
1. **LAB-01 — CAN BUS & OBD2 Fundamentals** — students observe CAN frames, capture PID responses, identify message meaning.
2. **LAB-02 — Active Test & Dashboard Warning** — students trigger dashboard warning icons via Active Test, explain real-world implications.

### Target course learning outcomes
- L.O.1 — Draw diagnostic procedures (post-lab: upload hand-drawn or digital flowchart)
- L.O.2 — Perform basic diagnostics on modern cars (practice phase)
- L.O.3 — Teamwork (group-based practice session, shared evidence)
- L.O.5 — Safety & professional ethics (pre-lab safety gate, 3S/5S questions)
- L.O.6 — Safe and correct use of diagnostic equipment (hardware usage guided by steps)

L.O.4 (slide 8-12 pages) is not addressed directly — remains a separate coursework submitted outside this system.

---

## 2. Architecture

### Component responsibilities

| Component | Responsibility |
|---|---|
| **App (Android / Kotlin+Compose)** | Minimal: add Lab Mode screen for entering a 6-digit session code; tag all CAN frame / Active Test uploads with `lab_session_id`. App knows nothing about lab flow. |
| **Website (React + Ant Design)** | Full lab UX: lab list, pre-lab quiz, session dashboard with real-time evidence, post-lab form, PDF preview + download. Admin CRUD for labs/questions/steps/groups, plus submissions review. |
| **Supabase** | Database (10 new tables), Storage (`lab-reports`, `lab-images` buckets), Realtime (evidence live-counter), RPC functions for session lifecycle. |
| **STM32 hardware** | No changes. |

### High-level flow (happy path)

1. Admin creates a lab + steps + questions; creates groups and assigns students by MSSV.
2. Student logs into web, takes pre-lab quiz, must pass threshold.
3. Group leader starts practice → web generates 6-digit code.
4. Group members open app → enter code → Lab Mode active.
5. On web, leader clicks "Start step N" → evidence captured from app during that window gets tagged with step N.
6. When all steps satisfy their required counts, session can be completed.
7. Each member independently fills post-lab form (free text + image upload for flowchart).
8. On submit, web renders PDF client-side via `html2pdf.js`, uploads copy to Storage, and serves download to student.
9. Student submits the PDF to instructor (outside this system — email / LMS).

### Key architectural decisions

- **Web-centric, app as "dumb reader"** — app never knows which step is active; web binds evidence to steps by timestamp window.
- **Session code pairing (6 digits)** — explicit pairing keeps the app usable as a generic CAN reader when not in a lab.
- **Step boundary by timestamp** — web sets `current_step_id` + `step_started_at`; evidence arriving in that window is attributed to that step.
- **PDF rendered client-side** — no server-side PDF stack needed; `html2pdf.js` produces A4 output from an HTML template.
- **Realtime via Supabase Realtime** — web subscribes to `lab_evidence` inserts filtered by `session_id`.
- **Pre-lab quiz as gating mechanism** — no pass, no practice (no code generated).
- **Session auto-expiry** — 3 hours without new evidence → session expires automatically.

---

## 3. Database Schema

All new tables are prefixed `lab_`. Written as DDL sketch (final SQL file will live in `website/` alongside existing `activity_logs.sql` and `export_records.sql`).

### 3.1. `labs`

```sql
CREATE TABLE labs (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text UNIQUE NOT NULL,              -- "LAB-01-CAN-OBD2"
    title           text NOT NULL,
    description     text,                              -- markdown
    order_index     int  NOT NULL DEFAULT 0,
    pre_quiz_pass_threshold int NOT NULL DEFAULT 70,
    is_published    boolean NOT NULL DEFAULT false,
    created_by      uuid REFERENCES auth.users(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
```

### 3.2. `lab_steps`

```sql
CREATE TABLE lab_steps (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id          uuid NOT NULL REFERENCES labs(id) ON DELETE CASCADE,
    step_order      int NOT NULL,
    title           text NOT NULL,
    instruction     text NOT NULL,                     -- markdown
    evidence_type   text NOT NULL CHECK (evidence_type IN ('raw_frames','active_test','screenshot','none')),
    required_count  int DEFAULT 0,
    hint            text,
    UNIQUE(lab_id, step_order)
);
```

### 3.3. `lab_questions`

```sql
CREATE TABLE lab_questions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id          uuid NOT NULL REFERENCES labs(id) ON DELETE CASCADE,
    phase           text NOT NULL CHECK (phase IN ('pre_lab','post_lab')),
    question_order  int NOT NULL,
    question_type   text NOT NULL CHECK (question_type IN ('multiple_choice','free_text','image_upload')),
    question_text   text NOT NULL,
    options         jsonb,                             -- {"A":"...","B":"..."} for MC
    correct_answer  text,                              -- "A" for MC; NULL otherwise
    points          int NOT NULL DEFAULT 1,
    hint            text
);
```

### 3.4. `lab_groups` + `lab_group_members`

```sql
CREATE TABLE lab_groups (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id          uuid NOT NULL REFERENCES labs(id) ON DELETE CASCADE,
    name            text NOT NULL,
    semester        text,
    created_by      uuid REFERENCES auth.users(id),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lab_group_members (
    group_id        uuid NOT NULL REFERENCES lab_groups(id) ON DELETE CASCADE,
    user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role            text DEFAULT 'member' CHECK (role IN ('leader','member')),
    PRIMARY KEY (group_id, user_id)
);
```

**Constraint:** a student may belong to at most one group per lab. Enforced via a BEFORE-INSERT/UPDATE trigger on `lab_group_members` that checks `lab_groups.lab_id` does not already appear for the same `user_id`. Raises on conflict so admin tooling can surface a clear error.

**Leader rules:** exactly one `role='leader'` per group. Enforced via a partial unique index: `CREATE UNIQUE INDEX ON lab_group_members(group_id) WHERE role='leader'`.

### 3.5. `lab_sessions`

```sql
CREATE TABLE lab_sessions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        uuid NOT NULL REFERENCES lab_groups(id),
    lab_id          uuid NOT NULL REFERENCES labs(id),
    session_code    char(6) NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','CANCELLED')),
    current_step_id uuid REFERENCES lab_steps(id),
    step_started_at timestamptz,
    started_by      uuid NOT NULL REFERENCES auth.users(id),
    started_at      timestamptz NOT NULL DEFAULT now(),
    ended_at        timestamptz,
    expires_at      timestamptz NOT NULL DEFAULT (now() + interval '3 hours')
);

CREATE UNIQUE INDEX idx_lab_sessions_code_active
    ON lab_sessions(session_code) WHERE status = 'ACTIVE';
CREATE INDEX idx_lab_sessions_group ON lab_sessions(group_id, started_at DESC);
```

### 3.6. `lab_evidence`

```sql
CREATE TABLE lab_evidence (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id           uuid NOT NULL REFERENCES lab_sessions(id) ON DELETE CASCADE,
    step_id              uuid REFERENCES lab_steps(id),
    submitted_by         uuid NOT NULL REFERENCES auth.users(id),
    evidence_type        text NOT NULL CHECK (evidence_type IN ('raw_frame','active_test','screenshot')),
    payload              jsonb NOT NULL,
    client_timestamp_ms  bigint NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_evidence_session_step ON lab_evidence(session_id, step_id);
CREATE INDEX idx_evidence_created      ON lab_evidence(session_id, created_at DESC);
```

Raw frames are batched by the app into 2-second windows; one row per batch (array of frames inside `payload`).

### 3.7. `lab_pre_quiz_submissions`

```sql
CREATE TABLE lab_pre_quiz_submissions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES auth.users(id),
    lab_id          uuid NOT NULL REFERENCES labs(id),
    answers         jsonb NOT NULL,
    score_percent   numeric(5,2) NOT NULL,
    passed          boolean NOT NULL,
    submitted_at    timestamptz NOT NULL DEFAULT now(),
    attempt_number  int NOT NULL DEFAULT 1
);
CREATE INDEX idx_pre_quiz_user_lab ON lab_pre_quiz_submissions(user_id, lab_id, submitted_at DESC);
```

Retries allowed; web uses the latest attempt to gate practice.

### 3.8. `lab_post_submissions`

```sql
CREATE TABLE lab_post_submissions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES auth.users(id),
    session_id      uuid NOT NULL REFERENCES lab_sessions(id),
    answers         jsonb NOT NULL,
    uploaded_images jsonb DEFAULT '[]',
    is_draft        boolean NOT NULL DEFAULT true,
    teacher_comment text,                              -- admin-only, not on PDF
    submitted_at    timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, session_id)
);
```

### 3.9. `lab_reports`

```sql
CREATE TABLE lab_reports (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid NOT NULL REFERENCES auth.users(id),
    session_id        uuid NOT NULL REFERENCES lab_sessions(id),
    pdf_storage_path  text NOT NULL,
    content_hash      text NOT NULL,                   -- SHA256 for tamper check
    file_size_bytes   bigint,
    generated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, session_id)
);
```

### 3.10. Storage buckets

- `lab-reports` (private) — one PDF per user/session; owner SELECT + admin SELECT all.
- `lab-images` (private) — images uploaded for post-lab answers.

### 3.11. RLS policy summary

| Table | Student | Admin/Moderator |
|---|---|---|
| `labs`, `lab_steps`, `lab_questions` | SELECT (published only) | ALL |
| `lab_groups`, `lab_group_members` | SELECT (own membership only) | ALL |
| `lab_sessions` | SELECT+INSERT of own group's sessions | ALL |
| `lab_evidence` | INSERT only while session ACTIVE and user is group member; SELECT own group | ALL |
| `lab_pre_quiz_submissions` | SELECT+INSERT own | ALL |
| `lab_post_submissions` | SELECT+INSERT+UPDATE own | ALL |
| `lab_reports` | SELECT own | ALL |

### 3.12. RPC functions

1. **`start_lab_session(lab_id uuid) → json`** — verifies caller passed pre-lab + is a group leader; generates unique 6-digit code; inserts row; returns `{session_id, session_code, expires_at}`.
2. **`validate_lab_code(code text) → json`** — app calls on entry; returns `{session_id, lab_id, lab_title, group_name, expires_at}` or error if invalid/expired/user-not-in-group.
3. **`submit_pre_quiz(lab_id uuid, answers jsonb) → json`** — auto-grades multiple choice, computes percentage, inserts submission, returns `{score_percent, passed, attempt_number}`.
4. **`set_current_step(session_id uuid, step_id uuid) → void`** — updates `current_step_id` + `step_started_at`; only session group members may call.
5. **`end_current_step(session_id uuid) → void`** — clears step; web fires on "End step" button.
6. **`expire_old_sessions()`** — scheduled job (pg_cron if available, otherwise a Supabase Edge Function triggered every 10 min) sets status to `EXPIRED` for sessions past `expires_at`.

Evidence→step assignment is done in a Postgres trigger on `lab_evidence` INSERT: if the session's `current_step_id` is not NULL at insert time, the trigger assigns that `step_id` to the new evidence row.

### 3.13. Evidence-type handling clarification

- `raw_frames` — app pushes directly via `LabEvidenceRepository` with batched payload.
- `active_test` — app pushes immediately on each `sendActiveTestCommand`.
- `screenshot` — **uploaded from the web** during the practice phase. On a step with `evidence_type='screenshot'`, the web dashboard shows an upload button. File goes to `lab-images` bucket, then an `INSERT INTO lab_evidence` row is written with `submitted_by = current_user` and `payload.image_path = <storage key>`. App is not involved.
- `none` — marker step with no evidence; student confirms by clicking "Step complete" button on web.

---

## 4. App (Android) Changes

### 4.1. New module: `com.example.bkdiagnostic.lab`

New files:
- `lab/LabModeManager.kt` — singleton holding `StateFlow<LabModeState>` (Active/Inactive); exposes `activate(code)`, `deactivate()`, `currentSessionId`.
- `lab/LabEvidenceRepository.kt` — thin wrapper on Supabase client to INSERT into `lab_evidence`. Handles batching raw frames on 2-second windows.
- `ui/screens/LabModeScreen.kt` — Compose screen: 6-digit input (either one text field with `.digits()` filter or 6 separate boxes), validate button, active state panel.
- `ui/components/LabModeBanner.kt` — persistent banner rendered at top of every screen when `LabModeManager.state == Active`.

### 4.2. Modified files

- `ui/screens/DiagnosticScreen.kt` — add "Lab Mode" card to DiagnosticHub.
- `MainActivity.kt` — wrap root Compose content with `LabModeBanner` overlay bound to `LabModeManager.state`.
- `ui/screens/RawMonitorScreen.kt` (`uploadExportToStorage`) — when Lab Mode active, additionally insert batched frames into `lab_evidence` via `LabEvidenceRepository`.
- `diagnostic/DiagnosticViewModel.kt` (`sendActiveTestCommand`) — when Lab Mode active, insert active-test evidence row.
- `ActivityLogger.kt` — unchanged (continues to log general activity to `activity_logs`).

### 4.3. Lab Mode banner UI

Persistent banner, 32dp tall, yellow-orange background (`#F59E0B`), text white:
`🔬 LAB MODE ACTIVE · LAB-01 · 482913 · Tap to manage`

Tap → returns to `LabModeScreen` where user can see info or exit.

### 4.4. Raw-frame batching

- Each incoming frame goes into an in-memory queue scoped to the current session.
- Every 2 seconds (or 100 frames, whichever first), flush: one INSERT into `lab_evidence` with `payload = {frames: [...]}`.
- Reduces chatter. If queue empty, no write.

### 4.5. Security

- App never SELECTs `lab_evidence` — no read path needed.
- RLS on INSERT: `session_id` must be ACTIVE + not expired + caller is member of the session's group.
- Session code is stored only in memory; cleared on app restart (user must re-enter — intentional, prevents leaving the device in Lab Mode by accident).

---

## 5. Website — Student Flow

### 5.1. New routes

| Path | Purpose |
|---|---|
| `/labs` | List labs assigned (via group membership) to the current user |
| `/labs/:labId` | Lab overview + pre-lab quiz |
| `/labs/:labId/session/:sid` | Active practice dashboard (realtime) |
| `/labs/:labId/session/:sid/post` | Post-lab analysis form |
| `/labs/:labId/session/:sid/report` | PDF preview + download |
| `/my-reports` | History of all PDFs submitted |

### 5.2. Per-lab student state machine

`NOT_ASSIGNED → PRE_LAB_PENDING → (PRE_LAB_FAILED ↺) → PRE_LAB_PASSED → PRACTICE_ACTIVE → PRACTICE_DONE_POST_PENDING → COMPLETED`

The UI on `/labs` shows the current state and appropriate CTA.

**Pre-lab gating rules:**
- Only the **leader** can click "Start practice" (generate session code).
- Each **member must individually pass pre-lab** before they can submit post-lab or generate a PDF. Members who haven't passed can still enter the session code on app (to contribute evidence as group participation) and can view the live practice dashboard on web — but the `Submit` button on post-lab stays disabled with a "Bạn cần hoàn thành pre-lab trước" message.
- If a leader fails pre-lab, admin can either reassign leader role to another member or wait for leader to retry.

### 5.3. Pre-lab quiz runner

One question per screen. Progress bar at top. Prev/Next navigation. Auto-save per question into local state; submit on last page. Backend auto-grades multiple choice via `submit_pre_quiz` RPC. On pass, member sees "Waiting for leader", leader sees "Start practice".

### 5.4. Practice dashboard

Split layout:
- **Left column**: step list (sequential, each showing status: pending / active / done).
- **Right column**: current step detail — markdown instruction, Start/End step buttons, live evidence counter (realtime), sample evidence preview.

Bottom-right persistent modal showing session code + expiry countdown.

When all steps reach `required_count`, a big "Kết thúc thực hành & làm Post-lab" button appears.

### 5.5. Post-lab form

One question per card. Markdown textarea for free-text. File upload (5 MB max, PNG/JPG) for image-upload questions. Auto-save draft every 10s into `lab_post_submissions` with `is_draft=true`. Submit → `is_draft=false`, `submitted_at = now()`.

Inline evidence viewer: a collapsible panel per question letting the student re-examine the CAN data they captured — helpful context for writing analysis.

### 5.6. Report page

On entry, fetch all data for the report → render `<LabReportPdfTemplate>` into a hidden DOM → show preview in an iframe → button to generate+download. On generate:

1. `html2pdf().from(element).outputPdf('blob')` → Blob
2. Upload blob to `lab-reports/{userId}/{sessionId}.pdf`
3. Compute SHA-256 of the Blob content → `content_hash`
4. Insert `lab_reports` row
5. Trigger browser download with correct filename

### 5.7. New components (file names)

```
web/src/pages/LabsListPage.jsx
web/src/pages/LabOverviewPage.jsx
web/src/pages/LabSessionPage.jsx
web/src/pages/LabPostLabPage.jsx
web/src/pages/LabReportPage.jsx
web/src/pages/MyReportsPage.jsx

web/src/components/lab/PreQuizRunner.jsx
web/src/components/lab/SessionCodeDisplay.jsx
web/src/components/lab/StepList.jsx
web/src/components/lab/StepDetail.jsx
web/src/components/lab/EvidenceLiveCounter.jsx
web/src/components/lab/EvidenceInlineViewer.jsx
web/src/components/lab/PostLabQuestion.jsx
web/src/components/lab/LabReportPdfTemplate.jsx

web/src/hooks/useLabSession.js
web/src/hooks/useLiveEvidence.js
web/src/hooks/useLabQuiz.js

web/src/services/labApi.js
```

---

## 6. Website — Admin Flow

### 6.1. Tabs added to `AdminPage.jsx`

- **Labs** — tree/accordion of published+draft labs; CRUD form for lab + nested management of questions (pre+post) and steps. Drag-drop reordering.
- **Groups** — list of groups with member chip display; CRUD form with MSSV autocomplete; CSV bulk import.
- **Sessions** — filterable list (lab / status / date); drill-down shows timeline, per-step evidence breakdown, option to force-end or reset.
- **Submissions** — per-student view: pre-quiz score, post-lab status, PDF download link, teacher_comment editor. Bulk download all PDFs of a lab as ZIP.

### 6.2. New components (file names)

```
web/src/pages/admin/LabsAdminTab.jsx
web/src/pages/admin/GroupsAdminTab.jsx
web/src/pages/admin/SessionsAdminTab.jsx
web/src/pages/admin/SubmissionsAdminTab.jsx

web/src/components/admin/LabForm.jsx
web/src/components/admin/QuestionForm.jsx
web/src/components/admin/QuestionList.jsx
web/src/components/admin/StepForm.jsx
web/src/components/admin/StepList.jsx
web/src/components/admin/GroupForm.jsx
web/src/components/admin/GroupBulkImport.jsx
web/src/components/admin/SessionDetail.jsx
web/src/components/admin/SubmissionDetail.jsx
web/src/components/admin/MarkdownEditor.jsx
```

---

## 7. PDF Report Template

Rendered client-side with `html2pdf.js` from a dedicated `<LabReportPdfTemplate>` component. Approximate length: 5-8 A4 pages.

### 7.1. Content sections

1. **Cover** — HCMUT logo + header, course title, lab info, student name + MSSV, group, semester, practice date, submission timestamp.
2. **Lab objectives & L.O. mapping** — pulled from `labs.description` + a static mapping.
3. **Pre-lab quiz result table** — question text, chosen answer, correct/incorrect, total score + pass/fail verdict.
4. **Practice session summary** — session code, start/end, duration, per-step timing table, top-10 CAN IDs with count+sample decoded value, evidence sample (3-5 frames per step — not a full dump).
5. **Post-lab analysis** — each question + student's answer rendered as markdown; uploaded images embedded.
6. **Declaration & signature block** — signed statement, signature line with printed name + MSSV.
7. **Footer every page** — page X / Y, student name + MSSV, generation timestamp, session hash fragment.

### 7.2. Styling

- Body: Times New Roman 12 pt.
- Tables / code blocks: Arial 10 pt.
- Mostly monochrome; tick/cross icons colored for readability.
- A4 page, 20 mm margins.

### 7.3. Anti-tampering

SHA-256 hash of `{session_id, answers_json, timestamps}` is embedded in the footer and stored in `lab_reports.content_hash`. Instructor can verify by re-running the hash on the stored server-side content if they suspect tampering.

### 7.4. Filename

`TR4021_LAB{NN}_{MSSV}_{HọTên}_{YYYY-MM-DD}.pdf` — Vietnamese diacritics stripped for cross-system compatibility.

Example: `TR4021_LAB01_2210xxxx_NguyenHoangKiet_2026-04-17.pdf`

---

## 8. Seed Content for the 2 Pilot Labs

Content is drafted below and will be seeded via migration. Admin can edit anytime.

### 8.1. LAB-01 — CAN BUS & OBD2 Fundamentals

**Pre-lab (5 MC questions, pass threshold 70%)**
1. HS-CAN bus speed?
2. Standard OBD2 functional request CAN ID?
3. Structure of an OBD2 request frame (byte layout)?
4. 11-bit vs 29-bit CAN ID — which is used for OBD2 on most passenger cars?
5. Which byte of an 8-byte CAN frame encodes DLC?

**Practice steps (5 steps)**
1. Ignition ON, capture 50 raw frames. (`raw_frames`, required=50)
2. Warmup 2 min — engine idle. (`none`, required=0)
3. Press accelerator 3 times, each held 2s; capture frames. (`raw_frames`, required=30)
4. Press brake 3 times; capture. (`raw_frames`, required=20)
5. Send OBD2 requests for PIDs 0x05 (coolant), 0x0C (RPM), 0x0D (speed) via Active Test mechanism. (`active_test`, required=3)

**Post-lab (4 free-text, 1 image-upload)**
1. Using step-3 evidence, identify which CAN ID carries RPM. Justify.
2. Decode a sample OBD2 response for PID 0x0D byte-by-byte.
3. Compare frame rate (frames/second) between ignition-OFF and ignition-ON. Why?
4. (Image upload) Draw and upload a basic OBD2 diagnostic procedure flowchart — fulfills L.O.1.
5. What safety precautions must be taken before plugging into the OBD2 port?

### 8.2. LAB-02 — Active Test & Dashboard Warning

**Pre-lab (5 MC questions, pass threshold 70%)**
1. Difference between Active Test and Live Data?
2. Which dashboard warning lights are mandatory per ECE R121?
3. When in a diagnostic procedure is it appropriate to run Active Test?
4. Risks of running Active Test while engine is running?
5. 3S/5S workshop preparation before Active Test?

**Practice steps (4 steps)**
1. Open Active Test screen; verify dashboard cluster displayed. (`screenshot`, required=1)
2. Tap one icon from each of the 4 zones (RED, BLUE, YELLOW, GREEN); observe blink and capture. (`active_test`, required=4)
3. Take a photo / screenshot of the physical dashboard cluster while warning lights are active. (`screenshot`, required=1)
4. Run "all-lights" sequence (if implemented) — tap every icon within 10s. (`active_test`, required=14)

**Post-lab (4 free-text, 1 image-upload)**
1. Pick 5 warning lights triggered. For each: (a) real-world condition, (b) severity (drive-continuable?), (c) next diagnostic action.
2. Engine-Check vs Oil-Pressure: differentiate visually and procedurally.
3. (Image upload) Flowchart for handling multi-light simultaneous warning — L.O.1.
4. List 3 safety precautions from 3S/5S before running Active Test on a live vehicle — L.O.5.
5. Why should Active Test never be used as a substitute for actuator diagnosis?

---

## 9. Out of Scope

Explicitly not included in this design:
- Auto-grading of post-lab free-text answers (teacher reviews offline via PDF).
- Teacher grading inside the system (grades entered wherever the department currently tracks marks).
- Real-time chat / video between group members.
- Mobile-responsive admin UI (admin usable on desktop only).
- Email / LMS integration for PDF submission.
- L.O.4 (slide report) integration — remains a separate course artifact.

---

## 10. Rollout Strategy

1. **Foundation**: database migrations, RLS, RPC functions, Storage buckets.
2. **App Lab Mode**: new screen + banner + tagging logic; test against seeded lab session.
3. **Web admin CRUD**: labs, questions, steps, groups — allows content seeding via UI.
4. **Web student flow**: lab list → pre-lab → practice dashboard → post-lab → PDF.
5. **PDF template polishing**: style pass, print preview test across Chromium / Firefox.
6. **Seed pilot content** for LAB-01 and LAB-02.
7. **Pilot run**: instructor tests end-to-end with 1-2 test accounts before opening to class.

Each phase ends with a demo-able increment. Individual implementation plans (one per phase) will be written via the writing-plans skill after this spec is approved.
