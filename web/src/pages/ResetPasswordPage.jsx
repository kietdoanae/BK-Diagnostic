import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Alert, Typography } from 'antd'
import { resetPassword } from '../services/auth'

const { Title, Text } = Typography

export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(false)

  async function handleSubmit({ password }) {
    setError(null)
    const { error: err } = await resetPassword(password)
    if (err) {
      setError(err.message)
    } else {
      setSuccess(true)
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

        {success ? (
          <>
            <Alert
              type="success"
              message="Password updated!"
              description="Your password has been changed successfully."
              showIcon
              style={{ marginBottom: 24 }}
            />
            <Button
              type="primary"
              block
              style={{ height: 44, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}
              onClick={() => navigate('/dashboard')}
            >
              Go to Dashboard
            </Button>
          </>
        ) : (
          <>
            <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>Set New Password</Title>
            <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginBottom: 32 }}>
              Enter your new password below.
            </Text>

            {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
            <Form layout="vertical" size="large" onFinish={handleSubmit}>
              <Form.Item name="password" label="Password" rules={[{ required: true, min: 8, message: 'Password must be at least 8 characters' }]}>
                <Input.Password placeholder="••••••••" />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                label="Confirm Password"
                dependencies={['password']}
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
                <Input.Password placeholder="••••••••" />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  block
                  style={{ height: 44, background: 'linear-gradient(135deg, #1565C0, #1E88E5)', border: 'none' }}
                >
                  Update Password
                </Button>
              </Form.Item>
            </Form>
          </>
        )}
      </div>
    </div>
  )
}
