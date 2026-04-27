import { useEffect, useState } from 'react'
import { Card, List, Tag, Button, Space, Typography, Alert, Empty } from 'antd'
import { ExperimentOutlined, ArrowRightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../hooks/useAuth'
import {
  listAssignedLabsForUser,
  getLatestPreQuizForLab,
  getActiveSessionForGroup,
  getLatestSessionForGroup,
  getMyPostSubmission,
  getMyReportForSession,
} from '../services/labApi'
import {
  computeLabState,
  labStateLabel,
  labStateTagColor,
  LAB_STATES,
} from '../services/labState'

const { Title, Text } = Typography

export default function LabsListPage() {
  const { session } = useAuth()
  const userId = session?.user?.id
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [rows, setRows] = useState([]) // [{ lab, group, role, state, activeSession, lastSession }]

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    async function load() {
      const { data: assignments, error: aErr } = await listAssignedLabsForUser(userId)
      if (cancelled) return
      if (aErr) {
        setError(aErr.message)
        setLoading(false)
        return
      }
      const enriched = await Promise.all(
        assignments.map(async ({ lab, group, role }) => {
          const [preQuiz, active, last] = await Promise.all([
            getLatestPreQuizForLab(userId, lab.id),
            getActiveSessionForGroup(group.id),
            getLatestSessionForGroup(group.id),
          ])
          const sessionForPost = active.data || last.data
          const [post, report] = await Promise.all([
            sessionForPost ? getMyPostSubmission(userId, sessionForPost.id) : Promise.resolve({ data: null }),
            sessionForPost ? getMyReportForSession(userId, sessionForPost.id) : Promise.resolve({ data: null }),
          ])
          const state = computeLabState({
            membership: { role, group_id: group.id, lab_id: lab.id },
            latestPreQuiz: preQuiz.data,
            activeSession: active.data,
            lastSession: last.data,
            myPostSubmission: post.data,
            myReport: report.data,
          })
          return {
            lab,
            group,
            role,
            state,
            activeSession: active.data,
            lastSession: last.data,
          }
        })
      )
      if (cancelled) return
      setRows(enriched)
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [userId])

  function cta(row) {
    const labId = row.lab.id
    switch (row.state) {
      case LAB_STATES.PRE_LAB_PENDING:
      case LAB_STATES.PRE_LAB_FAILED:
        return (
          <Button type="primary" onClick={() => navigate(`/labs/${labId}`)}>
            Làm pre-lab
          </Button>
        )
      case LAB_STATES.PRE_LAB_PASSED:
        return (
          <Button type="primary" onClick={() => navigate(`/labs/${labId}`)}>
            {row.role === 'leader' ? 'Bắt đầu thực hành' : 'Chờ leader'}
          </Button>
        )
      case LAB_STATES.PRACTICE_ACTIVE:
        return (
          <Button type="primary" onClick={() =>
            navigate(`/labs/${labId}/session/${row.activeSession.id}`)}>
            Vào dashboard thực hành
          </Button>
        )
      case LAB_STATES.PRACTICE_EXPIRED:
        // Session ACTIVE quá hạn — vẫn cho click vào để xem read-only.
        return (
          <Space>
            <Button onClick={() =>
              navigate(`/labs/${labId}/session/${row.activeSession.id}`)}>
              Xem lại phiên
            </Button>
            <Button type="primary" onClick={() =>
              navigate(`/labs/${labId}/session/${row.activeSession.id}/post`)}>
              Làm post-lab
            </Button>
          </Space>
        )
      case LAB_STATES.PRACTICE_DONE_POST_PENDING:
        return (
          <Button type="primary" onClick={() =>
            navigate(`/labs/${labId}/session/${row.lastSession.id}/post`)}>
            Làm post-lab
          </Button>
        )
      case LAB_STATES.COMPLETED:
        return (
          <Button onClick={() =>
            navigate(`/labs/${labId}/session/${row.lastSession.id}/report`)}>
            Xem báo cáo
          </Button>
        )
      default:
        return null
    }
  }

  return (
    <AppLayout>
      <div style={{ maxWidth: 960, margin: '0 auto' }}>
        <Space style={{ marginBottom: 16 }} align="center">
          <ExperimentOutlined style={{ fontSize: 22, color: '#1565C0' }} />
          <Title level={3} style={{ margin: 0 }}>Thực hành (Labs)</Title>
        </Space>

        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}

        <Card loading={loading}>
          {(!loading && rows.length === 0) && (
            <Empty description="Bạn chưa được gán vào nhóm lab nào. Liên hệ giảng viên." />
          )}
          <List
            itemLayout="horizontal"
            dataSource={rows}
            renderItem={(row) => (
              <List.Item actions={[cta(row)]}>
                <List.Item.Meta
                  title={
                    <Space>
                      <Text strong>{row.lab.code}</Text>
                      <Text>— {row.lab.title}</Text>
                      <Tag color={labStateTagColor(row.state)}>
                        {labStateLabel(row.state)}
                      </Tag>
                      {row.role === 'leader' && <Tag color="gold">Leader</Tag>}
                    </Space>
                  }
                  description={
                    <Text type="secondary">
                      Nhóm: {row.group.name} · Học kỳ: {row.group.semester || '—'}
                    </Text>
                  }
                />
              </List.Item>
            )}
          />
        </Card>

        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Button type="link" onClick={() => navigate('/my-reports')}>
            Lịch sử báo cáo của tôi <ArrowRightOutlined />
          </Button>
        </div>
      </div>
    </AppLayout>
  )
}
