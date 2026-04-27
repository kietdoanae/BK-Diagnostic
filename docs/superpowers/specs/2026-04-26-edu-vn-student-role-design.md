# Edu.vn Student Role + Instructor Role + MSSV Signup — Design Spec

**Date:** 2026-04-26
**Author:** kietdoanae (with Claude assistance)
**Status:** Draft, pending implementation
**Related code:** `mssv_migration.sql`, `web/src/pages/RegisterPage.jsx`, `web/src/components/AdminRoute.jsx`, `web/src/pages/AdminPage.jsx`, `web/src/App.jsx`

---

## 1. Goal

Phân biệt sinh viên (`.edu.vn`) với người dùng phổ thông và giảng viên trong hệ thống BK Diagnostic, để:

1. Khoá phần học tập (Lab system) chỉ cho sinh viên có MSSV — không phải ai login cũng vào được `/labs`.
2. Mở route quản lý lab riêng `/teach` cho giảng viên, không phải share `/admin` với system administrators.
3. Cho phép user thiết lập MSSV ngay khi đăng ký nếu có email `.edu.vn`, hoặc cập nhật sau qua modal nhắc.

---

## 2. Background

### 2.1 Hiện trạng (đã verify trong codebase)

| Item | Hiện trạng |
|---|---|
| `profiles.role` enum | `'admin'`, `'moderator'`, `'user'`, `'guest'` |
| Auto role on signup | Trigger `on_auth_user_created` set `role='user'` cho mọi account |
| RegisterPage fields | username, email, password, confirmPassword (không MSSV, không validate domain) |
| MSSV migration | File `mssv_migration.sql` đã viết (thêm `mssv`, `full_name`, trigger `enforce_mssv_immutable`, RPC `update_profile_fields`). **Chưa rõ đã apply lên Supabase production chưa** — implementation phải apply idempotent. |
| `/labs/*` access gate | `<ProtectedRoute>` (chỉ check authenticated, không check role) → mọi user login đều vào được |
| `/admin` access gate | `<AdminRoute>` check `role IN ('admin','moderator')` |
| Android `UserProfile.kt` | Đã đọc fields `mssv`, `full_name`, role check `admin`/`moderator` only |
| Lab system role | Group-level `'leader'`/`'member'` (không phải user-level) |

### 2.2 Project state context

Project đang ở giai đoạn **test**. Production deploy sẽ reset toàn bộ data → **không cần migration script cho user hiện hữu**. Khi feature deploy, account `.edu.vn` cũ sẽ giữ role `user` và mất quyền truy cập `/labs`. Admin có thể promote thủ công qua `/admin` cho từng case.

---

## 3. Roles & Permission Matrix

**Hierarchy:** `admin` > `moderator` > `instructor` > `student` > `user` > `guest`

| Role | `/dashboard` | `/labs/*` | `/teach` | `/admin` | Tạo/sửa lab content | Đổi role user khác |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `admin` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `moderator` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `instructor` | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| `student` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `user` | ✅ | ❌ (redirect /dashboard) | ❌ | ❌ | ❌ | ❌ |
| `guest` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

**Routes mới:**
- `/teach` — phần giảng dạy: lab content CRUD, xem mọi student report, quản lý lab groups
- `/admin` — phần quản trị (giữ nguyên): user role management, system config

**Phân chia tránh chồng chéo:** Lab Management UI hiện có trong AdminPage (nếu có) sẽ **migrate sang `/teach`**. AdminPage chỉ giữ User Management + system tools.

---

## 4. Architecture: Hybrid (Client UX + DB Authority)

### 4.1 Nguyên lý

- **Client (UX layer):** detect email `.edu.vn` để show MSSV input + badge "Bạn sẽ là sinh viên". Đây CHỈ là gợi ý hiển thị; user không thể bypass.
- **DB trigger (security layer):** server-side derive role từ `auth.users.email`, không trust client's role metadata. Là source of truth cho role assignment.
- MSSV: client gửi trong `auth.signUp` metadata, trigger pickup khi tạo profile row.

### 4.2 Data flow

