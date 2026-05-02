-- ============================================================
-- Supabase Storage: Setup bucket "exports" cho BKDiagnostic
-- Có thể chạy lại nhiều lần — tất cả đều idempotent
-- ============================================================

-- 1. Tạo bucket "exports" (private — không public)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'exports',
  'exports',
  false,
  5242880,
  ARRAY['text/csv', 'text/plain']
)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 2. RLS Policies — xóa cũ trước khi tạo lại (idempotent)
-- ============================================================

DROP POLICY IF EXISTS "User can upload own exports"  ON storage.objects;
DROP POLICY IF EXISTS "User can read own exports"    ON storage.objects;
DROP POLICY IF EXISTS "Admin can read all exports"   ON storage.objects;
DROP POLICY IF EXISTS "User can delete own exports"  ON storage.objects;

-- Cho phép user upload vào folder của chính họ ({user_id}/filename)
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

-- Cho phép admin và moderator đọc tất cả file
CREATE POLICY "Admin can read all exports"
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'exports'
    AND (
      (auth.jwt() -> 'user_metadata' ->> 'role') IN ('admin', 'moderator')
      OR EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role IN ('admin', 'moderator')
      )
    )
  );

-- Cho phép user xóa file của chính họ
CREATE POLICY "User can delete own exports"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'exports'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );
