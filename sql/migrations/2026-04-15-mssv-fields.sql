-- ============================================================
-- Migration: Add mssv + full_name to profiles table
-- Run this in Supabase SQL Editor
-- ============================================================

-- 1. Add columns
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS mssv      TEXT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS full_name TEXT DEFAULT NULL;

-- 2. Trigger: prevent changing mssv once set
CREATE OR REPLACE FUNCTION public.prevent_mssv_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  IF OLD.mssv IS NOT NULL AND NEW.mssv IS DISTINCT FROM OLD.mssv THEN
    RAISE EXCEPTION 'MSSV cannot be changed once it has been set.';
  END IF;
  IF OLD.full_name IS NOT NULL AND NEW.full_name IS DISTINCT FROM OLD.full_name THEN
    RAISE EXCEPTION 'Full name cannot be changed once it has been set.';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS enforce_mssv_immutable ON public.profiles;
CREATE TRIGGER enforce_mssv_immutable
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.prevent_mssv_change();

-- 3. RPC: update_profile_fields (SECURITY DEFINER bypasses RLS)
--    User can only set mssv/full_name when they are currently NULL (enforced by trigger above)
CREATE OR REPLACE FUNCTION public.update_profile_fields(
  p_mssv      TEXT DEFAULT NULL,
  p_full_name TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE public.profiles
  SET
    mssv      = COALESCE(p_mssv,      mssv),
    full_name = COALESCE(p_full_name, full_name)
  WHERE id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION public.update_profile_fields(TEXT, TEXT) TO authenticated;
