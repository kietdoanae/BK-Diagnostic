# Lab System — Phase 1 (Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the full Supabase backend for the lab experiment system — 10 tables with RLS, 6 RPC functions, an evidence→step trigger, and 2 private storage buckets — idempotent and runnable in Supabase SQL Editor.

**Architecture:** One SQL file per concern (matches existing `activity_logs.sql` / `export_records.sql` / `exports_storage_setup.sql` convention). All DDL uses `CREATE ... IF NOT EXISTS` and `DROP POLICY IF EXISTS` before re-creates so every file can be re-run safely. RLS enforcement hinges on three helpers: `auth.uid()`, a `public.is_staff()` helper to check `profiles.role IN ('admin','moderator')`, and a `public.user_is_in_group(group_id)` helper used by session + evidence policies.

**Tech Stack:** PostgreSQL 15 (Supabase), Supabase Auth (`auth.users`), Supabase Storage, pl/pgSQL for RPCs + triggers, Supabase SQL Editor for execution.

**Spec reference:** `docs/superpowers/specs/2026-04-16-lab-system-design.md` — Section 3 (Database Schema), 3.11 (RLS), 3.12 (RPCs), 3.13 (Evidence type handling).

---

## File Structure

All new files live directly in `website/` (flat, consistent with existing layout):

| File | Responsibility |
|---|---|
| `website/lab_helpers.sql` | Shared SQL helpers: `is_staff()`, `user_is_in_group(uuid)`, `generate_lab_session_code()`. Must be created before tables because RLS policies reference these helpers. |
| `website/lab_schema.sql` | All 10 tables + indexes + `lab_group_members` constraint triggers + `updated_at` triggers. No RLS yet. |
| `website/lab_rls.sql` | `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` + all policies for the 10 tables. |
| `website/lab_rpc.sql` | 6 RPC functions + `lab_evidence` BEFORE INSERT trigger (assigns `step_id`). |
| `website/lab_storage.sql` | 2 private buckets (`lab-reports`, `lab-images`) + storage RLS. |
| `website/lab_verification.sql` | Read-only verification queries the operator runs after migration to smoke-test RLS. Comments only — does not mutate. |

**Execution order (top of each file comments this):** `lab_helpers.sql` → `lab_schema.sql` → `lab_rls.sql` → `lab_rpc.sql` → `lab_storage.sql` → `lab_verification.sql`.

No existing files are modified. Phase 6 (seed content) will add a separate `lab_seed.sql`; do not touch it here.

---

## Task 1: Shared Helpers (`lab_helpers.sql`)

**Files:**
- Create: `website/lab_helpers.sql`

- [ ] **Step 1: Create `lab_helpers.sql` with the three helper functions**

File contents:

```sql
-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Helpers (Phase 1)
--  Order: run FIRST (before lab_schema.sql). Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── is_staff(): true if current user has role admin/moderator ───────────────
CREATE OR REPLACE FUNCTION public.is_staff()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
          AND role IN ('admin', 'moderator')
    );
$$;

GRANT EXECUTE ON FUNCTION public.is_staff() TO authenticated;

-- ── user_is_in_group(p_group uuid) ──────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.user_is_in_group(p_group uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = p_group
          AND user_id = auth.uid()
    );
$$;

GRANT EXECUTE ON FUNCTION public.user_is_in_group(uuid) TO authenticated;

-- ── generate_lab_session_code(): returns a 6-digit code not currently ACTIVE
CREATE OR REPLACE FUNCTION public.generate_lab_session_code()
RETURNS char(6)
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
    v_code char(6);
    v_tries int := 0;
BEGIN
    LOOP
        v_code := lpad((floor(random() * 1000000))::int::text, 6, '0');
        EXIT WHEN NOT EXISTS (
            SELECT 1 FROM public.lab_sessions
            WHERE session_code = v_code AND status = 'ACTIVE'
        );
        v_tries := v_tries + 1;
        IF v_tries > 20 THEN
            RAISE EXCEPTION 'Cannot allocate unique lab session code';
        END IF;
    END LOOP;
    RETURN v_code;
END;
$$;
```

> Note: `user_is_in_group` and `generate_lab_session_code` reference tables created in Task 2. Postgres compiles function bodies lazily, so creating these helpers before the tables is fine — they are only invoked after Task 2 runs.

- [ ] **Step 2: Smoke-test the helpers via Supabase SQL Editor**

Run:
```sql
SELECT public.is_staff();
```
Expected: `false` if run as a non-staff session; `true` if run as staff. No errors.

- [ ] **Step 3: Commit**

```bash
git add website/lab_helpers.sql
git commit -m "feat(lab): add SQL helpers is_staff/user_is_in_group/generate_session_code"
```

---

## Task 2: Core Schema (`lab_schema.sql`)

**Files:**
- Create: `website/lab_schema.sql`

- [ ] **Step 1: Write `lab_schema.sql` header + `labs` + `lab_steps` + `lab_questions`**

