import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Tag,
  Space,
  Typography,
  Select,
  DatePicker,
  Button,
  message,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { listSessions, listLabs } from '../../services/labApi'
import SessionDetail from '../../components/admin/SessionDetail'

const { Text } = Typography
const { RangePicker } = DatePicker

const STATUS_COLOR = {
  ACTIVE: 'processing',
  COMPLETED: 'success',
  EXPIRED: 'default',
  CANCELLED: 'error',
}

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SessionsAdminTab() {
  const [labs, setLabs] = useState([])
  const [sessions, setSessions] = useState([])
  const [loading, setLoading] = useState(true)
  const [filterLab, setFilterLab] = useState(null)
  const [filterStatus, setFilterStatus] = useState(null)
  const [dateRange, setDateRange] = useState(null)
  const [openSessionId, setOpenSessionId] = useState(null)

  async function reload() {
    setLoading(true)
    const [{ data: ss, error }, { data: ls }] = await Promise.all([
      listSessions({
        labId: filterLab,
        status: filterStatus,
        fromDate: dateRange?.[0]?.toISOString() ?? null,
        toDate: dateRange?.[1]?.toISOString() ?? null,
      }),
      listLabs(),
    ])
    setLoading(false)
    if (error) {
      message.error(error.message)
      return
    }
    setLabs(ls || [])
    setSessions(ss || [])
  }

  useEffect(() => {
    reload()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterLab, filterStatus, dateRange])

  const columns = [
    {
      title: 'Mã code',
      dataIndex: 'session_code',
      width: 110,
      render: (v) => <Text code>{v}</Text>,
    },
    {
      title: 'Lab',
      dataIndex: ['lab', 'code'],
      render: (v, r) => (
        <div>
          <Text code>{v}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.lab?.title}</div>
        </div>
      ),
    },
    {
      title: 'Nhóm',
      dataIndex: ['group', 'name'],
      render: (v, r) => (
        <div>
          <Text>{v}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.group?.semester}</div>
        </div>
      ),
    },
    {
      title: 'Khởi tạo bởi',
      dataIndex: 'started_by_profile',
      render: (p) =>
        p ? (
          <div>
            <Text code>{p.mssv || '—'}</Text>
            <div style={{ fontSize: 11, color: '#9ca3af' }}>
              {p.full_name || p.username}
            </div>
          </div>
        ) : (
          '—'
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 110,
      render: (v) => <Tag color={STATUS_COLOR[v] || 'default'}>{v}</Tag>,
    },
    { title: 'Bắt đầu', dataIndex: 'started_at', render: fmtTime },
    { title: 'Kết thúc', dataIndex: 'ended_at', render: fmtTime },
    {
      title: '',
      key: 'actions',
      align: 'right',
      render: (_, r) => (
        <Button size="small" onClick={() => setOpenSessionId(r.id)}>
          Chi tiết
        </Button>
      ),
    },
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Select
          placeholder="Lọc theo lab"
          value={filterLab || undefined}
          onChange={(v) => setFilterLab(v ?? null)}
          allowClear
          style={{ width: 240 }}
          options={labs.map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Select
          placeholder="Trạng thái"
          value={filterStatus || undefined}
          onChange={(v) => setFilterStatus(v ?? null)}
          allowClear
          style={{ width: 160 }}
          options={['ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED'].map((s) => ({
            value: s,
            label: s,
          }))}
        />
        <RangePicker
          showTime
          value={dateRange}
          onChange={(v) => setDateRange(v)}
          style={{ width: 360 }}
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          Làm mới
        </Button>
        <Text strong style={{ marginLeft: 'auto' }}>
          Tổng: {sessions.length}
        </Text>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={sessions}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 'max-content' }}
      />

      <SessionDetail
        sessionId={openSessionId}
        open={!!openSessionId}
        onClose={() => setOpenSessionId(null)}
        onChanged={reload}
      />
    </Card>
  )
}
