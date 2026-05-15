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

-- ── LAB-04 detailed check ──────────────────────────────────────────────────
SELECT 'LAB-04 step count' AS check_name, count(*) AS actual, 7 AS expected
  FROM public.lab_steps WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-04');

SELECT 'LAB-04 pre-quiz' AS check_name, count(*) AS actual, 5 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-04') AND phase = 'pre_lab';

SELECT 'LAB-04 post-quiz' AS check_name, count(*) AS actual, 4 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-04') AND phase = 'post_lab';

-- ── LAB-05 detailed check ──────────────────────────────────────────────────
SELECT 'LAB-05 step count' AS check_name, count(*) AS actual, 7 AS expected
  FROM public.lab_steps WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-05');

SELECT 'LAB-05 pre-quiz' AS check_name, count(*) AS actual, 6 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-05') AND phase = 'pre_lab';

SELECT 'LAB-05 post-quiz' AS check_name, count(*) AS actual, 4 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-05') AND phase = 'post_lab';

-- ── LAB-06 detailed check ──────────────────────────────────────────────────
SELECT 'LAB-06 step count' AS check_name, count(*) AS actual, 6 AS expected
  FROM public.lab_steps WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-06');

SELECT 'LAB-06 pre-quiz' AS check_name, count(*) AS actual, 8 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-06') AND phase = 'pre_lab';

SELECT 'LAB-06 post-quiz' AS check_name, count(*) AS actual, 5 AS expected
  FROM public.lab_questions WHERE lab_id = (SELECT id FROM public.labs WHERE code = 'LAB-06') AND phase = 'post_lab';

-- ════════════════════════════════════════════════════════════════════════════
--  TR4021 v2 — Full integrity report
-- ════════════════════════════════════════════════════════════════════════════

-- 1. All labs published & ordered correctly
SELECT 'Labs ordered & published' AS check_name,
       code, title, order_index, pre_quiz_pass_threshold, is_published
  FROM public.labs
 WHERE code LIKE 'LAB-0%'
 ORDER BY order_index;

-- 2. Evidence type distribution (sanity)
SELECT 'Evidence types in use' AS check_name,
       evidence_type, count(*) AS step_count
  FROM public.lab_steps
 WHERE lab_id IN (SELECT id FROM public.labs WHERE code LIKE 'LAB-0%')
 GROUP BY evidence_type
 ORDER BY evidence_type;

-- 3. Required count totals per lab
SELECT 'Evidence totals per lab' AS check_name,
       l.code,
       sum(CASE WHEN s.evidence_type = 'raw_frames'  THEN s.required_count ELSE 0 END) AS raw_frames_required,
       sum(CASE WHEN s.evidence_type = 'active_test' THEN s.required_count ELSE 0 END) AS active_test_required,
       sum(CASE WHEN s.evidence_type = 'screenshot' THEN s.required_count ELSE 0 END) AS screenshot_required
  FROM public.labs l
  JOIN public.lab_steps s ON s.lab_id = l.id
 WHERE l.code LIKE 'LAB-0%'
 GROUP BY l.code
 ORDER BY l.code;

-- 4. Question type distribution per lab
SELECT 'Question types' AS check_name,
       l.code, q.phase, q.question_type, count(*) AS qty
  FROM public.labs l
  JOIN public.lab_questions q ON q.lab_id = l.id
 WHERE l.code LIKE 'LAB-0%'
 GROUP BY l.code, q.phase, q.question_type
 ORDER BY l.code, q.phase, q.question_type;

-- 5. Multiple choice sanity — all MC must have non-NULL correct_answer
SELECT 'MC missing correct_answer' AS check_name, count(*) AS actual, 0 AS expected
  FROM public.lab_questions
 WHERE question_type = 'multiple_choice'
   AND (correct_answer IS NULL OR correct_answer = '');
-- Expected: 0 rows missing

-- 6. Final totals
SELECT 'TOTAL' AS check_name,
       (SELECT count(*) FROM public.labs WHERE code LIKE 'LAB-0%' AND is_published) AS labs,
       (SELECT count(*) FROM public.lab_steps
         WHERE lab_id IN (SELECT id FROM public.labs WHERE code LIKE 'LAB-0%')) AS steps,
       (SELECT count(*) FROM public.lab_questions
         WHERE lab_id IN (SELECT id FROM public.labs WHERE code LIKE 'LAB-0%')) AS questions;
-- Expected: labs=6, steps=44, questions=61
--   Steps: LAB-01 (8) + LAB-02 (8) + LAB-03 (8) + LAB-04 (7) + LAB-05 (7) + LAB-06 (6) = 44
--   Questions: LAB-01 (5+3=8) + LAB-02 (6+4=10) + LAB-03 (7+4=11) + LAB-04 (5+4=9) + LAB-05 (6+4=10) + LAB-06 (8+5=13) = 61
