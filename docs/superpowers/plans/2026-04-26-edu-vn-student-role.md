# Edu.vn Student Role + Instructor Role + MSSV Signup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phân biệt sinh viên (`.edu.vn`) với user phổ thông và giảng viên — gate `/labs` cho student+, mở `/teach` cho instructor+, conditional MSSV input ở RegisterPage. Spec: `docs/superpowers/specs/2026-04-26-edu-vn-student-role-design.md`.

**Architecture:** Hybrid (client UX layer + DB authority). Trigger `handle_new_user` derive role server-side từ email pattern; client chỉ hint UX và pass MSSV trong signup metadata. Route guards mới (`StudentRoute`, `TeachRoute`) replace `ProtectedRoute` cho lab routes.

**Tech Stack:** React 19 · Vite 8 · Ant Design 6 · React Router 7 · Supabase Postgres · PostgREST RPC

---

## Setup

### Task S0: Verify branch + working state

**Files:** Không

- [ ] **Step 1: Confirm trên branch feature/edu-vn-student-role**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git branch --show-current
```

Expected: `feature/edu-vn-student-role`

- [ ] **Step 2: Verify spec đã commit**

```bash
git log --oneline -3
```

Expected: thấy commit `docs(specs): edu.vn student role + instructor role + MSSV signup`.

- [ ] **Step 3: Verify build hiện tại pass**

```bash
cd web
npm run build
```

Expected: built thành công, không error.

---

## PHASE 1 — Database Migration

Mục tiêu: Tạo file migration idempotent, áp dụng lên Supabase, verify trigger phân biệt role theo email.

### Task 1.1: Tạo migration SQL file

**Files:**
- Create: `2026-04-26-edu-vn-student-role.sql` (repo root, theo pattern `mssv_migration.sql`)

- [ ] **Step 1: Tạo file migration**

Tạo `C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\2026-04-26-edu-vn-student-role.sql` với nội dung:

```sql
-- =============================================================
-- Migration: edu.vn student role + instructor role + MSSV signup
-- Date: 2026-04-26
-- Spec: docs/superpowers/specs/2026-04-26-edu-vn-student-role-design.md
--
-- Idempotent — có thể chạy lại nhiều lần an toàn.
-- =============================================================

-- 1. Apply MSSV columns + immutability (idempotent với mssv_migration.sql)
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS mssv TEXT DEFAULT NULL;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS full_name TEXT DEFAULT NULL;

DROP TRIGGER IF EXISTS enforce_mssv_immutable ON profiles;

CREATE OR REPLACE FUNCTION fn_enforce_mssv_immutable()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.mssv IS NOT NULL AND OLD.mssv IS DISTINCT FROM NEW.mssv THEN
    RAISE EXCEPTION 'mssv is immutable once set';
  END IF;
  IF OLD.full_name IS NOT NULL AND OLD.full_name IS DISTINCT FROM NEW.full_name THEN
    RAISE EXCEPTION 'full_name is immutable once set';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_mssv_immutable
BEFORE UPDATE ON profiles
FOR EACH ROW EXECUTE FUNCTION fn_enforce_mssv_immutable();

-- 2. RPC update_profile_fields (cho phép user tự cập nhật MSSV/full_name khi đang null)
CREATE OR REPLACE FUNCTION update_profile_fields(p_mssv TEXT, p_full_name TEXT)
RETURNS profiles AS $$
DECLARE
  result profiles;
BEGIN
  UPDATE profiles
  SET mssv = COALESCE(mssv, NULLIF(p_mssv, '')),
      full_name = COALESCE(full_name, NULLIF(p_full_name, ''))
  WHERE id = auth.uid()
  RETURNING * INTO result;
  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Mở rộng role check constraint
ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_role_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_role_check
  CHECK (role IN ('admin','moderator','instructor','student','user','guest'));

-- 4. Replace handle_new_user trigger để derive role từ email + pickup MSSV
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
  derived_role TEXT;
  signup_mssv TEXT;
  signup_username TEXT;
