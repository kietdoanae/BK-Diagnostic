# BK Diagnostic Web — React + Ant Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate toàn bộ website BK Diagnostic từ HTML/Tailwind thành React SPA với Ant Design 5, đặt tại thư mục `web/` ở root repo.

**Architecture:** Vite + React 18 + JavaScript. Services layer (`services/`) làm adapter cho Supabase (dễ swap sang Node.js + PostgreSQL sau). Custom hooks cách ly logic khỏi UI. React Context quản lý auth state. React Router v6 cho navigation.

**Tech Stack:** Vite 5, React 18, Ant Design 5, React Router v6, @supabase/supabase-js 2, @ant-design/icons

---

## File Map

```
web/
├── public/logo.png                        (copy từ website/logo.png)
├── index.html
├── vite.config.js
├── .env.example
├── src/
│   ├── main.jsx                           tạo mới
│   ├── App.jsx                            routes + AuthProvider wrapper
│   ├── services/
│   │   ├── supabase.js                    Supabase client singleton
│   │   ├── auth.js                        login/logout/register/forgot/reset/resolveEmail
│   │   └── api.js                         getUsers/updateUserStatus/updateUserRole/getLogs/getLogStats
│   ├── context/
│   │   └── AuthContext.jsx                Provider + useAuthContext hook
│   ├── hooks/
│   │   ├── useAuth.js                     session, profile, role, logout helper
│   │   ├── useUsers.js                    user list, filters, pagination, update
│   │   └── useLogs.js                     activity logs, stats, filters, realtime
│   ├── components/
│   │   ├── AppLayout.jsx                  Ant Design Layout + Sider + Header (sau login)
│   │   ├── ProtectedRoute.jsx             redirect /login nếu chưa auth
│   │   └── AdminRoute.jsx                 redirect /dashboard nếu không phải admin/mod
│   └── pages/
│       ├── LandingPage.jsx                Hero, Features, Hardware, Tech Stack, Team, Footer
│       ├── LoginPage.jsx                  split layout (left panel + Ant Design Form)
│       ├── RegisterPage.jsx               Ant Design Form + validation
│       ├── ForgotPasswordPage.jsx         card layout, email input
│       ├── ResetPasswordPage.jsx          4 states: loading/error/form/success
│       ├── DashboardPage.jsx              profile card + change password + about
│       └── AdminPage.jsx                  Tabs: Users | Activity Logs | My Profile | Wiring Diagram
```

---

## Task 1: Scaffold Vite Project

**Files:**
- Create: `web/` (toàn bộ thư mục)
- Create: `web/.env.example`
- Modify: `.claude/launch.json` (thêm Vite dev server)

- [ ] **Step 1: Tạo Vite project**

```bash
cd C:/Users/KIET/AndroidStudioProjects/BKDiagnostic/.claude/worktrees/infallible-napier
npm create vite@latest web -- --template react
```

Expected output: `Done. Now run: cd web && npm install`

- [ ] **Step 2: Cài dependencies**

```bash
cd web
npm install
npm install antd @ant-design/icons react-router-dom @supabase/supabase-js
```

- [ ] **Step 3: Copy logo**

```bash
cp ../website/logo.png public/logo.png
```

- [ ] **Step 4: Tạo .env.example**

```
# Copy to .env.local and fill in your values
VITE_SUPABASE_URL=https://YOUR_PROJECT_ID.supabase.co
VITE_SUPABASE_KEY=YOUR_SUPABASE_ANON_KEY
```

- [ ] **Step 5: Tạo .env.local từ config.js hiện có**

Mở `../website/config.js`, copy SUPABASE_URL và SUPABASE_KEY vào `.env.local`:

```
VITE_SUPABASE_URL=<giá trị từ config.js>
VITE_SUPABASE_KEY=<giá trị từ config.js>
```

- [ ] **Step 6: Xoá boilerplate của Vite**

Xoá nội dung `src/App.css`, `src/index.css`, `src/assets/react.svg`, `public/vite.svg`.
Để trống `src/index.css` (chỉ giữ file, không xoá).

- [ ] **Step 7: Thêm Vite dev server vào launch.json**

Mở `.claude/launch.json`, thêm vào mảng `configurations`:

```json
{
  "name": "BK Diagnostic React Dev",
  "runtimeExecutable": "npm",
  "runtimeArgs": ["run", "dev", "--", "--port", "5173", "--host"],
  "port": 5173
}
```

- [ ] **Step 8: Verify server khởi động**

```bash
# Từ thư mục web/
npm run dev
```

Mở browser tại `http://localhost:5173` — thấy trang Vite default là OK.

- [ ] **Step 9: Commit**

```bash
cd ..
git add web/ .claude/launch.json
git commit -m "feat(web): scaffold Vite + React + Ant Design project"
```

---

## Task 2: Services Layer

**Files:**
- Create: `web/src/services/supabase.js`
- Create: `web/src/services/auth.js`
- Create: `web/src/services/api.js`

- [ ] **Step 1: Tạo Supabase client**

`web/src/services/supabase.js`:
```js
import { createClient } from '@supabase/supabase-js'

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL
const supabaseKey = import.meta.env.VITE_SUPABASE_KEY

export const supabase = createClient(supabaseUrl, supabaseKey)
```

- [ ] **Step 2: Tạo auth service**

`web/src/services/auth.js`:
```js
import { supabase } from './supabase'

export async function getSession() {
  const { data } = await supabase.auth.getSession()
  return data?.session ?? null
}

export async function resolveEmail(identifier) {
  if (identifier.includes('@')) return identifier
  try {
    const { data, error } = await supabase.rpc('get_email_by_username', { p_username: identifier })
    if (error || !data) return null
    return String(data).replace(/^"|"$/g, '').trim()
  } catch {
    return null
  }
}

export async function login(identifier, password) {
  const email = await resolveEmail(identifier)
  if (!email) return { data: null, error: { message: 'No account found with this username.' } }
  return supabase.auth.signInWithPassword({ email, password })
}

export async function logout() {
  return supabase.auth.signOut()
}

export async function register(email, password, username) {
  return supabase.auth.signUp({ email, password, options: { data: { username } } })
}

export async function forgotPassword(email, redirectTo) {
  return supabase.auth.resetPasswordForEmail(email, { redirectTo })
}

export async function resetPassword(newPassword) {
  return supabase.auth.updateUser({ password: newPassword })
}

export async function getProfile(userId) {
  return supabase.from('profiles').select('*').eq('id', userId).maybeSingle()
}

export function onAuthStateChange(callback) {
  return supabase.auth.onAuthStateChange(callback)
}
```

- [ ] **Step 3: Tạo API service**

`web/src/services/api.js`:
```js
import { supabase } from './supabase'

export async function getUsers() {
  return supabase
    .from('profiles')
    .select('id, username, full_name, email, role, status, created_at, last_sign_in_at')
    .order('created_at', { ascending: false })
}

export async function updateUserStatus(userId, status) {
  return supabase.from('profiles').update({ status }).eq('id', userId)
}

export async function updateUserRole(userId, role) {
  return supabase.from('profiles').update({ role }).eq('id', userId)
}

export async function getLogs({ limit = 50, offset = 0, action = null, platform = null } = {}) {
  return supabase.rpc('get_activity_logs', {
    p_limit: limit,
    p_offset: offset,
    p_action: action,
    p_platform: platform,
  })
}

export async function getLogStats() {
  return supabase.rpc('get_log_stats')
}
```

- [ ] **Step 4: Verify không có lỗi import**

```bash
cd web && npm run build 2>&1 | head -30
```

Expected: build thành công, không có lỗi.

- [ ] **Step 5: Commit**

```bash
git add web/src/services/
git commit -m "feat(web): add services layer (supabase, auth, api)"
```

---

## Task 3: AuthContext + useAuth Hook

**Files:**
- Create: `web/src/context/AuthContext.jsx`
- Create: `web/src/hooks/useAuth.js`

- [ ] **Step 1: Tạo AuthContext**

`web/src/context/AuthContext.jsx`:
```jsx
import { createContext, useContext, useEffect, useState } from 'react'
import { getSession, getProfile, onAuthStateChange, logout as authLogout } from '../services/auth'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(undefined) // undefined = loading
  const [profile, setProfile] = useState(null)

  async function loadProfile(userId) {
    const { data } = await getProfile(userId)
    setProfile(data ?? null)
  }

  useEffect(() => {
    getSession().then(s => {
      setSession(s)
      if (s?.user) loadProfile(s.user.id)
    })

    const { data: { subscription } } = onAuthStateChange((event, s) => {
      setSession(s)
      if (s?.user) loadProfile(s.user.id)
      else setProfile(null)
    })

    return () => subscription.unsubscribe()
  }, [])

  async function logout() {
    await authLogout()
    setSession(null)
    setProfile(null)
  }

  const value = { session, profile, role: profile?.role ?? 'user', loading: session === undefined, logout }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuthContext() {
  return useContext(AuthContext)
}
```

- [ ] **Step 2: Tạo useAuth hook**

`web/src/hooks/useAuth.js`:
```js
import { useAuthContext } from '../context/AuthContext'

export function useAuth() {
  return useAuthContext()
}
```

- [ ] **Step 3: Wrap App với AuthProvider trong main.jsx**

`web/src/main.jsx`:
```jsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { AuthProvider } from './context/AuthContext'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </React.StrictMode>
)
```

- [ ] **Step 4: Verify dev server vẫn chạy không lỗi**

```bash
npm run dev
```

Mở `http://localhost:5173` — không có console error.

- [ ] **Step 5: Commit**

```bash
git add web/src/context/ web/src/hooks/useAuth.js web/src/main.jsx
git commit -m "feat(web): add AuthContext and useAuth hook"
```

---

## Task 4: Routing Skeleton + Guards

**Files:**
- Create: `web/src/components/ProtectedRoute.jsx`
- Create: `web/src/components/AdminRoute.jsx`
- Modify: `web/src/App.jsx`

- [ ] **Step 1: Tạo ProtectedRoute**

`web/src/components/ProtectedRoute.jsx`:
```jsx
import { Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'

export default function ProtectedRoute({ children }) {
  const { session, loading } = useAuth()
  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 80 }}><Spin size="large" /></div>
  if (!session) return <Navigate to="/login" replace />
  return children
}
```

