import { useEffect, useRef, useState, useCallback } from 'react'
import { Card, Button, Space, Typography, Alert, message, Tag } from 'antd'
import { SaveOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  listQuestions,
  listSteps,
  listEvidenceForSession,
  getMyPostSubmission,
  saveDraftPostSubmission,
  finalizePostSubmission,
  getLatestPreQuizForLab,
} from '../services/labApi'
import PostLabQuestion from '../components/lab/PostLabQuestion'
import EvidenceInlineViewer from '../components/lab/EvidenceInlineViewer'

const { Title, Text } = Typography

const AUTOSAVE_MS = 10_000

export default function LabPostLabPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const [questions, setQuestions] = useState([])
  const [steps, setSteps] = useState([])
  const [evidence, setEvidence] = useState([])
  const [answers, setAnswers] = useState({})        // { [qid]: string | {path,...} }
  const [uploadedImages, setUploadedImages] = useState({}) // same shape for image_upload qids
  const [preQuizPassed, setPreQuizPassed] = useState(false)
  const [submission, setSubmission] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lastSavedAt, setLastSavedAt] = useState(null)
  const [savingNow, setSavingNow] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const dirtyRef = useRef(false)

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    async function load() {
      const [qRes, sRes, eRes, subRes, pqRes] = await Promise.all([
        listQuestions(labId, 'post_lab'),
        listSteps(labId),
        listEvidenceForSession(sid),
        getMyPostSubmission(userId, sid),
        getLatestPreQuizForLab(userId, labId),
      ])
      if (cancelled) return
      if (qRes.error) { setError(qRes.error.message); setLoading(false); return }
      setQuestions(qRes.data || [])
      setSteps(sRes.data || [])
      setEvidence(eRes.data || [])
      setSubmission(subRes.data || null)
      setPreQuizPassed(!!pqRes.data?.passed)

      // Split existing answers into free_text vs image_upload buckets
      const sub = subRes.data
      if (sub?.answers) setAnswers(sub.answers)
      if (sub?.uploaded_images) setUploadedImages(sub.uploaded_images)
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [userId, labId, sid])

  // Merge answers + uploaded images into the single `answers` jsonb + separate
  // `uploaded_images` array for persistence.
  // Memoized so it can go in useCallback deps without refiring every render.
  const buildPersistencePayload = useCallback(() => {
    const plainAnswers = { ...answers }
    // For image_upload questions, answers holds the latest object too; we also
    // track them in uploadedImages for the aggregated list used by the PDF.
    const imageList = Object.entries(uploadedImages)
      .filter(([, v]) => v?.path)
      .map(([qid, v]) => ({ question_id: qid, ...v }))
    return { plainAnswers, imageList }
  }, [answers, uploadedImages])

  function handleAnswerChange(question, value) {
    dirtyRef.current = true
    setAnswers((prev) => ({ ...prev, [question.id]: value }))
    if (question.question_type === 'image_upload') {
      setUploadedImages((prev) => {
        const next = { ...prev }
        if (value) next[question.id] = value
        else delete next[question.id]
        return next
      })
    }
  }

  const saveDraft = useCallback(async () => {
    if (!userId || submission?.is_draft === false) return
    setSavingNow(true)
    const { plainAnswers, imageList } = buildPersistencePayload()
    const { data, error: err } = await saveDraftPostSubmission(
      userId, sid, plainAnswers, imageList
    )
    setSavingNow(false)
    if (err) {
      message.error(`Auto-save lỗi: ${err.message}`)
      return
    }
    setSubmission(data)
    setLastSavedAt(new Date())
    dirtyRef.current = false
  }, [userId, sid, submission?.is_draft, buildPersistencePayload])

  // 10s auto-save
  useEffect(() => {
    if (submission?.is_draft === false) return
    const t = setInterval(() => {
      if (dirtyRef.current) saveDraft()
    }, AUTOSAVE_MS)
    return () => clearInterval(t)
  }, [saveDraft, submission?.is_draft])

  // Save on unmount too
  useEffect(() => {
    return () => {
      if (dirtyRef.current) saveDraft()
    }
  }, [saveDraft])

  async function handleSubmit() {
    if (!preQuizPassed) {
      message.warning('Bạn cần hoàn thành pre-lab trước')
      return
    }
    // Require an answer on every question
    const missing = questions.find((q) => {
      const v = answers[q.id]
      if (q.question_type === 'image_upload') return !v?.path
      return !v || (typeof v === 'string' && v.trim() === '')
    })
    if (missing) {
      message.warning(`Câu ${missing.question_order} chưa có câu trả lời`)
      return
    }
    setSubmitting(true)
    const { plainAnswers, imageList } = buildPersistencePayload()
    const { data, error: err } = await finalizePostSubmission(
      userId, sid, plainAnswers, imageList
    )
    setSubmitting(false)
    if (err) { message.error(err.message); return }
    setSubmission(data)
    message.success('Đã nộp post-lab')
    navigate(`/labs/${labId}/session/${sid}/report`)
  }

  if (loading) return <AppLayout><Card loading /></AppLayout>
  if (error) return <AppLayout><Alert type="error" message={error} showIcon /></AppLayout>

  const readOnly = submission?.is_draft === false

  return (
    <AppLayout>
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <Space style={{ marginBottom: 12 }} wrap>
          <Button onClick={() => navigate('/labs')}>← Danh sách lab</Button>
          <Title level={4} style={{ margin: 0 }}>Post-lab · Phân tích</Title>
          {readOnly && <Tag color="success" icon={<CheckCircleOutlined />}>Đã nộp</Tag>}
        </Space>

        {!preQuizPassed && (
          <Alert
            type="warning"
            showIcon
            message="Bạn cần hoàn thành pre-lab trước"
            description="Nút Nộp bài sẽ bị khóa cho đến khi bạn đậu pre-lab."
            style={{ marginBottom: 16 }}
          />
        )}

        {!readOnly && (
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space>
              <SaveOutlined spin={savingNow} />
              <Text type="secondary">
                {savingNow
                  ? 'Đang lưu nháp...'
                  : lastSavedAt
                    ? `Đã lưu nháp lúc ${lastSavedAt.toLocaleTimeString('vi-VN')}`
                    : 'Chưa có thay đổi — tự lưu mỗi 10s'}
              </Text>
              <Button size="small" onClick={saveDraft} loading={savingNow}>Lưu nháp ngay</Button>
            </Space>
          </Card>
        )}

        {questions.map((q) => (
          <PostLabQuestion
            key={q.id}
            question={q}
            value={answers[q.id]}
            onChange={(v) => !readOnly && handleAnswerChange(q, v)}
            userId={userId}
            sessionId={sid}
          >
            <EvidenceInlineViewer evidence={evidence} steps={steps} />
          </PostLabQuestion>
        ))}

        <Space style={{ width: '100%', justifyContent: 'flex-end', marginTop: 16 }}>
          {!readOnly && (
            <Button
              type="primary"
              size="large"
              loading={submitting}
              disabled={!preQuizPassed}
              onClick={handleSubmit}
            >
              Nộp post-lab
            </Button>
          )}
          {readOnly && (
            <Button type="primary" size="large" onClick={() =>
              navigate(`/labs/${labId}/session/${sid}/report`)
            }>
              Sang trang báo cáo
            </Button>
          )}
        </Space>
      </div>
    </AppLayout>
  )
}
