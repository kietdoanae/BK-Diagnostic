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

export default function DashboardPage({ embedded = false }) {
  const { session, profile, role } = useAuth()
  const [pwMsg, setPwMsg] = useState(null)
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
    const { error: reAuthErr } = await supabase.auth.signInWithPassword({ email, password: current })
    if (reAuthErr) { setPwLoading(false); setPwMsg({ type: 'error', text: 'Current password is incorrect.' }); return }
    const { error } = await supabase.auth.updateUser({ password: newPw })
    setPwLoading(false)
    if (error) setPwMsg({ type: 'error', text: error.message })
    else { setPwMsg({ type: 'success', text: 'Password updated successfully.' }); form.resetFields() }
  }

  const content = (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <Card style={{ borderRadius: 24, marginBottom: 24, overflow: 'hidden', border: '1px solid #e8edf5' }} styles={{ body: { padding: 0 } }}>
        <div style={{ height: 140, background: 'linear-gradient(135deg,#003291 0%,#1565C0 50%,#1E88E5 80%,#bfdbfe 100%)', position: 'relative' }}>
          <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 60, background: 'linear-gradient(to bottom, transparent, rgba(255,255,255,0.85))' }} />
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
  )

  if (embedded) return content
  return <AppLayout>{content}</AppLayout>
}