- [ ] **Step 2: Tạo AdminRoute**

`web/src/components/AdminRoute.jsx`:
```jsx
import { Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'

export default function AdminRoute({ children }) {
  const { session, role, loading } = useAuth()
  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 80 }}><Spin size="large" /></div>
  if (!session) return <Navigate to="/login" replace />
  if (role !== 'admin' && role !== 'moderator') return <Navigate to="/dashboard" replace />
  return children
}
```

- [ ] **Step 3: Tạo App.jsx với tất cả routes**

`web/src/App.jsx`:
```jsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import ProtectedRoute from './components/ProtectedRoute'
import AdminRoute from './components/AdminRoute'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import DashboardPage from './pages/DashboardPage'
import AdminPage from './pages/AdminPage'

const theme = {
  token: {
    colorPrimary: '#1565C0',
    colorLink: '#1565C0',
    borderRadius: 8,
    fontFamily: "'Inter', sans-serif",
  },
}

export default function App() {
  return (
    <ConfigProvider theme={theme}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="/admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}
```

- [ ] **Step 4: Tạo placeholder cho tất cả pages (để build không lỗi)**

Tạo các file sau với nội dung placeholder:

`web/src/pages/LandingPage.jsx`:
```jsx
export default function LandingPage() { return <div>Landing</div> }
```

`web/src/pages/LoginPage.jsx`:
```jsx
export default function LoginPage() { return <div>Login</div> }
```

`web/src/pages/RegisterPage.jsx`:
```jsx
export default function RegisterPage() { return <div>Register</div> }
```

`web/src/pages/ForgotPasswordPage.jsx`:
```jsx
export default function ForgotPasswordPage() { return <div>Forgot Password</div> }
```

`web/src/pages/ResetPasswordPage.jsx`:
```jsx
export default function ResetPasswordPage() { return <div>Reset Password</div> }
```

`web/src/pages/DashboardPage.jsx`:
```jsx
export default function DashboardPage() { return <div>Dashboard</div> }
```

`web/src/pages/AdminPage.jsx`:
```jsx
export default function AdminPage() { return <div>Admin</div> }
```

- [ ] **Step 5: Thêm Inter font vào index.html**

`web/index.html` — thêm vào `<head>`:
```html
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet" />
<link rel="icon" type="image/png" href="https://i.ibb.co/Z0Xc41Z/logo.png" />
```

Thay `<title>Vite + React</title>` thành `<title>BK Diagnostic</title>`.

- [ ] **Step 6: Verify routing hoạt động**

```bash
npm run dev
```

Mở `http://localhost:5173/login` — thấy "Login". Mở `/dashboard` — redirect sang `/login`. OK.

- [ ] **Step 7: Commit**

```bash
git add web/src/
git commit -m "feat(web): add routing skeleton with ProtectedRoute and AdminRoute"
```

---

## Task 5: AppLayout Component (Sidebar + Header)

**Files:**
- Create: `web/src/components/AppLayout.jsx`

- [ ] **Step 1: Tạo AppLayout**

`web/src/components/AppLayout.jsx`:
```jsx
import { useState } from 'react'
import { Layout, Menu, Avatar, Dropdown, Button, Space, Typography } from 'antd'
import {
  DashboardOutlined,
  CrownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

const { Sider, Header, Content } = Layout
const { Text } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']

function avatarColor(username = '') {
  let h = 0
  for (const c of username) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

export default function AppLayout({ children }) {
  const [collapsed, setCollapsed] = useState(true)
  const { profile, role, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const username = profile?.username ?? '…'
  const initial = username[0]?.toUpperCase() ?? 'U'
  const bg = avatarColor(username)

  const menuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
    ...(role === 'admin' || role === 'moderator'
      ? [{ key: '/admin', icon: <CrownOutlined />, label: 'Admin Panel' }]
      : []),
  ]

  const userMenuItems = [
    { key: 'logout', icon: <LogoutOutlined />, label: 'Sign Out', danger: true },
  ]

  function handleUserMenu({ key }) {
    if (key === 'logout') { logout(); navigate('/') }
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        style={{ background: '#003291' }}
        width={200}
      >
        <div style={{ padding: collapsed ? '16px 8px' : '16px', display: 'flex', alignItems: 'center', gap: 8, borderBottom: '1px solid rgba(255,255,255,0.1)', marginBottom: 8 }}>
          <img src="https://i.ibb.co/Z0Xc41Z/logo.png" alt="logo" style={{ width: 32, height: 32, borderRadius: 8, flexShrink: 0 }} />
          {!collapsed && <Text strong style={{ color: '#fff', fontSize: 14, whiteSpace: 'nowrap' }}>BK Diagnostic</Text>}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: '#003291', borderRight: 'none' }}
        />
      </Sider>

      <Layout>
        <Header style={{ background: '#fff', padding: '0 20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #f0f0f0', position: 'sticky', top: 0, zIndex: 10 }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenu }} placement="bottomRight">
            <Space style={{ cursor: 'pointer' }}>
              <Avatar style={{ background: bg, fontWeight: 700 }}>{initial}</Avatar>
              <Text style={{ fontWeight: 600 }}>{username}</Text>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ padding: 24, background: '#f5f7fa' }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  )
}
```

- [ ] **Step 2: Áp dụng AppLayout cho DashboardPage placeholder**

`web/src/pages/DashboardPage.jsx`:
```jsx
import AppLayout from '../components/AppLayout'

export default function DashboardPage() {
  return <AppLayout><div>Dashboard content coming soon</div></AppLayout>
}
```

`web/src/pages/AdminPage.jsx`:
```jsx
import AppLayout from '../components/AppLayout'

export default function AdminPage() {
  return <AppLayout><div>Admin content coming soon</div></AppLayout>
}
```

- [ ] **Step 3: Verify layout hiển thị đúng**

Đăng nhập qua Supabase tạm thời (hoặc tạo user test), truy cập `/dashboard` — thấy sidebar màu `#003291`, header trắng, menu items. Collapse/expand sidebar hoạt động.

- [ ] **Step 4: Commit**

```bash
git add web/src/components/AppLayout.jsx web/src/pages/DashboardPage.jsx web/src/pages/AdminPage.jsx
git commit -m "feat(web): add AppLayout with collapsible sidebar"
```

---

## Task 6: LoginPage

**Files:**
- Modify: `web/src/pages/LoginPage.jsx`

- [ ] **Step 1: Implement LoginPage**

`web/src/pages/LoginPage.jsx`:
```jsx
import { useState, useEffect } from 'react'
import { Form, Input, Button, Alert, Typography } from 'antd'
import { EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '../services/auth'
import { useAuth } from '../hooks/useAuth'
import { getProfile } from '../services/auth'

const { Text, Title } = Typography

const BLOCKED = {
  banned: '🚫 Your account has been permanently banned. Contact support if you think this is a mistake.',
  suspended: '⏸️ Your account has been temporarily suspended. Please contact support.',
  inactive: '💤 Your account is inactive. Please contact support to reactivate.',
  pending: '⏳ Your account is pending approval by an administrator. Please check back later.',
}

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [form] = Form.useForm()

  useEffect(() => { if (session) navigate('/dashboard', { replace: true }) }, [session])

  async function handleSubmit({ identifier, password }) {
    setError('')
    setLoading(true)

    const { data, error: err } = await login(identifier, password)

    if (err) {
      setLoading(false)
      const msg = err.message === 'No account found with this username.' ? err.message
        : err.message.includes('Invalid login credentials') ? 'Incorrect email/username or password.'
        : err.message.includes('Email not confirmed') ? 'Email not confirmed. Please check your inbox.'
        : err.message.includes('rate limit') ? 'Too many requests. Please try again later.'
        : err.message
      setError(msg)
      return
    }

    // Check account status
    const userId = data?.user?.id
    if (userId) {
      const { data: prof } = await getProfile(userId)
      const status = (prof?.status ?? 'active').toLowerCase()
      if (status !== 'active') {
        await logout()
        setLoading(false)
        setError(BLOCKED[status] ?? 'Your account is not active.')
        return
      }
    }

    navigate('/dashboard', { replace: true })
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ width: '100%', maxWidth: 840, display: 'flex', borderRadius: 24, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.15)', minHeight: 520 }}>

        {/* Left panel */}
        <div style={{ width: 300, flexShrink: 0, background: 'linear-gradient(135deg, #0A1E6E 0%, #1565C0 60%, #1E88E5 100%)', padding: 40, color: '#fff', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 40, opacity: 0.9 }}>
              <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 40, height: 40, borderRadius: 10, background: 'rgba(255,255,255,0.1)', padding: 4 }} alt="logo" />
              <span style={{ fontWeight: 700, fontSize: 15, color: '#fff' }}>BK Diagnostic</span>
            </Link>
            <Title level={3} style={{ color: '#fff', margin: 0, lineHeight: 1.3 }}>Welcome<br />back!</Title>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13, marginTop: 12, display: 'block', lineHeight: 1.6 }}>
              Sign in to access your dashboard, view diagnostic history, and manage your account.
            </Text>
          </div>
          <Text style={{ color: 'rgba(255,255,255,0.4)', fontSize: 11 }}>© 2026 BK Diagnostic · HCMUT</Text>
        </div>

        {/* Right panel */}
        <div style={{ flex: 1, background: '#fff', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '40px 48px' }}>
          <Title level={3} style={{ marginBottom: 4 }}>Sign In</Title>
          <Text type="secondary" style={{ marginBottom: 28, display: 'block' }}>Enter your credentials to continue</Text>

          {error && <Alert message={error} type="error" showIcon style={{ marginBottom: 20 }} />}

          <Form form={form} layout="vertical" onFinish={handleSubmit} size="large">
            <Form.Item label="Email or Username" name="identifier" rules={[{ required: true, message: 'Please enter your email or username' }]}>
              <Input placeholder="Enter your email or username" autoComplete="username" />
            </Form.Item>
            <Form.Item label="Password" name="password" rules={[{ required: true, message: 'Please enter your password' }]}
              extra={<Link to="/forgot-password" style={{ fontSize: 12 }}>Forgot password?</Link>}>
              <Input.Password placeholder="••••••••" autoComplete="current-password"
                iconRender={v => v ? <EyeOutlined /> : <EyeInvisibleOutlined />} />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}>
              Sign In
            </Button>
          </Form>

          <Text style={{ marginTop: 24, display: 'block', textAlign: 'center', color: '#6b7280' }}>
            Don't have an account? <Link to="/register" style={{ fontWeight: 600 }}>Create account</Link>
          </Text>
          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Link to="/" style={{ fontSize: 12, color: '#9ca3af' }}>← Back to home</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify LoginPage**

Chạy dev server, mở `http://localhost:5173/login` — thấy split layout (left panel xanh + form bên phải). Thử đăng nhập với credentials không đúng — thấy error message.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/LoginPage.jsx
git commit -m "feat(web): implement LoginPage with Ant Design Form"
```

---

## Task 7: RegisterPage

**Files:**
- Modify: `web/src/pages/RegisterPage.jsx`

- [ ] **Step 1: Implement RegisterPage**

`web/src/pages/RegisterPage.jsx`:
```jsx
import { useState, useEffect } from 'react'
import { Form, Input, Button, Alert, Typography } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '../services/auth'
import { useAuth } from '../hooks/useAuth'