BEGIN
  IF NEW.email ILIKE '%.edu.vn' THEN
    derived_role := 'student';
  ELSE
    derived_role := 'user';
  END IF;

  signup_mssv := NULLIF(NEW.raw_user_meta_data->>'mssv', '');
  signup_username := COALESCE(
    NEW.raw_user_meta_data->>'username',
    split_part(NEW.email, '@', 1)
  );

  INSERT INTO profiles (id, username, role, status, mssv)
  VALUES (NEW.id, signup_username, derived_role, 'active', signup_mssv);

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger on_auth_user_created đã tồn tại từ setup.sql, chỉ replace function body
-- Nếu vì lý do nào đó trigger chưa có, uncomment dòng dưới:
-- CREATE TRIGGER on_auth_user_created
--   AFTER INSERT ON auth.users
--   FOR EACH ROW EXECUTE FUNCTION handle_new_user();
```

- [ ] **Step 2: Verify SQL syntax bằng Supabase SQL editor preview**

(Bước này là manual, không tự động được — sẽ chạy ở Task 1.2.)

- [ ] **Step 3: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add 2026-04-26-edu-vn-student-role.sql
git commit -m "feat(db): edu.vn student role migration + MSSV idempotent"
```

---

### Task 1.2: Apply migration lên Supabase (USER MANUAL)

**Files:** Không (chỉ thao tác Supabase Dashboard)

- [ ] **Step 1: Mở Supabase project SQL editor**

Mở `https://supabase.com/dashboard/project/ylspcqbwupnqskqemmiv/sql/new` (hoặc URL project hiện tại).

- [ ] **Step 2: Paste file `2026-04-26-edu-vn-student-role.sql` và Run**

Paste toàn bộ nội dung file, click **Run**. Expected: `Success. No rows returned`.

- [ ] **Step 3: Verify role check constraint**

Tab Table Editor → `profiles` → Definition → kéo xuống thấy `profiles_role_check` mới có 6 giá trị. Hoặc query:

```sql
SELECT pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conname = 'profiles_role_check';
```

Expected: `CHECK (role IN ('admin','moderator','instructor','student','user','guest'))`.

- [ ] **Step 4: Verify columns mssv + full_name tồn tại**

```sql
\d profiles
```

Hoặc Table Editor xem cột `mssv` (text, nullable) và `full_name` (text, nullable).

---

### Task 1.3: Smoke test trigger handle_new_user

**Files:** Không (chỉ test qua Supabase)

- [ ] **Step 1: Tạo test account .edu.vn qua SQL**

Trong SQL editor:

```sql
-- Tạo auth user giả lập (chỉ test trong dev, không làm production)
INSERT INTO auth.users (id, email, raw_user_meta_data)
VALUES (
  gen_random_uuid(),
  'test_student_001@hcmut.edu.vn',
  '{"username": "test_student_001", "mssv": "2052345"}'::jsonb
);
```

- [ ] **Step 2: Verify profile được tạo với role='student' và mssv set**

```sql
SELECT username, role, mssv, status
FROM profiles
WHERE username = 'test_student_001';
```

Expected: 1 row, `role='student'`, `mssv='2052345'`, `status='active'`.

- [ ] **Step 3: Tạo test account non-.edu.vn**

```sql
INSERT INTO auth.users (id, email, raw_user_meta_data)
VALUES (
  gen_random_uuid(),
  'test_user_002@gmail.com',
  '{"username": "test_user_002"}'::jsonb
);
```

- [ ] **Step 4: Verify role='user', mssv=NULL**

```sql
SELECT username, role, mssv FROM profiles WHERE username = 'test_user_002';
```

Expected: `role='user'`, `mssv=NULL`.

- [ ] **Step 5: Test MSSV immutability**

```sql
UPDATE profiles SET mssv = '9999999' WHERE username = 'test_student_001';
```

Expected: `ERROR: mssv is immutable once set`.

- [ ] **Step 6: Cleanup test data**

```sql
DELETE FROM auth.users WHERE email IN ('test_student_001@hcmut.edu.vn', 'test_user_002@gmail.com');
-- Cascade xoá luôn profiles
```

- [ ] **Step 7: Báo lại "DB migration verified" để tiếp Phase 2**

---

## PHASE 2 — Auth & Signup UI

Mục tiêu: RegisterPage hiện conditional MSSV input khi email `.edu.vn`, pass MSSV vào signup metadata.

### Task 2.1: Update services/auth.js — register accept mssv

**Files:**
- Modify: `web/src/services/auth.js`

- [ ] **Step 1: Update function `register` signature**

Mở `web/src/services/auth.js`, tìm function `register`. Thay thế:

```js
export async function register(email, password, username) {
  return supabase.auth.signUp({ email, password, options: { data: { username } } })
}
```

bằng:

```js
export async function register(email, password, username, mssv) {
  const data = { username }
  if (mssv) data.mssv = mssv
  return supabase.auth.signUp({ email, password, options: { data } })
}
```

- [ ] **Step 2: Verify build pass**

```bash
cd web
npm run build
```

Expected: thành công.

