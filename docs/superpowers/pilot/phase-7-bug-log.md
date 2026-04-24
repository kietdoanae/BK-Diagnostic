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

### BUG-005 — Raw Frame Monitor hidden from students → blocks lab evidence capture

- **Phase / Task:** Task 6 (app Raw Monitor capture) — **BLOCKER**
- **Severity:** Critical
- **Component:** App
- **Reproduction:**
  1. Sign in as a student account (e.g. `pilot_s1`, role=student).
  2. Open the Diagnostic screen from the main menu.
  3. Scroll through the function cards.
- **Expected:** The "Raw Frame Monitor" card is visible so the student can open Raw Monitor, activate Lab Mode, and stream CAN frames into `lab_evidence`. Task 6 of the pilot plan assumes this.
- **Actual (static):** `DiagnosticScreen.kt:172-184` wraps the Raw Monitor card in `if (isAdmin) { … }`. The `isAdmin` flag passed from `MainActivity.kt:243` is `canViewRawFrame` (= `UserProfile.isAdmin || isModerator` per `UserProfile.kt:18`). Students never see the card, so they cannot reach `RAW_MONITOR` view and cannot activate Lab Mode from that screen. The entire Task 6 evidence flow is unreachable for student accounts.
- **Evidence:**
  - `app/.../ui/screens/DiagnosticScreen.kt:172` — `// Raw Frame Monitor — chỉ hiện với Admin` + `if (isAdmin) { … }` (lines 173–184).
  - `app/.../MainActivity.kt:243` — `isAdmin = canViewRawFrame`.
  - `app/.../UserProfile.kt:18` — `val canViewRawFrame: Boolean get() = isAdmin || isModerator`.
- **Proposed fix (do NOT implement here):**
  Raw Monitor needs to remain discoverable by students specifically for Lab Mode, but should stay hidden from unprivileged users outside a lab session. Two equivalent options:
  - Option A (lab-aware gate): Replace `if (isAdmin)` at `DiagnosticScreen.kt:173` with `if (isAdmin || LabModeManager.state.collectAsState().value is LabModeState.Active)` so the card appears whenever Lab Mode is active OR the user is staff. Requires importing `LabModeManager` and `LabModeState` in `DiagnosticScreen.kt`.
  - Option B (permission flag): Add `canStreamLabEvidence: Boolean get() = isAdmin || isModerator || isStudent` on `UserProfile` and gate the card on that instead. Simpler, but exposes Raw Monitor to all signed-in users all the time.
  Option A is the minimum-blast-radius fix and aligns with the spec ("Raw Monitor is the evidence-capture surface for Lab Mode").
- **Status:** Open — blocks Task 6 until resolved.

---

### BUG-006 — `complete_lab_session` RPC: any group member can end the session

- **Phase / Task:** Task 10 (leader-only session close)
- **Severity:** Major
- **Component:** DB
- **Reproduction:**
  1. `pilot_s1` creates a session (leader).
  2. `pilot_s2` (non-leader member of the same group) calls `supabase.rpc('complete_lab_session', { p_session_id })`.
- **Expected (spec):** Only the group leader can close the session. Non-leader members receive `Only the group leader can complete the session`.
- **Actual (static):** `website/lab_complete_session_rpc.sql:30-35` only checks `EXISTS … lab_group_members WHERE group_id = v_group_id AND user_id = v_caller`. There is no `AND role = 'leader'` filter. Any group member can mark the session COMPLETED, ending the lab for everyone.
- **Evidence:** `website/lab_complete_session_rpc.sql:30-35`.
- **Proposed fix (do NOT implement here):** In `lab_complete_session_rpc.sql:30-35`, tighten the membership check to require leader role:
  ```sql
  IF NOT EXISTS (
      SELECT 1 FROM lab_group_members
      WHERE group_id = v_group_id
        AND user_id  = v_caller
        AND role     = 'leader'
  ) THEN
      RAISE EXCEPTION 'Only the group leader can complete the session';
  END IF;
  ```
- **Status:** Open (pending live confirmation in Task 10).

---

### BUG-007 — `set_current_step` RPC: any group member can override the current step

- **Phase / Task:** Task 5 / Task 7 (leader-driven step navigation)
- **Severity:** Major
- **Component:** DB
- **Reproduction:**
  1. Leader `pilot_s1` sets step A.
  2. Non-leader `pilot_s2` calls `supabase.rpc('set_current_step', { p_session_id, p_step_id: stepB })`.
- **Expected (spec):** Only the leader drives step navigation; non-leaders get `Only the group leader can change the current step`.
- **Actual (static):** `website/lab_rpc.sql:246-251` checks group membership but not leader role. Any member can override `current_step_id`, which every other member (and the live observer page) will immediately see via realtime subscription — breaks the "leader controls progression" invariant.
- **Evidence:** `website/lab_rpc.sql:246-251` (no `role = 'leader'` in the membership check).
- **Proposed fix (do NOT implement here):** Replace the membership check at `lab_rpc.sql:246-251` with:
  ```sql
  IF NOT EXISTS (
      SELECT 1 FROM public.lab_group_members
      WHERE group_id = v_group_id
        AND user_id  = v_uid
        AND role     = 'leader'
  ) THEN
      RAISE EXCEPTION 'Only the group leader can change the current step';
  END IF;
  ```
