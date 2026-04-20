# Lab System Phase 3 — Web Admin CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 4 admin tabs (Labs, Groups, Sessions, Submissions) to `web/src/pages/AdminPage.jsx` so staff can fully CRUD lab content, manage groups, monitor sessions, and review/bulk-download student submissions.

**Architecture:** Direct Supabase JS client calls from a single `services/labApi.js` module (admin tables already have RLS policies that allow staff full access via `is_staff()`). Each tab is a self-contained component under `pages/admin/`; reusable form bits live under `components/admin/`. Drag-drop reorder uses `@dnd-kit` (React 19 compatible). Markdown editing uses `@uiw/react-md-editor`. Bulk PDF ZIP export uses `jszip`.

**Tech Stack:** React 19, Ant Design 6, `@supabase/supabase-js` 2, react-router-dom 7, plus 3 new deps: `@dnd-kit/core` + `@dnd-kit/sortable`, `@uiw/react-md-editor`, `jszip`.

**Note on testing:** This codebase has no test runner installed (see `web/package.json`). Verification per task is `npm run build` + `npm run lint` (must both pass) plus a manual smoke test in `npm run dev` against the live Supabase using a staff account. We do **not** add a test framework in this phase — that is out of scope and would balloon the change. If the executor disagrees, they should raise it before starting.

---

## File Structure

### New files
```
web/src/services/labApi.js                            -- All lab CRUD + storage helpers
web/src/components/admin/MarkdownEditor.jsx           -- Reusable MD editor wrapper
web/src/components/admin/LabForm.jsx                  -- Create/edit lab modal
web/src/components/admin/StepList.jsx                 -- DnD-orderable step list
web/src/components/admin/StepForm.jsx                 -- Create/edit step modal
web/src/components/admin/QuestionList.jsx             -- DnD-orderable question list
web/src/components/admin/QuestionForm.jsx             -- Create/edit question modal
web/src/components/admin/GroupForm.jsx                -- Create/edit group modal w/ MSSV autocomplete
web/src/components/admin/GroupBulkImport.jsx          -- CSV bulk-import groups modal
web/src/components/admin/SessionDetail.jsx            -- Session drill-down drawer
web/src/components/admin/SubmissionDetail.jsx        -- Per-student submission drawer
web/src/pages/admin/LabsAdminTab.jsx                  -- Tab: lab tree + nested step/question editing
web/src/pages/admin/GroupsAdminTab.jsx                -- Tab: groups list + CSV import
web/src/pages/admin/SessionsAdminTab.jsx              -- Tab: filterable sessions list
web/src/pages/admin/SubmissionsAdminTab.jsx           -- Tab: submissions table + bulk ZIP
```

### Modified files
```
web/package.json                                      -- Add 3 deps
web/src/pages/AdminPage.jsx                           -- Add 4 new tab entries
```

### Responsibility boundaries
- `labApi.js` — **only** raw Supabase calls and small data shaping. No JSX, no Ant Design.
- `components/admin/*Form.jsx` — single-purpose modal forms. Each owns local form state via `Form.useForm()` and calls back to parent via `onSaved`.
- `components/admin/*List.jsx` — display + DnD reorder; emits `onReorder(newOrder)` to parent.
- `pages/admin/*Tab.jsx` — tab-level data fetching, filter state, and orchestration. Holds the modal open/close state for its child Forms.

---

## Task 1: Install dependencies

**Files:**
- Modify: `web/package.json`
- Modify: `web/package-lock.json` (auto via npm)

- [ ] **Step 1: Install runtime deps**

```bash
cd web
npm install @dnd-kit/core@^6.3.1 @dnd-kit/sortable@^10.0.0 @dnd-kit/utilities@^3.2.2 @uiw/react-md-editor@^4.0.5 jszip@^3.10.1
```

Expected: `package.json` `dependencies` now contains the 5 packages above; install completes without peer-dep warnings about React 19 (these libs declare React 18+ peer support).

- [ ] **Step 2: Verify build still passes after dep changes**

Run: `cd web && npm run build`
Expected: Vite build completes with no errors. Output ends with `✓ built in <time>` and emits `dist/` files. If you see "Cannot find module …", the install in Step 1 was incomplete — re-run.

- [ ] **Step 3: Commit**

```bash
git add web/package.json web/package-lock.json
git commit -m "chore(web): add dnd-kit, react-md-editor, jszip for lab admin"
```

---

## Task 2: Scaffold `labApi.js` — labs CRUD

**Files:**
- Create: `web/src/services/labApi.js`

- [ ] **Step 1: Create the file with labs CRUD only**

Create `web/src/services/labApi.js` with:

```javascript
import { supabase } from './supabase'

// ─── Labs ────────────────────────────────────────────────────────────────────

export async function listLabs() {
  return supabase
    .from('labs')
    .select('*')
    .order('order_index', { ascending: true })
    .order('created_at', { ascending: true })
}

export async function getLab(labId) {
  return supabase.from('labs').select('*').eq('id', labId).single()
}

export async function createLab(payload) {
  // payload: { code, title, description, order_index, pre_quiz_pass_threshold, is_published }
  return supabase.from('labs').insert(payload).select().single()
}

export async function updateLab(labId, patch) {
  return supabase.from('labs').update(patch).eq('id', labId).select().single()
}

export async function deleteLab(labId) {
  // ON DELETE CASCADE in schema removes steps + questions + groups + sessions
  return supabase.from('labs').delete().eq('id', labId)
}
```

- [ ] **Step 2: Verify file parses and build still passes**

Run: `cd web && npm run lint && npm run build`
Expected: lint clean, build succeeds.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labApi.js
git commit -m "feat(web/labApi): add labs CRUD wrappers"
```

---

## Task 3: Extend `labApi.js` — steps + questions

**Files:**
- Modify: `web/src/services/labApi.js`

- [ ] **Step 1: Append steps + questions sections**

Append to `web/src/services/labApi.js`:

```javascript
// ─── Steps ───────────────────────────────────────────────────────────────────

export async function listSteps(labId) {
  return supabase
    .from('lab_steps')
    .select('*')
    .eq('lab_id', labId)
    .order('step_order', { ascending: true })
}

export async function createStep(payload) {
  // payload: { lab_id, step_order, title, instruction, evidence_type, required_count, hint }
  return supabase.from('lab_steps').insert(payload).select().single()
}

export async function updateStep(stepId, patch) {
  return supabase.from('lab_steps').update(patch).eq('id', stepId).select().single()
}

export async function deleteStep(stepId) {
  return supabase.from('lab_steps').delete().eq('id', stepId)
}

export async function reorderSteps(labId, orderedIds) {
  // orderedIds: array of step UUIDs in their new order. Two-phase to avoid
  // tripping the UNIQUE(lab_id, step_order) constraint:
  //  1) bump every row's step_order to a temp value above all current values
  //  2) write the final 1..N order
  const tempBase = 1000
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_steps')
      .update({ step_order: tempBase + i })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
    if (error) return { error }
  }
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_steps')
      .update({ step_order: i + 1 })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
    if (error) return { error }
  }
  return { error: null }
}

// ─── Questions ───────────────────────────────────────────────────────────────

export async function listQuestions(labId, phase /* 'pre_lab' | 'post_lab' */) {
  return supabase
    .from('lab_questions')
    .select('*')
    .eq('lab_id', labId)
    .eq('phase', phase)
    .order('question_order', { ascending: true })
}

export async function createQuestion(payload) {
  // payload: { lab_id, phase, question_order, question_type, question_text,
  //            options, correct_answer, points, hint }
  return supabase.from('lab_questions').insert(payload).select().single()
}

export async function updateQuestion(questionId, patch) {
  return supabase
    .from('lab_questions')
    .update(patch)
    .eq('id', questionId)
    .select()
    .single()
}

export async function deleteQuestion(questionId) {
  return supabase.from('lab_questions').delete().eq('id', questionId)
}

export async function reorderQuestions(labId, phase, orderedIds) {
  const tempBase = 1000
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_questions')
      .update({ question_order: tempBase + i })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
      .eq('phase', phase)
    if (error) return { error }
  }
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_questions')
      .update({ question_order: i + 1 })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
      .eq('phase', phase)
    if (error) return { error }
  }
  return { error: null }
}
```

- [ ] **Step 2: Verify build/lint**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labApi.js
git commit -m "feat(web/labApi): add steps and questions CRUD with safe reorder"
```

---

## Task 4: Extend `labApi.js` — groups + members + MSSV search

**Files:**
- Modify: `web/src/services/labApi.js`

- [ ] **Step 1: Append groups section**

Append to `web/src/services/labApi.js`:

```javascript
// ─── Groups ──────────────────────────────────────────────────────────────────

export async function listGroups(labId /* optional filter */) {
  let q = supabase
    .from('lab_groups')
    .select('id, lab_id, name, semester, created_at, lab:labs(code,title)')
    .order('created_at', { ascending: false })
  if (labId) q = q.eq('lab_id', labId)
  return q
}

export async function listGroupMembers(groupId) {
  // Note: lab_group_members.user_id → auth.users; profile fields live in
  // public.profiles. We join profiles for MSSV + full_name + username.
  return supabase
    .from('lab_group_members')
    .select(
      'group_id, user_id, role, profile:profiles!user_id(username, full_name, mssv, email)'
    )
    .eq('group_id', groupId)
}

export async function createGroup(payload) {
  // payload: { lab_id, name, semester }
  return supabase.from('lab_groups').insert(payload).select().single()
}

export async function updateGroup(groupId, patch) {
  return supabase.from('lab_groups').update(patch).eq('id', groupId).select().single()
}

export async function deleteGroup(groupId) {
  return supabase.from('lab_groups').delete().eq('id', groupId)
}

export async function addGroupMember(groupId, userId, role /* 'leader'|'member' */) {
  return supabase
    .from('lab_group_members')
    .insert({ group_id: groupId, user_id: userId, role })
}

export async function removeGroupMember(groupId, userId) {
  return supabase
    .from('lab_group_members')
    .delete()
    .eq('group_id', groupId)
    .eq('user_id', userId)
}

export async function setGroupLeader(groupId, newLeaderUserId) {
  // Two-step because of partial unique index "one leader per group":
  //  1) demote every leader in this group to member
  //  2) promote the chosen user
  const { error: e1 } = await supabase
    .from('lab_group_members')
    .update({ role: 'member' })
    .eq('group_id', groupId)
    .eq('role', 'leader')
  if (e1) return { error: e1 }
  return supabase
    .from('lab_group_members')
    .update({ role: 'leader' })
    .eq('group_id', groupId)
    .eq('user_id', newLeaderUserId)
}

// MSSV / name autocomplete — staff-only (admin_get_users RPC already exists
// and returns mssv/full_name/username/email).
export async function searchProfilesByMssvOrName(query, limit = 20) {
  // Reuse existing admin_get_users RPC then filter client-side. The dataset
  // is small (course-scale), so client filter is fine and avoids needing a
  // new server-side RPC.
  const { data, error } = await supabase.rpc('admin_get_users')
  if (error) return { data: [], error }
  const q = (query || '').trim().toLowerCase()
  if (!q) return { data: data.slice(0, limit), error: null }
  const filtered = data.filter(
    (u) =>
      (u.mssv || '').toLowerCase().includes(q) ||
      (u.full_name || '').toLowerCase().includes(q) ||
      (u.username || '').toLowerCase().includes(q) ||
      (u.email || '').toLowerCase().includes(q)
  )
  return { data: filtered.slice(0, limit), error: null }
}

// Bulk-import helper used by GroupBulkImport. Accepts a parsed
// {groupName, semester, leaderMssv, memberMssvs[]} array; returns per-row
// {ok, error, groupId} so the UI can show a result table.
export async function bulkImportGroups(labId, rows) {
  const { data: profiles, error: pErr } = await supabase.rpc('admin_get_users')
  if (pErr) return { results: [], error: pErr }
  const byMssv = new Map(
    profiles.filter((p) => p.mssv).map((p) => [p.mssv.trim(), p])
  )

  const results = []
  for (const row of rows) {
    try {
      const leader = byMssv.get(row.leaderMssv?.trim())
      if (!leader) throw new Error(`Leader MSSV not found: ${row.leaderMssv}`)
      const members = (row.memberMssvs || []).map((m) => {
        const p = byMssv.get(m.trim())
        if (!p) throw new Error(`Member MSSV not found: ${m}`)
        return p
      })

      const { data: g, error: gErr } = await supabase
        .from('lab_groups')
        .insert({ lab_id: labId, name: row.groupName, semester: row.semester })
        .select()
        .single()
      if (gErr) throw gErr

      const { error: lErr } = await supabase
        .from('lab_group_members')
        .insert({ group_id: g.id, user_id: leader.id, role: 'leader' })
      if (lErr) throw lErr

      for (const m of members) {
        if (m.id === leader.id) continue
        const { error: mErr } = await supabase
          .from('lab_group_members')
          .insert({ group_id: g.id, user_id: m.id, role: 'member' })
        if (mErr) throw mErr
      }
      results.push({ ok: true, groupName: row.groupName, groupId: g.id })
    } catch (e) {
      results.push({ ok: false, groupName: row.groupName, error: e.message })
    }
  }
  return { results, error: null }
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labApi.js
git commit -m "feat(web/labApi): add groups, members, MSSV search, bulk import"
```