- [ ] **Step 3: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/services/auth.js
git commit -m "feat(auth): register accepts optional mssv in signup metadata"
```

---

### Task 2.2: Update RegisterPage với conditional MSSV input

**Files:**
- Modify: `web/src/pages/RegisterPage.jsx`

- [ ] **Step 1: Đọc RegisterPage.jsx hiện tại để hiểu structure**

```bash
cat web/src/pages/RegisterPage.jsx
```

Note structure: Form từ AntD, có Form.Item cho username/email/password/confirmPassword, có handler `onFinish` gọi `register(email, password, username)`.

- [ ] **Step 2: Thêm import Alert và useWatch**

Trong imports đầu file, đảm bảo có:

```jsx
import { Form, Input, Button, message, Alert } from 'antd'
```

(Nếu `Alert` chưa có, thêm vào.)

- [ ] **Step 3: Thêm Form.useWatch để track email + computed isEduVn**

Trong component RegisterPage, ngay sau khi tạo `[form] = Form.useForm()`, thêm:

```jsx
const email = Form.useWatch('email', form)
const isEduVn = /\.edu\.vn$/i.test(email || '')
```

- [ ] **Step 4: Thêm Alert badge và Form.Item MSSV (conditional)**

Trong JSX, sau `Form.Item name="confirmPassword"` và trước nút Submit, thêm:

```jsx
{isEduVn && (
  <>
    <Alert
      type="info"
      showIcon
      message="Bạn sẽ được gán role Sinh viên"
      description="Email .edu.vn được nhận diện là tài khoản sinh viên. Bạn có thể nhập MSSV ngay hoặc cập nhật sau."
      style={{ marginBottom: 16 }}
    />
    <Form.Item
      name="mssv"
      label="Mã số sinh viên (tùy chọn)"
      rules={[
        { pattern: /^\d{7,8}$/, message: 'MSSV phải là 7-8 chữ số' }
      ]}
    >
      <Input placeholder="VD: 2052345" maxLength={8} />
    </Form.Item>
  </>
)}
```

- [ ] **Step 5: Update onFinish handler để pass MSSV**

Tìm `async function onFinish(values)` (hoặc tương tự). Update lời gọi `register(...)`:

Trước:
```jsx
const { error } = await register(values.email, values.password, values.username)
```

Sau:
```jsx
const { error } = await register(
  values.email,
  values.password,
  values.username,
  values.mssv  // optional, undefined nếu non-.edu.vn
)
```

- [ ] **Step 6: Verify build pass**

```bash
cd web
npm run build
```

Expected: thành công.

- [ ] **Step 7: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/pages/RegisterPage.jsx
git commit -m "feat(auth/register): conditional MSSV input cho email .edu.vn"
```

---

### Task 2.3: Manual test signup flow

**Files:** Không

- [ ] **Step 1: Khởi động dev server**

```bash
cd web
npm run dev
```

Mở `http://localhost:5173/register`.

- [ ] **Step 2: Test case 1 — email .edu.vn + MSSV**

Nhập:
- username: `test_signup_01`
- email: `test01@hcmut.edu.vn`
- password: `Test12345`
- confirm password: `Test12345`

Verify: Alert info hiện ra, Form.Item MSSV xuất hiện. Nhập MSSV `2052345`. Click Đăng ký.

Expected: signup success, redirect login. Verify trong Supabase: `profiles` có row mới `role='student'`, `mssv='2052345'`.

- [ ] **Step 3: Test case 2 — email .edu.vn không nhập MSSV**

Reload `/register`, nhập:
- username: `test_signup_02`
- email: `test02@hcmut.edu.vn`
- password: `Test12345`
- bỏ trống MSSV

Click Đăng ký.

Expected: signup OK. Trong DB: `role='student'`, `mssv=NULL`.

- [ ] **Step 4: Test case 3 — email non-.edu.vn**

Reload `/register`, nhập email `test03@gmail.com`. Verify Alert + MSSV input KHÔNG hiện ra. Submit thành công với `role='user'`.

- [ ] **Step 5: Test case 4 — MSSV sai format**

Reload `/register`, email `.edu.vn`, nhập MSSV `abc` (không phải số). Click Submit.

Expected: client validation chặn, hiển thị lỗi "MSSV phải là 7-8 chữ số".

- [ ] **Step 6: Cleanup test accounts**

Trong Supabase SQL editor:

```sql
DELETE FROM auth.users WHERE email IN (
  'test01@hcmut.edu.vn', 'test02@hcmut.edu.vn', 'test03@gmail.com'
);
```

