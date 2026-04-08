-- ============================================================
-- Supabase: Bảng export_records cho BKDiagnostic
-- Lưu metadata của mỗi lần xuất file CAN từ app
-- Chạy file này trong Supabase Dashboard > SQL Editor
-- ============================================================

CREATE TABLE IF NOT EXISTS public.export_records (
    id            uuid        DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    username      text        NOT NULL,
    filename      text        NOT NULL,
    brand_id      text        NOT NULL,   -- "ford"
    model_id      text        NOT NULL,   -- "ranger"
    display_name  text        NOT NULL,   -- "Ford Ranger 2.0 Bi-Turbo"
    frame_count   int         NOT NULL DEFAULT 0,
    file_size_bytes bigint    DEFAULT 0,
    storage_path  text        NOT NULL,   -- "{user_id}/{filename}" trong bucket exports
    created_at    timestamptz DEFAULT now() NOT NULL
);

-- Index để query nhanh theo user và thời gian
CREATE INDEX IF NOT EXISTS idx_export_records_user_id    ON public.export_records(user_id);
CREATE INDEX IF NOT EXISTS idx_export_records_created_at ON public.export_records(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_export_records_brand      ON public.export_records(brand_id);

-- ── RLS ──────────────────────────────────────────────────────────────────────
ALTER TABLE public.export_records ENABLE ROW LEVEL SECURITY;

-- User chỉ INSERT record của chính họ
CREATE POLICY "User can insert own export records"
    ON public.export_records FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid());

-- User chỉ đọc record của chính họ
CREATE POLICY "User can read own export records"
    ON public.export_records FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());

-- Admin và moderator đọc tất cả
CREATE POLICY "Admin can read all export records"
    ON public.export_records FOR SELECT
    TO authenticated
    USING (
        (auth.jwt() -> 'user_metadata' ->> 'role') IN ('admin', 'moderator')
        OR
        EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()
              AND role IN ('admin', 'moderator')
        )
    );

-- ── RPC cho admin: lấy tất cả export records kèm thông tin user ──────────────
CREATE OR REPLACE FUNCTION public.get_export_records(
    p_limit  int     DEFAULT 100,
    p_offset int     DEFAULT 0,
    p_brand  text    DEFAULT NULL,
    p_user   text    DEFAULT NULL  -- filter theo username
)
RETURNS TABLE (
    id            uuid,
    user_id       uuid,
    username      text,
    filename      text,
    brand_id      text,
    model_id      text,
    display_name  text,
    frame_count   int,
    file_size_bytes bigint,
    storage_path  text,
    created_at    timestamptz
)
LANGUAGE sql
SECURITY DEFINER
AS $$
    SELECT
        er.id, er.user_id, er.username, er.filename,
        er.brand_id, er.model_id, er.display_name,
        er.frame_count, er.file_size_bytes, er.storage_path, er.created_at
    FROM public.export_records er
    WHERE
        (p_brand IS NULL OR er.brand_id = p_brand)
        AND (p_user IS NULL OR er.username ILIKE '%' || p_user || '%')
    ORDER BY er.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
$$;

-- Chỉ admin/moderator được gọi RPC này
REVOKE EXECUTE ON FUNCTION public.get_export_records FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.get_export_records TO authenticated;

-- ============================================================
-- LƯU Ý
-- ============================================================
-- Policy "Admin can read all export records" dùng 2 điều kiện OR:
--   1. JWT metadata.role = admin/moderator (nếu role lưu trong JWT)
--   2. Bảng profiles.role = admin/moderator (nếu role lưu trong DB)
-- Điều chỉnh theo cách app của bạn lưu role.
-- ============================================================