---

## Task 5: Extend `labApi.js` — sessions + submissions + reports

**Files:**
- Modify: `web/src/services/labApi.js`

- [ ] **Step 1: Append remaining sections**

Append to `web/src/services/labApi.js`:

```javascript
// ─── Sessions ────────────────────────────────────────────────────────────────

export async function listSessions({ labId, status, fromDate, toDate } = {}) {
  let q = supabase
    .from('lab_sessions')
    .select(
      'id, session_code, status, started_at, ended_at, expires_at, current_step_id, ' +
        'lab:labs(id,code,title), group:lab_groups(id,name,semester), ' +
        'started_by_profile:profiles!started_by(username,full_name,mssv)'
    )
    .order('started_at', { ascending: false })
  if (labId) q = q.eq('lab_id', labId)
  if (status) q = q.eq('status', status)
  if (fromDate) q = q.gte('started_at', fromDate)
  if (toDate) q = q.lte('started_at', toDate)
  return q
}

export async function getSession(sessionId) {
  return supabase
    .from('lab_sessions')
    .select(
      'id, session_code, status, started_at, ended_at, expires_at, current_step_id, ' +
        'lab:labs(id,code,title), group:lab_groups(id,name,semester)'
    )
    .eq('id', sessionId)
    .single()
}

export async function listSessionEvidence(sessionId) {
  return supabase
    .from('lab_evidence')
    .select(
      'id, step_id, submitted_by, evidence_type, payload, client_timestamp_ms, created_at, ' +
        'submitter:profiles!submitted_by(username,full_name,mssv)'
    )
    .eq('session_id', sessionId)
    .order('created_at', { ascending: true })
}

export async function forceEndSession(sessionId) {
  return supabase
    .from('lab_sessions')
    .update({ status: 'CANCELLED', ended_at: new Date().toISOString() })
    .eq('id', sessionId)
}

export async function resetSessionStep(sessionId) {
  return supabase
    .from('lab_sessions')
    .update({ current_step_id: null, step_started_at: null })
    .eq('id', sessionId)
}

// ─── Submissions ─────────────────────────────────────────────────────────────

export async function listPostSubmissions({ labId, sessionId } = {}) {
  let q = supabase
    .from('lab_post_submissions')
    .select(
      'id, user_id, session_id, is_draft, submitted_at, updated_at, teacher_comment, ' +
        'session:lab_sessions(id, session_code, lab_id, group_id, ' +
        '  lab:labs(code,title), group:lab_groups(name,semester)), ' +
        'profile:profiles!user_id(username,full_name,mssv,email)'
    )
    .order('updated_at', { ascending: false })
  if (sessionId) q = q.eq('session_id', sessionId)
  if (labId) {
    // Filter by lab via the joined session — needs server-side filter; cheapest
    // approach is two-step: fetch sessions for the lab then filter client-side.
    const { data: sessions, error: se } = await supabase
      .from('lab_sessions')
      .select('id')
      .eq('lab_id', labId)
    if (se) return { data: null, error: se }
    const ids = (sessions || []).map((s) => s.id)
    if (ids.length === 0) return { data: [], error: null }
    q = q.in('session_id', ids)
  }
  return q
}

export async function getPostSubmission(userId, sessionId) {
  return supabase
    .from('lab_post_submissions')
    .select('*')
    .eq('user_id', userId)
    .eq('session_id', sessionId)
    .single()
}

export async function setTeacherComment(submissionId, comment) {
  return supabase
    .from('lab_post_submissions')
    .update({ teacher_comment: comment })
    .eq('id', submissionId)
}

export async function listPreQuizSubmissions({ labId, userId } = {}) {
  let q = supabase
    .from('lab_pre_quiz_submissions')
    .select(
      'id, user_id, lab_id, score_percent, passed, attempt_number, submitted_at, ' +
        'profile:profiles!user_id(username,full_name,mssv)'
    )
    .order('submitted_at', { ascending: false })
  if (labId) q = q.eq('lab_id', labId)
  if (userId) q = q.eq('user_id', userId)
  return q
}

// ─── Reports / PDFs ──────────────────────────────────────────────────────────

export async function listReports({ labId, sessionId } = {}) {
  let q = supabase
    .from('lab_reports')
    .select(
      'id, user_id, session_id, pdf_storage_path, content_hash, file_size_bytes, generated_at, ' +
        'profile:profiles!user_id(username,full_name,mssv), ' +
        'session:lab_sessions(id, session_code, lab_id, lab:labs(code,title))'
    )
    .order('generated_at', { ascending: false })
  if (sessionId) q = q.eq('session_id', sessionId)
  if (labId) {
    const { data: sessions, error: se } = await supabase
      .from('lab_sessions')
      .select('id')
      .eq('lab_id', labId)
    if (se) return { data: null, error: se }
    const ids = (sessions || []).map((s) => s.id)
    if (ids.length === 0) return { data: [], error: null }
    q = q.in('session_id', ids)
  }
  return q
}

export async function getReportSignedUrl(storagePath, expiresIn = 60) {
  return supabase.storage
    .from('lab-reports')
    .createSignedUrl(storagePath, expiresIn)
}

export async function downloadReportBlob(storagePath) {
  return supabase.storage.from('lab-reports').download(storagePath)
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labApi.js
git commit -m "feat(web/labApi): add sessions, submissions, reports queries"
```

---

## Task 6: `MarkdownEditor` reusable component

**Files:**
- Create: `web/src/components/admin/MarkdownEditor.jsx`

- [ ] **Step 1: Create the wrapper**

```jsx
import MDEditor from '@uiw/react-md-editor'

/**
 * Thin wrapper around @uiw/react-md-editor that:
 *  - forces light color mode (admin UI is light-only — see AppLayout)
 *  - exposes a simple value/onChange API consistent with antd Form fields
 *  - hides the preview pane by default (toggleable via `showPreview`)
 */
export default function MarkdownEditor({
  value,
  onChange,
  height = 240,
  showPreview = false,
  placeholder,
}) {
  return (
    <div data-color-mode="light">
      <MDEditor
        value={value || ''}
        onChange={(v) => onChange?.(v ?? '')}
        height={height}
        preview={showPreview ? 'live' : 'edit'}
        textareaProps={{ placeholder }}
      />
    </div>
  )
}
```

- [ ] **Step 2: Verify build picks up the new dep**

Run: `cd web && npm run build`
Expected: build succeeds. If you see "Failed to resolve import @uiw/react-md-editor" then Task 1 was skipped — install the dep.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/MarkdownEditor.jsx
git commit -m "feat(web/admin): add MarkdownEditor wrapper component"
```

---

## Task 7: `LabForm` modal

**Files:**
- Create: `web/src/components/admin/LabForm.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect } from 'react'
import { Modal, Form, Input, InputNumber, Switch, message } from 'antd'
import MarkdownEditor from './MarkdownEditor'
import { createLab, updateLab } from '../../services/labApi'

/**
 * Modal form for creating/editing a Lab.
 * Props:
 *   open      — boolean
 *   lab       — existing lab object (for edit) or null (for create)
 *   onClose() — close without saving
 *   onSaved(lab) — called after successful create/update with the saved row
 */
export default function LabForm({ open, lab, onClose, onSaved }) {
  const [form] = Form.useForm()
  const isEdit = !!lab

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (lab) {
        form.setFieldsValue({
          code: lab.code,
          title: lab.title,
          description: lab.description || '',
          order_index: lab.order_index ?? 0,
          pre_quiz_pass_threshold: lab.pre_quiz_pass_threshold ?? 70,
          is_published: !!lab.is_published,
        })
      } else {
        form.setFieldsValue({
          order_index: 0,
          pre_quiz_pass_threshold: 70,
          is_published: false,
        })
      }
    }
  }, [open, lab, form])

  async function handleOk() {
    const values = await form.validateFields()
    const { data, error } = isEdit
      ? await updateLab(lab.id, values)
      : await createLab(values)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật lab' : 'Đã tạo lab')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `Chỉnh sửa lab: ${lab.code}` : 'Tạo lab mới'}
      onCancel={onClose}
      onOk={handleOk}
      okText="Lưu"
      cancelText="Hủy"
      width={760}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="code"
          label="Mã lab"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input placeholder="LAB-01-CAN-OBD2" />
        </Form.Item>
        <Form.Item
          name="title"
          label="Tiêu đề"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input placeholder="CAN BUS & OBD2 Fundamentals" />
        </Form.Item>
        <Form.Item name="description" label="Mô tả (markdown)">
          <MarkdownEditor height={200} />
        </Form.Item>
        <Form.Item name="order_index" label="Thứ tự hiển thị">
          <InputNumber min={0} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item
          name="pre_quiz_pass_threshold"
          label="Ngưỡng đậu pre-quiz (%)"
          rules={[{ required: true }]}
        >
          <InputNumber min={0} max={100} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="is_published" label="Đã publish?" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/LabForm.jsx
git commit -m "feat(web/admin): add LabForm modal"
```

---

## Task 8: `StepForm` modal

**Files:**
- Create: `web/src/components/admin/StepForm.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect } from 'react'
import { Modal, Form, Input, InputNumber, Select, message } from 'antd'
import MarkdownEditor from './MarkdownEditor'
import { createStep, updateStep } from '../../services/labApi'