- [ ] **Step 7: Báo "Phase 2 signup verified" để tiếp Phase 3**

---

## PHASE 3 — Route Guards

Mục tiêu: Tạo `StudentRoute` và `TeachRoute`, áp dụng cho `/labs/*` và `/teach`.

### Task 3.1: Tạo StudentRoute component

**Files:**
- Create: `web/src/components/StudentRoute.jsx`

- [ ] **Step 1: Đọc AdminRoute.jsx hiện tại làm template**

```bash
cat web/src/components/AdminRoute.jsx
```

Lưu structure: import useAuth, check loading/session/role, redirect logic.

- [ ] **Step 2: Tạo file StudentRoute.jsx**

Tạo `C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\web\src\components\StudentRoute.jsx`:

```jsx
import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'

const STUDENT_ROLES = ['student', 'instructor', 'moderator', 'admin']

export default function StudentRoute({ children }) {
  const { session, profile, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!session) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (!STUDENT_ROLES.includes(profile?.role)) {
    return <Navigate to="/dashboard" state={{ deniedReason: 'student-only' }} replace />
  }

  return children
}
```

- [ ] **Step 3: Verify build pass**

```bash
cd web && npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/components/StudentRoute.jsx
git commit -m "feat(routes): add StudentRoute guard for /labs"
```

---

### Task 3.2: Tạo TeachRoute component

**Files:**
- Create: `web/src/components/TeachRoute.jsx`

- [ ] **Step 1: Tạo file TeachRoute.jsx**

Tạo `C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\web\src\components\TeachRoute.jsx`:

```jsx
import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'

const TEACH_ROLES = ['instructor', 'moderator', 'admin']

export default function TeachRoute({ children }) {
  const { session, profile, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!session) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (!TEACH_ROLES.includes(profile?.role)) {
    return <Navigate to="/dashboard" state={{ deniedReason: 'teach-only' }} replace />
  }

  return children
}
```

- [ ] **Step 2: Verify build**

```bash
cd web && npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/components/TeachRoute.jsx
git commit -m "feat(routes): add TeachRoute guard for /teach"
```

---

### Task 3.3: Update App.jsx — wrap /labs/* + thêm /teach

**Files:**
- Modify: `web/src/App.jsx`

- [ ] **Step 1: Đọc App.jsx**

```bash
cat web/src/App.jsx
```

- [ ] **Step 2: Thêm imports**

Thêm sau `import AdminRoute from './components/AdminRoute'`:

```jsx
import StudentRoute from './components/StudentRoute'
import TeachRoute from './components/TeachRoute'
import TeachPage from './pages/TeachPage'
```

(TeachPage chưa có, sẽ tạo ở Task 4.1 — build sẽ fail tạm. Hoàn thành Task 4.1 rồi mới build pass. Hoặc đảo thứ tự: làm Task 4.1 trước, rồi Task 3.3.)

**ĐỀ XUẤT:** Làm Task 4.1 trước Task 3.3 để build không fail giữa chừng.

- [ ] **Step 3: Thay ProtectedRoute bằng StudentRoute cho 6 lab routes**

Tìm và replace từng dòng (6 routes lab):

```jsx
<Route path="/labs" element={<ProtectedRoute><LabsListPage /></ProtectedRoute>} />
<Route path="/labs/:labId" element={<ProtectedRoute><LabOverviewPage /></ProtectedRoute>} />
<Route path="/labs/:labId/session/:sid" element={<ProtectedRoute><LabSessionPage /></ProtectedRoute>} />
<Route path="/labs/:labId/session/:sid/post" element={<ProtectedRoute><LabPostLabPage /></ProtectedRoute>} />
<Route path="/labs/:labId/session/:sid/report" element={<ProtectedRoute><LabReportPage /></ProtectedRoute>} />
<Route path="/my-reports" element={<ProtectedRoute><MyReportsPage /></ProtectedRoute>} />
```

thay tất cả `<ProtectedRoute>` thành `<StudentRoute>`:

```jsx
<Route path="/labs" element={<StudentRoute><LabsListPage /></StudentRoute>} />
<Route path="/labs/:labId" element={<StudentRoute><LabOverviewPage /></StudentRoute>} />
<Route path="/labs/:labId/session/:sid" element={<StudentRoute><LabSessionPage /></StudentRoute>} />
<Route path="/labs/:labId/session/:sid/post" element={<StudentRoute><LabPostLabPage /></StudentRoute>} />
<Route path="/labs/:labId/session/:sid/report" element={<StudentRoute><LabReportPage /></StudentRoute>} />
<Route path="/my-reports" element={<StudentRoute><MyReportsPage /></StudentRoute>} />
```

