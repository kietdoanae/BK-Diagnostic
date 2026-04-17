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
} from 'antd'
import { StopOutlined, RollbackOutlined } from '@ant-design/icons'
import {
  getSession,
  listSessionEvidence,
  listSteps,
  forceEndSession,
  resetSessionStep,
} from '../../services/labApi'

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
      width={840}
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

          <Divider>Tiến độ steps</Divider>
          <Timeline
            items={perStep.map((s) => ({
              color: s.reached ? 'green' : 'gray',
              children: (
                <div>
                  <Text strong>
                    #{s.step_order} — {s.title}
                  </Text>
                  <div style={{ fontSize: 12, color: '#6b7280' }}>
                    {s.evidenceCount} / {s.required_count || 0} evidence ·{' '}
                    type: <Text code>{s.evidence_type}</Text>
                  </div>
                  {s.sample.length > 0 && (
                    <ul
                      style={{
                        marginTop: 4,
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
                    </ul>
                  )}
                </div>
              ),
            }))}
          />
        </>
      )}
    </Drawer>
  )
}