```sql
-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Schema (Phase 1)
--  Order: run AFTER lab_helpers.sql, BEFORE lab_rls.sql. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── 1. labs ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.labs (
    id                       uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code                     text        UNIQUE NOT NULL,
    title                    text        NOT NULL,
    description              text,
    order_index              int         NOT NULL DEFAULT 0,
    pre_quiz_pass_threshold  int         NOT NULL DEFAULT 70,
    is_published             boolean     NOT NULL DEFAULT false,
    created_by               uuid        REFERENCES auth.users(id),
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_labs_published ON public.labs(is_published, order_index);

-- ── 2. lab_steps ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_steps (
    id             uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id         uuid    NOT NULL REFERENCES public.labs(id) ON DELETE CASCADE,
    step_order     int     NOT NULL,
    title          text    NOT NULL,
    instruction    text    NOT NULL,
    evidence_type  text    NOT NULL
                   CHECK (evidence_type IN ('raw_frames','active_test','screenshot','none')),
    required_count int     DEFAULT 0,
    hint           text,
    UNIQUE(lab_id, step_order)
);
CREATE INDEX IF NOT EXISTS idx_lab_steps_lab ON public.lab_steps(lab_id, step_order);

-- ── 3. lab_questions ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_questions (
    id              uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id          uuid    NOT NULL REFERENCES public.labs(id) ON DELETE CASCADE,
    phase           text    NOT NULL CHECK (phase IN ('pre_lab','post_lab')),
    question_order  int     NOT NULL,
    question_type   text    NOT NULL
                    CHECK (question_type IN ('multiple_choice','free_text','image_upload')),
    question_text   text    NOT NULL,
    options         jsonb,
    correct_answer  text,
    points          int     NOT NULL DEFAULT 1,
    hint            text,
    UNIQUE(lab_id, phase, question_order)
);
CREATE INDEX IF NOT EXISTS idx_lab_questions_lab_phase
    ON public.lab_questions(lab_id, phase, question_order);
```

- [ ] **Step 2: Append groups + members tables + constraint triggers**

```sql
-- ── 4. lab_groups ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_groups (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id     uuid        NOT NULL REFERENCES public.labs(id) ON DELETE CASCADE,
    name       text        NOT NULL,
    semester   text,
    created_by uuid        REFERENCES auth.users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lab_groups_lab ON public.lab_groups(lab_id);

-- ── 5. lab_group_members ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_group_members (
    group_id uuid NOT NULL REFERENCES public.lab_groups(id) ON DELETE CASCADE,
    user_id  uuid NOT NULL REFERENCES auth.users(id)        ON DELETE CASCADE,
    role     text DEFAULT 'member' CHECK (role IN ('leader','member')),
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_lab_group_members_user ON public.lab_group_members(user_id);

-- Exactly one leader per group (partial unique index).
CREATE UNIQUE INDEX IF NOT EXISTS idx_lab_group_members_one_leader
    ON public.lab_group_members(group_id) WHERE role = 'leader';

-- Student may belong to at most one group per lab.
CREATE OR REPLACE FUNCTION public.lab_group_members_check_unique_lab()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_lab_id uuid;
BEGIN
    SELECT lab_id INTO v_lab_id FROM public.lab_groups WHERE id = NEW.group_id;

    IF EXISTS (
        SELECT 1
        FROM public.lab_group_members m
        JOIN public.lab_groups g ON g.id = m.group_id
        WHERE m.user_id = NEW.user_id
          AND g.lab_id  = v_lab_id
          AND m.group_id <> NEW.group_id
    ) THEN
        RAISE EXCEPTION
            'User % already belongs to another group for lab %', NEW.user_id, v_lab_id;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_lab_group_members_unique_lab
    ON public.lab_group_members;
CREATE TRIGGER trg_lab_group_members_unique_lab
    BEFORE INSERT OR UPDATE ON public.lab_group_members
    FOR EACH ROW EXECUTE FUNCTION public.lab_group_members_check_unique_lab();
```

- [ ] **Step 3: Append sessions + evidence**

```sql
-- ── 6. lab_sessions ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_sessions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        uuid        NOT NULL REFERENCES public.lab_groups(id),
    lab_id          uuid        NOT NULL REFERENCES public.labs(id),
    session_code    char(6)     NOT NULL,
    status          text        NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','CANCELLED')),
    current_step_id uuid        REFERENCES public.lab_steps(id),
    step_started_at timestamptz,
    started_by      uuid        NOT NULL REFERENCES auth.users(id),
    started_at      timestamptz NOT NULL DEFAULT now(),
    ended_at        timestamptz,
    expires_at      timestamptz NOT NULL DEFAULT (now() + interval '3 hours')
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_lab_sessions_code_active
    ON public.lab_sessions(session_code) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_lab_sessions_group
    ON public.lab_sessions(group_id, started_at DESC);

-- ── 7. lab_evidence ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_evidence (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          uuid        NOT NULL REFERENCES public.lab_sessions(id)
                                    ON DELETE CASCADE,
    step_id             uuid        REFERENCES public.lab_steps(id),
    submitted_by        uuid        NOT NULL REFERENCES auth.users(id),
    evidence_type       text        NOT NULL
                        CHECK (evidence_type IN ('raw_frame','active_test','screenshot')),
    payload             jsonb       NOT NULL,
    client_timestamp_ms bigint      NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_evidence_session_step
    ON public.lab_evidence(session_id, step_id);
CREATE INDEX IF NOT EXISTS idx_evidence_created
    ON public.lab_evidence(session_id, created_at DESC);
```

- [ ] **Step 4: Append pre-quiz + post-lab + reports tables**