const EVIDENCE_TYPES = [
  { value: 'raw_frames', label: 'raw_frames (CAN frames từ app)' },
  { value: 'active_test', label: 'active_test (lệnh actuator)' },
  { value: 'screenshot', label: 'screenshot (ảnh upload từ web)' },
  { value: 'none', label: 'none (mốc xác nhận, không cần evidence)' },
]

export default function StepForm({ open, labId, step, nextOrder, onClose, onSaved }) {
  const [form] = Form.useForm()
  const isEdit = !!step

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (step) {
        form.setFieldsValue({
          step_order: step.step_order,
          title: step.title,
          instruction: step.instruction,
          evidence_type: step.evidence_type,
          required_count: step.required_count ?? 0,
          hint: step.hint || '',
        })
      } else {
        form.setFieldsValue({
          step_order: nextOrder,
          evidence_type: 'raw_frames',
          required_count: 0,
        })
      }
    }
  }, [open, step, nextOrder, form])

  async function handleOk() {
    const values = await form.validateFields()
    const payload = { ...values, lab_id: labId }
    const { data, error } = isEdit
      ? await updateStep(step.id, values)
      : await createStep(payload)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật step' : 'Đã tạo step')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `Chỉnh sửa step #${step.step_order}` : 'Thêm step mới'}
      onCancel={onClose}
      onOk={handleOk}
      okText="Lưu"
      cancelText="Hủy"
      width={760}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="step_order"
          label="Thứ tự (step_order)"
          rules={[{ required: true }]}
          tooltip="Có thể tinh chỉnh sau bằng kéo-thả ở danh sách step"
        >
          <InputNumber min={1} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item
          name="title"
          label="Tiêu đề step"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="instruction"
          label="Hướng dẫn (markdown)"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <MarkdownEditor height={220} />
        </Form.Item>
        <Form.Item
          name="evidence_type"
          label="Loại bằng chứng"
          rules={[{ required: true }]}
        >
          <Select options={EVIDENCE_TYPES} />
        </Form.Item>
        <Form.Item
          name="required_count"
          label="Số lượng tối thiểu (required_count)"
          tooltip="Đặt 0 cho evidence_type = none"
        >
          <InputNumber min={0} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="hint" label="Gợi ý (tùy chọn)">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/StepForm.jsx
git commit -m "feat(web/admin): add StepForm modal"
```

---

## Task 9: `StepList` with DnD reorder

**Files:**
- Create: `web/src/components/admin/StepList.jsx`

- [ ] **Step 1: Implement using `@dnd-kit/sortable`**

```jsx
import { useState } from 'react'
import { Button, Card, List, Space, Tag, Popconfirm, Typography, message } from 'antd'
import {
  DragOutlined,
  EditOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { reorderSteps, deleteStep } from '../../services/labApi'
import StepForm from './StepForm'

const { Text } = Typography

const TYPE_COLOR = {
  raw_frames: 'geekblue',
  active_test: 'purple',
  screenshot: 'cyan',
  none: 'default',
}

function SortableRow({ step, onEdit, onDelete }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: step.id })
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
  }
  return (
    <div
      ref={setNodeRef}
      style={{
        ...style,
        display: 'flex',
        alignItems: 'center',
        padding: '8px 12px',
        borderBottom: '1px solid #f0f0f0',
        background: '#fff',
        gap: 12,
      }}
    >
      <span
        {...attributes}
        {...listeners}
        style={{ cursor: 'grab', color: '#9ca3af' }}
        aria-label="Kéo để sắp xếp"
      >
        <DragOutlined />
      </span>
      <Tag>#{step.step_order}</Tag>
      <Tag color={TYPE_COLOR[step.evidence_type] || 'default'}>
        {step.evidence_type}
      </Tag>
      <div style={{ flex: 1 }}>
        <Text strong>{step.title}</Text>
        <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
          required = {step.required_count ?? 0}
        </Text>
      </div>
      <Space>
        <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(step)}>
          Sửa
        </Button>
        <Popconfirm
          title="Xóa step này?"
          okText="Xóa"
          cancelText="Hủy"
          onConfirm={() => onDelete(step)}
        >
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      </Space>
    </div>
  )
}

export default function StepList({ labId, steps, onChanged }) {
  const [formOpen, setFormOpen] = useState(false)
  const [editingStep, setEditingStep] = useState(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }))

  const sorted = [...(steps || [])].sort((a, b) => a.step_order - b.step_order)

  async function handleDragEnd(event) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = sorted.findIndex((s) => s.id === active.id)
    const newIndex = sorted.findIndex((s) => s.id === over.id)
    const newOrder = arrayMove(sorted, oldIndex, newIndex)
    const { error } = await reorderSteps(
      labId,
      newOrder.map((s) => s.id)
    )
    if (error) {
      message.error(error.message)
      return
    }
    onChanged?.()
  }

  async function handleDelete(step) {
    const { error } = await deleteStep(step.id)
    if (error) message.error(error.message)
    else {
      message.success('Đã xóa')
      onChanged?.()
    }
  }

  const nextOrder =
    sorted.length === 0 ? 1 : Math.max(...sorted.map((s) => s.step_order)) + 1

  return (
    <Card
      size="small"
      title="Steps"
      styles={{ body: { padding: 0 } }}
      extra={
        <Button
          size="small"
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingStep(null)
            setFormOpen(true)
          }}
        >
          Thêm step
        </Button>
      }
    >
      {sorted.length === 0 ? (
        <List locale={{ emptyText: 'Chưa có step' }} dataSource={[]} renderItem={() => null} />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={sorted.map((s) => s.id)}
            strategy={verticalListSortingStrategy}
          >
            {sorted.map((s) => (
              <SortableRow
                key={s.id}
                step={s}
                onEdit={(st) => {
                  setEditingStep(st)
                  setFormOpen(true)
                }}
                onDelete={handleDelete}
              />
            ))}
          </SortableContext>
        </DndContext>
      )}

      <StepForm
        open={formOpen}
        labId={labId}
        step={editingStep}
        nextOrder={nextOrder}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false)
          onChanged?.()
        }}
      />
    </Card>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/StepList.jsx
git commit -m "feat(web/admin): add StepList with dnd-kit reorder"
```

---

## Task 10: `QuestionForm` modal

**Files:**
- Create: `web/src/components/admin/QuestionForm.jsx`

- [ ] **Step 1: Implement (handles MC + free_text + image_upload)**

```jsx
import { useEffect, useState } from 'react'
import {
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Button,
  Typography,
  message,
} from 'antd'
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons'
import { createQuestion, updateQuestion } from '../../services/labApi'

const { Text } = Typography

const QUESTION_TYPES = [
  { value: 'multiple_choice', label: 'Multiple Choice' },
  { value: 'free_text', label: 'Free Text' },
  { value: 'image_upload', label: 'Image Upload' },
]

export default function QuestionForm({
  open,
  labId,
  phase,
  question,
  nextOrder,
  onClose,
  onSaved,
}) {
  const [form] = Form.useForm()
  const isEdit = !!question
  const [qType, setQType] = useState('multiple_choice')

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (question) {
        const optsObj = question.options || {}
        const opts = Object.entries(optsObj).map(([k, v]) => ({ key: k, text: v }))
        form.setFieldsValue({
          question_order: question.question_order,
          question_type: question.question_type,
          question_text: question.question_text,
          options: opts.length ? opts : [{ key: 'A', text: '' }],
          correct_answer: question.correct_answer || '',
          points: question.points ?? 1,
          hint: question.hint || '',
        })
        setQType(question.question_type)
      } else {
        form.setFieldsValue({
          question_order: nextOrder,
          question_type: 'multiple_choice',
          options: [
            { key: 'A', text: '' },
            { key: 'B', text: '' },
          ],
          points: 1,
        })
        setQType('multiple_choice')
      }
    }
  }, [open, question, nextOrder, form])

  async function handleOk() {
    const values = await form.validateFields()
    const payload = {
      lab_id: labId,
      phase,
      question_order: values.question_order,
      question_type: values.question_type,
      question_text: values.question_text,
      points: values.points,
      hint: values.hint || null,
      options: null,
      correct_answer: null,
    }
    if (values.question_type === 'multiple_choice') {
      const optsObj = {}
      for (const o of values.options || []) {
        if (o.key && o.text) optsObj[o.key] = o.text
      }
      payload.options = optsObj
      payload.correct_answer = values.correct_answer || null
    }
    const { data, error } = isEdit
      ? await updateQuestion(question.id, payload)
      : await createQuestion(payload)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật câu hỏi' : 'Đã tạo câu hỏi')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={
        isEdit
          ? `Chỉnh sửa câu hỏi #${question.question_order} (${phase})`
          : `Thêm câu hỏi (${phase})`
      }
      onCancel={onClose}
      onOk={handleOk}
      okText="Lưu"
      cancelText="Hủy"
      width={720}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Space>
          <Form.Item name="question_order" label="Thứ tự" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="question_type" label="Loại câu hỏi" rules={[{ required: true }]}>
            <Select
              style={{ width: 220 }}
              options={QUESTION_TYPES}
              onChange={(v) => setQType(v)}
            />
          </Form.Item>
          <Form.Item name="points" label="Điểm" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: 80 }} />
          </Form.Item>
        </Space>

        <Form.Item
          name="question_text"
          label="Nội dung câu hỏi"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input.TextArea rows={3} />
        </Form.Item>

        {qType === 'multiple_choice' && (
          <>
            <Text strong>Các phương án</Text>
            <Form.List name="options">
              {(fields, { add, remove }) => (
                <div style={{ marginTop: 8 }}>
                  {fields.map((field) => (
                    <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }}>
                      <Form.Item
                        name={[field.name, 'key']}
                        rules={[{ required: true, message: 'Key' }]}
                        style={{ width: 80, marginBottom: 0 }}
                      >
                        <Input placeholder="A" maxLength={4} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'text']}
                        rules={[{ required: true, message: 'Nội dung' }]}
                        style={{ width: 460, marginBottom: 0 }}
                      >
                        <Input placeholder="Nội dung lựa chọn" />
                      </Form.Item>
                      <Button
                        type="text"
                        danger
                        icon={<MinusCircleOutlined />}
                        onClick={() => remove(field.name)}
                      />
                    </Space>
                  ))}
                  <Button
                    block
                    icon={<PlusOutlined />}
                    onClick={() => add({ key: '', text: '' })}
                  >
                    Thêm phương án
                  </Button>
                </div>
              )}
            </Form.List>
            <Form.Item
              name="correct_answer"
              label="Đáp án đúng (key)"
              rules={[{ required: true, message: 'Bắt buộc với MC' }]}
              style={{ marginTop: 12 }}
            >
              <Input placeholder="A" style={{ width: 100 }} />
            </Form.Item>
          </>
        )}

        <Form.Item name="hint" label="Gợi ý (tùy chọn)">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/QuestionForm.jsx
