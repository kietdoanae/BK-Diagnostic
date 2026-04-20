# Lab Phase 5 — PDF Report Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render a 5–8 page A4 lab report PDF client-side from a submitted `lab_post_submission`, upload it to the `lab-reports` Supabase bucket with a SHA-256 content hash, and wire the "Tạo PDF" button on `LabReportPage` so students can preview and download.

**Architecture:** A single parent component `<LabReportPdfTemplate>` composes six section sub-components and is rendered off-screen into a hidden `<div>`. `html2pdf.js` walks that DOM into a Blob. We compute SHA-256 over `{session_id, answers_json, timestamps}` (not over the Blob — the spec says hash inputs, so admins can re-verify server-side without re-rendering), upload the Blob, insert a `lab_reports` row, and hand a same-origin `blob:` URL to an `<iframe>` for in-page preview. One data-fetch helper gathers everything the template needs in one round trip so the template itself stays pure-presentational.

**Tech Stack:** React 19 + Ant Design 6, `html2pdf.js` 0.10 (adds `html2canvas` + `jspdf` transitively), Supabase JS 2, Web Crypto API `crypto.subtle.digest` (hashing), plain CSS via `<style>` injection inside the template (scoped by id so AntD resets don't bleed in).

---

## File Structure

**Create:**
- `web/src/components/lab/pdf/LabReportPdfTemplate.jsx` — top-level component; renders `<style>` block + six sections + footer.
- `web/src/components/lab/pdf/pdfStyles.js` — exports a CSS string for the Times-New-Roman / Arial / A4 rules. Kept separate so we don't duplicate the string across preview + print paths.
- `web/src/components/lab/pdf/CoverSection.jsx` — HCMUT header, student+group+date block.
- `web/src/components/lab/pdf/ObjectivesSection.jsx` — `labs.description` markdown + static L.O. mapping table.
- `web/src/components/lab/pdf/PreQuizSection.jsx` — pre-quiz results table.
- `web/src/components/lab/pdf/PracticeSummarySection.jsx` — session metadata + per-step timing + top-10 CAN IDs + evidence sample.
- `web/src/components/lab/pdf/PostLabSection.jsx` — markdown answers + embedded uploaded images as data-URIs.
- `web/src/components/lab/pdf/DeclarationSection.jsx` — signed statement + signature block.
- `web/src/services/labReportData.js` — `fetchLabReportData(userId, sessionId)` one-shot fetcher.
- `web/src/services/labReportFilename.js` — `buildReportFilename({ labCode, mssv, fullName, date })` pure helper.
- `web/src/services/labReportHash.js` — `sha256Hex(obj)` wrapper over `crypto.subtle`.
- `web/src/services/labReportGenerator.js` — orchestrator: renders template to Blob, computes hash, uploads, inserts row, returns `{ blobUrl, contentHash, storagePath, filename }`.
- `web/public/assets/hcmut-logo.png` — copy of the existing logo asset used by the Android app (or link to the web logo if one already exists).
- `web/src/assets/times.css.js` — (only if web-font fallback is needed; see Task 4 decision point).

**Modify:**
- `web/package.json` — add `html2pdf.js` dependency.
- `web/src/pages/LabReportPage.jsx` — replace the Phase-5 stub with: "Xem trước" iframe + "Tạo PDF" button + post-generation download handoff.
- `web/src/services/labApi.js` — add `insertLabReport()` wrapper.

**No-test-framework note:** the web subproject has no Vitest/Jest configured. Pure helpers (`buildReportFilename`, `sha256Hex`, `fetchLabReportData`) are verified via a one-shot Node harness (Task 16) that we can discard after. Visual output is verified via the preview iframe in dev + cross-browser manual QA (Task 18). **Do not add a test framework** — that is out of scope for this plan.

---

## Task 1: Add `html2pdf.js` dependency

**Files:**
- Modify: `web/package.json` (dependency list)

- [ ] **Step 1: Install the package**

Run from `web/`:
```bash
npm install html2pdf.js@^0.10.3
```

Expected output: one new dependency added, no peer warnings (it bundles `html2canvas` + `jspdf`).

- [ ] **Step 2: Verify bundle still builds**

Run:
```bash
cd web && npm run build
```

Expected: `vite build` exits 0, produces `dist/` without errors. Bundle size will grow ~250 kB gzipped — that is fine; the PDF template is only imported on `/labs/:labId/session/:sid/report` (lazy via dynamic import later, see Task 15).

- [ ] **Step 3: Commit**

```bash
git add web/package.json web/package-lock.json
git commit -m "chore(web): add html2pdf.js for lab PDF reports"
```

---

## Task 2: Filename helper — `buildReportFilename`

**Files:**
- Create: `web/src/services/labReportFilename.js`

- [ ] **Step 1: Write the helper**

Create `web/src/services/labReportFilename.js`:

```js
// Builds TR4021_LAB{NN}_{MSSV}_{HọTên}_{YYYY-MM-DD}.pdf per Section 7.4.
// Vietnamese diacritics are stripped so the filename survives filesystems
// and email clients that misencode NFC/NFD.
//
// Example: buildReportFilename({ labCode: 'LAB01', mssv: '2210xxxx',
//   fullName: 'Nguyễn Hoàng Kiệt', date: '2026-04-17' })
//   → 'TR4021_LAB01_2210xxxx_NguyenHoangKiet_2026-04-17.pdf'

// NFD decomposes characters into base + combining marks, then we drop the
// combining-mark range (U+0300–U+036F). `đ`/`Đ` do not decompose in NFD,
// so they need an explicit table.
const MANUAL_MAP = { đ: 'd', Đ: 'D' }

export function stripVietnameseDiacritics(input) {
  if (!input) return ''
  const decomposed = input.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  return decomposed.replace(/[đĐ]/g, (c) => MANUAL_MAP[c] ?? c)
}

export function buildReportFilename({ labCode, mssv, fullName, date }) {
  if (!labCode || !mssv || !fullName || !date) {
    throw new Error('buildReportFilename: labCode, mssv, fullName, date are all required')
  }
  // Remove whitespace from the name: "Nguyễn Hoàng Kiệt" → "NguyenHoangKiet"
  const asciiName = stripVietnameseDiacritics(fullName).replace(/\s+/g, '')
  // Also scrub anything outside [A-Za-z0-9] that might have snuck in (extended
  // punctuation, en-dash in typed names, etc.) — keep the filename stable.
  const safeName = asciiName.replace(/[^A-Za-z0-9]/g, '')
  const safeMssv = String(mssv).replace(/[^A-Za-z0-9]/g, '')
  return `TR4021_${labCode}_${safeMssv}_${safeName}_${date}.pdf`
}
```

- [ ] **Step 2: Verify via node REPL**

Run from repo root:
```bash
node --input-type=module -e "import('./web/src/services/labReportFilename.js').then(m => { console.log(m.buildReportFilename({ labCode: 'LAB01', mssv: '2210xxxx', fullName: 'Nguyễn Hoàng Kiệt', date: '2026-04-17' })) })"
```

Expected: `TR4021_LAB01_2210xxxx_NguyenHoangKiet_2026-04-17.pdf` printed.

Also try an edge-case diacritic:
```bash
node --input-type=module -e "import('./web/src/services/labReportFilename.js').then(m => { console.log(m.stripVietnameseDiacritics('Đặng Thị Ánh Ngọc')) })"
```

Expected: `Dang Thi Anh Ngoc`.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labReportFilename.js
git commit -m "feat(web/lab): report filename helper with diacritic stripping"
```

---

## Task 3: SHA-256 hash helper — `sha256Hex`

**Files:**
- Create: `web/src/services/labReportHash.js`

- [ ] **Step 1: Write the helper**

Create `web/src/services/labReportHash.js`:

```js
// SHA-256 over a deterministic JSON serialization of the given input.
// We hash {session_id, answers_json, timestamps} per Section 7.3 so the
// server can recompute and compare without re-rendering the PDF.
//
// Determinism requires stable key ordering. JSON.stringify is not stable for
// arbitrary objects, so we sort keys recursively before serializing.

function stableStringify(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value)
  if (Array.isArray(value)) {
    return '[' + value.map(stableStringify).join(',') + ']'
  }
  const keys = Object.keys(value).sort()
  return (
    '{' +
    keys
      .map((k) => JSON.stringify(k) + ':' + stableStringify(value[k]))
      .join(',') +
    '}'
  )
}