const { Text, Title } = Typography

export default function RegisterPage() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const { session } = useAuth()
  const navigate = useNavigate()
  const [form] = Form.useForm()

  useEffect(() => { if (session) navigate('/dashboard', { replace: true }) }, [session])

  async function handleSubmit({ username, email, password }) {
    setError('')
    setLoading(true)
    const { error: err } = await register(email, password, username)
    setLoading(false)
    if (err) {
      const msg = err.message.includes('already registered') ? 'This email is already registered.'
        : err.message.includes('Password should be') ? 'Password must be at least 6 characters.'
        : err.message
      setError(msg)
    } else {
      setSuccess(true)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '32px 16px' }}>
      <div style={{ width: '100%', maxWidth: 840, display: 'flex', borderRadius: 24, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.15)' }}>

        {/* Left panel */}
        <div style={{ width: 300, flexShrink: 0, background: 'linear-gradient(135deg, #0A1E6E 0%, #1565C0 60%, #1E88E5 100%)', padding: 40, color: '#fff', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 40, opacity: 0.9 }}>
              <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 40, height: 40, borderRadius: 10, background: 'rgba(255,255,255,0.1)', padding: 4 }} alt="logo" />
              <span style={{ fontWeight: 700, fontSize: 15, color: '#fff' }}>BK Diagnostic</span>
            </Link>
            <Title level={3} style={{ color: '#fff', margin: 0, lineHeight: 1.3 }}>Create a free<br />account</Title>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13, marginTop: 12, display: 'block', lineHeight: 1.6 }}>
              Register to access all vehicle diagnostic features.
            </Text>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {['Fill in your registration details', 'Confirm your email address', 'Sign in and start diagnosing'].map((t, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, color: 'rgba(255,255,255,0.8)', fontSize: 13 }}>
                <span style={{ width: 22, height: 22, borderRadius: '50%', background: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, flexShrink: 0 }}>{i + 1}</span>
                {t}
              </div>
            ))}
          </div>
          <Text style={{ color: 'rgba(255,255,255,0.4)', fontSize: 11 }}>© 2026 BK Diagnostic · HCMUT</Text>
        </div>

        {/* Right panel */}
        <div style={{ flex: 1, background: '#fff', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '40px 48px' }}>
          <Title level={3} style={{ marginBottom: 4 }}>Create Account</Title>
          <Text type="secondary" style={{ marginBottom: 24, display: 'block' }}>Fill in all details to register</Text>

          {error && <Alert message={error} type="error" showIcon style={{ marginBottom: 20 }} />}
          {success && <Alert message="Registration successful! Check your email to confirm your account." type="success" showIcon style={{ marginBottom: 20 }} />}

          {!success && (
            <Form form={form} layout="vertical" onFinish={handleSubmit} size="large">
              <Form.Item label="Username" name="username" rules={[{ required: true, message: 'Please enter a username' }]}>
                <Input placeholder="e.g. johndoe" autoComplete="username" />
              </Form.Item>
              <Form.Item label="Email" name="email" rules={[{ required: true, type: 'email', message: 'Please enter a valid email' }]}>
                <Input placeholder="example@bk.edu.vn" autoComplete="email" />
              </Form.Item>
              <Form.Item label="Password" name="password" rules={[{ required: true, min: 6, message: 'Password must be at least 6 characters' }]}>
                <Input.Password placeholder="At least 6 characters" autoComplete="new-password" />
              </Form.Item>
              <Form.Item label="Confirm Password" name="confirm"
                rules={[{ required: true, message: 'Please confirm your password' },
                  ({ getFieldValue }) => ({ validator(_, v) { return !v || getFieldValue('password') === v ? Promise.resolve() : Promise.reject('Passwords do not match') } })]}>
                <Input.Password placeholder="Re-enter your password" autoComplete="new-password" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}>
                Create Account
              </Button>
            </Form>
          )}

          <Text style={{ marginTop: 24, display: 'block', textAlign: 'center', color: '#6b7280' }}>
            Already have an account? <Link to="/login" style={{ fontWeight: 600 }}>Sign In</Link>
          </Text>
          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Link to="/" style={{ fontSize: 12, color: '#9ca3af' }}>← Back to home</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify RegisterPage**

