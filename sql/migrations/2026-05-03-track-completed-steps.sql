-- =============================================================
-- Migration: track completed step IDs cho lab_sessions
-- Date: 2026-05-03
--
-- Bug: LabSessionPage.allStepsSatisfied đối với evidence_type='none' chỉ
-- check 's.id !== current_step_id' → step chưa từng activate cũng tính là
-- 'satisfied' → leader có thể bấm Hoàn tất ngay khi vào session.
--
-- Fix: thêm column completed_step_ids uuid[] vào lab_sessions, mỗi lần
-- end_current_step() append step_id hiện tại vào array. Frontend kiểm
-- 's.id = ANY(session.completed_step_ids)' cho none-steps.
--
-- Idempotent.
-- =============================================================

-- 1. Thêm column (idempotent)
ALTER TABLE public.lab_sessions
  ADD COLUMN IF NOT EXISTS completed_step_ids uuid[] NOT NULL DEFAULT '{}';

-- 2. Update RPC end_current_step để append step_id vào array
CREATE OR REPLACE FUNCTION public.end_current_step(p_session_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_session    record;
    v_step_id    uuid;
BEGIN
    SELECT s.*
    INTO   v_session
    FROM   public.lab_sessions s WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;
    IF v_session.status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE';
    END IF;
    IF v_session.expires_at <= now() THEN
        RAISE EXCEPTION 'Session expired';
    END IF;

    -- Authorization: leader của group hoặc staff
    IF NOT (
        EXISTS (
            SELECT 1 FROM public.lab_group_members
            WHERE group_id = v_session.group_id
              AND user_id  = auth.uid()
              AND role     = 'leader'
        ) OR public.is_staff()
    ) THEN
        RAISE EXCEPTION 'Only the leader can end a step';
    END IF;

    v_step_id := v_session.current_step_id;
    IF v_step_id IS NULL THEN
        RAISE EXCEPTION 'No active step to end';
    END IF;

    -- Append step_id vào completed_step_ids (dedup nếu đã có)
    UPDATE public.lab_sessions
       SET current_step_id    = NULL,
           step_started_at    = NULL,
           completed_step_ids = CASE
               WHEN v_step_id = ANY(completed_step_ids) THEN completed_step_ids
               ELSE array_append(completed_step_ids, v_step_id)
           END
     WHERE id = p_session_id;
END;
$$;

-- 3. Tương tự update complete_lab_session để clear current_step
-- (giữ logic cũ, không cần sửa nếu đã có)

-- Reload PostgREST cache
NOTIFY pgrst, 'reload schema';

-- Verify:
--   SELECT column_name FROM information_schema.columns
--   WHERE table_name='lab_sessions' AND column_name='completed_step_ids';
-- → 1 dòng