- **Status:** Open (pending live confirmation in Task 7).

---

### BUG-008 — `end_current_step` RPC: any group member can clear the current step

- **Phase / Task:** Task 5 / Task 7
- **Severity:** Major
- **Component:** DB
- **Reproduction:** Analogous to BUG-007 but calling `end_current_step(p_session_id)` from a non-leader account.
- **Expected (spec):** Leader-only, same rationale as `set_current_step`.
- **Actual (static):** `website/lab_rpc.sql:298-303` checks membership without the leader filter. Any member can clear `current_step_id`.
- **Evidence:** `website/lab_rpc.sql:298-303`.
- **Proposed fix (do NOT implement here):** Same pattern as BUG-007 — change the membership check at `lab_rpc.sql:298-303` to require `AND role = 'leader'`, raising `Only the group leader can end the current step`.
- **Status:** Open (pending live confirmation in Task 7).

---

### BUG-009 — Android Lab Mode does not auto-deactivate on `expires_at`

- **Phase / Task:** Task 12 (expiry regression)
- **Severity:** Major
- **Component:** App
- **Reproduction:**
  1. Student activates Lab Mode; server sets `expires_at = now() + 2 hours`.
  2. In DB, shorten `expires_at` to `now() - 1 minute` (simulate expiry).
  3. On the phone, keep the Lab Mode screen open without exiting.
- **Expected:** After `expires_at` passes, the app detects expiry, flips `LabModeState` to `Inactive`, and subsequent Raw Monitor captures stop being tagged (or refuse to upload, matching the DB-side `expires_at > now()` check on `lab_evidence_insert_active`).
- **Actual (static):** `LabModeManager.kt` (entire file, 55 lines) has no timer, no realtime subscription, and no `expires_at` comparison. The only path to `Inactive` is the user pressing Exit (`deactivate()`). If the user does not press Exit, the app continues to believe Lab Mode is Active and continues to call `pushActiveTest` / `pushRawFrameBatch`. The DB `lab_evidence_insert_active` RLS policy (`lab_rls.sql:134-145`) will reject these inserts (because `s.expires_at > now()` is false), but the app produces a silent stream of rejected inserts instead of surfacing the expiry to the user.
- **Evidence:** `app/.../lab/LabModeManager.kt:14-55` — no expiry watcher. `LabModeState.Active.expiresAt` is stored (line 14 of `LabModeState.kt`) but never consulted after activation.
- **Proposed fix (do NOT implement here):** Add an expiry watcher in `LabModeManager`:
  ```kotlin
  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private var expiryJob: Job? = null

  private fun armExpiryTimer(expiresAtIso: String) {
      expiryJob?.cancel()
      val delayMs = (Instant.parse(expiresAtIso).toEpochMilli() - System.currentTimeMillis())
                      .coerceAtLeast(0L)
      expiryJob = scope.launch {
          delay(delayMs)
          Log.w(TAG, "Lab session expired — auto-deactivating")
          deactivate()
      }
  }
  ```
  Call `armExpiryTimer(response.expiresAt)` at the end of `activate()` (after line 38), and `expiryJob?.cancel()` inside `deactivate()` (after line 52).
- **Status:** Open (pending live confirmation in Task 12 if run).

---

### BUG-010 — Lab Mode "Expires" UI displays UTC time without timezone conversion

- **Phase / Task:** Task 5 (app Lab Mode UX)
- **Severity:** Minor
- **Component:** App
- **Reproduction:**
  1. Student activates Lab Mode. Server returns `expires_at` in UTC ISO-8601 (`2026-04-20T09:15:00Z`).
  2. Observe the "Expires" row in Lab Mode screen.
- **Expected:** Value displayed in the device's local timezone (Asia/Ho_Chi_Minh is UTC+7 for pilot operators) so a student can reason about "how much time do I have left?".
- **Actual (static):** `LabModeScreen.kt:163` does `active.expiresAt.take(19).replace("T", " ")`. This strips the `Z` and does NOT convert to local time — a student sees `2026-04-20 09:15:00` when their wall clock reads `16:15`, creating a 7-hour illusion of extra time.
- **Evidence:** `app/.../ui/screens/LabModeScreen.kt:163`.
- **Proposed fix (do NOT implement here):** Replace line 163 with a formatter that converts to local time:
  ```kotlin
  val localExpires = remember(active.expiresAt) {
      java.time.Instant.parse(active.expiresAt)
          .atZone(java.time.ZoneId.systemDefault())
          .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
  }
  InfoRow(label = "Expires", value = localExpires)
  ```
- **Status:** Open (pending live inspection in Task 5).

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
