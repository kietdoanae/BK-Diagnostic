-- =============================================================
-- Migration: schedule expire_old_sessions() chạy mỗi 10 phút
-- Date: 2026-05-03
--
-- Bug: ACTIVE sessions vượt expires_at vẫn ở status='ACTIVE' vì không
-- có ai gọi expire_old_sessions(). Hậu quả: leader không start được
-- session mới (1 group chỉ 1 ACTIVE session/lab), member nhập code cũ
-- vẫn join được, evidence push vào session đáng lẽ đã hết hạn.
--
-- Fix: dùng pg_cron schedule mỗi 10 phút.
--
-- Yêu cầu: extension pg_cron đã được Supabase enable.
--   Vào Dashboard → Database → Extensions → enable 'pg_cron' nếu chưa.
--
-- Idempotent — DROP IF EXISTS trước rồi schedule lại.
-- =============================================================

-- 1. Bật extension nếu chưa
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- 2. Đảm bảo function expire_old_sessions() có quyền cho cron service
GRANT EXECUTE ON FUNCTION public.expire_old_sessions() TO postgres;

-- 3. Xóa job cũ nếu tồn tại (idempotent re-run)
DO $$
DECLARE
    v_jobid bigint;
BEGIN
    SELECT jobid INTO v_jobid
    FROM cron.job
    WHERE jobname = 'lab_expire_old_sessions';
    IF v_jobid IS NOT NULL THEN
        PERFORM cron.unschedule(v_jobid);
        RAISE NOTICE 'Unscheduled old job %', v_jobid;
    END IF;
END;
$$;

-- 4. Schedule mới: mỗi 10 phút
SELECT cron.schedule(
    'lab_expire_old_sessions',
    '*/10 * * * *',                 -- every 10 minutes
    $$ SELECT public.expire_old_sessions(); $$
);

-- Verify (chạy thử sau khi apply):
--   SELECT jobid, jobname, schedule, command, active
--   FROM cron.job
--   WHERE jobname = 'lab_expire_old_sessions';
-- → 1 dòng với schedule '*/10 * * * *' và active=true
--
-- Xem log lần chạy gần nhất:
--   SELECT * FROM cron.job_run_details
--   WHERE jobid = (SELECT jobid FROM cron.job WHERE jobname='lab_expire_old_sessions')
--   ORDER BY start_time DESC LIMIT 5;
