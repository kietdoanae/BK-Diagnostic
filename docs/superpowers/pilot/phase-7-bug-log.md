# Phase 7 Pilot — Bug Log

**Pilot run date:** 2026-04-20
**Operator:** kiet.doanae@gmail.com
**Pre-run:** Static-analysis pass executed by agent before live pilot
**Supabase project:** `<project-ref — fill in before live run>`
**App build:** `<git sha of app/ — fill in before live run>`
**Web build:** `<git sha of web/ — fill in before live run>`
**Session code used:** `<SESSION_CODE — fill in during live run>`
**Session id:** `<SESSION_ID — fill in during live run>`

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

## Pre-run static-analysis summary

All DB RPCs, triggers, schema, and RLS policies were verified by reading the
actual source files. The following items were found to be **correct** and do
NOT require action:

| Area | File | Finding |
|------|------|---------|
| `submit_pre_quiz` attempt numbering | `website/lab_rpc.sql:194-196` | Uses `COALESCE(MAX(attempt_number),0)+1` ✓ |
| `validate_lab_code` response fields | `website/lab_rpc.sql:117-124` + `LabModeState.kt:19-25` | Returns `group_name`; Kotlin `@SerialName` maps it ✓ |
| `expire_old_sessions()` existence | `website/lab_rpc.sql:318-338` | Function exists, sets EXPIRED + ended_at ✓ |
| Cross-group unique constraint | `website/lab_schema.sql:79-108` | BEFORE INSERT trigger enforces one group per user per lab ✓ |
| `LabModeManager` state persistence | `app/.../LabModeManager.kt:16` | Pure in-memory `MutableStateFlow`, no DataStore read ✓ |
| Realtime subscription cleanup | `web/src/hooks/useLiveEvidence.js:56` | `return () => supabase.removeChannel(channel)` ✓ |
| Vietnamese diacritic stripping | `web/src/services/labReportFilename.js:14-18` | NFD + range-strip + đ/Đ manual map ✓ |
| `complete_lab_session` RPC | `website/lab_complete_session_rpc.sql:37-41` | Sets COMPLETED + ended_at ✓ |

---

## Bugs

### BUG-001 — `lab_post_submissions` INSERT allowed for pre-lab-failed users at DB level

- **Phase / Task:** Task 8, Step 3
- **Severity:** Major
- **Component:** DB / Web-Student
- **Reproduction:**
  1. Log in as `pilot_s5` (no pre-lab quiz taken).
  2. Navigate directly to `/labs/<labId>/session/<sid>/post`.
  3. Type any text in Q1 and wait 10 seconds for the autosave timer.
  4. Check DB: `SELECT is_draft FROM public.lab_post_submissions WHERE user_id = (SELECT id FROM public.profiles WHERE mssv='99990005')`.
- **Expected:** INSERT is blocked — no draft row created.
- **Actual (static):** The RLS policy `post_submissions_insert_own` (`website/lab_rls.sql:180-182`) only checks `user_id = auth.uid()`. It does not verify pre-lab passage. Additionally, `saveDraft` in `LabPostLabPage.jsx:99-114` only guards on `submission?.is_draft === false`; it does NOT check `preQuizPassed`. The 10-second autosave interval fires unconditionally. A draft row WILL be written for `pilot_s5` as soon as the autosave timer fires, causing Task 8 Step 4 to return 5 rows instead of 4.
- **Evidence:**
  - `website/lab_rls.sql:180-182` — INSERT WITH CHECK has no pre-quiz condition.
  - `web/src/pages/LabPostLabPage.jsx:99-114` — `saveDraft` body lacks `if (!preQuizPassed) return`.
- **Proposed fix (do NOT implement here):**
  Option A (frontend, simpler): `web/src/pages/LabPostLabPage.jsx:100` — add `if (!preQuizPassed) return` at the top of the `saveDraft` callback, before the `setSavingNow(true)` call.
  Option B (defense-in-depth, add to RLS): `website/lab_rls.sql:180-182` — change `post_submissions_insert_own` WITH CHECK to also require:
  ```sql
  AND EXISTS (
      SELECT 1 FROM public.lab_pre_quiz_submissions pq
      JOIN  public.lab_sessions s ON s.id = lab_post_submissions.session_id
      WHERE pq.user_id = auth.uid()
        AND pq.lab_id  = s.lab_id
        AND pq.passed  = true
  )
  ```
  Both options should be applied together for complete coverage.
- **Status:** Open (pending live confirmation in Task 8 Step 3)

---

### BUG-002 — `LabEvidenceRepository`: no Lab Mode re-check before Supabase INSERT

- **Phase / Task:** Task 11 (regression)
- **Severity:** Major
- **Component:** App
- **Reproduction:**
  1. Activate Lab Mode in the app.
  2. Start Raw Monitor capture.
  3. While capture is running, tap Exit in Lab Mode screen (`deactivate()` called).
  4. In-flight batch from `enqueueRawFrame` → `doInsertRawFrames` may still execute.
  5. Similarly, `pushActiveTest` or `pushRawFrameBatch` called by ViewModel right after deactivation can still fire.
