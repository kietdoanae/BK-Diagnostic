-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — TR4021 Lab Content v2 (6 labs)
--  Replaces lab-seed.sql v1 (LAB-01, LAB-02 only).
--
--  Order: run AFTER 01-lab-phase1-helpers.sql, 02-lab-schema.sql, 04-lab-rls.sql
--         (RLS policies must exist before content insert).
--  Idempotent: ON CONFLICT DO UPDATE everywhere.
--  Spec: docs/superpowers/specs/2026-05-15-lab-content-tr4021-design.md
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

-- ── Cleanup: remove orphan v1 steps/questions for LAB-01 & LAB-02 ───────────
-- ON CONFLICT DO UPDATE only handles existing PK; rows with step_order >
-- new max would remain. Explicit DELETE ensures clean state.
DELETE FROM public.lab_steps
 WHERE lab_id IN (SELECT id FROM public.labs WHERE code IN ('LAB-01','LAB-02'))
   AND step_order > 8;  -- v2 LAB-01 + LAB-02 each have ≤ 8 steps

DELETE FROM public.lab_questions
 WHERE lab_id IN (SELECT id FROM public.labs WHERE code IN ('LAB-01','LAB-02'))
   AND (
     (phase = 'pre_lab'  AND question_order > 8) OR
     (phase = 'post_lab' AND question_order > 5)
   );
