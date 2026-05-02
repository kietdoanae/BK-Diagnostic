-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Storage (Phase 1)
--  Buckets: lab-reports (PDFs), lab-images (post-lab images + screenshots).
--  Both private, RLS-enforced. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── 1. Create buckets ───────────────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'lab-reports',
    'lab-reports',
    false,
    10485760,                              -- 10 MB
    ARRAY['application/pdf']
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'lab-images',
    'lab-images',
    false,
    5242880,                               -- 5 MB
    ARRAY['image/png','image/jpeg','image/jpg']
)
ON CONFLICT (id) DO NOTHING;

-- ── 2. Drop existing policies for these buckets (idempotent) ────────────────
DROP POLICY IF EXISTS "lab_reports_user_upload"   ON storage.objects;
DROP POLICY IF EXISTS "lab_reports_user_read"     ON storage.objects;
DROP POLICY IF EXISTS "lab_reports_staff_read"    ON storage.objects;
DROP POLICY IF EXISTS "lab_reports_user_delete"   ON storage.objects;

DROP POLICY IF EXISTS "lab_images_user_upload"    ON storage.objects;
DROP POLICY IF EXISTS "lab_images_user_read"      ON storage.objects;
DROP POLICY IF EXISTS "lab_images_staff_read"     ON storage.objects;
DROP POLICY IF EXISTS "lab_images_user_delete"    ON storage.objects;

-- ── 3. Policies for `lab-reports` ───────────────────────────────────────────
-- Path convention: {user_id}/{session_id}.pdf
CREATE POLICY "lab_reports_user_upload"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'lab-reports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_reports_user_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (
        bucket_id = 'lab-reports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_reports_staff_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (bucket_id = 'lab-reports' AND public.is_staff());

CREATE POLICY "lab_reports_user_delete"
    ON storage.objects FOR DELETE TO authenticated
    USING (
        bucket_id = 'lab-reports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- ── 4. Policies for `lab-images` ────────────────────────────────────────────
-- Path convention: {user_id}/{session_id}/{filename}
CREATE POLICY "lab_images_user_upload"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'lab-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_images_user_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (
        bucket_id = 'lab-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "lab_images_staff_read"
    ON storage.objects FOR SELECT TO authenticated
    USING (bucket_id = 'lab-images' AND public.is_staff());

CREATE POLICY "lab_images_user_delete"
    ON storage.objects FOR DELETE TO authenticated
    USING (
        bucket_id = 'lab-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
