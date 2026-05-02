-- =============================================================
-- Migration: ON DELETE CASCADE cho group → sessions → submissions/reports
-- Date: 2026-05-02
--
-- Bug: Xóa nhóm trong /teach hoặc /admin báo lỗi:
--   "update or delete on table 'lab_groups' violates foreign key
--    constraint 'lab_sessions_group_id_fkey' on table 'lab_sessions'"
--
-- Root cause: 3 FK thiếu ON DELETE CASCADE
--   1. lab_sessions.group_id        → lab_groups
--   2. lab_post_submissions.session_id → lab_sessions
--   3. lab_reports.session_id       → lab_sessions
--
-- UI đã hiển thị "Xóa nhóm này (và tất cả thành viên + sessions của nó)?"
-- nên cascade là intended behavior.
--
-- Fix: drop + re-add các FK với ON DELETE CASCADE.
-- Idempotent — IF EXISTS trước khi drop.
-- =============================================================

-- 1. lab_sessions.group_id → lab_groups (CASCADE)
ALTER TABLE public.lab_sessions
  DROP CONSTRAINT IF EXISTS lab_sessions_group_id_fkey;
ALTER TABLE public.lab_sessions
  ADD CONSTRAINT lab_sessions_group_id_fkey
  FOREIGN KEY (group_id) REFERENCES public.lab_groups(id)
  ON DELETE CASCADE;

-- 2. lab_post_submissions.session_id → lab_sessions (CASCADE)
ALTER TABLE public.lab_post_submissions
  DROP CONSTRAINT IF EXISTS lab_post_submissions_session_id_fkey;
ALTER TABLE public.lab_post_submissions
  ADD CONSTRAINT lab_post_submissions_session_id_fkey
  FOREIGN KEY (session_id) REFERENCES public.lab_sessions(id)
  ON DELETE CASCADE;

-- 3. lab_reports.session_id → lab_sessions (CASCADE)
ALTER TABLE public.lab_reports
  DROP CONSTRAINT IF EXISTS lab_reports_session_id_fkey;
ALTER TABLE public.lab_reports
  ADD CONSTRAINT lab_reports_session_id_fkey
  FOREIGN KEY (session_id) REFERENCES public.lab_sessions(id)
  ON DELETE CASCADE;

-- Reload PostgREST cache
NOTIFY pgrst, 'reload schema';

-- Verify (chạy thử sau khi apply):
--   SELECT conname, conrelid::regclass, confdeltype
--   FROM pg_constraint
--   WHERE contype = 'f'
--     AND conname IN (
--       'lab_sessions_group_id_fkey',
--       'lab_post_submissions_session_id_fkey',
--       'lab_reports_session_id_fkey'
--     );
-- → confdeltype phải là 'c' (cascade) cho cả 3.
