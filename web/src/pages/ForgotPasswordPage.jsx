import { useState } from 'react'
import { Navigate, Link } from 'react-router-dom'
import { Form, Input, Button, Alert, Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../hooks/useAuth'
import { forgotPassword } from '../services/auth'

const { Title, Text } = Typography

export default function ForgotPasswordPage() {
  const { t } = useTranslation()
  const { session, loading } = useAuth()
  const [error, setError] = useState(null)
  const [sent, setSent] = useState(false)

  if (loading) return null
  if (session) return <Navigate to="/dashboard" replace />

  async function handleSubmit({ email }) {
    setError(null)
    // Always redirect to www to avoid naked domain (old server) consuming the token
    const host = window.location.hostname.replace(/^www\./, '')
    const redirectTo = `${window.location.protocol}//www.${host}/reset-password`
    const { error: err } = await forgotPassword(email, redirectTo)
    if (err) {
      setError(err.message)
    } else {
      setSent(true)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ width: '100%', maxWidth: 480, background: '#fff', borderRadius: 24, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.15)', padding: '48px 40px' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Link to="/">
            <img src="https://i.ibb.co/Z0Xc41Z/logo.png" alt="logo" style={{ width: 40, height: 40, borderRadius: 8 }} />
          </Link>
        </div>

        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>{t('auth.resetTitle')}</Title>
        <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginBottom: 32 }}>
          {t('auth.forgotSubtitle')}
        </Text>

        {sent ? (
          <Alert
            type="success"
            message={t('auth.forgotSuccessTitle')}
            description={t('forgotPage.checkInbox')}
            showIcon
          />
        ) : (
          <>
            {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
            <Form layout="vertical" size="large" onFinish={handleSubmit}>
              <Form.Item name="email" label={t('auth.email')} rules={[{ required: true, type: 'email', message: t('registerPage.errEmailInvalid') }]}>
                <Input placeholder="you@example.com" />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  block
                  style={{ height: 44, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}
                >
                  {t('auth.btnSendReset')}
                </Button>
              </Form.Item>
            </Form>
          </>
        )}

        <div style={{ textAlign: 'center', marginTop: 24 }}>
          <Text type="secondary">{t('forgotPage.rememberPassword')} </Text>
          <Link to="/login">{t('auth.btnSignIn')}</Link>
        </div>
      </div>
    </div>
  )
}