```sql
-- ── 8. lab_pre_quiz_submissions ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_pre_quiz_submissions (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL REFERENCES auth.users(id),
    lab_id         uuid         NOT NULL REFERENCES public.labs(id),
    answers        jsonb        NOT NULL,
    score_percent  numeric(5,2) NOT NULL,
    passed         boolean      NOT NULL,
    submitted_at   timestamptz  NOT NULL DEFAULT now(),
    attempt_number int          NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_pre_quiz_user_lab
    ON public.lab_pre_quiz_submissions(user_id, lab_id, submitted_at DESC);

-- ── 9. lab_post_submissions ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_post_submissions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES auth.users(id),
    session_id      uuid        NOT NULL REFERENCES public.lab_sessions(id),
    answers         jsonb       NOT NULL,
    uploaded_images jsonb       DEFAULT '[]',
    is_draft        boolean     NOT NULL DEFAULT true,
    teacher_comment text,
    submitted_at    timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, session_id)
);
CREATE INDEX IF NOT EXISTS idx_post_submissions_session
    ON public.lab_post_submissions(session_id);

-- ── 10. lab_reports ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lab_reports (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES auth.users(id),
    session_id       uuid        NOT NULL REFERENCES public.lab_sessions(id),
    pdf_storage_path text        NOT NULL,
    content_hash     text        NOT NULL,
    file_size_bytes  bigint,
    generated_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, session_id)
);
CREATE INDEX IF NOT EXISTS idx_lab_reports_user ON public.lab_reports(user_id, generated_at DESC);
```

- [ ] **Step 5: Append `updated_at` auto-maintenance triggers for `labs` and `lab_post_submissions`**

```sql
-- ── 11. updated_at auto-touch ───────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.lab_touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_labs_updated_at ON public.labs;
CREATE TRIGGER trg_labs_updated_at
    BEFORE UPDATE ON public.labs
    FOR EACH ROW EXECUTE FUNCTION public.lab_touch_updated_at();

DROP TRIGGER IF EXISTS trg_lab_post_submissions_updated_at ON public.lab_post_submissions;
CREATE TRIGGER trg_lab_post_submissions_updated_at
    BEFORE UPDATE ON public.lab_post_submissions
    FOR EACH ROW EXECUTE FUNCTION public.lab_touch_updated_at();
```

- [ ] **Step 6: Enable Realtime publication for `lab_evidence`**

Realtime is needed for the live evidence counter (Section 5.4).

```sql
-- ── 12. Realtime: lab_evidence stream ───────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname   = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename  = 'lab_evidence'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.lab_evidence;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname   = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename  = 'lab_sessions'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.lab_sessions;
    END IF;
END;
$$;
```

- [ ] **Step 7: Run the schema file in Supabase SQL Editor, then verify tables exist**

```sql
SELECT table_name
FROM   information_schema.tables
WHERE  table_schema = 'public'
  AND  table_name LIKE 'lab_%' OR table_name = 'labs'
ORDER  BY table_name;
```
Expected: 10 rows — `lab_evidence`, `lab_group_members`, `lab_groups`, `lab_post_submissions`, `lab_pre_quiz_submissions`, `lab_questions`, `lab_reports`, `lab_sessions`, `lab_steps`, `labs`.

- [ ] **Step 8: Commit**

```bash
git add website/lab_schema.sql
git commit -m "feat(lab): add 10 lab_* tables, indexes, constraint triggers"
```

---

## Task 3: Row Level Security Policies (`lab_rls.sql`)

**Files:**
- Create: `website/lab_rls.sql`

- [ ] **Step 1: Write file header + RLS enable block**

```sql
-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System RLS Policies (Phase 1)
--  Order: run AFTER lab_schema.sql, BEFORE lab_rpc.sql. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.labs                     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_steps                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_questions            ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_groups               ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_group_members        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_sessions             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_evidence             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_pre_quiz_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_post_submissions     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lab_reports              ENABLE ROW LEVEL SECURITY;
```

- [ ] **Step 2: Policies for `labs` / `lab_steps` / `lab_questions` (public-read catalog, staff-write)**

```sql
-- labs ───────────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "labs_select_public" ON public.labs;
DROP POLICY IF EXISTS "labs_staff_all"      ON public.labs;

CREATE POLICY "labs_select_public"
    ON public.labs FOR SELECT TO authenticated
    USING (is_published = true OR public.is_staff());

CREATE POLICY "labs_staff_all"
    ON public.labs FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_steps ──────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_steps_select" ON public.lab_steps;
DROP POLICY IF EXISTS "lab_steps_staff_all" ON public.lab_steps;

CREATE POLICY "lab_steps_select"
    ON public.lab_steps FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.labs l
            WHERE l.id = lab_steps.lab_id AND l.is_published = true
        )
    );

CREATE POLICY "lab_steps_staff_all"
    ON public.lab_steps FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_questions ──────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_questions_select" ON public.lab_questions;
DROP POLICY IF EXISTS "lab_questions_staff_all" ON public.lab_questions;

CREATE POLICY "lab_questions_select"
    ON public.lab_questions FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.labs l
            WHERE l.id = lab_questions.lab_id AND l.is_published = true
        )
    );

CREATE POLICY "lab_questions_staff_all"
    ON public.lab_questions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());
```

- [ ] **Step 3: Policies for groups + members (student sees only own groups)**

```sql
-- lab_groups ─────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_groups_select_own" ON public.lab_groups;
DROP POLICY IF EXISTS "lab_groups_staff_all"  ON public.lab_groups;

CREATE POLICY "lab_groups_select_own"
    ON public.lab_groups FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.lab_group_members m
            WHERE m.group_id = lab_groups.id AND m.user_id = auth.uid()
        )
    );

CREATE POLICY "lab_groups_staff_all"
    ON public.lab_groups FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_group_members ──────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_group_members_select_own" ON public.lab_group_members;
DROP POLICY IF EXISTS "lab_group_members_staff_all"  ON public.lab_group_members;

CREATE POLICY "lab_group_members_select_own"
    ON public.lab_group_members FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR user_id = auth.uid()
        OR public.user_is_in_group(group_id)
    );

CREATE POLICY "lab_group_members_staff_all"
    ON public.lab_group_members FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());
```

