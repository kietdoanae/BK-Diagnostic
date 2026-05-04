import { useState } from 'react'
import { Form, Input, Button, Alert, Typography, Grid } from 'antd'
import { EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons'
import { Link, useNavigate, Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { login, getProfile, logout as authLogout } from '../services/auth'
import { logActivity } from '../services/api'
import { useAuth } from '../hooks/useAuth'

const { useBreakpoint } = Grid

const { Text, Title } = Typography

export default function LoginPage() {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const { session, loading } = useAuth()
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const screens = useBreakpoint()
  const isMobile = !screens.md

  const BLOCKED = {
    banned: t('loginPage.blockedBanned'),
    suspended: t('loginPage.blockedSuspended'),
    inactive: t('loginPage.blockedInactive'),
    pending: t('loginPage.blockedPending'),
  }

  if (loading) return null
  if (session) return <Navigate to="/dashboard" replace />

  async function handleSubmit({ identifier, password }) {
    setError('')
    setSubmitting(true)

    const { data, error: err } = await login(identifier, password)

    if (err) {
      setSubmitting(false)
      const msg = err.message === 'No account found with this username.' ? t('loginPage.errNoAccount')
        : err.message.includes('Invalid login credentials') ? t('auth.errInvalid')
        : err.message.includes('Email not confirmed') ? t('loginPage.errEmailNotConfirmed')
        : err.message.includes('rate limit') ? t('loginPage.errRateLimit')
        : err.message
      setError(msg)
      logActivity('LOGIN_FAILED', { identifier })
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
        setError(BLOCKED[status] ?? t('loginPage.blockedDefault'))
        logActivity('LOGIN_FAILED', { reason: status })
        return
      }
    }

    logActivity('LOGIN')
    setSubmitting(false)
    navigate('/dashboard', { replace: true })
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: isMobile ? '16px' : 0 }}>
      <div style={{ width: '100%', maxWidth: isMobile ? 440 : 840, display: 'flex', borderRadius: 24, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.15)', minHeight: isMobile ? 0 : 520 }}>

        {/* Left panel — hidden on mobile */}
        {!isMobile && <div style={{ width: 300, flexShrink: 0, background: 'linear-gradient(135deg, #0A1E6E 0%, #1565C0 60%, #1E88E5 100%)', padding: 40, color: '#fff', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 40, opacity: 0.9 }}>
              <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 40, height: 40, borderRadius: 10, background: 'rgba(255,255,255,0.1)', padding: 4 }} alt="logo" />
              <span style={{ fontWeight: 700, fontSize: 15, color: '#fff' }}>{t('app.name')}</span>
            </Link>
            <Title level={3} style={{ color: '#fff', margin: 0, lineHeight: 1.3 }}>{t('loginPage.welcomeBack')}</Title>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13, marginTop: 12, display: 'block', lineHeight: 1.6 }}>
              {t('loginPage.welcomeDesc')}
            </Text>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[
              { icon: '📈', text: t('loginPage.feat1') },
              { icon: '⚠️', text: t('loginPage.feat2') },
              { icon: '💻', text: t('loginPage.feat3') },
            ].map(({ icon, text }) => (
              <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 10, color: 'rgba(255,255,255,0.8)', fontSize: 13 }}>
                <div style={{ width: 28, height: 28, borderRadius: 8, background: 'rgba(255,255,255,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 14 }}>{icon}</div>
                {text}
              </div>
            ))}
          </div>
          <Text style={{ color: 'rgba(255,255,255,0.4)', fontSize: 11 }}>{t('loginPage.copyright')}</Text>
        </div>}

        {/* Right panel */}
        <div style={{ flex: 1, background: '#fff', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: isMobile ? '32px 24px' : '40px 48px' }}>
          <Title level={3} style={{ marginBottom: 4 }}>{t('auth.loginTitle')}</Title>
          <Text type="secondary" style={{ marginBottom: 28, display: 'block' }}>{t('loginPage.enterCredentials')}</Text>

          {error && <Alert message={error} type="error" showIcon style={{ marginBottom: 20 }} />}

          <Form form={form} layout="vertical" onFinish={handleSubmit} size="large">
            <Form.Item label={t('auth.emailOrUsername')} name="identifier" rules={[{ required: true, message: t('loginPage.errIdentifierRequired') }]}>
              <Input placeholder={t('loginPage.placeholderIdentifier')} autoComplete="username" />
            </Form.Item>
            <Form.Item
              label={t('auth.password')}
              name="password"
              rules={[{ required: true, message: t('loginPage.errPasswordRequired') }]}
              extra={<Link to="/forgot-password" style={{ fontSize: 12 }}>{t('auth.forgotLink')}</Link>}
            >
              <Input.Password placeholder="••••••••" autoComplete="current-password"
                iconRender={v => v ? <EyeOutlined /> : <EyeInvisibleOutlined />} />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={submitting} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}>
              {t('auth.btnSignIn')}
            </Button>
          </Form>

          <Text style={{ marginTop: 24, display: 'block', textAlign: 'center', color: '#6b7280' }}>
            {t('auth.noAccount')} <Link to="/register" style={{ fontWeight: 600 }}>{t('loginPage.createAccount')}</Link>
          </Text>
          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Link to="/" style={{ fontSize: 12, color: '#9ca3af' }}>{t('loginPage.backHome')}</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
