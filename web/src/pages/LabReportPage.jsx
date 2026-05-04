import { useEffect, useRef, useState } from 'react'
import { Card, Button, Alert, Typography, Space, Tag, Spin, message } from 'antd'
import { FilePdfOutlined, ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { getReportSignedUrl } from '../services/labApi'
import { supabase } from '../services/supabase'
import { fetchLabReportData } from '../services/labReportData'
import { generateAndUploadReport } from '../services/labReportGenerator'
import LabReportPdfTemplate from '../components/lab/pdf/LabReportPdfTemplate'
import ErrorBoundary from '../components/ErrorBoundary'

const { Title, Text } = Typography

export default function LabReportPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [data, setData] = useState(null)
  const [existingReport, setExistingReport] = useState(null)
  const [existingSignedUrl, setExistingSignedUrl] = useState(null)

  // Preview-iframe + generation state
  const [generating, setGenerating] = useState(false)
  const [previewUrl, setPreviewUrl] = useState(null) // blob: URL
  const [lastFilename, setLastFilename] = useState(null)
  const [lastHash, setLastHash] = useState(null)

  const templateRef = useRef(null)

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    async function load() {
      const result = await fetchLabReportData(userId, sid)
      if (cancelled) return
      if (result.error) {
        setError(result.error.message)
        setLoading(false)
        return
      }
      setData(result.data)

      // Check if a report already exists for this user/session.
      const { data: existing } = await supabase
        .from('lab_reports')
        .select('id, pdf_storage_path, content_hash, generated_at, file_size_bytes')
        .eq('user_id', userId)
        .eq('session_id', sid)
        .maybeSingle()
      if (cancelled) return
      if (existing) {
        setExistingReport(existing)
        const { data: url } = await getReportSignedUrl(existing.pdf_storage_path, 300)
        if (cancelled) return
        if (url) setExistingSignedUrl(url.signedUrl)
      }
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [userId, sid])

  // Revoke any previous blob URL when a new one is created.
  useEffect(() => {
    return () => { if (previewUrl) URL.revokeObjectURL(previewUrl) }
  }, [previewUrl])

  async function handleGenerate() {
    if (!templateRef.current) return
    setGenerating(true)
    try {
      const res = await generateAndUploadReport({
        element: templateRef.current,
        data,
        userId,
        sessionId: sid,
      })
      setPreviewUrl(res.blobUrl)
      setLastFilename(res.filename)
      setLastHash(res.contentHash)
      setExistingReport(res.reportRow)
      message.success('Đã tạo báo cáo')
    } catch (e) {
      message.error(e.message || String(e))
    } finally {
      setGenerating(false)
    }
  }

  function handleDownloadFresh() {
    if (!previewUrl || !lastFilename) return
    const a = document.createElement('a')
    a.href = previewUrl
    a.download = lastFilename
    document.body.appendChild(a)
    a.click()
    a.remove()
  }

  if (loading) return <AppLayout><Spin /></AppLayout>
  if (error) return <AppLayout><Alert type="error" message={error} showIcon /></AppLayout>

  if (!data?.postSubmission || data.postSubmission.is_draft) {
    return (
      <AppLayout>
        <Alert
          type="warning" showIcon
          message="Bạn chưa nộp post-lab"
          description="Hoàn thành và nộp post-lab để sinh báo cáo PDF."
          style={{ marginBottom: 16 }}
        />
        <Button type="primary" onClick={() => navigate(`/labs/${labId}/session/${sid}/post`)}>
          Sang trang post-lab
        </Button>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 1100, margin: '0 auto' }}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/labs')}>
            Danh sách lab
          </Button>
          <Title level={4} style={{ margin: 0 }}>Báo cáo lab</Title>
          {existingReport
            ? <Tag color="success">Đã phát hành</Tag>
            : <Tag>Chưa phát hành</Tag>}
        </Space>

        <Card>
          <Space wrap style={{ marginBottom: 16 }}>
            <Button
              type="primary" icon={<FilePdfOutlined />}
              loading={generating} onClick={handleGenerate}
            >
              {existingReport ? 'Tạo lại PDF' : 'Tạo PDF'}
            </Button>
            {previewUrl && (
              <Button icon={<DownloadOutlined />} onClick={handleDownloadFresh}>
                Tải xuống ({lastFilename})
              </Button>
            )}
            {existingSignedUrl && !previewUrl && (
              <Button
                icon={<DownloadOutlined />}
                href={existingSignedUrl} target="_blank" rel="noreferrer"
              >
                Tải bản đã phát hành (link 5 phút)
              </Button>
            )}
          </Space>

          {lastHash && (
            <Alert
              type="success" showIcon
              message="Hash báo cáo (SHA-256)"
              description={<Text code>{lastHash}</Text>}
              style={{ marginBottom: 16 }}
            />
          )}

          {/* Live preview in iframe (same-origin blob: URL, no CORS issue). */}
          {previewUrl ? (
            <iframe
              title="PDF preview"
              src={previewUrl}
              style={{ width: '100%', height: '80vh', border: '1px solid #ddd' }}
            />
          ) : (
            <Alert
              type="info" showIcon
              message="Nhấn 'Tạo PDF' để dựng báo cáo"
              description="Dữ liệu đã có đủ. Quá trình render mất khoảng 5–15 giây."
            />
          )}
        </Card>

        {/* Off-screen template — html2pdf reads from this DOM. We position it
            off-screen rather than display:none, because html2canvas cannot
            rasterize a node inside a display:none ancestor.
            Wrapped in ErrorBoundary so any throw inside a section (eg. schema
            mismatch) surfaces as visible error instead of blanking the page. */}
        <ErrorBoundary label="Lỗi render template báo cáo PDF">
          <div
            style={{
              position: 'fixed',
              left: '-10000px',
              top: 0,
              width: '210mm',
            }}
            aria-hidden="true"
          >
            <LabReportPdfTemplate ref={templateRef} data={data} hashPreview={(lastHash || '').slice(0, 16)} />
          </div>
        </ErrorBoundary>
      </div>
    </AppLayout>
  )
}
