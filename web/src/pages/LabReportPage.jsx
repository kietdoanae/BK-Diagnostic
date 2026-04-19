import { useEffect, useState } from 'react'
import { Card, Button, Alert, Typography, Space, Tag, Spin } from 'antd'
import { FilePdfOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  getMyPostSubmission,
  getMyReportForSession,
  getReportSignedUrl,
} from '../services/labApi'

const { Title, Text } = Typography

export default function LabReportPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const [loading, setLoading] = useState(true)
  const [submission, setSubmission] = useState(null)
  const [report, setReport] = useState(null)
  const [downloadUrl, setDownloadUrl] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    async function load() {
      const [sub, rep] = await Promise.all([
        getMyPostSubmission(userId, sid),
        getMyReportForSession(userId, sid),
      ])
      if (cancelled) return
      if (sub.error) {
        setError(sub.error.message)
        setLoading(false)
        return
      }
      if (rep.error) {
        setError(rep.error.message)
        setLoading(false)
        return
      }
      setSubmission(sub.data)
      setReport(rep.data)
      if (rep.data?.pdf_storage_path) {
        const { data, error: err } = await getReportSignedUrl(rep.data.pdf_storage_path, 300)
        if (cancelled) return
        if (!err) setDownloadUrl(data.signedUrl)
      }
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [userId, sid])

  if (loading) return <AppLayout><Spin /></AppLayout>

  if (error) return <AppLayout><Alert type="error" message={error} showIcon /></AppLayout>

  if (!submission || submission.is_draft) {
    return (
      <AppLayout>
        <Alert
          type="warning"
          showIcon
          message="Bạn chưa nộp post-lab"
          description="Hoàn thành và nộp post-lab để sinh báo cáo PDF."
          style={{ marginBottom: 16 }}
        />
        <Button
          type="primary"
          onClick={() => navigate(`/labs/${labId}/session/${sid}/post`)}
        >
          Sang trang post-lab
        </Button>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 860, margin: '0 auto' }}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/labs')}>
            Danh sách lab
          </Button>
          <Title level={4} style={{ margin: 0 }}>Báo cáo lab</Title>
          {report ? <Tag color="success">Đã phát hành</Tag> : <Tag>Chưa phát hành</Tag>}
        </Space>

        <Card>
          {!report && (
            <Alert
              type="info"
              showIcon
              message="Template PDF sẽ được thêm ở Phase 5"
              description={
                <>
                  <div>Post-lab đã được ghi nhận. Tại Phase 5, nút "Tạo PDF" sẽ
                  xuất hiện ở đây để render template, tính hash SHA-256, upload
                  lên bucket <Text code>lab-reports</Text> và tải xuống về máy.</div>
                </>
              }
              style={{ marginBottom: 16 }}
            />
          )}

          {report && (
            <>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  <FilePdfOutlined style={{ fontSize: 28, color: '#d97706' }} />
                  <div>
                    <Text strong>Báo cáo đã phát hành</Text>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        Hash: <Text code>{report.content_hash?.slice(0, 16)}...</Text>
                      </Text>
                    </div>
                  </div>
                </Space>
                {downloadUrl && (
                  <Button
                    type="primary"
                    icon={<FilePdfOutlined />}
                    href={downloadUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Tải PDF (link có hạn 5 phút)
                  </Button>
                )}
              </Space>
            </>
          )}
        </Card>
      </div>
    </AppLayout>
  )
}