- [ ] **Step 4: Policies for `lab_sessions` (own group SELECT; INSERT happens via RPC only)**

```sql
-- lab_sessions ───────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_sessions_select_own_group" ON public.lab_sessions;
DROP POLICY IF EXISTS "lab_sessions_staff_all"        ON public.lab_sessions;

CREATE POLICY "lab_sessions_select_own_group"
    ON public.lab_sessions FOR SELECT TO authenticated
    USING (public.is_staff() OR public.user_is_in_group(group_id));

CREATE POLICY "lab_sessions_staff_all"
    ON public.lab_sessions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());
```

> **Note:** No student-writable policy on `lab_sessions`. Inserts/updates go through `start_lab_session` / `set_current_step` / `end_current_step` RPCs (all `SECURITY DEFINER`), which bypass RLS but do their own membership + leader checks.

- [ ] **Step 5: Policies for `lab_evidence` (INSERT while session ACTIVE, SELECT own group)**

```sql
-- lab_evidence ───────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_evidence_select_own_group" ON public.lab_evidence;
DROP POLICY IF EXISTS "lab_evidence_insert_active"    ON public.lab_evidence;
DROP POLICY IF EXISTS "lab_evidence_staff_all"        ON public.lab_evidence;

CREATE POLICY "lab_evidence_select_own_group"
    ON public.lab_evidence FOR SELECT TO authenticated
    USING (
        public.is_staff()
        OR EXISTS (
            SELECT 1 FROM public.lab_sessions s
            WHERE s.id = lab_evidence.session_id
              AND public.user_is_in_group(s.group_id)
        )
    );

CREATE POLICY "lab_evidence_insert_active"
    ON public.lab_evidence FOR INSERT TO authenticated
    WITH CHECK (
        submitted_by = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.lab_sessions s
            WHERE s.id = lab_evidence.session_id
              AND s.status = 'ACTIVE'
              AND s.expires_at > now()
              AND public.user_is_in_group(s.group_id)
        )
    );

CREATE POLICY "lab_evidence_staff_all"
    ON public.lab_evidence FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());
```

- [ ] **Step 6: Policies for pre-quiz, post-lab, reports (own-row SELECT/INSERT/UPDATE)**

```sql
-- lab_pre_quiz_submissions ───────────────────────────────────────────────────
DROP POLICY IF EXISTS "pre_quiz_select_own"   ON public.lab_pre_quiz_submissions;
DROP POLICY IF EXISTS "pre_quiz_insert_own"   ON public.lab_pre_quiz_submissions;
DROP POLICY IF EXISTS "pre_quiz_staff_all"    ON public.lab_pre_quiz_submissions;

CREATE POLICY "pre_quiz_select_own"
    ON public.lab_pre_quiz_submissions FOR SELECT TO authenticated
    USING (public.is_staff() OR user_id = auth.uid());

CREATE POLICY "pre_quiz_insert_own"
    ON public.lab_pre_quiz_submissions FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "pre_quiz_staff_all"
    ON public.lab_pre_quiz_submissions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_post_submissions ───────────────────────────────────────────────────────
DROP POLICY IF EXISTS "post_submissions_select_own" ON public.lab_post_submissions;
DROP POLICY IF EXISTS "post_submissions_insert_own" ON public.lab_post_submissions;
DROP POLICY IF EXISTS "post_submissions_update_own" ON public.lab_post_submissions;
DROP POLICY IF EXISTS "post_submissions_staff_all"  ON public.lab_post_submissions;

CREATE POLICY "post_submissions_select_own"
    ON public.lab_post_submissions FOR SELECT TO authenticated
    USING (public.is_staff() OR user_id = auth.uid());

CREATE POLICY "post_submissions_insert_own"
    ON public.lab_post_submissions FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "post_submissions_update_own"
    ON public.lab_post_submissions FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "post_submissions_staff_all"
    ON public.lab_post_submissions FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());

-- lab_reports ────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "lab_reports_select_own" ON public.lab_reports;
DROP POLICY IF EXISTS "lab_reports_insert_own" ON public.lab_reports;
DROP POLICY IF EXISTS "lab_reports_staff_all"  ON public.lab_reports;

CREATE POLICY "lab_reports_select_own"
    ON public.lab_reports FOR SELECT TO authenticated
    USING (public.is_staff() OR user_id = auth.uid());

CREATE POLICY "lab_reports_insert_own"
    ON public.lab_reports FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "lab_reports_staff_all"
    ON public.lab_reports FOR ALL TO authenticated
    USING (public.is_staff())
    WITH CHECK (public.is_staff());
```

- [ ] **Step 7: Run the RLS file in Supabase SQL Editor, then verify all 10 tables have RLS enabled**

```sql
SELECT c.relname AS table_name, c.relrowsecurity AS rls_enabled
FROM   pg_class c
JOIN   pg_namespace n ON n.oid = c.relnamespace
WHERE  n.nspname = 'public'
  AND  c.relname IN (
         'labs','lab_steps','lab_questions','lab_groups','lab_group_members',
         'lab_sessions','lab_evidence','lab_pre_quiz_submissions',
         'lab_post_submissions','lab_reports'
       )
ORDER  BY c.relname;
```
Expected: 10 rows, `rls_enabled = true` for every row.

- [ ] **Step 8: Commit**

```bash
git add website/lab_rls.sql
git commit -m "feat(lab): add RLS policies for all lab_* tables"
```

---

## Task 4: RPC Functions + Evidence Trigger (`lab_rpc.sql`)

