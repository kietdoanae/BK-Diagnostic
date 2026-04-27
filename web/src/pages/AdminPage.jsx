import { useState } from 'react'
import { Tabs, Table, Tag, Button, Input, Select, Space, Typography, Row, Col, Card, Modal, Badge, Alert, Tooltip } from 'antd'
import { SearchOutlined, ReloadOutlined, ExportOutlined, DownloadOutlined, FileTextOutlined } from '@ant-design/icons'
import AppLayout from '../components/AppLayout'
import { useUsers } from '../hooks/useUsers'
import { useLogs } from '../hooks/useLogs'
import { useAllExports } from '../hooks/useExports'
import LabsAdminTab from './admin/LabsAdminTab'
import GroupsAdminTab from './admin/GroupsAdminTab'
import SessionsAdminTab from './admin/SessionsAdminTab'
import SubmissionsAdminTab from './admin/SubmissionsAdminTab'

const { Text, Title } = Typography
const { Option } = Select

const STATUS_COLOR = { active: 'success', inactive: 'default', banned: 'error', suspended: 'warning', pending: 'processing' }
const ROLE_COLOR   = { admin: 'purple', moderator: 'blue', instructor: 'cyan', student: 'green', user: 'default', guest: 'default' }
const ACTION_COLOR = {
  LOGIN: 'green', LOGIN_FAILED: 'red', LOGOUT: 'orange', REGISTER: 'blue',
  DIAGNOSTIC_START: 'cyan', DIAGNOSTIC_STOP: 'orange', ACTIVE_TEST_RUN: 'purple', RAW_EXPORT: 'geekblue',
}

