-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Verification (Phase 1)
--  NOTE: run each section independently in the Supabase SQL Editor.
--        Replace <PLACEHOLDER> values with real IDs from your environment.
--        This file is NOT part of the migration; it is a smoke-test harness.
-- ════════════════════════════════════════════════════════════════════════════

-- ── A. Existence checks ─────────────────────────────────────────────────────

-- A1. All 10 lab tables exist.
SELECT table_name
FROM   information_schema.tables
WHERE  table_schema = 'public'
  AND  table_name IN (
         'labs','lab_steps','lab_questions','lab_groups','lab_group_members',
         'lab_sessions','lab_evidence','lab_pre_quiz_submissions',
         'lab_post_submissions','lab_reports'
       )
ORDER  BY table_name;
-- Expected: 10 rows.

-- A2. All 10 tables have RLS enabled.
SELECT c.relname, c.relrowsecurity
FROM   pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE  n.nspname = 'public'
  AND  c.relname IN (
         'labs','lab_steps','lab_questions','lab_groups','lab_group_members',
         'lab_sessions','lab_evidence','lab_pre_quiz_submissions',
         'lab_post_submissions','lab_reports'
       );
-- Expected: relrowsecurity = true for every row.

-- A3. All 6 RPCs + helpers exist.
SELECT proname
FROM   pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE  n.nspname = 'public'
  AND  proname IN (
         'is_staff','user_is_in_group','generate_lab_session_code',
         'start_lab_session','validate_lab_code','submit_pre_quiz',
         'set_current_step','end_current_step','expire_old_sessions',
         'lab_evidence_assign_step','lab_group_members_check_unique_lab',
         'lab_touch_updated_at'
       )
ORDER  BY proname;
-- Expected: 12 rows.

-- A4. Storage buckets exist and are private.
SELECT id, public FROM storage.buckets WHERE id IN ('lab-reports','lab-images');
-- Expected: 2 rows, public = false.

-- A5. Realtime publication includes lab_evidence.
SELECT tablename FROM pg_publication_tables
WHERE  pubname = 'supabase_realtime' AND schemaname = 'public'
  AND  tablename IN ('lab_evidence','lab_sessions');
-- Expected: 2 rows.

-- ── B. RLS behavior (run while authenticated as a STUDENT, not staff) ───────

-- B1. Student sees only published labs.
--     Create 1 published + 1 draft lab as staff, then run as student:
SELECT code, is_published FROM public.labs ORDER BY code;
-- Expected: only published labs returned.

-- B2. Student cannot SELECT a group they are not a member of.
--     Seed two groups (A, B) for same lab, add student only to A, then:
SELECT id, name FROM public.lab_groups WHERE lab_id = '<LAB_ID>';
-- Expected: only group A returned.

-- B3. Student cannot INSERT evidence into a session whose group they don't belong to.
INSERT INTO public.lab_evidence
    (session_id, submitted_by, evidence_type, payload, client_timestamp_ms)
VALUES ('<OTHER_GROUP_SESSION_ID>', auth.uid(), 'raw_frame', '{}'::jsonb, 0);
-- Expected: RLS violation error.

-- B4. Student CAN INSERT evidence into an ACTIVE session of their own group.
INSERT INTO public.lab_evidence
    (session_id, submitted_by, evidence_type, payload, client_timestamp_ms)
VALUES ('<OWN_ACTIVE_SESSION_ID>', auth.uid(), 'raw_frame',
        '{"frames":[]}'::jsonb, 0)
RETURNING id, step_id;
-- Expected: one row; step_id equals the session's current_step_id
--           if set_current_step was previously called.

-- B5. Student cannot INSERT into an EXPIRED session.
UPDATE public.lab_sessions SET status = 'EXPIRED' WHERE id = '<OWN_SESSION_ID>';
-- (then as student)
INSERT INTO public.lab_evidence
    (session_id, submitted_by, evidence_type, payload, client_timestamp_ms)
VALUES ('<OWN_SESSION_ID>', auth.uid(), 'raw_frame', '{}'::jsonb, 0);
-- Expected: RLS violation error.

-- B6. Student sees only own pre-quiz submissions.
SELECT user_id FROM public.lab_pre_quiz_submissions;
-- Expected: only rows where user_id = auth.uid().

-- ── C. RPC behavior ─────────────────────────────────────────────────────────

-- C1. start_lab_session fails for non-leader.
SELECT public.start_lab_session('<LAB_ID>');
-- Expected (member, not leader): 'Only the group leader may start...'

-- C2. start_lab_session fails if leader has not passed pre-lab.
-- Expected: 'Leader must pass the pre-lab quiz...'

-- C3. start_lab_session succeeds for eligible leader; returns 6-digit code.
SELECT public.start_lab_session('<LAB_ID>');
-- Expected: { session_id, session_code (6 digits), expires_at }.

-- C4. validate_lab_code returns session info for member, errors for outsider.
SELECT public.validate_lab_code('123456');

-- C5. submit_pre_quiz auto-grades multiple choice correctly.
--    Seed 5 MC questions with known correct_answer, call with known answers:
SELECT public.submit_pre_quiz(
    '<LAB_ID>',
    jsonb_build_object('<Q1_ID>','A','<Q2_ID>','B')
);
-- Expected: score_percent matches hand calculation.

-- C6. set_current_step updates the session; subsequent INSERT into
--     lab_evidence (without explicit step_id) picks up step_id via trigger.
SELECT public.set_current_step('<SESSION_ID>','<STEP_ID>');

-- C7. end_current_step clears the step.
SELECT public.end_current_step('<SESSION_ID>');

-- C8. expire_old_sessions flips stale ACTIVE rows to EXPIRED.
UPDATE public.lab_sessions SET expires_at = now() - interval '1 minute'
WHERE  id = '<SESSION_ID>';
SELECT public.expire_old_sessions();
-- Expected: returns count >= 1; session now status='EXPIRED'.

-- ── D. Constraint triggers ──────────────────────────────────────────────────

-- D1. One-leader-per-group partial unique index.
-- (as staff) Insert a second leader into a group that already has one:
INSERT INTO public.lab_group_members (group_id, user_id, role)
VALUES ('<GROUP_ID>','<OTHER_USER_ID>','leader');
-- Expected: unique violation on idx_lab_group_members_one_leader.

-- D2. One-group-per-lab per user trigger.
-- Add a user that already belongs to group A (for lab X) into group B (also lab X):
INSERT INTO public.lab_group_members (group_id, user_id, role)
VALUES ('<GROUP_B_ID>','<USER_ID_IN_GROUP_A>','member');
-- Expected: exception 'User % already belongs to another group for lab %'.