export async function sha256Hex(input) {
  const payload = typeof input === 'string' ? input : stableStringify(input)
  const bytes = new TextEncoder().encode(payload)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * Build the canonical hash-input object per spec Section 7.3:
 *   { session_id, answers_json, timestamps }
 * `timestamps` pulls the two timestamps that tie the report to an actual
 * session run (session.started_at, post_submission.finalized_at).
 */
export function buildHashInput({ sessionId, answers, startedAt, finalizedAt }) {
  return {
    session_id: sessionId,
    answers_json: answers || {},
    timestamps: {
      session_started_at: startedAt || null,
      post_finalized_at: finalizedAt || null,
    },
  }
}
```

- [ ] **Step 2: Verify against a known vector**

Use the Node REPL (Node 20 supports `crypto.subtle` globally):
```bash
node --input-type=module -e "import('./web/src/services/labReportHash.js').then(async m => { console.log(await m.sha256Hex('hello')) })"
```

Expected: `2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824` (the SHA-256 of the 5-byte string `hello`).

And stability:
```bash
node --input-type=module -e "import('./web/src/services/labReportHash.js').then(async m => { const a = await m.sha256Hex({b:1,a:2}); const b = await m.sha256Hex({a:2,b:1}); console.log(a === b) })"
```

Expected: `true` — key order must not change the hash.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labReportHash.js
git commit -m "feat(web/lab): sha256 hash helper with stable key ordering"
```

---

## Task 4: PDF CSS — `pdfStyles.js`

**Files:**
- Create: `web/src/components/lab/pdf/pdfStyles.js`

- [ ] **Step 1: Decide on font strategy**

Times New Roman and Arial are safe everywhere on Windows and macOS. On Linux the nearest bundled fallbacks are `Liberation Serif` / `Liberation Sans`, which render at the same metrics. `html2pdf.js` rasterizes via `html2canvas` — it uses whatever the browser renders, so we rely on generic-family fallbacks rather than shipping a webfont. **No webfont**. (If a reviewer wants pixel-identical Linux output later, we add `@font-face` in a separate change.)

- [ ] **Step 2: Write the stylesheet**

Create `web/src/components/lab/pdf/pdfStyles.js`:

```js
// Exported as a string so we can inject it via <style> inside the template's
// root container (id="lab-report-root"). Scoping to that id keeps AntD's
// global resets out — the template must not be styled by the host page.
//
// Spec Section 7.2:
//   - Body: Times New Roman 12 pt
//   - Tables / code blocks: Arial 10 pt
//   - A4 page, 20 mm margins, mostly monochrome

export const PDF_STYLES = `
#lab-report-root {
  font-family: "Times New Roman", "Liberation Serif", Times, serif;
  font-size: 12pt;
  line-height: 1.45;
  color: #000;
  background: #fff;
  width: 170mm;           /* 210mm A4 − 2×20mm margin */
  margin: 0 auto;
  padding: 0;
}

#lab-report-root .page {
  page-break-after: always;
  padding: 20mm;
  box-sizing: border-box;
  width: 210mm;
  min-height: 297mm;
}

#lab-report-root .page:last-child {
  page-break-after: auto;
}

#lab-report-root h1 {
  font-size: 18pt;
  margin: 0 0 8mm;
  font-weight: 700;
}

#lab-report-root h2 {
  font-size: 14pt;
  margin: 6mm 0 3mm;
  font-weight: 700;
  border-bottom: 1pt solid #000;
  padding-bottom: 1mm;
}

#lab-report-root h3 {
  font-size: 12pt;
  margin: 4mm 0 2mm;
  font-weight: 700;
}

#lab-report-root p,
#lab-report-root li {
  margin: 0 0 2mm;
  text-align: justify;
}

/* Tables and code → Arial 10pt per spec. */
#lab-report-root table,
#lab-report-root pre,
#lab-report-root code {
  font-family: Arial, "Liberation Sans", Helvetica, sans-serif;
  font-size: 10pt;
}

#lab-report-root table {
  width: 100%;
  border-collapse: collapse;
  margin: 2mm 0 4mm;
}

#lab-report-root th,
#lab-report-root td {
  border: 0.5pt solid #000;
  padding: 1.5mm 2mm;
  vertical-align: top;
  text-align: left;
}

#lab-report-root th {
  background: #eee;
  font-weight: 700;
}

#lab-report-root pre {
  border: 0.5pt solid #888;
  padding: 2mm;
  background: #fafafa;
  white-space: pre-wrap;
  word-break: break-all;
}

#lab-report-root .cover {
  text-align: center;
  padding-top: 30mm;
}

#lab-report-root .cover-logo {
  width: 28mm;
  height: auto;
  margin: 0 auto 6mm;
  display: block;
}

#lab-report-root .cover-meta {
  margin-top: 20mm;
  text-align: left;
  display: inline-block;
  min-width: 100mm;
}

#lab-report-root .tick {
  color: #2c7a2c;
  font-weight: 700;
}
#lab-report-root .cross {
  color: #a33;
  font-weight: 700;
}

#lab-report-root .footer {
  position: running(footer);
  font-family: Arial, "Liberation Sans", sans-serif;
  font-size: 9pt;
  color: #555;
  border-top: 0.5pt solid #888;
  padding-top: 1mm;
  display: flex;
  justify-content: space-between;
}

#lab-report-root .declaration-sig {
  margin-top: 30mm;
  display: flex;
  justify-content: flex-end;
}

#lab-report-root .declaration-sig .sig-box {
  text-align: center;
  width: 70mm;
}

#lab-report-root .declaration-sig .sig-line {
  margin: 18mm auto 2mm;
  border-top: 0.5pt solid #000;
  width: 60mm;
}

#lab-report-root .post-image {
  max-width: 150mm;
  max-height: 90mm;
  display: block;
  margin: 2mm 0;
  border: 0.5pt solid #888;
}

#lab-report-root .evidence-sample {
  max-width: 80mm;
  max-height: 50mm;
  border: 0.5pt solid #888;
  margin: 1mm;
}

#lab-report-root .muted {
  color: #555;
}

@media print {
  body { margin: 0; }
  #lab-report-root { margin: 0; }
}
`
```

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/pdf/pdfStyles.js
git commit -m "feat(web/lab/pdf): A4 print stylesheet for report template"
```

---

## Task 5: Data fetcher — `fetchLabReportData`

**Files:**
- Create: `web/src/services/labReportData.js`

- [ ] **Step 1: Write the fetcher**

Create `web/src/services/labReportData.js`:

```js
import { supabase } from './supabase'

/**
 * One-shot fetcher: returns every field the PDF template needs, so the
 * template stays a pure function of its props.
 *
 * Returns:
 *   { lab, student, group, session, steps, preQuiz, postSubmission,
 *     evidenceByStep, topCanIds, questions }
 *
 * Errors short-circuit — the first failing query returns { data: null, error }.
 * Call sites render the Alert on error and bail.
 */
