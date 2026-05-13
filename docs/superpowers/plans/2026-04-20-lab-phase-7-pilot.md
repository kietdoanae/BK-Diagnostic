# Lab System — Phase 7 (Pilot Run / End-to-End Test) Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to run this plan top-to-bottom in a single session. This is a **manual / integration test plan**, not a code-writing plan — every step is a verification action with an expected result. Tick `- [ ]` as you pass each check. On any failure, add a row to the **Bug Log** (Task 13) and continue; do **not** attempt fixes in this session.

**Goal:** Validate the full lab-experiment pipeline built in Phases 1–6 — from account provisioning → pre-lab quiz gating → practice session with realtime CAN evidence streaming from the Android app → post-lab submission → client-side PDF generation + hash-verified upload → admin review — by running LAB-01 end-to-end with **1 admin + 5 student** test accounts in **one group**. Catch regressions by re-verifying that Raw Monitor and Active Test still work identically when the app is **not** in Lab Mode.

**Architecture:** Black-box acceptance test. The system-under-test is the deployed Supabase project + the current `web/` build + the current Android app build from `main`. We provision 6 auth users (1 admin, 5 students MSSV 99990001–99990005), assign all 5 students to one `lab_groups` row targeting LAB-01, then walk through every state transition of the student state machine (`NOT_ASSIGNED → PRE_LAB_PENDING → PRE_LAB_PASSED → PRACTICE_ACTIVE → PRACTICE_DONE_POST_PENDING → COMPLETED`) while tailing `lab_evidence` and Supabase Realtime subscriptions to assert the app ↔ web contract. PDF generation is verified against spec §7 (filename format, SHA-256 match, 5–8 pages A4). All findings go into a structured Bug Log (Task 13).

**Tech Stack:** Existing stack — no new code. Tools needed: Supabase Dashboard (Auth + SQL Editor + Storage), Chromium-based browser + Firefox (for PDF cross-browser check), `web/` dev server (`npm run dev`), Android Studio + a physical or virtual device running a debug build of `app/` against a real CAN source (STM32 gateway) **or** the mock/replay path if no hardware is available, and `sha256sum` CLI.

**Spec references:**
- Full spec: `docs/superpowers/specs/2026-04-16-lab-system-design.md`
- Phase 1 (DB): `docs/superpowers/plans/2026-04-17-lab-phase-1-foundation.md`
- Phase 2 (App): `docs/superpowers/plans/2026-04-17-lab-phase-2-app.md`
- Phase 3 (Admin): `docs/superpowers/plans/2026-04-17-lab-phase-3-admin.md`
- Phase 4 (Student): `docs/superpowers/plans/2026-04-19-lab-phase-4-student.md`
- Phase 5 (PDF): `docs/superpowers/plans/2026-04-20-lab-phase-5-pdf.md`
- Phase 6 (Seed): `docs/superpowers/plans/2026-04-20-lab-phase-6-seed.md`

**Out of scope for this phase:** Fixing bugs. Load testing. Multi-group concurrency. Admin CSV bulk-import. LAB-02. L.O.4 content. Mobile-responsive admin UI.

---

## Preconditions (verify BEFORE running any task)

- [ ] **Phase 1–6 migrations applied** in the target Supabase project. Run this query in the Supabase SQL Editor and confirm all rows return `true`:

  ```sql
  SELECT
    to_regclass('public.labs')                IS NOT NULL AS has_labs,
    to_regclass('public.lab_steps')           IS NOT NULL AS has_steps,
    to_regclass('public.lab_evidence')        IS NOT NULL AS has_evidence,
    to_regclass('public.lab_reports')         IS NOT NULL AS has_reports,
    (SELECT count(*)>=2 FROM public.labs)                   AS seeded,
    (SELECT count(*)=5  FROM public.lab_steps s
       JOIN public.labs l ON l.id=s.lab_id WHERE l.code='LAB-01') AS lab01_steps,
    EXISTS (SELECT 1 FROM pg_proc WHERE proname='start_lab_session')  AS rpc_start,
    EXISTS (SELECT 1 FROM pg_proc WHERE proname='validate_lab_code')  AS rpc_validate,
    EXISTS (SELECT 1 FROM pg_proc WHERE proname='submit_pre_quiz')    AS rpc_quiz,
    EXISTS (SELECT 1 FROM pg_proc WHERE proname='complete_lab_session') AS rpc_complete,
    EXISTS (SELECT 1 FROM storage.buckets WHERE id='lab-reports')     AS bucket_reports,
    EXISTS (SELECT 1 FROM storage.buckets WHERE id='lab-images')      AS bucket_images;
  ```

  Expected: all 12 columns `true`. If any is `false`, stop and re-run the corresponding phase plan.

- [ ] **Web dev server runs clean**. From repo root:
  ```bash
  cd web && npm install && npm run dev
  ```
  Expected: dev server on `http://localhost:5173`. No TypeScript / ESLint errors in the terminal.

- [ ] **Android app builds from `main`**. Open `app/` in Android Studio → Sync Gradle → Build → Make Project. Expected: `BUILD SUCCESSFUL`. Install debug APK on the test device.