Mở `http://localhost:5173/register` — thấy split layout giống login. Thử submit form trống — thấy validation errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/RegisterPage.jsx
git commit -m "feat(web): implement RegisterPage"
```

---

## Task 8: ForgotPasswordPage + ResetPasswordPage

**Files:**
- Modify: `web/src/pages/ForgotPasswordPage.jsx`
- Modify: `web/src/pages/ResetPasswordPage.jsx`

- [ ] **Step 1: Implement ForgotPasswordPage**

`web/src/pages/ForgotPasswordPage.jsx`:
```jsx
import { useState } from 'react'
import { Form, Input, Button, Alert, Typography, Card } from 'antd'
import { MailOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { forgotPassword } from '../services/auth'

const { Text, Title } = Typography

export default function ForgotPasswordPage() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [sent, setSent] = useState(false)

  async function handleSubmit({ email }) {
    setError('')
    setLoading(true)
    const redirectTo = window.location.origin + '/reset-password'
    const { error: err } = await forgotPassword(email, redirectTo)
    setLoading(false)
    if (err) {
      const msg = err.message.includes('rate limit') ? 'Too many requests. Please try again in a few minutes.' : err.message
      setError(msg)
    } else {
      setSent(true)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 16 }}>
      <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 32 }}>
        <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 36, height: 36, borderRadius: 10 }} alt="logo" />
        <Text strong style={{ fontSize: 18, color: '#003291' }}>BK Diagnostic</Text>
      </Link>
      <Card style={{ width: '100%', maxWidth: 440, borderRadius: 24, boxShadow: '0 20px 60px rgba(0,0,0,0.1)' }} bodyStyle={{ padding: 32 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ width: 56, height: 56, background: '#eff6ff', borderRadius: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
            <MailOutlined style={{ fontSize: 24, color: '#1565C0' }} />
          </div>
          <Title level={4} style={{ margin: 0 }}>Forgot Password?</Title>
          <Text type="secondary" style={{ fontSize: 13, lineHeight: 1.6, display: 'block', marginTop: 8 }}>
            Enter your registered email. We'll send you a password reset link.
          </Text>
        </div>

        {error && <Alert message={error} type="error" showIcon style={{ marginBottom: 16 }} />}
        {sent
          ? <Alert message="Email sent! Check your inbox for the reset link." type="success" showIcon />
          : (
            <Form layout="vertical" onFinish={handleSubmit} size="large">
              <Form.Item label="Email" name="email" rules={[{ required: true, type: 'email', message: 'Please enter a valid email' }]}>
                <Input placeholder="example@hcmut.edu.vn" autoComplete="email" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg,#1565C0,#1E88E5)', border: 'none' }}>
                Send Reset Link
              </Button>
            </Form>
          )}

        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <Link to="/login" style={{ fontWeight: 600, fontSize: 13 }}>← Back to Sign In</Link>
        </div>
      </Card>
    </div>
  )
}
```

- [ ] **Step 2: Implement ResetPasswordPage**

`web/src/pages/ResetPasswordPage.jsx`:
```jsx
import { useState, useEffect } from 'react'
import { Form, Input, Button, Alert, Typography, Card, Spin } from 'antd'
import { LockOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { resetPassword, onAuthStateChange } from '../services/auth'

const { Text, Title } = Typography

export default function ResetPasswordPage() {
  const [state, setState] = useState('loading') // loading | error | form | success
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    const { data: { subscription } } = onAuthStateChange((event, session) => {
      if (event === 'PASSWORD_RECOVERY') { setState('form') }
      else if (event === 'SIGNED_IN' && session) {
        const hash = window.location.hash
        if (hash.includes('type=recovery') || hash.includes('access_token')) setState('form')
        else navigate('/dashboard', { replace: true })
      }
    })
    const timeout = setTimeout(() => {
      setState(s => s === 'loading' ? 'error' : s)
    }, 4000)
    return () => { subscription.unsubscribe(); clearTimeout(timeout) }
  }, [])

  async function handleSubmit({ password }) {
    setError('')
    setLoading(true)
    const { error: err } = await resetPassword(password)
    setLoading(false)
    if (err) setError(err.message)
    else setState('success')
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 16 }}>
      <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 32 }}>
        <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 36, height: 36, borderRadius: 10 }} alt="logo" />
        <Text strong style={{ fontSize: 18, color: '#003291' }}>BK Diagnostic</Text>
      </Link>
      <Card style={{ width: '100%', maxWidth: 440, borderRadius: 24, boxShadow: '0 20px 60px rgba(0,0,0,0.1)' }} bodyStyle={{ padding: 32 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ width: 56, height: 56, background: state === 'success' ? '#f0fdf4' : '#eff6ff', borderRadius: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
            {state === 'success'
              ? <CheckCircleOutlined style={{ fontSize: 28, color: '#22c55e' }} />
              : <LockOutlined style={{ fontSize: 24, color: '#1565C0' }} />}
          </div>
          <Title level={4} style={{ margin: 0 }}>{state === 'success' ? 'Password Updated!' : 'Reset Password'}</Title>
          {state !== 'success' && <Text type="secondary" style={{ fontSize: 13, display: 'block', marginTop: 8 }}>Enter a new password for your account.</Text>}
        </div>

        {state === 'loading' && <div style={{ textAlign: 'center', padding: '16px 0' }}><Spin /> <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>Verifying reset link…</Text></div>}

        {state === 'error' && (
          <>
            <Alert message="This link is invalid or has expired." description="Please request a new password reset email." type="error" showIcon style={{ marginBottom: 16 }} />
            <Button type="primary" block onClick={() => navigate('/forgot-password')} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg,#1565C0,#1E88E5)', border: 'none' }}>Request New Link</Button>
          </>
        )}

        {state === 'form' && (
          <>
            {error && <Alert message={error} type="error" showIcon style={{ marginBottom: 16 }} />}
            <Form layout="vertical" onFinish={handleSubmit} size="large">
              <Form.Item label="New Password" name="password" rules={[{ required: true, min: 6, message: 'Password must be at least 6 characters' }]}>
                <Input.Password placeholder="At least 6 characters" autoComplete="new-password" />
              </Form.Item>
              <Form.Item label="Confirm New Password" name="confirm"
                rules={[{ required: true, message: 'Please confirm your password' },
                  ({ getFieldValue }) => ({ validator(_, v) { return !v || getFieldValue('password') === v ? Promise.resolve() : Promise.reject('Passwords do not match') } })]}>
                <Input.Password placeholder="Repeat new password" autoComplete="new-password" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg,#1565C0,#1E88E5)', border: 'none' }}>
                Reset Password
              </Button>
            </Form>
          </>
        )}

        {state === 'success' && (
          <>
            <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginBottom: 20 }}>You can now sign in with your new password.</Text>
            <Button type="primary" block onClick={() => navigate('/login')} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg,#1565C0,#1E88E5)', border: 'none' }}>Sign In</Button>
          </>
        )}
      </Card>
    </div>
  )
}
```

- [ ] **Step 3: Verify pages**

Mở `/forgot-password` — thấy card với form email. Mở `/reset-password` — thấy "Verifying reset link…" rồi sau 4s chuyển sang state error.

- [ ] **Step 4: Commit**

```bash
git add web/src/pages/ForgotPasswordPage.jsx web/src/pages/ResetPasswordPage.jsx
git commit -m "feat(web): implement ForgotPasswordPage and ResetPasswordPage"
```

---

## Task 9: DashboardPage (User Profile)

**Files:**
- Modify: `web/src/pages/DashboardPage.jsx`

- [ ] **Step 1: Implement DashboardPage**

`web/src/pages/DashboardPage.jsx`:
```jsx
import { useState } from 'react'
import { Card, Avatar, Typography, Tag, Row, Col, Form, Input, Button, Alert } from 'antd'
import { UserOutlined, CalendarOutlined, SafetyOutlined, MailOutlined, LockOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { supabase } from '../services/supabase'

const { Text, Title } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']
function avatarColor(u = '') {
  let h = 0; for (const c of u) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

const ROLE_COLOR = { admin: 'purple', moderator: 'blue', user: 'default', guest: 'default' }
const STATUS_COLOR = { active: 'success', inactive: 'default', banned: 'error', suspended: 'warning', pending: 'processing' }

export default function DashboardPage() {
  const { session, profile, role } = useAuth()
  const [pwMsg, setPwMsg] = useState(null) // { type, text }
  const [pwLoading, setPwLoading] = useState(false)
  const [form] = Form.useForm()

  const username = profile?.username ?? session?.user?.email?.split('@')[0] ?? 'User'
  const email = session?.user?.email ?? '—'
  const joined = profile?.created_at ? new Date(profile.created_at).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' }) : '—'
  const status = profile?.status ?? 'active'
  const bg = avatarColor(username)
  const initial = username[0]?.toUpperCase() ?? 'U'

  async function handleChangePassword({ current, newPw }) {
    setPwMsg(null)
    setPwLoading(true)
    // Re-auth with current password first
    const { error: reAuthErr } = await supabase.auth.signInWithPassword({ email, password: current })
    if (reAuthErr) { setPwLoading(false); setPwMsg({ type: 'error', text: 'Current password is incorrect.' }); return }
    const { error } = await supabase.auth.updateUser({ password: newPw })
    setPwLoading(false)
    if (error) setPwMsg({ type: 'error', text: error.message })
    else { setPwMsg({ type: 'success', text: 'Password updated successfully.' }); form.resetFields() }
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 800, margin: '0 auto' }}>

        {/* Profile Hero Card */}
        <Card style={{ borderRadius: 24, marginBottom: 24, overflow: 'hidden', border: '1px solid #e8edf5' }} bodyStyle={{ padding: 0 }}>
          <div style={{ height: 128, background: 'linear-gradient(135deg,#003291 0%,#1565C0 45%,#1E88E5 75%,#0ea5e9 100%)', position: 'relative' }}>
            <div style={{ position: 'absolute', top: 16, right: 20 }}>
              <Tag color={ROLE_COLOR[role] ?? 'default'} style={{ fontWeight: 700, borderRadius: 20 }}>{role?.charAt(0).toUpperCase() + role?.slice(1)}</Tag>
            </div>
          </div>
          <div style={{ padding: '0 28px 28px' }}>
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20, marginTop: -48, marginBottom: 20 }}>
              <Avatar size={96} style={{ background: bg, fontWeight: 900, fontSize: 40, border: '4px solid #fff', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', flexShrink: 0 }}>{initial}</Avatar>
              <div style={{ marginBottom: 8 }}>
                <Title level={4} style={{ margin: 0 }}>{username}</Title>
                <Text type="secondary">{email}</Text>
              </div>
              <div style={{ marginBottom: 8, marginLeft: 'auto' }}>
                <Tag color={STATUS_COLOR[status]} style={{ fontWeight: 600, borderRadius: 20, padding: '4px 12px' }}>{status.charAt(0).toUpperCase() + status.slice(1)}</Tag>
              </div>
            </div>

            <Row gutter={[16, 16]}>
              {[
                { icon: <UserOutlined style={{ color: '#1565C0' }} />, label: 'Username', value: username, bg: '#eff6ff' },
                { icon: <CalendarOutlined style={{ color: '#059669' }} />, label: 'Member Since', value: joined, bg: '#f0fdf4' },
                { icon: <SafetyOutlined style={{ color: '#7c3aed' }} />, label: 'Status', value: status.charAt(0).toUpperCase() + status.slice(1), bg: '#f5f3ff' },
              ].map(({ icon, label, value, bg: ibg }) => (
                <Col span={8} key={label}>
                  <div style={{ background: '#fafafa', border: '1px solid #f0f0f0', borderRadius: 16, padding: '16px', display: 'flex', gap: 12 }}>
                    <div style={{ width: 32, height: 32, background: ibg, borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{icon}</div>
                    <div>
                      <Text style={{ fontSize: 10, color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1 }}>{label}</Text>
                      <div style={{ fontWeight: 700, fontSize: 14, marginTop: 2 }}>{value}</div>
                    </div>
                  </div>
                </Col>
              ))}
              <Col span={24}>
                <div style={{ background: '#fafafa', border: '1px solid #f0f0f0', borderRadius: 16, padding: '16px', display: 'flex', gap: 12 }}>
                  <div style={{ width: 32, height: 32, background: '#f0f9ff', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><MailOutlined style={{ color: '#0284c7' }} /></div>
                  <div>
                    <Text style={{ fontSize: 10, color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1 }}>Email Address</Text>
                    <div style={{ fontWeight: 700, fontSize: 14, marginTop: 2 }}>{email}</div>
                  </div>
                </div>
              </Col>
            </Row>
          </div>
        </Card>

        <Row gutter={24}>
          {/* Change Password */}
          <Col xs={24} md={12}>
            <Card style={{ borderRadius: 24, border: '1px solid #e8edf5', height: '100%' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
                <div style={{ width: 40, height: 40, borderRadius: 14, background: 'linear-gradient(135deg,#003291,#1E88E5)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <LockOutlined style={{ color: '#fff', fontSize: 18 }} />
                </div>
                <div>
                  <Text strong style={{ display: 'block', fontSize: 15 }}>Change Password</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>Update your account password</Text>
                </div>
              </div>
              {pwMsg && <Alert message={pwMsg.text} type={pwMsg.type} showIcon style={{ marginBottom: 16 }} />}
              <Form form={form} layout="vertical" onFinish={handleChangePassword}>
                <Form.Item label="Current Password" name="current" rules={[{ required: true, message: 'Required' }]}>
                  <Input.Password placeholder="Enter current password" />
                </Form.Item>
                <Form.Item label="New Password" name="newPw" rules={[{ required: true, min: 6, message: 'Min. 6 characters' }]}>
                  <Input.Password placeholder="Min. 6 characters" />
                </Form.Item>
                <Form.Item label="Confirm New Password" name="confirmPw"
                  rules={[{ required: true, message: 'Required' },
                    ({ getFieldValue }) => ({ validator(_, v) { return !v || getFieldValue('newPw') === v ? Promise.resolve() : Promise.reject('Passwords do not match') } })]}>
                  <Input.Password placeholder="Repeat new password" />
                </Form.Item>
                <Button type="primary" htmlType="submit" block loading={pwLoading} style={{ background: 'linear-gradient(135deg,#003291,#1E88E5)', border: 'none', fontWeight: 600 }}>
                  Update Password
                </Button>
              </Form>
            </Card>
          </Col>

          {/* About */}
          <Col xs={24} md={12}>
            <Card style={{ borderRadius: 20, height: '100%' }}>
              <Title level={5} style={{ marginBottom: 4 }}>About BK Diagnostic</Title>
              <Text type="secondary" style={{ fontSize: 13 }}>HCMUT · Automotive Engineering</Text>
              <ul style={{ listStyle: 'none', padding: 0, marginTop: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
                {['Vehicle diagnostics via OBD2 port', 'Supports Ford, Toyota, Hyundai & more', 'MCP2515 → STM32 → CP2102 → USB', 'Real-time CAN bus data monitoring'].map(t => (
                  <li key={t} style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <span style={{ width: 20, height: 20, borderRadius: '50%', background: '#f0fdf4', color: '#22c55e', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, flexShrink: 0, marginTop: 1 }}>✓</span>
                    <Text style={{ fontSize: 13 }}>{t}</Text>
                  </li>
                ))}
              </ul>
              <div style={{ marginTop: 20, paddingTop: 16, borderTop: '1px solid #f0f0f0' }}>
                <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, fontSize: 13 }}>← Back to Homepage</Link>
              </div>
            </Card>
          </Col>
        </Row>
      </div>
    </AppLayout>
  )
}
```

- [ ] **Step 2: Verify DashboardPage**

Đăng nhập, vào `/dashboard` — thấy profile card với avatar, tên, email, ngày tham gia. Card Change Password hiển thị form.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/DashboardPage.jsx
git commit -m "feat(web): implement DashboardPage with profile and change password"
```

---

## Task 10: useUsers + useLogStats hooks

**Files:**
- Create: `web/src/hooks/useUsers.js`
- Create: `web/src/hooks/useLogs.js`

- [ ] **Step 1: Tạo useUsers hook**

`web/src/hooks/useUsers.js`:
```js
import { useState, useEffect, useMemo } from 'react'
import { getUsers, updateUserStatus, updateUserRole } from '../services/api'

export function useUsers() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [search, setSearch] = useState('')
  const [filterRole, setFilterRole] = useState('')
  const [filterStatus, setFilterStatus] = useState('')
  const [sortField, setSortField] = useState('created_at')
  const [sortOrder, setSortOrder] = useState('descend')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  async function load() {
    setLoading(true)
    const { data, error: err } = await getUsers()
    setLoading(false)
    if (err) setError(err.message)
    else setUsers(data ?? [])
  }

  useEffect(() => { load() }, [])

  const filtered = useMemo(() => {
    let result = [...users]
    if (search) {
      const q = search.toLowerCase()
      result = result.filter(u =>
        u.username?.toLowerCase().includes(q) ||
        u.email?.toLowerCase().includes(q) ||
        u.full_name?.toLowerCase().includes(q)
      )
    }
    if (filterRole) result = result.filter(u => u.role === filterRole)
    if (filterStatus) result = result.filter(u => u.status === filterStatus)
    result.sort((a, b) => {
      const av = a[sortField] ?? '', bv = b[sortField] ?? ''
      const cmp = av < bv ? -1 : av > bv ? 1 : 0
      return sortOrder === 'ascend' ? cmp : -cmp
    })
    return result
  }, [users, search, filterRole, filterStatus, sortField, sortOrder])

  const stats = useMemo(() => ({
    total: users.length,
    admins: users.filter(u => u.role === 'admin').length,
    active: users.filter(u => u.status === 'active').length,
    banned: users.filter(u => u.status === 'banned' || u.status === 'suspended').length,
  }), [users])

  async function changeStatus(userId, status) {
    const { error: err } = await updateUserStatus(userId, status)
    if (!err) setUsers(prev => prev.map(u => u.id === userId ? { ...u, status } : u))
    return err
  }

  async function changeRole(userId, role) {
    const { error: err } = await updateUserRole(userId, role)
    if (!err) setUsers(prev => prev.map(u => u.id === userId ? { ...u, role } : u))
    return err
  }

  return {
    users: filtered, allUsers: users, loading, error, stats,
    search, setSearch, filterRole, setFilterRole, filterStatus, setFilterStatus,
    sortField, setSortField, sortOrder, setSortOrder,
    page, setPage, pageSize, setPageSize,
    changeStatus, changeRole, reload: load,
  }
}
```

- [ ] **Step 2: Tạo useLogs hook**

`web/src/hooks/useLogs.js`:
```js
import { useState, useEffect, useCallback } from 'react'
import { getLogs, getLogStats } from '../services/api'
import { supabase } from '../services/supabase'

export function useLogs() {
  const [logs, setLogs] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [filterPlatform, setFilterPlatform] = useState('')
  const [filterAction, setFilterAction] = useState('')
  const [filterUser, setFilterUser] = useState('')
  const [isLive, setIsLive] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    const [logsRes, statsRes] = await Promise.all([
      getLogs({ limit: 100, action: filterAction || null, platform: filterPlatform || null }),
      getLogStats(),
    ])
    setLoading(false)
    if (logsRes.data) setLogs(logsRes.data)
    if (statsRes.data) setStats(statsRes.data)
  }, [filterAction, filterPlatform])

  useEffect(() => { load() }, [load])

  // Realtime subscription
  useEffect(() => {
    const channel = supabase
      .channel('activity_logs_changes')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'activity_logs' }, () => { load() })
      .subscribe(status => setIsLive(status === 'SUBSCRIBED'))
    return () => { supabase.removeChannel(channel) }
  }, [load])

  const filteredLogs = filterUser
    ? logs.filter(l => l.username?.toLowerCase().includes(filterUser.toLowerCase()))
    : logs

  return {
    logs: filteredLogs, stats, loading, isLive,
    filterPlatform, setFilterPlatform,
    filterAction, setFilterAction,
    filterUser, setFilterUser,
    reload: load,
  }
}
```

- [ ] **Step 3: Verify build**

```bash
npm run build 2>&1 | tail -20
```

Expected: build thành công.

- [ ] **Step 4: Commit**

```bash
git add web/src/hooks/
git commit -m "feat(web): add useUsers and useLogs hooks"
```

---

## Task 11: AdminPage

**Files:**
- Modify: `web/src/pages/AdminPage.jsx`

- [ ] **Step 1: Implement AdminPage**

`web/src/pages/AdminPage.jsx`:
```jsx
import { useState } from 'react'
import { Tabs, Table, Tag, Button, Input, Select, Space, Typography, Row, Col, Card, Modal, Badge, Tooltip, Alert } from 'antd'
import { SearchOutlined, ReloadOutlined, ExportOutlined, WifiOutlined, DisconnectOutlined } from '@ant-design/icons'
import AppLayout from '../components/AppLayout'
import DashboardPage from './DashboardPage'
import { useUsers } from '../hooks/useUsers'
import { useLogs } from '../hooks/useLogs'

const { Text, Title } = Typography
const { Option } = Select

// ── Status / Role badge helpers ──────────────────────────────
const STATUS_COLOR = { active: 'success', inactive: 'default', banned: 'error', suspended: 'warning', pending: 'processing' }
const ROLE_COLOR   = { admin: 'purple', moderator: 'blue', user: 'default', guest: 'default' }

const ACTION_COLOR = {
  LOGIN: 'green', LOGIN_FAILED: 'red', LOGOUT: 'orange', REGISTER: 'blue',
  DIAGNOSTIC_START: 'cyan', DIAGNOSTIC_STOP: 'orange', ACTIVE_TEST_RUN: 'purple', RAW_EXPORT: 'geekblue',
}

// ── Users Tab ─────────────────────────────────────────────────
function UsersTab() {
  const {
    users, loading, error, stats,
    search, setSearch, filterRole, setFilterRole, filterStatus, setFilterStatus,
    page, setPage, pageSize, setPageSize,
    changeStatus, changeRole,
  } = useUsers()

  const [editModal, setEditModal] = useState(null) // { user, field } | null

  const statsCards = [
    { label: 'Total Users', value: stats.total, color: '#1f2937' },
    { label: 'Admins', value: stats.admins, color: '#d97706' },
    { label: 'Active', value: stats.active, color: '#16a34a' },
    { label: 'Banned / Suspended', value: stats.banned, color: '#dc2626' },
  ]

  const columns = [
    { title: 'User', dataIndex: 'username', key: 'username', sorter: true,
      render: (_, r) => (
        <Space>
          <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#1565C0', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, flexShrink: 0 }}>
            {(r.username || r.full_name || '?')[0].toUpperCase()}
          </div>
          <div>
            <Text strong style={{ display: 'block', fontSize: 13 }}>{r.full_name || r.username || '—'}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{r.email}</Text>
          </div>
        </Space>
      )
    },
    { title: 'Username', dataIndex: 'username', key: 'uname', render: v => <Text code>{v}</Text> },
    { title: 'Status', dataIndex: 'status', key: 'status', sorter: true,
      render: (v, r) => (
        <Tag color={STATUS_COLOR[v] || 'default'} style={{ cursor: 'pointer', fontWeight: 600, borderRadius: 20 }}
          onClick={() => setEditModal({ user: r, field: 'status' })}>
          {v?.charAt(0).toUpperCase() + v?.slice(1)}
        </Tag>
      )
    },
    { title: 'Role', dataIndex: 'role', key: 'role', sorter: true,
      render: (v, r) => (
        <Tag color={ROLE_COLOR[v] || 'default'} style={{ cursor: 'pointer', fontWeight: 600, borderRadius: 20 }}
          onClick={() => setEditModal({ user: r, field: 'role' })}>
          {v?.charAt(0).toUpperCase() + v?.slice(1)}
        </Tag>
      )
    },
    { title: 'Joined', dataIndex: 'created_at', key: 'created_at', sorter: true,
      render: v => v ? new Date(v).toLocaleDateString() : '—'
    },
    { title: 'Last Active', dataIndex: 'last_sign_in_at', key: 'last_sign_in_at', sorter: true,
      render: v => v ? new Date(v).toLocaleString() : 'Never'
    },
    { title: 'Actions', key: 'actions', align: 'right',
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => setEditModal({ user: r, field: 'status' })}>Status</Button>
          <Button size="small" onClick={() => setEditModal({ user: r, field: 'role' })}>Role</Button>
        </Space>
      )
    },
  ]

  function exportCSV() {
    const headers = ['username', 'email', 'role', 'status', 'created_at']
    const rows = users.map(u => headers.map(h => u[h] ?? '').join(','))
    const csv = [headers.join(','), ...rows].join('\n')
    const a = document.createElement('a'); a.href = 'data:text/csv,' + encodeURIComponent(csv)
    a.download = 'users.csv'; a.click()
  }

  async function handleEditSave(value) {
    if (!editModal) return
    const { user, field } = editModal
    if (field === 'status') await changeStatus(user.id, value)
    else await changeRole(user.id, value)
    setEditModal(null)
  }

  return (
    <>
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        {statsCards.map(({ label, value, color }) => (
          <Col span={6} key={label}>
            <Card size="small" style={{ borderRadius: 12 }}>
              <Text style={{ fontSize: 11, color: '#9ca3af', textTransform: 'uppercase', fontWeight: 600, letterSpacing: 0.8 }}>{label}</Text>
              <div style={{ fontSize: 28, fontWeight: 900, color, marginTop: 4 }}>{value}</div>
            </Card>
          </Col>
        ))}
      </Row>

      <Card style={{ borderRadius: 16 }} bodyStyle={{ padding: 0 }}>
        <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Input prefix={<SearchOutlined />} placeholder="Search name, email or username…" value={search} onChange={e => setSearch(e.target.value)} style={{ width: 260 }} />
          <Select value={filterRole || undefined} onChange={v => setFilterRole(v ?? '')} placeholder="All Roles" allowClear style={{ width: 140 }}>
            {['admin','moderator','user','guest'].map(r => <Option key={r} value={r}>{r.charAt(0).toUpperCase() + r.slice(1)}</Option>)}
          </Select>
          <Select value={filterStatus || undefined} onChange={v => setFilterStatus(v ?? '')} placeholder="All Statuses" allowClear style={{ width: 150 }}>
            {['active','inactive','pending','suspended','banned'].map(s => <Option key={s} value={s}>{s.charAt(0).toUpperCase() + s.slice(1)}</Option>)}
          </Select>
          <Button icon={<ExportOutlined />} onClick={exportCSV} style={{ marginLeft: 'auto' }}>Export CSV</Button>
        </div>
        {error && <Alert message={error} type="warning" style={{ margin: '8px 16px' }} />}
        <Table
          columns={columns} dataSource={users} rowKey="id" loading={loading}
          size="small"
          pagination={{ current: page, pageSize, total: users.length, onChange: (p, ps) => { setPage(p); setPageSize(ps) }, showSizeChanger: true, pageSizeOptions: ['10','25','50'] }}
          style={{ borderRadius: 0 }}
        />
      </Card>

      <Modal open={!!editModal} onCancel={() => setEditModal(null)} footer={null} title={`Edit ${editModal?.field} — ${editModal?.user?.username}`}>
        {editModal?.field === 'status' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            {['active','inactive','pending','suspended','banned'].map(s => (
              <Button key={s} block type={editModal.user.status === s ? 'primary' : 'default'} onClick={() => handleEditSave(s)}>
                {s.charAt(0).toUpperCase() + s.slice(1)}
              </Button>
            ))}
          </Space>
        )}
        {editModal?.field === 'role' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            {['user','moderator','admin','guest'].map(r => (
              <Button key={r} block type={editModal.user.role === r ? 'primary' : 'default'} onClick={() => handleEditSave(r)}>
                {r.charAt(0).toUpperCase() + r.slice(1)}
              </Button>
            ))}
          </Space>
        )}
      </Modal>
    </>
  )
}

// ── Logs Tab ──────────────────────────────────────────────────
function LogsTab() {
  const { logs, stats, loading, isLive, filterPlatform, setFilterPlatform, filterAction, setFilterAction, filterUser, setFilterUser, reload } = useLogs()

  const statsCards = stats ? [
    { label: "Today's Events", value: stats.today ?? 0, color: '#1f2937', accent: '#9ca3af' },
    { label: 'Logins Today', value: stats.logins_today ?? 0, color: '#16a34a', accent: '#10b981' },
    { label: 'App Events', value: stats.app_events ?? 0, color: '#1565C0', accent: '#3b82f6' },
    { label: 'Web Events', value: stats.web_events ?? 0, color: '#7c3aed', accent: '#8b5cf6' },
    { label: 'Failed Logins', value: stats.failed_logins ?? 0, color: '#dc2626', accent: '#ef4444' },
  ] : []

  const columns = [
    { title: 'Time', dataIndex: 'created_at', key: 'time', width: 160,
      render: v => v ? new Date(v).toLocaleString() : '—'
    },
    { title: 'User', dataIndex: 'username', key: 'user', render: v => <Text code>{v}</Text> },
    { title: 'Action', dataIndex: 'action', key: 'action',
      render: v => <Tag color={ACTION_COLOR[v] || 'default'} style={{ fontWeight: 600 }}>{v}</Tag>
    },
    { title: 'Platform', dataIndex: 'platform', key: 'platform',
      render: v => <Tag color={v === 'web' ? 'blue' : 'green'}>{v}</Tag>
    },
    { title: 'Details', dataIndex: 'details', key: 'details',
      render: v => v ? <Text type="secondary" style={{ fontSize: 12 }}>{JSON.stringify(v)}</Text> : '—'
    },
  ]

  return (
    <>
      {stats && (
        <Row gutter={[12, 12]} style={{ marginBottom: 20 }}>
          {statsCards.map(({ label, value, color, accent }) => (
            <Col span={5} key={label}>
              <Card size="small" style={{ borderRadius: 12, borderLeft: `4px solid ${accent}` }}>
                <Text style={{ fontSize: 10, color: '#9ca3af', textTransform: 'uppercase', fontWeight: 700, letterSpacing: 0.8 }}>{label}</Text>
                <div style={{ fontSize: 32, fontWeight: 900, color, marginTop: 4, lineHeight: 1 }}>{value}</div>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Card style={{ borderRadius: 16 }} bodyStyle={{ padding: 0 }}>
        <div style={{ padding: '10px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <Badge status={isLive ? 'success' : 'default'} text={<Text style={{ fontSize: 12, fontWeight: 600, color: isLive ? '#16a34a' : '#9ca3af' }}>{isLive ? 'Live' : 'Connecting…'}</Text>} />
          <div style={{ width: 1, height: 16, background: '#e5e7eb' }} />
          <Select value={filterPlatform || undefined} onChange={v => setFilterPlatform(v ?? '')} placeholder="All Platforms" allowClear style={{ width: 140 }}>
            <Option value="app">App</Option>
            <Option value="web">Web</Option>
          </Select>
          <Select value={filterAction || undefined} onChange={v => setFilterAction(v ?? '')} placeholder="All Actions" allowClear style={{ width: 180 }}>
            {['LOGIN','LOGIN_FAILED','LOGOUT','REGISTER','PASSWORD_RESET','DIAGNOSTIC_START','DIAGNOSTIC_STOP','ACTIVE_TEST_RUN','RAW_EXPORT','USER_STATUS_CHANGED'].map(a => (
              <Option key={a} value={a}>{a}</Option>
            ))}
          </Select>
          <Input placeholder="Filter by username…" value={filterUser} onChange={e => setFilterUser(e.target.value)} style={{ width: 180 }} />
          <Button icon={<ReloadOutlined />} onClick={reload} style={{ marginLeft: 'auto' }}>Refresh</Button>
        </div>
        <Table columns={columns} dataSource={logs} rowKey="id" loading={loading} size="small" pagination={{ pageSize: 50, showSizeChanger: false }} />
      </Card>
    </>
  )
}

// ── Main AdminPage ────────────────────────────────────────────
export default function AdminPage() {
  const items = [
    { key: 'users', label: 'Users', children: <UsersTab /> },
    { key: 'logs', label: 'Activity Logs', children: <LogsTab /> },
    { key: 'profile', label: 'My Profile', children: <DashboardPage embedded /> },
    { key: 'wiring', label: 'Wiring Diagram', children: (
      <Card style={{ borderRadius: 16 }}>
        <iframe
          src="http://localhost:9091/wiring_diagram.html"
          style={{ width: '100%', height: 600, border: 'none', borderRadius: 8 }}
          title="Wiring Diagram"
        />
      </Card>
    )},
  ]

  return (
    <AppLayout>
      <div style={{ marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>👑 Admin Panel</Title>
      </div>
      <Tabs items={items} size="large" />
    </AppLayout>
  )
}
```

- [ ] **Step 2: Sửa DashboardPage để hỗ trợ `embedded` prop**

Mở `web/src/pages/DashboardPage.jsx`, thay dòng đầu của component:

```jsx
// Thay:
export default function DashboardPage() {
  ...
  return (
    <AppLayout>
      ...
    </AppLayout>
  )
}

// Thành:
export default function DashboardPage({ embedded = false }) {
  ...
  const content = (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      {/* ... giữ nguyên toàn bộ JSX bên trong AppLayout ... */}
    </div>
  )
  if (embedded) return content
  return <AppLayout>{content}</AppLayout>
}
```

- [ ] **Step 3: Verify AdminPage**

Đăng nhập với tài khoản admin, vào `/admin` — thấy 4 tabs. Tab Users hiển thị bảng user với search/filter. Tab Logs hiển thị activity logs với realtime badge.

- [ ] **Step 4: Commit**

```bash
git add web/src/pages/AdminPage.jsx web/src/pages/DashboardPage.jsx
git commit -m "feat(web): implement AdminPage with Users and Activity Logs tabs"
```

---

## Task 12: LandingPage

**Files:**
- Modify: `web/src/pages/LandingPage.jsx`

- [ ] **Step 1: Implement LandingPage**

`web/src/pages/LandingPage.jsx`:
```jsx
import { useEffect, useState } from 'react'
import { Button, Typography, Row, Col, Card, Avatar, Space, Tag } from 'antd'
import {
  ClockCircleOutlined, WarningOutlined, LaptopOutlined,
  LineChartOutlined, ToolOutlined, ApiOutlined,
} from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { logout as authLogout } from '../services/auth'

const { Text, Title, Paragraph } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']
function avatarColor(u = '') {
  let h = 0; for (const c of u) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

// ── Navigation ──────────────────────────────────────────────
function Navbar({ session, profile, role, onSignOut }) {
  const navigate = useNavigate()
  const username = profile?.username ?? session?.user?.email?.split('@')[0] ?? 'User'
  const initial = username[0]?.toUpperCase() ?? 'U'
  const bg = avatarColor(username)

  return (
    <nav style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 50, background: 'rgba(255,255,255,0.92)', backdropFilter: 'blur(8px)', borderBottom: '1px solid #f0f0f0', height: 64, display: 'flex', alignItems: 'center', padding: '0 24px', justifyContent: 'space-between' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 36, height: 36, borderRadius: 8 }} alt="logo" />
        <Text strong style={{ color: '#003291', fontSize: 17 }}>BK Diagnostic</Text>
      </div>
      <Space size={24} style={{ display: 'flex' }}>
        <a href="#features" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Features</a>
        <a href="#hardware" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Hardware</a>
        <a href="#technology" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Technology</a>
        <a href="#team" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Team</a>
      </Space>
      <Space>
        {session ? (
          <>
            <Avatar style={{ background: bg, fontWeight: 700, cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>{initial}</Avatar>
            <Text strong style={{ color: '#374151' }}>{username}</Text>
            <Button type="primary" onClick={() => navigate('/dashboard')}>Dashboard</Button>
            <Button danger onClick={onSignOut}>Sign Out</Button>
          </>
        ) : (
          <Button type="primary" onClick={() => navigate('/login')}>Sign In</Button>
        )}
      </Space>
    </nav>
  )
}

// ── Features data ─────────────────────────────────────────────
const FEATURES = [
  { icon: <ClockCircleOutlined style={{ fontSize: 24, color: '#1565C0' }} />, bg: '#eff6ff', title: 'Real-Time Live Data', desc: 'Displays RPM, vehicle speed, engine temperature, engine load, and 15+ sensor parameters continuously updated through an animated gauge interface.' },
  { icon: <WarningOutlined style={{ fontSize: 24, color: '#ea580c' }} />, bg: '#fff7ed', title: 'Read & Clear Fault Codes (DTC)', desc: 'Scan and decode OBD2 fault codes (P/C/B/U codes) from all ECUs. Clear DTCs and reset the Check Engine warning light in a single action.' },
  { icon: <LaptopOutlined style={{ fontSize: 24, color: '#16a34a' }} />, bg: '#f0fdf4', title: 'ECU & VIN Information', desc: 'Read vehicle VIN, ECU software version, and calibration numbers from modules: engine, transmission, ABS, airbag, and BCM.' },
  { icon: <LineChartOutlined style={{ fontSize: 24, color: '#7c3aed' }} />, bg: '#faf5ff', title: 'Data Graph & Logger', desc: 'Plot real-time graphs for multiple parameters simultaneously. Log data to CSV files for offline analysis after a test drive.' },
  { icon: <ToolOutlined style={{ fontSize: 24, color: '#dc2626' }} />, bg: '#fef2f2', title: 'Actuator Tests', desc: 'Activate and test actuators: EGR valve, fuel pump, electric fan, ABS system — via UDS Mode 31 (Routine Control).' },
  { icon: <ApiOutlined style={{ fontSize: 24, color: '#475569' }} />, bg: '#f8fafc', title: 'Raw Frame Monitor', desc: 'View raw CAN bytes side-by-side with decoded results — a debug tool for engineers to verify the correctness of the data decoding pipeline.', badge: 'Admin only' },
]

// ── Hardware pipeline steps ───────────────────────────────────
const PIPELINE = [
  { emoji: '🚗', label: 'Vehicle', sub: 'CAN Bus\n250/500 kbps', color: '#1565C0', bg: '#eff6ff' },
  { emoji: '🔌', label: 'MCP2515', sub: 'CAN Controller\nSPI interface', color: '#ea580c', bg: '#fff7ed' },
  { emoji: '⚙️', label: 'STM32', sub: 'Frame protocol\nBinary framing', color: '#16a34a', bg: '#f0fdf4' },
  { emoji: '🔗', label: 'CP2102', sub: 'USB-UART Bridge\nSilicon Labs', color: '#7c3aed', bg: '#faf5ff' },
  { emoji: '📱', label: 'Android App', sub: 'USB Host Mode\nDecode + Display', color: '#1565C0', bg: '#eff6ff' },
]

const TECH = [
  { emoji: '🤖', name: 'Android', desc: 'Kotlin · Jetpack Compose\nMaterial 3 · API 24+' },
  { emoji: '☁️', name: 'Supabase', desc: 'Auth · PostgreSQL\nUser management' },
  { emoji: '🔌', name: 'USB Serial', desc: 'usb-serial-for-android\nCP2102 · UART' },
  { emoji: '📡', name: 'CAN Bus', desc: 'ISO 15765-4\nSAE J1979 · UDS' },
]

const TEAM = [
  { name: 'Đoàn Anh Kiệt', id: '2211716', role: 'Team Leader\nAndroid app · Protocol', avatar: 'https://i.ibb.co/Lz2Rx3Z7/avt-k.jpg', isImg: true },
  { name: 'Trần Phan Duy', id: '22xxxxxx', role: 'Member\nSTM32 · Hardware', initial: 'B', bg: '#1d4ed8' },
  { name: 'Trương Việt Hoàng', id: '22xxxxxx', role: 'Member\nOBD2 Protocol · Testing', initial: 'C', bg: '#4f46e5' },
]

// ── Main Component ────────────────────────────────────────────
export default function LandingPage() {
  const { session, profile, role, logout } = useAuth()
  const navigate = useNavigate()

  async function handleSignOut() { await logout(); navigate('/') }

  return (
    <div style={{ fontFamily: "'Inter', sans-serif", background: '#fff', color: '#1f2937' }}>
      <Navbar session={session} profile={profile} role={role} onSignOut={handleSignOut} />

      {/* ── Hero ────────────────────────────────────────────── */}
      <section style={{ paddingTop: 128, paddingBottom: 96, background: 'linear-gradient(135deg,#0A1E6E 0%,#1565C0 50%,#1E88E5 100%)', color: '#fff' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto', padding: '0 24px', display: 'flex', alignItems: 'center', gap: 48, flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 280 }}>
            <Tag style={{ background: 'rgba(255,255,255,0.15)', border: 'none', color: 'rgba(255,255,255,0.9)', fontWeight: 700, letterSpacing: 2, marginBottom: 20, borderRadius: 20, padding: '4px 12px', fontSize: 11 }}>
              AUTOMOTIVE ENGINEERING PROJECT · HCMUT
            </Tag>
            <Title level={1} style={{ color: '#fff', fontSize: 48, lineHeight: 1.1, margin: '0 0 20px' }}>
              Intelligent Vehicle<br /><span style={{ color: '#bfdbfe' }}>Diagnostic System</span>
            </Title>
            <Paragraph style={{ color: 'rgba(255,255,255,0.8)', fontSize: 17, lineHeight: 1.7, marginBottom: 32, maxWidth: 500 }}>
              Android application that reads CAN bus data directly from vehicles through the{' '}
              <strong style={{ color: '#fff' }}>MCP2515 → STM32 → CP2102</strong> hardware interface,
              supporting OBD2, Ford UDS Mode&nbsp;22, and other OEM protocols.
            </Paragraph>
            <Space size={12}>
              <Button size="large" style={{ background: '#fff', color: '#003291', fontWeight: 700, border: 'none', borderRadius: 12 }} onClick={() => document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' })}>Explore Features</Button>
              <Button size="large" ghost style={{ borderRadius: 12, fontWeight: 600 }} onClick={() => document.getElementById('hardware')?.scrollIntoView({ behavior: 'smooth' })}>View Hardware Architecture</Button>
            </Space>
          </div>
          {/* Phone mockup */}
          <div style={{ width: 220, flexShrink: 0 }}>
            <div style={{ background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.2)', borderRadius: 28, padding: 16 }}>
              <div style={{ background: '#111827', borderRadius: 20, padding: 12, aspectRatio: '9/18', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 10, fontWeight: 600 }}>Ford Ranger · Live Data</Text>
                  <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#4ade80' }} />
                </div>
                <div style={{ background: 'rgba(107,114,128,0.4)', borderRadius: 12, padding: 12, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                  <div style={{ width: 64, height: 32, borderTop: 0, borderLeft: '4px solid #60a5fa', borderRight: '4px solid #60a5fa', borderBottom: '4px solid transparent', borderRadius: '50% 50% 0 0 / 100% 100% 0 0' }} />
                  <Text style={{ color: '#93c5fd', fontSize: 11, fontWeight: 700, marginTop: 4 }}>1,724 RPM</Text>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, flex: 1 }}>
                  {[['Speed','62 km/h','#86efac'],['Temp','91 °C','#fde047'],['Engine Load','43 %','#93c5fd'],['Throttle','28 %','#d8b4fe']].map(([l,v,c]) => (
                    <div key={l} style={{ background: 'rgba(107,114,128,0.4)', borderRadius: 8, padding: 8 }}>
                      <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: 9 }}>{l}</Text>
                      <div style={{ color: c, fontSize: 13, fontWeight: 700, marginTop: 4 }}>{v}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── Stats Bar ────────────────────────────────────────── */}
      <section style={{ background: '#003291', padding: '32px 24px' }}>
        <Row justify="center" gutter={[32, 16]} style={{ maxWidth: 1152, margin: '0 auto' }}>
          {[['8+','Supported Brands'],['50+','Sensors & Parameters'],['500ms','Data Update Cycle'],['OBD2','ISO 15765-4 / SAE J1979']].map(([v, l]) => (
            <Col key={l} style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 32, fontWeight: 900, color: '#bfdbfe' }}>{v}</div>
              <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13 }}>{l}</Text>
            </Col>
          ))}
        </Row>
      </section>

      {/* ── Features ─────────────────────────────────────────── */}
      <section id="features" style={{ padding: '80px 24px', background: '#f9fafb' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 56 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>Capabilities</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Full Diagnostic Feature Set</Title>
            <Text type="secondary">From real-time data streaming to fault code analysis and actuator testing</Text>
          </div>
          <Row gutter={[24, 24]}>
            {FEATURES.map(({ icon, bg, title, desc, badge }) => (
              <Col xs={24} md={8} key={title}>
                <Card hoverable style={{ borderRadius: 20, height: '100%', border: '1px solid #f0f0f0' }}>
                  <div style={{ width: 48, height: 48, background: bg, borderRadius: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>{icon}</div>
                  <Title level={5} style={{ margin: '0 0 8px' }}>{title}</Title>
                  <Text type="secondary" style={{ fontSize: 13, lineHeight: 1.7 }}>{desc}</Text>
                  {badge && <Tag style={{ marginTop: 8, borderRadius: 20, background: '#f1f5f9', color: '#64748b', border: 'none', fontSize: 11, fontWeight: 600 }}>{badge}</Tag>}
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      </section>

      {/* ── Hardware Pipeline ──────────────────────────────────── */}
      <section id="hardware" style={{ padding: '80px 24px', background: '#fff' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 56 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>System Architecture</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Data Transmission Pipeline</Title>
            <Text type="secondary">CAN bus data from the vehicle passes through a hardware chain before being processed and displayed by the app</Text>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flexWrap: 'wrap', gap: 0 }}>
            {PIPELINE.map(({ emoji, label, sub, color, bg }, i) => (
              <div key={label} style={{ display: 'flex', alignItems: 'center' }}>
                <div style={{ border: `2px solid ${color}`, background: bg, borderRadius: 20, padding: 20, width: 130, textAlign: 'center' }}>
                  <div style={{ fontSize: 28, marginBottom: 6 }}>{emoji}</div>
                  <Text strong style={{ color, display: 'block', fontSize: 13 }}>{label}</Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>{sub.split('\n').map((t, i) => <span key={i}>{t}<br /></span>)}</Text>
                </div>
                {i < PIPELINE.length - 1 && <div style={{ width: 32, height: 1, background: '#d1d5db', flexShrink: 0 }} />}
              </div>
            ))}
          </div>
          <Row gutter={[24, 24]} style={{ marginTop: 48 }}>
            <Col xs={24} md={8}>
              <div style={{ background: '#f9fafb', borderRadius: 14, padding: 20, border: '1px solid #f0f0f0' }}>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>Frame Protocol</Text>
                <code style={{ fontSize: 12, color: '#4b5563', background: '#fff', display: 'block', padding: 12, borderRadius: 8, border: '1px solid #e5e7eb', lineHeight: 1.8 }}>
                  [0xAA][TYPE][LEN]<br />[PAYLOAD...][XOR][0x55]
                </code>
                <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Binary framing with XOR checksum ensures data integrity</Text>
              </div>
            </Col>
            <Col xs={24} md={8}>
              <div style={{ background: '#f9fafb', borderRadius: 14, padding: 20, border: '1px solid #f0f0f0' }}>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>Supported Protocols</Text>
                {[['#60a5fa','OBD2 Mode 01 — Live Data (standard)'],['#4ade80','OBD2 Mode 03/04 — DTC Read/Clear'],['#fb923c','Ford UDS Mode 22 — Proprietary PIDs'],['#d1d5db','More OEMs (Toyota, Hyundai…)']].map(([c, t]) => (
                  <div key={t} style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: c, flexShrink: 0 }} />
                    <Text style={{ fontSize: 12 }}>{t}</Text>
                  </div>
                ))}
              </div>
            </Col>
            <Col xs={24} md={8}>
              <div style={{ background: '#f9fafb', borderRadius: 14, padding: 20, border: '1px solid #f0f0f0' }}>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>Ford — Proprietary PIDs</Text>
                {[['Turbo Boost (DID 0x1046)'],['DPF Pressure (DID 0x1030)'],['EGR Rate (DID 0x2EF1)'],['Trans. Temp (DID 0x1940)']].map(([t]) => (
                  <div key={t} style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#60a5fa', flexShrink: 0 }} />
                    <Text style={{ fontSize: 12 }}>{t}</Text>
                  </div>
                ))}
              </div>
            </Col>
          </Row>
        </div>
      </section>

      {/* ── Technology Stack ──────────────────────────────────── */}
      <section id="technology" style={{ padding: '80px 24px', background: '#f9fafb' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 48 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>Tech Stack</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Technologies Used</Title>
          </div>
          <Row gutter={[16, 16]} justify="center">
            {TECH.map(({ emoji, name, desc }) => (
              <Col xs={12} md={6} key={name}>
                <Card hoverable style={{ borderRadius: 16, textAlign: 'center', border: '1px solid #f0f0f0' }}>
                  <div style={{ fontSize: 36, marginBottom: 12 }}>{emoji}</div>
                  <Text strong style={{ fontSize: 15, display: 'block' }}>{name}</Text>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>{desc.split('\n').map((t, i) => <span key={i}>{t}<br /></span>)}</Text>
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      </section>

      {/* ── Team ─────────────────────────────────────────────── */}
      <section id="team" style={{ padding: '80px 24px', background: '#fff' }}>
        <div style={{ maxWidth: 960, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 48 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>Team</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Project Members</Title>
            <Text type="secondary">Course project — Faculty of Transportation Engineering, HCMUT</Text>
          </div>
          <Row gutter={[24, 24]}>
            {TEAM.map(({ name, id, role: r, avatar, isImg, initial, bg }) => (
              <Col xs={24} md={8} key={name}>
                <Card hoverable style={{ borderRadius: 20, textAlign: 'center', border: '1px solid #f0f0f0' }}>
                  {isImg
                    ? <img src={avatar} alt={name} style={{ width: 80, height: 80, borderRadius: '50%', objectFit: 'cover', border: '2px solid #003291', marginBottom: 16 }} />
                    : <Avatar size={80} style={{ background: bg, fontWeight: 700, fontSize: 32, marginBottom: 16 }}>{initial}</Avatar>
                  }
                  <Title level={5} style={{ margin: 0 }}>{name}</Title>
                  <Text style={{ color: '#1565C0', fontSize: 13, fontWeight: 600, display: 'block', marginTop: 4 }}>ID: {id}</Text>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>{r.split('\n').map((t, i) => <span key={i}>{t}<br /></span>)}</Text>
                </Card>
              </Col>
            ))}
          </Row>
          <div style={{ marginTop: 32, background: '#eff6ff', borderRadius: 16, padding: 20, border: '1px solid #dbeafe', textAlign: 'center' }}>
            <Text style={{ color: '#1565C0', fontSize: 14, fontWeight: 500 }}>
              🎓 Supervisor: <strong>M.S. Phạm Trần Đăng Quang</strong> — Automotive Engineering Department, Faculty of Transportation Engineering
            </Text>
          </div>
        </div>
      </section>

      {/* ── Footer ────────────────────────────────────────────── */}
      <footer style={{ background: '#003291', color: '#fff', padding: '48px 24px' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <Row justify="space-between" gutter={[32, 32]} style={{ marginBottom: 32 }}>
            <Col>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 32, height: 32, borderRadius: 8 }} alt="logo" />
                <Text strong style={{ color: '#fff', fontSize: 17 }}>BK Diagnostic</Text>
              </div>
              <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: 13, maxWidth: 300, display: 'block', lineHeight: 1.7 }}>
                Intelligent vehicle diagnostic system — Automotive Engineering Project<br />
                Ho Chi Minh City University of Technology (HCMUT)
              </Text>
            </Col>
            <Col>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 48px' }}>
                {[['#features','Features'],['#hardware','Hardware'],['#technology','Technology'],['#team','Team']].map(([href, label]) => (
                  <a key={href} href={href} style={{ color: 'rgba(255,255,255,0.7)', fontSize: 14, textDecoration: 'none' }}>{label}</a>
                ))}
                <Link to="/login" style={{ color: 'rgba(255,255,255,0.7)', fontSize: 14 }}>Sign In</Link>
              </div>
            </Col>
          </Row>
          <div style={{ borderTop: '1px solid rgba(255,255,255,0.15)', paddingTop: 24, textAlign: 'center' }}>
            <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>
              © 2026 BK Diagnostic · Ho Chi Minh City University of Technology · Faculty of Transportation Engineering
            </Text>
          </div>
        </div>
      </footer>
    </div>
  )
}
```

- [ ] **Step 2: Verify LandingPage**

Mở `http://localhost:5173/` — thấy Hero section màu xanh gradient, stats bar, 6 feature cards, hardware pipeline, team section, footer. Navbar hiển thị "Sign In" nếu chưa login, hoặc avatar + Dashboard button nếu đã login.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/LandingPage.jsx
git commit -m "feat(web): implement LandingPage with all sections"
```

---

## Task 13: Cập nhật launch.json + Final Verification

**Files:**
- Modify: `.claude/launch.json` (cập nhật Website Preview sang React)

- [ ] **Step 1: Cập nhật Website Preview trong launch.json**

Mở `.claude/launch.json`, thay entry `Website Preview`:

```json
{
  "name": "Website Preview",
  "runtimeExecutable": "npm",
  "runtimeArgs": ["run", "dev", "--prefix", "web", "--", "--port", "9090", "--host"],
  "port": 9090
}
```

- [ ] **Step 2: Chạy production build**

```bash
cd web && npm run build
```

Expected: build thành công, không có warnings nghiêm trọng.

- [ ] **Step 3: Final end-to-end verify**

Chạy dev server, kiểm tra toàn bộ flow:

1. `http://localhost:5173/` — Landing page hiển thị đầy đủ
2. Click "Sign In" → `/login` — split layout, form hoạt động
3. Đăng nhập thành công → redirect `/dashboard`
4. Dashboard hiển thị profile card đúng thông tin
5. Vào `/admin` với tài khoản admin → tabs Users + Activity Logs
6. Users tab: search, filter, click Status/Role tags → modal hiện ra
7. Logs tab: realtime badge xanh, filter hoạt động
8. `/forgot-password` — form email, submit thấy success message
9. `/register` — form validation hoạt động

- [ ] **Step 4: Final commit**

```bash
cd ..
git add web/ .claude/launch.json
git commit -m "feat(web): complete React + Ant Design migration

- LandingPage with Hero, Features, Hardware, Tech Stack, Team, Footer
- Auth pages: Login, Register, ForgotPassword, ResetPassword
- DashboardPage with profile card and change password
- AdminPage with Users (table, search, filter, pagination, CSV export) and Activity Logs (realtime, stats) tabs
- Services layer (auth, api) as adapter for future Node.js migration
- Custom hooks: useAuth, useUsers, useLogs"
```
