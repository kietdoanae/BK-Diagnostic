# Lab System Phase 4 — Web Student Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the full student-facing lab UX on the React web app: 6 new routes (`/labs`, `/labs/:labId`, `/labs/:labId/session/:sid`, `.../post`, `.../report`, `/my-reports`) that walk a student from pre-lab quiz → group practice dashboard (with realtime evidence) → post-lab form (with draft auto-save) → PDF download → history.

**Architecture:**
- Routes registered in `web/src/App.jsx` behind `ProtectedRoute`; nav entry added to `AppLayout`.
- Supabase JS client calls encapsulated in extensions to the existing `web/src/services/labApi.js` (Phase 3 created it). No new service file — keep lab APIs in one module.
- Three new hooks own their stateful concerns: `useLabQuiz` (pre-lab paging + submit), `useLabSession` (session lifecycle + current step), `useLiveEvidence` (Supabase Realtime subscription on `lab_evidence`).
- A pure helper `web/src/services/labState.js` computes the per-lab state enum used by `/labs`.
- Phase-5 deliverables (PDF template, content-hash, bucket upload) are intentionally stubbed in `LabReportPage` — the route exists and fetches data, but full rendering is deferred.
- One small SQL migration adds a `complete_lab_session` RPC (+ matching RLS allowance) because Section 3.11 doesn't grant students `UPDATE` on `lab_sessions`, yet Section 5.4 requires a "finish practice" action.

**Tech Stack:** React 19, Ant Design 6, `@supabase/supabase-js` 2 (Realtime included), `react-router-dom` 7. No new npm deps in this phase.

**Note on testing:** As in Phase 3, the web package has no test runner. Verification per task is `npm run lint && npm run build` plus a manual smoke test in `npm run dev` against live Supabase using a real student account assigned to a group. Do **not** add a test framework here.

---

## File Structure

### New files
```
website/lab_complete_session_rpc.sql               -- RPC + RLS for student session completion
web/src/services/labState.js                       -- Pure state-machine helper
web/src/hooks/useLabQuiz.js                        -- Pre-lab quiz paging + submit
web/src/hooks/useLabSession.js                     -- Session lifecycle (start/step/complete)
web/src/hooks/useLiveEvidence.js                   -- Supabase Realtime evidence feed
web/src/components/lab/PreQuizRunner.jsx           -- 1-question-per-screen quiz UI
web/src/components/lab/SessionCodeDisplay.jsx      -- Bottom-right code + countdown modal
web/src/components/lab/StepList.jsx                -- Left-column student step list
web/src/components/lab/EvidenceLiveCounter.jsx     -- "X / Y evidences" badge + ring
web/src/components/lab/StepDetail.jsx              -- Right-column current step detail
web/src/components/lab/EvidenceInlineViewer.jsx    -- Collapsible per-step evidence panel
web/src/components/lab/PostLabQuestion.jsx         -- Single post-lab question card
web/src/pages/LabsListPage.jsx                     -- Route: /labs
web/src/pages/LabOverviewPage.jsx                  -- Route: /labs/:labId
web/src/pages/LabSessionPage.jsx                   -- Route: /labs/:labId/session/:sid
web/src/pages/LabPostLabPage.jsx                   -- Route: /labs/:labId/session/:sid/post
web/src/pages/LabReportPage.jsx                    -- Route: /labs/:labId/session/:sid/report (Phase-5 stub)
web/src/pages/MyReportsPage.jsx                    -- Route: /my-reports
```

### Modified files
```
web/src/services/labApi.js                         -- Add student-facing queries + RPC wrappers
web/src/App.jsx                                    -- Register 6 new routes
web/src/components/AppLayout.jsx                   -- Add "Labs" nav entry
```

### Responsibility boundaries
- `labApi.js` — raw Supabase calls only. Includes new student helpers; no JSX, no Ant Design.
- `labState.js` — pure function `computeLabState({ preQuiz, activeSession, lastSession, postSubmission, hasReport })`. No I/O, no React.
- `hooks/useXxx.js` — own data fetching + optimistic state. No JSX.
- `components/lab/*.jsx` — presentational, take props, emit callbacks. No routing.
- `pages/*.jsx` — orchestrate hooks, render components inside `<AppLayout>`, handle redirects. One page per route.

---

## Task 1: SQL migration — `complete_lab_session` RPC + member UPDATE RLS

**Why:** Section 3.11 grants students only SELECT+INSERT on `lab_sessions`, but Section 5.4 needs a "Kết thúc thực hành" button that flips status to `COMPLETED`. We add a `SECURITY DEFINER` RPC that verifies the caller is a group member and clears `current_step_id` + sets `status='COMPLETED'` + `ended_at=now()`.

**Files:**
- Create: `website/lab_complete_session_rpc.sql`

- [ ] **Step 1: Write the migration**

Create `website/lab_complete_session_rpc.sql` with:

```sql
-- Phase 4: allow students to mark their own session COMPLETED.
-- Mirrors start_lab_session style (SECURITY DEFINER, explicit membership check).

CREATE OR REPLACE FUNCTION complete_lab_session(p_session_id uuid)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_group_id uuid;
    v_status   text;
    v_caller   uuid := auth.uid();
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, status INTO v_group_id, v_status
    FROM lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE (current: %)', v_status;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM lab_group_members
        WHERE group_id = v_group_id AND user_id = v_caller
    ) THEN
        RAISE EXCEPTION 'Caller is not a member of the session group';
    END IF;

    UPDATE lab_sessions
       SET status = 'COMPLETED',
           current_step_id = NULL,
           step_started_at = NULL,
           ended_at = now()
     WHERE id = p_session_id;

    RETURN json_build_object('ok', true, 'session_id', p_session_id);
END;
$$;

GRANT EXECUTE ON FUNCTION complete_lab_session(uuid) TO authenticated;
```

- [ ] **Step 2: Run the migration against Supabase**

In the Supabase SQL editor (or via `psql`), paste the contents of `website/lab_complete_session_rpc.sql` and run it.

Expected: `CREATE FUNCTION` + `GRANT` succeed. Verify with:

```sql
SELECT proname FROM pg_proc WHERE proname = 'complete_lab_session';
-- → 1 row
```

- [ ] **Step 3: Commit**

```bash
git add website/lab_complete_session_rpc.sql
git commit -m "feat(db): add complete_lab_session RPC for student session finish"
```

---

## Task 2: Extend `labApi.js` — student-facing queries

**Files:**
- Modify: `web/src/services/labApi.js`

- [ ] **Step 1: Append the student-facing section**

Append to `web/src/services/labApi.js`:

```javascript
// ─── Student-facing queries ──────────────────────────────────────────────────

/**
 * Labs the given user is assigned to (via group membership) + the user's
 * role (leader/member) and group info. Filters to published labs only.
 * Returns an array of { lab, group, role }.
 */
export async function listAssignedLabsForUser(userId) {
  const { data, error } = await supabase
    .from('lab_group_members')
    .select(
      'role, group:lab_groups(id, name, semester, lab_id, lab:labs(*))'
    )
    .eq('user_id', userId)
  if (error) return { data: [], error }
  const rows = (data || [])
    .filter((r) => r.group?.lab?.is_published)
    .map((r) => ({ lab: r.group.lab, group: r.group, role: r.role }))
  return { data: rows, error: null }
}

/**
 * Latest pre-quiz attempt for (user, lab). Returns row or null.
 */
export async function getLatestPreQuizForLab(userId, labId) {
  const { data, error } = await supabase
    .from('lab_pre_quiz_submissions')
    .select('*')
    .eq('user_id', userId)
    .eq('lab_id', labId)
    .order('submitted_at', { ascending: false })
    .limit(1)
    .maybeSingle()
  return { data, error }
}

/**
 * The most recent session for a group (any status). Used by the state
 * machine to distinguish PRACTICE_ACTIVE vs PRACTICE_DONE_POST_PENDING.
 */
export async function getLatestSessionForGroup(groupId) {
  const { data, error } = await supabase
    .from('lab_sessions')
    .select('*')
    .eq('group_id', groupId)
    .order('started_at', { ascending: false })
    .limit(1)
    .maybeSingle()
  return { data, error }
}

/**
 * The current ACTIVE session for a group, or null.
 */
export async function getActiveSessionForGroup(groupId) {
  const { data, error } = await supabase
    .from('lab_sessions')
    .select('*')
    .eq('group_id', groupId)
    .eq('status', 'ACTIVE')
    .order('started_at', { ascending: false })
    .limit(1)
    .maybeSingle()
  return { data, error }
}

/** User's own post-lab submission for a session, or null. */
export async function getMyPostSubmission(userId, sessionId) {
  return supabase
    .from('lab_post_submissions')
    .select('*')
    .eq('user_id', userId)
    .eq('session_id', sessionId)
    .maybeSingle()
}

/** User's own report (PDF row) for a session, or null. */
export async function getMyReportForSession(userId, sessionId) {
  return supabase
    .from('lab_reports')
    .select('*')
    .eq('user_id', userId)
    .eq('session_id', sessionId)
    .maybeSingle()
}

/** All reports owned by the user — powers /my-reports. */
export async function listMyReports(userId) {
  return supabase
    .from('lab_reports')
    .select(
      'id, session_id, pdf_storage_path, file_size_bytes, generated_at, ' +
        'session:lab_sessions(id, session_code, started_at, ended_at, ' +
        '  lab:labs(id, code, title), group:lab_groups(id, name, semester))'
    )
    .eq('user_id', userId)
    .order('generated_at', { ascending: false })
}

// ─── RPC wrappers ────────────────────────────────────────────────────────────

export async function rpcStartLabSession(labId) {
  return supabase.rpc('start_lab_session', { p_lab_id: labId })
}

export async function rpcSubmitPreQuiz(labId, answers) {
  // answers: { "<question_id>": "A" | "..." }
  return supabase.rpc('submit_pre_quiz', {
    p_lab_id: labId,
    p_answers: answers,
  })
}

export async function rpcSetCurrentStep(sessionId, stepId) {
  return supabase.rpc('set_current_step', {
    p_session_id: sessionId,
    p_step_id: stepId,
  })
}

export async function rpcEndCurrentStep(sessionId) {
  return supabase.rpc('end_current_step', { p_session_id: sessionId })
}

export async function rpcCompleteLabSession(sessionId) {
  return supabase.rpc('complete_lab_session', { p_session_id: sessionId })
}

// ─── Post-lab auto-save ──────────────────────────────────────────────────────

/**
 * Upsert-ish: if a row for (user, session) exists, update it; else insert
 * with is_draft=true. UNIQUE(user_id, session_id) makes a single onConflict
 * upsert safe.
 */
export async function saveDraftPostSubmission(userId, sessionId, answers, uploadedImages) {
  return supabase
    .from('lab_post_submissions')
    .upsert(
      {
        user_id: userId,
        session_id: sessionId,
        answers,
        uploaded_images: uploadedImages,
        is_draft: true,
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'user_id,session_id' }
    )
    .select()
    .single()
}

export async function finalizePostSubmission(userId, sessionId, answers, uploadedImages) {
  return supabase
    .from('lab_post_submissions')
    .upsert(
      {
        user_id: userId,
        session_id: sessionId,
        answers,
        uploaded_images: uploadedImages,
        is_draft: false,
        submitted_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'user_id,session_id' }
    )
    .select()
    .single()
}

// ─── Screenshot evidence upload (web-originated) ────────────────────────────

/**
 * Uploads an image to the `lab-images` bucket and inserts a matching
 * lab_evidence row with evidence_type='screenshot'. Used by StepDetail
 * when the current step's evidence_type is 'screenshot'.
 */
export async function uploadScreenshotEvidence({
  sessionId,
  stepId,
  userId,
  file,
}) {
  const ext = (file.name.split('.').pop() || 'png').toLowerCase()
  const storageKey = `${userId}/${sessionId}/${stepId}/${crypto.randomUUID()}.${ext}`
  const { error: upErr } = await supabase.storage
    .from('lab-images')
    .upload(storageKey, file, { contentType: file.type || 'image/png' })
  if (upErr) return { data: null, error: upErr }

  const { data, error } = await supabase
    .from('lab_evidence')
    .insert({
      session_id: sessionId,
      step_id: stepId,
      submitted_by: userId,
      evidence_type: 'screenshot',
      payload: { image_path: storageKey, original_name: file.name },
      client_timestamp_ms: Date.now(),
    })
    .select()
    .single()
  return { data, error }
}

export async function getLabImageSignedUrl(storagePath, expiresIn = 120) {
  return supabase.storage
    .from('lab-images')
    .createSignedUrl(storagePath, expiresIn)
}

// ─── Evidence queries for a session (student view) ──────────────────────────

/**
 * All evidence for a session, grouped by step client-side. Used by
 * EvidenceInlineViewer and the live counter's initial state.
 */
export async function listEvidenceForSession(sessionId) {
  return supabase
    .from('lab_evidence')
    .select('id, step_id, submitted_by, evidence_type, payload, client_timestamp_ms, created_at')
    .eq('session_id', sessionId)
    .order('created_at', { ascending: true })
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: lint clean, Vite build emits `dist/` with no errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labApi.js
git commit -m "feat(web/labApi): add student-facing queries, RPC wrappers, screenshot upload"
```