- [ ] **CAN data source ready**. Either (a) STM32 gateway connected + OBD2 plugged into a test vehicle, or (b) the replay / mock path used in Phase 2 testing is working. Open Raw Monitor on the app **outside Lab Mode** and confirm frames arrive for ~5 seconds.

If any precondition fails, stop. Do not attempt the pilot.

---

## File Structure (test artifacts only — no production code changes)

| File | Responsibility |
|---|---|
| Create: `docs/superpowers/pilot/phase-7-test-accounts.sql` | One-shot SQL to set profile `role`, `mssv`, `full_name` on the 6 test users **after** they are created via Supabase Dashboard Auth UI. Also provides the cleanup script at the end. |
| Create: `docs/superpowers/pilot/phase-7-bug-log.md` | Append-only bug log populated during Tasks 2–12. Template defined in Task 13. |
| Create: `docs/superpowers/pilot/phase-7-evidence/` | Folder for downloaded PDFs, screenshots, CSV exports of query results — kept out of `git` (add to `.gitignore`). |

No files in `app/`, `web/`, `website/`, or `stm32/` are modified by this plan.

---

## Task 1: Provision 6 test accounts (1 admin + 5 students)

**Files:**
- Create: `docs/superpowers/pilot/phase-7-test-accounts.sql`

The 6 accounts are created through the **Supabase Dashboard → Authentication → Users → Add user** UI (so passwords are real bcrypt hashes + `email_confirm=true` is set). After creation, an SQL script stamps the `profiles` row for each (role, mssv, full_name) — `profiles` rows are auto-created by the existing `handle_new_user` trigger but MSSV/full_name need to be set manually for these synthetic test users.

**Account matrix (record this in the bug-log header too):**

| # | Email | Username | Password | Role | MSSV | Full name |
|---|---|---|---|---|---|---|
| 1 | `pilot.admin@bk.test` | `pilot_admin` | `PilotAdmin!2026` | admin | `ADMIN001` | `Pilot Admin` |
| 2 | `pilot.lead@bk.test` | `pilot_s1` | `PilotStudent!2026` | student | `99990001` | `Nguyễn Văn An` (leader) |
| 3 | `pilot.s2@bk.test` | `pilot_s2` | `PilotStudent!2026` | student | `99990002` | `Trần Thị Bảo` |
| 4 | `pilot.s3@bk.test` | `pilot_s3` | `PilotStudent!2026` | student | `99990003` | `Lê Minh Cường` |
| 5 | `pilot.s4@bk.test` | `pilot_s4` | `PilotStudent!2026` | student | `99990004` | `Phạm Thu Dung` |
| 6 | `pilot.s5@bk.test` | `pilot_s5` | `PilotStudent!2026` | student | `99990005` | `Võ Hoàng Em` |

- [ ] **Step 1: Create the 6 users via Supabase Dashboard**

For each row in the matrix:
1. Dashboard → Authentication → Users → **Add user** → **Create new user**.
2. Email = the email in the matrix. Password = the password in the matrix.
3. Check **Auto Confirm User** (skips email verification).
4. Click **Create user**.

Expected: 6 rows in `auth.users`, 6 matching rows in `public.profiles` created by the sign-up trigger (with `username` defaulting to the email local-part or NULL — the SQL in Step 2 overwrites it).

- [ ] **Step 2: Write the profile-stamping SQL**

Create file `docs/superpowers/pilot/phase-7-test-accounts.sql`:

```sql
-- ════════════════════════════════════════════════════════════════════════════
-- Phase 7 pilot — stamp profile fields (role / mssv / full_name / username)
-- Run AFTER the 6 users are created via Supabase Dashboard.
-- Idempotent: re-run is safe (all UPDATEs are keyed by email via auth.users).
-- ════════════════════════════════════════════════════════════════════════════

-- Helper inline CTE pattern: look up the auth user id by email, then upsert
-- the profile fields. No DO blocks, so this is safe to paste into SQL Editor.

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.admin@bk.test')
UPDATE public.profiles SET
    username='pilot_admin', full_name='Pilot Admin', mssv='ADMIN001', role='admin'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.lead@bk.test')
UPDATE public.profiles SET
    username='pilot_s1', full_name='Nguyễn Văn An', mssv='99990001', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s2@bk.test')
UPDATE public.profiles SET
    username='pilot_s2', full_name='Trần Thị Bảo', mssv='99990002', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s3@bk.test')
UPDATE public.profiles SET
    username='pilot_s3', full_name='Lê Minh Cường', mssv='99990003', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s4@bk.test')
UPDATE public.profiles SET
    username='pilot_s4', full_name='Phạm Thu Dung', mssv='99990004', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s5@bk.test')
UPDATE public.profiles SET
    username='pilot_s5', full_name='Võ Hoàng Em', mssv='99990005', role='student'
WHERE id = (SELECT id FROM u);

-- ── Verification ─────────────────────────────────────────────────────────────
-- SELECT u.email, p.username, p.full_name, p.mssv, p.role
-- FROM   auth.users u JOIN public.profiles p ON p.id = u.id
-- WHERE  u.email LIKE 'pilot.%@bk.test'
-- ORDER  BY u.email;

-- ── Cleanup (run at end of pilot — Task 14) ──────────────────────────────────
-- DELETE FROM auth.users WHERE email LIKE 'pilot.%@bk.test';
-- (CASCADE removes profiles + group memberships + sessions + evidence.)
```

