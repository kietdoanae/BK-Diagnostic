-- lab_profiles_fk.sql
-- ---------------------------------------------------------------------------
-- PostgREST can only traverse FK relationships declared in the public schema.
-- All lab tables use user_id/started_by/submitted_by → auth.users(id), which
-- lives in the auth schema and is invisible to PostgREST's relationship cache.
--
-- Fix: add a SECOND FK on each column pointing to profiles(id).
-- PostgREST then resolves hints like profiles!user_id / profiles!started_by
-- correctly. The NOT VALID flag skips the historical-rows scan (safe because
-- every auth.users row that has a lab row also has a profiles row in practice).
-- ---------------------------------------------------------------------------

-- lab_sessions.started_by  →  profiles
ALTER TABLE public.lab_sessions
  ADD CONSTRAINT fk_lab_sessions_started_by_profile
  FOREIGN KEY (started_by) REFERENCES public.profiles(id)
  NOT VALID;

-- lab_evidence.submitted_by  →  profiles
ALTER TABLE public.lab_evidence
  ADD CONSTRAINT fk_lab_evidence_submitted_by_profile
  FOREIGN KEY (submitted_by) REFERENCES public.profiles(id)
  NOT VALID;

-- lab_group_members.user_id  →  profiles  (also fixes Groups tab)
ALTER TABLE public.lab_group_members
  ADD CONSTRAINT fk_lab_group_members_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id)
  NOT VALID;

-- lab_pre_quiz_submissions.user_id  →  profiles
ALTER TABLE public.lab_pre_quiz_submissions
  ADD CONSTRAINT fk_lab_pre_quiz_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id)
  NOT VALID;

-- lab_post_submissions.user_id  →  profiles
ALTER TABLE public.lab_post_submissions
  ADD CONSTRAINT fk_lab_post_submissions_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id)
  NOT VALID;

-- lab_reports.user_id  →  profiles
ALTER TABLE public.lab_reports
  ADD CONSTRAINT fk_lab_reports_user_profile
  FOREIGN KEY (user_id) REFERENCES public.profiles(id)
  NOT VALID;

-- ---------------------------------------------------------------------------
-- Reload PostgREST schema cache so the new FKs are picked up immediately.
-- (Supabase auto-reloads every ~30 s, but this is instant.)
-- ---------------------------------------------------------------------------
NOTIFY pgrst, 'reload schema';