export async function fetchLabReportData(userId, sessionId) {
  // 1. Session (+ lab + group) — one hop gives us most of the cover page.
  const { data: session, error: sErr } = await supabase
    .from('lab_sessions')
    .select(
      'id, session_code, status, started_at, ended_at, expires_at, ' +
        'current_step_id, started_by, lab_id, group_id, ' +
        'lab:labs(id, code, title, description, pre_quiz_pass_threshold), ' +
        'group:lab_groups(id, name, semester)'
    )
    .eq('id', sessionId)
    .single()
  if (sErr) return { data: null, error: sErr }

  // 2. Student profile.
  const { data: student, error: pErr } = await supabase
    .from('profiles')
    .select('id, username, full_name, mssv')
    .eq('id', userId)
    .single()
  if (pErr) return { data: null, error: pErr }

  // 3. Everything else in parallel — independent reads.
  const [stepsRes, preQuizRes, postRes, questionsRes, evidenceRes] =
    await Promise.all([
      supabase
        .from('lab_steps')
        .select('id, step_order, title, instruction, evidence_type, required_count, hint')
        .eq('lab_id', session.lab_id)
        .order('step_order', { ascending: true }),
      supabase
        .from('lab_pre_quiz_submissions')
        .select('id, score_percent, passed, attempt_number, submitted_at, answers')
        .eq('user_id', userId)
        .eq('lab_id', session.lab_id)
        .order('submitted_at', { ascending: false })
        .limit(1)
        .maybeSingle(),
      supabase
        .from('lab_post_submissions')
        .select('id, answers, uploaded_images, finalized_at, is_draft, submitted_at')
        .eq('user_id', userId)
        .eq('session_id', sessionId)
        .maybeSingle(),
      supabase
        .from('lab_questions')
        .select('id, question_order, question_type, question_text, options, correct_answer, stage, points')
        .eq('lab_id', session.lab_id)
        .order('question_order', { ascending: true }),
      supabase
        .from('lab_evidence')
        .select('id, step_id, evidence_type, payload, captured_at')
        .eq('session_id', sessionId)
        .order('captured_at', { ascending: true }),
    ])

  for (const r of [stepsRes, preQuizRes, postRes, questionsRes, evidenceRes]) {
    if (r.error) return { data: null, error: r.error }
  }

  // Bucket evidence by step_id so the template can render a per-step sample.
  const evidenceByStep = {}
  for (const e of evidenceRes.data || []) {
    const key = e.step_id || 'unassigned'
    ;(evidenceByStep[key] ||= []).push(e)
  }

  // Top-10 CAN IDs across all frame evidence. Frames look like
  // { canId: '0x7E8', data: '...', ts: ... } — we count by canId and keep
  // one sample payload per id.
  const canCounter = new Map()
  for (const e of evidenceRes.data || []) {
    if (e.evidence_type !== 'raw_frame' && e.evidence_type !== 'frame_batch') continue
    const frames = e.evidence_type === 'frame_batch' ? (e.payload?.frames || []) : [e.payload]
    for (const f of frames) {
      const id = f?.canId || f?.can_id
      if (!id) continue
      const cur = canCounter.get(id) || { canId: id, count: 0, sample: f }
      cur.count++
      canCounter.set(id, cur)
    }
  }
  const topCanIds = [...canCounter.values()]
    .sort((a, b) => b.count - a.count)
    .slice(0, 10)

  return {
    data: {
      lab: session.lab,
      student,
      group: session.group,
      session,
      steps: stepsRes.data || [],
      preQuiz: preQuizRes.data || null,
      postSubmission: postRes.data || null,
      questions: questionsRes.data || [],
      evidenceByStep,
      topCanIds,
    },
    error: null,
  }
}
```

- [ ] **Step 2: Verify against a real submitted session**

Spin up the dev server:
```bash
cd web && npm run dev
```

Open DevTools on `/labs/<labId>/session/<sid>/report`. In the console:
```js
import('/src/services/labReportData.js').then(async m => {
  const { data, error } = await m.fetchLabReportData(
    (await window.supabase?.auth.getUser())?.data?.user?.id,
    '<sessionId>'
  )
  console.log({ data, error })
})
```

If `window.supabase` is not exposed, read `userId` and `sessionId` from the URL / `useAuth()` in a component scratch render instead — the point is to confirm the fetcher returns populated `lab`, `student`, `preQuiz`, and `postSubmission` for a session the logged-in user has actually submitted.

Expected: `data.student.mssv` filled, `data.preQuiz.passed === true`, `data.postSubmission.is_draft === false`, `data.steps.length > 0`, `data.topCanIds` up to 10 entries.

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labReportData.js
git commit -m "feat(web/lab): aggregated data fetcher for PDF report"
```

---

## Task 6: Cover section component

**Files:**
- Create: `web/src/components/lab/pdf/CoverSection.jsx`
- Verify asset exists: `web/public/assets/hcmut-logo.png`

- [ ] **Step 1: Ensure logo is available**

Check if `web/public/assets/hcmut-logo.png` or `web/src/assets/hcmut-logo.png` already exists:
```bash
ls web/public/assets/ web/src/assets/ 2>/dev/null | grep -i hcmut
```

If absent, copy the Android-side asset:
```bash
cp app/src/main/res/drawable-nodpi/hcmut_logo.png web/public/assets/hcmut-logo.png
```

(If no Android asset exists either, skip the `<img>` and render a text header `"ĐẠI HỌC BÁCH KHOA TP.HCM"` only. The spec says "HCMUT logo + header"; text header alone is acceptable if the logo file is missing, and we flag it in the commit message.)

- [ ] **Step 2: Write the component**

Create `web/src/components/lab/pdf/CoverSection.jsx`:

```jsx
// Cover page — HCMUT header + course + lab + student + group + dates.
// Pure presentational. All data arrives via props; no hooks.

function formatVN(dt) {
  if (!dt) return '—'
  const d = typeof dt === 'string' ? new Date(dt) : dt
  return d.toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export default function CoverSection({ lab, student, group, session, postSubmission }) {
  const practiceDate = session?.started_at ? formatVN(session.started_at) : '—'
  const submittedAt = postSubmission?.finalized_at
    ? formatVN(postSubmission.finalized_at)
    : postSubmission?.submitted_at
      ? formatVN(postSubmission.submitted_at)
      : '—'

  return (
    <section className="page cover">
      <img className="cover-logo" src="/assets/hcmut-logo.png" alt="HCMUT" />
      <div style={{ fontSize: '11pt' }}>
        ĐẠI HỌC QUỐC GIA TP.HCM · TRƯỜNG ĐẠI HỌC BÁCH KHOA
      </div>
      <div style={{ fontSize: '11pt', marginBottom: '10mm' }}>
        KHOA KỸ THUẬT GIAO THÔNG
      </div>

      <h1>BÁO CÁO THỰC HÀNH</h1>
      <div style={{ fontSize: '14pt', marginTop: '4mm' }}>
        TR4021 — Chẩn đoán ô tô
      </div>
      <div style={{ fontSize: '14pt', marginTop: '2mm', fontWeight: 700 }}>
        {lab?.code} · {lab?.title}
      </div>

      <div className="cover-meta">
        <table style={{ fontFamily: '"Times New Roman", serif', fontSize: '12pt', border: 0 }}>
          <tbody>
            <tr><td style={{ border: 0, width: '40mm' }}>Sinh viên</td>
                <td style={{ border: 0 }}><strong>{student?.full_name || '—'}</strong></td></tr>
            <tr><td style={{ border: 0 }}>MSSV</td>
                <td style={{ border: 0 }}>{student?.mssv || '—'}</td></tr>
            <tr><td style={{ border: 0 }}>Nhóm</td>
                <td style={{ border: 0 }}>{group?.name || '—'}</td></tr>
            <tr><td style={{ border: 0 }}>Học kỳ</td>
                <td style={{ border: 0 }}>{group?.semester || '—'}</td></tr>
            <tr><td style={{ border: 0 }}>Ngày thực hành</td>
                <td style={{ border: 0 }}>{practiceDate}</td></tr>
            <tr><td style={{ border: 0 }}>Nộp báo cáo</td>
                <td style={{ border: 0 }}>{submittedAt}</td></tr>
            <tr><td style={{ border: 0 }}>Mã session</td>
                <td style={{ border: 0 }}><code>{session?.session_code || '—'}</code></td></tr>
          </tbody>
        </table>
      </div>
    </section>
  )
}
```