- [ ] **Step 3: Run the SQL + verify**

Paste file contents into Supabase SQL Editor → Run. Then uncomment the `-- Verification` block and run it.

Expected: 6 rows returned, `role` = `admin` for `pilot.admin@bk.test` and `student` for the other 5, all `mssv` populated, all `full_name` populated. If any row is missing → re-check Step 1 (user may not have been created).

- [ ] **Step 4: Sanity — username login works**

Log into `http://localhost:5173/login` with username `pilot_admin` / password `PilotAdmin!2026`. Expected: redirect to `/dashboard` with the admin header visible. Log out.

- [ ] **Step 5: Commit the test-accounts SQL**

```bash
git add docs/superpowers/pilot/phase-7-test-accounts.sql
git commit -m "chore(pilot): phase-7 test account stamping script"
```

---

## Task 2: Admin assigns 5 students to one LAB-01 group

**Files:** none — UI-only action in the web admin.

- [ ] **Step 1: Log in as `pilot_admin` and open Groups tab**

URL: `http://localhost:5173/login` → `pilot_admin` / `PilotAdmin!2026` → navigate to `/admin` → click the **👥 Groups** tab.

Expected: `GroupsAdminTab.jsx` loads; existing groups (if any) appear in the left list.

- [ ] **Step 2: Create the pilot group for LAB-01**

Click **New group**. In the modal:
- **Name:** `Pilot-LAB01-Group-A`
- **Semester:** `HK2-2025-2026`
- **Lab:** select `LAB-01 — CAN Bus & OBD2 Fundamentals` from the dropdown.

Save. Expected: group appears in the list. `lab_groups` row created.

- [ ] **Step 3: Add the 5 student members with MSSV autocomplete**

Select the new group → **Add member**. For each MSSV `99990001…99990005`:
1. Type MSSV in the autocomplete box.
2. Select the matching suggestion (full name + username should render).
3. **Role:** set `99990001` (Nguyễn Văn An) to `leader`; set the others to `member`.
4. Click **Add**.

Expected: 5 `lab_group_members` rows; exactly one has `role='leader'`.

- [ ] **Step 4: Verify DB state**

In SQL Editor:

```sql
SELECT g.name, gm.role, p.mssv, p.full_name
FROM   public.lab_group_members gm
JOIN   public.lab_groups g ON g.id = gm.group_id
JOIN   public.profiles p    ON p.id = gm.user_id
WHERE  g.name = 'Pilot-LAB01-Group-A'
ORDER  BY gm.role, p.mssv;
```

Expected: 5 rows. 1 `leader` (MSSV 99990001), 4 `member` (99990002–99990005).

- [ ] **Step 5: Attempt to add the leader to a SECOND LAB-01 group — must fail**

Create another temporary group `Pilot-LAB01-Group-B` for LAB-01. Try to add MSSV `99990001`. Expected: UI shows an error like `"User already in another group for this lab"` (from the trigger in spec §3.4). Delete the temp group afterward.

If the add succeeds silently → **log a bug** (trigger missing or broken).

---

## Task 3: Each student takes the pre-lab quiz

**Files:** none. For each student, use an **incognito window** to avoid session bleed.

Execute Steps 1–3 below sequentially for each of the 5 students `pilot_s1 … pilot_s5`.

- [ ] **Step 1: `pilot_s2` — intentional FAIL first attempt, then PASS retry**

1. Incognito → `/login` → `pilot_s2` / `PilotStudent!2026`.
2. Navigate to `/labs`. Expected: LAB-01 card visible with status `PRE_LAB_PENDING`.
3. Click into LAB-01 → click **Start pre-lab quiz**.
4. **Answer all 5 questions incorrectly on purpose.** Submit.
5. Expected: result screen shows `score_percent < 70`, `passed = false`, and "Retry" button appears.
6. Retry → answer all correctly (use §8.1 of the spec — C / A / A / A / A) → Submit.
7. Expected: `score_percent = 100`, `passed = true`, status advances to `PRE_LAB_PASSED`.

DB verification in SQL Editor:

```sql
SELECT attempt_number, score_percent, passed, submitted_at
FROM   public.lab_pre_quiz_submissions q
JOIN   public.profiles p ON p.id = q.user_id
JOIN   public.labs l     ON l.id = q.lab_id
WHERE  p.mssv = '99990002' AND l.code = 'LAB-01'
ORDER  BY q.submitted_at;
```

Expected: 2 rows. Row 1 has `passed=false`, row 2 has `passed=true`, `attempt_number` = 2 on the second row.

- [ ] **Step 2: `pilot_s1` (leader), `pilot_s3`, `pilot_s4` — each PASS on first attempt**

Repeat the login+quiz flow for each; answer C / A / A / A / A. Expected: each advances to `PRE_LAB_PASSED` on the first attempt.

- [ ] **Step 3: `pilot_s5` — does NOT take the quiz yet** (intentional negative case — used in Task 8)

