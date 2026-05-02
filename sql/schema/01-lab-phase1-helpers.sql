-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Phase 1: Combined Ordered Migration
--  Generated from individual files in this folder. Run top-to-bottom in
--  Supabase SQL Editor. Idempotent (safe to re-run).
--
--  Order:
--    1. lab_helpers.sql   — SQL helpers (is_staff, user_is_in_group,
--                           generate_lab_session_code)
--    2. lab_schema.sql    — 10 tables + indexes + constraint triggers +
--                           updated_at triggers + realtime publication
--    3. lab_rls.sql       — RLS enable + all policies
--    4. lab_rpc.sql       — 6 RPCs + lab_evidence step-assign trigger
--    5. lab_storage.sql   — Private buckets + storage RLS
--
--  NOTE: lab_verification.sql is a smoke-test harness, not part of the
--        migration — do not include here.
-- ════════════════════════════════════════════════════════════════════════════


-- ======= 1/5: lab_helpers.sql =============================================

-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Helpers (Phase 1)
--  Order: run FIRST (before lab_schema.sql). Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── is_staff(): true if current user has role admin/moderator ───────────────
CREATE OR REPLACE FUNCTION public.is_staff()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
          AND role IN ('admin', 'moderator')
    );
$$;

GRANT EXECUTE ON FUNCTION public.is_staff() TO authenticated;