git commit -m "feat(web/admin): add QuestionForm modal"
```

---

## Task 11: `QuestionList` with DnD reorder

**Files:**
- Create: `web/src/components/admin/QuestionList.jsx`

- [ ] **Step 1: Implement (mirrors StepList)**

```jsx
import { useState } from 'react'
import { Button, Card, Space, Tag, Popconfirm, Typography, message } from 'antd'
import {
  DragOutlined,
  EditOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { reorderQuestions, deleteQuestion } from '../../services/labApi'
import QuestionForm from './QuestionForm'

const { Text } = Typography

const TYPE_COLOR = {
  multiple_choice: 'blue',
  free_text: 'green',
  image_upload: 'orange',
}

function SortableRow({ q, onEdit, onDelete }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: q.id })
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
  }
  return (
    <div
      ref={setNodeRef}
      style={{
        ...style,
        display: 'flex',
        alignItems: 'center',
        padding: '8px 12px',
        borderBottom: '1px solid #f0f0f0',
        background: '#fff',
        gap: 12,
      }}
    >
      <span
        {...attributes}
        {...listeners}
        style={{ cursor: 'grab', color: '#9ca3af' }}
        aria-label="Kéo để sắp xếp"
      >
        <DragOutlined />
      </span>
      <Tag>#{q.question_order}</Tag>
      <Tag color={TYPE_COLOR[q.question_type] || 'default'}>{q.question_type}</Tag>
      <div style={{ flex: 1 }}>
        <Text>{q.question_text}</Text>
        <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
          {q.points} điểm
        </Text>
      </div>
      <Space>
        <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(q)}>
          Sửa
        </Button>
        <Popconfirm
          title="Xóa câu hỏi này?"
          okText="Xóa"
          cancelText="Hủy"
          onConfirm={() => onDelete(q)}
        >
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      </Space>
    </div>
  )
}

export default function QuestionList({ labId, phase, questions, onChanged }) {
  const [formOpen, setFormOpen] = useState(false)
  const [editingQ, setEditingQ] = useState(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }))

  const sorted = [...(questions || [])].sort(
    (a, b) => a.question_order - b.question_order
  )

  async function handleDragEnd(event) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = sorted.findIndex((q) => q.id === active.id)
    const newIndex = sorted.findIndex((q) => q.id === over.id)
    const newOrder = arrayMove(sorted, oldIndex, newIndex)
    const { error } = await reorderQuestions(
      labId,
      phase,
      newOrder.map((q) => q.id)
    )
    if (error) {
      message.error(error.message)
      return
    }
    onChanged?.()
  }

  async function handleDelete(q) {
    const { error } = await deleteQuestion(q.id)
    if (error) message.error(error.message)
    else {
      message.success('Đã xóa')
      onChanged?.()
    }
  }

  const nextOrder =
    sorted.length === 0
      ? 1
      : Math.max(...sorted.map((q) => q.question_order)) + 1

  return (
    <Card
      size="small"
      title={`Questions — ${phase}`}
      styles={{ body: { padding: 0 } }}
      extra={
        <Button
          size="small"
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingQ(null)
            setFormOpen(true)
          }}
        >
          Thêm câu hỏi
        </Button>
      }
    >
      {sorted.length === 0 ? (
        <div style={{ padding: 16, color: '#9ca3af' }}>Chưa có câu hỏi</div>
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={sorted.map((q) => q.id)}
            strategy={verticalListSortingStrategy}
          >
            {sorted.map((q) => (
              <SortableRow
                key={q.id}
                q={q}
                onEdit={(qq) => {
                  setEditingQ(qq)
                  setFormOpen(true)
                }}
                onDelete={handleDelete}
              />
            ))}
          </SortableContext>
        </DndContext>
      )}

      <QuestionForm
        open={formOpen}
        labId={labId}
        phase={phase}
        question={editingQ}
        nextOrder={nextOrder}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false)
          onChanged?.()
        }}
      />
    </Card>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/QuestionList.jsx
git commit -m "feat(web/admin): add QuestionList with dnd-kit reorder"
```

---

## Task 12: `LabsAdminTab` — top-level tab

**Files:**
- Create: `web/src/pages/admin/LabsAdminTab.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect, useState } from 'react'
import {
  Card,
  Button,
  Table,
  Tag,
  Space,
  Typography,
  Drawer,
  Tabs,
  Popconfirm,
  message,
  Input,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SearchOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import {
  listLabs,
  deleteLab,
  listSteps,
  listQuestions,
} from '../../services/labApi'
import LabForm from '../../components/admin/LabForm'
import StepList from '../../components/admin/StepList'
import QuestionList from '../../components/admin/QuestionList'

const { Text, Title } = Typography

export default function LabsAdminTab() {
  const [labs, setLabs] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [labFormOpen, setLabFormOpen] = useState(false)
  const [editingLab, setEditingLab] = useState(null)
  const [drawerLab, setDrawerLab] = useState(null) // lab being managed in drawer
  const [steps, setSteps] = useState([])
  const [preQs, setPreQs] = useState([])
  const [postQs, setPostQs] = useState([])

  async function reload() {
    setLoading(true)
    const { data, error } = await listLabs()
    setLoading(false)
    if (error) message.error(error.message)
    else setLabs(data || [])
  }

  useEffect(() => {
    reload()
  }, [])

  async function reloadDrawer(labId) {
    const [{ data: s }, { data: pre }, { data: post }] = await Promise.all([
      listSteps(labId),
      listQuestions(labId, 'pre_lab'),
      listQuestions(labId, 'post_lab'),
    ])
    setSteps(s || [])
    setPreQs(pre || [])
    setPostQs(post || [])
  }

  async function openDrawer(lab) {
    setDrawerLab(lab)
    await reloadDrawer(lab.id)
  }

  async function handleDelete(lab) {
    const { error } = await deleteLab(lab.id)
    if (error) message.error(error.message)
    else {
      message.success('Đã xóa lab')
      reload()
    }
  }

  const filtered = labs.filter((l) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      l.code.toLowerCase().includes(q) || l.title.toLowerCase().includes(q)
    )
  })

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      render: (v) => <Text code>{v}</Text>,
    },
    { title: 'Tiêu đề', dataIndex: 'title' },
    {
      title: 'Order',
      dataIndex: 'order_index',
      width: 80,
    },
    {
      title: 'Pass %',
      dataIndex: 'pre_quiz_pass_threshold',
      width: 90,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'is_published',
      width: 110,
      render: (v) =>
        v ? <Tag color="success">Published</Tag> : <Tag>Draft</Tag>,
    },
    {
      title: 'Hành động',
      key: 'actions',
      align: 'right',
      render: (_, lab) => (
        <Space>
          <Button size="small" onClick={() => openDrawer(lab)}>
            Quản lý nội dung
          </Button>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingLab(lab)
              setLabFormOpen(true)
            }}
          >
            Sửa
          </Button>
          <Popconfirm
            title="Xóa lab và toàn bộ steps/questions/groups/sessions của nó?"
            okText="Xóa"
            cancelText="Hủy"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(lab)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
        }}
      >
        <Input
          prefix={<SearchOutlined />}
          placeholder="Tìm theo code hoặc tiêu đề…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 280 }}
          allowClear
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          Làm mới
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          style={{ marginLeft: 'auto' }}
          onClick={() => {
            setEditingLab(null)
            setLabFormOpen(true)
          }}
        >
          Tạo lab
        </Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={filtered}
        pagination={{ pageSize: 20 }}
      />

      <LabForm
        open={labFormOpen}
        lab={editingLab}
        onClose={() => setLabFormOpen(false)}
        onSaved={() => {
          setLabFormOpen(false)
          reload()
        }}
      />

      <Drawer
        open={!!drawerLab}
        onClose={() => setDrawerLab(null)}
        title={
          drawerLab
            ? `Quản lý nội dung: ${drawerLab.code} — ${drawerLab.title}`
            : ''
        }
        width={960}
        destroyOnClose
      >
        {drawerLab && (
          <Tabs
            items={[
              {
                key: 'steps',
                label: `Steps (${steps.length})`,
                children: (
                  <StepList
                    labId={drawerLab.id}
                    steps={steps}
                    onChanged={() => reloadDrawer(drawerLab.id)}
                  />
                ),
              },
              {
                key: 'pre',
                label: `Pre-lab (${preQs.length})`,
                children: (
                  <QuestionList
                    labId={drawerLab.id}
                    phase="pre_lab"
                    questions={preQs}
                    onChanged={() => reloadDrawer(drawerLab.id)}
                  />
                ),
              },
              {
                key: 'post',
                label: `Post-lab (${postQs.length})`,
                children: (
                  <QuestionList
                    labId={drawerLab.id}
                    phase="post_lab"
                    questions={postQs}
                    onChanged={() => reloadDrawer(drawerLab.id)}
                  />
                ),
              },
            ]}
          />
        )}
      </Drawer>
    </Card>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/admin/LabsAdminTab.jsx
git commit -m "feat(web/admin): add LabsAdminTab with nested step/question editing"
```

---

## Task 13: Wire `LabsAdminTab` into `AdminPage`

**Files:**
- Modify: `web/src/pages/AdminPage.jsx`

- [ ] **Step 1: Add import + tab item**

In `web/src/pages/AdminPage.jsx`:

After the existing `import { useAllExports } from '../hooks/useExports'` line, add:

```javascript
import LabsAdminTab from './admin/LabsAdminTab'
```

In the `items` array inside `export default function AdminPage()`, insert as the **first** entry (before `'users'`):

```javascript
{ key: 'labs', label: '🧪 Labs', children: <LabsAdminTab /> },
```

- [ ] **Step 2: Smoke test in dev**

Run:
```bash
cd web && npm run dev
```

In a browser, log in as a staff (admin/moderator) user, navigate to `/admin`, click the new "🧪 Labs" tab.

Expected:
- Lab list loads (empty if no labs yet — that's fine).
- "Tạo lab" opens modal; submit creates a row visible in the table.
- "Quản lý nội dung" opens drawer with Steps / Pre-lab / Post-lab tabs.
- Drag a step row to reorder; refresh → order persists.
- Delete a step → row disappears.

If any of these fail, fix the bug before proceeding.

- [ ] **Step 3: Verify build still passes**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 4: Commit**

```bash
git add web/src/pages/AdminPage.jsx
git commit -m "feat(web/admin): wire LabsAdminTab into AdminPage"
```

---

## Task 14: `GroupForm` with MSSV autocomplete

**Files:**
- Create: `web/src/components/admin/GroupForm.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect, useState, useRef } from 'react'
import {
  Modal,
  Form,
  Input,
  Select,
  Space,
  Button,
  Tag,
  List,
  Typography,
  Popconfirm,
  message,
} from 'antd'
import { CrownOutlined, DeleteOutlined } from '@ant-design/icons'
import {
  createGroup,
  updateGroup,
  listGroupMembers,
  addGroupMember,
  removeGroupMember,
  setGroupLeader,
  searchProfilesByMssvOrName,
} from '../../services/labApi'

const { Text } = Typography