Log in as `pilot_s5`, navigate to `/labs`, confirm the card shows `PRE_LAB_PENDING`, then log out without taking the quiz.

- [ ] **Step 4: Final DB check**

```sql
SELECT p.mssv, p.full_name,
       (SELECT bool_or(passed) FROM public.lab_pre_quiz_submissions q
        WHERE q.user_id = p.id AND q.lab_id = (SELECT id FROM public.labs WHERE code='LAB-01')) AS passed_prelab
FROM   public.profiles p
WHERE  p.mssv IN ('99990001','99990002','99990003','99990004','99990005')
ORDER  BY p.mssv;
```

Expected: `99990001..99990004` → `passed_prelab = true`; `99990005` → `NULL` (no attempts).

---

## Task 4: Leader starts the practice session

**Files:** none.

- [ ] **Step 1: As `pilot_s1`, click "Start practice"**

Incognito → `/login` → `pilot_s1` / `PilotStudent!2026` → `/labs` → LAB-01 → **Start practice** button (only visible because pilot_s1 is `leader` AND has passed pre-lab).

Expected: a modal or panel appears displaying a **6-digit numeric code** and an expiry countdown (3 hours per spec §3.5). Navigation lands on `/labs/<labId>/session/<sid>`.

- [ ] **Step 2: Copy the session code** — write it into the bug-log header (Task 13). Call it `SESSION_CODE` below.

- [ ] **Step 3: Verify session row**

```sql
SELECT session_code, status, started_at, expires_at, current_step_id
FROM   public.lab_sessions
WHERE  session_code = '<SESSION_CODE>';
```

Expected: 1 row. `status='ACTIVE'`. `expires_at` ≈ `started_at + 3 hours`. `current_step_id IS NULL` (no step started yet).

- [ ] **Step 4: Non-leader cannot start a second session for the same lab**

In a different incognito window, log in as `pilot_s2` and navigate to `/labs/<labId>`. Expected: UI does NOT show a "Start practice" button for pilot_s2 (only a "Join the leader's session" / "Waiting for leader" affordance). If a second session can be created → **log a bug**.

---

## Task 5: App Lab Mode activation

**Files:** none.

- [ ] **Step 1: Open the app → Diagnostic Hub → Lab Mode card**

On the test device, launch the BK Diagnostic app (logged in as `pilot_s1` or any other of the 5 students — the app login is independent of the web login). Navigate **Diagnostic Hub → "Lab Mode"** card.

Expected: `LabModeScreen.kt` renders a 6-digit input field with a **Validate** button. No banner yet.

- [ ] **Step 2: Enter an INVALID code first**

Type `000000` → **Validate**. Expected: in-screen error like `"Mã không hợp lệ hoặc đã hết hạn"`. No banner shown. No row inserted anywhere.

- [ ] **Step 3: Enter the real `SESSION_CODE`**

Type `<SESSION_CODE>` → **Validate**. Expected:
- RPC `validate_lab_code(code)` returns `{session_id, lab_id, lab_title:'CAN Bus & OBD2 Fundamentals', group_name:'Pilot-LAB01-Group-A', expires_at}`.
- The screen switches to the **Active** panel showing lab name + session code + group name + exit button.
- The 32dp yellow-orange banner appears at the top of the app and persists as you navigate away (Diagnostic Hub, Raw Monitor, Active Test screens).

Banner text must match spec §4.3: `🔬 LAB MODE ACTIVE · <LAB TITLE> · <CODE> · Tap to manage`.

- [ ] **Step 4: Banner tap returns to LabModeScreen**

From Raw Monitor, tap the banner. Expected: navigation pops back to `LabModeScreen`; state still `Active`.

- [ ] **Step 5: Attempt to activate for a user NOT in the group**

Stop here (no second phone needed) — defer to a secondary run if a second device is available. Otherwise skip, but **log as `Deferred`** in the bug log.

- [ ] **Step 6: Kill + relaunch app — session code must NOT persist**

Swipe away the app from recent-apps (force kill), relaunch, open Lab Mode. Expected: the banner is **gone** and LabModeScreen shows the empty 6-digit input (per spec §4.5 — code cleared on restart).

Re-enter `<SESSION_CODE>` to re-activate before proceeding to Task 6.

---

## Task 6: Raw Monitor capture → evidence streams to web in realtime

**Files:** none.

Open **two** browser windows:
1. **Leader dashboard:** incognito logged in as `pilot_s1` at `/labs/<labId>/session/<sid>`.
2. **Side DB viewer:** Supabase SQL Editor open to this live query you will re-run manually:
   ```sql
   SELECT evidence_type, step_id, created_at, jsonb_array_length(payload->'frames') AS n_frames
   FROM   public.lab_evidence
   WHERE  session_id = '<SESSION_ID>'
   ORDER  BY created_at DESC
   LIMIT  20;
   ```

- [ ] **Step 1: Leader clicks "Start Step 1" (IG-ON capture)**

On the dashboard, click **Start step 1**. Expected: `lab_sessions.current_step_id` updates to Step 1's id; the step header on the right column shows `ACTIVE`; the step-1 evidence counter reads `0 / 50`.