- [ ] **Step 3: Commit**

```bash
git add web/src/components/lab/pdf/CoverSection.jsx web/public/assets/hcmut-logo.png 2>/dev/null
git commit -m "feat(web/lab/pdf): cover page section"
```

---

## Task 7: Objectives + L.O. section

**Files:**
- Create: `web/src/components/lab/pdf/ObjectivesSection.jsx`

- [ ] **Step 1: Write the component**

Create `web/src/components/lab/pdf/ObjectivesSection.jsx`:

```jsx
import MDEditor from '@uiw/react-md-editor'

// Static L.O. mapping per spec Section 1.
// (If later you want per-lab L.O. mapping, move this table to a DB column and
//  pass it as a prop. For now, one static map is enough for the 2 pilot labs.)
const LO_MAP = {
  LAB01: [
    { code: 'L.O.2', desc: 'Giải thích cấu trúc khung CAN và các lệnh OBD-II chuẩn.' },
    { code: 'L.O.5', desc: 'Sử dụng công cụ chẩn đoán để đọc PID và DTC.' },
  ],
  LAB02: [
    { code: 'L.O.3', desc: 'Diễn giải dữ liệu cảm biến live và các mã lỗi hệ thống.' },
    { code: 'L.O.6', desc: 'Thực hiện active test an toàn và phân tích kết quả.' },
  ],
}

export default function ObjectivesSection({ lab }) {
  const mapping = LO_MAP[lab?.code] || []
  return (
    <section className="page">
      <h2>1. Mục tiêu bài thực hành</h2>
      <div data-color-mode="light">
        <MDEditor.Markdown source={lab?.description || '_(Không có mô tả)_'} />
      </div>

      <h3>1.1. Liên hệ chuẩn đầu ra (Learning Outcomes)</h3>
      {mapping.length === 0 ? (
        <p className="muted">Chưa khai báo L.O. mapping cho lab này.</p>
      ) : (
        <table>
          <thead>
            <tr><th style={{ width: '25mm' }}>Mã L.O.</th><th>Mô tả</th></tr>
          </thead>
          <tbody>
            {mapping.map((m) => (
              <tr key={m.code}>
                <td><strong>{m.code}</strong></td>
                <td>{m.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/lab/pdf/ObjectivesSection.jsx
git commit -m "feat(web/lab/pdf): objectives and L.O. mapping section"
```

---

## Task 8: Pre-quiz result section

**Files:**
- Create: `web/src/components/lab/pdf/PreQuizSection.jsx`

- [ ] **Step 1: Write the component**

Create `web/src/components/lab/pdf/PreQuizSection.jsx`:

```jsx
// Pre-quiz table: question, chosen answer, correct/incorrect, score + verdict.
// `preQuiz.answers` is stored as { [questionId]: answerKey } (see submit_pre_quiz RPC).
// `questions` is the full question bank filtered to the pre-lab stage.

function answerLabel(question, key) {
  if (!question?.options || key == null) return '—'
  // options is jsonb: [{ key: 'A', text: '…' }, …]
  const found = question.options.find((o) => o.key === key)
  return found ? `${found.key}. ${found.text}` : String(key)
}

export default function PreQuizSection({ preQuiz, questions, lab }) {
  const preQs = (questions || []).filter((q) => q.stage === 'pre_lab')

  if (!preQuiz) {
    return (
      <section className="page">
        <h2>2. Kết quả pre-lab quiz</h2>
        <p className="muted">Chưa có kết quả pre-lab cho lần nộp này.</p>
      </section>
    )
  }

  const threshold = lab?.pre_quiz_pass_threshold ?? 70
  const verdict = preQuiz.passed ? 'ĐẠT' : 'CHƯA ĐẠT'

  return (
    <section className="page">
      <h2>2. Kết quả pre-lab quiz</h2>
      <p>
        Lần thứ <strong>{preQuiz.attempt_number}</strong> · Điểm{' '}
        <strong>{Number(preQuiz.score_percent).toFixed(1)}%</strong> · Ngưỡng đậu{' '}
        {threshold}% → <strong>{verdict}</strong>
      </p>

      <table>
        <thead>
          <tr>
            <th style={{ width: '10mm' }}>#</th>
            <th>Câu hỏi</th>
            <th style={{ width: '55mm' }}>Trả lời</th>
            <th style={{ width: '15mm' }}>Kết quả</th>
          </tr>
        </thead>
        <tbody>
          {preQs.map((q, i) => {
            const chosen = preQuiz.answers?.[q.id]
            const correct = chosen === q.correct_answer
            return (
              <tr key={q.id}>
                <td>{i + 1}</td>
                <td>{q.question_text}</td>
                <td>{answerLabel(q, chosen)}</td>
                <td className={correct ? 'tick' : 'cross'}>
                  {correct ? '✓' : '✗'}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/lab/pdf/PreQuizSection.jsx
git commit -m "feat(web/lab/pdf): pre-quiz results section"
```

---

## Task 9: Practice summary section

**Files:**
- Create: `web/src/components/lab/pdf/PracticeSummarySection.jsx`

- [ ] **Step 1: Write the component**

Create `web/src/components/lab/pdf/PracticeSummarySection.jsx`:

```jsx
// Session metadata + per-step timing + top-10 CAN IDs + evidence sample
// (3–5 frames per step, NOT a full dump per spec Section 7.1).

function fmt(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('vi-VN')
}

function durationMs(fromIso, toIso) {
  if (!fromIso || !toIso) return null
  const ms = new Date(toIso).getTime() - new Date(fromIso).getTime()
  return ms >= 0 ? ms : null
}

function fmtDuration(ms) {
  if (ms == null) return '—'
  const s = Math.round(ms / 1000)
  const m = Math.floor(s / 60)
  const rem = s % 60
  return `${m}m ${String(rem).padStart(2, '0')}s`
}

function briefPayload(payload) {
  // Shrink a raw frame / active-test / DTC payload to one-line text.
  if (!payload) return ''
  if (payload.data) return `data=${payload.data}`
  if (payload.dtc) return `DTC=${payload.dtc}`
  if (payload.command) return `cmd=${payload.command}`
  return JSON.stringify(payload).slice(0, 60)
}

export default function PracticeSummarySection({ session, steps, evidenceByStep, topCanIds }) {
  return (
    <section className="page">
      <h2>3. Tổng kết phiên thực hành</h2>
      <table>
        <tbody>
          <tr><th style={{ width: '40mm' }}>Mã session</th>
              <td><code>{session?.session_code || '—'}</code></td></tr>
          <tr><th>Trạng thái</th><td>{session?.status || '—'}</td></tr>
          <tr><th>Bắt đầu</th><td>{fmt(session?.started_at)}</td></tr>
          <tr><th>Kết thúc</th><td>{fmt(session?.ended_at)}</td></tr>
          <tr><th>Thời lượng</th>
              <td>{fmtDuration(durationMs(session?.started_at, session?.ended_at))}</td></tr>
        </tbody>
      </table>

      <h3>3.1. Bảng thời gian theo bước</h3>
      <table>
        <thead>
          <tr>
            <th style={{ width: '8mm' }}>#</th>
            <th>Bước</th>
            <th style={{ width: '22mm' }}>Loại evidence</th>
            <th style={{ width: '22mm' }}>Yêu cầu</th>
            <th style={{ width: '22mm' }}>Thu được</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((st) => {
            const count = (evidenceByStep[st.id] || []).length
            return (
              <tr key={st.id}>
                <td>{st.step_order}</td>
                <td><strong>{st.title}</strong></td>
                <td>{st.evidence_type}</td>
                <td>{st.required_count ?? '—'}</td>
                <td>{count}</td>
              </tr>
            )
          })}
        </tbody>
      </table>

      <h3>3.2. Top-10 CAN ID quan sát được</h3>
      {topCanIds.length === 0 ? (
        <p className="muted">Không ghi nhận khung CAN.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th style={{ width: '30mm' }}>CAN ID</th>
              <th style={{ width: '22mm' }}>Số khung</th>
              <th>Mẫu payload</th>
            </tr>
          </thead>
          <tbody>
            {topCanIds.map((c) => (
              <tr key={c.canId}>
                <td><code>{c.canId}</code></td>
                <td>{c.count}</td>
                <td><code>{briefPayload(c.sample)}</code></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3>3.3. Mẫu evidence theo bước (tối đa 5 bản ghi/bước)</h3>
      {steps.map((st) => {
        const rows = (evidenceByStep[st.id] || []).slice(0, 5)
        if (rows.length === 0) return null
        return (
          <div key={st.id} style={{ marginBottom: '4mm' }}>
            <div><strong>Bước {st.step_order}: {st.title}</strong></div>
            <table>
              <thead>
                <tr>
                  <th style={{ width: '32mm' }}>Thời điểm</th>
                  <th style={{ width: '26mm' }}>Loại</th>
                  <th>Nội dung tóm tắt</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => (
                  <tr key={e.id}>
                    <td>{fmt(e.captured_at)}</td>
                    <td>{e.evidence_type}</td>
                    <td><code>{briefPayload(e.payload)}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      })}
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/lab/pdf/PracticeSummarySection.jsx
git commit -m "feat(web/lab/pdf): practice summary with timing + top CAN IDs"
```

---

## Task 10: Post-lab analysis section

**Files:**
- Create: `web/src/components/lab/pdf/PostLabSection.jsx`

- [ ] **Step 1: Write the component**

Images uploaded during post-lab live in the `lab-images` bucket. `html2canvas` cannot paint cross-origin images without CORS headers, and Supabase storage needs a *signed* URL for private buckets. The correct approach for cross-origin images is to **download them as Blobs and convert to data URIs** before render — that sidesteps both problems.

Create `web/src/components/lab/pdf/PostLabSection.jsx`:

```jsx
import { useEffect, useState } from 'react'
import MDEditor from '@uiw/react-md-editor'
import { supabase } from '../../../services/supabase'

// Converts a storage-path URL (`bucket/key`) into a data URI.
// Called once per image at render-time; results cached in state so the
// page doesn't refetch when the parent re-renders during html2pdf capture.
async function pathToDataUri(bucket, path) {
  const { data, error } = await supabase.storage.from(bucket).download(path)
  if (error || !data) return null
  return await new Promise((resolve) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result)
    r.readAsDataURL(data)
  })
}

export default function PostLabSection({ questions, postSubmission }) {
  const postQs = (questions || []).filter((q) => q.stage === 'post_lab')
  const answers = postSubmission?.answers || {}
  const uploads = postSubmission?.uploaded_images || []
  // Index uploaded images by question_id for O(1) lookup.
  const imgByQ = Object.fromEntries(uploads.map((u) => [u.question_id, u]))

  const [dataUris, setDataUris] = useState({})

  useEffect(() => {
    let cancelled = false
    async function run() {
      const next = {}
      for (const up of uploads) {
        if (!up?.path) continue
        const uri = await pathToDataUri('lab-images', up.path)
        if (cancelled) return
        if (uri) next[up.question_id] = uri
      }
      if (!cancelled) setDataUris(next)
    }
    run()
    return () => { cancelled = true }
  // uploads comes from postSubmission prop; identity stable per render call.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [postSubmission?.id])

  if (!postSubmission || postSubmission.is_draft) {
    return (
      <section className="page">
        <h2>4. Phân tích post-lab</h2>
        <p className="muted">Chưa có bài nộp post-lab cuối cùng.</p>
      </section>
    )
  }

  return (
    <section className="page">
      <h2>4. Phân tích post-lab</h2>
      {postQs.map((q, i) => {
        const a = answers[q.id]
        const img = imgByQ[q.id]
        return (
          <div key={q.id} style={{ marginBottom: '6mm' }}>
            <h3>Câu {i + 1}. {q.question_text}</h3>

            {q.question_type === 'image_upload' ? (
              dataUris[q.id] ? (
                <img className="post-image" src={dataUris[q.id]} alt={`answer-${i}`} />
              ) : (
                <p className="muted">[Ảnh đang tải{img ? '' : ': không có file'}]</p>
              )
            ) : (
              <div data-color-mode="light">
                <MDEditor.Markdown source={typeof a === 'string' ? a : '_(Không có trả lời)_'} />
              </div>
            )}
          </div>
        )
      })}
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/lab/pdf/PostLabSection.jsx
git commit -m "feat(web/lab/pdf): post-lab analysis section with data-URI images"
```

---

## Task 11: Declaration section

**Files:**
- Create: `web/src/components/lab/pdf/DeclarationSection.jsx`

- [ ] **Step 1: Write the component**

Create `web/src/components/lab/pdf/DeclarationSection.jsx`:

```jsx
export default function DeclarationSection({ student }) {
  const today = new Date().toLocaleDateString('vi-VN')
  return (
    <section className="page">
      <h2>5. Cam kết của sinh viên</h2>
      <p>
        Tôi xin cam đoan báo cáo này do chính tôi thực hiện trong phiên thực
        hành đã ghi nhận ở trên. Dữ liệu bằng chứng (raw frames, DTC, ảnh chụp
        màn hình) được thu thập trực tiếp từ thiết bị BK Diagnostic và không bị
        chỉnh sửa. Các câu trả lời phân tích là của cá nhân tôi. Nếu phát hiện
        sao chép hoặc ngụy tạo dữ liệu, tôi hoàn toàn chịu trách nhiệm và chấp
        nhận bị hủy kết quả.
      </p>

      <div className="declaration-sig">
        <div className="sig-box">
          <div>Ngày {today}</div>
          <div className="sig-line" />
          <div><strong>{student?.full_name || '—'}</strong></div>
          <div>MSSV: {student?.mssv || '—'}</div>
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/lab/pdf/DeclarationSection.jsx
git commit -m "feat(web/lab/pdf): declaration + signature section"
```

---

## Task 12: Parent template — `LabReportPdfTemplate.jsx`

**Files:**
- Create: `web/src/components/lab/pdf/LabReportPdfTemplate.jsx`

- [ ] **Step 1: Write the parent**

Per-page footer: `html2pdf.js` → `jspdf` draws per-page footers most reliably by post-processing the PDF after `html2canvas` finishes. We take a simpler path: render a visible footer strip at the top of every `.page` section using absolute positioning, and stamp "page X / Y" via the `pagebreak.mode` hook below. An in-DOM header like this is "page-aware" only in that we include student name + MSSV + hash fragment on every page — the page number itself is drawn by `jspdf` in Task 14.