export default function GroupForm({ open, labId, group, labs, onClose, onSaved }) {
  const [form] = Form.useForm()
  const isEdit = !!group

  // Member-management state — only meaningful in edit mode (need a group id).
  const [members, setMembers] = useState([])
  const [memberLoading, setMemberLoading] = useState(false)
  const [searchOpts, setSearchOpts] = useState([])
  const [searchVal, setSearchVal] = useState(null)
  const debounceRef = useRef(null)

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (group) {
        form.setFieldsValue({
          lab_id: group.lab_id,
          name: group.name,
          semester: group.semester || '',
        })
        loadMembers(group.id)
      } else {
        form.setFieldsValue({ lab_id: labId || undefined })
        setMembers([])
      }
    }
  }, [open, group, labId, form])

  async function loadMembers(gid) {
    setMemberLoading(true)
    const { data, error } = await listGroupMembers(gid)
    setMemberLoading(false)
    if (error) message.error(error.message)
    else setMembers(data || [])
  }

  function onSearch(value) {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      const { data } = await searchProfilesByMssvOrName(value, 20)
      setSearchOpts(
        (data || []).map((u) => ({
          value: u.id,
          label: `${u.mssv || '—'} · ${u.full_name || u.username || u.email}`,
          raw: u,
        }))
      )
    }, 200)
  }

  async function handleAddMember() {
    if (!searchVal || !group) return
    if (members.some((m) => m.user_id === searchVal)) {
      message.warning('Sinh viên này đã có trong nhóm')
      return
    }
    const role = members.length === 0 ? 'leader' : 'member'
    const { error } = await addGroupMember(group.id, searchVal, role)
    if (error) {
      message.error(error.message)
      return
    }
    message.success('Đã thêm thành viên')
    setSearchVal(null)
    loadMembers(group.id)
  }

  async function handleRemove(userId) {
    const { error } = await removeGroupMember(group.id, userId)
    if (error) message.error(error.message)
    else loadMembers(group.id)
  }

  async function handlePromote(userId) {
    const { error } = await setGroupLeader(group.id, userId)
    if (error) message.error(error.message)
    else {
      message.success('Đã chuyển leader')
      loadMembers(group.id)
    }
  }

  async function handleSaveMeta() {
    const values = await form.validateFields()
    const { data, error } = isEdit
      ? await updateGroup(group.id, values)
      : await createGroup(values)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật' : 'Đã tạo nhóm')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `Chỉnh sửa nhóm: ${group.name}` : 'Tạo nhóm mới'}
      onCancel={onClose}
      onOk={handleSaveMeta}
      okText="Lưu"
      cancelText="Đóng"
      width={780}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="lab_id"
          label="Lab"
          rules={[{ required: true, message: 'Chọn lab' }]}
        >
          <Select
            placeholder="Chọn lab"
            options={(labs || []).map((l) => ({
              value: l.id,
              label: `${l.code} — ${l.title}`,
            }))}
            disabled={isEdit}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên nhóm"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input placeholder="Nhóm 1 · Tổ A · Sáng thứ 2" />
        </Form.Item>
        <Form.Item name="semester" label="Học kỳ (tùy chọn)">
          <Input placeholder="HK2-2025-2026" />
        </Form.Item>
      </Form>

      {isEdit && (
        <>
          <Text strong>Thành viên ({members.length})</Text>
          <Space style={{ display: 'flex', marginTop: 8 }}>
            <Select
              showSearch
              placeholder="Tìm theo MSSV / tên / email…"
              filterOption={false}
              onSearch={onSearch}
              value={searchVal}
              onChange={(v) => setSearchVal(v)}
              options={searchOpts}
              style={{ width: 480 }}
              notFoundContent={null}
              allowClear
            />
            <Button type="primary" onClick={handleAddMember} disabled={!searchVal}>
              Thêm
            </Button>
          </Space>

          <List
            style={{ marginTop: 12 }}
            loading={memberLoading}
            dataSource={members}
            locale={{ emptyText: 'Chưa có thành viên' }}
            renderItem={(m) => (
              <List.Item
                actions={[
                  m.role !== 'leader' && (
                    <Button
                      key="leader"
                      size="small"
                      icon={<CrownOutlined />}
                      onClick={() => handlePromote(m.user_id)}
                    >
                      Đặt làm leader
                    </Button>
                  ),
                  <Popconfirm
                    key="del"
                    title="Xóa thành viên này khỏi nhóm?"
                    okText="Xóa"
                    cancelText="Hủy"
                    onConfirm={() => handleRemove(m.user_id)}
                  >
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>,
                ].filter(Boolean)}
              >
                <Space>
                  {m.role === 'leader' && <Tag color="gold">Leader</Tag>}
                  <Text code>{m.profile?.mssv || '—'}</Text>
                  <Text>{m.profile?.full_name || m.profile?.username || '—'}</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {m.profile?.email}
                  </Text>
                </Space>
              </List.Item>
            )}
          />
        </>
      )}
    </Modal>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/GroupForm.jsx
git commit -m "feat(web/admin): add GroupForm with MSSV autocomplete & leader management"
```

---

## Task 15: `GroupBulkImport` modal — CSV

**Files:**
- Create: `web/src/components/admin/GroupBulkImport.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useState } from 'react'
import {
  Modal,
  Upload,
  Button,
  Table,
  Tag,
  Typography,
  Alert,
  Select,
  Space,
  message,
} from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { bulkImportGroups } from '../../services/labApi'

const { Text, Paragraph } = Typography

/**
 * CSV format (one group per row):
 *   group_name,semester,leader_mssv,member_mssvs
 *   "Nhóm 1","HK2-2025-2026","2210001","2210002;2210003;2210004"
 *
 * - Header row required.
 * - member_mssvs uses ';' to separate multiple MSSVs (commas are reserved
 *   for CSV). The leader's MSSV does NOT need to appear in member_mssvs;
 *   the importer adds them automatically as 'leader'.
 */
function parseCsv(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length < 2) return { rows: [], error: 'CSV phải có ít nhất header + 1 dòng dữ liệu' }
  const header = splitCsvLine(lines[0]).map((h) => h.trim().toLowerCase())
  const need = ['group_name', 'semester', 'leader_mssv', 'member_mssvs']
  for (const k of need) {
    if (!header.includes(k)) return { rows: [], error: `Thiếu cột: ${k}` }
  }
  const idx = (k) => header.indexOf(k)
  const rows = []
  for (let i = 1; i < lines.length; i++) {
    const cols = splitCsvLine(lines[i])
    rows.push({
      groupName: cols[idx('group_name')]?.trim(),
      semester: cols[idx('semester')]?.trim(),
      leaderMssv: cols[idx('leader_mssv')]?.trim(),
      memberMssvs: (cols[idx('member_mssvs')] || '')
        .split(';')
        .map((s) => s.trim())
        .filter(Boolean),
    })
  }
  return { rows, error: null }
}

// Minimal CSV splitter that respects double-quoted fields.
function splitCsvLine(line) {
  const out = []
  let cur = ''
  let inQ = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (inQ) {
      if (c === '"' && line[i + 1] === '"') {
        cur += '"'
        i++
      } else if (c === '"') {
        inQ = false
      } else {
        cur += c
      }
    } else {
      if (c === ',') {
        out.push(cur)
        cur = ''
      } else if (c === '"') {
        inQ = true
      } else {
        cur += c
      }
    }
  }
  out.push(cur)
  return out
}

export default function GroupBulkImport({ open, labs, onClose, onImported }) {
  const [labId, setLabId] = useState(null)
  const [rows, setRows] = useState([])
  const [parseError, setParseError] = useState(null)
  const [results, setResults] = useState(null)
  const [importing, setImporting] = useState(false)

  function handleFile(file) {
    const reader = new FileReader()
    reader.onload = (e) => {
      const { rows: r, error } = parseCsv(e.target.result)
      if (error) {
        setParseError(error)
        setRows([])
      } else {
        setParseError(null)
        setRows(r)
      }
      setResults(null)
    }
    reader.readAsText(file, 'utf-8')
    return false // prevent antd from auto-uploading
  }

  async function handleImport() {
    if (!labId) {
      message.warning('Chọn lab trước')
      return
    }
    if (rows.length === 0) {
      message.warning('Chưa có dữ liệu')
      return
    }
    setImporting(true)
    const { results: r, error } = await bulkImportGroups(labId, rows)
    setImporting(false)
    if (error) {
      message.error(error.message)
      return
    }
    setResults(r)
    const okCount = r.filter((x) => x.ok).length
    message[okCount === r.length ? 'success' : 'warning'](
      `${okCount}/${r.length} nhóm import thành công`
    )
    onImported?.()
  }

  return (
    <Modal
      open={open}
      title="Bulk import nhóm từ CSV"
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        <Button
          key="import"
          type="primary"
          loading={importing}
          disabled={rows.length === 0 || !labId}
          onClick={handleImport}
        >
          Import {rows.length} nhóm
        </Button>,
      ]}
      width={820}
      destroyOnClose
    >
      <Paragraph type="secondary" style={{ fontSize: 12 }}>
        Định dạng CSV (UTF-8, có header):{' '}
        <Text code>group_name,semester,leader_mssv,member_mssvs</Text>. MSSV
        thành viên cách nhau bằng dấu chấm phẩy (<Text code>;</Text>). Leader
        sẽ được tự động thêm — không cần liệt kê lại trong{' '}
        <Text code>member_mssvs</Text>.
      </Paragraph>

      <Space style={{ display: 'flex', marginBottom: 12 }}>
        <Select
          placeholder="Chọn lab để import vào"
          style={{ width: 360 }}
          value={labId}
          onChange={setLabId}
          options={(labs || []).map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Upload accept=".csv,text/csv" beforeUpload={handleFile} showUploadList={false}>
          <Button icon={<UploadOutlined />}>Chọn file CSV</Button>
        </Upload>
      </Space>

      {parseError && <Alert type="error" message={parseError} style={{ marginBottom: 12 }} />}

      {rows.length > 0 && !results && (
        <Table
          size="small"
          rowKey={(_, i) => i}
          pagination={{ pageSize: 8 }}
          columns={[
            { title: 'Tên nhóm', dataIndex: 'groupName' },
            { title: 'Học kỳ', dataIndex: 'semester' },
            { title: 'Leader MSSV', dataIndex: 'leaderMssv', render: (v) => <Text code>{v}</Text> },
            {
              title: 'Member MSSVs',
              dataIndex: 'memberMssvs',
              render: (arr) => (arr || []).map((m) => <Tag key={m}>{m}</Tag>),
            },
          ]}
          dataSource={rows}
        />
      )}

      {results && (
        <Table
          size="small"
          rowKey={(_, i) => i}
          pagination={false}
          columns={[
            {
              title: '',
              dataIndex: 'ok',
              width: 60,
              render: (v) =>
                v ? <Tag color="success">OK</Tag> : <Tag color="error">FAIL</Tag>,
            },
            { title: 'Tên nhóm', dataIndex: 'groupName' },
            {
              title: 'Lỗi',
              dataIndex: 'error',
              render: (v) => v && <Text type="danger">{v}</Text>,
            },
          ]}
          dataSource={results}
        />
      )}
    </Modal>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/GroupBulkImport.jsx
git commit -m "feat(web/admin): add GroupBulkImport CSV modal"
```

---

## Task 16: `GroupsAdminTab`

**Files:**
- Create: `web/src/pages/admin/GroupsAdminTab.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect, useState } from 'react'
import {
  Card,
  Button,
  Table,
  Tag,
  Space,
  Typography,
  Popconfirm,
  message,
  Select,
  Input,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  ImportOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  listGroups,
  deleteGroup,
  listLabs,
  listGroupMembers,
} from '../../services/labApi'
import GroupForm from '../../components/admin/GroupForm'
import GroupBulkImport from '../../components/admin/GroupBulkImport'