---

## Task 3: `labState.js` — pure state-machine helper

**Files:**
- Create: `web/src/services/labState.js`

- [ ] **Step 1: Implement the helper**

Create `web/src/services/labState.js` with:

```javascript
// Lab state machine (per-user, per-lab). See spec Section 5.2.
//
//   NOT_ASSIGNED
//        │
//        ▼
//   PRE_LAB_PENDING ───► PRE_LAB_FAILED (retry loops back)
//        │
//        ▼
//   PRE_LAB_PASSED
//        │
//        ▼  (leader starts, or member joins active session)
//   PRACTICE_ACTIVE
//        │
//        ▼  (session COMPLETED/EXPIRED, member hasn't submitted post-lab)
//   PRACTICE_DONE_POST_PENDING
//        │
//        ▼
//   COMPLETED  (report PDF generated)

export const LAB_STATES = {
  NOT_ASSIGNED: 'NOT_ASSIGNED',
  PRE_LAB_PENDING: 'PRE_LAB_PENDING',
  PRE_LAB_FAILED: 'PRE_LAB_FAILED',
  PRE_LAB_PASSED: 'PRE_LAB_PASSED',
  PRACTICE_ACTIVE: 'PRACTICE_ACTIVE',
  PRACTICE_DONE_POST_PENDING: 'PRACTICE_DONE_POST_PENDING',
  COMPLETED: 'COMPLETED',
}

/**
 * Pure function. Inputs are plain objects (DB rows or null).
 *
 *   membership        — { role, group_id, lab_id } or null
 *   latestPreQuiz     — lab_pre_quiz_submissions row or null
 *   activeSession     — lab_sessions row (status ACTIVE) or null
 *   lastSession       — most recent lab_sessions row (any status) or null
 *   myPostSubmission  — lab_post_submissions row for (user, lastSession) or null
 *   myReport          — lab_reports row for (user, lastSession) or null
 */
export function computeLabState({
  membership,
  latestPreQuiz,
  activeSession,
  lastSession,
  myPostSubmission,
  myReport,
}) {
  if (!membership) return LAB_STATES.NOT_ASSIGNED

  // Pre-lab gate first — a member may see activeSession in their group but
  // if they haven't passed pre-lab they still belong in the PRE_LAB_* bucket
  // on the list view. The practice page itself lets them observe; it's the
  // post-lab submit that is gated.
  if (!latestPreQuiz) return LAB_STATES.PRE_LAB_PENDING
  if (!latestPreQuiz.passed) return LAB_STATES.PRE_LAB_FAILED

  if (activeSession) return LAB_STATES.PRACTICE_ACTIVE

  if (lastSession && ['COMPLETED', 'EXPIRED'].includes(lastSession.status)) {
    if (myReport) return LAB_STATES.COMPLETED
    return LAB_STATES.PRACTICE_DONE_POST_PENDING
  }

  return LAB_STATES.PRE_LAB_PASSED
}

/** Human label for the state tag in /labs. */
export function labStateLabel(state) {
  switch (state) {
    case LAB_STATES.NOT_ASSIGNED: return 'Chưa được gán nhóm'
    case LAB_STATES.PRE_LAB_PENDING: return 'Chưa làm pre-lab'
    case LAB_STATES.PRE_LAB_FAILED: return 'Pre-lab chưa đạt'
    case LAB_STATES.PRE_LAB_PASSED: return 'Sẵn sàng thực hành'
    case LAB_STATES.PRACTICE_ACTIVE: return 'Đang thực hành'
    case LAB_STATES.PRACTICE_DONE_POST_PENDING: return 'Cần làm post-lab'
    case LAB_STATES.COMPLETED: return 'Đã hoàn thành'
    default: return state
  }
}

/** Ant Design tag color per state. */
export function labStateTagColor(state) {
  switch (state) {
    case LAB_STATES.NOT_ASSIGNED: return 'default'
    case LAB_STATES.PRE_LAB_PENDING: return 'warning'
    case LAB_STATES.PRE_LAB_FAILED: return 'error'
    case LAB_STATES.PRE_LAB_PASSED: return 'processing'
    case LAB_STATES.PRACTICE_ACTIVE: return 'processing'
    case LAB_STATES.PRACTICE_DONE_POST_PENDING: return 'warning'
    case LAB_STATES.COMPLETED: return 'success'
    default: return 'default'
  }
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labState.js
git commit -m "feat(web/lab): add pure labState machine helper"
```

---

## Task 4: `useLabQuiz` hook

**Files:**
- Create: `web/src/hooks/useLabQuiz.js`

- [ ] **Step 1: Implement**

Create `web/src/hooks/useLabQuiz.js` with:

```javascript
import { useEffect, useState, useCallback } from 'react'
import { listQuestions, rpcSubmitPreQuiz } from '../services/labApi'

/**
 * Pre-lab quiz state manager. Loads questions in order, keeps answers in
 * local state, exposes paging + submit.
 *
 * Return shape:
 *   { loading, error, questions, index, answers, setAnswer,
 *     next, prev, canNext, isLast, submitting, result, submit }
 */
export function useLabQuiz(labId) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [questions, setQuestions] = useState([])
  const [index, setIndex] = useState(0)
  const [answers, setAnswers] = useState({}) // { [question_id]: 'A' | text }
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null) // { score_percent, passed, attempt_number }

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      const { data, error: err } = await listQuestions(labId, 'pre_lab')
      if (cancelled) return
      if (err) setError(err.message)
      else setQuestions(data || [])
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [labId])

  const setAnswer = useCallback((questionId, value) => {
    setAnswers((prev) => ({ ...prev, [questionId]: value }))
  }, [])

  const current = questions[index]
  const canNext = current ? answers[current.id] !== undefined && answers[current.id] !== '' : false
  const isLast = index === questions.length - 1

  const next = useCallback(() => {
    setIndex((i) => Math.min(i + 1, questions.length - 1))
  }, [questions.length])

  const prev = useCallback(() => {
    setIndex((i) => Math.max(i - 1, 0))
  }, [])

  const submit = useCallback(async () => {
    setSubmitting(true)
    setError(null)
    const { data, error: err } = await rpcSubmitPreQuiz(labId, answers)
    setSubmitting(false)
    if (err) {
      setError(err.message)
      return { ok: false, error: err.message }
    }
    setResult(data)
    return { ok: true, result: data }
  }, [labId, answers])

  return {
    loading,
    error,
    questions,
    index,
    current,
    answers,
    setAnswer,
    next,
    prev,
    canNext,
    isLast,
    submitting,
    result,
    submit,
  }
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/hooks/useLabQuiz.js
git commit -m "feat(web/hooks): add useLabQuiz for pre-lab paging + submit"
```

---

## Task 5: `useLabSession` hook

**Files:**
- Create: `web/src/hooks/useLabSession.js`

- [ ] **Step 1: Implement**

Create `web/src/hooks/useLabSession.js` with:

```javascript
import { useEffect, useState, useCallback } from 'react'
import { supabase } from '../services/supabase'
import {
  rpcSetCurrentStep,
  rpcEndCurrentStep,
  rpcCompleteLabSession,
} from '../services/labApi'

/**
 * Fetches a session by id, subscribes to UPDATE events so current_step_id +
 * status stay fresh, and exposes leader actions.
 *
 *   { session, loading, error, startStep, endStep, completeSession, reload }
 */
export function useLabSession(sessionId) {
  const [session, setSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    if (!sessionId) return
    setLoading(true)
    setError(null)
    const { data, error: err } = await supabase
      .from('lab_sessions')
      .select(
        'id, session_code, status, current_step_id, step_started_at, ' +
          'started_at, ended_at, expires_at, started_by, lab_id, group_id'
      )
      .eq('id', sessionId)
      .maybeSingle()
    if (err) setError(err.message)
    else setSession(data)
    setLoading(false)
  }, [sessionId])

  useEffect(() => {
    reload()
  }, [reload])

  useEffect(() => {
    if (!sessionId) return
    const channel = supabase
      .channel(`lab-session-${sessionId}`)
      .on(
        'postgres_changes',
        {
          event: 'UPDATE',
          schema: 'public',
          table: 'lab_sessions',
          filter: `id=eq.${sessionId}`,
        },
        (payload) => {
          setSession((prev) => ({ ...(prev || {}), ...payload.new }))
        }
      )
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [sessionId])

  const startStep = useCallback(
    async (stepId) => {
      const { error: err } = await rpcSetCurrentStep(sessionId, stepId)
      if (err) return { ok: false, error: err.message }
      await reload()
      return { ok: true }
    },
    [sessionId, reload]
  )

  const endStep = useCallback(async () => {
    const { error: err } = await rpcEndCurrentStep(sessionId)
    if (err) return { ok: false, error: err.message }
    await reload()
    return { ok: true }
  }, [sessionId, reload])

  const completeSession = useCallback(async () => {
    const { error: err } = await rpcCompleteLabSession(sessionId)
    if (err) return { ok: false, error: err.message }
    await reload()
    return { ok: true }
  }, [sessionId, reload])

  return { session, loading, error, startStep, endStep, completeSession, reload }
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/hooks/useLabSession.js
git commit -m "feat(web/hooks): add useLabSession with realtime session row + RPC wrappers"
```

---

## Task 6: `useLiveEvidence` hook (Supabase Realtime)

**Files:**
- Create: `web/src/hooks/useLiveEvidence.js`

- [ ] **Step 1: Implement**

Create `web/src/hooks/useLiveEvidence.js` with:

```javascript
import { useEffect, useState, useMemo } from 'react'
import { supabase } from '../services/supabase'
import { listEvidenceForSession } from '../services/labApi'

/**
 * Loads all evidence for a session, then subscribes to INSERTs on lab_evidence
 * filtered by session_id so the practice dashboard updates live.
 *
 *   { loading, error, evidence, countsByStep }
 *
 *   countsByStep — { [step_id]: number } for easy badge rendering.
 */
export function useLiveEvidence(sessionId) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [evidence, setEvidence] = useState([])

  // Initial load
  useEffect(() => {
    if (!sessionId) return
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      const { data, error: err } = await listEvidenceForSession(sessionId)
      if (cancelled) return
      if (err) setError(err.message)
      else setEvidence(data || [])
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [sessionId])

  // Realtime INSERT subscription
  useEffect(() => {
    if (!sessionId) return
    const channel = supabase
      .channel(`lab-evidence-${sessionId}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'lab_evidence',
          filter: `session_id=eq.${sessionId}`,
        },
        (payload) => {
          setEvidence((prev) => {
            if (prev.some((e) => e.id === payload.new.id)) return prev
            return [...prev, payload.new]
          })
        }
      )
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [sessionId])

  const countsByStep = useMemo(() => {
    const out = {}
    for (const e of evidence) {
      if (!e.step_id) continue
      out[e.step_id] = (out[e.step_id] || 0) + 1
    }
    return out
  }, [evidence])

  return { loading, error, evidence, countsByStep }
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/hooks/useLiveEvidence.js
git commit -m "feat(web/hooks): add useLiveEvidence (realtime lab_evidence feed)"
```

---

## Task 7: `PreQuizRunner` component

**Files:**
- Create: `web/src/components/lab/PreQuizRunner.jsx`

- [ ] **Step 1: Implement**

Create `web/src/components/lab/PreQuizRunner.jsx` with:

```jsx
import { Card, Progress, Radio, Input, Button, Space, Alert, Typography, Result } from 'antd'
import { useLabQuiz } from '../../hooks/useLabQuiz'

const { Title, Text } = Typography

/**
 * One-question-per-screen pre-lab quiz runner.
 * Props:
 *   labId
 *   onPassed(result)   — called after a passing submit
 *   onFailed(result)   — called after a failing submit (retry path)
 */
