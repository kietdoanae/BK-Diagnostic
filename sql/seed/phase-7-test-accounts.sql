-- ════════════════════════════════════════════════════════════════════════════
-- Phase 7 pilot — stamp profile fields (role / mssv / full_name / username)
-- Run AFTER the 6 users are created via Supabase Dashboard.
-- Idempotent: re-run is safe (all UPDATEs are keyed by email via auth.users).
-- ════════════════════════════════════════════════════════════════════════════

-- Helper inline CTE pattern: look up the auth user id by email, then upsert
-- the profile fields. No DO blocks, so this is safe to paste into SQL Editor.

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.admin@bk.test')
UPDATE public.profiles SET
    username='pilot_admin', full_name='Pilot Admin', mssv='ADMIN001', role='admin'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.lead@bk.test')
UPDATE public.profiles SET
    username='pilot_s1', full_name='Nguyễn Văn An', mssv='99990001', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s2@bk.test')
UPDATE public.profiles SET
    username='pilot_s2', full_name='Trần Thị Bảo', mssv='99990002', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s3@bk.test')
UPDATE public.profiles SET
    username='pilot_s3', full_name='Lê Minh Cường', mssv='99990003', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s4@bk.test')
UPDATE public.profiles SET
    username='pilot_s4', full_name='Phạm Thu Dung', mssv='99990004', role='student'
WHERE id = (SELECT id FROM u);

WITH u AS (SELECT id FROM auth.users WHERE email = 'pilot.s5@bk.test')
UPDATE public.profiles SET
    username='pilot_s5', full_name='Võ Hoàng Em', mssv='99990005', role='student'
WHERE id = (SELECT id FROM u);

-- ── Verification ─────────────────────────────────────────────────────────────
-- SELECT u.email, p.username, p.full_name, p.mssv, p.role
-- FROM   auth.users u JOIN public.profiles p ON p.id = u.id
-- WHERE  u.email LIKE 'pilot.%@bk.test'
-- ORDER  BY u.email;

-- ── Cleanup (run at end of pilot — Task 14) ──────────────────────────────────
-- DELETE FROM auth.users WHERE email LIKE 'pilot.%@bk.test';
-- (CASCADE removes profiles + group memberships + sessions + evidence.)