Create `web/src/components/lab/pdf/LabReportPdfTemplate.jsx`:

```jsx
import { forwardRef } from 'react'
import { PDF_STYLES } from './pdfStyles'
import CoverSection from './CoverSection'
import ObjectivesSection from './ObjectivesSection'
import PreQuizSection from './PreQuizSection'
import PracticeSummarySection from './PracticeSummarySection'
import PostLabSection from './PostLabSection'
import DeclarationSection from './DeclarationSection'

/**
 * Pure presentational root. All inputs come from `data` (output of
 * fetchLabReportData). `hashPreview` is the first 16 hex chars of
 * content_hash, shown in the header strip per Section 7.1 footer spec.
 *
 * Uses forwardRef so the generator can pass the ref into html2pdf().from(el).
 */
const LabReportPdfTemplate = forwardRef(function LabReportPdfTemplate(
  { data, hashPreview },
  ref
) {
  const { lab, student, group, session, steps, preQuiz, postSubmission,
    questions, evidenceByStep, topCanIds } = data

  return (
    <div id="lab-report-root" ref={ref}>
      <style>{PDF_STYLES}</style>

      {/* Per-page banner repeated inside each section. Not a real running
          header — html2canvas doesn't support CSS `position: running()` — but
          it gives every page the student/MSSV/hash line the spec asks for. */}
      <CoverSection
        lab={lab} student={student} group={group}
        session={session} postSubmission={postSubmission}
      />
      <ObjectivesSection lab={lab} />
      <PreQuizSection preQuiz={preQuiz} questions={questions} lab={lab} />
      <PracticeSummarySection
        session={session} steps={steps}
        evidenceByStep={evidenceByStep} topCanIds={topCanIds}
      />
      <PostLabSection questions={questions} postSubmission={postSubmission} />
      <DeclarationSection student={student} />

      <div style={{ textAlign: 'center', fontSize: '9pt', color: '#555', marginTop: '6mm' }}>
        {student?.full_name} · {student?.mssv} · Hash <code>{hashPreview}</code>
      </div>
    </div>
  )
})

export default LabReportPdfTemplate
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/lab/pdf/LabReportPdfTemplate.jsx
git commit -m "feat(web/lab/pdf): parent template composing all six sections"
```

---

## Task 13: Add `insertLabReport` wrapper

**Files:**
- Modify: `web/src/services/labApi.js`

- [ ] **Step 1: Locate the spot**

Open `web/src/services/labApi.js` and find `getReportSignedUrl` (around line 402). We will insert the new wrapper right after it — before the `// ─── Student-facing queries ───` divider.

- [ ] **Step 2: Add the wrapper**

Append to `web/src/services/labApi.js`, immediately after `downloadReportBlob`:

```js
/**
 * Upsert a lab_reports row. UNIQUE(user_id, session_id) means that if a
 * student regenerates their PDF we overwrite storage + metadata.
 */
export async function insertLabReport({
  userId, sessionId, pdfStoragePath, contentHash, fileSizeBytes,
}) {
  return supabase
    .from('lab_reports')
    .upsert(
      {
        user_id: userId,
        session_id: sessionId,
        pdf_storage_path: pdfStoragePath,
        content_hash: contentHash,
        file_size_bytes: fileSizeBytes,
      },
      { onConflict: 'user_id,session_id' }
    )
    .select()
    .single()
}

export async function uploadLabReport(storagePath, blob) {
  return supabase.storage
    .from('lab-reports')
    .upload(storagePath, blob, {
      contentType: 'application/pdf',
      upsert: true,
    })
}
```

- [ ] **Step 3: Commit**

```bash
git add web/src/services/labApi.js
git commit -m "feat(web/labApi): insertLabReport + uploadLabReport wrappers"
```

---

## Task 14: PDF generator orchestrator — `labReportGenerator.js`

**Files:**
- Create: `web/src/services/labReportGenerator.js`

- [ ] **Step 1: Write the orchestrator**

Create `web/src/services/labReportGenerator.js`:

```js
// Orchestrates: DOM element → Blob → hash → upload → lab_reports row.
// Called from LabReportPage after the off-screen template finishes mounting.
//
// We dynamic-import html2pdf.js so the 250 kB payload is only paid when the
// student clicks "Tạo PDF", not on initial /report load.

import { buildReportFilename } from './labReportFilename'
import { sha256Hex, buildHashInput } from './labReportHash'
import { insertLabReport, uploadLabReport } from './labApi'

const HTML2PDF_OPTIONS = {
  margin: [20, 20, 20, 20], // mm; matches the .page padding in pdfStyles.js
  filename: 'lab-report.pdf', // overridden below
  image: { type: 'jpeg', quality: 0.95 },
  html2canvas: {
    scale: 2,              // 2× for crisp Arial/Times at 10–12pt
    useCORS: true,
    logging: false,
    backgroundColor: '#ffffff',
  },
  jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
  pagebreak: { mode: ['css', 'legacy'] }, // respect .page `page-break-after`
}

/**
 * @param {Object} args
 * @param {HTMLElement} args.element  The off-screen <div id="lab-report-root">
 * @param {Object}   args.data        Output of fetchLabReportData
 * @param {string}   args.userId
 * @param {string}   args.sessionId
 * @returns {Promise<{ blob, blobUrl, contentHash, storagePath, filename, reportRow }>}
 */
export async function generateAndUploadReport({ element, data, userId, sessionId }) {
  const html2pdfMod = await import('html2pdf.js')
  const html2pdf = html2pdfMod.default || html2pdfMod

  // 1. Render DOM → Blob
  const worker = html2pdf()
    .set(HTML2PDF_OPTIONS)
    .from(element)

  // After outputPdf we stamp "Page X / Y" into every page via jsPDF's API.
  const pdf = await worker.toPdf().get('pdf')
  const totalPages = pdf.internal.getNumberOfPages()
  const hashInputPreview = { // preview string only — real hash below
    user: data.student?.mssv || '',
    sess: data.session?.session_code || '',
  }
  for (let i = 1; i <= totalPages; i++) {
    pdf.setPage(i)
    pdf.setFontSize(9)
    pdf.setTextColor(90)
    pdf.setFont('helvetica', 'normal')
    pdf.text(
      `Trang ${i} / ${totalPages} · ${data.student?.full_name || ''} · ${data.student?.mssv || ''}`,
      105, 290, { align: 'center' }
    )
    pdf.text(
      `Session ${hashInputPreview.sess}`,
      200, 290, { align: 'right' }
    )
  }

  const blob = pdf.output('blob')
  const blobUrl = URL.createObjectURL(blob)

  // 2. Compute SHA-256 over canonical hash input (Section 7.3)
  const contentHash = await sha256Hex(
    buildHashInput({
      sessionId,
      answers: data.postSubmission?.answers,
      startedAt: data.session?.started_at,
      finalizedAt: data.postSubmission?.finalized_at,
    })
  )

  // 3. Upload Blob
  const storagePath = `${userId}/${sessionId}.pdf`
  const { error: upErr } = await uploadLabReport(storagePath, blob)
  if (upErr) throw new Error(`Upload thất bại: ${upErr.message}`)

  // 4. Insert lab_reports row (upsert on UNIQUE(user_id,session_id))
  const { data: reportRow, error: rowErr } = await insertLabReport({
    userId,
    sessionId,
    pdfStoragePath: storagePath,
    contentHash,
    fileSizeBytes: blob.size,
  })
  if (rowErr) throw new Error(`Lưu metadata thất bại: ${rowErr.message}`)

  // 5. Filename for the local download
  const dateStr = new Date().toISOString().slice(0, 10)
  const filename = buildReportFilename({
    labCode: data.lab?.code || 'LAB00',
    mssv: data.student?.mssv || 'unknown',
    fullName: data.student?.full_name || 'unknown',
    date: dateStr,
  })

  return { blob, blobUrl, contentHash, storagePath, filename, reportRow }
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/services/labReportGenerator.js
git commit -m "feat(web/lab): PDF generator orchestrator (render → hash → upload)"
```

