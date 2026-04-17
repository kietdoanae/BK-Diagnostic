import { useEffect, useState } from 'react'
import {
  Drawer,
  Descriptions,
  Typography,
  Input,
  Button,
  Space,
  Empty,
  Divider,
  Tag,
  message,
} from 'antd'
import { SaveOutlined, DownloadOutlined } from '@ant-design/icons'
import {
  getPostSubmission,
  setTeacherComment,
  listQuestions,
  listReports,
  getReportSignedUrl,
  listPreQuizSubmissions,
} from '../../services/labApi'

const { Text, Paragraph } = Typography

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SubmissionDetail({ open, submission, onClose, onChanged }) {
  const [post, setPost] = useState(null)
  const [postQs, setPostQs] = useState([])
  const [report, setReport] = useState(null)
  const [preLatest, setPreLatest] = useState(null)
  const [loading, setLoading] = useState(true)
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open || !submission) return
    let cancelled = false
    ;(async () => {
      setLoading(true)
      const [{ data: p }, { data: qs }, { data: rs }, { data: pre }] =
        await Promise.all([
          getPostSubmission(submission.user_id, submission.session_id),
          listQuestions(submission.session?.lab_id, 'post_lab'),
          listReports({ sessionId: submission.session_id }),
          listPreQuizSubmissions({
            labId: submission.session?.lab_id,
            userId: submission.user_id,
          }),
        ])
      if (cancelled) return
      setPost(p || null)
      setPostQs(qs || [])
      setReport((rs || []).find((r) => r.user_id === submission.user_id) || null)
      setPreLatest((pre || [])[0] || null)
      setComment(p?.teacher_comment || '')
      setLoading(false)
    })()
    return () => {
      cancelled = true
    }
  }, [open, submission])

  async function handleSaveComment() {
    if (!post) return
    setSaving(true)
    const { error } = await setTeacherComment(post.id, comment)
    setSaving(false)
    if (error) message.error(error.message)
    else {
      message.success('Đã lưu nhận xét')
      onChanged?.()
    }
  }

  async function handleDownloadPdf() {
    if (!report) return
    const { data, error } = await getReportSignedUrl(report.pdf_storage_path, 60)
    if (error || !data?.signedUrl) {
      message.error(error?.message || 'Không lấy được signed URL')
      return
    }
    const a = document.createElement('a')
    a.href = data.signedUrl
    a.download = report.pdf_storage_path.split('/').pop()
    a.click()
  }

  return (
    <Drawer
      open={open}
      title={
        submission
          ? `Bài nộp — ${submission.profile?.mssv || '—'} · ${
              submission.profile?.full_name || submission.profile?.username
            }`
          : ''
      }
      onClose={onClose}
      width={780}
      destroyOnClose
      loading={loading}
    >
      {!submission ? (
        <Empty />
      ) : (
        <>
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="Lab">
              <Text code>{submission.session?.lab?.code}</Text> —{' '}
              {submission.session?.lab?.title}
            </Descriptions.Item>
            <Descriptions.Item label="Nhóm">
              {submission.session?.group?.name}
            </Descriptions.Item>
            <Descriptions.Item label="Session code">
              <Text code>{submission.session?.session_code}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Pre-quiz (mới nhất)">
              {preLatest ? (
                <Space>
                  <Text>{preLatest.score_percent}%</Text>
                  <Tag color={preLatest.passed ? 'success' : 'error'}>
                    {preLatest.passed ? 'PASSED' : 'FAILED'}
                  </Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Lần {preLatest.attempt_number} · {fmtTime(preLatest.submitted_at)}
                  </Text>
                </Space>
              ) : (
                <Text type="secondary">Chưa làm</Text>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Post-lab">
              {post ? (
                <Space>
                  <Tag color={post.is_draft ? 'warning' : 'success'}>
                    {post.is_draft ? 'DRAFT' : 'SUBMITTED'}
                  </Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {fmtTime(post.submitted_at || post.updated_at)}
                  </Text>
                </Space>
              ) : (
                <Text type="secondary">Chưa nộp</Text>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="PDF">
              {report ? (
                <Button
                  size="small"
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={handleDownloadPdf}
                >
                  Tải PDF
                </Button>
              ) : (
                <Text type="secondary">Chưa generate</Text>
              )}
            </Descriptions.Item>
          </Descriptions>

          <Divider>Câu trả lời post-lab</Divider>
          {!post || !post.answers ? (
            <Text type="secondary">Chưa có dữ liệu</Text>
          ) : (
            postQs.map((q) => {
              const ans = post.answers?.[q.id]
              return (
                <div key={q.id} style={{ marginBottom: 16 }}>
                  <Text strong>
                    #{q.question_order} — {q.question_text}
                  </Text>
                  <Paragraph
                    style={{
                      whiteSpace: 'pre-wrap',
                      background: '#f9fafb',
                      padding: 8,
                      borderRadius: 6,
                      marginTop: 4,
                      marginBottom: 0,
                    }}
                  >
                    {typeof ans === 'string' ? ans : JSON.stringify(ans) || '—'}
                  </Paragraph>
                </div>
              )
            })
          )}

          <Divider>Nhận xét của giáo viên</Divider>
          <Input.TextArea
            rows={4}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Nhận xét nội bộ — KHÔNG xuất hiện trên PDF của sinh viên"
          />
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            disabled={!post}
            style={{ marginTop: 8 }}
            onClick={handleSaveComment}
          >
            Lưu nhận xét
          </Button>
        </>
      )}
    </Drawer>
  )
}
