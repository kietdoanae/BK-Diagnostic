import { useState } from 'react'
import { Form, Input, Button, Alert, Typography, Grid } from 'antd'
import { Link, Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { register } from '../services/auth'
import { logActivity } from '../services/api'
import { useAuth } from '../hooks/useAuth'

const { useBreakpoint } = Grid

const { Text, Title } = Typography

export default function RegisterPage() {
  const { t } = useTranslation()
  const [formLoading, setFormLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const { session, loading } = useAuth()
  const [form] = Form.useForm()
  const screens = useBreakpoint()
  const isMobile = !screens.md
  const watchedEmail = Form.useWatch('email', form)
  const isEduVn = /\.edu\.vn$/i.test(watchedEmail || '')

  if (loading) return null
  if (session) return <Navigate to="/dashboard" replace />

  async function handleSubmit({ username, email, password, mssv }) {
    setError('')
    setFormLoading(true)

    const { error: err } = await register(email, password, username, mssv)

    if (err) {
      setFormLoading(false)
      const msg = err.message.includes('User already registered')
        ? t('registerPage.errEmailExists')
        : err.message.includes('Password should be')
        ? t('registerPage.errPasswordMin8')
        : err.message
      setError(msg)
      return
    }

    logActivity('REGISTER', { username })
    setFormLoading(false)
    setSuccess(true)
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
            <Title level={3} style={{ color: '#fff', margin: 0, lineHeight: 1.3 }}>{t('registerPage.heroTitle')}</Title>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13, marginTop: 12, display: 'block', lineHeight: 1.6 }}>
              {t('registerPage.heroDesc')}
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
          <Title level={3} style={{ marginBottom: 4 }}>{t('auth.registerTitle')}</Title>
          <Text type="secondary" style={{ marginBottom: 28, display: 'block' }}>{t('registerPage.fillDetails')}</Text>

          {error && <Alert message={error} type="error" showIcon style={{ marginBottom: 20 }} />}

          {success ? (
            <Alert
              type="success"
              message={t('auth.registerSuccess')}
              description={t('auth.registerSuccessMsg')}
              showIcon
            />
          ) : (
            <Form form={form} layout="vertical" onFinish={handleSubmit} size="large">
              <Form.Item
                label={t('auth.username')}
                name="username"
                rules={[
                  { required: true, message: t('registerPage.errUsernameRequired') },
                  { min: 3, message: t('registerPage.errUsernameMin') },
                  { max: 20, message: t('registerPage.errUsernameMax') },
                  { pattern: /^[a-zA-Z0-9_]+$/, message: t('registerPage.errUsernamePattern') },
                ]}
              >
                <Input placeholder={t('registerPage.placeholderUsername')} autoComplete="username" />
              </Form.Item>
              <Form.Item
                label={t('auth.email')}
                name="email"
                rules={[
                  { required: true, message: t('registerPage.errEmailRequired') },
                  { type: 'email', message: t('registerPage.errEmailInvalid') },
                ]}
              >
                <Input placeholder={t('registerPage.placeholderEmail')} autoComplete="email" />
              </Form.Item>
              <Form.Item
                label={t('auth.password')}
                name="password"
                rules={[
                  { required: true, message: t('registerPage.errPasswordRequired') },
                  { min: 8, message: t('registerPage.errPasswordMin8') },
                ]}
              >
                <Input.Password placeholder="••••••••" autoComplete="new-password" />
              </Form.Item>
              <Form.Item
                label={t('auth.confirmPassword')}
                name="confirmPassword"
                rules={[
                  { required: true, message: t('registerPage.errConfirmRequired') },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve()
                      }
                      return Promise.reject(new Error(t('auth.errMismatch')))
                    },
                  }),
                ]}
              >
                <Input.Password placeholder="••••••••" autoComplete="new-password" />
              </Form.Item>
              {isEduVn && (
                <>
                  <Alert
                    type="info"
                    showIcon
                    message={t('registerPage.eduVnRoleTitle')}
                    description={t('registerPage.eduVnRoleDesc')}
                    style={{ marginBottom: 16 }}
                  />
                  <Form.Item
                    label={t('registerPage.mssvOptionalLabel')}
                    name="mssv"
                    rules={[
                      { pattern: /^\d{7,8}$/, message: t('registerPage.errMssvPattern') },
                    ]}
                  >
                    <Input placeholder={t('registerPage.mssvPlaceholder')} maxLength={8} />
                  </Form.Item>
                </>
              )}
              <Button type="primary" htmlType="submit" block loading={formLoading} style={{ height: 44, fontWeight: 600, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}>
                {t('auth.btnSignUp')}
              </Button>
            </Form>
          )}

          <Text style={{ marginTop: 24, display: 'block', textAlign: 'center', color: '#6b7280' }}>
            {t('auth.haveAccount')} <Link to="/login" style={{ fontWeight: 600 }}>{t('auth.btnSignIn')}</Link>
          </Text>
          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Link to="/" style={{ fontSize: 12, color: '#9ca3af' }}>{t('loginPage.backHome')}</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
