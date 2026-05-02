-- =============================================================
-- Fix: profiles SELECT policy quá hẹp khiến PostgREST embed null
-- Date: 2026-04-29
--
-- Triệu chứng: GroupsAdminTab/SessionsAdminTab/SubmissionsAdminTab embed
--   `profile:profiles!user_id(...)` luôn về null cho HẾT thảy user khác
--   chính mình → cột "Thành viên", "Khởi tạo bởi", "Sinh viên/MSSV" toàn '—'.
--
-- Root cause: RLS trên public.profiles chỉ cho phép user đọc CHÍNH profile
--   của mình. Khi PostgREST embed cho admin/teacher → các profile khác bị
--   filter → trả về null (không phải lỗi → silent fail).
--
-- Fix: thêm policy cho phép authenticated user đọc TẤT CẢ profiles.
--   Đây là cách Supabase recommend cho username/avatar lookups công khai.
--   profiles chỉ chứa public-ish data (username, full_name, mssv, role).
--
-- Idempotent — DROP POLICY IF EXISTS rồi CREATE.
-- =============================================================

-- Đảm bảo RLS đang bật (không tắt — chỉ thêm policy thêm quyền)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Policy: ai đăng nhập đều SELECT được mọi profile
DROP POLICY IF EXISTS "profiles_select_all_authenticated" ON public.profiles;
CREATE POLICY "profiles_select_all_authenticated"
  ON public.profiles
  FOR SELECT
  TO authenticated
  USING (true);

-- (giữ nguyên các policy UPDATE/DELETE/INSERT hiện có — không đụng tới)

-- Reload PostgREST cache
NOTIFY pgrst, 'reload schema';

-- Verify (chạy thử sau khi apply):
--   SELECT id, username, full_name, mssv, role
--   FROM public.profiles LIMIT 5;
-- → admin sẽ thấy tất cả profiles, không chỉ profile của mình.
