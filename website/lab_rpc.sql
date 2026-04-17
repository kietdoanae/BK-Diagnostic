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
