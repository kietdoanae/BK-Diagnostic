-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Activity Logs
--  Chạy file này trong Supabase SQL Editor sau khi đã chạy setup.sql
-- ════════════════════════════════════════════════════════════════════════════

-- ── 1. Bảng activity_logs ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.activity_logs (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         REFERENCES auth.users(id) ON DELETE SET NULL,
    username    TEXT         NOT NULL DEFAULT 'unknown',
    action      TEXT         NOT NULL,
    details     JSONB        NOT NULL DEFAULT '{}',
    platform    TEXT         NOT NULL DEFAULT 'app'
                             CHECK (platform IN ('app', 'web')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index để query nhanh
CREATE INDEX IF NOT EXISTS idx_activity_logs_user_id    ON public.activity_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_created_at ON public.activity_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_logs_action     ON public.activity_logs(action);
CREATE INDEX IF NOT EXISTS idx_activity_logs_platform   ON public.activity_logs(platform);

-- ── 2. Row Level Security ────────────────────────────────────────────────────
ALTER TABLE public.activity_logs ENABLE ROW LEVEL SECURITY;

-- Xóa policy cũ nếu đã tồn tại (idempotent)
DROP POLICY IF EXISTS "users_insert_own_logs" ON public.activity_logs;
DROP POLICY IF EXISTS "admins_read_all_logs"  ON public.activity_logs;

-- User tự ghi log của mình
CREATE POLICY "users_insert_own_logs"
ON public.activity_logs FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

-- Chỉ admin / moderator mới đọc được toàn bộ log
CREATE POLICY "admins_read_all_logs"
ON public.activity_logs FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
          AND role IN ('admin', 'moderator')
    )
);

-- ── 3. Bật Realtime cho bảng này (idempotent) ────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename  = 'activity_logs'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.activity_logs;
    END IF;
END;
$$;

-- ── 4. RPC: log_activity ─────────────────────────────────────────────────────
--  Hàm ghi log — Web app gọi hàm này để insert vào activity_logs
CREATE OR REPLACE FUNCTION public.log_activity(
    p_action    TEXT,
    p_platform  TEXT    DEFAULT 'web',
    p_details   JSONB   DEFAULT '{}'
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_username TEXT;
BEGIN
    -- Lấy username từ profiles
    SELECT COALESCE(pr.username, split_part(u.email, '@', 1))
    INTO v_username
    FROM public.profiles pr
    JOIN auth.users u ON u.id = pr.id
    WHERE pr.id = auth.uid();

    INSERT INTO public.activity_logs (user_id, username, action, platform, details)
    VALUES (auth.uid(), COALESCE(v_username, 'unknown'), p_action, p_platform, p_details);
END;
$$;

GRANT EXECUTE ON FUNCTION public.log_activity(TEXT, TEXT, JSONB) TO authenticated;

-- ── 5. RPC: get_activity_logs ─────────────────────────────────────────────────
--  Dùng LANGUAGE sql thay vì plpgsql để tránh lỗi "column reference is ambiguous"
--  (trong plpgsql, tên cột RETURNS TABLE tạo biến local xung đột với cột bảng)
CREATE OR REPLACE FUNCTION public.get_activity_logs(
    p_limit     INT     DEFAULT 100,
    p_offset    INT     DEFAULT 0,
    p_action    TEXT    DEFAULT NULL,
    p_platform  TEXT    DEFAULT NULL
)
RETURNS TABLE (
    id          BIGINT,
    user_id     UUID,
    username    TEXT,
    action      TEXT,
    details     JSONB,
    platform    TEXT,
    created_at  TIMESTAMPTZ
)
LANGUAGE sql
SECURITY DEFINER
AS $$
  SELECT  l.id,
          l.user_id,
          l.username,
          l.action,
          l.details,
          l.platform,
          l.created_at
  FROM    public.activity_logs l
  WHERE
    (p_action   IS NULL OR l.action   = p_action)
    AND (p_platform IS NULL OR l.platform = p_platform)
    AND EXISTS (
        SELECT 1 FROM public.profiles p
        WHERE  p.id   = auth.uid()
          AND  p.role IN ('admin', 'moderator')
    )
  ORDER BY l.created_at DESC
  LIMIT  p_limit
  OFFSET p_offset;
$$;

-- ── 5. RPC: get_log_stats ─────────────────────────────────────────────────────
--  Trả về thống kê tổng hợp cho dashboard
CREATE OR REPLACE FUNCTION public.get_log_stats()
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_today       DATE := CURRENT_DATE;
    v_total_today INT;
    v_logins      INT;
    v_app_events  INT;
    v_web_events  INT;
    v_errors      INT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
          AND role IN ('admin', 'moderator')
    ) THEN
        RAISE EXCEPTION 'Access denied';
    END IF;

    SELECT COUNT(*) INTO v_total_today
    FROM public.activity_logs
    WHERE created_at::date = v_today;

    SELECT COUNT(*) INTO v_logins
    FROM public.activity_logs
    WHERE created_at::date = v_today AND action = 'LOGIN';

    SELECT COUNT(*) INTO v_app_events
    FROM public.activity_logs
    WHERE platform = 'app';

    SELECT COUNT(*) INTO v_web_events
    FROM public.activity_logs
    WHERE platform = 'web';

    SELECT COUNT(*) INTO v_errors
    FROM public.activity_logs
    WHERE action = 'LOGIN_FAILED' AND created_at::date = v_today;

    RETURN json_build_object(
        'today',         v_total_today,
        'logins_today',  v_logins,
        'app_events',    v_app_events,
        'web_events',    v_web_events,
        'failed_logins', v_errors
    );
END;
$$;
