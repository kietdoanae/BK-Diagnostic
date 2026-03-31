-- ============================================================
-- BK Diagnostic — Supabase Setup SQL
-- Run this in: Supabase Dashboard → SQL Editor → New Query
-- ============================================================

-- 1. Add "status" column to profiles table (if not exists)
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';

-- ============================================================
-- 1b. Auto-create profile row on signup (trigger)
--     Without this, users who register have NO profiles row →
--     the Android app fallback always forces role = "user".
-- ============================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, username, role, status)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'username', split_part(NEW.email, '@', 1)),
    'user',
    'active'
  )
  ON CONFLICT (id) DO NOTHING;  -- safe to re-run
  RETURN NEW;
END;
$$;

-- Attach trigger to auth.users
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Back-fill profiles for existing users who have no row yet
INSERT INTO public.profiles (id, username, role, status)
SELECT
  u.id,
  COALESCE(u.raw_user_meta_data->>'username', split_part(u.email, '@', 1)),
  'user',
  'active'
FROM auth.users u
WHERE NOT EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = u.id);

-- ============================================================
-- 1c. Set your own account as admin
--     Replace 'your-email@example.com' with the admin email
-- ============================================================
UPDATE public.profiles
SET role = 'admin'
WHERE id = (SELECT id FROM auth.users WHERE email = 'your-email@example.com');

-- ============================================================
-- 2. RPC: admin_get_users()
--    Returns all users — accessible by Admin AND Moderator
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_get_users()
RETURNS TABLE (
  id              UUID,
  username        TEXT,
  email           TEXT,
  role            TEXT,
  status          TEXT,
  created_at      TIMESTAMPTZ,
  last_sign_in_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Allow admin and moderator
  IF (SELECT p.role FROM public.profiles p WHERE p.id = auth.uid())
     NOT IN ('admin', 'moderator') THEN
    RAISE EXCEPTION 'Access denied: admin or moderator role required';
  END IF;

  RETURN QUERY
    SELECT
      p.id,
      COALESCE(p.username, split_part(u.email, '@', 1))::TEXT AS username,
      u.email::TEXT,
      COALESCE(p.role,   'user')::TEXT   AS role,
      COALESCE(p.status, 'active')::TEXT AS status,
      u.created_at,
      u.last_sign_in_at
    FROM public.profiles p
    JOIN auth.users u ON p.id = u.id
    ORDER BY u.created_at DESC;
END;
$$;

-- ============================================================
-- 3. RPC: admin_update_user(target_id, new_role, new_status)
--    Admin: can change both role and status
--    Moderator: can only change status (new_role must be NULL)
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_update_user(
  target_id  UUID,
  new_role   TEXT DEFAULT NULL,
  new_status TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  caller_role TEXT;
BEGIN
  SELECT p.role INTO caller_role
  FROM public.profiles p WHERE p.id = auth.uid();

  IF caller_role NOT IN ('admin', 'moderator') THEN
    RAISE EXCEPTION 'Access denied: admin or moderator role required';
  END IF;

  -- Moderators cannot change roles
  IF caller_role = 'moderator' AND new_role IS NOT NULL THEN
    RAISE EXCEPTION 'Access denied: moderators cannot change user roles';
  END IF;

  UPDATE public.profiles
  SET
    role   = COALESCE(new_role,   role),
    status = COALESCE(new_status, status)
  WHERE id = target_id;
END;
$$;

-- ============================================================
-- 4. RPC: admin_delete_user(target_id)
--    Admin only — moderators cannot delete users
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_delete_user(target_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Strictly admin only
  IF (SELECT p.role FROM public.profiles p WHERE p.id = auth.uid()) <> 'admin' THEN
    RAISE EXCEPTION 'Access denied: only admins can delete users';
  END IF;

  DELETE FROM public.profiles WHERE id = target_id;
END;
$$;

-- ============================================================
-- 5. Grant execute permissions to authenticated users
--    (the functions themselves check for admin role internally)
-- ============================================================
GRANT EXECUTE ON FUNCTION public.admin_get_users()                          TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_update_user(UUID, TEXT, TEXT)        TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_delete_user(UUID)                    TO authenticated;

-- ============================================================
-- DONE. Verify by running:
--   SELECT routine_name FROM information_schema.routines
--   WHERE routine_schema = 'public'
--   AND routine_name LIKE 'admin_%';
-- ============================================================
