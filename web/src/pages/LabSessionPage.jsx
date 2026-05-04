import { useEffect, useState, useMemo } from 'react'
import { Row, Col, Card, Button, Space, Typography, Alert, message, Modal } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import { useLabSession } from '../hooks/useLabSession'
import { useLiveEvidence } from '../hooks/useLiveEvidence'
import {
  listSteps,
  listGroupMembers,
} from '../services/labApi'
import StepList from '../components/lab/StepList'
import StepDetail from '../components/lab/StepDetail'
import SessionCodeDisplay from '../components/lab/SessionCodeDisplay'

const { Title, Text } = Typography

export default function LabSessionPage() {
  const { labId, sid } = useParams()
  const navigate = useNavigate()
  const { session: auth } = useAuth()
  const userId = auth?.user?.id

  const { session, loading, error, isExpired, readOnly, startStep, endStep, completeSession } = useLabSession(sid)
  const { evidence, countsByStep } = useLiveEvidence(sid)

  const [steps, setSteps] = useState([])
  const [stepsLoading, setStepsLoading] = useState(true)
  const [isLeader, setIsLeader] = useState(false)
  const [completing, setCompleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setStepsLoading(true)
      const { data, error: err } = await listSteps(labId)
      if (!cancelled && !err) setSteps(data || [])
      setStepsLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [labId])

  useEffect(() => {
    let cancelled = false
    async function loadRole() {
      if (!session?.group_id || !userId) return
      const { data, error: err } = await listGroupMembers(session.group_id)
      if (cancelled || err) return
      const mine = (data || []).find((m) => m.user_id === userId)
      setIsLeader(mine?.role === 'leader')
    }
    loadRole()
    return () => { cancelled = true }
  }, [session?.group_id, userId])

  const currentStep = useMemo(
    () => steps.find((s) => s.id === session?.current_step_id) || null,
    [steps, session?.current_step_id]
  )

  const allStepsSatisfied = useMemo(() => {
    if (steps.length === 0) return false
    // session.completed_step_ids — uuid[] track steps đã end_current_step() ít nhất 1 lần.
    // Cần migration 2026-05-03-track-completed-steps để có column này.
    const completed = new Set(session?.completed_step_ids ?? [])
    return steps.every((s) => {
      if (s.evidence_type === 'none') {
        // none-step thực sự "satisfied" khi leader đã activate + end nó (trong completed_step_ids).
        return completed.has(s.id)
      }
      return (countsByStep[s.id] || 0) >= s.required_count
    })
  }, [steps, countsByStep, session?.completed_step_ids])

  async function handleStart(step) {
    const { ok, error: err } = await startStep(step.id)
    if (!ok) message.error(err)
  }

  async function handleEnd() {
    const { ok, error: err } = await endStep()
    if (!ok) message.error(err)
  }

  async function handleComplete() {
    Modal.confirm({
      title: 'Kết thúc thực hành?',
      content: 'Bạn sẽ chuyển sang phần post-lab. Không thể thêm evidence sau khi kết thúc.',
      okText: 'Kết thúc',
      cancelText: 'Hủy',
      onOk: async () => {
        setCompleting(true)
        const { ok, error: err } = await completeSession()
        setCompleting(false)
        if (!ok) { message.error(err); return }
        navigate(`/labs/${labId}/session/${sid}/post`)
      },
    })
  }

  if (loading || stepsLoading) return <AppLayout><Card loading /></AppLayout>
  if (error) return <AppLayout><Alert type="error" message={error} showIcon /></AppLayout>
  if (!session) return <AppLayout><Alert type="warning" message="Session không tồn tại." /></AppLayout>

  // Session đã COMPLETED hoặc CANCELLED — chuyển sang post-lab.
  // Lưu ý: EXPIRED status xử lý khác, vẫn hiện dashboard read-only ở dưới.
  if (session.status !== 'ACTIVE' && session.status !== 'EXPIRED') {
    return (
      <AppLayout>
        <Alert
          type={session.status === 'COMPLETED' ? 'success' : 'warning'}
          message={`Session ${session.status}`}
          description="Chuyển sang trang post-lab để tiếp tục."
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Space>
          <Button type="primary" onClick={() => navigate(`/labs/${labId}/session/${sid}/post`)}>
            Làm post-lab
          </Button>
          <Button onClick={() => navigate('/labs')}>← Danh sách lab</Button>
        </Space>
      </AppLayout>
    )
  }

  // readOnly = isExpired (ACTIVE quá hạn theo client clock) HOẶC status='EXPIRED' đã set bởi cron.
  const sessionExpired = isExpired || session.status === 'EXPIRED'

  return (
    <AppLayout>
      <Space style={{ marginBottom: 12 }}>
        <Button onClick={() => navigate('/labs')}>← Danh sách lab</Button>
        <Title level={4} style={{ margin: 0 }}>Dashboard thực hành</Title>
        {isLeader ? <Text type="success">(Bạn là leader)</Text> : <Text type="secondary">(Thành viên)</Text>}
      </Space>

      {sessionExpired && (
        <Alert
          type="warning"
          showIcon
          message="Phiên thực hành đã hết hạn"
          description="Bạn đang ở chế độ chỉ xem. Có thể xem lại các bước và bằng chứng đã upload, nhưng không thể thực hiện thao tác nào nữa."
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={() => navigate(`/labs/${labId}/session/${sid}/post`)}>
              Làm post-lab
            </Button>
          }
        />
      )}

      <Row gutter={16}>
        <Col xs={24} md={10}>
          <Card>
            <StepList
              steps={steps}
              currentStepId={session.current_step_id}
              countsByStep={countsByStep}
              isLeader={isLeader}
              onSelectStep={handleStart}
              readOnly={readOnly}
            />
            {allStepsSatisfied && !readOnly && (
              <Button
                block
                size="large"
                type="primary"
                loading={completing}
                style={{ marginTop: 16 }}
                onClick={handleComplete}
              >
                Kết thúc thực hành & làm Post-lab
              </Button>
            )}
          </Card>
        </Col>
        <Col xs={24} md={14}>
          <StepDetail
            step={currentStep}
            session={session}
            isLeader={isLeader}
            userId={userId}
            countForStep={currentStep ? (countsByStep[currentStep.id] || 0) : 0}
            onStart={handleStart}
            onEnd={handleEnd}
            readOnly={readOnly}
          />
          <Card style={{ marginTop: 16 }} size="small">
            <Text type="secondary">Tổng evidence đã thu: </Text>
            <Text strong>{evidence.length}</Text>
          </Card>
        </Col>
      </Row>

      <SessionCodeDisplay
        code={session.session_code}
        expiresAt={session.expires_at}
        status={session.status}
      />
    </AppLayout>
  )
}