function UsersTab() {
  const {
    users, loading, error, stats,
    search, setSearch, filterRole, setFilterRole, filterStatus, setFilterStatus,
    page, setPage, pageSize, setPageSize,
    changeStatus, changeRole,
  } = useUsers()

  const [editModal, setEditModal] = useState(null)

  const statsCards = [
    { label: 'Total Users', value: stats.total, color: '#1f2937' },
    { label: 'Admins', value: stats.admins, color: '#d97706' },
    { label: 'Active', value: stats.active, color: '#16a34a' },
    { label: 'Banned / Suspended', value: stats.banned, color: '#dc2626' },
  ]

  const columns = [
    { title: 'User', dataIndex: 'username', key: 'username', sorter: true,
      render: (_, r) => (
        <Space>
          <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#1565C0', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, flexShrink: 0 }}>
            {(r.username || r.full_name || '?')[0].toUpperCase()}
          </div>
          <div>
            <Text strong style={{ display: 'block', fontSize: 13 }}>{r.full_name || r.username || '—'}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{r.email}</Text>
          </div>
        </Space>
      )
    },
    { title: 'Username', dataIndex: 'username', key: 'uname', render: v => <Text code>{v}</Text> },
    { title: 'Status', dataIndex: 'status', key: 'status', sorter: true,
      render: (v, r) => (
        <Tag color={STATUS_COLOR[v] || 'default'} style={{ cursor: 'pointer', fontWeight: 600, borderRadius: 20 }}
          onClick={() => setEditModal({ user: r, field: 'status' })}>
          {v?.charAt(0).toUpperCase() + v?.slice(1)}
        </Tag>
      )
    },
    { title: 'Role', dataIndex: 'role', key: 'role', sorter: true,
      render: (v, r) => (
        <Tag color={ROLE_COLOR[v] || 'default'} style={{ cursor: 'pointer', fontWeight: 600, borderRadius: 20 }}
          onClick={() => setEditModal({ user: r, field: 'role' })}>
          {v?.charAt(0).toUpperCase() + v?.slice(1)}
        </Tag>
      )
    },
    { title: 'Joined', dataIndex: 'created_at', key: 'created_at', sorter: true,
      render: v => {
        if (!v) return '—'
        const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
        return d.toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
      }
    },
    { title: 'Last Active', dataIndex: 'last_sign_in_at', key: 'last_sign_in_at', sorter: true,
      render: v => {
        if (!v) return 'Never'
        const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
        return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
      }
    },
    { title: 'Actions', key: 'actions', align: 'right',
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => setEditModal({ user: r, field: 'status' })}>Status</Button>
          <Button size="small" onClick={() => setEditModal({ user: r, field: 'role' })}>Role</Button>
        </Space>
      )
    },
  ]

  function exportCSV() {
    const headers = ['username', 'email', 'role', 'status', 'created_at']
    const rows = users.map(u => headers.map(h => u[h] ?? '').join(','))
    const csv = [headers.join(','), ...rows].join('\n')
    const a = document.createElement('a'); a.href = 'data:text/csv,' + encodeURIComponent(csv)
    a.download = 'users.csv'; a.click()
  }

  async function handleEditSave(value) {
    if (!editModal) return
    const { user, field } = editModal
    if (field === 'status') await changeStatus(user.id, value)
    else await changeRole(user.id, value)
    setEditModal(null)
  }

  return (
    <>
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        {statsCards.map(({ label, value, color }) => (
          <Col span={6} key={label}>
            <Card size="small" style={{ borderRadius: 12 }}>
              <Text style={{ fontSize: 11, color: '#9ca3af', textTransform: 'uppercase', fontWeight: 600, letterSpacing: 0.8 }}>{label}</Text>
              <div style={{ fontSize: 28, fontWeight: 900, color, marginTop: 4 }}>{value}</div>
            </Card>
          </Col>
        ))}
      </Row>

      <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
        <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Input prefix={<SearchOutlined />} placeholder="Search name, email or username…" value={search} onChange={e => setSearch(e.target.value)} style={{ width: 260 }} />
          <Select value={filterRole || undefined} onChange={v => setFilterRole(v ?? '')} placeholder="All Roles" allowClear style={{ width: 140 }}>
            {['admin','moderator','instructor','student','user','guest'].map(r => <Option key={r} value={r}>{r.charAt(0).toUpperCase() + r.slice(1)}</Option>)}
          </Select>
          <Select value={filterStatus || undefined} onChange={v => setFilterStatus(v ?? '')} placeholder="All Statuses" allowClear style={{ width: 150 }}>
            {['active','inactive','pending','suspended','banned'].map(s => <Option key={s} value={s}>{s.charAt(0).toUpperCase() + s.slice(1)}</Option>)}
          </Select>
          <Button icon={<ExportOutlined />} onClick={exportCSV} style={{ marginLeft: 'auto' }}>Export CSV</Button>
        </div>
        {error && <Alert message={error} type="warning" style={{ margin: '8px 16px' }} />}
        <Table
          columns={columns} dataSource={users} rowKey="id" loading={loading}
          size="small"
          pagination={{ current: page, pageSize, total: users.length, onChange: (p, ps) => { setPage(p); setPageSize(ps) }, showSizeChanger: true, pageSizeOptions: ['10','25','50'] }}
        />
      </Card>

      <Modal open={!!editModal} onCancel={() => setEditModal(null)} footer={null} title={`Edit ${editModal?.field} — ${editModal?.user?.username}`}>
        {editModal?.field === 'status' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            {['active','inactive','pending','suspended','banned'].map(s => (
              <Button key={s} block type={editModal.user.status === s ? 'primary' : 'default'} onClick={() => handleEditSave(s)}>
                {s.charAt(0).toUpperCase() + s.slice(1)}
              </Button>
            ))}
          </Space>
        )}
        {editModal?.field === 'role' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            {['user','student','instructor','moderator','admin','guest'].map(r => (
              <Button key={r} block type={editModal.user.role === r ? 'primary' : 'default'} onClick={() => handleEditSave(r)}>
                {r.charAt(0).toUpperCase() + r.slice(1)}
              </Button>
            ))}
          </Space>
        )}
      </Modal>
    </>
  )
}