**Files:**
- Create: `website/lab_rpc.sql`

- [ ] **Step 1: Write file header + `start_lab_session`**

```sql
-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System RPC (Phase 1)
--  Order: run AFTER lab_rls.sql. Idempotent.
-- ════════════════════════════════════════════════════════════════════════════

-- ── 1. start_lab_session(lab_id) ────────────────────────────────────────────
--     Caller must be group leader for a group of that lab AND have passed pre-lab.
CREATE OR REPLACE FUNCTION public.start_lab_session(p_lab_id uuid)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid        uuid := auth.uid();
    v_group_id   uuid;
    v_passed     boolean;
    v_session_id uuid;
    v_code       char(6);
    v_expires_at timestamptz;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Must be leader of a group attached to this lab.
    SELECT g.id INTO v_group_id
    FROM   public.lab_groups g
    JOIN   public.lab_group_members m ON m.group_id = g.id
    WHERE  g.lab_id = p_lab_id
      AND  m.user_id = v_uid
      AND  m.role = 'leader';

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Only the group leader may start a practice session';
    END IF;

    -- Must have passed pre-lab on latest attempt.
    SELECT passed INTO v_passed
    FROM   public.lab_pre_quiz_submissions
    WHERE  user_id = v_uid AND lab_id = p_lab_id
    ORDER  BY submitted_at DESC
    LIMIT  1;

    IF v_passed IS NOT TRUE THEN
        RAISE EXCEPTION 'Leader must pass the pre-lab quiz before starting practice';
    END IF;

    -- Disallow if there is already an ACTIVE session for this group.
    IF EXISTS (
        SELECT 1 FROM public.lab_sessions
        WHERE group_id = v_group_id AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Group already has an active session';
    END IF;

    v_code := public.generate_lab_session_code();

    INSERT INTO public.lab_sessions (group_id, lab_id, session_code, started_by)
    VALUES (v_group_id, p_lab_id, v_code, v_uid)
    RETURNING id, expires_at INTO v_session_id, v_expires_at;

    RETURN json_build_object(
        'session_id',   v_session_id,
        'session_code', v_code,
        'expires_at',   v_expires_at
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.start_lab_session(uuid) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.start_lab_session(uuid) TO authenticated;
```

- [ ] **Step 2: Append `validate_lab_code`**

```sql
-- ── 2. validate_lab_code(code) ──────────────────────────────────────────────
--     App calls this after user enters 6-digit code.
--     Succeeds only if caller is a member of the session's group.
CREATE OR REPLACE FUNCTION public.validate_lab_code(p_code text)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid      uuid := auth.uid();
    v_session  public.lab_sessions%ROWTYPE;
    v_lab      public.labs%ROWTYPE;
    v_group    public.lab_groups%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_session
    FROM   public.lab_sessions
    WHERE  session_code = p_code AND status = 'ACTIVE'
    LIMIT  1;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Invalid or expired code';
    END IF;

    IF v_session.expires_at < now() THEN
        RAISE EXCEPTION 'Session has expired';
    END IF;

    -- Caller must be a member of the session's group.
    IF NOT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = v_session.group_id AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'You are not a member of this group';
    END IF;

    SELECT * INTO v_lab   FROM public.labs       WHERE id = v_session.lab_id;
    SELECT * INTO v_group FROM public.lab_groups WHERE id = v_session.group_id;

    RETURN json_build_object(
        'session_id', v_session.id,
        'lab_id',     v_lab.id,
        'lab_title',  v_lab.title,
        'lab_code',   v_lab.code,
        'group_name', v_group.name,
        'expires_at', v_session.expires_at
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.validate_lab_code(text) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.validate_lab_code(text) TO authenticated;
```

- [ ] **Step 3: Append `submit_pre_quiz` (auto-grade multiple choice)**

```sql
-- ── 3. submit_pre_quiz(lab_id, answers) ─────────────────────────────────────
--     answers is jsonb: { "<question_id>": "A" | "free text" | "image:/path" }
--     Only multiple_choice contributes to auto-grade; free_text & image_upload
--     count as full credit (teacher reviews them offline).
CREATE OR REPLACE FUNCTION public.submit_pre_quiz(
    p_lab_id  uuid,
    p_answers jsonb
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid            uuid := auth.uid();
    v_total_points   int := 0;
    v_earned_points  numeric := 0;
    v_score_percent  numeric(5,2);
    v_threshold      int;
    v_passed         boolean;
    v_attempt_number int;
    r                record;
    v_user_answer    text;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT pre_quiz_pass_threshold INTO v_threshold
    FROM public.labs WHERE id = p_lab_id;

    IF v_threshold IS NULL THEN
        RAISE EXCEPTION 'Lab not found';
    END IF;

    FOR r IN
        SELECT id, question_type, correct_answer, points
        FROM   public.lab_questions
        WHERE  lab_id = p_lab_id AND phase = 'pre_lab'
    LOOP
        v_total_points := v_total_points + r.points;
        v_user_answer  := p_answers ->> r.id::text;

        IF r.question_type = 'multiple_choice' THEN
            IF v_user_answer IS NOT NULL AND v_user_answer = r.correct_answer THEN
                v_earned_points := v_earned_points + r.points;
            END IF;
        ELSE
            -- free_text / image_upload: credit if answered
            IF v_user_answer IS NOT NULL AND length(trim(v_user_answer)) > 0 THEN
                v_earned_points := v_earned_points + r.points;
            END IF;
        END IF;
    END LOOP;

    IF v_total_points = 0 THEN
        v_score_percent := 0;
    ELSE
        v_score_percent := round((v_earned_points / v_total_points) * 100.0, 2);
    END IF;

    v_passed := v_score_percent >= v_threshold;

    SELECT COALESCE(MAX(attempt_number), 0) + 1 INTO v_attempt_number
    FROM   public.lab_pre_quiz_submissions
    WHERE  user_id = v_uid AND lab_id = p_lab_id;

    INSERT INTO public.lab_pre_quiz_submissions
        (user_id, lab_id, answers, score_percent, passed, attempt_number)
    VALUES
        (v_uid, p_lab_id, p_answers, v_score_percent, v_passed, v_attempt_number);

    RETURN json_build_object(
        'score_percent',  v_score_percent,
        'passed',         v_passed,
        'attempt_number', v_attempt_number,
        'threshold',      v_threshold
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.submit_pre_quiz(uuid, jsonb) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.submit_pre_quiz(uuid, jsonb) TO authenticated;
```