```
[RegisterPage] user nhập email "abc@hcmut.edu.vn"
     │
     ├─→ client regex /\.edu\.vn$/ match → render <Form.Item name="mssv"/>
     │   + badge "Role: Sinh viên" cho UX rõ ràng
     │
     ├─→ user submit → supabase.auth.signUp({
     │       email, password,
     │       options: { data: { username, mssv } }
     │   })
     │
     ├─→ Supabase tạo row trong auth.users
     │
     └─→ Trigger on_auth_user_created fires:
         IF NEW.email ILIKE '%.edu.vn' THEN role='student' ELSE role='user'
         INSERT INTO profiles (id, username, role, status, mssv) VALUES (...)
         (mssv = COALESCE(metadata->>'mssv', NULL))
```

### 4.3 Lý do chọn Hybrid (loại các option khác)

- **Pure DB-driven** (client không hiển thị gì cho đến khi login lần đầu): UX không rõ — user không biết role mình sắp được gán.
- **Pure client-driven** (client tự compute role gửi vào metadata): **lỗ hổng bảo mật** — user có thể chỉnh metadata gửi `role='admin'` qua DevTools. Loại.

---

## 5. Database Changes

**File:** `db/migrations/2026-04-26-edu-vn-student-role.sql` (NEW)

```sql
-- =============================================================
-- 5.1: Apply mssv columns + immutability (idempotent — safe nếu
-- mssv_migration.sql đã apply trước đó)
-- =============================================================
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS mssv TEXT DEFAULT NULL;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS full_name TEXT DEFAULT NULL;

-- Trigger enforce immutability (re-create idempotent)
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

-- RPC để user tự cập nhật MSSV/full_name khi đang null
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

-- =============================================================
-- 5.2: Mở rộng role check constraint
-- =============================================================
ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_role_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_role_check
  CHECK (role IN ('admin','moderator','instructor','student','user','guest'));

-- =============================================================
-- 5.3: Replace handle_new_user trigger để derive role từ email
-- =============================================================
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

-- Trigger đã có sẵn từ setup.sql, chỉ replace function body
```

**Validation MSSV (server side):** Trigger không enforce format `^\d{7,8}$` — để lỏng ở DB cho linh hoạt. Client validate trước khi submit.

---

## 6. Client UI Changes

### 6.1 RegisterPage.jsx

```jsx
// Pseudo-code
const email = Form.useWatch('email', form)
const isEduVn = /\.edu\.vn$/i.test(email || '')

return (
  <Form>
    <Form.Item name="username" rules={[...]} />
    <Form.Item name="email" rules={[...]} />
    <Form.Item name="password" rules={[...]} />
    <Form.Item name="confirmPassword" rules={[...]} />

    {isEduVn && (
      <>
        <Alert type="info" message="Bạn sẽ được gán role Sinh viên" />
        <Form.Item
          name="mssv"
          label="Mã số sinh viên (tùy chọn — có thể cập nhật sau)"
          rules={[{ pattern: /^\d{7,8}$/, message: 'MSSV phải là 7-8 chữ số' }]}
        >
          <Input placeholder="VD: 2052345" />
        </Form.Item>
      </>
    )}

    <Button type="primary" htmlType="submit">Đăng ký</Button>
  </Form>
)

// Submit handler
async function onFinish(values) {
  const { error } = await signUp(values.email, values.password, {
    username: values.username,
    mssv: values.mssv  // optional, only if .edu.vn
  })
}
```

### 6.2 services/auth.js

Update `signUp` để accept thêm `mssv` field trong metadata:

```js
export async function signUp(email, password, metadata) {
  return supabase.auth.signUp({
    email, password,
    options: { data: metadata }  // metadata = { username, mssv? }
  })
}
```

### 6.3 New route guard components

**`web/src/components/StudentRoute.jsx`** (NEW):
```jsx
export default function StudentRoute({ children }) {
  const { session, profile, loading } = useAuth()
  if (loading) return <Spin />
  if (!session) return <Navigate to="/login" replace />
  const allowed = ['student','instructor','moderator','admin']
  if (!allowed.includes(profile?.role)) {
    return <Navigate to="/dashboard" state={{ msg: 'Tính năng dành cho sinh viên' }} replace />
  }
  return children
}
```

