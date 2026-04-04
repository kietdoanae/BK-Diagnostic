import { useState } from 'react'
import { Form, Input, Button, Alert, Typography } from 'antd'
import { EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons'
import { Link, useNavigate, Navigate } from 'react-router-dom'
import { login, getProfile, logout as authLogout } from '../services/auth'
import { useAuth } from '../hooks/useAuth'

const { Text, Title } = Typography

const BLOCKED = {
  banned: '🚫 Your account has been permanently banned. Contact support if you think this is a mistake.',
  suspended: '⏸️ Your account has been temporarily suspended. Please contact support.',
  inactive: '💤 Your account is inactive. Please contact support to reactivate.',
  pending: '⏳ Your account is pending approval by an administrator. Please check back later.',
}

export default function LoginPage() {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const { session, loading } = useAuth()
  const navigate = useNavigate()
  const [form] = Form.useForm()

  if (loading) return null
  if (session) return <Navigate to="/dashboard" replace />

  async function handleSubmit({ identifier, password }) {
    setError('')
    setSubmitting(true)

    const { data, error: err } = await login(identifier, password)

    if (err) {
      setSubmitting(false)
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
        await authLogout()
        setSubmitting(false)
        setError(BLOCKED[status] ?? 'Your account is not active.')
        return
      }
    }

    setSubmitting(false)
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
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[
              { icon: '📈', text: 'Real-time live data' },
              { icon: '⚠️', text: 'Read & clear fault codes (DTC)' },
              { icon: '💻', text: 'ECU & VIN information' },
            ].map(({ icon, text }) => (
              <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 10, color: 'rgba(255,255,255,0.8)', fontSize: 13 }}>
                <div style={{ width: 28, height: 28, borderRadius: 8, background: 'rgba(255,255,255,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 14 }}>{icon}</div>
                {text}
              </div>
            ))}
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
            <Form.Item
              label="Password"
              name="password"
              rules={[{ required: true, message: 'Please enter your password' }]}
              extra={<Link to="/forgot-password" style={{ fontSize: 12 }}>Forgot password?</Link>}
            >
              <Input.Password placeholder="••••••••" autoComplete="current-password"
                iconRender={v => v ? <EyeOutlined /> : <EyeInvisibleOutlined />} />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={submitting} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}>
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