Verify:
```sql
SELECT current_step_id, step_started_at FROM public.lab_sessions WHERE id = '<SESSION_ID>';
```
Expected: `current_step_id` NOT NULL; `step_started_at` = recent `now()`.

- [ ] **Step 2: On app, run Raw Monitor capture while Lab Mode active**

Switch to the app → Raw Monitor → **Start Capture**. Let it run ≥ 10 seconds while CAN frames arrive. Then **Stop** and **Submit evidence** (or the flow described in the Phase-2 plan: frames are batched every 2 s / 100 frames and pushed to `lab_evidence`).

Expected: multiple `lab_evidence` rows appear with `evidence_type = 'raw_frame'`, each payload containing an array of frames. Each row's `step_id` MUST equal the Step-1 id (assigned by the evidence→step trigger from spec §3.12).

Re-run the SQL viewer query. Expected: at least 5 rows with `evidence_type='raw_frame'` and `n_frames` between 1 and 100 per row.

- [ ] **Step 3: Realtime counter on web updates live**

Watch the dashboard in the leader window while the app is still streaming. Expected: the `EvidenceLiveCounter` number increments in near-real-time (≤ 2 s lag) as new rows arrive. If it requires a manual refresh → **log a bug** (realtime subscription broken).

- [ ] **Step 4: Stop at ≥ 50 frames total, click "End step 1"**

Once the counter shows `≥ 50 / 50`, click **End step 1**. Expected: `current_step_id` clears to NULL; subsequent frames from the app (if any slip through during a small handover window) get `step_id = NULL`.

- [ ] **Step 5: Step 2 (warmup, `none`) — no evidence needed**

Leader clicks **Start step 2** → wait ~10 s (do NOT capture) → click **Step complete / End step 2**. Expected: dashboard auto-marks step 2 as done; no evidence rows required. Step 3 becomes available.

- [ ] **Step 6: Steps 3 and 4 (accelerator + brake) — repeat capture flow**

Same pattern as Step 1 of this task: leader starts step → app streams raw frames via Raw Monitor → leader ends step once counter hits required count (30 for step 3, 20 for step 4).

---

## Task 7: Active Test fire → evidence streams

**Files:** none.

- [ ] **Step 1: Leader starts Step 5 ("OBD2 request: PID 0x05 / 0x0C / 0x0D")**

Click **Start step 5**. Expected: right column shows the markdown instructions; counter `0 / 3`.

- [ ] **Step 2: On app, open Active Test screen while Lab Mode active**

Navigate to the Active Test screen in the app. Expected: banner still visible.

- [ ] **Step 3: Send PID 0x05, 0x0C, 0x0D each**

For each PID:
1. Select PID on app.
2. Tap **Send**.
3. Confirm an OBD2 response is received (per normal Active Test behavior).

Expected: each tap causes `DiagnosticViewModel.sendActiveTestCommand` to insert one row into `lab_evidence` with `evidence_type = 'active_test'`, `step_id = <step-5-id>`, and `payload` including PID + response bytes.

Verify:
```sql
SELECT evidence_type, payload->>'pid' AS pid, created_at
FROM   public.lab_evidence
WHERE  session_id = '<SESSION_ID>' AND evidence_type = 'active_test'
ORDER  BY created_at;
```
Expected: 3 rows, PIDs `0x05 / 0x0C / 0x0D` in order (or equivalent hex string format).

- [ ] **Step 4: Counter reaches 3/3 on dashboard → leader ends step 5**

Expected: dashboard shows all 5 steps as DONE. A big **"Kết thúc thực hành & làm Post-lab"** button appears.

- [ ] **Step 5: Leader clicks "Kết thúc thực hành"**

Expected: `complete_lab_session` RPC runs. `lab_sessions.status` → `COMPLETED`, `ended_at` populated. Members are redirected / offered a "Take post-lab" CTA.

```sql
SELECT status, ended_at FROM public.lab_sessions WHERE id = '<SESSION_ID>';
```
Expected: `status='COMPLETED'`, `ended_at IS NOT NULL`.

---

## Task 8: Per-student post-lab submission (3 passing students + 1 blocked student)

**Files:** none.

Each student submits individually. Use incognito windows.

- [ ] **Step 1: `pilot_s1` (leader) — fill post-lab, save draft, then submit**

Log in → `/labs/<labId>/session/<sid>/post` → see 5 post-lab questions (4 free-text, 1 image-upload).

1. Fill Q1 (free-text) — ≥ 200 chars. Wait 10–15 s. Expected: a "Đã lưu nháp" toast / indicator fires. Confirm in DB:
   ```sql
   SELECT is_draft, jsonb_pretty(answers) FROM public.lab_post_submissions
   WHERE session_id='<SESSION_ID>' AND user_id=(SELECT id FROM public.profiles WHERE mssv='99990001');
   ```
   Expected: `is_draft=true`, Q1 present in `answers`.

2. Fill Q2, Q3 (free-text), Q5 (free-text).
3. Q4 (image_upload): upload a small PNG (≤ 5 MB) of a hand-drawn flowchart.
4. Click **Submit**.

Expected: `is_draft` flips to `false`, `submitted_at` set. Page advances to report screen.