- [ ] **Step 4: Append `set_current_step` + `end_current_step`**

```sql
-- ── 4. set_current_step(session_id, step_id) ────────────────────────────────
CREATE OR REPLACE FUNCTION public.set_current_step(
    p_session_id uuid,
    p_step_id    uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid      uuid := auth.uid();
    v_group_id uuid;
    v_lab_id   uuid;
    v_status   text;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, lab_id, status INTO v_group_id, v_lab_id, v_status
    FROM   public.lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = v_group_id AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'You are not a member of this group';
    END IF;

    -- Step must belong to session's lab
    IF NOT EXISTS (
        SELECT 1 FROM public.lab_steps
        WHERE id = p_step_id AND lab_id = v_lab_id
    ) THEN
        RAISE EXCEPTION 'Step does not belong to this lab';
    END IF;

    UPDATE public.lab_sessions
       SET current_step_id = p_step_id,
           step_started_at = now()
     WHERE id = p_session_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.set_current_step(uuid, uuid) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.set_current_step(uuid, uuid) TO authenticated;

-- ── 5. end_current_step(session_id) ─────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.end_current_step(p_session_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid      uuid := auth.uid();
    v_group_id uuid;
    v_status   text;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT group_id, status INTO v_group_id, v_status
    FROM   public.lab_sessions WHERE id = p_session_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Session not found';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Session is not ACTIVE';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.lab_group_members
        WHERE group_id = v_group_id AND user_id = v_uid
    ) THEN
        RAISE EXCEPTION 'You are not a member of this group';
    END IF;

    UPDATE public.lab_sessions
       SET current_step_id = NULL,
           step_started_at = NULL
     WHERE id = p_session_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.end_current_step(uuid) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.end_current_step(uuid) TO authenticated;
```

- [ ] **Step 5: Append `expire_old_sessions`**

```sql
-- ── 6. expire_old_sessions() ────────────────────────────────────────────────
--     Scheduled job (pg_cron or Edge Function every 10 minutes).
--     Marks ACTIVE sessions whose expires_at has passed as EXPIRED.
CREATE OR REPLACE FUNCTION public.expire_old_sessions()
RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_count int;
BEGIN
    WITH expired AS (
        UPDATE public.lab_sessions
           SET status   = 'EXPIRED',
               ended_at = now()
         WHERE status = 'ACTIVE' AND expires_at < now()
        RETURNING id
    )
    SELECT count(*) INTO v_count FROM expired;

    RETURN v_count;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.expire_old_sessions() FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.expire_old_sessions() TO service_role;
```

> **Scheduling note (document, do not automate here):** Attach this RPC either via `pg_cron` (`SELECT cron.schedule('expire-lab-sessions', '*/10 * * * *', $$SELECT public.expire_old_sessions();$$)`) if the extension is available, or via a Supabase Edge Function scheduled every 10 minutes that calls this function with the service-role key. The operator sets this up after the migration lands.

- [ ] **Step 6: Append the `lab_evidence` BEFORE INSERT trigger (assigns `step_id`)**

```sql
-- ── 7. Trigger: lab_evidence.step_id auto-assignment ────────────────────────
--     If session has a current_step_id at insert time, copy it onto the row.
CREATE OR REPLACE FUNCTION public.lab_evidence_assign_step()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_step uuid;
BEGIN
    IF NEW.step_id IS NULL THEN
        SELECT current_step_id INTO v_current_step
        FROM   public.lab_sessions
        WHERE  id = NEW.session_id;

        IF v_current_step IS NOT NULL THEN
            NEW.step_id := v_current_step;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_lab_evidence_assign_step ON public.lab_evidence;
CREATE TRIGGER trg_lab_evidence_assign_step
    BEFORE INSERT ON public.lab_evidence
    FOR EACH ROW EXECUTE FUNCTION public.lab_evidence_assign_step();
```

- [ ] **Step 7: Run the RPC file in Supabase SQL Editor, then verify all 6 functions + trigger exist**

```sql
SELECT proname
FROM   pg_proc p
JOIN   pg_namespace n ON n.oid = p.pronamespace
WHERE  n.nspname = 'public'
  AND  proname IN ('start_lab_session','validate_lab_code','submit_pre_quiz',
                   'set_current_step','end_current_step','expire_old_sessions',
                   'lab_evidence_assign_step')
ORDER  BY proname;
```
Expected: 7 rows.

```sql
SELECT tgname FROM pg_trigger
WHERE  tgrelid = 'public.lab_evidence'::regclass
  AND  NOT tgisinternal;
```
Expected: includes `trg_lab_evidence_assign_step`.

- [ ] **Step 8: Commit**

