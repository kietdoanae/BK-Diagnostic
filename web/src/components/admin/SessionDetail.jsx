import { useEffect, useState } from 'react'
import {
  Drawer,
  Descriptions,
  Tag,
  Timeline,
  Space,
  Button,
  Typography,
  Divider,
  Popconfirm,
  message,
  Empty,
  Tabs,
  Statistic,
  Row,
  Col,
} from 'antd'
import {
  StopOutlined,
  RollbackOutlined,
  ProfileOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import {
  getSession,
  listSessionEvidence,
  listSteps,
  forceEndSession,
  resetSessionStep,
} from '../../services/labApi'
import EvidenceInlineViewer from '../lab/EvidenceInlineViewer'

const { Text } = Typography

const STATUS_COLOR = {
  ACTIVE: 'processing',
  COMPLETED: 'success',
  EXPIRED: 'default',
  CANCELLED: 'error',
}

function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v.endsWith('Z') || v.includes('+') ? v : v + 'Z')
  return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })
}

export default function SessionDetail({ sessionId, open, onClose, onChanged }) {
  const [session, setSession] = useState(null)
  const [evidence, setEvidence] = useState([])
  const [steps, setSteps] = useState([])
  const [loading, setLoading] = useState(true)

  async function reload() {
    if (!sessionId) return
    setLoading(true)
    const [{ data: s }, { data: ev }] = await Promise.all([
      getSession(sessionId),
      listSessionEvidence(sessionId),
    ])
    setSession(s)
    setEvidence(ev || [])
    if (s?.lab?.id) {
      const { data: st } = await listSteps(s.lab.id)
      setSteps(st || [])
    }
    setLoading(false)
  }

  useEffect(() => {
    if (open) reload()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, sessionId])

  async function handleForceEnd() {
    const { error } = await forceEndSession(sessionId)
    if (error) message.error(error.message)
    else {
      message.success('Đã kết thúc session')
      onChanged?.()
      reload()
    }
  }

  async function handleResetStep() {
    const { error } = await resetSessionStep(sessionId)
    if (error) message.error(error.message)
    else {
      message.success('Đã reset step')
      reload()
    }
  }

  const perStep = steps.map((s) => {
    const items = evidence.filter((e) => e.step_id === s.id)
    return {
      ...s,
      evidenceCount: items.length,
      reached: (s.required_count || 0) === 0 ? true : items.length >= s.required_count,
      sample: items.slice(0, 3),
    }
  })

  return (
    <Drawer
      open={open}
      title={session ? `Session ${session.session_code}` : 'Session'}
      onClose={onClose}
      width="min(840px, 100vw)"
      destroyOnClose
      loading={loading}
    >
      {!session ? (
        <Empty description="Không tìm thấy session" />
      ) : (
        <>
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="Mã code">
              <Text code>{session.session_code}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <Tag color={STATUS_COLOR[session.status] || 'default'}>{session.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Lab">
              {session.lab?.code} — {session.lab?.title}
            </Descriptions.Item>
            <Descriptions.Item label="Nhóm">
              {session.group?.name} ({session.group?.semester || '—'})
            </Descriptions.Item>
            <Descriptions.Item label="Bắt đầu">{fmtTime(session.started_at)}</Descriptions.Item>
            <Descriptions.Item label="Kết thúc">{fmtTime(session.ended_at)}</Descriptions.Item>
            <Descriptions.Item label="Hết hạn lúc">{fmtTime(session.expires_at)}</Descriptions.Item>
            <Descriptions.Item label="Step hiện tại">
              {session.current_step_id || '— (không có step active)'}
            </Descriptions.Item>
          </Descriptions>

          {session.status === 'ACTIVE' && (
            <Space style={{ marginTop: 12 }}>
              <Popconfirm
                title="Force-end session này (chuyển sang CANCELLED)?"
                okText="Force end"
                cancelText="Hủy"
                okButtonProps={{ danger: true }}
                onConfirm={handleForceEnd}
              >
                <Button danger icon={<StopOutlined />}>
                  Force end
                </Button>
              </Popconfirm>
              <Button icon={<RollbackOutlined />} onClick={handleResetStep}>
                Reset current step
              </Button>
            </Space>
          )}

          <Divider />

          {/* Stats overview */}
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col xs={8}>
              <Statistic
                title="Tổng evidence"
                value={evidence.length}
                prefix={<DatabaseOutlined />}
                valueStyle={{ fontSize: 22, color: '#1565C0' }}
              />
            </Col>
            <Col xs={8}>
              <Statistic
                title="Steps đạt yêu cầu"
                value={perStep.filter((s) => s.reached).length}
                suffix={`/ ${perStep.length}`}
                valueStyle={{
                  fontSize: 22,
                  color: perStep.every((s) => s.reached) ? '#52c41a' : '#faad14',
                }}
              />
            </Col>
            <Col xs={8}>
              <Statistic
                title="Người tham gia"
                value={new Set(evidence.map((e) => e.submitted_by)).size}
                valueStyle={{ fontSize: 22, color: '#722ed1' }}
              />
            </Col>
          </Row>

          <Tabs
            items={[
              {
                key: 'progress',
                label: (
                  <Space size={6}>
                    <ProfileOutlined /> Tiến độ steps
                  </Space>
                ),
                children: (
                  <Timeline
                    items={perStep.map((s) => ({
                      color: s.reached ? 'green' : 'gray',
                      children: (
                        <div>
                          <Text strong>
                            #{s.step_order} — {s.title}
                          </Text>
                          <div style={{ fontSize: 12, color: '#6b7280', marginTop: 2 }}>
                            <Tag color={s.reached ? 'success' : 'default'} style={{ marginRight: 6 }}>
                              {s.evidenceCount} / {s.required_count || 0}
                            </Tag>
                            type: <Text code style={{ fontSize: 11 }}>{s.evidence_type}</Text>
                          </div>
                          {s.sample.length > 0 && (
                            <ul
                              style={{
                                marginTop: 6,
                                paddingLeft: 16,
                                fontSize: 11,
                                color: '#9ca3af',
                              }}
                            >
                              {s.sample.map((ev) => (
                                <li key={ev.id}>
                                  {fmtTime(ev.created_at)} · {ev.evidence_type} · by{' '}
                                  {ev.submitter?.mssv || ev.submitter?.username || '—'}
                                </li>
                              ))}
                              {s.evidenceCount > s.sample.length && (
                                <li style={{ fontStyle: 'italic' }}>
                                  ... và {s.evidenceCount - s.sample.length} evidence khác
                                  (xem tab "Bằng chứng")
                                </li>
                              )}
                            </ul>
                          )}
                        </div>
                      ),
                    }))}
                  />
                ),
              },
              {
                key: 'evidence',
                label: (
                  <Space size={6}>
                    <DatabaseOutlined /> Bằng chứng ({evidence.length})
                  </Space>
                ),
                children:
                  evidence.length === 0 ? (
                    <Empty description="Chưa có evidence nào trong session này" />
                  ) : (
                    <EvidenceInlineViewer evidence={evidence} steps={steps} />
                  ),
              },
            ]}
          />
        </>
      )}
    </Drawer>
  )
}
