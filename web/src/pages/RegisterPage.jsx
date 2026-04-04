import { useState } from 'react'
import { Form, Input, Button, Alert, Typography } from 'antd'
import { Link, Navigate } from 'react-router-dom'
import { register } from '../services/auth'
import { useAuth } from '../hooks/useAuth'

const { Text, Title } = Typography

export default function RegisterPage() {
  const [formLoading, setFormLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const { session, loading } = useAuth()
  const [form] = Form.useForm()

  if (loading) return null
  if (session) return <Navigate to="/dashboard" replace />

  async function handleSubmit({ username, email, password }) {
    setError('')
    setFormLoading(true)

    const { error: err } = await register(email, password, username)

    if (err) {
      setFormLoading(false)
      const msg = err.message.includes('User already registered')
        ? 'An account with this email already exists.'
        : err.message.includes('Password should be')
        ? 'Password must be at least 8 characters.'
        : err.message
      setError(msg)
      return
    }

    setFormLoading(false)
    setSuccess(true)
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
            <Title level={3} style={{ color: '#fff', margin: 0, lineHeight: 1.3 }}>Create your<br />account</Title>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13, marginTop: 12, display: 'block', lineHeight: 1.6 }}>
              Join BK Diagnostic to access your dashboard, view diagnostic history, and manage your vehicle.
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
          <Title level={3} style={{ marginBottom: 4 }}>Create Account</Title>
          <Text type="secondary" style={{ marginBottom: 28, display: 'block' }}>Fill in the details below to get started</Text>

          {error && <Alert message={error} type="error" showIcon style={{ marginBottom: 20 }} />}

          {success ? (
            <Alert
              type="success"
              message="Account created!"
              description="Please check your email to confirm your account before signing in."
              showIcon
            />
          ) : (
            <Form form={form} layout="vertical" onFinish={handleSubmit} size="large">
              <Form.Item
                label="Username"
                name="username"
                rules={[
                  { required: true, message: 'Please enter a username' },
                  { min: 3, message: 'Username must be at least 3 characters' },
                  { max: 20, message: 'Username must be at most 20 characters' },
                  { pattern: /^[a-zA-Z0-9_]+$/, message: 'Only letters, numbers, and underscores allowed' },
                ]}
              >
                <Input placeholder="Enter your username" autoComplete="username" />
              </Form.Item>
              <Form.Item
                label="Email"
                name="email"
                rules={[
                  { required: true, message: 'Please enter your email' },
                  { type: 'email', message: 'Please enter a valid email' },
                ]}
              >
                <Input placeholder="Enter your email" autoComplete="email" />
              </Form.Item>
              <Form.Item
                label="Password"
                name="password"
                rules={[
                  { required: true, message: 'Please enter a password' },
                  { min: 8, message: 'Password must be at least 8 characters' },
                ]}
              >
                <Input.Password placeholder="••••••••" autoComplete="new-password" />
              </Form.Item>
              <Form.Item
                label="Confirm Password"
                name="confirmPassword"
                rules={[
                  { required: true, message: 'Please confirm your password' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve()
                      }
                      return Promise.reject(new Error('Passwords do not match'))
                    },
                  }),
                ]}
              >
                <Input.Password placeholder="••••••••" autoComplete="new-password" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={formLoading} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}>
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