- [ ] **Step 4: Thêm route /teach**

Thêm dòng mới ngay trước route `/admin`:

```jsx
<Route path="/teach" element={<TeachRoute><TeachPage /></TeachRoute>} />
```

- [ ] **Step 5: Verify build pass (sau khi Task 4.1 đã xong)**

```bash
cd web && npm run build
```

- [ ] **Step 6: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/App.jsx
git commit -m "feat(routes): wrap /labs with StudentRoute, add /teach route"
```

---

## PHASE 4 — TeachPage skeleton

Mục tiêu: Trang `/teach` với 3 tabs. Phase 1 chỉ làm structural; Reports/Groups là placeholder "Coming soon".

### Task 4.1: Tạo TeachPage skeleton

**Files:**
- Create: `web/src/pages/TeachPage.jsx`

- [ ] **Step 1: Tạo file TeachPage.jsx**

Tạo `C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\web\src\pages\TeachPage.jsx`:

```jsx
import { Tabs, Empty, Card } from 'antd'
import { ReadOutlined, FileTextOutlined, TeamOutlined } from '@ant-design/icons'
import AppLayout from '../components/AppLayout'

function LabManagementTab() {
  return (
    <Card>
      <Empty
        image={<ReadOutlined style={{ fontSize: 48, color: '#1565C0' }} />}
        description={
          <>
            <p style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>
              Quản lý nội dung Lab
            </p>
            <p style={{ color: '#6B7280' }}>
              Danh sách lab và editor sẽ được tích hợp ở phase tiếp theo.
            </p>
          </>
        }
      />
    </Card>
  )
}

function StudentReportsTab() {
  return (
    <Card>
      <Empty
        image={<FileTextOutlined style={{ fontSize: 48, color: '#1565C0' }} />}
        description={
          <>
            <p style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>
              Báo cáo của sinh viên
            </p>
            <p style={{ color: '#6B7280' }}>Coming soon.</p>
          </>
        }
      />
    </Card>
  )
}

function GroupsTab() {
  return (
    <Card>
      <Empty
        image={<TeamOutlined style={{ fontSize: 48, color: '#1565C0' }} />}
        description={
          <>
            <p style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>
              Quản lý nhóm Lab
            </p>
            <p style={{ color: '#6B7280' }}>Coming soon.</p>
          </>
        }
      />
    </Card>
  )
}