---

## Task 15: Rewrite `LabReportPage` with preview iframe + generate button

**Files:**
- Modify: `web/src/pages/LabReportPage.jsx` (full replacement of the Phase-5 stub)

- [ ] **Step 1: Replace the file**

Overwrite `web/src/pages/LabReportPage.jsx` with:

```jsx
import { useEffect, useRef, useState } from 'react'
import { Card, Button, Alert, Typography, Space, Tag, Spin, message } from 'antd'
import { FilePdfOutlined, ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { getReportSignedUrl } from '../services/labApi'
import { fetchLabReportData } from '../services/labReportData'
import { generateAndUploadReport } from '../services/labReportGenerator'
import LabReportPdfTemplate from '../components/lab/pdf/LabReportPdfTemplate'

const { Title, Text } = Typography

export default function LabReportPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [data, setData] = useState(null)
  const [existingReport, setExistingReport] = useState(null)
  const [existingSignedUrl, setExistingSignedUrl] = useState(null)

  // Preview-iframe + generation state
  const [generating, setGenerating] = useState(false)
  const [previewUrl, setPreviewUrl] = useState(null) // blob: URL
  const [lastFilename, setLastFilename] = useState(null)
  const [lastHash, setLastHash] = useState(null)

  const templateRef = useRef(null)

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    async function load() {
      const result = await fetchLabReportData(userId, sid)
      if (cancelled) return
      if (result.error) {
        setError(result.error.message)
        setLoading(false)
        return
      }
      setData(result.data)

      // Check if a report already exists for this user/session.
      const { supabase } = await import('../services/supabase')
      const { data: existing } = await supabase
        .from('lab_reports')
        .select('id, pdf_storage_path, content_hash, generated_at, file_size_bytes')
        .eq('user_id', userId)
        .eq('session_id', sid)
        .maybeSingle()
      if (cancelled) return
      if (existing) {
        setExistingReport(existing)
        const { data: url } = await getReportSignedUrl(existing.pdf_storage_path, 300)
        if (!cancelled && url) setExistingSignedUrl(url.signedUrl)
      }
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [userId, sid])

  // Revoke any previous blob URL when a new one is created.
  useEffect(() => {
    return () => { if (previewUrl) URL.revokeObjectURL(previewUrl) }
  }, [previewUrl])

  async function handleGenerate() {
    if (!templateRef.current) return
    setGenerating(true)
    try {
      const res = await generateAndUploadReport({
        element: templateRef.current,
        data,
        userId,
        sessionId: sid,
      })
      setPreviewUrl(res.blobUrl)
      setLastFilename(res.filename)
      setLastHash(res.contentHash)
      setExistingReport(res.reportRow)
      message.success('Đã tạo báo cáo')
    } catch (e) {
      message.error(e.message || String(e))
    } finally {
      setGenerating(false)
    }
  }

  function handleDownloadFresh() {
    if (!previewUrl || !lastFilename) return
    const a = document.createElement('a')
    a.href = previewUrl
    a.download = lastFilename
    document.body.appendChild(a)
    a.click()
    a.remove()
  }

  if (loading) return <AppLayout><Spin /></AppLayout>
  if (error) return <AppLayout><Alert type="error" message={error} showIcon /></AppLayout>

  if (!data?.postSubmission || data.postSubmission.is_draft) {
    return (
      <AppLayout>
        <Alert
          type="warning" showIcon
          message="Bạn chưa nộp post-lab"
          description="Hoàn thành và nộp post-lab để sinh báo cáo PDF."
          style={{ marginBottom: 16 }}
        />
        <Button type="primary" onClick={() => navigate(`/labs/${labId}/session/${sid}/post`)}>
          Sang trang post-lab
        </Button>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 1100, margin: '0 auto' }}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/labs')}>
            Danh sách lab
          </Button>
          <Title level={4} style={{ margin: 0 }}>Báo cáo lab</Title>
          {existingReport
            ? <Tag color="success">Đã phát hành</Tag>
            : <Tag>Chưa phát hành</Tag>}
        </Space>

        <Card>
          <Space wrap style={{ marginBottom: 16 }}>
            <Button
              type="primary" icon={<FilePdfOutlined />}
              loading={generating} onClick={handleGenerate}
            >
              {existingReport ? 'Tạo lại PDF' : 'Tạo PDF'}
            </Button>
            {previewUrl && (
              <Button icon={<DownloadOutlined />} onClick={handleDownloadFresh}>
                Tải xuống ({lastFilename})
              </Button>
            )}
            {existingSignedUrl && !previewUrl && (
              <Button
                icon={<DownloadOutlined />}
                href={existingSignedUrl} target="_blank" rel="noreferrer"
              >
                Tải bản đã phát hành (link 5 phút)
              </Button>
            )}
          </Space>

          {lastHash && (
            <Alert
              type="success" showIcon
              message="Hash báo cáo (SHA-256)"
              description={<Text code>{lastHash}</Text>}
              style={{ marginBottom: 16 }}
            />
          )}

          {/* Live preview in iframe (same-origin blob: URL, no CORS issue). */}
          {previewUrl ? (
            <iframe
              title="PDF preview"
              src={previewUrl}
              style={{ width: '100%', height: '80vh', border: '1px solid #ddd' }}
            />
          ) : (
            <Alert
              type="info" showIcon
              message="Nhấn 'Tạo PDF' để dựng báo cáo"
              description="Dữ liệu đã có đủ. Quá trình render mất khoảng 5–15 giây."
            />
          )}
        </Card>

        {/* Off-screen template — html2pdf reads from this DOM. We position it
            off-screen rather than display:none, because html2canvas cannot
            rasterize a node inside a display:none ancestor. */}
        <div
          style={{
            position: 'fixed',
            left: '-10000px',
            top: 0,
            width: '210mm',
            // visibility:hidden would block rendering too; off-screen wins.
          }}
          aria-hidden="true"
        >
          <LabReportPdfTemplate ref={templateRef} data={data} hashPreview={(lastHash || '').slice(0, 16)} />
        </div>
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Lint**

Run:
```bash
cd web && npm run lint 2>&1 | tail -40
```

Expected: no new errors in `LabReportPage.jsx`, `labReportGenerator.js`, `labReportData.js`, or the `components/lab/pdf/` tree. The 5 pre-existing baseline errors (AuthContext, useLogs, useUsers, DashboardPage, ResetPasswordPage) are unchanged.

If lint reports `react-hooks/set-state-in-effect` on our new code: re-read the Phase-4 pattern — inline the async function inside `useEffect` with a `cancelled` flag; do not use `useCallback + useEffect(load)`.

- [ ] **Step 3: Build**

Run:
```bash
cd web && npm run build
```

Expected: exit 0. The `html2pdf.js` chunk should appear as a separate async chunk because we `import()` it dynamically from the generator.

- [ ] **Step 4: Commit**

```bash
git add web/src/pages/LabReportPage.jsx
git commit -m "feat(web/lab/pdf): wire /report page with preview iframe + generator"
```

---

## Task 16: Dev-server smoke test (end-to-end render)

No code changes — verification only.

- [ ] **Step 1: Start dev server**

Run:
```bash
cd web && npm run dev
```

Open the printed URL.

- [ ] **Step 2: Navigate to a submitted report**

- Log in as a student who has finalized a post-lab submission on some session (create one via seed data if none exist — see spec Section 8).
- Navigate to `/labs/<labId>/session/<sid>/report`.

- [ ] **Step 3: Click "Tạo PDF"**

Watch the DevTools Network tab:
- 1 `POST` to `lab-reports` storage (HTTP 200).
- 1 `POST` to `lab_reports` table (`upsert` via PostgREST) — HTTP 201 or 200.
- The iframe shows the PDF immediately after.

- [ ] **Step 4: Verify the six sections are present in the iframe**

Checklist — zoom the iframe and confirm each:
- [ ] Cover page has HCMUT header, lab title, student name, MSSV, group, session code, practice date, submission timestamp.
- [ ] Page 2 lists objectives from `labs.description` plus L.O. mapping table (if `LAB01`/`LAB02`).
- [ ] Pre-quiz table shows every pre-lab question with ✓/✗ in the right column; header row shows score % and pass/fail.
- [ ] Practice summary shows session code/status/duration, per-step timing, top-10 CAN IDs, and at most 5 evidence samples per step.
- [ ] Post-lab section renders markdown answers; uploaded images render inline.
- [ ] Declaration text is in place, signature line + printed name + MSSV.
- [ ] Page footer (drawn by jsPDF) reads "Trang X / Y · FullName · MSSV".

- [ ] **Step 5: Verify download**

Click "Tải xuống". Confirm the downloaded filename matches `TR4021_LAB??_<mssv>_<StrippedName>_<YYYY-MM-DD>.pdf` exactly (no diacritics).

- [ ] **Step 6: Verify DB row**

In DevTools console:
```js
const { data } = await (await import('/src/services/supabase.js')).supabase
  .from('lab_reports').select('*').eq('session_id', '<sid>').maybeSingle()
