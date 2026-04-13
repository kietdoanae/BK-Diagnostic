import { useState, useEffect } from 'react'
import { Card, Avatar, Typography, Tag, Row, Col, Form, Input, Button, Alert, Table, Space, Tooltip } from 'antd'
import { UserOutlined, CalendarOutlined, SafetyOutlined, MailOutlined, LockOutlined, DownloadOutlined, FileTextOutlined, ReloadOutlined, IdcardOutlined, CheckCircleOutlined, ContactsOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { supabase } from '../services/supabase'
import { useMyExports } from '../hooks/useExports'

const { Text, Title } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']
function avatarColor(u = '') {
  let h = 0; for (const c of u) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

const ROLE_COLOR = { admin: 'purple', moderator: 'blue', user: 'default', guest: 'default' }
const STATUS_COLOR = { active: 'success', inactive: 'default', banned: 'error', suspended: 'warning', pending: 'processing' }

function formatBytes(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

// ── Shared one-time update card ───────────────────────────────────────────────
function OnceUpdateCard({ icon, accentColor, title, subtitle, fieldName, inputPlaceholder, inputRules, currentValue, onSave }) {
  const [msg, setMsg] = useState(null)
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  async function handleSubmit(values) {
    setMsg(null)
    setLoading(true)
    const err = await onSave(values[fieldName].trim())
    setLoading(false)
    if (err) {
      setMsg({ type: 'error', text: err })
    } else {
      setMsg({ type: 'success', text: 'Cập nhật thành công!' })
      form.resetFields()
    }
  }

  if (currentValue) return null

  return (
    <Card style={{ borderRadius: 24, border: `2px solid ${accentColor}`, background: '#fffbeb', marginBottom: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <div style={{ width: 40, height: 40, borderRadius: 14, background: `linear-gradient(135deg,${accentColor},${accentColor}cc)`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {icon}
        </div>
        <div>
          <Text strong style={{ display: 'block', fontSize: 15 }}>{title}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{subtitle}</Text>
        </div>
      </div>
      {msg && <Alert message={msg.text} type={msg.type} showIcon style={{ marginBottom: 16 }} />}
      <Form form={form} layout="inline" onFinish={handleSubmit}>
        <Form.Item name={fieldName} rules={inputRules} style={{ flex: 1, minWidth: 200 }}>
          {inputPlaceholder}
        </Form.Item>
        <Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            style={{ background: `linear-gradient(135deg,${accentColor},${accentColor}cc)`, border: 'none', fontWeight: 600 }}
            icon={<CheckCircleOutlined />}
          >
            Xác nhận
          </Button>
        </Form.Item>
      </Form>
    </Card>
  )
}

// ── Export history ────────────────────────────────────────────────────────────
function ExportHistoryCard({ session }) {
  const userId = session?.user?.id
  const { records, loading, error, reload, getDownloadUrl } = useMyExports(userId)
  const [downloading, setDownloading] = useState({})

  async function handleDownload(storagePath, filename) {
    setDownloading(d => ({ ...d, [storagePath]: true }))
    const url = await getDownloadUrl(storagePath)
    if (url) {
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
    }
    setDownloading(d => ({ ...d, [storagePath]: false }))
  }

  const columns = [
    {
      title: 'Hãng / Model',
      dataIndex: 'display_name',
      key: 'display_name',
      render: v => (
        <Space>
          <SafetyOutlined style={{ color: '#1565C0' }} />
          <Text style={{ fontWeight: 600 }}>{v || '—'}</Text>
        </Space>
      )
    },
    {
      title: 'Tên file',
      dataIndex: 'filename',
      key: 'filename',
      render: v => (
        <Space>
          <FileTextOutlined style={{ color: '#1565C0' }} />
          <Text style={{ fontFamily: 'monospace', fontSize: 13 }}>{v}</Text>
        </Space>
      )
    },
    { title: 'Frames', dataIndex: 'frame_count', key: 'frame_count', render: v => v != null ? v.toLocaleString() : '—' },
    { title: 'Dung lượng', dataIndex: 'file_size_bytes', key: 'file_size_bytes', render: v => formatBytes(v) },
    {
      title: 'Thời gian',
      dataIndex: 'created_at',
      key: 'created_at',
      render: v => v ? new Date(v).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—'
    },
    {
      title: '',
      key: 'action',
      align: 'right',
      render: (_, row) => (
        <Button type="primary" size="small" icon={<DownloadOutlined />}
          loading={downloading[row.storage_path]}
          onClick={() => handleDownload(row.storage_path, row.filename)}>
          Tải xuống
        </Button>
      )
    }
  ]

  return (
    <Card
      style={{ borderRadius: 20, border: '1px solid #e8edf5', marginTop: 24 }}
      title={
        <Space>
          <FileTextOutlined style={{ color: '#1565C0' }} />
          <span style={{ fontWeight: 700 }}>Lịch sử xuất file CAN</span>
          <Text type="secondary" style={{ fontSize: 13 }}>({records.length} file)</Text>
        </Space>
      }
      extra={<Button icon={<ReloadOutlined />} size="small" onClick={reload} loading={loading}>Làm mới</Button>}
    >
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      <Table dataSource={records} columns={columns} rowKey="id" loading={loading} size="small"
        pagination={{ pageSize: 10, showTotal: (t) => `${t} file` }}
        locale={{ emptyText: 'Chưa có file xuất nào' }} />
    </Card>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function DashboardPage({ embedded = false }) {
  const { session, profile, role } = useAuth()
  const [pwMsg, setPwMsg] = useState(null)
  const [pwLoading, setPwLoading] = useState(false)
  const [form] = Form.useForm()

  // Local state for mssv + full_name (synced from profile after save)
  const [mssv, setMssv] = useState(null)
  const [fullName, setFullName] = useState(null)

  useEffect(() => {
    if (profile?.mssv)      setMssv(profile.mssv)
    if (profile?.full_name) setFullName(profile.full_name)
  }, [profile?.mssv, profile?.full_name])

  const username    = profile?.username ?? session?.user?.email?.split('@')[0] ?? 'User'
  const email       = session?.user?.email ?? '—'
  const joined      = profile?.created_at ? new Date(profile.created_at).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' }) : '—'
  const status      = profile?.status ?? 'active'
  const bg          = avatarColor(username)
  const initial     = username[0]?.toUpperCase() ?? 'U'
  const displayMssv = mssv ?? profile?.mssv ?? null
  const displayFull = fullName ?? profile?.full_name ?? null

  // Reload a specific field from DB after RPC save
  async function reloadField(field, setter) {
    const { data } = await supabase.from('profiles').select(field).eq('id', session?.user?.id).maybeSingle()
    if (data?.[field]) setter(data[field])
  }

  // Call SECURITY DEFINER RPC — bypasses RLS
  async function saveMssv(value) {
    const { error } = await supabase.rpc('update_profile_fields', { p_mssv: value })
    if (error) return error.message.includes('MSSV cannot be changed') ? 'MSSV đã được thiết lập, không thể thay đổi.' : error.message
    await reloadField('mssv', setMssv)
    return null
  }

  async function saveFullName(value) {
    const { error } = await supabase.rpc('update_profile_fields', { p_full_name: value })
    if (error) return error.message.includes('Full name cannot be changed') ? 'Họ tên đã được thiết lập, không thể thay đổi.' : error.message
    await reloadField('full_name', setFullName)
    return null
  }

  async function handleChangePassword({ current, newPw }) {
    setPwMsg(null)
    setPwLoading(true)
    const { error: reAuthErr } = await supabase.auth.signInWithPassword({ email, password: current })
    if (reAuthErr) { setPwLoading(false); setPwMsg({ type: 'error', text: 'Mật khẩu hiện tại không đúng.' }); return }
    const { error } = await supabase.auth.updateUser({ password: newPw })
    setPwLoading(false)
    if (error) setPwMsg({ type: 'error', text: error.message })
    else { setPwMsg({ type: 'success', text: 'Cập nhật mật khẩu thành công.' }); form.resetFields() }
  }

  const content = (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>

      {/* One-time update banners */}
      <OnceUpdateCard
        icon={<ContactsOutlined style={{ color: '#fff', fontSize: 18 }} />}
        accentColor="#059669"
        title="Cập nhật Họ và tên"
        subtitle="Chỉ cập nhật được 1 lần — không thể thay đổi sau khi xác nhận"
        fieldName="full_name"
        inputPlaceholder={
          <Input prefix={<ContactsOutlined style={{ color: '#059669' }} />} placeholder="Nhập họ và tên đầy đủ" />
        }
        inputRules={[
          { required: true, message: 'Vui lòng nhập họ và tên' },
          { min: 3, message: 'Họ tên phải có ít nhất 3 ký tự' }
        ]}
        currentValue={displayFull}
        onSave={saveFullName}
      />

      <OnceUpdateCard
        icon={<IdcardOutlined style={{ color: '#fff', fontSize: 18 }} />}
        accentColor="#d97706"
        title="Cập nhật Mã số sinh viên"
        subtitle="Chỉ cập nhật được 1 lần — không thể thay đổi sau khi xác nhận"
        fieldName="mssv"
        inputPlaceholder={
          <Input prefix={<IdcardOutlined style={{ color: '#d97706' }} />} placeholder="Nhập MSSV (7 chữ số)" maxLength={7} />
        }
        inputRules={[
          { required: true, message: 'Vui lòng nhập MSSV' },
          { pattern: /^\d{7}$/, message: 'MSSV phải gồm đúng 7 chữ số' }
        ]}
        currentValue={displayMssv}
        onSave={saveMssv}
      />

      {/* Profile card */}
      <Card style={{ borderRadius: 24, marginBottom: 24, overflow: 'hidden', border: '1px solid #e8edf5' }} styles={{ body: { padding: 0 } }}>
        <div style={{ height: 140, background: 'linear-gradient(180deg,#002a80 0%,#1565C0 45%,#5b9bd5 75%,#ffffff 100%)', position: 'relative' }}>
          <div style={{ position: 'absolute', top: 16, right: 20 }}>
            <Tag color={ROLE_COLOR[role] ?? 'default'} style={{ fontWeight: 700, borderRadius: 20 }}>{role?.charAt(0).toUpperCase() + role?.slice(1)}</Tag>
          </div>
        </div>
        <div style={{ padding: '0 28px 28px' }}>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20, marginTop: -48, marginBottom: 20 }}>
            <Avatar size={96} style={{ background: bg, fontWeight: 900, fontSize: 40, border: '4px solid #fff', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', flexShrink: 0 }}>{initial}</Avatar>
            <div style={{ marginBottom: 8 }}>
              <Title level={4} style={{ margin: 0 }}>{displayFull ?? username}</Title>
              <Text type="secondary">{email}</Text>
            </div>
            <div style={{ marginBottom: 8, marginLeft: 'auto' }}>
              <Tag color={STATUS_COLOR[status]} style={{ fontWeight: 600, borderRadius: 20, padding: '4px 12px' }}>{status.charAt(0).toUpperCase() + status.slice(1)}</Tag>
            </div>
          </div>

          <Row gutter={[16, 16]}>
            {[
              { icon: <UserOutlined style={{ color: '#1565C0' }} />, label: 'Username', value: username, ibg: '#eff6ff' },
              { icon: <CalendarOutlined style={{ color: '#059669' }} />, label: 'Member Since', value: joined, ibg: '#f0fdf4' },
              { icon: <SafetyOutlined style={{ color: '#7c3aed' }} />, label: 'Status', value: status.charAt(0).toUpperCase() + status.slice(1), ibg: '#f5f3ff' },
            ].map(({ icon, label, value, ibg }) => (
              <Col xs={24} sm={8} key={label}>
                <InfoTile icon={icon} label={label} value={value} ibg={ibg} />
              </Col>
            ))}

            {/* Email */}
            <Col xs={24} sm={12}>
              <InfoTile
                icon={<MailOutlined style={{ color: '#0284c7' }} />}
                label="Email Address" value={email} ibg="#f0f9ff"
              />
            </Col>

            {/* Họ và tên */}
            <Col xs={24} sm={12}>
              <InfoTile
                icon={<ContactsOutlined style={{ color: '#059669' }} />}
                label="Họ và tên"
                value={displayFull}
                ibg="#f0fdf4"
                missing={!displayFull}
                missingText="Chưa cập nhật ⚠"
                missingColor="#059669"
                missingBorder="#6ee7b7"
              />
            </Col>

            {/* MSSV */}
            <Col xs={24} sm={12}>
              <InfoTile
                icon={<IdcardOutlined style={{ color: '#d97706' }} />}
                label="Mã số sinh viên"
                value={displayMssv}
                ibg="#fff7ed"
                missing={!displayMssv}
                missingText="Chưa cập nhật ⚠"
                missingColor="#d97706"
                missingBorder="#fbbf24"
              />
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
                <Text strong style={{ display: 'block', fontSize: 15 }}>Đổi mật khẩu</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>Cập nhật mật khẩu tài khoản</Text>
              </div>
            </div>
            {pwMsg && <Alert message={pwMsg.text} type={pwMsg.type} showIcon style={{ marginBottom: 16 }} />}
            <Form form={form} layout="vertical" onFinish={handleChangePassword}>
              <Form.Item label="Mật khẩu hiện tại" name="current" rules={[{ required: true, message: 'Bắt buộc' }]}>
                <Input.Password placeholder="Nhập mật khẩu hiện tại" />
              </Form.Item>
              <Form.Item label="Mật khẩu mới" name="newPw" rules={[{ required: true, min: 6, message: 'Tối thiểu 6 ký tự' }]}>
                <Input.Password placeholder="Tối thiểu 6 ký tự" />
              </Form.Item>
              <Form.Item label="Xác nhận mật khẩu mới" name="confirmPw"
                rules={[{ required: true, message: 'Bắt buộc' },
                  ({ getFieldValue }) => ({ validator(_, v) { return !v || getFieldValue('newPw') === v ? Promise.resolve() : Promise.reject('Mật khẩu không khớp') } })]}>
                <Input.Password placeholder="Nhập lại mật khẩu mới" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={pwLoading} style={{ background: 'linear-gradient(135deg,#003291,#1E88E5)', border: 'none', fontWeight: 600 }}>
                Cập nhật mật khẩu
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

      <ExportHistoryCard session={session} />
    </div>
  )

  if (embedded) return content
  return <AppLayout>{content}</AppLayout>
}

// ── Reusable info tile ────────────────────────────────────────────────────────
function InfoTile({ icon, label, value, ibg, missing = false, missingText = '—', missingColor, missingBorder }) {
  return (
    <div style={{
      background: '#fafafa',
      border: `1px solid ${missing && missingBorder ? missingBorder : '#f0f0f0'}`,
      borderRadius: 16, padding: '16px', display: 'flex', gap: 12
    }}>
      <div style={{ width: 32, height: 32, background: ibg, borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{icon}</div>
      <div>
        <Text style={{ fontSize: 10, color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1 }}>{label}</Text>
        <div style={{ fontWeight: 700, fontSize: 14, marginTop: 2, color: missing && missingColor ? missingColor : 'inherit' }}>
          {missing
            ? <Tooltip title="Cuộn lên để cập nhật"><span style={{ cursor: 'pointer' }}>{missingText}</span></Tooltip>
            : value}
        </div>
      </div>
    </div>
  )
}
