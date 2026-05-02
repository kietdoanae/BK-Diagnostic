-- =============================================================
-- Diagnostic: tại sao admin embed profile vẫn null?
-- =============================================================

-- 1. Kiểm tra FK đã tồn tại chưa
SELECT
  conname               AS constraint_name,
  conrelid::regclass    AS source_table,
  confrelid::regclass   AS target_table
FROM pg_constraint
WHERE contype = 'f'
  AND conrelid::regclass::text LIKE 'public.lab_%'
  AND confrelid::regclass::text = 'public.profiles'
ORDER BY conrelid::regclass::text;
-- Kỳ vọng: 6 dòng (lab_sessions, lab_evidence, lab_group_members,
--          lab_pre_quiz_submissions, lab_post_submissions, lab_reports)

-- 2. Kiểm tra RLS trên profiles
SELECT
  schemaname, tablename, rowsecurity
FROM pg_tables
WHERE schemaname = 'public' AND tablename = 'profiles';

SELECT
  policyname, cmd, qual, with_check
FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'profiles';
-- Kỳ vọng: có policy cho phép SELECT cho admin/teacher hoặc public read
-- Nếu policy chỉ cho phép SELECT mỗi profile của chính user → embed null
