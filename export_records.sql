-- ============================================================
-- Supabase: Bảng export_records cho BKDiagnostic
-- Có thể chạy lại nhiều lần — tất cả đều idempotent
-- ============================================================

CREATE TABLE IF NOT EXISTS public.export_records (
    id              uuid        DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         uuid        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    username        text        NOT NULL,
    filename        text        NOT NULL,
    brand_id        text        NOT NULL,
    model_id        text        NOT NULL,
    display_name    text        NOT NULL,
    frame_count     int         NOT NULL DEFAULT 0,
    file_size_bytes bigint      DEFAULT 0,
    storage_path    text        NOT NULL,
    created_at      timestamptz DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_export_records_user_id    ON public.export_records(user_id);
CREATE INDEX IF NOT EXISTS idx_export_records_created_at ON public.export_records(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_export_records_brand      ON public.export_records(brand_id);

ALTER TABLE public.export_records ENABLE ROW LEVEL SECURITY;

-- Xóa policies cũ trước khi tạo lại
DROP POLICY IF EXISTS "User can insert own export records" ON public.export_records;
DROP POLICY IF EXISTS "User can read own export records"   ON public.export_records;
DROP POLICY IF EXISTS "Admin can read all export records"  ON public.export_records;

CREATE POLICY "User can insert own export records"
    ON public.export_records FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "User can read own export records"
    ON public.export_records FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY "Admin can read all export records"
    ON public.export_records FOR SELECT
    TO authenticated
    USING (
        (auth.jwt() -> 'user_metadata' ->> 'role') IN ('admin', 'moderator')
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid() AND role IN ('admin', 'moderator')
        )
    );

-- RPC cho admin
CREATE OR REPLACE FUNCTION public.get_export_records(
    p_limit  int  DEFAULT 100,
    p_offset int  DEFAULT 0,
    p_brand  text DEFAULT NULL,
    p_user   text DEFAULT NULL
)
RETURNS TABLE (
    id              uuid,
    user_id         uuid,
    username        text,
    filename        text,
    brand_id        text,
    model_id        text,
    display_name    text,
    frame_count     int,
    file_size_bytes bigint,
    storage_path    text,
    created_at      timestamptz
)
LANGUAGE sql SECURITY DEFINER
AS $$
    SELECT er.id, er.user_id, er.username, er.filename,
           er.brand_id, er.model_id, er.display_name,
           er.frame_count, er.file_size_bytes, er.storage_path, er.created_at
    FROM public.export_records er
    WHERE (p_brand IS NULL OR er.brand_id = p_brand)
      AND (p_user  IS NULL OR er.username ILIKE '%' || p_user || '%')
    ORDER BY er.created_at DESC
    LIMIT p_limit OFFSET p_offset;
$$;

REVOKE EXECUTE ON FUNCTION public.get_export_records FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.get_export_records TO authenticated;
