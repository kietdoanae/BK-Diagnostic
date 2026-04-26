-- =============================================================
-- Migration: edu.vn student role + instructor role + MSSV signup
-- Date: 2026-04-26
-- Spec: docs/superpowers/specs/2026-04-26-edu-vn-student-role-design.md
--
-- Idempotent — có thể chạy lại nhiều lần an toàn.
-- =============================================================

-- 1. Apply MSSV columns + immutability (idempotent với mssv_migration.sql)
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS mssv TEXT DEFAULT NULL;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS full_name TEXT DEFAULT NULL;

DROP TRIGGER IF EXISTS enforce_mssv_immutable ON profiles;

CREATE OR REPLACE FUNCTION fn_enforce_mssv_immutable()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.mssv IS NOT NULL AND OLD.mssv IS DISTINCT FROM NEW.mssv THEN
    RAISE EXCEPTION 'mssv is immutable once set';
  END IF;
  IF OLD.full_name IS NOT NULL AND OLD.full_name IS DISTINCT FROM NEW.full_name THEN
    RAISE EXCEPTION 'full_name is immutable once set';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_mssv_immutable
BEFORE UPDATE ON profiles
FOR EACH ROW EXECUTE FUNCTION fn_enforce_mssv_immutable();

-- 2. RPC update_profile_fields (cho phép user tự cập nhật MSSV/full_name khi đang null)
-- DROP trước vì mssv_migration.sql cũ có version trả về VOID, không thể CREATE OR REPLACE
DROP FUNCTION IF EXISTS update_profile_fields(TEXT, TEXT);
DROP FUNCTION IF EXISTS public.update_profile_fields(TEXT, TEXT);

CREATE OR REPLACE FUNCTION update_profile_fields(p_mssv TEXT, p_full_name TEXT)
RETURNS profiles AS $$
DECLARE
  result profiles;
BEGIN
  UPDATE profiles
  SET mssv = COALESCE(mssv, NULLIF(p_mssv, '')),
      full_name = COALESCE(full_name, NULLIF(p_full_name, ''))
  WHERE id = auth.uid()
  RETURNING * INTO result;
  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grant execute để authenticated user gọi qua PostgREST RPC
GRANT EXECUTE ON FUNCTION update_profile_fields(TEXT, TEXT) TO authenticated;

-- 3. Mở rộng role check constraint
ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_role_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_role_check
  CHECK (role IN ('admin','moderator','instructor','student','user','guest'));

-- 4. Replace handle_new_user trigger để derive role từ email + pickup MSSV
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
  derived_role TEXT;
  signup_mssv TEXT;
  signup_username TEXT;
BEGIN
  IF NEW.email ILIKE '%.edu.vn' THEN
    derived_role := 'student';
  ELSE
    derived_role := 'user';
  END IF;

  signup_mssv := NULLIF(NEW.raw_user_meta_data->>'mssv', '');
  signup_username := COALESCE(
    NEW.raw_user_meta_data->>'username',
    split_part(NEW.email, '@', 1)
  );

  INSERT INTO profiles (id, username, role, status, mssv)
  VALUES (NEW.id, signup_username, derived_role, 'active', signup_mssv);

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger on_auth_user_created đã tồn tại từ setup.sql, chỉ replace function body
-- Nếu vì lý do nào đó trigger chưa có, uncomment dòng dưới:
-- CREATE TRIGGER on_auth_user_created
--   AFTER INSERT ON auth.users
--   FOR EACH ROW EXECUTE FUNCTION handle_new_user();