**`web/src/components/TeachRoute.jsx`** (NEW): tương tự, role IN `('instructor','moderator','admin')`, redirect `/dashboard`.

### 6.4 App.jsx route updates

```jsx
// Wrap /labs/* bằng StudentRoute thay ProtectedRoute
<Route path="/labs" element={<StudentRoute><LabsListPage /></StudentRoute>} />
<Route path="/labs/:labId" element={<StudentRoute><LabOverviewPage /></StudentRoute>} />
// ... v.v.

// Thêm route /teach
<Route path="/teach" element={<TeachRoute><TeachPage /></TeachRoute>} />
```

### 6.5 New page: TeachPage.jsx

**Verified:** AdminPage hiện KHÔNG có Lab Management UI — chỉ có User Role Management. Nên TeachPage tạo mới hoàn toàn, không migrate.

Layout với 3 tabs:
- **Lab Management** — CRUD lab content (form Title/Description/Steps/Quiz). Phase 1 có thể chỉ làm danh sách lab + nút Edit redirect tới editor riêng (giảm scope cho task này).
- **Student Reports** — bảng list mọi report đã submit, filter theo lab + student
- **Groups** — quản lý `lab_groups`, gán student vào group qua MSSV

Dùng AntD `<Tabs>` + `<Table>` để giữ consistency với AdminPage.

**Scope reduction:** TeachPage Phase 1 chỉ implement structural layout + tab Lab Management ở mức "list + redirect to existing edit page nếu đã có". Tab Reports và Groups có thể chỉ là placeholder "Coming soon" cho phase này, làm chi tiết trong feature ticket riêng. Mục tiêu: route gate hoạt động, UX flow xuyên suốt.

### 6.6 AdminPage.jsx changes

- Thêm `'instructor'` và `'student'` vào role select buttons (cho admin promote student → instructor).
- User Management UI giữ nguyên.
- **Không** đụng phần Lab Management (vì hiện không có).

### 6.7 AppLayout.jsx menu items (theo role)

```js
const menuItems = [
  { key: 'dashboard', label: 'Bảng điều khiển', path: '/dashboard', show: true },
  { key: 'labs', label: 'Lab', path: '/labs', show: ['student','instructor','moderator','admin'].includes(role) },
  { key: 'my-reports', label: 'Báo cáo của tôi', path: '/my-reports', show: !['user','guest'].includes(role) },
  { key: 'teach', label: 'Giảng dạy', path: '/teach', show: ['instructor','moderator','admin'].includes(role) },
  { key: 'admin', label: 'Quản trị', path: '/admin', show: ['moderator','admin'].includes(role) },
]
```

### 6.8 UpdateMSSVModal.jsx (NEW)

Render trong AuthContext khi:
- `profile?.role === 'student'`
- `profile?.mssv === null`
- User truy cập `/labs/*` lần đầu

Modal:
- Title: "Cập nhật mã số sinh viên"
- Body: "Để truy cập phần Lab, vui lòng nhập MSSV của bạn (không thể đổi sau)."
- Form input MSSV + nút "Cập nhật" gọi RPC `update_profile_fields`.
- Không có nút Cancel — user phải nhập hoặc rời route.

---

## 7. Mobile (Android) Out of Scope

Lý do:
- `UserProfile.kt` đã đọc field `mssv` từ profiles
- Logic role mới ở server-side trigger; Android client chỉ nhận response
- Khi student login trên Android, role 'student' return về OK, app hoạt động bình thường

**Future ticket riêng:** thêm UI "Cập nhật MSSV" trong Android settings, gọi RPC `update_profile_fields`. Tạo issue sau khi web ổn.

---

## 8. Test Plan

### 8.1 Signup flow

| Case | Email | MSSV input | Expected |
|---|---|---|---|
| 1 | `abc@hcmut.edu.vn` | `2052345` | role=student, mssv=2052345 |
| 2 | `abc@hcmut.edu.vn` | (trống) | role=student, mssv=NULL → modal nhắc khi vào /labs |
| 3 | `abc@gmail.com` | (input không hiện) | role=user |
| 4 | `abc@vnu.edu.vn` | `12345678` (8 số) | role=student, mssv=12345678 |
| 5 | `abc@hcmut.edu.vn` | `abc` (sai format) | client validation chặn submit |

