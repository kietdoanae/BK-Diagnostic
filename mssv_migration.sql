-- ============================================================
-- Migration: Add mssv (student ID) to profiles table
-- Run this in Supabase SQL Editor
-- ============================================================

-- 1. Add mssv column (nullable TEXT, unique when set)
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS mssv TEXT DEFAULT NULL;

-- Optional: enforce unique MSSV across all users (skip if not needed)
-- ALTER TABLE public.profiles ADD CONSTRAINT profiles_mssv_unique UNIQUE (mssv);

-- 2. Trigger function: prevent changing mssv once it has been set
CREATE OR REPLACE FUNCTION public.prevent_mssv_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  -- If old mssv was already set (not null) and new value differs → block
  IF OLD.mssv IS NOT NULL AND NEW.mssv IS DISTINCT FROM OLD.mssv THEN
    RAISE EXCEPTION 'MSSV cannot be changed once it has been set.';
  END IF;
  RETURN NEW;
END;
$$;

-- 3. Attach trigger to profiles table
DROP TRIGGER IF EXISTS enforce_mssv_immutable ON public.profiles;
CREATE TRIGGER enforce_mssv_immutable
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.prevent_mssv_change();
