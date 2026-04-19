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