const { Text } = Typography

export default function GroupsAdminTab() {
  const [labs, setLabs] = useState([])
  const [groups, setGroups] = useState([])
  const [memberCache, setMemberCache] = useState({}) // groupId -> members[]
  const [loading, setLoading] = useState(true)
  const [filterLab, setFilterLab] = useState(null)
  const [search, setSearch] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingGroup, setEditingGroup] = useState(null)
  const [bulkOpen, setBulkOpen] = useState(false)

  async function reload() {
    setLoading(true)
    const [{ data: gs, error }, { data: ls }] = await Promise.all([
      listGroups(filterLab),
      listLabs(),
    ])
    setLoading(false)
    if (error) {
      message.error(error.message)
      return
    }
    setLabs(ls || [])
    setGroups(gs || [])
    // Fetch members per group (small N — acceptable). Done in parallel.
    const memberEntries = await Promise.all(
      (gs || []).map(async (g) => {
        const { data } = await listGroupMembers(g.id)
        return [g.id, data || []]
      })
    )
    setMemberCache(Object.fromEntries(memberEntries))
  }

  useEffect(() => {
    reload()
  }, [filterLab])

  async function handleDelete(g) {
    const { error } = await deleteGroup(g.id)
    if (error) message.error(error.message)
    else {
      message.success('Đã xóa nhóm')
      reload()
    }
  }

  const filtered = groups.filter((g) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      g.name.toLowerCase().includes(q) ||
      (g.semester || '').toLowerCase().includes(q) ||
      (g.lab?.code || '').toLowerCase().includes(q)
    )
  })

  const columns = [
    {
      title: 'Lab',
      dataIndex: ['lab', 'code'],
      width: 200,
      render: (v, r) => (
        <div>
          <Text code>{v}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.lab?.title}</div>
        </div>
      ),
    },
    { title: 'Tên nhóm', dataIndex: 'name' },
    { title: 'Học kỳ', dataIndex: 'semester', width: 140 },
    {
      title: 'Thành viên',
      key: 'members',
      render: (_, g) => {
        const ms = memberCache[g.id] || []
        if (ms.length === 0) return <Text type="secondary">— chưa có —</Text>
        return (
          <Space size={4} wrap>
            {ms.map((m) => (
              <Tag
                key={m.user_id}
                color={m.role === 'leader' ? 'gold' : 'default'}
              >
                {m.role === 'leader' ? '👑 ' : ''}
                {m.profile?.mssv || m.profile?.username || '—'}
              </Tag>
            ))}
          </Space>
        )
      },
    },
    {
      title: 'Hành động',
      key: 'actions',
      align: 'right',
      width: 180,
      render: (_, g) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingGroup(g)
              setFormOpen(true)
            }}
          >
            Sửa
          </Button>
          <Popconfirm
            title="Xóa nhóm này (và tất cả thành viên + sessions của nó)?"
            okText="Xóa"
            cancelText="Hủy"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(g)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Select
          placeholder="Lọc theo lab"
          value={filterLab || undefined}
          onChange={(v) => setFilterLab(v ?? null)}
          allowClear
          style={{ width: 280 }}
          options={labs.map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Input
          prefix={<SearchOutlined />}
          placeholder="Tìm theo tên / học kỳ / mã lab…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 260 }}
          allowClear
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          Làm mới
        </Button>
        <Button
          icon={<ImportOutlined />}
          onClick={() => setBulkOpen(true)}
          style={{ marginLeft: 'auto' }}
        >
          Bulk import CSV
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingGroup(null)
            setFormOpen(true)
          }}
        >
          Tạo nhóm
        </Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={filtered}
        pagination={{ pageSize: 20 }}
      />

      <GroupForm
        open={formOpen}
        labId={filterLab}
        labs={labs}
        group={editingGroup}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false)
          reload()
        }}
      />

      <GroupBulkImport
        open={bulkOpen}
        labs={labs}
        onClose={() => setBulkOpen(false)}
        onImported={() => {
          setBulkOpen(false)
          reload()
        }}
      />
    </Card>
  )
}
```

- [ ] **Step 2: Wire into AdminPage**

In `web/src/pages/AdminPage.jsx`, add import:

```javascript
import GroupsAdminTab from './admin/GroupsAdminTab'
```

In the `items` array, insert after the Labs entry:

```javascript
{ key: 'groups', label: '👥 Groups', children: <GroupsAdminTab /> },
```

- [ ] **Step 3: Smoke test**

Run: `cd web && npm run dev` → visit `/admin` → "👥 Groups" tab.

Expected:
- Lab filter populated.
- "Tạo nhóm" opens modal; creating a group succeeds. Re-open it (Sửa) → MSSV autocomplete returns matches; adding a member persists.
- "Bulk import CSV" opens; selecting a small test CSV with 2-3 groups parses + previews rows; import succeeds; result table shows OK/FAIL per row.

- [ ] **Step 4: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add web/src/pages/admin/GroupsAdminTab.jsx web/src/pages/AdminPage.jsx
git commit -m "feat(web/admin): add GroupsAdminTab + wire into AdminPage"
```

---

## Task 17: `SessionDetail` drawer

**Files:**
- Create: `web/src/components/admin/SessionDetail.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect, useState } from 'react'
import {
  Drawer,
  Descriptions,
  Tag,
  Timeline,
  Space,
  Button,
  Typography,
  Divider,
  Popconfirm,
  message,
  Empty,
} from 'antd'
import { StopOutlined, RollbackOutlined } from '@ant-design/icons'
import {
  getSession,
  listSessionEvidence,
  listSteps,
  forceEndSession,
  resetSessionStep,
} from '../../services/labApi'

const { Text } = Typography

const STATUS_COLOR = {
  ACTIVE: 'processing',
  COMPLETED: 'success',
  EXPIRED: 'default',
  CANCELLED: 'error',
}

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SessionDetail({ sessionId, open, onClose, onChanged }) {
  const [session, setSession] = useState(null)
  const [evidence, setEvidence] = useState([])
  const [steps, setSteps] = useState([])
  const [loading, setLoading] = useState(true)

  async function reload() {
    if (!sessionId) return
    setLoading(true)
    const [{ data: s }, { data: ev }] = await Promise.all([
      getSession(sessionId),
      listSessionEvidence(sessionId),
    ])
    setSession(s)
    setEvidence(ev || [])
    if (s?.lab?.id) {
      const { data: st } = await listSteps(s.lab.id)
      setSteps(st || [])
    }
    setLoading(false)
  }

  useEffect(() => {
    if (open) reload()
  }, [open, sessionId])

  async function handleForceEnd() {
    const { error } = await forceEndSession(sessionId)
    if (error) message.error(error.message)
    else {
      message.success('Đã kết thúc session')
      onChanged?.()
      reload()
    }
  }

  async function handleResetStep() {
    const { error } = await resetSessionStep(sessionId)
    if (error) message.error(error.message)
    else {
      message.success('Đã reset step')
      reload()
    }
  }

  // Aggregate evidence per step.
  const perStep = steps.map((s) => {
    const items = evidence.filter((e) => e.step_id === s.id)
    return {
      ...s,
      evidenceCount: items.length,
      reached: (s.required_count || 0) === 0 ? true : items.length >= s.required_count,
      sample: items.slice(0, 3),
    }
  })

  return (
    <Drawer
      open={open}
      title={session ? `Session ${session.session_code}` : 'Session'}
      onClose={onClose}
      width={840}
      destroyOnClose
      loading={loading}
    >
      {!session ? (
        <Empty description="Không tìm thấy session" />
      ) : (
        <>
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="Mã code">
              <Text code>{session.session_code}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <Tag color={STATUS_COLOR[session.status] || 'default'}>{session.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Lab">
              {session.lab?.code} — {session.lab?.title}
            </Descriptions.Item>
            <Descriptions.Item label="Nhóm">
              {session.group?.name} ({session.group?.semester || '—'})
            </Descriptions.Item>
            <Descriptions.Item label="Bắt đầu">{fmtTime(session.started_at)}</Descriptions.Item>
            <Descriptions.Item label="Kết thúc">{fmtTime(session.ended_at)}</Descriptions.Item>
            <Descriptions.Item label="Hết hạn lúc">{fmtTime(session.expires_at)}</Descriptions.Item>
            <Descriptions.Item label="Step hiện tại">
              {session.current_step_id || '— (không có step active)'}
            </Descriptions.Item>
          </Descriptions>

          {session.status === 'ACTIVE' && (
            <Space style={{ marginTop: 12 }}>
              <Popconfirm
                title="Force-end session này (chuyển sang CANCELLED)?"
                okText="Force end"
                cancelText="Hủy"
                okButtonProps={{ danger: true }}
                onConfirm={handleForceEnd}
              >
                <Button danger icon={<StopOutlined />}>
                  Force end
                </Button>
              </Popconfirm>
              <Button icon={<RollbackOutlined />} onClick={handleResetStep}>
                Reset current step
              </Button>
            </Space>
          )}

          <Divider>Tiến độ steps</Divider>
          <Timeline
            items={perStep.map((s) => ({
              color: s.reached ? 'green' : 'gray',
              children: (
                <div>
                  <Text strong>
                    #{s.step_order} — {s.title}
                  </Text>
                  <div style={{ fontSize: 12, color: '#6b7280' }}>
                    {s.evidenceCount} / {s.required_count || 0} evidence ·{' '}
                    type: <Text code>{s.evidence_type}</Text>
                  </div>
                  {s.sample.length > 0 && (
                    <ul
                      style={{
                        marginTop: 4,
                        paddingLeft: 16,
                        fontSize: 11,
                        color: '#9ca3af',
                      }}
                    >
                      {s.sample.map((ev) => (
                        <li key={ev.id}>
                          {fmtTime(ev.created_at)} · {ev.evidence_type} · by{' '}
                          {ev.submitter?.mssv || ev.submitter?.username || '—'}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ),
            }))}
          />
        </>
      )}
    </Drawer>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/SessionDetail.jsx
git commit -m "feat(web/admin): add SessionDetail drawer"
```

---

## Task 18: `SessionsAdminTab`