```bash
git add website/lab_rpc.sql
git commit -m "feat(lab): add 6 RPCs + evidence step-assign trigger"
```

---

## Task 5: Storage Buckets (`lab_storage.sql`)

**Files:**
- Create: `website/lab_storage.sql`

- [ ] **Step 1: Write the full storage setup file**

```sql
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
```

- [ ] **Step 2: Run the file, then verify buckets exist**

```sql
SELECT id, public, file_size_limit, allowed_mime_types
FROM   storage.buckets
WHERE  id IN ('lab-reports','lab-images');
```
Expected: 2 rows, both `public = false`.

- [ ] **Step 3: Commit**

```bash
git add website/lab_storage.sql
git commit -m "feat(lab): add private storage buckets lab-reports & lab-images"
```

---

## Task 6: Verification Harness (`lab_verification.sql`)

**Files:**
- Create: `website/lab_verification.sql`

- [ ] **Step 1: Write the verification file — read-only queries grouped by concern**

```sql
-- ════════════════════════════════════════════════════════════════════════════
--  BK Diagnostic — Lab System Verification (Phase 1)
--  NOTE: run each section independently in the Supabase SQL Editor.
--        Replace <PLACEHOLDER> values with real IDs from your environment.
--        This file is NOT part of the migration; it is a smoke-test harness.
-- ════════════════════════════════════════════════════════════════════════════

-- ── A. Existence checks ─────────────────────────────────────────────────────

-- A1. All 10 lab tables exist.
SELECT table_name
FROM   information_schema.tables
WHERE  table_schema = 'public'
  AND  table_name IN (
         'labs','lab_steps','lab_questions','lab_groups','lab_group_members',
         'lab_sessions','lab_evidence','lab_pre_quiz_submissions',
         'lab_post_submissions','lab_reports'
       )
ORDER  BY table_name;
-- Expected: 10 rows.

-- A2. All 10 tables have RLS enabled.
SELECT c.relname, c.relrowsecurity
FROM   pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE  n.nspname = 'public'
  AND  c.relname IN (
         'labs','lab_steps','lab_questions','lab_groups','lab_group_members',
         'lab_sessions','lab_evidence','lab_pre_quiz_submissions',
         'lab_post_submissions','lab_reports'
       );
-- Expected: relrowsecurity = true for every row.

-- A3. All 6 RPCs + helpers exist.
SELECT proname
FROM   pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE  n.nspname = 'public'
  AND  proname IN (
         'is_staff','user_is_in_group','generate_lab_session_code',
         'start_lab_session','validate_lab_code','submit_pre_quiz',
         'set_current_step','end_current_step','expire_old_sessions',
         'lab_evidence_assign_step','lab_group_members_check_unique_lab',
         'lab_touch_updated_at'
       )
ORDER  BY proname;
-- Expected: 12 rows.

-- A4. Storage buckets exist and are private.
SELECT id, public FROM storage.buckets WHERE id IN ('lab-reports','lab-images');
-- Expected: 2 rows, public = false.

-- A5. Realtime publication includes lab_evidence.
SELECT tablename FROM pg_publication_tables
WHERE  pubname = 'supabase_realtime' AND schemaname = 'public'
  AND  tablename IN ('lab_evidence','lab_sessions');
-- Expected: 2 rows.

-- ── B. RLS behavior (run while authenticated as a STUDENT, not staff) ───────

-- B1. Student sees only published labs.
--     Create 1 published + 1 draft lab as staff, then run as student:
SELECT code, is_published FROM public.labs ORDER BY code;
-- Expected: only published labs returned.

-- B2. Student cannot SELECT a group they are not a member of.
--     Seed two groups (A, B) for same lab, add student only to A, then:
SELECT id, name FROM public.lab_groups WHERE lab_id = '<LAB_ID>';
-- Expected: only group A returned.

-- B3. Student cannot INSERT evidence into a session whose group they don't belong to.
INSERT INTO public.lab_evidence
    (session_id, submitted_by, evidence_type, payload, client_timestamp_ms)
VALUES ('<OTHER_GROUP_SESSION_ID>', auth.uid(), 'raw_frame', '{}'::jsonb, 0);
-- Expected: RLS violation error.

-- B4. Student CAN INSERT evidence into an ACTIVE session of their own group.
INSERT INTO public.lab_evidence
    (session_id, submitted_by, evidence_type, payload, client_timestamp_ms)
VALUES ('<OWN_ACTIVE_SESSION_ID>', auth.uid(), 'raw_frame',
        '{"frames":[]}'::jsonb, 0)
RETURNING id, step_id;
-- Expected: one row; step_id equals the session's current_step_id
--           if set_current_step was previously called.

-- B5. Student cannot INSERT into an EXPIRED session.
UPDATE public.lab_sessions SET status = 'EXPIRED' WHERE id = '<OWN_SESSION_ID>';
-- (then as student)
INSERT INTO public.lab_evidence
    (session_id, submitted_by, evidence_type, payload, client_timestamp_ms)
VALUES ('<OWN_SESSION_ID>', auth.uid(), 'raw_frame', '{}'::jsonb, 0);
-- Expected: RLS violation error.

-- B6. Student sees only own pre-quiz submissions.
SELECT user_id FROM public.lab_pre_quiz_submissions;
-- Expected: only rows where user_id = auth.uid().

-- ── C. RPC behavior ─────────────────────────────────────────────────────────

-- C1. start_lab_session fails for non-leader.
SELECT public.start_lab_session('<LAB_ID>');
-- Expected (member, not leader): 'Only the group leader may start...'

-- C2. start_lab_session fails if leader has not passed pre-lab.
-- Expected: 'Leader must pass the pre-lab quiz...'

-- C3. start_lab_session succeeds for eligible leader; returns 6-digit code.
SELECT public.start_lab_session('<LAB_ID>');
-- Expected: { session_id, session_code (6 digits), expires_at }.

-- C4. validate_lab_code returns session info for member, errors for outsider.
SELECT public.validate_lab_code('123456');

-- C5. submit_pre_quiz auto-grades multiple choice correctly.
--    Seed 5 MC questions with known correct_answer, call with known answers:
SELECT public.submit_pre_quiz(
    '<LAB_ID>',
    jsonb_build_object('<Q1_ID>','A','<Q2_ID>','B')
);
-- Expected: score_percent matches hand calculation.

-- C6. set_current_step updates the session; subsequent INSERT into
--     lab_evidence (without explicit step_id) picks up step_id via trigger.
SELECT public.set_current_step('<SESSION_ID>','<STEP_ID>');

-- C7. end_current_step clears the step.
SELECT public.end_current_step('<SESSION_ID>');

-- C8. expire_old_sessions flips stale ACTIVE rows to EXPIRED.
UPDATE public.lab_sessions SET expires_at = now() - interval '1 minute'
WHERE  id = '<SESSION_ID>';
SELECT public.expire_old_sessions();
-- Expected: returns count >= 1; session now status='EXPIRED'.

-- ── D. Constraint triggers ──────────────────────────────────────────────────

-- D1. One-leader-per-group partial unique index.
-- (as staff) Insert a second leader into a group that already has one:
INSERT INTO public.lab_group_members (group_id, user_id, role)
VALUES ('<GROUP_ID>','<OTHER_USER_ID>','leader');
-- Expected: unique violation on idx_lab_group_members_one_leader.

-- D2. One-group-per-lab per user trigger.
-- Add a user that already belongs to group A (for lab X) into group B (also lab X):
INSERT INTO public.lab_group_members (group_id, user_id, role)
VALUES ('<GROUP_B_ID>','<USER_ID_IN_GROUP_A>','member');
-- Expected: exception 'User % already belongs to another group for lab %'.
```

