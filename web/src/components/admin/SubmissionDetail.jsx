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
import { CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons'
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
  const [preQs, setPreQs] = useState([])
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
      const [
        { data: p },
        { data: postQRes },
        { data: preQRes },
        { data: rs },
        { data: pre },
      ] = await Promise.all([
        getPostSubmission(submission.user_id, submission.session_id),
        listQuestions(submission.session?.lab_id, 'post_lab'),
        listQuestions(submission.session?.lab_id, 'pre_lab'),
        listReports({ sessionId: submission.session_id }),
        listPreQuizSubmissions({
          labId: submission.session?.lab_id,
          userId: submission.user_id,
        }),
      ])
      if (cancelled) return
      setPost(p || null)
      setPostQs(postQRes || [])
      setPreQs(preQRes || [])
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
      width="min(780px, 100vw)"
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

          {/* Pre-quiz answers detail */}
          {preLatest && preQs.length > 0 && (
            <>
              <Divider>Câu trả lời pre-quiz</Divider>
              {preQs.map((q) => {
                const ans = preLatest.answers?.[q.id]
                const correctKey = q.correct_answer
                const isCorrect = ans != null && String(ans) === String(correctKey)
                const opts = q.options || {}
                const optEntries = Array.isArray(opts)
                  ? opts.map((o) => [o.key ?? o, o.text ?? o])
                  : Object.entries(opts)
                return (
                  <div
                    key={q.id}
                    style={{
                      marginBottom: 14,
                      padding: 12,
                      background: isCorrect ? '#f6ffed' : '#fff1f0',
                      border: `1px solid ${isCorrect ? '#b7eb8f' : '#ffa39e'}`,
                      borderRadius: 8,
                    }}
                  >
                    <Space size={8} style={{ marginBottom: 8 }} align="start">
                      {isCorrect ? (
                        <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16, marginTop: 3 }} />
                      ) : (
                        <CloseCircleFilled style={{ color: '#ff4d4f', fontSize: 16, marginTop: 3 }} />
                      )}
                      <div>
                        <Text strong>
                          #{q.question_order} — {q.question_text}
                        </Text>
                        <div style={{ fontSize: 11, color: '#9ca3af' }}>
                          {q.points || 1} điểm · {q.question_type}
                        </div>
                      </div>
                    </Space>
                    {optEntries.length > 0 && (
                      <ul style={{ listStyle: 'none', paddingLeft: 24, margin: 0 }}>
                        {optEntries.map(([key, text]) => {
                          const isStudent = String(ans) === String(key)
                          const isRight = String(correctKey) === String(key)
                          return (
                            <li
                              key={key}
                              style={{
                                padding: '4px 8px',
                                margin: '2px 0',
                                borderRadius: 4,
                                background: isRight
                                  ? '#d9f7be'
                                  : isStudent
                                    ? '#ffccc7'
                                    : 'transparent',
                                fontWeight: isStudent || isRight ? 600 : 400,
                              }}
                            >
                              <Text code style={{ fontSize: 11, marginRight: 6 }}>
                                {key}
                              </Text>
                              {text}
                              {isStudent && (
                                <Tag color={isCorrect ? 'success' : 'error'} style={{ marginLeft: 8, fontSize: 10 }}>
                                  Sinh viên chọn
                                </Tag>
                              )}
                              {isRight && !isStudent && (
                                <Tag color="success" style={{ marginLeft: 8, fontSize: 10 }}>
                                  Đáp án đúng
                                </Tag>
                              )}
                            </li>
                          )
                        })}
                      </ul>
                    )}
                  </div>
                )
              })}
            </>
          )}

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