export default function PreQuizRunner({ labId, onPassed, onFailed }) {
  const q = useLabQuiz(labId)

  if (q.loading) return <Card loading />
  if (q.error) return <Alert type="error" message={q.error} showIcon />
  if (q.questions.length === 0) {
    return <Alert type="info" message="Lab này chưa có câu hỏi pre-lab." showIcon />
  }

  // Post-submit result screen
  if (q.result) {
    const passed = q.result.passed
    return (
      <Result
        status={passed ? 'success' : 'warning'}
        title={passed ? 'Bạn đã đạt pre-lab!' : 'Chưa đạt — thử lại'}
        subTitle={`Điểm: ${q.result.score_percent}% · Lần thử: ${q.result.attempt_number}`}
        extra={[
          passed ? (
            <Button key="go" type="primary" onClick={() => onPassed?.(q.result)}>
              Tiếp tục
            </Button>
          ) : (
            <Button key="retry" type="primary" onClick={() => window.location.reload()}>
              Làm lại
            </Button>
          ),
          !passed && (
            <Button key="back" onClick={() => onFailed?.(q.result)}>Quay về</Button>
          ),
        ]}
      />
    )
  }

  const question = q.current
  const percent = Math.round(((q.index + 1) / q.questions.length) * 100)
  const chosen = q.answers[question.id]

  return (
    <Card>
      <Progress percent={percent} showInfo={false} style={{ marginBottom: 16 }} />
      <Text type="secondary">Câu {q.index + 1} / {q.questions.length}</Text>
      <Title level={4} style={{ marginTop: 8 }}>{question.question_text}</Title>
      {question.hint && <Text type="secondary">💡 {question.hint}</Text>}

      <div style={{ marginTop: 24 }}>
        {question.question_type === 'multiple_choice' && question.options && (
          <Radio.Group
            value={chosen}
            onChange={(e) => q.setAnswer(question.id, e.target.value)}
          >
            <Space direction="vertical">
              {Object.entries(question.options).map(([key, text]) => (
                <Radio key={key} value={key}>
                  <Text strong>{key}.</Text> {text}
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        )}
        {question.question_type === 'free_text' && (
          <Input.TextArea
            rows={4}
            value={chosen || ''}
            onChange={(e) => q.setAnswer(question.id, e.target.value)}
            placeholder="Nhập câu trả lời..."
          />
        )}
      </div>

      <Space style={{ marginTop: 24, width: '100%', justifyContent: 'space-between' }}>
        <Button onClick={q.prev} disabled={q.index === 0}>Trước</Button>
        {q.isLast ? (
          <Button
            type="primary"
            loading={q.submitting}
            disabled={!q.canNext}
            onClick={q.submit}
          >
            Nộp bài
          </Button>
        ) : (
          <Button type="primary" disabled={!q.canNext} onClick={q.next}>Tiếp</Button>
        )}
      </Space>
    </Card>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/PreQuizRunner.jsx
git commit -m "feat(web/lab): add PreQuizRunner component"
```

---

## Task 8: `SessionCodeDisplay` component

**Files:**
- Create: `web/src/components/lab/SessionCodeDisplay.jsx`

- [ ] **Step 1: Implement**

Create `web/src/components/lab/SessionCodeDisplay.jsx` with:

```jsx
import { useEffect, useState } from 'react'
import { Card, Typography, Tag } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'

const { Text, Title } = Typography

function formatCountdown(msRemaining) {
  if (msRemaining <= 0) return 'Đã hết hạn'
  const total = Math.floor(msRemaining / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/**
 * Fixed-position card in the bottom-right showing the 6-digit session code
 * and a live countdown to `expires_at`.
 *
 * Props:
 *   code       — string, "482913"
 *   expiresAt  — ISO string
 *   status     — session status (hides display when not ACTIVE)
 */
export default function SessionCodeDisplay({ code, expiresAt, status }) {
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  if (status !== 'ACTIVE') return null

  const remaining = new Date(expiresAt).getTime() - now
  const expired = remaining <= 0

  return (
    <Card
      size="small"
      style={{
        position: 'fixed',
        right: 24,
        bottom: 24,
        zIndex: 100,
        boxShadow: '0 8px 24px rgba(0,0,0,0.18)',
        borderRadius: 14,
        minWidth: 220,
      }}
      styles={{ body: { padding: 14 } }}
    >
      <Text type="secondary" style={{ fontSize: 11, letterSpacing: 1 }}>MÃ SESSION</Text>
      <Title level={3} style={{ margin: '4px 0 8px', letterSpacing: 4, fontFamily: 'monospace' }}>
        {code}
      </Title>
      <Tag color={expired ? 'error' : 'processing'} icon={<ClockCircleOutlined />}>
        {formatCountdown(remaining)}
      </Tag>
    </Card>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/SessionCodeDisplay.jsx
git commit -m "feat(web/lab): add SessionCodeDisplay fixed-position countdown"
```

---

## Task 9: `StepList` (student view)

**Files:**
- Create: `web/src/components/lab/StepList.jsx`

- [ ] **Step 1: Implement**

Create `web/src/components/lab/StepList.jsx` with:

```jsx
import { List, Tag, Typography, Button } from 'antd'
import { CheckCircleOutlined, PlayCircleOutlined, ClockCircleOutlined } from '@ant-design/icons'

const { Text } = Typography

/**
 * Left-column sequential step list for the student practice dashboard.
 *
 * Props:
 *   steps                — ordered lab_steps rows
 *   currentStepId        — session.current_step_id (or null)
 *   countsByStep         — { [stepId]: number } of evidence counted
 *   isLeader             — boolean, gates the "Chọn" button
 *   onSelectStep(step)   — leader-only: make this step current
 */
export default function StepList({
  steps,
  currentStepId,
  countsByStep,
  isLeader,
  onSelectStep,
}) {
  function statusFor(step) {
    const got = countsByStep[step.id] || 0
    const done = step.required_count === 0
      ? step.id !== currentStepId && got === 0 && false // 'none' steps never auto-complete by count
      : got >= step.required_count
    if (done) return { tag: 'success', icon: <CheckCircleOutlined />, label: 'Đã đủ' }
    if (step.id === currentStepId) return { tag: 'processing', icon: <PlayCircleOutlined />, label: 'Đang làm' }
    return { tag: 'default', icon: <ClockCircleOutlined />, label: 'Chờ' }
  }

  return (
    <List
      header={<Text strong>Các bước thực hành</Text>}
      itemLayout="horizontal"
      dataSource={steps}
      renderItem={(step) => {
        const s = statusFor(step)
        const got = countsByStep[step.id] || 0
        return (
          <List.Item
            actions={
              isLeader && step.id !== currentStepId
                ? [<Button size="small" key="sel" onClick={() => onSelectStep?.(step)}>Chọn</Button>]
                : []
            }
          >
            <List.Item.Meta
              title={
                <span>
                  <Tag color={s.tag} icon={s.icon}>{s.label}</Tag>
                  Bước {step.step_order}. {step.title}
                </span>
              }
              description={
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {step.evidence_type === 'none'
                    ? 'Không cần bằng chứng'
                    : `${got} / ${step.required_count} · ${step.evidence_type}`}
                </Text>
              }
            />
          </List.Item>
        )
      }}
    />
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/StepList.jsx
git commit -m "feat(web/lab): add student StepList with per-step status"
```

---

## Task 10: `EvidenceLiveCounter` component

**Files:**
- Create: `web/src/components/lab/EvidenceLiveCounter.jsx`

- [ ] **Step 1: Implement**

Create `web/src/components/lab/EvidenceLiveCounter.jsx` with:

```jsx
import { Progress, Space, Typography } from 'antd'
import { FireOutlined } from '@ant-design/icons'

const { Text } = Typography

/**
 * Ring-style live counter for the current step.
 *
 *   got / required → percent; required=0 shows "N/A" fill.
 */
export default function EvidenceLiveCounter({ got, required }) {
  if (required === 0) {
    return (
      <Space>
        <FireOutlined style={{ color: '#9ca3af' }} />
        <Text type="secondary">Không yêu cầu bằng chứng</Text>
      </Space>
    )
  }
  const percent = Math.min(100, Math.round((got / required) * 100))
  const done = got >= required
  return (
    <Space size={16} align="center">
      <Progress
        type="circle"
        percent={percent}
        size={72}
        status={done ? 'success' : 'active'}
        format={() => `${got}/${required}`}
      />
      <div>
        <Text strong style={{ fontSize: 14, display: 'block' }}>
          {done ? 'Đã đủ bằng chứng' : 'Đang thu thập...'}
        </Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          Cập nhật realtime từ app
        </Text>
      </div>
    </Space>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/EvidenceLiveCounter.jsx
git commit -m "feat(web/lab): add EvidenceLiveCounter ring"
```

---

## Task 11: `StepDetail` component

**Files:**
- Create: `web/src/components/lab/StepDetail.jsx`

- [ ] **Step 1: Implement**

Create `web/src/components/lab/StepDetail.jsx` with:

```jsx
import { useState } from 'react'
import { Card, Button, Upload, Space, Typography, Empty, Alert, message } from 'antd'
import { UploadOutlined, PlayCircleOutlined, StopOutlined, CheckOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import EvidenceLiveCounter from './EvidenceLiveCounter'
import { uploadScreenshotEvidence } from '../../services/labApi'

const { Title, Text } = Typography

/**
 * Right-column detail for the currently selected step.
 *
 * Props:
 *   step, session, isLeader, userId,
 *   countForStep  — number (from useLiveEvidence.countsByStep)
 *   onStart, onEnd  — leader-only callbacks (call RPCs in parent)
 */
export default function StepDetail({
  step,
  session,
  isLeader,
  userId,
  countForStep,
  onStart,
  onEnd,
}) {
  const [uploading, setUploading] = useState(false)

  if (!step) {
    return (
      <Card>
        <Empty description={
          isLeader
            ? 'Chọn một bước ở bên trái để bắt đầu.'
            : 'Leader chưa chọn bước nào.'
        } />
      </Card>
    )
  }

  const isCurrent = session?.current_step_id === step.id

  async function handleUpload(file) {
    setUploading(true)
    const { error } = await uploadScreenshotEvidence({
      sessionId: session.id,
      stepId: step.id,
      userId,
      file,
    })
    setUploading(false)
    if (error) {
      message.error(`Upload lỗi: ${error.message}`)
      return Upload.LIST_IGNORE
    }
    message.success('Đã upload bằng chứng')
    return false // prevent default upload
  }

  return (
    <Card>
      <Title level={4}>
        Bước {step.step_order}. {step.title}
      </Title>
      <Text type="secondary">Loại bằng chứng: <Text code>{step.evidence_type}</Text></Text>

      <div data-color-mode="light" style={{ marginTop: 16 }}>
        <MDEditor.Markdown source={step.instruction || ''} />
      </div>

      {step.hint && (
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          message={<span>💡 {step.hint}</span>}
        />
      )}

      <div style={{ marginTop: 20 }}>
        <EvidenceLiveCounter got={countForStep} required={step.required_count} />
      </div>

      <Space style={{ marginTop: 24, width: '100%' }} wrap>
        {isLeader && !isCurrent && (
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={() => onStart?.(step)}
          >
            Bắt đầu bước này
          </Button>
        )}
        {isLeader && isCurrent && (
          <Button
            danger
            icon={<StopOutlined />}
            onClick={() => onEnd?.()}
          >
            Kết thúc bước
          </Button>
        )}
        {isCurrent && step.evidence_type === 'screenshot' && (
          <Upload
            accept="image/png,image/jpeg"
            showUploadList={false}
            beforeUpload={handleUpload}
          >
            <Button loading={uploading} icon={<UploadOutlined />}>
              Upload screenshot
            </Button>
          </Upload>
        )}
        {isCurrent && step.evidence_type === 'none' && isLeader && (
          <Button
            icon={<CheckOutlined />}
            onClick={() => onEnd?.()}
          >
            Đánh dấu hoàn tất
          </Button>
        )}
      </Space>
    </Card>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/StepDetail.jsx
git commit -m "feat(web/lab): add StepDetail with instruction, counter, screenshot upload"
```

---

## Task 12: `EvidenceInlineViewer` component

**Files:**
- Create: `web/src/components/lab/EvidenceInlineViewer.jsx`

- [ ] **Step 1: Implement**

Create `web/src/components/lab/EvidenceInlineViewer.jsx` with:

```jsx
import { useState } from 'react'
import { Collapse, List, Tag, Typography, Button, Empty, message } from 'antd'
import { EyeOutlined } from '@ant-design/icons'
import { getLabImageSignedUrl } from '../../services/labApi'

const { Text } = Typography

/**
 * Collapsible panel listing evidence rows filtered to a subset of steps.
 * Used on the post-lab page so students can re-examine captured CAN frames
 * / active-test commands / screenshots while writing analysis.
 *
 * Props:
 *   evidence — array of lab_evidence rows (already loaded by parent)
 *   steps    — lab_steps rows (for labeling)
 *   stepIds  — optional filter — only show evidence for these steps
 */
export default function EvidenceInlineViewer({ evidence, steps, stepIds }) {
  const filtered = stepIds
    ? evidence.filter((e) => stepIds.includes(e.step_id))
    : evidence

  if (filtered.length === 0) {
    return <Empty description="Chưa có bằng chứng để xem" />
  }

  const byStep = new Map()
  for (const e of filtered) {
    const list = byStep.get(e.step_id) || []
    list.push(e)
    byStep.set(e.step_id, list)
  }

  const items = Array.from(byStep.entries()).map(([stepId, rows]) => {
    const step = steps.find((s) => s.id === stepId)
    return {
      key: stepId,
      label: (
        <span>
          <Tag color="blue">Bước {step?.step_order ?? '?'}</Tag>
          {step?.title} · {rows.length} bằng chứng
        </span>
      ),
      children: <EvidenceRowList rows={rows} />,
    }
  })

  return <Collapse items={items} />
}

function EvidenceRowList({ rows }) {
  const [signedUrls, setSignedUrls] = useState({})

  async function openImage(path) {
    if (signedUrls[path]) {
      window.open(signedUrls[path], '_blank')
      return
    }
    const { data, error } = await getLabImageSignedUrl(path, 120)
    if (error) {
      message.error(error.message)
      return
    }
    setSignedUrls((prev) => ({ ...prev, [path]: data.signedUrl }))
    window.open(data.signedUrl, '_blank')
  }

  return (
    <List
      size="small"
      dataSource={rows}
      renderItem={(r) => {
        const ts = new Date(r.client_timestamp_ms).toLocaleTimeString('vi-VN')
        if (r.evidence_type === 'raw_frame') {
          const frames = r.payload?.frames?.length ?? 0
          return (
            <List.Item>
              <Text type="secondary">{ts}</Text> · <Tag>raw_frame</Tag>
              <Text> {frames} frame{frames !== 1 ? 's' : ''} trong batch</Text>
            </List.Item>
          )
        }
        if (r.evidence_type === 'active_test') {
          return (
            <List.Item>
              <Text type="secondary">{ts}</Text> · <Tag color="orange">active_test</Tag>
              <Text code>{r.payload?.command || JSON.stringify(r.payload)}</Text>
            </List.Item>
          )
        }
        if (r.evidence_type === 'screenshot') {
          const path = r.payload?.image_path
          return (
            <List.Item
              actions={[
                <Button
                  key="view"
                  size="small"
                  icon={<EyeOutlined />}
                  onClick={() => openImage(path)}
                  disabled={!path}
                >
                  Xem
                </Button>,
              ]}
            >
              <Text type="secondary">{ts}</Text> · <Tag color="purple">screenshot</Tag>
              <Text>{r.payload?.original_name || path}</Text>
            </List.Item>
          )
        }
        return (
          <List.Item>
            <Text type="secondary">{ts}</Text> · <Tag>{r.evidence_type}</Tag>
          </List.Item>
        )
      }}
    />
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/EvidenceInlineViewer.jsx
git commit -m "feat(web/lab): add EvidenceInlineViewer"
```

---

## Task 13: `PostLabQuestion` component

**Files:**
- Create: `web/src/components/lab/PostLabQuestion.jsx`

- [ ] **Step 1: Implement**

Create `web/src/components/lab/PostLabQuestion.jsx` with:

```jsx
import { useState } from 'react'
import { Card, Upload, Button, Typography, Tag, Space, message } from 'antd'
import { UploadOutlined, DeleteOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { supabase } from '../../services/supabase'

const { Text, Title } = Typography

const MAX_IMG_BYTES = 5 * 1024 * 1024

/**
 * Single post-lab question card. Supports free_text (markdown) and
 * image_upload. Value shape:
 *   free_text:     string (markdown)
 *   image_upload:  { path, original_name, size }
 */
export default function PostLabQuestion({
  question,
  value,
  onChange,
  userId,
  sessionId,
  children, // slot for EvidenceInlineViewer
}) {
  const [uploading, setUploading] = useState(false)

  async function handleImageUpload(file) {
    if (file.size > MAX_IMG_BYTES) {
      message.error('File quá 5MB')
      return Upload.LIST_IGNORE
    }
    setUploading(true)
    const ext = (file.name.split('.').pop() || 'png').toLowerCase()
    const path = `${userId}/${sessionId}/post/${question.id}-${Date.now()}.${ext}`
    const { error } = await supabase.storage
      .from('lab-images')
      .upload(path, file, { contentType: file.type || 'image/png' })
    setUploading(false)
    if (error) {
      message.error(error.message)
      return Upload.LIST_IGNORE
    }
    onChange?.({ path, original_name: file.name, size: file.size })
    message.success('Đã upload')
    return false
  }

  function clearImage() {
    onChange?.(null)
  }

  return (
    <Card style={{ marginBottom: 16 }}>
      <Space style={{ marginBottom: 8 }}>
        <Tag color="geekblue">Câu {question.question_order}</Tag>
        <Tag>{question.points} điểm</Tag>
        <Tag color={question.question_type === 'image_upload' ? 'purple' : 'default'}>
          {question.question_type}
        </Tag>
      </Space>
      <Title level={5} style={{ marginTop: 0 }}>{question.question_text}</Title>
      {question.hint && <Text type="secondary">💡 {question.hint}</Text>}

      <div style={{ marginTop: 16 }} data-color-mode="light">
        {question.question_type === 'free_text' && (
          <MDEditor
            value={value || ''}
            onChange={(v) => onChange?.(v ?? '')}
            height={220}
            preview="edit"
          />
        )}
        {question.question_type === 'image_upload' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            {value?.path ? (
              <Space>
                <Text>📎 {value.original_name}</Text>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={clearImage}>
                  Xóa
                </Button>
              </Space>
            ) : (
              <Upload
                accept="image/png,image/jpeg"
                showUploadList={false}
                beforeUpload={handleImageUpload}
              >
                <Button loading={uploading} icon={<UploadOutlined />}>
                  Upload hình (PNG/JPG, &lt;5MB)
                </Button>
              </Upload>
            )}
          </Space>
        )}
      </div>

      {children && <div style={{ marginTop: 16 }}>{children}</div>}
    </Card>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/PostLabQuestion.jsx
git commit -m "feat(web/lab): add PostLabQuestion card (markdown + image upload)"
```

---

## Task 14: `LabsListPage` — `/labs`

**Files:**
- Create: `web/src/pages/LabsListPage.jsx`

- [ ] **Step 1: Implement**

Create `web/src/pages/LabsListPage.jsx` with:

```jsx
import { useEffect, useState, useCallback } from 'react'
import { Card, List, Tag, Button, Space, Typography, Alert, Empty } from 'antd'
import { ExperimentOutlined, ArrowRightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  listAssignedLabsForUser,
  getLatestPreQuizForLab,
  getActiveSessionForGroup,
  getLatestSessionForGroup,
  getMyPostSubmission,
  getMyReportForSession,
} from '../services/labApi'
import {
  computeLabState,
  labStateLabel,
  labStateTagColor,
  LAB_STATES,
} from '../services/labState'

const { Title, Text } = Typography

export default function LabsListPage() {
  const { session } = useAuth()
  const userId = session?.user?.id
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [rows, setRows] = useState([]) // [{ lab, group, role, state, activeSession, lastSession }]

  const load = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    const { data: assignments, error: aErr } = await listAssignedLabsForUser(userId)
    if (aErr) {
      setError(aErr.message)
      setLoading(false)
      return
    }
    const enriched = await Promise.all(
      assignments.map(async ({ lab, group, role }) => {
        const [preQuiz, active, last] = await Promise.all([
          getLatestPreQuizForLab(userId, lab.id),
          getActiveSessionForGroup(group.id),
          getLatestSessionForGroup(group.id),
        ])
        const sessionForPost = active.data || last.data
        const [post, report] = await Promise.all([
          sessionForPost ? getMyPostSubmission(userId, sessionForPost.id) : Promise.resolve({ data: null }),
          sessionForPost ? getMyReportForSession(userId, sessionForPost.id) : Promise.resolve({ data: null }),
        ])
        const state = computeLabState({
          membership: { role, group_id: group.id, lab_id: lab.id },
          latestPreQuiz: preQuiz.data,
          activeSession: active.data,
          lastSession: last.data,
          myPostSubmission: post.data,
          myReport: report.data,
        })
        return {
          lab,
          group,
          role,
          state,
          activeSession: active.data,
          lastSession: last.data,
        }
      })
    )
    setRows(enriched)
    setLoading(false)
  }, [userId])

  useEffect(() => { load() }, [load])

  function cta(row) {
    const labId = row.lab.id
    switch (row.state) {
      case LAB_STATES.PRE_LAB_PENDING:
      case LAB_STATES.PRE_LAB_FAILED:
        return (
          <Button type="primary" onClick={() => navigate(`/labs/${labId}`)}>
            Làm pre-lab
          </Button>
        )
      case LAB_STATES.PRE_LAB_PASSED:
        return (
          <Button type="primary" onClick={() => navigate(`/labs/${labId}`)}>
            {row.role === 'leader' ? 'Bắt đầu thực hành' : 'Chờ leader'}
          </Button>
        )
      case LAB_STATES.PRACTICE_ACTIVE:
        return (
          <Button type="primary" onClick={() =>
            navigate(`/labs/${labId}/session/${row.activeSession.id}`)}>
            Vào dashboard thực hành
          </Button>
        )
      case LAB_STATES.PRACTICE_DONE_POST_PENDING:
        return (
          <Button type="primary" onClick={() =>
            navigate(`/labs/${labId}/session/${row.lastSession.id}/post`)}>
            Làm post-lab
          </Button>
        )
      case LAB_STATES.COMPLETED:
        return (
          <Button onClick={() =>
            navigate(`/labs/${labId}/session/${row.lastSession.id}/report`)}>
            Xem báo cáo
          </Button>
        )
      default:
        return null
    }
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 960, margin: '0 auto' }}>
        <Space style={{ marginBottom: 16 }} align="center">
          <ExperimentOutlined style={{ fontSize: 22, color: '#1565C0' }} />
          <Title level={3} style={{ margin: 0 }}>Thực hành (Labs)</Title>
        </Space>

        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}

        <Card loading={loading}>
          {(!loading && rows.length === 0) && (
            <Empty description="Bạn chưa được gán vào nhóm lab nào. Liên hệ giảng viên." />
          )}
          <List
            itemLayout="horizontal"
            dataSource={rows}
            renderItem={(row) => (
              <List.Item actions={[cta(row)]}>
                <List.Item.Meta
                  title={
                    <Space>
                      <Text strong>{row.lab.code}</Text>
                      <Text>— {row.lab.title}</Text>
                      <Tag color={labStateTagColor(row.state)}>
                        {labStateLabel(row.state)}
                      </Tag>
                      {row.role === 'leader' && <Tag color="gold">Leader</Tag>}
                    </Space>
                  }
                  description={
                    <Text type="secondary">
                      Nhóm: {row.group.name} · Học kỳ: {row.group.semester || '—'}
                    </Text>
                  }
                />
              </List.Item>
            )}
          />
        </Card>

        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Button type="link" onClick={() => navigate('/my-reports')}>
            Lịch sử báo cáo của tôi <ArrowRightOutlined />
          </Button>
        </div>
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/LabsListPage.jsx
git commit -m "feat(web/pages): add LabsListPage with state-machine CTAs"
```

---

## Task 15: `LabOverviewPage` — `/labs/:labId`

**Files:**
- Create: `web/src/pages/LabOverviewPage.jsx`

- [ ] **Step 1: Implement**

Create `web/src/pages/LabOverviewPage.jsx` with:

```jsx
import { useEffect, useState, useCallback } from 'react'
import { Card, Button, Space, Typography, Alert, Tag, message } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import MDEditor from '@uiw/react-md-editor'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  getLab,
  listAssignedLabsForUser,
  getLatestPreQuizForLab,
  getActiveSessionForGroup,
  getLatestSessionForGroup,
  getMyPostSubmission,
  getMyReportForSession,
  rpcStartLabSession,
} from '../services/labApi'
import {
  computeLabState,
  LAB_STATES,
  labStateLabel,
  labStateTagColor,
} from '../services/labState'
import PreQuizRunner from '../components/lab/PreQuizRunner'

const { Title, Text, Paragraph } = Typography

export default function LabOverviewPage() {
  const { labId } = useParams()
  const navigate = useNavigate()
  const { session } = useAuth()
  const userId = session?.user?.id

  const [lab, setLab] = useState(null)
  const [membership, setMembership] = useState(null) // { role, group_id }
  const [group, setGroup] = useState(null)
  const [state, setState] = useState(null)
  const [activeSession, setActiveSession] = useState(null)
  const [lastSession, setLastSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showQuiz, setShowQuiz] = useState(false)
  const [starting, setStarting] = useState(false)

  const load = useCallback(async () => {
    if (!userId || !labId) return
    setLoading(true)
    setError(null)

    const [labRes, assignmentsRes] = await Promise.all([
      getLab(labId),
      listAssignedLabsForUser(userId),
    ])
    if (labRes.error) {
      setError(labRes.error.message)
      setLoading(false)
      return
    }
    setLab(labRes.data)

    const assignment = (assignmentsRes.data || []).find((a) => a.lab.id === labId)
    if (!assignment) {
      setError('Bạn chưa được gán vào nhóm cho lab này.')
      setLoading(false)
      return
    }
    setMembership({ role: assignment.role, group_id: assignment.group.id })
    setGroup(assignment.group)

    const [pre, active, last] = await Promise.all([
      getLatestPreQuizForLab(userId, labId),
      getActiveSessionForGroup(assignment.group.id),
      getLatestSessionForGroup(assignment.group.id),
    ])
    setActiveSession(active.data)
    setLastSession(last.data)

    const sessionForPost = active.data || last.data
    const [post, report] = await Promise.all([
      sessionForPost ? getMyPostSubmission(userId, sessionForPost.id) : Promise.resolve({ data: null }),
      sessionForPost ? getMyReportForSession(userId, sessionForPost.id) : Promise.resolve({ data: null }),
    ])

    setState(computeLabState({
      membership: { role: assignment.role, group_id: assignment.group.id, lab_id: labId },
      latestPreQuiz: pre.data,
      activeSession: active.data,
      lastSession: last.data,
      myPostSubmission: post.data,
      myReport: report.data,
    }))
    setLoading(false)
  }, [userId, labId])

  useEffect(() => { load() }, [load])

  async function handleStartPractice() {
    setStarting(true)
    const { data, error: err } = await rpcStartLabSession(labId)
    setStarting(false)
    if (err) {
      message.error(err.message)
      return
    }
    navigate(`/labs/${labId}/session/${data.session_id}`)
  }

  if (loading) return <AppLayout><Card loading /></AppLayout>

  if (error) {
    return (
      <AppLayout>
        <Alert type="error" message={error} showIcon />
        <Button style={{ marginTop: 12 }} onClick={() => navigate('/labs')}>← Quay lại</Button>
      </AppLayout>
    )
  }

  // Active session exists → redirect shortcut
  if (activeSession && !showQuiz) {
    return (
      <AppLayout>
        <Card>
          <Title level={4}>{lab.code} · {lab.title}</Title>
          <Alert
            type="info"
            showIcon
            message="Nhóm của bạn đang có session thực hành đang chạy."
            style={{ marginBottom: 16 }}
          />
          <Button type="primary" onClick={() =>
            navigate(`/labs/${labId}/session/${activeSession.id}`)
          }>
            Vào dashboard thực hành
          </Button>
        </Card>
      </AppLayout>
    )
  }

  // Pre-lab quiz runner surface
  if (showQuiz) {
    return (
      <AppLayout>
        <div style={{ maxWidth: 760, margin: '0 auto' }}>
          <Button onClick={() => { setShowQuiz(false); load() }} style={{ marginBottom: 12 }}>
            ← Quay về tổng quan
          </Button>
          <PreQuizRunner
            labId={labId}
            onPassed={() => { setShowQuiz(false); load() }}
            onFailed={() => { setShowQuiz(false); load() }}
          />
        </div>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 860, margin: '0 auto' }}>
        <Button onClick={() => navigate('/labs')} style={{ marginBottom: 12 }}>← Danh sách lab</Button>
        <Card>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Space>
              <Title level={3} style={{ margin: 0 }}>{lab.code} · {lab.title}</Title>
              <Tag color={labStateTagColor(state)}>{labStateLabel(state)}</Tag>
              {membership?.role === 'leader' && <Tag color="gold">Leader</Tag>}
            </Space>
            <Text type="secondary">Nhóm: {group?.name} · Học kỳ: {group?.semester || '—'}</Text>
            <Paragraph>
              <Text strong>Ngưỡng đậu pre-quiz:</Text> {lab.pre_quiz_pass_threshold}%
            </Paragraph>

            <div data-color-mode="light">
              <MDEditor.Markdown source={lab.description || '*Chưa có mô tả.*'} />
            </div>

            <Space wrap style={{ marginTop: 12 }}>
              {state === LAB_STATES.PRE_LAB_PENDING && (
                <Button type="primary" onClick={() => setShowQuiz(true)}>Làm pre-lab</Button>
              )}
              {state === LAB_STATES.PRE_LAB_FAILED && (
                <Button type="primary" onClick={() => setShowQuiz(true)}>Làm lại pre-lab</Button>
              )}
              {state === LAB_STATES.PRE_LAB_PASSED && membership?.role === 'leader' && (
                <Button type="primary" loading={starting} onClick={handleStartPractice}>
                  Tạo session & bắt đầu thực hành
                </Button>
              )}
              {state === LAB_STATES.PRE_LAB_PASSED && membership?.role !== 'leader' && (
                <Alert
                  type="info"
                  showIcon
                  message="Chờ leader của nhóm tạo session để bắt đầu."
                />
              )}
              {state === LAB_STATES.PRACTICE_DONE_POST_PENDING && lastSession && (
                <Button type="primary" onClick={() =>
                  navigate(`/labs/${labId}/session/${lastSession.id}/post`)
                }>
                  Làm post-lab
                </Button>
              )}
              {state === LAB_STATES.COMPLETED && lastSession && (
                <Button onClick={() =>
                  navigate(`/labs/${labId}/session/${lastSession.id}/report`)
                }>
                  Xem báo cáo
                </Button>
              )}
            </Space>
          </Space>
        </Card>
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/LabOverviewPage.jsx
git commit -m "feat(web/pages): add LabOverviewPage (pre-lab entry + start-practice CTA)"
```

---

## Task 16: `LabSessionPage` — `/labs/:labId/session/:sid`

**Files:**
- Create: `web/src/pages/LabSessionPage.jsx`

- [ ] **Step 1: Implement**

Create `web/src/pages/LabSessionPage.jsx` with:

```jsx
import { useEffect, useState, useMemo } from 'react'
import { Row, Col, Card, Button, Space, Typography, Alert, message, Modal } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { useLabSession } from '../hooks/useLabSession'
import { useLiveEvidence } from '../hooks/useLiveEvidence'
import {
  listSteps,
  listGroupMembers,
} from '../services/labApi'
import StepList from '../components/lab/StepList'
import StepDetail from '../components/lab/StepDetail'
import SessionCodeDisplay from '../components/lab/SessionCodeDisplay'

const { Title, Text } = Typography

export default function LabSessionPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const { session, loading, error, startStep, endStep, completeSession } = useLabSession(sid)
  const { evidence, countsByStep } = useLiveEvidence(sid)

  const [steps, setSteps] = useState([])
  const [stepsLoading, setStepsLoading] = useState(true)
  const [isLeader, setIsLeader] = useState(false)
  const [completing, setCompleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setStepsLoading(true)
      const { data, error: err } = await listSteps(labId)
      if (!cancelled && !err) setSteps(data || [])
      setStepsLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [labId])

  useEffect(() => {
    let cancelled = false
    async function loadRole() {
      if (!session?.group_id || !userId) return
      const { data, error: err } = await listGroupMembers(session.group_id)
      if (cancelled || err) return
      const mine = (data || []).find((m) => m.user_id === userId)
      setIsLeader(mine?.role === 'leader')
    }
    loadRole()
    return () => { cancelled = true }
  }, [session?.group_id, userId])

  const currentStep = useMemo(
    () => steps.find((s) => s.id === session?.current_step_id) || null,
    [steps, session?.current_step_id]
  )

  const allStepsSatisfied = useMemo(() => {
    if (steps.length === 0) return false
    return steps.every((s) => {
      if (s.evidence_type === 'none') {
        // "none" steps are satisfied only if they were at some point the current step
        // AND subsequently ended. We approximate by requiring required_count=0 AND
        // that the step is not currently active. Leader-advanced workflow means this
        // holds once the leader clicks "Đánh dấu hoàn tất".
        return s.id !== session?.current_step_id
      }
      return (countsByStep[s.id] || 0) >= s.required_count
    })
  }, [steps, countsByStep, session?.current_step_id])

  async function handleStart(step) {
    const { ok, error: err } = await startStep(step.id)
    if (!ok) message.error(err)
  }

  async function handleEnd() {
    const { ok, error: err } = await endStep()
    if (!ok) message.error(err)
  }

  async function handleComplete() {
    Modal.confirm({
      title: 'Kết thúc thực hành?',
      content: 'Bạn sẽ chuyển sang phần post-lab. Không thể thêm evidence sau khi kết thúc.',
      okText: 'Kết thúc',
      cancelText: 'Hủy',
      onOk: async () => {
        setCompleting(true)
        const { ok, error: err } = await completeSession()
        setCompleting(false)
        if (!ok) { message.error(err); return }
        navigate(`/labs/${labId}/session/${sid}/post`)
      },
    })
  }

  if (loading || stepsLoading) return <AppLayout><Card loading /></AppLayout>
  if (error) return <AppLayout><Alert type="error" message={error} showIcon /></AppLayout>
  if (!session) return <AppLayout><Alert type="warning" message="Session không tồn tại." /></AppLayout>

  if (session.status !== 'ACTIVE') {
    return (
      <AppLayout>
        <Alert
          type={session.status === 'COMPLETED' ? 'success' : 'warning'}
          message={`Session ${session.status}`}
          description="Chuyển sang trang post-lab để tiếp tục."
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Space>
          <Button type="primary" onClick={() => navigate(`/labs/${labId}/session/${sid}/post`)}>
            Làm post-lab
          </Button>
          <Button onClick={() => navigate('/labs')}>← Danh sách lab</Button>
        </Space>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <Space style={{ marginBottom: 12 }}>
        <Button onClick={() => navigate('/labs')}>← Danh sách lab</Button>
        <Title level={4} style={{ margin: 0 }}>Dashboard thực hành</Title>
        {isLeader ? <Text type="success">(Bạn là leader)</Text> : <Text type="secondary">(Thành viên)</Text>}
      </Space>

      <Row gutter={16}>
        <Col xs={24} md={10}>
          <Card>
            <StepList
              steps={steps}
              currentStepId={session.current_step_id}
              countsByStep={countsByStep}
              isLeader={isLeader}
              onSelectStep={handleStart}
            />
            {allStepsSatisfied && (
              <Button
                block
                size="large"
                type="primary"
                loading={completing}
                style={{ marginTop: 16 }}
                onClick={handleComplete}
              >
                Kết thúc thực hành & làm Post-lab
              </Button>
            )}
          </Card>
        </Col>
        <Col xs={24} md={14}>
          <StepDetail
            step={currentStep}
            session={session}
            isLeader={isLeader}
            userId={userId}
            countForStep={currentStep ? (countsByStep[currentStep.id] || 0) : 0}
            onStart={handleStart}
            onEnd={handleEnd}
          />
          <Card style={{ marginTop: 16 }} size="small">
            <Text type="secondary">Tổng evidence đã thu: </Text>
            <Text strong>{evidence.length}</Text>
          </Card>
        </Col>
      </Row>

      <SessionCodeDisplay
        code={session.session_code}
        expiresAt={session.expires_at}
        status={session.status}
      />
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/LabSessionPage.jsx
git commit -m "feat(web/pages): add LabSessionPage (realtime practice dashboard)"
```

---

## Task 17: `LabPostLabPage` — `/labs/:labId/session/:sid/post`

**Files:**
- Create: `web/src/pages/LabPostLabPage.jsx`

- [ ] **Step 1: Implement**

Create `web/src/pages/LabPostLabPage.jsx` with:

```jsx
import { useEffect, useRef, useState, useCallback } from 'react'
import { Card, Button, Space, Typography, Alert, message, Tag } from 'antd'
import { SaveOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  listQuestions,
  listSteps,
  listEvidenceForSession,
  getMyPostSubmission,
  saveDraftPostSubmission,
  finalizePostSubmission,
  getLatestPreQuizForLab,
} from '../services/labApi'
import PostLabQuestion from '../components/lab/PostLabQuestion'
import EvidenceInlineViewer from '../components/lab/EvidenceInlineViewer'

const { Title, Text } = Typography

const AUTOSAVE_MS = 10_000

export default function LabPostLabPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const [questions, setQuestions] = useState([])
  const [steps, setSteps] = useState([])
  const [evidence, setEvidence] = useState([])
  const [answers, setAnswers] = useState({})        // { [qid]: string | {path,...} }
  const [uploadedImages, setUploadedImages] = useState({}) // same shape for image_upload qids
  const [preQuizPassed, setPreQuizPassed] = useState(false)
  const [submission, setSubmission] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lastSavedAt, setLastSavedAt] = useState(null)
  const [savingNow, setSavingNow] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const dirtyRef = useRef(false)

  const load = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    const [qRes, sRes, eRes, subRes, pqRes] = await Promise.all([
      listQuestions(labId, 'post_lab'),
      listSteps(labId),
      listEvidenceForSession(sid),
      getMyPostSubmission(userId, sid),
      getLatestPreQuizForLab(userId, labId),
    ])
    if (qRes.error) { setError(qRes.error.message); setLoading(false); return }
    setQuestions(qRes.data || [])
    setSteps(sRes.data || [])
    setEvidence(eRes.data || [])
    setSubmission(subRes.data || null)
    setPreQuizPassed(!!pqRes.data?.passed)

    // Split existing answers into free_text vs image_upload buckets
    const sub = subRes.data
    if (sub?.answers) setAnswers(sub.answers)
    if (sub?.uploaded_images) setUploadedImages(sub.uploaded_images)
    setLoading(false)
  }, [userId, labId, sid])

  useEffect(() => { load() }, [load])

  // Merge answers + uploaded images into the single `answers` jsonb + separate
  // `uploaded_images` array for persistence.
  function buildPersistencePayload() {
    const plainAnswers = { ...answers }
    // For image_upload questions, answers holds the latest object too; we also
    // track them in uploadedImages for the aggregated list used by the PDF.
    const imageList = Object.entries(uploadedImages)
      .filter(([, v]) => v?.path)
      .map(([qid, v]) => ({ question_id: qid, ...v }))
    return { plainAnswers, imageList }
  }

  function handleAnswerChange(question, value) {
    dirtyRef.current = true
    setAnswers((prev) => ({ ...prev, [question.id]: value }))
    if (question.question_type === 'image_upload') {
      setUploadedImages((prev) => {
        const next = { ...prev }
        if (value) next[question.id] = value
        else delete next[question.id]
        return next
      })
    }
  }

  const saveDraft = useCallback(async () => {
    if (!userId || submission?.is_draft === false) return
    setSavingNow(true)
    const { plainAnswers, imageList } = buildPersistencePayload()
    const { data, error: err } = await saveDraftPostSubmission(
      userId, sid, plainAnswers, imageList
    )
    setSavingNow(false)
    if (err) {
      message.error(`Auto-save lỗi: ${err.message}`)
      return
    }
    setSubmission(data)
    setLastSavedAt(new Date())
    dirtyRef.current = false
  }, [userId, sid, submission?.is_draft, answers, uploadedImages])

  // 10s auto-save
  useEffect(() => {
    if (submission?.is_draft === false) return
    const t = setInterval(() => {
      if (dirtyRef.current) saveDraft()
    }, AUTOSAVE_MS)
    return () => clearInterval(t)
  }, [saveDraft, submission?.is_draft])

  // Save on unmount too
  useEffect(() => {
    return () => {
      if (dirtyRef.current) saveDraft()
    }
  }, [saveDraft])

  async function handleSubmit() {
    if (!preQuizPassed) {
      message.warning('Bạn cần hoàn thành pre-lab trước')
      return
    }
    // Require an answer on every question
    const missing = questions.find((q) => {
      const v = answers[q.id]
      if (q.question_type === 'image_upload') return !v?.path
      return !v || (typeof v === 'string' && v.trim() === '')
    })
    if (missing) {
      message.warning(`Câu ${missing.question_order} chưa có câu trả lời`)
      return
    }
    setSubmitting(true)
    const { plainAnswers, imageList } = buildPersistencePayload()
    const { data, error: err } = await finalizePostSubmission(
      userId, sid, plainAnswers, imageList
    )
    setSubmitting(false)
    if (err) { message.error(err.message); return }
    setSubmission(data)
    message.success('Đã nộp post-lab')
    navigate(`/labs/${labId}/session/${sid}/report`)
  }

  if (loading) return <AppLayout><Card loading /></AppLayout>
  if (error) return <AppLayout><Alert type="error" message={error} showIcon /></AppLayout>

  const readOnly = submission?.is_draft === false

  return (
    <AppLayout>
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <Space style={{ marginBottom: 12 }} wrap>
          <Button onClick={() => navigate('/labs')}>← Danh sách lab</Button>
          <Title level={4} style={{ margin: 0 }}>Post-lab · Phân tích</Title>
          {readOnly && <Tag color="success" icon={<CheckCircleOutlined />}>Đã nộp</Tag>}
        </Space>

        {!preQuizPassed && (
          <Alert
            type="warning"
            showIcon
            message="Bạn cần hoàn thành pre-lab trước"
            description="Nút Nộp bài sẽ bị khóa cho đến khi bạn đậu pre-lab."
            style={{ marginBottom: 16 }}
          />
        )}

        {!readOnly && (
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space>
              <SaveOutlined spin={savingNow} />
              <Text type="secondary">
                {savingNow
                  ? 'Đang lưu nháp...'
                  : lastSavedAt
                    ? `Đã lưu nháp lúc ${lastSavedAt.toLocaleTimeString('vi-VN')}`
                    : 'Chưa có thay đổi — tự lưu mỗi 10s'}
              </Text>
              <Button size="small" onClick={saveDraft} loading={savingNow}>Lưu nháp ngay</Button>
            </Space>
          </Card>
        )}

        {questions.map((q) => (
          <PostLabQuestion
            key={q.id}
            question={q}
            value={answers[q.id]}
            onChange={(v) => !readOnly && handleAnswerChange(q, v)}
            userId={userId}
            sessionId={sid}
          >
            <EvidenceInlineViewer evidence={evidence} steps={steps} />
          </PostLabQuestion>
        ))}

        <Space style={{ width: '100%', justifyContent: 'flex-end', marginTop: 16 }}>
          {!readOnly && (
            <Button
              type="primary"
              size="large"
              loading={submitting}
              disabled={!preQuizPassed}
              onClick={handleSubmit}
            >
              Nộp post-lab
            </Button>
          )}
          {readOnly && (
            <Button type="primary" size="large" onClick={() =>
              navigate(`/labs/${labId}/session/${sid}/report`)
            }>
              Sang trang báo cáo
            </Button>
          )}
        </Space>
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/LabPostLabPage.jsx
git commit -m "feat(web/pages): add LabPostLabPage with 10s draft autosave and pre-lab gating"
```

---

## Task 18: `LabReportPage` — `/labs/:labId/session/:sid/report` (Phase-5 stub)

**Files:**
- Create: `web/src/pages/LabReportPage.jsx`

**Why this is a stub:** Phase 5 owns the full PDF template, SHA-256 hashing, and storage upload. Phase 4 only needs a route that (a) blocks navigation when post-lab hasn't been submitted, (b) shows a placeholder while Phase 5 is incomplete, (c) links back on completion.

- [ ] **Step 1: Implement**

Create `web/src/pages/LabReportPage.jsx` with:

```jsx
import { useEffect, useState, useCallback } from 'react'
import { Card, Button, Alert, Typography, Space, Tag, Spin } from 'antd'
import { FilePdfOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  getMyPostSubmission,
  getMyReportForSession,
  getReportSignedUrl,
} from '../services/labApi'

const { Title, Text } = Typography

export default function LabReportPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const [loading, setLoading] = useState(true)
  const [submission, setSubmission] = useState(null)
  const [report, setReport] = useState(null)
  const [downloadUrl, setDownloadUrl] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    const [sub, rep] = await Promise.all([
      getMyPostSubmission(userId, sid),
      getMyReportForSession(userId, sid),
    ])
    setSubmission(sub.data)
    setReport(rep.data)
    if (rep.data?.pdf_storage_path) {
      const { data, error: err } = await getReportSignedUrl(rep.data.pdf_storage_path, 300)
      if (!err) setDownloadUrl(data.signedUrl)
    }
    setLoading(false)
  }, [userId, sid])

  useEffect(() => { load() }, [load])

  if (loading) return <AppLayout><Spin /></AppLayout>

  if (!submission || submission.is_draft) {
    return (
      <AppLayout>
        <Alert
          type="warning"
          showIcon
          message="Bạn chưa nộp post-lab"
          description="Hoàn thành và nộp post-lab để sinh báo cáo PDF."
          style={{ marginBottom: 16 }}
        />
        <Button
          type="primary"
          onClick={() => navigate(`/labs/${labId}/session/${sid}/post`)}
        >
          Sang trang post-lab
        </Button>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 860, margin: '0 auto' }}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/labs')}>
            Danh sách lab
          </Button>
          <Title level={4} style={{ margin: 0 }}>Báo cáo lab</Title>
          {report ? <Tag color="success">Đã phát hành</Tag> : <Tag>Chưa phát hành</Tag>}
        </Space>

        <Card>
          {!report && (
            <Alert
              type="info"
              showIcon
              message="Template PDF sẽ được thêm ở Phase 5"
              description={
                <>
                  <div>Post-lab đã được ghi nhận. Tại Phase 5, nút "Tạo PDF" sẽ
                  xuất hiện ở đây để render template, tính hash SHA-256, upload
                  lên bucket <Text code>lab-reports</Text> và tải xuống về máy.</div>
                </>
              }
              style={{ marginBottom: 16 }}
            />
          )}

          {report && (
            <>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  <FilePdfOutlined style={{ fontSize: 28, color: '#d97706' }} />
                  <div>
                    <Text strong>Báo cáo đã phát hành</Text>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        Hash: <Text code>{report.content_hash?.slice(0, 16)}...</Text>
                      </Text>
                    </div>
                  </div>
                </Space>
                {downloadUrl && (
                  <Button
                    type="primary"
                    icon={<FilePdfOutlined />}
                    href={downloadUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Tải PDF (link có hạn 5 phút)
                  </Button>
                )}
              </Space>
            </>
          )}
        </Card>
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/LabReportPage.jsx
git commit -m "feat(web/pages): add LabReportPage (Phase-5 stub, post-lab gating + download path)"
```

---

## Task 19: `MyReportsPage` — `/my-reports`

**Files:**
- Create: `web/src/pages/MyReportsPage.jsx`

- [ ] **Step 1: Implement**

Create `web/src/pages/MyReportsPage.jsx` with:

```jsx
import { useEffect, useState, useCallback } from 'react'
import { Card, Table, Button, Typography, Space, Alert } from 'antd'
import { DownloadOutlined, HistoryOutlined } from '@ant-design/icons'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { listMyReports, getReportSignedUrl } from '../services/labApi'

const { Title, Text } = Typography

function formatBytes(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

export default function MyReportsPage() {
  const { session: auth } = useAuth()
  const userId = auth?.user?.id
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [downloading, setDownloading] = useState({})

  const load = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    const { data, error: err } = await listMyReports(userId)
    if (err) setError(err.message)
    else setRows(data || [])
    setLoading(false)
  }, [userId])

  useEffect(() => { load() }, [load])

  async function handleDownload(row) {
    setDownloading((d) => ({ ...d, [row.id]: true }))
    const { data, error: err } = await getReportSignedUrl(row.pdf_storage_path, 300)
    setDownloading((d) => ({ ...d, [row.id]: false }))
    if (err || !data?.signedUrl) return
    window.open(data.signedUrl, '_blank')
  }

  const columns = [
    { title: 'Lab', dataIndex: ['session', 'lab', 'code'], render: (v, row) =>
        <Text strong>{v} · {row.session?.lab?.title}</Text> },
    { title: 'Nhóm', dataIndex: ['session', 'group', 'name'] },
    { title: 'Mã session', dataIndex: ['session', 'session_code'], render: (v) =>
        <Text code>{v}</Text> },
    { title: 'Dung lượng', dataIndex: 'file_size_bytes', render: formatBytes },
    { title: 'Phát hành', dataIndex: 'generated_at',
      render: (v) => v ? new Date(v).toLocaleString('vi-VN') : '—' },
    {
      title: '',
      key: 'action',
      align: 'right',
      render: (_, row) => (
        <Button
          icon={<DownloadOutlined />}
          loading={!!downloading[row.id]}
          onClick={() => handleDownload(row)}
        >
          Tải
        </Button>
      ),
    },
  ]

  return (
    <AppLayout>
      <div style={{ maxWidth: 1000, margin: '0 auto' }}>
        <Space style={{ marginBottom: 16 }}>
          <HistoryOutlined style={{ fontSize: 22, color: '#1565C0' }} />
          <Title level={3} style={{ margin: 0 }}>Báo cáo của tôi</Title>
        </Space>
        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 12 }} />}
        <Card>
          <Table
            dataSource={rows}
            columns={columns}
            rowKey="id"
            loading={loading}
            size="small"
            pagination={{ pageSize: 10, showTotal: (t) => `${t} báo cáo` }}
            locale={{ emptyText: 'Bạn chưa phát hành báo cáo nào.' }}
          />
        </Card>
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/MyReportsPage.jsx
git commit -m "feat(web/pages): add MyReportsPage (student PDF history)"
```

---

## Task 20: Wire routes + nav menu

**Files:**
- Modify: `web/src/App.jsx`
- Modify: `web/src/components/AppLayout.jsx`

- [ ] **Step 1: Register routes in `App.jsx`**

Modify `web/src/App.jsx` so its final content matches exactly:

```jsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import ProtectedRoute from './components/ProtectedRoute'
import AdminRoute from './components/AdminRoute'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import DashboardPage from './pages/DashboardPage'
import AdminPage from './pages/AdminPage'
import WiringPage from './pages/WiringPage'
import LabsListPage from './pages/LabsListPage'
import LabOverviewPage from './pages/LabOverviewPage'
import LabSessionPage from './pages/LabSessionPage'
import LabPostLabPage from './pages/LabPostLabPage'
import LabReportPage from './pages/LabReportPage'
import MyReportsPage from './pages/MyReportsPage'

const theme = {
  token: {
    colorPrimary: '#1565C0',
    colorLink: '#1565C0',
    borderRadius: 8,
    fontFamily: "'Inter', sans-serif",
  },
}

export default function App() {
  return (
    <ConfigProvider theme={theme}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="/wiring" element={<ProtectedRoute><WiringPage /></ProtectedRoute>} />
          <Route path="/labs" element={<ProtectedRoute><LabsListPage /></ProtectedRoute>} />
          <Route path="/labs/:labId" element={<ProtectedRoute><LabOverviewPage /></ProtectedRoute>} />
          <Route path="/labs/:labId/session/:sid" element={<ProtectedRoute><LabSessionPage /></ProtectedRoute>} />
          <Route path="/labs/:labId/session/:sid/post" element={<ProtectedRoute><LabPostLabPage /></ProtectedRoute>} />
          <Route path="/labs/:labId/session/:sid/report" element={<ProtectedRoute><LabReportPage /></ProtectedRoute>} />
          <Route path="/my-reports" element={<ProtectedRoute><MyReportsPage /></ProtectedRoute>} />
          <Route path="/admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}
```

- [ ] **Step 2: Add the "Labs" menu entry in `AppLayout.jsx`**

In `web/src/components/AppLayout.jsx`, update the icon import and `menuItems`:

Replace the icons import block (currently):

```jsx
import {
  DashboardOutlined,
  CrownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ApartmentOutlined,
} from '@ant-design/icons'
```

with:

```jsx
import {
  DashboardOutlined,
  CrownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ApartmentOutlined,
  ExperimentOutlined,
  HistoryOutlined,
} from '@ant-design/icons'
```

Then replace the `menuItems` array (currently):

```jsx
  const menuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
    { key: '/wiring', icon: <ApartmentOutlined />, label: 'Wiring Diagram' },
    ...(role === 'admin' || role === 'moderator'
      ? [{ key: '/admin', icon: <CrownOutlined />, label: 'Admin Panel' }]
      : []),
  ]
```

with:

```jsx
  const menuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
    { key: '/labs', icon: <ExperimentOutlined />, label: 'Labs' },
    { key: '/my-reports', icon: <HistoryOutlined />, label: 'Báo cáo của tôi' },
    { key: '/wiring', icon: <ApartmentOutlined />, label: 'Wiring Diagram' },
    ...(role === 'admin' || role === 'moderator'
      ? [{ key: '/admin', icon: <CrownOutlined />, label: 'Admin Panel' }]
      : []),
  ]
```

- [ ] **Step 3: Verify build + manual smoke test**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

Then run: `cd web && npm run dev`

Manual smoke test using a real student account assigned to a group:

1. Log in → Dashboard loads unchanged.
2. Sidebar shows new entries **Labs** and **Báo cáo của tôi**.
3. Click **Labs** → see your assigned lab(s) with a status tag.
4. Click **Làm pre-lab** → answer all MC → submit. Result screen shows score.
5. If passed and you are leader → **Tạo session & bắt đầu thực hành** → routed to `/labs/:id/session/:sid`. Session code card appears bottom-right with countdown.
6. On phone with Lab Mode app (if available), enter the code. Raw Monitor evidence appears in the dashboard evidence counter in real time (should tick up within seconds of each batch).
7. Leader clicks **Bắt đầu bước này** on any step → `current_step_id` updates, StepDetail switches, `step_started_at` visible on subsequent inserts.
8. For a `screenshot` step — click **Upload screenshot** → file uploads to `lab-images`, evidence counter increments by 1.
9. Once all steps meet required counts, click **Kết thúc thực hành & làm Post-lab** → session status becomes `COMPLETED`, navigates to post-lab page.
10. On post-lab page: type into a question → wait 10s → see "Đã lưu nháp lúc …". Reload page → draft persists.
11. Upload a flowchart image on the `image_upload` question → the answer card shows the filename + "Xóa".
12. Click **Nộp post-lab** → lands on `/…/report`. Phase-5 stub alert appears.
13. Go to **Báo cáo của tôi** → no rows until Phase 5 actually inserts `lab_reports`.

Regression (all should still work):
- `/dashboard` loads, profile cards unchanged.
- `/admin` (as admin) loads, Phase-3 tabs unchanged.
- `/wiring` loads unchanged.
- Logout.

- [ ] **Step 4: Commit**

```bash
git add web/src/App.jsx web/src/components/AppLayout.jsx
git commit -m "feat(web): wire 6 student-flow routes + Labs nav entry"
```

---

## Self-Review

**Spec coverage:**
- 5.1 (6 routes) — Tasks 14–19, wired in Task 20.
- 5.2 (state machine + `/labs` rendering) — Task 3 (pure helper), Task 14 (render).
- 5.3 (pre-lab quiz runner, 1 per screen, progress bar) — Tasks 4 + 7, used by Task 15.
- 5.4 (split practice dashboard, live counter, finish button) — Tasks 9–11, 16; realtime via Tasks 5–6.
- 5.5 (post-lab, 10s autosave, evidence inline viewer) — Tasks 12–13, 17.
- 5.6 (report page with hash + upload) — **Phase 5 deferred**; stub in Task 18 explicitly notes the split.
- 5.7 (component filename list) — all 8 components + 3 hooks + 1 service file covered.
- Pre-lab gating (leader starts, members can't submit post-lab without pass) — Task 15 (leader-only Start CTA), Task 17 (`preQuizPassed` gate on Submit).
- Screenshot web upload (Section 3.13) — Task 2 (`uploadScreenshotEvidence`) + Task 11.
- Missing-but-needed `complete_lab_session` RPC — called out and implemented in Task 1.

**Placeholder scan:** no "TBD", no "implement later", every component has full JSX, every hook has full implementation. The only intentional stub is `LabReportPage` where Phase 5 is explicitly the owner — the stub still does useful work (post-lab gate + download link if a report already exists).

**Type consistency:**
- `lab.id`, `group.id`, `step.id`, `question.id` used consistently as uuid strings.
- `membership` shape = `{ role, group_id, lab_id }` across `labState.js`, `LabsListPage`, `LabOverviewPage`.
- `session` shape from `useLabSession` returns the columns `useLiveEvidence` and `SessionCodeDisplay` read — `status`, `current_step_id`, `session_code`, `expires_at`, `group_id`.
- RPC wrappers in Task 2 use the exact parameter names from Phase 1 (`p_lab_id`, `p_session_id`, `p_step_id`, `p_answers`).
- `countsByStep` key naming identical in Tasks 6, 9, 11, 16.

No issues found after review.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-lab-phase-4-student.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