-- ── user_is_in_group(p_group uuid) ──────────────────────────────────────────
-- NOTE: LANGUAGE plpgsql (not sql) so body compiles lazily — table
--       lab_group_members is created in lab_schema.sql which runs after this file.
CREATE OR REPLACE FUNCTION public.user_is_in_group(p_group uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = p_group
          AND user_id = auth.uid()
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.user_is_in_group(uuid) TO authenticated;

-- ── generate_lab_session_code(): returns a 6-digit code not currently ACTIVE
CREATE OR REPLACE FUNCTION public.generate_lab_session_code()
RETURNS char(6)
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
    v_code char(6);
    v_tries int := 0;
BEGIN
    LOOP
        v_code := lpad((floor(random() * 1000000))::int::text, 6, '0');
        EXIT WHEN NOT EXISTS (
            SELECT 1 FROM public.lab_sessions
            WHERE session_code = v_code AND status = 'ACTIVE'
        );
        v_tries := v_tries + 1;
        IF v_tries > 20 THEN
            RAISE EXCEPTION 'Cannot allocate unique lab session code';
        END IF;
    END LOOP;
    RETURN v_code;
END;
$$;

-- ======= 2/5: lab_schema.sql ==============================================

-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Schema (Phase 1)
--  Order: run AFTER lab_helpers.sql, BEFORE lab_rls.sql. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── 1. labs ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.labs (
    id                       uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code                     text        UNIQUE NOT NULL,
    title                    text        NOT NULL,
    description              text,
    order_index              int         NOT NULL DEFAULT 0,
    pre_quiz_pass_threshold  int         NOT NULL DEFAULT 70,
    is_published             boolean     NOT NULL DEFAULT false,
    created_by               uuid        REFERENCES auth.users(id),
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_labs_published ON public.labs(is_published, order_index);

-- ── 2. lab_steps ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_steps (
    id             uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id         uuid    NOT NULL REFERENCES public.labs(id) ON DELETE CASCADE,
    step_order     int     NOT NULL,
    title          text    NOT NULL,
    instruction    text    NOT NULL,
    evidence_type  text    NOT NULL
                   CHECK (evidence_type IN ('raw_frames','active_test','screenshot','none')),
    required_count int     DEFAULT 0,
    hint           text,
    UNIQUE(lab_id, step_order)
);
CREATE INDEX IF NOT EXISTS idx_lab_steps_lab ON public.lab_steps(lab_id, step_order);

-- ── 3. lab_questions ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_questions (
    id              uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id          uuid    NOT NULL REFERENCES public.labs(id) ON DELETE CASCADE,
    phase           text    NOT NULL CHECK (phase IN ('pre_lab','post_lab')),
    question_order  int     NOT NULL,
    question_type   text    NOT NULL
                    CHECK (question_type IN ('multiple_choice','free_text','image_upload')),
    question_text   text    NOT NULL,
    options         jsonb,
    correct_answer  text,
    points          int     NOT NULL DEFAULT 1,
    hint            text,
    UNIQUE(lab_id, phase, question_order)
);
CREATE INDEX IF NOT EXISTS idx_lab_questions_lab_phase
    ON public.lab_questions(lab_id, phase, question_order);

-- ── 4. lab_groups ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_groups (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id     uuid        NOT NULL REFERENCES public.labs(id) ON DELETE CASCADE,
    name       text        NOT NULL,
    semester   text,
    created_by uuid        REFERENCES auth.users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lab_groups_lab ON public.lab_groups(lab_id);

-- ── 5. lab_group_members ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_group_members (
    group_id uuid NOT NULL REFERENCES public.lab_groups(id) ON DELETE CASCADE,
    user_id  uuid NOT NULL REFERENCES auth.users(id)        ON DELETE CASCADE,
    role     text DEFAULT 'member' CHECK (role IN ('leader','member')),
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_lab_group_members_user ON public.lab_group_members(user_id);

-- Exactly one leader per group (partial unique index).
CREATE UNIQUE INDEX IF NOT EXISTS idx_lab_group_members_one_leader
    ON public.lab_group_members(group_id) WHERE role = 'leader';

-- Student may belong to at most one group per lab.
CREATE OR REPLACE FUNCTION public.lab_group_members_check_unique_lab()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_lab_id uuid;
BEGIN
    SELECT lab_id INTO v_lab_id FROM public.lab_groups WHERE id = NEW.group_id;

    IF EXISTS (
        SELECT 1
        FROM public.lab_group_members m
        JOIN public.lab_groups g ON g.id = m.group_id
        WHERE m.user_id = NEW.user_id
          AND g.lab_id  = v_lab_id
          AND m.group_id <> NEW.group_id
    ) THEN
        RAISE EXCEPTION
            'User % already belongs to another group for lab %', NEW.user_id, v_lab_id;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_lab_group_members_unique_lab
    ON public.lab_group_members;
CREATE TRIGGER trg_lab_group_members_unique_lab
    BEFORE INSERT OR UPDATE ON public.lab_group_members
    FOR EACH ROW EXECUTE FUNCTION public.lab_group_members_check_unique_lab();

-- ── 6. lab_sessions ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_sessions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        uuid        NOT NULL REFERENCES public.lab_groups(id),
    lab_id          uuid        NOT NULL REFERENCES public.labs(id),
    session_code    char(6)     NOT NULL,
    status          text        NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','CANCELLED')),
    current_step_id uuid        REFERENCES public.lab_steps(id),
    step_started_at timestamptz,
    started_by      uuid        NOT NULL REFERENCES auth.users(id),
    started_at      timestamptz NOT NULL DEFAULT now(),
    ended_at        timestamptz,
    expires_at      timestamptz NOT NULL DEFAULT (now() + interval '3 hours')
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_lab_sessions_code_active
    ON public.lab_sessions(session_code) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_lab_sessions_group
    ON public.lab_sessions(group_id, started_at DESC);

-- ── 7. lab_evidence ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_evidence (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          uuid        NOT NULL REFERENCES public.lab_sessions(id)
                                    ON DELETE CASCADE,
    step_id             uuid        REFERENCES public.lab_steps(id),
    submitted_by        uuid        NOT NULL REFERENCES auth.users(id),
    evidence_type       text        NOT NULL
                        CHECK (evidence_type IN ('raw_frame','active_test','screenshot')),
    payload             jsonb       NOT NULL,
    client_timestamp_ms bigint      NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_evidence_session_step
    ON public.lab_evidence(session_id, step_id);
CREATE INDEX IF NOT EXISTS idx_evidence_created
    ON public.lab_evidence(session_id, created_at DESC);

-- ── 8. lab_pre_quiz_submissions ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_pre_quiz_submissions (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL REFERENCES auth.users(id),
    lab_id         uuid         NOT NULL REFERENCES public.labs(id),
    answers        jsonb        NOT NULL,
    score_percent  numeric(5,2) NOT NULL,
    passed         boolean      NOT NULL,
    submitted_at   timestamptz  NOT NULL DEFAULT now(),
    attempt_number int          NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_pre_quiz_user_lab
    ON public.lab_pre_quiz_submissions(user_id, lab_id, submitted_at DESC);

-- ── 9. lab_post_submissions ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_post_submissions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES auth.users(id),
    session_id      uuid        NOT NULL REFERENCES public.lab_sessions(id),
    answers         jsonb       NOT NULL,
    uploaded_images jsonb       DEFAULT '[]',
    is_draft        boolean     NOT NULL DEFAULT true,
    teacher_comment text,
    submitted_at    timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, session_id)
);
CREATE INDEX IF NOT EXISTS idx_post_submissions_session
    ON public.lab_post_submissions(session_id);

-- ── 10. lab_reports ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_reports (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES auth.users(id),
    session_id       uuid        NOT NULL REFERENCES public.lab_sessions(id),
    pdf_storage_path text        NOT NULL,
    content_hash     text        NOT NULL,
    file_size_bytes  bigint,
    generated_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, session_id)
);
CREATE INDEX IF NOT EXISTS idx_lab_reports_user ON public.lab_reports(user_id, generated_at DESC);

-- ── 11. updated_at auto-touch ───────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.lab_touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_labs_updated_at ON public.labs;
CREATE TRIGGER trg_labs_updated_at
    BEFORE UPDATE ON public.labs
    FOR EACH ROW EXECUTE FUNCTION public.lab_touch_updated_at();

DROP TRIGGER IF EXISTS trg_lab_post_submissions_updated_at ON public.lab_post_submissions;
CREATE TRIGGER trg_lab_post_submissions_updated_at
    BEFORE UPDATE ON public.lab_post_submissions
    FOR EACH ROW EXECUTE FUNCTION public.lab_touch_updated_at();

-- ── 12. Realtime: lab_evidence + lab_sessions stream ────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname   = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename  = 'lab_evidence'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.lab_evidence;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname   = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename  = 'lab_sessions'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.lab_sessions;
    END IF;
END;
$$;

-- ======= 3/5: lab_rls.sql =================================================

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

-- ======= 4/5: lab_rpc.sql =================================================

-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System RPC (Phase 1)
--  Order: run AFTER lab_rls.sql. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── 1. start_lab_session(lab_id) ────────────────────────────────────────────
--     Caller must be group leader for a group of that lab AND have passed pre-lab.
CREATE OR REPLACE FUNCTION public.start_lab_session(p_lab_id uuid)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid        uuid := auth.uid();
    v_group_id   uuid;
    v_passed     boolean;
    v_session_id uuid;
    v_code       char(6);
    v_expires_at timestamptz;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Must be leader of a group attached to this lab.
    SELECT g.id INTO v_group_id
    FROM   public.lab_groups g
    JOIN   public.lab_group_members m ON m.group_id = g.id
    WHERE  g.lab_id = p_lab_id
      AND  m.user_id = v_uid
      AND  m.role = 'leader';

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Only the group leader may start a practice session';
    END IF;

    -- Must have passed pre-lab on latest attempt.
    SELECT passed INTO v_passed
    FROM   public.lab_pre_quiz_submissions
    WHERE  user_id = v_uid AND lab_id = p_lab_id
    ORDER  BY submitted_at DESC
    LIMIT  1;

    IF v_passed IS NOT TRUE THEN
        RAISE EXCEPTION 'Leader must pass the pre-lab quiz before starting practice';
    END IF;

    -- Disallow if there is already an ACTIVE session for this group.
    IF EXISTS (
        SELECT 1 FROM public.lab_sessions
        WHERE group_id = v_group_id AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Group already has an active session';
    END IF;

    v_code := public.generate_lab_session_code();

    INSERT INTO public.lab_sessions (group_id, lab_id, session_code, started_by)
    VALUES (v_group_id, p_lab_id, v_code, v_uid)
    RETURNING id, expires_at INTO v_session_id, v_expires_at;

    RETURN json_build_object(
        'session_id',   v_session_id,
        'session_code', v_code,
        'expires_at',   v_expires_at
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.start_lab_session(uuid) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.start_lab_session(uuid) TO authenticated;

-- ── 2. validate_lab_code(code) ──────────────────────────────────────────────
--     App calls this after user enters 6-digit code.
--     Succeeds only if caller is a member of the session's group.
CREATE OR REPLACE FUNCTION public.validate_lab_code(p_code text)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid      uuid := auth.uid();
    v_session  public.lab_sessions%ROWTYPE;
    v_lab      public.labs%ROWTYPE;
    v_group    public.lab_groups%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_session
    FROM   public.lab_sessions
    WHERE  session_code = p_code AND status = 'ACTIVE'
    LIMIT  1;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Invalid or expired code';
    END IF;

    IF v_session.expires_at < now() THEN
        RAISE EXCEPTION 'Session has expired';
    END IF;

    -- Caller must be a member of the session's group.
    IF NOT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = v_session.group_id AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'You are not a member of this group';
    END IF;

    SELECT * INTO v_lab   FROM public.labs       WHERE id = v_session.lab_id;
    SELECT * INTO v_group FROM public.lab_groups WHERE id = v_session.group_id;

    RETURN json_build_object(
        'session_id', v_session.id,
        'lab_id',     v_lab.id,
        'lab_title',  v_lab.title,
        'lab_code',   v_lab.code,
        'group_name', v_group.name,
        'expires_at', v_session.expires_at
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.validate_lab_code(text) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.validate_lab_code(text) TO authenticated;

-- ── 3. submit_pre_quiz(lab_id, answers) ─────────────────────────────────────
--     answers is jsonb: { "<question_id>": "A" | "free text" | "image:/path" }
--     Only multiple_choice contributes to auto-grade; free_text & image_upload
--     count as full credit (teacher reviews them offline).
CREATE OR REPLACE FUNCTION public.submit_pre_quiz(
    p_lab_id  uuid,
    p_answers jsonb
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid            uuid := auth.uid();
    v_total_points   int := 0;
    v_earned_points  numeric := 0;
    v_score_percent  numeric(5,2);
    v_threshold      int;
    v_passed         boolean;
    v_attempt_number int;
    r                record;
    v_user_answer    text;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT pre_quiz_pass_threshold INTO v_threshold
    FROM public.labs WHERE id = p_lab_id;

    IF v_threshold IS NULL THEN
        RAISE EXCEPTION 'Lab not found';
    END IF;

    FOR r IN
        SELECT id, question_type, correct_answer, points
        FROM   public.lab_questions
        WHERE  lab_id = p_lab_id AND phase = 'pre_lab'
    LOOP
        v_total_points := v_total_points + r.points;
        v_user_answer  := p_answers ->> r.id::text;

        IF r.question_type = 'multiple_choice' THEN
            IF v_user_answer IS NOT NULL AND v_user_answer = r.correct_answer THEN
                v_earned_points := v_earned_points + r.points;
            END IF;
        ELSE
            -- free_text / image_upload: credit if answered
            IF v_user_answer IS NOT NULL AND length(trim(v_user_answer)) > 0 THEN
                v_earned_points := v_earned_points + r.points;
            END IF;
        END IF;
    END LOOP;

    IF v_total_points = 0 THEN
        v_score_percent := 0;
    ELSE
        v_score_percent := round((v_earned_points / v_total_points) * 100.0, 2);
    END IF;

    v_passed := v_score_percent >= v_threshold;

    SELECT COALESCE(MAX(attempt_number), 0) + 1 INTO v_attempt_number
    FROM   public.lab_pre_quiz_submissions
    WHERE  user_id = v_uid AND lab_id = p_lab_id;

    INSERT INTO public.lab_pre_quiz_submissions
        (user_id, lab_id, answers, score_percent, passed, attempt_number)
    VALUES
        (v_uid, p_lab_id, p_answers, v_score_percent, v_passed, v_attempt_number);

    RETURN json_build_object(
        'score_percent',  v_score_percent,
        'passed',         v_passed,
        'attempt_number', v_attempt_number,
        'threshold',      v_threshold
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.submit_pre_quiz(uuid, jsonb) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.submit_pre_quiz(uuid, jsonb) TO authenticated;

-- ── 4. set_current_step(session_id, step_id) ────────────────────────────────
CREATE OR REPLACE FUNCTION public.set_current_step(
    p_session_id uuid,
    p_step_id    uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid      uuid := auth.uid();
    v_group_id uuid;
    v_lab_id   uuid;
    v_status   text;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, lab_id, status INTO v_group_id, v_lab_id, v_status
    FROM   public.lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = v_group_id AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'You are not a member of this group';
    END IF;

    -- Step must belong to session's lab
    IF NOT EXISTS (
        SELECT 1 FROM public.lab_steps
        WHERE id = p_step_id AND lab_id = v_lab_id
    ) THEN
        RAISE EXCEPTION 'Step does not belong to this lab';
    END IF;

    UPDATE public.lab_sessions
       SET current_step_id = p_step_id,
           step_started_at = now()
     WHERE id = p_session_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.set_current_step(uuid, uuid) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.set_current_step(uuid, uuid) TO authenticated;

-- ── 5. end_current_step(session_id) ─────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.end_current_step(p_session_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid      uuid := auth.uid();
    v_group_id uuid;
    v_status   text;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, status INTO v_group_id, v_status
    FROM   public.lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = v_group_id AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'You are not a member of this group';
    END IF;

    UPDATE public.lab_sessions
       SET current_step_id = NULL,
           step_started_at = NULL
     WHERE id = p_session_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.end_current_step(uuid) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.end_current_step(uuid) TO authenticated;

-- ── 6. expire_old_sessions() ────────────────────────────────────────────────
--     Scheduled job (pg_cron or Edge Function every 10 minutes).
--     Marks ACTIVE sessions whose expires_at has passed as EXPIRED.
CREATE OR REPLACE FUNCTION public.expire_old_sessions()
RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_count int;
BEGIN
    WITH expired AS (
        UPDATE public.lab_sessions
           SET status   = 'EXPIRED',
               ended_at = now()
         WHERE status = 'ACTIVE' AND expires_at < now()
        RETURNING id
    )
    SELECT count(*) INTO v_count FROM expired;

    RETURN v_count;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.expire_old_sessions() FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.expire_old_sessions() TO service_role;

-- Scheduling note: attach either via pg_cron:
--   SELECT cron.schedule('expire-lab-sessions', '*/10 * * * *',
--                        $$SELECT public.expire_old_sessions();$$);
-- …or via a Supabase Edge Function (every 10 min) calling this RPC with
-- the service-role key. Operator concern — not automated here.

-- ── 7. Trigger: lab_evidence.step_id auto-assignment ────────────────────────
--     If session has a current_step_id at insert time, copy it onto the row.
CREATE OR REPLACE FUNCTION public.lab_evidence_assign_step()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_step uuid;
BEGIN
    IF NEW.step_id IS NULL THEN
        SELECT current_step_id INTO v_current_step
        FROM   public.lab_sessions
        WHERE  id = NEW.session_id;

        IF v_current_step IS NOT NULL THEN
            NEW.step_id := v_current_step;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_lab_evidence_assign_step ON public.lab_evidence;
CREATE TRIGGER trg_lab_evidence_assign_step
    BEFORE INSERT ON public.lab_evidence
    FOR EACH ROW EXECUTE FUNCTION public.lab_evidence_assign_step();

-- ======= 5/5: lab_storage.sql =============================================

-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Storage (Phase 1)
--  Buckets: lab-reports (PDFs), lab-images (post-lab images + screenshots).
--  Both private, RLS-enforced. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── 1. Create buckets ───────────────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'lab-reports',
    'lab-reports',
    false,
    10485760,                              -- 10 MB
    ARRAY['application/pdf']
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'lab-images',
    'lab-images',
    false,
    5242880,                               -- 5 MB
    ARRAY['image/png','image/jpeg','image/jpg']
)
ON CONFLICT (id) DO NOTHING;

-- ── 2. Drop existing policies for these buckets (idempotent) ────────────────
DROP POLICY IF EXISTS "lab_reports_user_upload"   ON storage.objects;
DROP POLICY IF EXISTS "lab_reports_user_read"     ON storage.objects;
DROP POLICY IF EXISTS "lab_reports_staff_read"    ON storage.objects;
DROP POLICY IF EXISTS "lab_reports_user_delete"   ON storage.objects;

DROP POLICY IF EXISTS "lab_images_user_upload"    ON storage.objects;
DROP POLICY IF EXISTS "lab_images_user_read"      ON storage.objects;
DROP POLICY IF EXISTS "lab_images_staff_read"     ON storage.objects;
DROP POLICY IF EXISTS "lab_images_user_delete"    ON storage.objects;

-- ── 3. Policies for `lab-reports` ───────────────────────────────────────────
-- Path convention: {user_id}/{session_id}.pdf
CREATE POLICY "lab_reports_user_upload"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'lab-reports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_reports_user_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (
        bucket_id = 'lab-reports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_reports_staff_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (bucket_id = 'lab-reports' AND public.is_staff());

CREATE POLICY "lab_reports_user_delete"
    ON storage.objects FOR DELETE TO authenticated
    USING (
        bucket_id = 'lab-reports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- ── 4. Policies for `lab-images` ────────────────────────────────────────────
-- Path convention: {user_id}/{session_id}/{filename}
CREATE POLICY "lab_images_user_upload"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'lab-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_images_user_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (
        bucket_id = 'lab-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_images_staff_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (bucket_id = 'lab-images' AND public.is_staff());

CREATE POLICY "lab_images_user_delete"
    ON storage.objects FOR DELETE TO authenticated
    USING (
        bucket_id = 'lab-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
