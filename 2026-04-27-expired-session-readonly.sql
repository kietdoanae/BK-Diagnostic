-- =============================================================
-- Migration: lab session expired = read-only
-- Date: 2026-04-27
--
-- Vấn đề: lab_sessions.status='ACTIVE' nhưng expires_at < now() vẫn cho
-- student gọi RPCs set_current_step / end_current_step / complete_lab_session.
-- RLS policy lab_evidence_insert_active đã check expires_at đúng — nhưng các
-- RPCs khác bị thiếu defense, gây hiện tượng "đã hết hạn" mà vẫn thao tác được.
--
-- Fix:
-- 1. Thêm expires_at > now() vào 3 RPCs trên.
-- 2. Bulk cleanup: flip mọi ACTIVE đã quá hạn → EXPIRED + ended_at=now().
--
-- Idempotent — chạy lại an toàn.
-- =============================================================

-- ── 1. set_current_step: add expires_at check ────────────────────────────────
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
    v_uid       uuid := auth.uid();
    v_group_id  uuid;
    v_lab_id    uuid;
    v_status    text;
    v_expires_at timestamptz;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, lab_id, status, expires_at
      INTO v_group_id, v_lab_id, v_status, v_expires_at
    FROM   public.lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE';
    END IF;

    IF v_expires_at <= now() THEN
        RAISE EXCEPTION 'Session has expired (read-only mode)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = v_group_id AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'You are not a member of this group';
    END IF;

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

-- ── 2. end_current_step: add expires_at check ────────────────────────────────
CREATE OR REPLACE FUNCTION public.end_current_step(p_session_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid        uuid := auth.uid();
    v_group_id   uuid;
    v_status     text;
    v_expires_at timestamptz;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, status, expires_at
      INTO v_group_id, v_status, v_expires_at
    FROM   public.lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE';
    END IF;

    IF v_expires_at <= now() THEN
        RAISE EXCEPTION 'Session has expired (read-only mode)';
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

-- ── 3. complete_lab_session: add expires_at check ───────────────────────────
CREATE OR REPLACE FUNCTION public.complete_lab_session(p_session_id uuid)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_group_id   uuid;
    v_status     text;
    v_expires_at timestamptz;
    v_caller     uuid := auth.uid();
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, status, expires_at
      INTO v_group_id, v_status, v_expires_at
    FROM lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE (current: %)', v_status;
    END IF;

    IF v_expires_at <= now() THEN
        RAISE EXCEPTION 'Session has expired (read-only mode)';
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

-- ── 4. Bulk cleanup: ACTIVE đã quá hạn → EXPIRED ─────────────────────────────
-- 1-shot apply migration; tuần hoàn dùng expire_old_sessions() qua pg_cron.
UPDATE public.lab_sessions
   SET status = 'EXPIRED',
       ended_at = COALESCE(ended_at, expires_at)
 WHERE status = 'ACTIVE'
   AND expires_at <= now();