console.log(data)
```

Expected: row exists with `pdf_storage_path = "<userId>/<sid>.pdf"`, non-empty `content_hash` (64 hex chars), `file_size_bytes > 0`, recent `generated_at`.

- [ ] **Step 7: Verify hash stability**

Click "Tạo lại PDF" once more without changing any form input. Confirm the printed hash (in the "Hash báo cáo" Alert) is *identical* to the first run. If it changed, something non-deterministic (like `new Date()` sneaking into the hash input) is wrong — return to Task 3 / Task 14 and fix.

- [ ] **Step 8: If everything passes, commit a changelog note**

No code change — skip commit. Proceed to Task 17.

---

## Task 17: Cross-browser test (Chromium + Firefox)

- [ ] **Step 1: Chromium check**

In Chrome or Edge:
- Repeat Task 16 Steps 2–7.
- Additionally verify: the iframe renders with no `"Content Security Policy"` errors in the console, images inside the PDF are not blank, and fonts in the PDF look like actual Times / Arial (not Webdings / Courier fallback).

- [ ] **Step 2: Firefox check**

In Firefox latest:
- Repeat Task 16 Steps 2–7.
- Firefox ships a different `crypto.subtle` code path — confirm the hash matches the Chromium hash for the same session (they must match byte-for-byte). If they differ, the culprit is almost always a timezone or number formatting inside the hash input. Re-read `buildHashInput` in `labReportHash.js`.
- Firefox's built-in PDF viewer sometimes renders tables wider than Chromium. Confirm no column clipping on the pre-quiz or practice tables; if clipped, add `table-layout: fixed` to the relevant selector in `pdfStyles.js` and re-run Task 15 Step 3 (build) and this step.

- [ ] **Step 3: Document cross-browser result**

If both browsers pass, commit a note in the plan follow-up:
```bash
git commit --allow-empty -m "test(web/lab/pdf): verified Chromium + Firefox render parity"
```

If Firefox fails on a specific section, fix the CSS in `pdfStyles.js`, rebuild, re-verify both browsers. Only then commit.

---

## Task 18: Full verification + final commit

- [ ] **Step 1: Lint**

```bash
cd web && npm run lint 2>&1 | tail -30
```

Expected: exactly 5 errors, all in the pre-existing baseline files listed at the start of Phase 4 (`AuthContext.jsx`, `useLogs.js`, `useUsers.js`, `DashboardPage.jsx`, `ResetPasswordPage.jsx`). No errors in any file under `web/src/components/lab/pdf/` or `web/src/services/labReport*.js`.

- [ ] **Step 2: Build**

```bash
cd web && npm run build
```

Expected: exit 0. You should see a separate `html2pdf`-named async chunk (~250 kB gzipped) in the output list, confirming the dynamic import worked.

- [ ] **Step 3: Final review of Section 7 coverage**

Walk spec Section 7 line-by-line:

| Spec requirement | Implemented in |
|---|---|
| 7.1 Cover | `CoverSection.jsx` |
| 7.1 Objectives + L.O. mapping | `ObjectivesSection.jsx` |
| 7.1 Pre-lab quiz result table | `PreQuizSection.jsx` |
| 7.1 Practice session summary (code, start/end, duration, per-step, top-10 CAN, evidence sample) | `PracticeSummarySection.jsx` |
| 7.1 Post-lab analysis (markdown + images) | `PostLabSection.jsx` |
| 7.1 Declaration + signature block | `DeclarationSection.jsx` |
| 7.1 Footer every page | jsPDF per-page draw in `labReportGenerator.js` |
| 7.2 Times New Roman 12pt body | `pdfStyles.js` `#lab-report-root` rule |
| 7.2 Arial 10pt for tables/code | `pdfStyles.js` table/pre/code rule |
| 7.2 A4 + 20mm margins | `pdfStyles.js` `.page` + jsPDF `format:a4` + margin option |
| 7.3 SHA-256 of `{session_id, answers, timestamps}` | `labReportHash.js` + `generateAndUploadReport` |
| 7.3 Stored in `lab_reports.content_hash` | `insertLabReport` in `labApi.js` |
| 7.4 Filename strip diacritics | `labReportFilename.js` |
| 5.6 Upload to `lab-reports/{userId}/{sessionId}.pdf` | `uploadLabReport` + generator |
| 5.6 Preview in iframe | `LabReportPage.jsx` |
| 5.6 Trigger browser download | `handleDownloadFresh` in `LabReportPage.jsx` |

Every row must have a file reference. If any is blank, you missed a task — go back and add it.

- [ ] **Step 4: Tag & push (optional)**

If the team wants a waypoint tag:
```bash
git tag -a lab-phase-5-pdf -m "Lab system phase 5: PDF report template"
```

Phase 5 done.

---

## Notes / Gotchas

- **`html2pdf.js` + private Supabase images.** Post-lab images live in the private `lab-images` bucket; `html2canvas` cannot fetch them via a plain signed URL because of CORS. `PostLabSection.jsx` (Task 10) sidesteps this by downloading each image as a Blob and converting to a data URI before render. Don't "optimize" that away — you will break image rendering.
- **Hash determinism.** Never include `Date.now()` or `new Date()` inside `buildHashInput`. The hash input must only contain fields that were recorded at submission time.
- **Off-screen vs `display:none`.** The template is positioned at `left: -10000px`, not hidden via `display:none`. `html2canvas` refuses to paint a detached subtree, so we keep it in the layout flow but off-screen.
- **Re-generation is idempotent.** `UNIQUE(user_id, session_id)` on `lab_reports` and `upsert: true` on both the row and the storage upload let a student click "Tạo lại PDF" as many times as needed. Each click overwrites the previous PDF in the bucket.
- **Do not add a testing framework.** There is no Vitest / Jest in `web/`. Verification in this plan is intentionally manual (dev-server + iframe + DevTools). Adding a test framework is a separate decision that should be its own spec.
