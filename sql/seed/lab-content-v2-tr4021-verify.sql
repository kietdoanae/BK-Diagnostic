-- ════════════════════════════════════════════════════════════════════════════
--  TR4021 Lab Content v2 — Verification Queries
--  Run these AFTER lab-content-v2-tr4021.sql to confirm seed integrity.
--  Each block prints a counter; mismatch = re-check seed.
-- ════════════════════════════════════════════════════════════════════════════

-- Top-level count check
SELECT 'labs published'   AS check_name, count(*) AS actual, 6 AS expected
  FROM public.labs WHERE is_published = true AND code LIKE 'LAB-0%';

-- Per-lab step + question summary
SELECT l.code,
       (SELECT count(*) FROM public.lab_steps     WHERE lab_id = l.id) AS step_count,
       (SELECT count(*) FROM public.lab_questions WHERE lab_id = l.id AND phase = 'pre_lab')  AS pre_q_count,
       (SELECT count(*) FROM public.lab_questions WHERE lab_id = l.id AND phase = 'post_lab') AS post_q_count
  FROM public.labs l
 WHERE l.code LIKE 'LAB-0%'
 ORDER BY l.code;
-- Expected output after full seed:
--  LAB-01 | 8 | 5 | 3
--  LAB-02 | 8 | 6 | 4
--  LAB-03 | 8 | 7 | 4
--  LAB-04 | 7 | 5 | 4
--  LAB-05 | 7 | 6 | 4
--  LAB-06 | 6 | 8 | 5
