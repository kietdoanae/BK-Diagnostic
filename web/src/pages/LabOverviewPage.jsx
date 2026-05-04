import { useEffect, useState } from 'react'
import { Card, Button, Space, Typography, Alert, Tag, message } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
        setError(t('labsList.empty'))
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
  }, [userId, labId, reloadCounter, t])

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
        <Button style={{ marginTop: 12 }} onClick={() => navigate('/labs')}>← {t('common.back')}</Button>
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
            message={t('labOverview.leaderHint')}
            style={{ marginBottom: 16 }}
          />
          <Button type="primary" onClick={() =>
            navigate(`/labs/${labId}/session/${activeSession.id}`)
          }>
            {t('lab.btn.joinDashboard')}
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
            ← {t('common.back')}
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
        <Button onClick={() => navigate('/labs')} style={{ marginBottom: 12 }}>← {t('labReport.labListBack')}</Button>
        <Card>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Space>
              <Title level={3} style={{ margin: 0 }}>{lab.code} · {lab.title}</Title>
              <Tag color={labStateTagColor(state)}>{t(labStateLabel(state))}</Tag>
              {membership?.role === 'leader' && <Tag color="gold">{t('labSession.leaderLabel')}</Tag>}
            </Space>
            <Text type="secondary">{t('labsList.groupLabel')}: {group?.name} · {t('labsList.semesterLabel')}: {group?.semester || '—'}</Text>
            <Paragraph>
              <Text strong>{t('labOverview.passScore', { score: lab.pre_quiz_pass_threshold })}</Text>
            </Paragraph>

            <div data-color-mode="light">
              <MDEditor.Markdown source={lab.description || `*${t('common.noData')}*`} />
            </div>

            <Space wrap style={{ marginTop: 12 }}>
              {state === LAB_STATES.PRE_LAB_PENDING && (
                <Button type="primary" onClick={() => setShowQuiz(true)}>{t('labOverview.btnStart')}</Button>
              )}
              {state === LAB_STATES.PRE_LAB_FAILED && (
                <Button type="primary" onClick={() => setShowQuiz(true)}>{t('labOverview.btnRetry')}</Button>
              )}
              {state === LAB_STATES.PRE_LAB_PASSED && membership?.role === 'leader' && (
                <Button type="primary" loading={starting} onClick={handleStartPractice}>
                  {t('lab.btn.startPractice')}
                </Button>
              )}
              {state === LAB_STATES.PRE_LAB_PASSED && membership?.role !== 'leader' && (
                <Alert
                  type="info"
                  showIcon
                  message={t('labOverview.memberHint')}
                />
              )}
              {state === LAB_STATES.PRACTICE_DONE_POST_PENDING && lastSession && (
                <Button type="primary" onClick={() =>
                  navigate(`/labs/${labId}/session/${lastSession.id}/post`)
                }>
                  {t('lab.btn.doPostLab')}
                </Button>
              )}
              {state === LAB_STATES.COMPLETED && lastSession && (
                <Button onClick={() =>
                  navigate(`/labs/${labId}/session/${lastSession.id}/report`)
                }>
                  {t('lab.btn.viewReport')}
                </Button>
              )}
            </Space>
          </Space>
        </Card>
      </div>
    </AppLayout>
  )
}
