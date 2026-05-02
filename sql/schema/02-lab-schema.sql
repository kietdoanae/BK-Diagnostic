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
    group_id        uuid        NOT NULL REFERENCES public.lab_groups(id) ON DELETE CASCADE,
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
    session_id      uuid        NOT NULL REFERENCES public.lab_sessions(id) ON DELETE CASCADE,
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
    session_id       uuid        NOT NULL REFERENCES public.lab_sessions(id) ON DELETE CASCADE,
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
