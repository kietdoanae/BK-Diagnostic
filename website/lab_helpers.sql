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
CREATE OR REPLACE FUNCTION public.user_is_in_group(p_group uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = p_group
          AND user_id = auth.uid()
    );
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
