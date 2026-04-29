-- =============================================================
-- Migration: Ensure FK constraints lab_*.user_id → public.profiles(id)
-- Date: 2026-04-27
--
-- Bug: GroupsAdminTab và SubmissionsAdminTab không hiển thị tên/MSSV sinh viên.
-- Root cause: PostgREST cần FK trong public schema để resolve embed
-- `profile:profiles!user_id(...)`. lab_*.user_id chỉ có FK đến auth.users(id)
-- (auth schema invisible to PostgREST), nên embed silent-fail → profile=null.
--
-- Fix: idempotently add a SECOND FK trên mỗi bảng lab có user_id (hoặc tương
-- đương) trỏ đến public.profiles(id). Then NOTIFY PostgREST để reload cache.
--
-- Idempotent — drop + add pattern, có thể chạy lại nhiều lần an toàn.
-- =============================================================

-- ── 1. lab_sessions.started_by → profiles ───────────────────────────────────
ALTER TABLE public.lab_sessions
  DROP CONSTRAINT IF EXISTS fk_lab_sessions_started_by_profile;
ALTER TABLE public.lab_sessions
  ADD CONSTRAINT fk_lab_sessions_started_by_profile
  FOREIGN KEY (started_by) REFERENCES public.profiles(id) NOT VALID;

-- ── 2. lab_evidence.submitted_by → profiles ─────────────────────────────────
ALTER TABLE public.lab_evidence
  DROP CONSTRAINT IF EXISTS fk_lab_evidence_submitted_by_profile;
ALTER TABLE public.lab_evidence
  ADD CONSTRAINT fk_lab_evidence_submitted_by_profile
  FOREIGN KEY (submitted_by) REFERENCES public.profiles(id) NOT VALID;

-- ── 3. lab_group_members.user_id → profiles (fix Groups tab) ────────────────
ALTER TABLE public.lab_group_members
  DROP CONSTRAINT IF EXISTS fk_lab_group_members_user_profile;
ALTER TABLE public.lab_group_members
  ADD CONSTRAINT fk_lab_group_members_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id) NOT VALID;

-- ── 4. lab_pre_quiz_submissions.user_id → profiles ──────────────────────────
ALTER TABLE public.lab_pre_quiz_submissions
  DROP CONSTRAINT IF EXISTS fk_lab_pre_quiz_user_profile;
ALTER TABLE public.lab_pre_quiz_submissions
  ADD CONSTRAINT fk_lab_pre_quiz_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id) NOT VALID;

-- ── 5. lab_post_submissions.user_id → profiles (fix Submissions tab) ────────
ALTER TABLE public.lab_post_submissions
  DROP CONSTRAINT IF EXISTS fk_lab_post_submissions_user_profile;
ALTER TABLE public.lab_post_submissions
  ADD CONSTRAINT fk_lab_post_submissions_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id) NOT VALID;

-- ── 6. lab_reports.user_id → profiles (fix Reports list) ────────────────────
ALTER TABLE public.lab_reports
  DROP CONSTRAINT IF EXISTS fk_lab_reports_user_profile;
ALTER TABLE public.lab_reports
  ADD CONSTRAINT fk_lab_reports_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id) NOT VALID;

-- ── 7. Reload PostgREST schema cache ────────────────────────────────────────
-- Supabase auto-reloads sau ~30s nhưng NOTIFY làm instant.
NOTIFY pgrst, 'reload schema';
