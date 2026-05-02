-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System RLS Policies (Phase 1)
--  Order: run AFTER lab_schema.sql, BEFORE lab_rpc.sql. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.labs                     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_steps                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_questions            ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_groups               ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_group_members        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_sessions             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_evidence             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_pre_quiz_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_post_submissions     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_reports              ENABLE ROW LEVEL SECURITY;

-- labs ───────────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "labs_select_public" ON public.labs;
DROP POLICY IF EXISTS "labs_staff_all"      ON public.labs;

CREATE POLICY "labs_select_public"
    ON public.labs FOR SELECT TO authenticated
    USING (is_published = true OR public.is_staff());

CREATE POLICY "labs_staff_all"
    ON public.labs FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_steps ──────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_steps_select" ON public.lab_steps;
DROP POLICY IF EXISTS "lab_steps_staff_all" ON public.lab_steps;

CREATE POLICY "lab_steps_select"
    ON public.lab_steps FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.labs l
            WHERE l.id = lab_steps.lab_id AND l.is_published = true
        )
    );

CREATE POLICY "lab_steps_staff_all"
    ON public.lab_steps FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_questions ──────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_questions_select" ON public.lab_questions;
DROP POLICY IF EXISTS "lab_questions_staff_all" ON public.lab_questions;

CREATE POLICY "lab_questions_select"
    ON public.lab_questions FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.labs l
            WHERE l.id = lab_questions.lab_id AND l.is_published = true
        )
    );

CREATE POLICY "lab_questions_staff_all"
    ON public.lab_questions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_groups ─────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_groups_select_own" ON public.lab_groups;
DROP POLICY IF EXISTS "lab_groups_staff_all"  ON public.lab_groups;

CREATE POLICY "lab_groups_select_own"
    ON public.lab_groups FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.lab_group_members m
            WHERE m.group_id = lab_groups.id AND m.user_id = auth.uid()
        )
    );

CREATE POLICY "lab_groups_staff_all"
    ON public.lab_groups FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_group_members ──────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_group_members_select_own" ON public.lab_group_members;
DROP POLICY IF EXISTS "lab_group_members_staff_all"  ON public.lab_group_members;

CREATE POLICY "lab_group_members_select_own"
    ON public.lab_group_members FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR user_id = auth.uid()
        OR public.user_is_in_group(group_id)
    );

CREATE POLICY "lab_group_members_staff_all"
    ON public.lab_group_members FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_sessions ───────────────────────────────────────────────────────────────
-- Note: No student-writable policy. Inserts/updates go through SECURITY DEFINER RPCs.
DROP POLICY IF EXISTS "lab_sessions_select_own_group" ON public.lab_sessions;
DROP POLICY IF EXISTS "lab_sessions_staff_all"        ON public.lab_sessions;

CREATE POLICY "lab_sessions_select_own_group"
    ON public.lab_sessions FOR SELECT TO authenticated
    USING (public.is_staff() OR public.user_is_in_group(group_id));

CREATE POLICY "lab_sessions_staff_all"
    ON public.lab_sessions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_evidence ───────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_evidence_select_own_group" ON public.lab_evidence;
DROP POLICY IF EXISTS "lab_evidence_insert_active"    ON public.lab_evidence;
DROP POLICY IF EXISTS "lab_evidence_staff_all"        ON public.lab_evidence;

CREATE POLICY "lab_evidence_select_own_group"
    ON public.lab_evidence FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.lab_sessions s
            WHERE s.id = lab_evidence.session_id
              AND public.user_is_in_group(s.group_id)
        )
    );

CREATE POLICY "lab_evidence_insert_active"
    ON public.lab_evidence FOR INSERT TO authenticated
    WITH CHECK (
        submitted_by = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.lab_sessions s
            WHERE s.id = lab_evidence.session_id
              AND s.status = 'ACTIVE'
              AND s.expires_at > now()
              AND public.user_is_in_group(s.group_id)
        )
    );

CREATE POLICY "lab_evidence_staff_all"
    ON public.lab_evidence FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_pre_quiz_submissions ───────────────────────────────────────────────────
DROP POLICY IF EXISTS "pre_quiz_select_own"   ON public.lab_pre_quiz_submissions;
DROP POLICY IF EXISTS "pre_quiz_insert_own"   ON public.lab_pre_quiz_submissions;
DROP POLICY IF EXISTS "pre_quiz_staff_all"    ON public.lab_pre_quiz_submissions;

CREATE POLICY "pre_quiz_select_own"
    ON public.lab_pre_quiz_submissions FOR SELECT TO authenticated
    USING (public.is_staff() OR user_id = auth.uid());

CREATE POLICY "pre_quiz_insert_own"
    ON public.lab_pre_quiz_submissions FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "pre_quiz_staff_all"
    ON public.lab_pre_quiz_submissions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_post_submissions ───────────────────────────────────────────────────────
DROP POLICY IF EXISTS "post_submissions_select_own" ON public.lab_post_submissions;
DROP POLICY IF EXISTS "post_submissions_insert_own" ON public.lab_post_submissions;
DROP POLICY IF EXISTS "post_submissions_update_own" ON public.lab_post_submissions;
DROP POLICY IF EXISTS "post_submissions_staff_all"  ON public.lab_post_submissions;

CREATE POLICY "post_submissions_select_own"
    ON public.lab_post_submissions FOR SELECT TO authenticated
    USING (public.is_staff() OR user_id = auth.uid());

CREATE POLICY "post_submissions_insert_own"
    ON public.lab_post_submissions FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "post_submissions_update_own"
    ON public.lab_post_submissions FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "post_submissions_staff_all"
    ON public.lab_post_submissions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_reports ────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_reports_select_own" ON public.lab_reports;
DROP POLICY IF EXISTS "lab_reports_insert_own" ON public.lab_reports;
DROP POLICY IF EXISTS "lab_reports_staff_all"  ON public.lab_reports;

CREATE POLICY "lab_reports_select_own"
    ON public.lab_reports FOR SELECT TO authenticated
    USING (public.is_staff() OR user_id = auth.uid());

CREATE POLICY "lab_reports_insert_own"
    ON public.lab_reports FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "lab_reports_staff_all"
    ON public.lab_reports FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());
