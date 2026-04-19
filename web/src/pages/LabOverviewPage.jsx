import { useEffect, useState } from 'react'
import { Card, Button, Space, Typography, Alert, Tag, message } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import MDEditor from '@uiw/react-md-editor'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  getLab,
  listAssignedLabsForUser,
  getLatestPreQuizForLab,
  getActiveSessionForGroup,
  getLatestSessionForGroup,
  getMyPostSubmission,
  getMyReportForSession,
  rpcStartLabSession,
} from '../services/labApi'
import {
  computeLabState,
  LAB_STATES,
  labStateLabel,
  labStateTagColor,
} from '../services/labState'
import PreQuizRunner from '../components/lab/PreQuizRunner'

const { Title, Text, Paragraph } = Typography

export default function LabOverviewPage() {
  const { labId } = useParams()
  const navigate = useNavigate()
  const { session } = useAuth()
  const userId = session?.user?.id

  const [lab, setLab] = useState(null)
  const [membership, setMembership] = useState(null) // { role, group_id }
  const [group, setGroup] = useState(null)
  const [state, setState] = useState(null)
  const [activeSession, setActiveSession] = useState(null)
  const [lastSession, setLastSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showQuiz, setShowQuiz] = useState(false)
  const [starting, setStarting] = useState(false)

  // Re-fetch counter: bumping this triggers the effect below to re-run.
  const [reloadCounter, setReloadCounter] = useState(0)
  function reload() { setReloadCounter((c) => c + 1) }

  useEffect(() => {
    if (!userId || !labId) return
    let cancelled = false
    async function load() {
      const [labRes, assignmentsRes] = await Promise.all([
        getLab(labId),
        listAssignedLabsForUser(userId),
      ])
      if (cancelled) return
      if (labRes.error) {
        setError(labRes.error.message)
        setLoading(false)
        return
      }
      setLab(labRes.data)

      const assignment = (assignmentsRes.data || []).find((a) => a.lab.id === labId)
      if (!assignment) {
        setError('Bạn chưa được gán vào nhóm cho lab này.')
        setLoading(false)
        return
      }
      setMembership({ role: assignment.role, group_id: assignment.group.id })
      setGroup(assignment.group)

      const [pre, active, last] = await Promise.all([
        getLatestPreQuizForLab(userId, labId),
        getActiveSessionForGroup(assignment.group.id),
        getLatestSessionForGroup(assignment.group.id),
      ])
      if (cancelled) return
      setActiveSession(active.data)
      setLastSession(last.data)

      const sessionForPost = active.data || last.data
      const [post, report] = await Promise.all([
        sessionForPost ? getMyPostSubmission(userId, sessionForPost.id) : Promise.resolve({ data: null }),
        sessionForPost ? getMyReportForSession(userId, sessionForPost.id) : Promise.resolve({ data: null }),
      ])
      if (cancelled) return

      setState(computeLabState({
        membership: { role: assignment.role, group_id: assignment.group.id, lab_id: labId },
        latestPreQuiz: pre.data,
        activeSession: active.data,
        lastSession: last.data,
        myPostSubmission: post.data,
        myReport: report.data,
      }))
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [userId, labId, reloadCounter])

  async function handleStartPractice() {
    setStarting(true)
    const { data, error: err } = await rpcStartLabSession(labId)
    setStarting(false)
    if (err) {
      message.error(err.message)
      return
    }
    navigate(`/labs/${labId}/session/${data.session_id}`)
  }

  if (loading) return <AppLayout><Card loading /></AppLayout>

  if (error) {
    return (
      <AppLayout>
        <Alert type="error" message={error} showIcon />
        <Button style={{ marginTop: 12 }} onClick={() => navigate('/labs')}>← Quay lại</Button>
      </AppLayout>
    )
  }

  // Active session exists → redirect shortcut
  if (activeSession && !showQuiz) {
    return (
      <AppLayout>
        <Card>
          <Title level={4}>{lab.code} · {lab.title}</Title>
          <Alert
            type="info"
            showIcon
            message="Nhóm của bạn đang có session thực hành đang chạy."
            style={{ marginBottom: 16 }}
          />
          <Button type="primary" onClick={() =>
            navigate(`/labs/${labId}/session/${activeSession.id}`)
          }>
            Vào dashboard thực hành
          </Button>
        </Card>
      </AppLayout>
    )
  }

  // Pre-lab quiz runner surface
  if (showQuiz) {
    return (
      <AppLayout>
        <div style={{ maxWidth: 760, margin: '0 auto' }}>
          <Button onClick={() => { setShowQuiz(false); reload() }} style={{ marginBottom: 12 }}>
            ← Quay về tổng quan
          </Button>
          <PreQuizRunner
            labId={labId}
            onPassed={() => { setShowQuiz(false); reload() }}
            onFailed={() => { setShowQuiz(false); reload() }}
          />
        </div>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 860, margin: '0 auto' }}>
        <Button onClick={() => navigate('/labs')} style={{ marginBottom: 12 }}>← Danh sách lab</Button>
        <Card>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Space>
              <Title level={3} style={{ margin: 0 }}>{lab.code} · {lab.title}</Title>
              <Tag color={labStateTagColor(state)}>{labStateLabel(state)}</Tag>
              {membership?.role === 'leader' && <Tag color="gold">Leader</Tag>}
            </Space>
            <Text type="secondary">Nhóm: {group?.name} · Học kỳ: {group?.semester || '—'}</Text>
            <Paragraph>
              <Text strong>Ngưỡng đậu pre-quiz:</Text> {lab.pre_quiz_pass_threshold}%
            </Paragraph>

            <div data-color-mode="light">
              <MDEditor.Markdown source={lab.description || '*Chưa có mô tả.*'} />
            </div>

            <Space wrap style={{ marginTop: 12 }}>
              {state === LAB_STATES.PRE_LAB_PENDING && (
                <Button type="primary" onClick={() => setShowQuiz(true)}>Làm pre-lab</Button>
              )}
              {state === LAB_STATES.PRE_LAB_FAILED && (
                <Button type="primary" onClick={() => setShowQuiz(true)}>Làm lại pre-lab</Button>
              )}
              {state === LAB_STATES.PRE_LAB_PASSED && membership?.role === 'leader' && (
                <Button type="primary" loading={starting} onClick={handleStartPractice}>
                  Tạo session & bắt đầu thực hành
                </Button>
              )}
              {state === LAB_STATES.PRE_LAB_PASSED && membership?.role !== 'leader' && (
                <Alert
                  type="info"
                  showIcon
                  message="Chờ leader của nhóm tạo session để bắt đầu."
                />
              )}
              {state === LAB_STATES.PRACTICE_DONE_POST_PENDING && lastSession && (
                <Button type="primary" onClick={() =>
                  navigate(`/labs/${labId}/session/${lastSession.id}/post`)
                }>
                  Làm post-lab
                </Button>
              )}
              {state === LAB_STATES.COMPLETED && lastSession && (
                <Button onClick={() =>
                  navigate(`/labs/${labId}/session/${lastSession.id}/report`)
                }>
                  Xem báo cáo
                </Button>
              )}
            </Space>
          </Space>
        </Card>
      </div>
    </AppLayout>
  )
}