function LogsTab() {
  const { logs, stats, loading, isLive, filterPlatform, setFilterPlatform, filterAction, setFilterAction, filterUser, setFilterUser, reload } = useLogs()

  const statsCards = stats ? [
    { label: "Today's Events", value: stats.today ?? 0, color: '#1f2937', accent: '#9ca3af' },
    { label: 'Logins Today', value: stats.logins_today ?? 0, color: '#16a34a', accent: '#10b981' },
    { label: 'App Events', value: stats.app_events ?? 0, color: '#1565C0', accent: '#3b82f6' },
    { label: 'Web Events', value: stats.web_events ?? 0, color: '#7c3aed', accent: '#8b5cf6' },
    { label: 'Failed Logins', value: stats.failed_logins ?? 0, color: '#dc2626', accent: '#ef4444' },
  ] : []

  const columns = [
    { title: 'Time', dataIndex: 'created_at', key: 'time', width: 180,
      render: v => {
        if (!v) return '—'
        const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
        return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false,
          year: 'numeric', month: '2-digit', day: '2-digit',
          hour: '2-digit', minute: '2-digit', second: '2-digit' })
      }
    },
    { title: 'User', dataIndex: 'username', key: 'user', render: v => <Text code>{v}</Text> },
    { title: 'Action', dataIndex: 'action', key: 'action',
      render: v => <Tag color={ACTION_COLOR[v] || 'default'} style={{ fontWeight: 600 }}>{v}</Tag>
    },
    { title: 'Platform', dataIndex: 'platform', key: 'platform',
      render: v => <Tag color={v === 'web' ? 'blue' : 'green'}>{v}</Tag>
    },
    { title: 'Details', dataIndex: 'details', key: 'details',
      render: v => v ? <Text type="secondary" style={{ fontSize: 12 }}>{JSON.stringify(v)}</Text> : '—'
    },
  ]

  return (
    <>
      {stats && (
        <Row gutter={[12, 12]} style={{ marginBottom: 20 }}>
          {statsCards.map(({ label, value, color, accent }) => (
            <Col span={5} key={label}>
              <Card size="small" style={{ borderRadius: 12, borderLeft: `4px solid ${accent}` }}>
                <Text style={{ fontSize: 10, color: '#9ca3af', textTransform: 'uppercase', fontWeight: 700, letterSpacing: 0.8 }}>{label}</Text>
                <div style={{ fontSize: 32, fontWeight: 900, color, marginTop: 4, lineHeight: 1 }}>{value}</div>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
        <div style={{ padding: '10px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <Badge status={isLive ? 'success' : 'default'} text={<Text style={{ fontSize: 12, fontWeight: 600, color: isLive ? '#16a34a' : '#9ca3af' }}>{isLive ? 'Live' : 'Connecting…'}</Text>} />
          <div style={{ width: 1, height: 16, background: '#e5e7eb' }} />
          <Select value={filterPlatform || undefined} onChange={v => setFilterPlatform(v ?? '')} placeholder="All Platforms" allowClear style={{ width: 140 }}>
            <Option value="app">App</Option>
            <Option value="web">Web</Option>
          </Select>
          <Select value={filterAction || undefined} onChange={v => setFilterAction(v ?? '')} placeholder="All Actions" allowClear style={{ width: 180 }}>
            {['LOGIN','LOGIN_FAILED','LOGOUT','REGISTER','PASSWORD_RESET','DIAGNOSTIC_START','DIAGNOSTIC_STOP','ACTIVE_TEST_RUN','RAW_EXPORT','USER_STATUS_CHANGED'].map(a => (
              <Option key={a} value={a}>{a}</Option>
            ))}
          </Select>
          <Input placeholder="Filter by username…" value={filterUser} onChange={e => setFilterUser(e.target.value)} style={{ width: 180 }} />
          <Button icon={<ReloadOutlined />} onClick={reload} style={{ marginLeft: 'auto' }}>Refresh</Button>
        </div>
        <Table columns={columns} dataSource={logs} rowKey="id" loading={loading} size="small" pagination={{ pageSize: 50, showSizeChanger: false }} />
      </Card>
    </>
  )
}

function formatBytes(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function ExportsTab() {
  const { records, loading, error, reload, getDownloadUrl, setFilterBrand, setFilterUser } = useAllExports()
  const [downloading, setDownloading] = useState({})
  const [brandInput, setBrandInput] = useState('')
  const [userInput, setUserInput] = useState('')

  async function handleDownload(storagePath, filename) {
    setDownloading(d => ({ ...d, [storagePath]: true }))
    try {
      const url = await getDownloadUrl(storagePath)
      if (url) {
        const a = document.createElement('a')
        a.href = url
        a.download = filename
        a.click()
      } else {
        alert(`Không tìm thấy file trong Storage.\nPath: ${storagePath}`)
      }
    } catch (e) {
      alert(`Lỗi tải xuống: ${e.message}`)
    } finally {
      setDownloading(d => ({ ...d, [storagePath]: false }))
    }
  }

  const columns = [
    {
      title: 'User',
      dataIndex: 'username',
      key: 'username',
      render: v => <Text code style={{ fontSize: 12 }}>{v || '—'}</Text>
    },
    {
      title: 'Hãng / Model',
      dataIndex: 'display_name',
      key: 'display_name',
      render: (v, row) => (
        <div>
          <Text strong style={{ display: 'block', fontSize: 13 }}>{v || '—'}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.brand_id}</Text>
        </div>
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
    {
      title: 'Frames',
      dataIndex: 'frame_count',
      key: 'frame_count',
      render: v => v != null ? v.toLocaleString() : '—'
    },
    {
      title: 'Dung lượng',
      dataIndex: 'file_size_bytes',
      key: 'file_size_bytes',
      render: v => formatBytes(v)
    },
    {
      title: 'Thời gian',
      dataIndex: 'created_at',
      key: 'created_at',
      render: v => {
        if (!v) return '—'
        const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
        return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
      }
    },
    {
      title: 'Tải xuống',
      key: 'action',
      align: 'right',
      render: (_, row) => (
        <Button
          type="primary"
          size="small"
          icon={<DownloadOutlined />}
          loading={downloading[row.storage_path]}
          onClick={() => handleDownload(row.storage_path, row.filename)}
        >
          Tải xuống
        </Button>
      )
    }
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <Input
          prefix={<SearchOutlined />}
          placeholder="Lọc theo username…"
          value={userInput}
          onChange={e => { setUserInput(e.target.value); setFilterUser(e.target.value || null) }}
          style={{ width: 200 }}
          allowClear
        />
        <Select
          value={brandInput || undefined}
          onChange={v => { setBrandInput(v ?? ''); setFilterBrand(v || null) }}
          placeholder="Tất cả hãng xe"
          allowClear
          style={{ width: 160 }}
        >
          <Option value="ford">Ford</Option>
          <Option value="toyota">Toyota</Option>
          <Option value="honda">Honda</Option>
        </Select>
        <Text strong style={{ fontSize: 14 }}>
          Tổng cộng: <Text style={{ color: '#1565C0' }}>{records.length}</Text> file
        </Text>
        <Button icon={<ReloadOutlined />} onClick={reload} loading={loading} style={{ marginLeft: 'auto' }}>
          Làm mới
        </Button>
      </div>
      {error && <Alert message={error} type="error" style={{ margin: '8px 16px' }} />}
      <Table
        columns={columns}
        dataSource={records}
        rowKey="id"
        loading={loading}
        size="small"
        pagination={{ pageSize: 20, showTotal: (t) => `${t} file`, showSizeChanger: false }}
        locale={{ emptyText: 'Chưa có file xuất nào' }}
      />
    </Card>
  )
}

export default function AdminPage() {
  const items = [
    { key: 'labs', label: '🧪 Labs', children: <LabsAdminTab /> },
    { key: 'groups', label: '👥 Groups', children: <GroupsAdminTab /> },
    { key: 'sessions', label: '🎯 Sessions', children: <SessionsAdminTab /> },
    { key: 'submissions', label: '📝 Submissions', children: <SubmissionsAdminTab /> },
    { key: 'users', label: 'Users', children: <UsersTab /> },
    { key: 'logs', label: 'Activity Logs', children: <LogsTab /> },
    { key: 'exports', label: '📁 Exports', children: <ExportsTab /> },
    { key: 'wiring', label: 'Wiring Diagram', children: (
      <Card style={{ borderRadius: 16 }}>
        <iframe
          src="/wiring_diagram.html"
          style={{ width: '100%', height: 600, border: 'none', borderRadius: 8 }}
          title="Wiring Diagram"
        />
      </Card>
    )},
  ]

  return (
    <AppLayout>
      <div style={{ marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>Admin Panel</Title>
      </div>
      <Tabs items={items} size="large" />
    </AppLayout>
  )
}