**Files:**
- Create: `web/src/pages/admin/SessionsAdminTab.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Tag,
  Space,
  Typography,
  Select,
  DatePicker,
  Button,
  message,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { listSessions, listLabs } from '../../services/labApi'
import SessionDetail from '../../components/admin/SessionDetail'

const { Text } = Typography
const { RangePicker } = DatePicker

const STATUS_COLOR = {
  ACTIVE: 'processing',
  COMPLETED: 'success',
  EXPIRED: 'default',
  CANCELLED: 'error',
}

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SessionsAdminTab() {
  const [labs, setLabs] = useState([])
  const [sessions, setSessions] = useState([])
  const [loading, setLoading] = useState(true)
  const [filterLab, setFilterLab] = useState(null)
  const [filterStatus, setFilterStatus] = useState(null)
  const [dateRange, setDateRange] = useState(null)
  const [openSessionId, setOpenSessionId] = useState(null)

  async function reload() {
    setLoading(true)
    const [{ data: ss, error }, { data: ls }] = await Promise.all([
      listSessions({
        labId: filterLab,
        status: filterStatus,
        fromDate: dateRange?.[0]?.toISOString() ?? null,
        toDate: dateRange?.[1]?.toISOString() ?? null,
      }),
      listLabs(),
    ])
    setLoading(false)
    if (error) {
      message.error(error.message)
      return
    }
    setLabs(ls || [])
    setSessions(ss || [])
  }

  useEffect(() => {
    reload()
  }, [filterLab, filterStatus, dateRange])

  const columns = [
    {
      title: 'Mã code',
      dataIndex: 'session_code',
      width: 110,
      render: (v) => <Text code>{v}</Text>,
    },
    {
      title: 'Lab',
      dataIndex: ['lab', 'code'],
      render: (v, r) => (
        <div>
          <Text code>{v}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.lab?.title}</div>
        </div>
      ),
    },
    {
      title: 'Nhóm',
      dataIndex: ['group', 'name'],
      render: (v, r) => (
        <div>
          <Text>{v}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.group?.semester}</div>
        </div>
      ),
    },
    {
      title: 'Khởi tạo bởi',
      dataIndex: 'started_by_profile',
      render: (p) =>
        p ? (
          <div>
            <Text code>{p.mssv || '—'}</Text>
            <div style={{ fontSize: 11, color: '#9ca3af' }}>
              {p.full_name || p.username}
            </div>
          </div>
        ) : (
          '—'
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 110,
      render: (v) => <Tag color={STATUS_COLOR[v] || 'default'}>{v}</Tag>,
    },
    { title: 'Bắt đầu', dataIndex: 'started_at', render: fmtTime },
    { title: 'Kết thúc', dataIndex: 'ended_at', render: fmtTime },
    {
      title: '',
      key: 'actions',
      align: 'right',
      render: (_, r) => (
        <Button size="small" onClick={() => setOpenSessionId(r.id)}>
          Chi tiết
        </Button>
      ),
    },
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Select
          placeholder="Lọc theo lab"
          value={filterLab || undefined}
          onChange={(v) => setFilterLab(v ?? null)}
          allowClear
          style={{ width: 240 }}
          options={labs.map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Select
          placeholder="Trạng thái"
          value={filterStatus || undefined}
          onChange={(v) => setFilterStatus(v ?? null)}
          allowClear
          style={{ width: 160 }}
          options={['ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED'].map((s) => ({
            value: s,
            label: s,
          }))}
        />
        <RangePicker
          showTime
          value={dateRange}
          onChange={(v) => setDateRange(v)}
          style={{ width: 360 }}
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          Làm mới
        </Button>
        <Text strong style={{ marginLeft: 'auto' }}>
          Tổng: {sessions.length}
        </Text>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={sessions}
        pagination={{ pageSize: 20 }}
      />

      <SessionDetail
        sessionId={openSessionId}
        open={!!openSessionId}
        onClose={() => setOpenSessionId(null)}
        onChanged={reload}
      />
    </Card>
  )
}
```

- [ ] **Step 2: Wire into AdminPage**

In `web/src/pages/AdminPage.jsx`, add import:

```javascript
import SessionsAdminTab from './admin/SessionsAdminTab'
```

In the `items` array, insert after the Groups entry:

```javascript
{ key: 'sessions', label: '🎯 Sessions', children: <SessionsAdminTab /> },
```

- [ ] **Step 3: Smoke test**

Run: `cd web && npm run dev` → `/admin` → "🎯 Sessions" tab.

Expected:
- Filters apply correctly.
- "Chi tiết" opens drawer with descriptions + step timeline.
- For ACTIVE sessions, the "Force end" button cancels the session and refreshes the list to show CANCELLED.

- [ ] **Step 4: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add web/src/pages/admin/SessionsAdminTab.jsx web/src/pages/AdminPage.jsx
git commit -m "feat(web/admin): add SessionsAdminTab + SessionDetail wiring"
```

---

## Task 19: `SubmissionDetail` drawer

**Files:**
- Create: `web/src/components/admin/SubmissionDetail.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect, useState } from 'react'
import {
  Drawer,
  Descriptions,
  Typography,
  Input,
  Button,
  Space,
  Empty,
  Divider,
  Tag,
  message,
} from 'antd'
import { SaveOutlined, DownloadOutlined } from '@ant-design/icons'
import {
  getPostSubmission,
  setTeacherComment,
  listQuestions,
  listReports,
  getReportSignedUrl,
  listPreQuizSubmissions,
} from '../../services/labApi'