- [ ] **Step 2: Commit**

```bash
git add website/lab_verification.sql
git commit -m "docs(lab): add verification SQL harness for Phase 1"
```

---

## Task 7: End-to-End Smoke Test in a Fresh Supabase Project

**Files:** none (manual operator task)

- [ ] **Step 1: Run all 5 migration files in order in a clean Supabase project**

Order:
1. `website/lab_helpers.sql`
2. `website/lab_schema.sql`
3. `website/lab_rls.sql`
4. `website/lab_rpc.sql`
5. `website/lab_storage.sql`

Expected: no errors at any step.

- [ ] **Step 2: Run each file a second time**

Expected: no errors (idempotency).

- [ ] **Step 3: Seed one test lab + one test group (via SQL Editor as staff)**

```sql
INSERT INTO public.labs (code, title, is_published, pre_quiz_pass_threshold)
VALUES ('LAB-TEST', 'Phase 1 smoke test', true, 60)
RETURNING id;
-- record as $LAB

INSERT INTO public.lab_steps (lab_id, step_order, title, instruction,
                              evidence_type, required_count)
VALUES ($LAB, 1, 'Capture', 'Capture frames', 'raw_frames', 5)
RETURNING id;
-- record as $STEP

INSERT INTO public.lab_questions (lab_id, phase, question_order,
                                  question_type, question_text,
                                  options, correct_answer, points)
VALUES ($LAB, 'pre_lab', 1, 'multiple_choice', 'HS-CAN speed?',
        '{"A":"125 kbps","B":"500 kbps","C":"1 Mbps"}'::jsonb, 'B', 1);

INSERT INTO public.lab_groups (lab_id, name) VALUES ($LAB, 'G1') RETURNING id;
-- record as $GROUP

-- Add 1 leader + 1 member (use real auth.users IDs)
INSERT INTO public.lab_group_members (group_id, user_id, role) VALUES
  ($GROUP, '<LEADER_UID>',  'leader'),
  ($GROUP, '<MEMBER_UID>',  'member');
```

- [ ] **Step 4: Walk through sections B, C, D of `lab_verification.sql`**

Go through each check and confirm expected results. Record any discrepancy immediately — do not proceed.

- [ ] **Step 5: Commit any fixes discovered during smoke-test**

If any correction was needed, commit it against the offending SQL file. Do **not** commit the smoke-test seed rows — they live only in the test project.

---

## Summary Commit + Index Update

- [ ] **Step 1: Final sanity diff**

```bash
git status
git log --oneline -10
```

Confirm exactly 6 new commits in the worktree (one per SQL file — helpers, schema, rls, rpc, storage, verification — plus any smoke-test fixes).

- [ ] **Step 2: Optional — update an ops doc if one exists**

Grep for `activity_logs.sql` references in `docs/` to see whether a migration-order doc exists. If so, add the 5 lab files in execution order. If not, skip (the header comment inside each file is authoritative).

---

## Out of Scope for Phase 1

Explicitly NOT part of this plan (deferred to later phases):
- Seed content for LAB-01 and LAB-02 → Phase 6.
- Any Kotlin / React code → Phases 2, 3, 4.
- PDF template & hashing → Phase 5.
- pg_cron / Edge Function scheduling for `expire_old_sessions` → operator concern, documented in Task 4 Step 5 but not automated.

Plan complete and saved to `docs/superpowers/plans/2026-04-17-lab-phase-1-foundation.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