export default function TeachPage() {
  const items = [
    { key: 'labs',    label: 'Quản lý Lab',         children: <LabManagementTab /> },
    { key: 'reports', label: 'Báo cáo sinh viên',   children: <StudentReportsTab /> },
    { key: 'groups',  label: 'Quản lý nhóm',        children: <GroupsTab /> },
  ]

  return (
    <AppLayout>
      <div style={{ maxWidth: 1200, margin: '0 auto', padding: 24 }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, marginBottom: 8 }}>
          Phần giảng dạy
        </h1>
        <p style={{ color: '#6B7280', marginBottom: 24 }}>
          Tạo và quản lý nội dung Lab, xem báo cáo sinh viên, quản lý nhóm thực hành.
        </p>
        <Tabs defaultActiveKey="labs" items={items} />
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify build (sẽ fail vì App.jsx chưa import — đó là OK, sẽ fix ở Task 3.3)**

Skip build check ở step này, chuyển luôn sang Task 3.3 để wire route.

- [ ] **Step 3: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/pages/TeachPage.jsx
git commit -m "feat(teach): add TeachPage skeleton with 3 tabs (labs/reports/groups)"
```

---

## PHASE 5 — AdminPage role buttons

Mục tiêu: Cho admin promote student → instructor qua AdminPage.

### Task 5.1: Thêm 'instructor' và 'student' vào AdminPage role buttons

**Files:**
- Modify: `web/src/pages/AdminPage.jsx`

- [ ] **Step 1: Đọc AdminPage.jsx, locate ROLE_COLOR + role array**

```bash
grep -n "ROLE_COLOR\|'admin','moderator','user','guest'" web/src/pages/AdminPage.jsx
```

Expected: dòng 17 (ROLE_COLOR), 128 (Option list), 155 (edit modal buttons).

- [ ] **Step 2: Update ROLE_COLOR (dòng 17)**

Thay:
```jsx
const ROLE_COLOR   = { admin: 'purple', moderator: 'blue', user: 'default', guest: 'default' }
```

bằng:
```jsx
const ROLE_COLOR   = { admin: 'purple', moderator: 'blue', instructor: 'cyan', student: 'green', user: 'default', guest: 'default' }
```

- [ ] **Step 3: Update role Option list (~dòng 128)**

Tìm dòng:
```jsx
{['admin','moderator','user','guest'].map(r => <Option key={r} value={r}>{r.charAt(0).toUpperCase() + r.slice(1)}</Option>)}
```

Thay bằng:
```jsx
{['admin','moderator','instructor','student','user','guest'].map(r => <Option key={r} value={r}>{r.charAt(0).toUpperCase() + r.slice(1)}</Option>)}
```

- [ ] **Step 4: Update edit modal role buttons (~dòng 155)**

Tìm dòng:
```jsx
{['user','moderator','admin','guest'].map(r => (
```

Thay bằng:
```jsx
{['user','student','instructor','moderator','admin','guest'].map(r => (
```

- [ ] **Step 5: Verify build**

```bash
cd web && npm run build
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/pages/AdminPage.jsx
git commit -m "feat(admin): add instructor + student to role select buttons"
```

---

## PHASE 6 — AppLayout menu items

Mục tiêu: Hiển thị menu Lab/Teach/Admin theo role.

### Task 6.1: Update AppLayout menu items theo role

**Files:**
- Modify: `web/src/components/AppLayout.jsx`

- [ ] **Step 1: Đọc AppLayout.jsx hiện tại**

```bash
cat web/src/components/AppLayout.jsx
```

Note structure: tìm chỗ define menu items array hoặc render `<Menu items={...}>` từ AntD. Note role check hiện tại (chỉ admin/moderator).

- [ ] **Step 2: Cập nhật menu items definition**

Định vị block code define menu (khoảng dòng 40-60). Thay logic role check bằng:

```jsx
const role = profile?.role || 'guest'

const menuItems = [
  { key: '/dashboard',   label: 'Bảng điều khiển',     show: !!session },
  { key: '/labs',        label: 'Lab',                 show: ['student','instructor','moderator','admin'].includes(role) },
  { key: '/my-reports',  label: 'Báo cáo của tôi',     show: ['student','instructor','moderator','admin'].includes(role) },
  { key: '/teach',       label: 'Giảng dạy',           show: ['instructor','moderator','admin'].includes(role) },
  { key: '/admin',       label: 'Quản trị',            show: ['moderator','admin'].includes(role) },
].filter(item => item.show)
```

(Nếu file hiện đang dùng `<Menu>` của AntD, pass `items={menuItems.map(({key,label}) => ({key,label}))}`. Nếu dùng custom component, render `menuItems.map(...)` thành Link list.)

**LƯU Ý:** AppLayout đang import gì từ useAuth — đảm bảo có `profile` và `session`. Nếu chưa có:

```jsx
const { session, profile } = useAuth()
```

- [ ] **Step 3: Verify build**

```bash
cd web && npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/components/AppLayout.jsx
git commit -m "feat(layout): conditional menu items theo role (student/instructor/admin)"
```

---

## PHASE 7 — UpdateMSSVModal

Mục tiêu: Modal nhắc student có mssv=null cập nhật MSSV trước khi vào /labs.

### Task 7.1: Tạo UpdateMSSVModal component

**Files:**
- Create: `web/src/components/UpdateMSSVModal.jsx`

- [ ] **Step 1: Tạo file**

Tạo `C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\web\src\components\UpdateMSSVModal.jsx`:

```jsx
import { useState } from 'react'
import { Modal, Form, Input, message } from 'antd'
import { supabase } from '../services/supabase'

export default function UpdateMSSVModal({ open, onSuccess }) {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

  async function handleSubmit() {
    try {
      const values = await form.validateFields()
      setLoading(true)
      const { error } = await supabase.rpc('update_profile_fields', {
        p_mssv: values.mssv,
        p_full_name: values.full_name || ''
      })
      if (error) {
        message.error('Lỗi: ' + error.message)
        return
      }
      message.success('Đã cập nhật MSSV')
      form.resetFields()
      onSuccess?.()
    } catch (err) {
      // form validation error
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      open={open}
      title="Cập nhật mã số sinh viên"
      okText="Cập nhật"
      cancelButtonProps={{ style: { display: 'none' } }}
      closable={false}
      maskClosable={false}
      confirmLoading={loading}
      onOk={handleSubmit}
    >
      <p style={{ marginBottom: 16, color: '#6B7280' }}>
        Để truy cập phần Lab, vui lòng nhập MSSV của bạn. <strong>MSSV không thể thay đổi sau khi lưu.</strong>
      </p>
      <Form form={form} layout="vertical">
        <Form.Item
          name="mssv"
          label="Mã số sinh viên"
          rules={[
            { required: true, message: 'MSSV là bắt buộc' },
            { pattern: /^\d{7,8}$/, message: 'MSSV phải là 7-8 chữ số' }
          ]}
        >
          <Input placeholder="VD: 2052345" maxLength={8} />
        </Form.Item>
        <Form.Item
          name="full_name"
          label="Họ và tên (tùy chọn)"
        >
          <Input placeholder="VD: Nguyễn Văn A" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
```

- [ ] **Step 2: Verify build**

```bash
cd web && npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/components/UpdateMSSVModal.jsx
git commit -m "feat(mssv): add UpdateMSSVModal for student missing MSSV"
```

---

### Task 7.2: Tích hợp UpdateMSSVModal vào StudentRoute

**Files:**
- Modify: `web/src/components/StudentRoute.jsx`

- [ ] **Step 1: Update StudentRoute để render modal khi mssv null**

Thay nội dung file `web/src/components/StudentRoute.jsx`:

```jsx
import { useState, useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'
import UpdateMSSVModal from './UpdateMSSVModal'

const STUDENT_ROLES = ['student', 'instructor', 'moderator', 'admin']

export default function StudentRoute({ children }) {
  const { session, profile, loading } = useAuth()
  const location = useLocation()
  const [modalOpen, setModalOpen] = useState(false)

  // Check student missing MSSV
  useEffect(() => {
    if (profile?.role === 'student' && !profile?.mssv) {
      setModalOpen(true)
    } else {
      setModalOpen(false)
    }
  }, [profile?.role, profile?.mssv])

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!session) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (!STUDENT_ROLES.includes(profile?.role)) {
    return <Navigate to="/dashboard" state={{ deniedReason: 'student-only' }} replace />
  }

  function handleMssvSuccess() {
    // Force reload to pick up new profile data
    window.location.reload()
  }

  return (
    <>
      {children}
      <UpdateMSSVModal open={modalOpen} onSuccess={handleMssvSuccess} />
    </>
  )
}
```

- [ ] **Step 2: Verify build**

```bash
cd web && npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git add web/src/components/StudentRoute.jsx
git commit -m "feat(mssv): force MSSV input on /labs entry for student missing MSSV"
```

---

## PHASE 8 — End-to-end verification + PR

### Task 8.1: Manual end-to-end test

**Files:** Không

- [ ] **Step 1: Cleanup test accounts (nếu còn)**

```sql
-- Trong Supabase SQL editor
DELETE FROM auth.users WHERE email LIKE 'e2e_%';
```

- [ ] **Step 2: Khởi động dev server**

```bash
cd web && npm run dev
```

- [ ] **Step 3: E2E test 1 — student có MSSV ở signup**

1. `/register` → email `e2e_student_a@hcmut.edu.vn`, MSSV `2052345`, password `Test12345`
2. Login → menu thấy Dashboard + Lab + Báo cáo của tôi (KHÔNG thấy Giảng dạy/Quản trị)
3. Click Lab → vào `/labs` thành công, KHÔNG thấy modal MSSV
4. Truy cập `/teach` qua URL → redirect về `/dashboard`
5. Truy cập `/admin` qua URL → redirect về `/dashboard`

- [ ] **Step 4: E2E test 2 — student không có MSSV ở signup**

1. `/register` → email `e2e_student_b@hcmut.edu.vn`, bỏ trống MSSV
2. Login → click Lab → modal "Cập nhật mã số sinh viên" hiện ra (không cancel được)
3. Nhập MSSV `2052346` → click Cập nhật → page reload, modal đóng, vào /labs OK
4. Reload page → modal KHÔNG hiện lại

- [ ] **Step 5: E2E test 3 — user phổ thông**

1. `/register` → email `e2e_user_c@gmail.com` (không thấy MSSV input)
2. Login → menu thấy Dashboard (KHÔNG thấy Lab/Báo cáo/Giảng dạy/Quản trị)
3. Truy cập `/labs` qua URL → redirect `/dashboard`

- [ ] **Step 6: E2E test 4 — admin promote student → instructor**

1. Login admin (account hiện hữu role=admin)
2. Vào `/admin` → tìm user `e2e_student_a@hcmut.edu.vn` → click row → modal edit role
3. Click button "Instructor" → save
4. Logout → login lại bằng `e2e_student_a` → menu thấy thêm "Giảng dạy"
5. Click Giảng dạy → vào `/teach` thấy 3 tabs

- [ ] **Step 7: Cleanup**

```sql
DELETE FROM auth.users WHERE email LIKE 'e2e_%';
```

- [ ] **Step 8: Báo "All E2E tests passed"**

---

### Task 8.2: Push branch + tạo PR

**Files:** Không

- [ ] **Step 1: Verify branch state**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git log --oneline main..HEAD
```

Expected: ~10-12 commits của feature.

- [ ] **Step 2: Push branch**

```bash
git push -u origin feature/edu-vn-student-role
```

- [ ] **Step 3: Tạo PR (manual qua GitHub web hoặc gh CLI nếu đã auth)**

Title: `Phân quyền sinh viên/giảng viên + MSSV signup theo email .edu.vn`

Body: dùng template dưới đây paste vào GitHub web:

```markdown
## Tóm tắt

Phân biệt sinh viên (`.edu.vn`), người dùng phổ thông và giảng viên trong hệ thống. Khoá `/labs` cho student+, mở `/teach` cho instructor+, conditional MSSV input ở RegisterPage.

- **Spec:** `docs/superpowers/specs/2026-04-26-edu-vn-student-role-design.md`
- **Plan:** `docs/superpowers/plans/2026-04-26-edu-vn-student-role.md`

## Thay đổi chính

**Database (`2026-04-26-edu-vn-student-role.sql`):**
- Thêm cột `mssv`, `full_name` (idempotent với `mssv_migration.sql`)
- Trigger `enforce_mssv_immutable` chặn update sau khi đã set
- RPC `update_profile_fields` cho user tự cập nhật MSSV null
- Mở rộng role check: `instructor`, `student` thêm vào enum
- Replace `handle_new_user`: derive role từ email `.edu.vn`, pickup MSSV từ metadata

**Client:**
- `RegisterPage`: conditional MSSV input + Alert badge khi `.edu.vn`
- `services/auth.js`: `register()` accept thêm `mssv` param
- `StudentRoute` + `TeachRoute` (NEW route guards)
- `App.jsx`: wrap `/labs/*` với StudentRoute, thêm `/teach`
- `TeachPage` skeleton 3 tabs (Lab Management/Reports/Groups — Phase 1 chỉ structural)
- `AdminPage`: thêm 6 role options
- `AppLayout`: menu items conditional theo role
- `UpdateMSSVModal`: force MSSV input cho student có mssv=null

## Permission matrix

| Role | /dashboard | /labs | /teach | /admin |
|---|:-:|:-:|:-:|:-:|
| admin | ✅ | ✅ | ✅ | ✅ |
| moderator | ✅ | ✅ | ✅ | ✅ |
| instructor | ✅ | ✅ | ✅ | ❌ |
| student | ✅ | ✅ | ❌ | ❌ |
| user | ✅ | ❌ | ❌ | ❌ |

## Test plan

- [x] DB migration apply lên Supabase staging
- [x] Trigger phân biệt role theo email .edu.vn
- [x] MSSV immutability enforced
- [x] E2E signup 3 cases (student có MSSV / student không MSSV / user)
- [x] Route gates 5 roles
- [x] UpdateMSSVModal force input
- [x] Admin promote student → instructor

## Out of scope

- Migration user hiện hữu (project test phase, sẽ reset data trước production)
- Android UI cập nhật MSSV (ticket riêng sau khi web ổn)
- Instructor binding to specific lab_groups (đơn giản hoá: instructor xem mọi report)
- TeachPage Reports/Groups tabs (placeholder "Coming soon")
```

- [ ] **Step 4: Báo PR URL**

---

## Tổng kết

**Số task:** 13 (1 setup + 3 phase 1 DB + 3 phase 2 auth + 3 phase 3 routes + 1 phase 4 teach + 1 phase 5 admin + 1 phase 6 layout + 2 phase 7 mssv modal + 2 phase 8 E2E/PR)

**Số commit dự kiến:** ~12 commits

**Order phụ thuộc:**
- Task 1.1 → 1.2 → 1.3 (DB phải apply trước khi test)
- Task 4.1 phải làm TRƯỚC Task 3.3 (App.jsx import TeachPage)
- Task 7.1 phải làm TRƯỚC Task 7.2 (StudentRoute import UpdateMSSVModal)
- Phase 8 cần tất cả phase trước hoàn tất

**Critical path:** Phase 1 (DB) → Phase 4.1 (TeachPage) → Phase 3 (Routes) → còn lại có thể parallel.

**Testing strategy:** Manual E2E qua dev server + Supabase SQL editor verification. Không có automated tests vì codebase web hiện không có test harness.
