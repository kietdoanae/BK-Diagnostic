# BK Diagnostic Web — React + Ant Design Migration

**Date:** 2026-04-04
**Scope:** Migrate toàn bộ website (`website/`) thành React SPA, đặt tại thư mục `web/`

---

## 1. Quyết định kiến trúc

| Quyết định | Lựa chọn | Lý do |
|---|---|---|
| Scope | Toàn bộ 6 trang thành SPA | Nhất quán, một project duy nhất |
| Framework | Vite + React 18 + JavaScript | Nhanh, không cần TypeScript |
| UI library | Ant Design 5.x | Yêu cầu của user |
| Routing | React Router v6 | SPA standard |
| State | React Context (AuthContext) | Đủ dùng, không over-engineer |
| Data layer | Services → Hooks → Pages | Adapter pattern, dễ swap backend |
| Backend hiện tại | Supabase (tạm thời) | Sẽ thay bằng Node.js + PostgreSQL sau |
| Layout sau login | Ant Design Layout + Sider (sidebar collapsed) | Scalable admin dashboard |
| Vị trí | `web/` ở root repo | Tách biệt khỏi `website/` HTML cũ |

---

## 2. Cấu trúc thư mục

```
web/
├── public/
│   └── logo.png
├── src/
│   ├── services/              ← Adapter layer (thay ở đây khi đổi backend)
│   │   ├── supabase.js        ← Supabase client init + env vars
│   │   ├── auth.js            ← login, logout, register, forgotPassword, resetPassword, resolveEmail
│   │   └── api.js             ← getUsers, updateUserStatus, updateUserRole, getLogs, getLogStats
│   ├── hooks/
│   │   ├── useAuth.js         ← session, profile, role (admin/moderator/user)
│   │   ├── useUsers.js        ← danh sách users, update status/role, search/sort
│   │   └── useLogs.js         ← activity logs, stats, filter theo action/platform
│   ├── context/
│   │   └── AuthContext.jsx    ← Provider bọc toàn app, expose session + profile
│   ├── components/
│   │   ├── AppLayout.jsx      ← Ant Design Layout + Sider + Header (sau login)
│   │   ├── ProtectedRoute.jsx ← redirect về /login nếu chưa auth
│   │   └── AdminRoute.jsx     ← redirect về /dashboard nếu không phải admin/mod
│   ├── pages/
│   │   ├── LandingPage.jsx    ← Hero, Features, Hardware, Tech Stack, Team, Footer
│   │   ├── LoginPage.jsx      ← Split layout (left panel + form)
│   │   ├── RegisterPage.jsx
│   │   ├── ForgotPasswordPage.jsx
│   │   ├── ResetPasswordPage.jsx
│   │   ├── DashboardPage.jsx  ← Profile card + stats (regular user)
│   │   └── AdminPage.jsx      ← Tabs: Users | Activity Logs (admin/mod only)
│   ├── App.jsx                ← Route definitions
│   └── main.jsx
├── .env.example               ← VITE_SUPABASE_URL, VITE_SUPABASE_KEY
├── index.html
└── vite.config.js
```

---

## 3. Routing

```
/                    → LandingPage (public)
/login               → LoginPage (redirect → /dashboard nếu đã login)
/register            → RegisterPage (redirect → /dashboard nếu đã login)
/forgot-password     → ForgotPasswordPage
/reset-password      → ResetPasswordPage (xử lý token từ Supabase email link)
/dashboard           → DashboardPage (ProtectedRoute)
/admin               → AdminPage (AdminRoute — chỉ admin/moderator)
```

---

## 4. Services layer (adapter pattern)

`services/auth.js` export:
- `login(email, password)` → `{ data, error }`
- `logout()`
- `register(email, password, username)` → `{ data, error }`
- `forgotPassword(email)` → `{ error }`
- `resetPassword(newPassword)` → `{ error }`
- `resolveEmail(identifier)` → email string | null (username → email lookup)
- `getSession()` → session | null

`services/api.js` export:
- `getUsers(filters)` → `{ data, error }`
- `updateUserStatus(userId, status)` → `{ error }`
- `updateUserRole(userId, role)` → `{ error }`
- `getLogs(filters)` → `{ data, error }`
- `getLogStats()` → `{ data, error }`

**Khi đổi sang Node.js + PostgreSQL:** chỉ cần rewrite `services/auth.js` và `services/api.js` để gọi REST API thay vì Supabase client. Hooks và pages không thay đổi.

---

## 5. Auth flow

1. App load → `AuthContext` gọi `getSession()`
2. Có session → fetch profile (username, role, status) từ `profiles` table
3. Status không phải `active` → tự động logout, redirect `/login` với thông báo
4. `useAuth()` hook expose: `{ user, profile, role, loading, logout }`
5. Supabase realtime `onAuthStateChange` cập nhật context khi session thay đổi

---

## 6. Màu sắc & Theme Ant Design

```js
// Ant Design 5 ConfigProvider token
{
  colorPrimary: '#1565C0',
  colorLink: '#1565C0',
  borderRadius: 8,
  fontFamily: 'Inter, sans-serif'
}
```

- Primary blue: `#1565C0` / `#003291` (HCMUT)
- Accent gold: `#FFC107`
- Header/Sider: `#003291`

---

## 7. Nội dung giữ nguyên

Tất cả nội dung hiện có được port sang React component tương đương:

| HTML cũ | React component |
|---|---|
| Hero section, stats bar | `LandingPage.jsx` — sections |
| Features grid (6 cards) | Ant Design `Card` components |
| Hardware pipeline | Flex layout với Ant Design `Steps` hoặc custom |
| Team section | Ant Design `Card` với avatar |
| Login split layout | Ant Design `Form` + `Input` |
| Register form | Ant Design `Form` |
| Dashboard profile card | Ant Design `Card` + `Avatar` |
| Admin tabs (Users/Logs) | Ant Design `Tabs` + `Table` |
| User status/role badges | Ant Design `Tag` |
| User modal edit | Ant Design `Modal` + `Select` |

---

## 8. Env config

```env
# .env.local (không commit)
VITE_SUPABASE_URL=https://xxx.supabase.co
VITE_SUPABASE_KEY=eyJ...

# Khi đổi sang Node.js backend:
VITE_API_BASE_URL=http://localhost:3000
```

---

## 9. Những gì KHÔNG thay đổi

- Supabase database schema, RLS policies, RPCs — giữ nguyên
- Logic nghiệp vụ (resolveEmail, account status check, activity logging) — port 1:1
- Tất cả nội dung text, hình ảnh, màu sắc — giữ nguyên
- `website/` HTML cũ — giữ nguyên (tham chiếu, không xóa)
