import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Alert, Typography, Spin } from 'antd'
import { useTranslation } from 'react-i18next'
import { supabase } from '../services/supabase'
import { resetPassword, logout } from '../services/auth'

const { Title, Text } = Typography

export default function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  // status: 'loading' | 'ready' | 'error' | 'success'
  const [status, setStatus] = useState('loading')
  const [errorMsg, setErrorMsg] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    // Check hash for error params (e.g. otp_expired)
    const hash = window.location.hash.slice(1)
    if (hash.includes('error=')) {
      const params = new URLSearchParams(hash)
      const desc = params.get('error_description') ?? t('resetPage.errLinkInvalidDefault')
      setErrorMsg(decodeURIComponent(desc.replace(/\+/g, ' ')))
      setStatus('error')
      return
    }

    // Listen for PASSWORD_RECOVERY event from Supabase
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event) => {
      if (event === 'PASSWORD_RECOVERY') {
        setStatus('ready')
      }
    })

    // Fallback: if session already exists with recovery token in hash
    if (hash.includes('type=recovery')) {
      supabase.auth.getSession().then(({ data: { session } }) => {
        if (session) setStatus('ready')
      })
    }

    return () => subscription.unsubscribe()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleSubmit({ password }) {
    setSubmitting(true)
    setErrorMsg(null)
    const { error } = await resetPassword(password)
    setSubmitting(false)
    if (error) {
      setErrorMsg(error.message)
    } else {
      setStatus('success')
      await logout()
      setTimeout(() => navigate('/login'), 2500)
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

        {status === 'loading' && (
          <div style={{ textAlign: 'center', padding: '32px 0' }}>
            <Spin size="large" />
            <Text type="secondary" style={{ display: 'block', marginTop: 16 }}>{t('resetPage.verifyingLink')}</Text>
          </div>
        )}

        {status === 'error' && (
          <>
            <Alert
              type="error"
              message={t('resetPage.linkInvalidTitle')}
              description={errorMsg}
              showIcon
              style={{ marginBottom: 24 }}
            />
            <Button
              block
              style={{ height: 44 }}
              onClick={() => navigate('/forgot-password')}
            >
              {t('resetPage.resendEmail')}
            </Button>
          </>
        )}

        {status === 'ready' && (
          <>
            <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>{t('auth.resetTitle')}</Title>
            <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginBottom: 32 }}>
              {t('auth.resetSubtitle')}
            </Text>

            {errorMsg && <Alert type="error" message={errorMsg} showIcon style={{ marginBottom: 16 }} />}
            <Form layout="vertical" size="large" onFinish={handleSubmit}>
              <Form.Item name="password" label={t('auth.password')} rules={[{ required: true, min: 8, message: t('registerPage.errPasswordMin8') }]}>
                <Input.Password placeholder="••••••••" />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                label={t('auth.confirmPassword')}
                dependencies={['password']}
                rules={[
                  { required: true, message: t('registerPage.errConfirmRequired') },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) return Promise.resolve()
                      return Promise.reject(new Error(t('auth.errMismatch')))
                    },
                  }),
                ]}
              >
                <Input.Password placeholder="••••••••" />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  block
                  loading={submitting}
                  style={{ height: 44, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}
                >
                  {t('auth.btnUpdate')}
                </Button>
              </Form.Item>
            </Form>
          </>
        )}

        {status === 'success' && (
          <>
            <Alert
              type="success"
              message={t('resetPage.successTitle')}
              description={t('resetPage.successDesc')}
              showIcon
              style={{ marginBottom: 24 }}
            />
          </>
        )}
      </div>
    </div>
  )
}
