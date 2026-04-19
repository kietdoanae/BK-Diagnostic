import { useEffect, useState } from 'react'
import { Card, Table, Button, Typography, Space, Alert } from 'antd'
import { DownloadOutlined, HistoryOutlined } from '@ant-design/icons'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { listMyReports, getReportSignedUrl } from '../services/labApi'

const { Title, Text } = Typography

function formatBytes(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

export default function MyReportsPage() {
  const { session: auth } = useAuth()
  const userId = auth?.user?.id
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [downloading, setDownloading] = useState({})

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    async function load() {
      const { data, error: err } = await listMyReports(userId)
      if (cancelled) return
      if (err) setError(err.message)
      else setRows(data || [])
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [userId])

  async function handleDownload(row) {
    setDownloading((d) => ({ ...d, [row.id]: true }))
    const { data, error: err } = await getReportSignedUrl(row.pdf_storage_path, 300)
    setDownloading((d) => ({ ...d, [row.id]: false }))
    if (err || !data?.signedUrl) return
    window.open(data.signedUrl, '_blank')
  }

  const columns = [
    { title: 'Lab', dataIndex: ['session', 'lab', 'code'], render: (v, row) =>
        <Text strong>{v} · {row.session?.lab?.title}</Text> },
    { title: 'Nhóm', dataIndex: ['session', 'group', 'name'] },
    { title: 'Mã session', dataIndex: ['session', 'session_code'], render: (v) =>
        <Text code>{v}</Text> },
    { title: 'Dung lượng', dataIndex: 'file_size_bytes', render: formatBytes },
    { title: 'Phát hành', dataIndex: 'generated_at',
      render: (v) => v ? new Date(v).toLocaleString('vi-VN') : '—' },
    {
      title: '',
      key: 'action',
      align: 'right',
      render: (_, row) => (
        <Button
          icon={<DownloadOutlined />}
          loading={!!downloading[row.id]}
          onClick={() => handleDownload(row)}
        >
          Tải
        </Button>
      ),
    },
  ]

  return (
    <AppLayout>
      <div style={{ maxWidth: 1000, margin: '0 auto' }}>
        <Space style={{ marginBottom: 16 }}>
          <HistoryOutlined style={{ fontSize: 22, color: '#1565C0' }} />
          <Title level={3} style={{ margin: 0 }}>Báo cáo của tôi</Title>
        </Space>
        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 12 }} />}
        <Card>
          <Table
            dataSource={rows}
            columns={columns}
            rowKey="id"
            loading={loading}
            size="small"
            pagination={{ pageSize: 10, showTotal: (t) => `${t} báo cáo` }}
            locale={{ emptyText: 'Bạn chưa phát hành báo cáo nào.' }}
          />
        </Card>
      </div>
    </AppLayout>
  )
}