- [ ] **Step 2: `pilot_s2`, `pilot_s3`, `pilot_s4` — each submits**

Repeat for the 3 other pre-lab-passed students. Each writes their own answers.

- [ ] **Step 3: `pilot_s5` (didn't pass pre-lab) — Submit button DISABLED**

Log in as `pilot_s5` → navigate to `/labs/<labId>/session/<sid>/post`. Expected: either the page shows an Alert `"Bạn cần hoàn thành pre-lab trước"` (per spec §5.2) with the Submit button disabled, **or** the route redirects back to the lab overview. If pilot_s5 can submit a post-lab without passing pre-lab → **log a bug**.

- [ ] **Step 4: Verify submissions**

```sql
SELECT p.mssv, s.is_draft, s.submitted_at, jsonb_array_length(s.uploaded_images) AS imgs
FROM   public.lab_post_submissions s
JOIN   public.profiles p ON p.id = s.user_id
WHERE  s.session_id = '<SESSION_ID>'
ORDER  BY p.mssv;
```
Expected: 4 rows (MSSV 99990001–99990004), all `is_draft=false`, all `submitted_at IS NOT NULL`, each `imgs = 1`. No row for 99990005.

---

## Task 9: Generate PDF → download → verify format + hash

**Files:** save every downloaded PDF under `docs/superpowers/pilot/phase-7-evidence/pdfs/`.

Repeat this task once for each of the 4 submitting students.

- [ ] **Step 1: As `pilot_s1`, navigate to the report page**

URL: `/labs/<labId>/session/<sid>/report`. Expected: `LabReportPage.jsx` renders:
- A preview iframe showing the templated report.
- A **"Tạo PDF"** / **"Download PDF"** button.

- [ ] **Step 2: Click generate**

Expected flow (from Phase-5 plan):
1. `html2pdf.js` renders the template to a Blob.
2. SHA-256 computed client-side.
3. Blob uploaded to `lab-reports/<userId>/<sessionId>.pdf`.
4. `lab_reports` row inserted.
5. Browser download fires.

Expected filename (spec §7.4): `TR4021_LAB01_99990001_NguyenVanAn_2026-04-20.pdf` (Vietnamese diacritics stripped; today's date).

- [ ] **Step 3: Open the PDF — visual format check**

Open in both Chromium (Chrome/Edge) and Firefox. Confirm:

| Criterion | Pass condition |
|---|---|
| Page count | 5 – 8 A4 pages |
| Page size | A4 (portrait) |
| Body font | Times New Roman 12 pt |
| Table / code font | Arial 10 pt |
| Margins | ~20 mm on all sides |
| Section 1 Cover | HCMUT header, course, lab title, student full name + MSSV + group + date |
| Section 3 Pre-quiz table | 5 rows, shows correct/incorrect marks, final score |
| Section 4 Practice summary | Session code, start/end timestamps, per-step table, top-10 CAN IDs table, sample frames |
| Section 5 Post-lab answers | All 4 free-text answers rendered; 1 uploaded image embedded |
| Section 6 Declaration | Signature block with printed name + MSSV |
| Footer (every page) | `page X / Y`, student name + MSSV, generation timestamp, hash fragment |

Any discrepancy → **log a bug** with a screenshot of the offending page.

- [ ] **Step 4: Hash integrity check**

Download the PDF. From a bash-capable shell:

```bash
sha256sum "TR4021_LAB01_99990001_NguyenVanAn_2026-04-20.pdf"
```

Compare to the value stored in DB:

```sql
SELECT content_hash, pdf_storage_path, file_size_bytes
FROM   public.lab_reports
WHERE  user_id = (SELECT id FROM public.profiles WHERE mssv='99990001')
  AND  session_id = '<SESSION_ID>';
```

**Note:** The spec §7.3 states the stored hash is of `{session_id, answers_json, timestamps}`, NOT of the PDF bytes. If the hash does not match the PDF bytes, confirm which of the two semantics the Phase-5 implementation actually uses by reading `web/src/services/labReportGenerator.js`. Record the answer in the bug log under **"Hash semantic"** — both choices are defensible, but the choice must be consistent.

- [ ] **Step 5: Open Supabase Storage → `lab-reports` bucket**

Navigate to Dashboard → Storage → `lab-reports` → `<userId>/<sessionId>.pdf`. Expected: file exists, size matches `file_size_bytes` in `lab_reports`. Download via Dashboard, `sha256sum` the downloaded file, confirm byte-identical to the student-downloaded copy.

- [ ] **Step 6: Repeat Steps 1–5 for `pilot_s2`, `pilot_s3`, `pilot_s4`**

Save each PDF into `phase-7-evidence/pdfs/`. Any rendering regression that only appears in one student's report (e.g., Vietnamese characters mangled for a specific name) → **log a bug**.

- [ ] **Step 7: `pilot_s5` attempts to generate — must be blocked**

Log in as `pilot_s5`, navigate to the report URL. Expected: blocked (either redirected away, or the Tạo PDF button is disabled with an explanation). If pilot_s5 can generate a report without a post-lab submission → **log a bug**.

---

## Task 10: Admin reviews submissions

**Files:** none.

- [ ] **Step 1: Log in as `pilot_admin` → /admin → Submissions tab**

Expected: `SubmissionsAdminTab.jsx` loads. A filter row (lab / group / date) appears. Filter by `LAB-01` + group `Pilot-LAB01-Group-A`.

- [ ] **Step 2: Verify the 4 submission rows**

Expected table rows: MSSV 99990001, 99990002, 99990003, 99990004. Each shows:
- Pre-quiz score (100% for 99990001/3/4; 100% on attempt 2 for 99990002 — the UI should reflect the latest attempt).
- Post-lab: **Submitted** status with timestamp.
- PDF: **Download** link.

Row for 99990005 should either be absent or show `Pre-lab incomplete` status.

- [ ] **Step 3: Download PDF from admin UI**

Click the download link for `pilot_s1`. Expected: same byte-identical PDF as downloaded in Task 9.

- [ ] **Step 4: Add teacher_comment**

Edit `pilot_s1`'s submission → write a short teacher comment (≥ 20 chars) → save. Verify:

```sql
SELECT teacher_comment
FROM   public.lab_post_submissions
WHERE  session_id = '<SESSION_ID>'
  AND  user_id = (SELECT id FROM public.profiles WHERE mssv='99990001');
```

Expected: comment saved. Also reload `pilot_s1`'s PDF page on the student side → the comment must **NOT** appear on the PDF (per spec §3.8 — `teacher_comment` is admin-only).

- [ ] **Step 5: Bulk ZIP download**

Click **Download all PDFs (ZIP)** for the group. Expected: a single `.zip` downloads containing 4 PDFs with the correct filenames. Open the ZIP → verify all 4 present.

- [ ] **Step 6: Session drill-down**

Go to the **Sessions** tab → filter `LAB-01` → open the pilot session. Expected: detail drawer shows the timeline, per-step evidence counts (50 / 0 / 30 / 20 / 3), and a "Force end / Reset" button (do not click).

---

## Task 11: Regression — Raw Monitor + Active Test OUTSIDE Lab Mode

**Files:** none. This confirms Phase-2 tagging did not break the default code paths.

- [ ] **Step 1: Exit Lab Mode on the app**

Open Lab Mode screen → **Exit / Deactivate**. Expected: banner disappears, `LabModeManager.state` → `Inactive`.

- [ ] **Step 2: Raw Monitor capture + export works as before**

Raw Monitor → **Start Capture** → let frames flow ≥ 10 s → **Stop** → **Export**. Expected: normal export path runs (CSV upload to the existing `exports` bucket / `export_records` table). **No** row inserted into `lab_evidence` (critical).

Verify:
```sql
SELECT count(*) FROM public.lab_evidence WHERE created_at > now() - interval '1 minute';
```
Expected: **0** new rows (the count should match whatever was there at the start of this step).

- [ ] **Step 3: Active Test fire works as before**

Active Test → send any PID. Expected: normal CAN command goes out; response displays on screen. **No** row inserted into `lab_evidence`. Re-run the count query — still 0 new rows.

- [ ] **Step 4: Other app features still work**

Smoke-check: Diagnostic Hub → DTC scan screen, Wiring screen, any other tabs. Expected: no crashes, no errors in `adb logcat` tagged `LabModeManager` / `LabEvidenceRepository` while not in Lab Mode.

---

## Task 12: Session expiry regression (optional if time permits)

**Files:** none.

- [ ] **Step 1: Create a one-off session with a short expiry for testing**

In SQL Editor (as admin):
```sql
UPDATE public.lab_sessions
SET    expires_at = now() + interval '2 minutes'
WHERE  session_code = '<SESSION_CODE>'  -- or create a new dummy one
RETURNING session_code, expires_at;
```

- [ ] **Step 2: Wait > 2 min, then run the expire job**

Either wait for the scheduled `expire_old_sessions()` cron (if pg_cron), or invoke manually:
```sql
SELECT public.expire_old_sessions();
```

Expected: session row moves to `status = 'EXPIRED'`. App side: if the app tries to push evidence to that session_id, RLS must reject the insert.

- [ ] **Step 3: App rejects post-expiry evidence**

On the app, with Lab Mode still "Active" locally (stale state), trigger a Raw Monitor capture. Expected: Supabase returns an RLS error (or silent failure logged to `LabEvidenceRepository`'s TAG). Verify **no** rows for the expired session land in `lab_evidence` after the expiry timestamp.

(If this regression is not feasible in the pilot window, mark it `Deferred` in the bug log.)

---

## Task 13: Bug log

**Files:**
- Create: `docs/superpowers/pilot/phase-7-bug-log.md`

- [ ] **Step 1: Create the bug log from this template**

```markdown
# Phase 7 Pilot — Bug Log

**Pilot run date:** 2026-04-20
**Operator:** <your name>
**Supabase project:** <project-ref>
**App build:** <git sha of app/>
**Web build:** <git sha of web/>
**Session code used:** <SESSION_CODE>
**Session id:** <SESSION_ID>

Accounts:
| # | Email | Username | MSSV | Role |
|---|---|---|---|---|
| 1 | pilot.admin@bk.test | pilot_admin | ADMIN001 | admin |
| 2 | pilot.lead@bk.test  | pilot_s1    | 99990001 | student (leader) |
| 3 | pilot.s2@bk.test    | pilot_s2    | 99990002 | student |
| 4 | pilot.s3@bk.test    | pilot_s3    | 99990003 | student |
| 5 | pilot.s4@bk.test    | pilot_s4    | 99990004 | student |
| 6 | pilot.s5@bk.test    | pilot_s5    | 99990005 | student (no pre-lab) |

---

## Bugs

### BUG-001 — <short title>

- **Phase / Task:** Task N, Step M
- **Severity:** Blocker | Major | Minor | Cosmetic
- **Component:** App | Web-Student | Web-Admin | DB | PDF | Realtime
- **Reproduction:** numbered steps
- **Expected:** what the spec / plan says
- **Actual:** what happened
- **Evidence:** screenshot path / SQL output / `adb logcat` snippet
- **Proposed fix (do NOT implement here):** `file:line` + one-line description of the change
- **Status:** Open | Deferred | Filed-as-issue-#NNN

---

### BUG-002 — ...

(Append more bugs in the same format.)

---

## Deferred checks

- List of steps marked `Deferred` during the run with a one-line reason.

## Sign-off

- [ ] Tasks 1–11 executed
- [ ] Task 12 executed or deferred with reason
- [ ] All bugs above have a proposed fix line
- [ ] PDFs archived under `docs/superpowers/pilot/phase-7-evidence/pdfs/`
```

- [ ] **Step 2: Populate as you go**

For every ❌ result during Tasks 1–12, add a `BUG-NNN` entry in the format above. Keep entries concise; include SQL output inline where short, otherwise reference a file in `phase-7-evidence/`.

- [ ] **Step 3: Commit the bug log**

```bash
git add docs/superpowers/pilot/phase-7-bug-log.md
git commit -m "docs(pilot): phase-7 bug log from pilot run"
```

---

## Task 14: Cleanup (run ONLY after bug log is finalized)

**Files:** none.

- [ ] **Step 1: Decide — keep or delete the test data?**

If the bug log requires re-reproducing any issue later, **keep** the pilot users + session until the fixes land. Otherwise proceed.

- [ ] **Step 2: Delete the 6 test users (cascade removes all their lab rows)**

Uncomment and run the cleanup block at the bottom of `docs/superpowers/pilot/phase-7-test-accounts.sql`:

```sql
DELETE FROM auth.users WHERE email LIKE 'pilot.%@bk.test';
```

- [ ] **Step 3: Verify cleanup**

```sql
SELECT count(*) FROM auth.users WHERE email LIKE 'pilot.%@bk.test';                         -- expect 0
SELECT count(*) FROM public.lab_group_members gm
  JOIN public.lab_groups g ON g.id = gm.group_id WHERE g.name LIKE 'Pilot-LAB01-%';          -- expect 0
SELECT count(*) FROM public.lab_sessions WHERE group_id IN
  (SELECT id FROM public.lab_groups WHERE name LIKE 'Pilot-LAB01-%');                        -- expect 0
SELECT count(*) FROM public.lab_reports WHERE user_id NOT IN (SELECT id FROM public.profiles); -- expect 0
```

Expected: all four counts = 0.

- [ ] **Step 4: Delete the empty pilot groups**

```sql
DELETE FROM public.lab_groups WHERE name LIKE 'Pilot-LAB01-%';
```

- [ ] **Step 5: Storage cleanup**

Dashboard → Storage → `lab-reports` → delete the folder(s) for the 6 pilot user-ids. Same for `lab-images`.

- [ ] **Step 6: Final commit (nothing to commit if no files changed)**

The bug log and the `phase-7-test-accounts.sql` are the only in-repo artifacts. No production code should have been modified during this pilot.

---

## Self-Review Notes

- **Spec coverage.** Tasks 1–12 walk the full state machine in spec §5.2 (`NOT_ASSIGNED → … → COMPLETED`) for 5 students, plus the negative case (pilot_s5, no pre-lab). §4 (App Lab Mode), §5 (Student flow), §6 (Admin), §7 (PDF), §3.11 (RLS) all have at least one explicit verification step. §3.12 (`expire_old_sessions`) is covered in Task 12.
- **Regression coverage.** Task 11 pins Raw Monitor and Active Test behavior when Lab Mode is inactive — the exact surface touched in Phase 2. If any `lab_evidence` row appears during Task 11, the Phase-2 tagging path leaked.
- **Realtime coverage.** Tasks 6 & 7 require the leader window to observe live counter updates without manual refresh — the core Phase-4 value prop.
- **Hash semantic caveat.** Task 9 Step 4 explicitly flags the ambiguity between "PDF bytes hash" vs "content object hash" from spec §7.3 so the operator records the actual behavior rather than flagging a mismatch as a bug without investigation.
- **No placeholders.** Every SQL query, URL, input value, and expected result is concrete. No `TBD` / `TODO` anywhere.
- **No code changes.** By design — this phase is acceptance, not implementation. The only files created are the test-accounts SQL, the bug log, and the downloaded PDF evidence folder.