- **Expected:** No new `lab_evidence` rows after Lab Mode is deactivated (Task 11 Step 2 query should return 0).
- **Actual (static):** `LabEvidenceRepository.kt:83-99` (`pushActiveTest`) and `:103-132` (`pushRawFrameBatch`) call `supabaseClient.postgrest["lab_evidence"].insert(...)` without checking `LabModeManager.currentSessionId`. If the ViewModel captured the `sessionId` from `LabModeState.Active` and calls these methods after `deactivate()` flips state to `Inactive`, the insert proceeds. The RLS policy (`lab_rls.sql:134-145`) does protect at the DB level by requiring `s.status = 'ACTIVE'` and group membership — but if the session is still ACTIVE on the server at the time of the insert (e.g., user deactivated the app session but hasn't ended the web session), evidence leaks in.
- **Evidence:** `app/.../LabEvidenceRepository.kt:83-99` and `:103-132` — no `if (LabModeManager.currentSessionId != sessionId) return` guard present.
- **Proposed fix (do NOT implement here):** Add the following guard at the top of both `pushActiveTest` and `pushRawFrameBatch`, and inside `doInsertRawFrames` (before the `supabaseClient` call at line 147):
  ```kotlin
  if (LabModeManager.currentSessionId != sessionId) {
      Log.w(TAG, "session mismatch — insert skipped (Lab Mode deactivated)")
      return
  }
  ```
  Files to change: `app/.../LabEvidenceRepository.kt:83` (top of `pushActiveTest`), `:103` (top of `pushRawFrameBatch`), `:144` (top of `doInsertRawFrames`).
- **Status:** Open (pending live confirmation in Task 11 Step 2)

---

### BUG-003 — PDF footer missing `content_hash` fragment (spec §7.3 discrepancy)

- **Phase / Task:** Task 9, Steps 3 and 4
- **Severity:** Minor
- **Component:** PDF
- **Reproduction:**
  1. `pilot_s1` generates the PDF.
  2. Open the PDF and inspect the bottom of any page.
- **Expected (spec §7.3):** Every page footer includes a short hash fragment alongside the page counter and student info.
- **Actual (static):** `labReportGenerator.js:51-58` adds footer text: `Trang X / Y · <name> · <mssv>` (left) and `Session <code>` (right). The `contentHash` is computed at line 65 (after the PDF is rendered and blobbed at line 61) and is only stored in DB. It is never embedded in the PDF itself.
- **Evidence:** `web/src/services/labReportGenerator.js:51-58` (footer stamp), `:64-72` (hash computed after PDF rendered).
- **Proposed fix (do NOT implement here):**
  Move the `contentHash` computation to BEFORE `worker.toPdf()` (the hash input is the content JSON, which is available before rendering). Then include the first 16 characters of the hash in the footer stamp. Pseudocode change in `labReportGenerator.js`:
  ```js
  // 1. Compute hash early (before toPdf) — depends only on content JSON
  const contentHash = await sha256Hex(buildHashInput({ sessionId, answers: ..., ... }))
  // 2. Render PDF
  const pdf = await worker.toPdf().get('pdf')
  // 3. Footer now includes hash fragment
  pdf.text(`${contentHash.slice(0,16)}…`, 105, 294, { align: 'center' })
  ```
- **Status:** Open (pending live test — verify in Task 9 Step 3)

---

### BUG-004 — `GroupForm.jsx`: cross-group duplicate error shows raw UUID in message

- **Phase / Task:** Task 2, Step 5
- **Severity:** Cosmetic
- **Component:** Web-Admin
- **Reproduction:**
  1. Add MSSV `99990001` to `Pilot-LAB01-Group-B` while they are already in `Pilot-LAB01-Group-A`.
  2. The DB trigger fires: `RAISE EXCEPTION 'User % already belongs to another group for lab %', NEW.user_id, v_lab_id`.
  3. Supabase returns the raw message; `GroupForm.jsx:86-89` shows it via `message.error(error.message)`.
- **Expected:** User-friendly Vietnamese message: `"Sinh viên này đã tham gia nhóm khác cho cùng lab"`.
- **Actual (static):** The displayed message will be `"User <uuid> already belongs to another group for lab <uuid>"` — confusing for an admin.
- **Evidence:** `web/src/components/admin/GroupForm.jsx:86-89` — raw `error.message` shown.
- **Proposed fix (do NOT implement here):** In `GroupForm.jsx:86`, before `message.error(error.message)`, add:
  ```js
  if (error.message?.includes('already belongs to another group')) {
    message.error('Sinh viên này đã tham gia nhóm khác cho cùng lab')
    return
  }
  ```
- **Status:** Open (pending live test in Task 2 Step 5)

---

## Deferred checks

- **Task 5 Step 5** — "Activate Lab Mode for a user NOT in the group, using a second device" — deferred; requires second physical device.
- **Task 12** — Session expiry regression — will defer if not feasible within the pilot window (requires waiting 2+ minutes for the manually shortened `expires_at` to lapse).
- **All interactive tasks (1–12)** — Supabase Dashboard UI, web browser, and Android device interactions were not executed. The static-analysis pre-run above covered all source code. A human operator must run Tasks 1–12 end-to-end to confirm/refute each bug entry above.

---

## Sign-off

- [ ] Tasks 1–11 executed
- [ ] Task 12 executed or deferred with reason
- [ ] All bugs above have a proposed fix line
- [ ] PDFs archived under `docs/superpowers/pilot/phase-7-evidence/pdfs/`
