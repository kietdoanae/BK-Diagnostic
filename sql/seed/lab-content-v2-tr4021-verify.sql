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

-- ── LAB-01 detailed check ──────────────────────────────────────────────────
SELECT 'LAB-01 step evidence types' AS check_name,
       step_order, title, evidence_type, required_count
  FROM public.lab_steps
 WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-01')
 ORDER BY step_order;
-- Expected 8 rows: 1=none/0, 2=screenshot/1, 3=screenshot/1, 4=raw_frames/200,
--                  5=screenshot/1, 6=raw_frames/100, 7=screenshot/1, 8=screenshot/1

SELECT 'LAB-01 pre-quiz' AS check_name, count(*) AS actual, 5 AS expected
  FROM public.lab_questions
 WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-01')
   AND phase = 'pre_lab';

SELECT 'LAB-01 post-quiz' AS check_name, count(*) AS actual, 3 AS expected
  FROM public.lab_questions
 WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-01')
   AND phase = 'post_lab';

-- ── LAB-02 detailed check ──────────────────────────────────────────────────
SELECT 'LAB-02 step count' AS check_name, count(*) AS actual, 8 AS expected
  FROM public.lab_steps WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-02');

SELECT 'LAB-02 pre-quiz' AS check_name, count(*) AS actual, 6 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-02') AND phase = 'pre_lab';

SELECT 'LAB-02 post-quiz' AS check_name, count(*) AS actual, 4 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-02') AND phase = 'post_lab';

-- ── LAB-03 detailed check ──────────────────────────────────────────────────
SELECT 'LAB-03 step count' AS check_name, count(*) AS actual, 8 AS expected
  FROM public.lab_steps WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-03');

SELECT 'LAB-03 pre-quiz' AS check_name, count(*) AS actual, 7 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-03') AND phase = 'pre_lab';

SELECT 'LAB-03 post-quiz' AS check_name, count(*) AS actual, 4 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-03') AND phase = 'post_lab';
