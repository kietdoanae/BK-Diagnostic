import { useState, useEffect } from 'react'
import { Card, Avatar, Typography, Tag, Row, Col, Form, Input, Button, Alert, Table, Space, Tooltip } from 'antd'
import { UserOutlined, CalendarOutlined, SafetyOutlined, MailOutlined, LockOutlined, DownloadOutlined, FileTextOutlined, ReloadOutlined, IdcardOutlined, CheckCircleOutlined, ContactsOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
      setMsg({ type: 'success', text: t('dashboardPage.updateSuccess') })
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
            {t('common.confirm')}
          </Button>
        </Form.Item>
      </Form>
    </Card>
  )
}

// ── Export history ────────────────────────────────────────────────────────────
function ExportHistoryCard({ session }) {
  const { t } = useTranslation()
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
      title: t('dashboardPage.export.brandModel'),
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
      title: t('dashboardPage.export.filename'),
      dataIndex: 'filename',
      key: 'filename',
      render: v => (
        <Space>
          <FileTextOutlined style={{ color: '#1565C0' }} />
          <Text style={{ fontFamily: 'monospace', fontSize: 13 }}>{v}</Text>
        </Space>
      )
    },
    { title: t('dashboardPage.export.frames'), dataIndex: 'frame_count', key: 'frame_count', render: v => v != null ? v.toLocaleString() : '—' },
    { title: t('dashboardPage.export.size'), dataIndex: 'file_size_bytes', key: 'file_size_bytes', render: v => formatBytes(v) },
    {
      title: t('dashboardPage.export.time'),
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
          {t('common.download')}
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
          <span style={{ fontWeight: 700 }}>{t('dashboardPage.export.title')}</span>
          <Text type="secondary" style={{ fontSize: 13 }}>{t('dashboardPage.export.fileCount', { count: records.length })}</Text>
        </Space>
      }
      extra={<Button icon={<ReloadOutlined />} size="small" onClick={reload} loading={loading}>{t('common.refresh')}</Button>}
    >
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      <Table dataSource={records} columns={columns} rowKey="id" loading={loading} size="small"
        pagination={{ pageSize: 10, showTotal: (n) => t('dashboardPage.export.totalFmt', { count: n }) }}
        locale={{ emptyText: t('dashboardPage.export.empty') }} />
    </Card>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function DashboardPage({ embedded = false }) {
  const { t } = useTranslation()
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
    if (error) return error.message.includes('MSSV cannot be changed') ? t('dashboardPage.errMssvLocked') : error.message
    await reloadField('mssv', setMssv)
    return null
  }

  async function saveFullName(value) {
    const { error } = await supabase.rpc('update_profile_fields', { p_full_name: value })
    if (error) return error.message.includes('Full name cannot be changed') ? t('dashboardPage.errFullNameLocked') : error.message
    await reloadField('full_name', setFullName)
    return null
  }

  async function handleChangePassword({ current, newPw }) {
    setPwMsg(null)
    setPwLoading(true)
    const { error: reAuthErr } = await supabase.auth.signInWithPassword({ email, password: current })
    if (reAuthErr) { setPwLoading(false); setPwMsg({ type: 'error', text: t('dashboard.errCurrentPassword') }); return }
    const { error } = await supabase.auth.updateUser({ password: newPw })
    setPwLoading(false)
    if (error) setPwMsg({ type: 'error', text: error.message })
    else { setPwMsg({ type: 'success', text: t('dashboard.passwordChanged') }); form.resetFields() }
  }

  const content = (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>

      {/* One-time update banners */}
      <OnceUpdateCard
        icon={<ContactsOutlined style={{ color: '#fff', fontSize: 18 }} />}
        accentColor="#059669"
        title={t('dashboard.fullNameUpdate')}
        subtitle={t('dashboard.fullNameSubtitle')}
        fieldName="full_name"
        inputPlaceholder={
          <Input prefix={<ContactsOutlined style={{ color: '#059669' }} />} placeholder={t('dashboard.fullNamePlaceholder')} />
        }
        inputRules={[
          { required: true, message: t('dashboardPage.errFullNameRequired') },
          { min: 3, message: t('dashboardPage.errFullNameMin') }
        ]}
        currentValue={displayFull}
        onSave={saveFullName}
      />

      <OnceUpdateCard
        icon={<IdcardOutlined style={{ color: '#fff', fontSize: 18 }} />}
        accentColor="#d97706"
        title={t('dashboard.updateMssv')}
        subtitle={t('dashboard.updateMssvSubtitle')}
        fieldName="mssv"
        inputPlaceholder={
          <Input prefix={<IdcardOutlined style={{ color: '#d97706' }} />} placeholder={t('dashboard.mssvPlaceholder')} maxLength={7} />
        }
        inputRules={[
          { required: true, message: t('dashboard.mssvErrRequired') },
          { pattern: /^\d{7}$/, message: t('dashboard.mssvErrPattern') }
        ]}
        currentValue={displayMssv}
        onSave={saveMssv}
      />

      {/* Profile card */}
      <Card style={{ borderRadius: 24, marginBottom: 24, overflow: 'hidden', border: '1px solid #e8edf5' }} styles={{ body: { padding: 0 } }}>
        {/* ── HEADER: gradient xanh đậm, text trắng — không còn fade về trắng để tránh che chữ ── */}
        <div
          style={{
            background: 'linear-gradient(135deg,#001f6b 0%,#003291 40%,#1565C0 80%,#1E88E5 100%)',
            padding: '32px 28px 28px',
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Decorative circles */}
          <div style={{ position: 'absolute', top: -60, right: -40, width: 200, height: 200, borderRadius: '50%', background: 'rgba(255,255,255,0.06)' }} />
          <div style={{ position: 'absolute', bottom: -30, left: -30, width: 120, height: 120, borderRadius: '50%', background: 'rgba(255,255,255,0.04)' }} />

          {/* Role tag góc phải trên */}
          <div style={{ position: 'absolute', top: 16, right: 20, zIndex: 2 }}>
            <Tag color={ROLE_COLOR[role] ?? 'default'} style={{ fontWeight: 700, borderRadius: 20, fontSize: 12, padding: '2px 12px' }}>
              {role?.charAt(0).toUpperCase() + role?.slice(1)}
            </Tag>
          </div>

          {/* Avatar + tên + email — tất cả text TRẮNG trên nền xanh, không bị che */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 20, position: 'relative', zIndex: 1 }}>
            <Avatar
              size={88}
              style={{
                background: bg,
                fontWeight: 900,
                fontSize: 38,
                border: '4px solid rgba(255,255,255,0.95)',
                boxShadow: '0 8px 24px rgba(0,0,0,0.25)',
                flexShrink: 0,
              }}
            >
              {initial}
            </Avatar>
            <div style={{ minWidth: 0, flex: 1 }}>
              <Title level={3} style={{ margin: 0, color: '#fff', fontWeight: 800 }}>
                {displayFull ?? username}
              </Title>
              <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 14 }}>{email}</Text>
              <div style={{ marginTop: 8 }}>
                <Tag
                  color={STATUS_COLOR[status]}
                  style={{ fontWeight: 600, borderRadius: 20, padding: '2px 12px', fontSize: 12 }}
                >
                  {status.charAt(0).toUpperCase() + status.slice(1)}
                </Tag>
              </div>
            </div>
          </div>
        </div>

        {/* ── BODY: thông tin chia 2 hàng × 3 cột logic ── */}
        <div style={{ padding: '24px 28px 28px' }}>
          {/* Section 1: Định danh cá nhân */}
          <div style={{ marginBottom: 20 }}>
            <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.2, color: '#9ca3af', display: 'block', marginBottom: 10 }}>
              {t('dashboard.section.identity')}
            </Text>
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={8}>
                <InfoTile
                  icon={<ContactsOutlined style={{ color: '#059669' }} />}
                  label={t('dashboard.field.fullName')}
                  value={displayFull}
                  ibg="#f0fdf4"
                  missing={!displayFull}
                  missingText={`${t('dashboard.missing')} ⚠`}
                  missingColor="#059669"
                  missingBorder="#6ee7b7"
                />
              </Col>
              <Col xs={24} sm={8}>
                <InfoTile
                  icon={<IdcardOutlined style={{ color: '#d97706' }} />}
                  label={t('dashboard.field.mssv')}
                  value={displayMssv}
                  ibg="#fff7ed"
                  missing={!displayMssv}
                  missingText={`${t('dashboard.missing')} ⚠`}
                  missingColor="#d97706"
                  missingBorder="#fbbf24"
                />
              </Col>
              <Col xs={24} sm={8}>
                <InfoTile
                  icon={<UserOutlined style={{ color: '#1565C0' }} />}
                  label={t('dashboard.field.username')}
                  value={username}
                  ibg="#eff6ff"
                />
              </Col>
            </Row>
          </div>

          {/* Section 2: Tài khoản */}
          <div>
            <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.2, color: '#9ca3af', display: 'block', marginBottom: 10 }}>
              {t('dashboard.section.account')}
            </Text>
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12}>
                <InfoTile
                  icon={<MailOutlined style={{ color: '#0284c7' }} />}
                  label={t('dashboard.field.email')}
                  value={email}
                  ibg="#f0f9ff"
                />
              </Col>
              <Col xs={24} sm={6}>
                <InfoTile
                  icon={<CalendarOutlined style={{ color: '#059669' }} />}
                  label={t('dashboard.field.joined')}
                  value={joined}
                  ibg="#f0fdf4"
                />
              </Col>
              <Col xs={24} sm={6}>
                <InfoTile
                  icon={<SafetyOutlined style={{ color: '#7c3aed' }} />}
                  label={t('dashboard.field.status')}
                  value={status.charAt(0).toUpperCase() + status.slice(1)}
                  ibg="#f5f3ff"
                />
              </Col>
            </Row>
          </div>
        </div>
      </Card>

      {/* Đổi mật khẩu — full width, form chia 3 cột để tận dụng không gian */}
      <Card style={{ borderRadius: 24, border: '1px solid #e8edf5' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
          <div style={{ width: 40, height: 40, borderRadius: 14, background: 'linear-gradient(135deg,#003291,#1E88E5)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <LockOutlined style={{ color: '#fff', fontSize: 18 }} />
          </div>
          <div>
            <Text strong style={{ display: 'block', fontSize: 15 }}>{t('dashboard.changePassword')}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.changePasswordDesc')}</Text>
          </div>
        </div>
        {pwMsg && <Alert message={pwMsg.text} type={pwMsg.type} showIcon style={{ marginBottom: 16 }} />}
        <Form form={form} layout="vertical" onFinish={handleChangePassword}>
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item label={t('auth.currentPassword')} name="current" rules={[{ required: true, message: t('common.required') }]}>
                <Input.Password placeholder={t('dashboardPage.currentPwPlaceholder')} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label={t('auth.newPassword')} name="newPw" rules={[{ required: true, min: 6, message: t('auth.errShort') }]}>
                <Input.Password placeholder={t('dashboardPage.newPwPlaceholder')} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label={t('auth.confirmNewPassword')} name="confirmPw"
                rules={[{ required: true, message: t('common.required') },
                  ({ getFieldValue }) => ({ validator(_, v) { return !v || getFieldValue('newPw') === v ? Promise.resolve() : Promise.reject(t('auth.errMismatch')) } })]}>
                <Input.Password placeholder={t('dashboardPage.confirmPwPlaceholder')} />
              </Form.Item>
            </Col>
          </Row>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={pwLoading}
              style={{
                background: 'linear-gradient(135deg,#003291,#1E88E5)',
                border: 'none',
                fontWeight: 600,
                height: 44,
                minWidth: 200,
                borderRadius: 10,
              }}
            >
              {t('dashboard.btnUpdate')}
            </Button>
          </div>
        </Form>
      </Card>

      <ExportHistoryCard session={session} />
    </div>
  )

  if (embedded) return content
  return <AppLayout>{content}</AppLayout>
}

// ── Reusable info tile ────────────────────────────────────────────────────────
function InfoTile({ icon, label, value, ibg, missing = false, missingText = '—', missingColor, missingBorder }) {
  const { t } = useTranslation()
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
            ? <Tooltip title={t('dashboardPage.scrollUpToUpdate')}><span style={{ cursor: 'pointer' }}>{missingText}</span></Tooltip>
            : value}
        </div>
      </div>
    </div>
  )
}