const { Text, Paragraph } = Typography

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SubmissionDetail({ open, submission, onClose, onChanged }) {
  const [post, setPost] = useState(null)
  const [postQs, setPostQs] = useState([])
  const [report, setReport] = useState(null)
  const [preLatest, setPreLatest] = useState(null)
  const [loading, setLoading] = useState(true)
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open || !submission) return
    let cancelled = false
    ;(async () => {
      setLoading(true)
      const [{ data: p }, { data: qs }, { data: rs }, { data: pre }] =
        await Promise.all([
          getPostSubmission(submission.user_id, submission.session_id),
          listQuestions(submission.session?.lab_id, 'post_lab'),
          listReports({ sessionId: submission.session_id }),
          listPreQuizSubmissions({
            labId: submission.session?.lab_id,
            userId: submission.user_id,
          }),
        ])
      if (cancelled) return
      setPost(p || null)
      setPostQs(qs || [])
      setReport((rs || []).find((r) => r.user_id === submission.user_id) || null)
      setPreLatest((pre || [])[0] || null)
      setComment(p?.teacher_comment || '')
      setLoading(false)
    })()
    return () => {
      cancelled = true
    }
  }, [open, submission])

  async function handleSaveComment() {
    if (!post) return
    setSaving(true)
    const { error } = await setTeacherComment(post.id, comment)
    setSaving(false)
    if (error) message.error(error.message)
    else {
      message.success('Đã lưu nhận xét')
      onChanged?.()
    }
  }

  async function handleDownloadPdf() {
    if (!report) return
    const { data, error } = await getReportSignedUrl(report.pdf_storage_path, 60)
    if (error || !data?.signedUrl) {
      message.error(error?.message || 'Không lấy được signed URL')
      return
    }
    const a = document.createElement('a')
    a.href = data.signedUrl
    a.download = report.pdf_storage_path.split('/').pop()
    a.click()
  }

  return (
    <Drawer
      open={open}
      title={
        submission
          ? `Bài nộp — ${submission.profile?.mssv || '—'} · ${
              submission.profile?.full_name || submission.profile?.username
            }`
          : ''
      }
      onClose={onClose}
      width={780}
      destroyOnClose
      loading={loading}
    >
      {!submission ? (
        <Empty />
      ) : (
        <>
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="Lab">
              <Text code>{submission.session?.lab?.code}</Text> —{' '}
              {submission.session?.lab?.title}
            </Descriptions.Item>
            <Descriptions.Item label="Nhóm">
              {submission.session?.group?.name}
            </Descriptions.Item>
            <Descriptions.Item label="Session code">
              <Text code>{submission.session?.session_code}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Pre-quiz (mới nhất)">
              {preLatest ? (
                <Space>
                  <Text>{preLatest.score_percent}%</Text>
                  <Tag color={preLatest.passed ? 'success' : 'error'}>
                    {preLatest.passed ? 'PASSED' : 'FAILED'}
                  </Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Lần {preLatest.attempt_number} · {fmtTime(preLatest.submitted_at)}
                  </Text>
                </Space>
              ) : (
                <Text type="secondary">Chưa làm</Text>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Post-lab">
              {post ? (
                <Space>
                  <Tag color={post.is_draft ? 'warning' : 'success'}>
                    {post.is_draft ? 'DRAFT' : 'SUBMITTED'}
                  </Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {fmtTime(post.submitted_at || post.updated_at)}
                  </Text>
                </Space>
              ) : (
                <Text type="secondary">Chưa nộp</Text>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="PDF">
              {report ? (
                <Button
                  size="small"
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={handleDownloadPdf}
                >
                  Tải PDF
                </Button>
              ) : (
                <Text type="secondary">Chưa generate</Text>
              )}
            </Descriptions.Item>
          </Descriptions>

          <Divider>Câu trả lời post-lab</Divider>
          {!post || !post.answers ? (
            <Text type="secondary">Chưa có dữ liệu</Text>
          ) : (
            postQs.map((q) => {
              const ans = post.answers?.[q.id]
              return (
                <div key={q.id} style={{ marginBottom: 16 }}>
                  <Text strong>
                    #{q.question_order} — {q.question_text}
                  </Text>
                  <Paragraph
                    style={{
                      whiteSpace: 'pre-wrap',
                      background: '#f9fafb',
                      padding: 8,
                      borderRadius: 6,
                      marginTop: 4,
                      marginBottom: 0,
                    }}
                  >
                    {typeof ans === 'string' ? ans : JSON.stringify(ans) || '—'}
                  </Paragraph>
                </div>
              )
            })
          )}

          <Divider>Nhận xét của giáo viên</Divider>
          <Input.TextArea
            rows={4}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Nhận xét nội bộ — KHÔNG xuất hiện trên PDF của sinh viên"
          />
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            disabled={!post}
            style={{ marginTop: 8 }}
            onClick={handleSaveComment}
          >
            Lưu nhận xét
          </Button>
        </>
      )}
    </Drawer>
  )
}
```

- [ ] **Step 2: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/admin/SubmissionDetail.jsx
git commit -m "feat(web/admin): add SubmissionDetail drawer"
```

---

## Task 20: `SubmissionsAdminTab` with bulk ZIP download

**Files:**
- Create: `web/src/pages/admin/SubmissionsAdminTab.jsx`

- [ ] **Step 1: Implement**

```jsx
import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Tag,
  Space,
  Typography,
  Select,
  Button,
  message,
  Progress,
} from 'antd'
import { ReloadOutlined, FileZipOutlined, DownloadOutlined } from '@ant-design/icons'
import JSZip from 'jszip'
import {
  listPostSubmissions,
  listLabs,
  listReports,
  downloadReportBlob,
} from '../../services/labApi'
import SubmissionDetail from '../../components/admin/SubmissionDetail'

const { Text } = Typography

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SubmissionsAdminTab() {
  const [labs, setLabs] = useState([])
  const [filterLab, setFilterLab] = useState(null)
  const [submissions, setSubmissions] = useState([])
  const [loading, setLoading] = useState(true)
  const [openSubmission, setOpenSubmission] = useState(null)
  const [zipping, setZipping] = useState(false)
  const [zipProgress, setZipProgress] = useState({ done: 0, total: 0 })

  async function reload() {
    setLoading(true)
    const [{ data: subs, error }, { data: ls }] = await Promise.all([
      listPostSubmissions({ labId: filterLab }),
      listLabs(),
    ])
    setLoading(false)
    if (error) {
      message.error(error.message)
      return
    }
    setLabs(ls || [])
    setSubmissions(subs || [])
  }

  useEffect(() => {
    reload()
  }, [filterLab])

  /**
   * Download every PDF for the selected lab into a single ZIP. PDFs are
   * fetched as Blobs via supabase.storage.download (RLS allows staff to read
   * any file in lab-reports).
   *
   * Filename inside the ZIP uses pdf_storage_path's basename, which already
   * follows the spec's TR4021_LAB{NN}_{MSSV}_{Name}_{date}.pdf format.
   */
  async function handleBulkZip() {
    if (!filterLab) {
      message.warning('Chọn lab trước khi tải ZIP')
      return
    }
    const lab = labs.find((l) => l.id === filterLab)
    setZipping(true)
    setZipProgress({ done: 0, total: 0 })
    const { data: reports, error } = await listReports({ labId: filterLab })
    if (error) {
      setZipping(false)
      message.error(error.message)
      return
    }
    if (!reports || reports.length === 0) {
      setZipping(false)
      message.info('Không có PDF nào cho lab này')
      return
    }
    setZipProgress({ done: 0, total: reports.length })

    const zip = new JSZip()
    let done = 0
    for (const r of reports) {
      const { data: blob, error: dlErr } = await downloadReportBlob(r.pdf_storage_path)
      if (!dlErr && blob) {
        const name = r.pdf_storage_path.split('/').pop()
        zip.file(name, blob)
      } else if (dlErr) {
        // Skip the failed file but keep going.
        console.error('[bulkZip]', r.pdf_storage_path, dlErr.message)
      }
      done++
      setZipProgress({ done, total: reports.length })
    }
    const zipBlob = await zip.generateAsync({ type: 'blob' })
    const url = URL.createObjectURL(zipBlob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${lab.code}_submissions_${new Date().toISOString().slice(0, 10)}.zip`
    a.click()
    URL.revokeObjectURL(url)
    setZipping(false)
    message.success(`Đã đóng gói ${done} PDF`)
  }

  const columns = [
    {
      title: 'MSSV',
      dataIndex: ['profile', 'mssv'],
      width: 110,
      render: (v) => <Text code>{v || '—'}</Text>,
    },
    {
      title: 'Sinh viên',
      dataIndex: ['profile', 'full_name'],
      render: (v, r) => (
        <div>
          <Text>{v || r.profile?.username || '—'}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.profile?.email}</div>
        </div>
      ),
    },
    {
      title: 'Lab',
      dataIndex: ['session', 'lab', 'code'],
      render: (v) => <Text code>{v}</Text>,
    },
    {
      title: 'Nhóm',
      dataIndex: ['session', 'group', 'name'],
    },
    {
      title: 'Trạng thái',
      dataIndex: 'is_draft',
      width: 110,
      render: (v) =>
        v ? <Tag color="warning">DRAFT</Tag> : <Tag color="success">SUBMITTED</Tag>,
    },
    {
      title: 'Cập nhật',
      dataIndex: 'updated_at',
      width: 170,
      render: fmtTime,
    },
    {
      title: 'Nhận xét',
      dataIndex: 'teacher_comment',
      render: (v) =>
        v ? (
          <Tag color="blue">Có</Tag>
        ) : (
          <Text type="secondary" style={{ fontSize: 11 }}>
            —
          </Text>
        ),
    },
    {
      title: '',
      key: 'actions',
      align: 'right',
      render: (_, r) => (
        <Button size="small" icon={<DownloadOutlined />} onClick={() => setOpenSubmission(r)}>
          Mở
        </Button>
      ),
    },
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Select
          placeholder="Lọc theo lab"
          value={filterLab || undefined}
          onChange={(v) => setFilterLab(v ?? null)}
          allowClear
          style={{ width: 280 }}
          options={labs.map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          Làm mới
        </Button>
        <Button
          type="primary"
          icon={<FileZipOutlined />}
          loading={zipping}
          disabled={!filterLab}
          style={{ marginLeft: 'auto' }}
          onClick={handleBulkZip}
        >
          Tải ZIP toàn bộ PDF (theo lab)
        </Button>
      </div>

      {zipping && zipProgress.total > 0 && (
        <div style={{ padding: '8px 16px' }}>
          <Progress
            percent={Math.round((zipProgress.done / zipProgress.total) * 100)}
            format={() => `${zipProgress.done}/${zipProgress.total}`}
          />
        </div>
      )}

      <Table
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={submissions}
        pagination={{ pageSize: 20 }}
      />

      <SubmissionDetail
        open={!!openSubmission}
        submission={openSubmission}
        onClose={() => setOpenSubmission(null)}
        onChanged={reload}
      />
    </Card>
  )
}
```

- [ ] **Step 2: Wire into AdminPage**

In `web/src/pages/AdminPage.jsx`, add import:

```javascript
import SubmissionsAdminTab from './admin/SubmissionsAdminTab'
```

In the `items` array, insert after the Sessions entry:

```javascript
{ key: 'submissions', label: '📝 Submissions', children: <SubmissionsAdminTab /> },
```

- [ ] **Step 3: Smoke test**

Run: `cd web && npm run dev` → `/admin` → "📝 Submissions" tab.

Expected (with at least one seeded `lab_post_submissions` row):
- Submissions table loads.
- Filter by lab narrows results.
- "Mở" opens the SubmissionDetail drawer; teacher comment saves; PDF download works (if a `lab_reports` row + storage object exists).
- "Tải ZIP toàn bộ PDF" with a lab selected downloads a single ZIP containing every PDF for that lab. Progress bar updates during fetch.

- [ ] **Step 4: Verify**

Run: `cd web && npm run lint && npm run build`
Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add web/src/pages/admin/SubmissionsAdminTab.jsx web/src/pages/AdminPage.jsx
git commit -m "feat(web/admin): add SubmissionsAdminTab with bulk ZIP export"
```

---

## Task 21: Final tab order audit + screenshot for the spec doc

**Files:**
- Modify: `web/src/pages/AdminPage.jsx` (only if order is wrong)

- [ ] **Step 1: Confirm tab order**

Open `web/src/pages/AdminPage.jsx`. The `items` array should now read in this order:

```
labs → groups → sessions → submissions → users → logs → exports → wiring
```

The four lab tabs come **first** because they are the new primary admin workflow; legacy tabs follow.

If the order doesn't match (e.g. you appended at the end), reorder by moving lines.

- [ ] **Step 2: Final full verify**

Run:
```bash
cd web && npm run lint && npm run build
```
Expected: both clean.

- [ ] **Step 3: Manual end-to-end smoke**

Run `cd web && npm run dev`. Log in as a staff user. Walk this happy path:

1. **Labs** tab → "Tạo lab" with code `LAB-TEST-PHASE3` → Save.
2. Click "Quản lý nội dung" → Steps tab → add 3 steps with different evidence_types → drag to reorder → confirm order persists after closing/reopening drawer.
3. Pre-lab tab → add 2 multiple_choice questions, set correct_answer → Save.
4. Post-lab tab → add 1 free_text + 1 image_upload question → Save.
5. **Groups** tab → "Tạo nhóm" for `LAB-TEST-PHASE3` → save → open the group again → add 2-3 members via MSSV autocomplete → promote one to leader.
6. Bulk import: prepare a CSV with 2 rows, import. Verify the result table shows both as OK.
7. **Sessions** tab → if there are existing sessions, filter by lab + status; "Chi tiết" should show timeline.
8. **Submissions** tab → if any post-submissions exist, the table populates; opening "Mở" shows the answers + lets you save a teacher comment. Bulk-ZIP downloads a `.zip` you can open in any unzipper.
9. Cleanup: delete `LAB-TEST-PHASE3` (cascade removes test groups + submissions).

If anything fails, fix before committing the final commit.

- [ ] **Step 4: Commit (if any reorder change)**

```bash
git add web/src/pages/AdminPage.jsx
git commit -m "chore(web/admin): reorder admin tabs — lab tabs first"
```

If no reorder was needed, skip this commit.

---

## Self-Review Notes

This plan was reviewed against Section 6 of `docs/superpowers/specs/2026-04-16-lab-system-design.md`:

**Spec coverage check (Section 6):**
- 6.1 Labs tab — ✅ Task 12 (LabsAdminTab) + Tasks 7–11 (forms + lists + DnD).
- 6.1 Groups tab — ✅ Task 16 (GroupsAdminTab) + Tasks 14–15 (form + bulk import).
- 6.1 Sessions tab — ✅ Task 18 (SessionsAdminTab) + Task 17 (SessionDetail).
- 6.1 Submissions tab — ✅ Task 20 (SubmissionsAdminTab) + Task 19 (SubmissionDetail).
- 6.2 Component file list — ✅ Every component named in 6.2 has a dedicated task. `MarkdownEditor` in Task 6, used by `LabForm` (Task 7) and `StepForm` (Task 8) for the markdown fields.
- Drag-drop reorder for steps + questions — ✅ Tasks 9 & 11 with `@dnd-kit/sortable` + `reorderSteps`/`reorderQuestions` two-phase update in `labApi.js`.
- MSSV autocomplete for members — ✅ Task 14 (`searchProfilesByMssvOrName` + debounced `Select`).
- CSV bulk import — ✅ Task 15 with format documented inside the modal.
- Bulk ZIP PDFs — ✅ Task 20, JSZip-based with progress UI.
- `services/labApi.js` — ✅ Tasks 2–5 split by concern (labs, steps/questions, groups/members, sessions/submissions/reports).
- Admin force-end + reset-step — ✅ Task 17 (`forceEndSession`, `resetSessionStep`).
- Teacher comment editor — ✅ Task 19 (`setTeacherComment`).
- Per-student PDF download — ✅ Task 19 via signed URL.

**Naming consistency check:**
- `reorderSteps(labId, orderedIds)` (Task 3) consumed unchanged in Task 9.
- `reorderQuestions(labId, phase, orderedIds)` (Task 3) consumed unchanged in Task 11.
- `addGroupMember(groupId, userId, role)` (Task 4) consumed by GroupForm (Task 14).
- `setGroupLeader(groupId, newLeaderUserId)` (Task 4) consumed by GroupForm (Task 14).
- `searchProfilesByMssvOrName(query, limit)` (Task 4) consumed by GroupForm (Task 14).
- `bulkImportGroups(labId, rows)` (Task 4) row shape `{groupName, semester, leaderMssv, memberMssvs[]}` matches what `parseCsv` in Task 15 produces.
- `getReportSignedUrl(storagePath, expiresIn)` (Task 5) consumed by SubmissionDetail (Task 19).
- `downloadReportBlob(storagePath)` (Task 5) consumed by SubmissionsAdminTab bulk ZIP (Task 20).

**Out-of-scope items intentionally NOT covered:**
- Tests / test framework setup (codebase has none; would balloon scope — see plan header).
- Realtime subscriptions for the admin tabs (admin uses manual "Làm mới"; realtime is a Phase-4 student-flow concern).
- Mobile-responsive admin (spec §9 explicitly out of scope).
- Edge function for `expire_old_sessions` cron (operator concern — see `lab_rpc.sql` notes).

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-17-lab-phase-3-admin.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
