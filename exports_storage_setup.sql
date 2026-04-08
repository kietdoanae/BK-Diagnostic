-- ============================================================
-- Supabase Storage: Setup bucket "exports" cho BKDiagnostic
-- Chạy file này trong Supabase Dashboard > SQL Editor
-- ============================================================

-- 1. Tạo bucket "exports" (private — không public)
--    (Hoặc tạo thủ công trong Supabase Dashboard > Storage > New bucket)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'exports',
  'exports',
  false,                        -- private: không ai truy cập được mà không có signed URL
  5242880,                      -- 5 MB max per file
  ARRAY['text/csv', 'text/plain']
)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 2. RLS Policies cho Storage Objects (bảng storage.objects)
-- ============================================================

-- Cho phép user đã đăng nhập UPLOAD file vào folder của chính họ
-- Path format: {user_id}/{filename}
CREATE POLICY "User can upload own exports"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'exports'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- Cho phép user đọc/download file của chính họ
CREATE POLICY "User can read own exports"
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'exports'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- Cho phép admin và moderator đọc TẤT CẢ file trong bucket
CREATE POLICY "Admin can read all exports"
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'exports'
    AND (
      (auth.jwt() -> 'user_metadata' ->> 'role') IN ('admin', 'moderator')
    )
  );

-- Cho phép user xóa file của chính họ (tùy chọn)
CREATE POLICY "User can delete own exports"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'exports'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- ============================================================
-- HƯỚNG DẪN SỬ DỤNG
-- ============================================================
-- Nếu metadata.role lưu trong bảng profiles thay vì JWT:
-- Thay điều kiện admin bằng:
--   EXISTS (
--     SELECT 1 FROM public.profiles
--     WHERE id = auth.uid() AND role IN ('admin', 'moderator')
--   )
--
-- Sau khi chạy SQL này:
-- 1. Kiểm tra trong Supabase Dashboard > Storage > exports (bucket đã tạo)
-- 2. Export 1 file từ app → kiểm tra file xuất hiện trong Storage
-- 3. Vào web dashboard (user) → xem tab "Lịch sử xuất file CAN"
-- 4. Vào admin page → tab "📁 Exports" → thấy file của tất cả users
-- ============================================================