### 8.2 Route gates

| Login as | Truy cập `/labs` | `/teach` | `/admin` |
|---|---|---|---|
| user | → /dashboard + toast "Tính năng dành cho sinh viên" | → /dashboard | → /dashboard |
| student | OK | → /dashboard | → /dashboard |
| instructor | OK | OK | → /dashboard |
| moderator | OK | OK | OK |
| admin | OK | OK | OK |

### 8.3 MSSV immutability

- Test: student có mssv=`2052345` → gọi `UPDATE profiles SET mssv='9999999'` qua Supabase REST → expect error `mssv is immutable once set`.

### 8.4 Admin promote flow

- Login admin → /admin → tìm user role=student → click "Promote to Instructor" → user đó reload thấy menu "Giảng dạy" hiện ra.

### 8.5 UpdateMSSVModal

- Login student với mssv=null → vào /labs → modal hiện, không cancel được.
- Nhập MSSV `2052345` → submit → modal đóng, mssv saved, vào /labs OK.
- Reload page → modal không hiện lại (mssv đã set).

---

## 9. Out of Scope / Non-goals

- ❌ Migration của user hiện hữu (project đang test phase, sẽ reset data)
- ❌ Email validation OTP cho `.edu.vn` (giả định Supabase email confirmation đã đủ)
- ❌ Phân biệt MSSV của trường khác nhau (chỉ regex 7-8 chữ số, không check trường)
- ❌ Instructor binding to specific lab_groups (instructor xem được mọi report, không bind class — đơn giản hoá)
- ❌ Android UI cập nhật MSSV (ticket riêng)
- ❌ Self-claim instructor (user signup thấy form đặc biệt) — admin gán thủ công

---

## 10. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Trigger `handle_new_user` fail trong production → user signup không tạo được profile | Wrap migration trong transaction; test trên Supabase staging trước; có rollback script |
| User cố gửi `role='admin'` trong metadata | Trigger ignore client metadata cho field `role`, chỉ derive từ email. Pen-test bằng curl + signUp. |
| User dùng email format hợp lệ nhưng không phải đuôi `.edu.vn` thuộc trường VN (vd `@my.private.edu.vn`) | Accept (giả định mọi `.edu.vn` là trường giáo dục VN). Nếu sau này cần whitelist, thêm bảng `domain_allowlist`. |
| MSSV sai format do user nhập nhầm, không thể sửa do trigger immutable | RPC `update_profile_fields` chỉ cho update khi đang NULL. Nếu user set sai → cần admin xoá account + tạo lại. Trong phase test có thể chấp nhận. Production có thể relax: thêm RPC admin-only `force_update_mssv`. |
| Existing test users `.edu.vn` đang là `role=user` mất quyền lab sau deploy | Acceptable — admin promote thủ công cho từng case trong giai đoạn test. Production sẽ reset data. |

---

## 11. Implementation Order (preview)

1. DB migration (apply lên Supabase staging trước)
2. New route guards `StudentRoute`, `TeachRoute`
3. Update `App.jsx` để wrap `/labs/*` bằng StudentRoute, thêm `/teach`
4. Update `RegisterPage.jsx` + `services/auth.js` cho conditional MSSV input
5. Tạo `UpdateMSSVModal.jsx` + tích hợp vào AuthContext
6. Tạo `TeachPage.jsx` skeleton (3 tabs, có thể tạm placeholder cho tab Reports/Groups)
7. Cập nhật `AdminPage.jsx`: thêm role buttons `instructor`/`student`
8. Cập nhật `AppLayout.jsx` (nếu có) menu theo role
9. Manual test theo Section 8
10. Commit + push

Plan chi tiết step-by-step sẽ tạo qua skill `writing-plans` sau khi spec này được duyệt.

---

## 12. Open Questions (cần user xác nhận trước khi viết plan)

Không còn — đã đóng tất cả qua brainstorm. Nếu user đọc lại thấy điểm nào thiếu/sai, sửa trước khi gọi `writing-plans`.
