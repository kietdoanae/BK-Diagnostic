import { List, Tag, Typography, Button } from 'antd'
import { CheckCircleOutlined, PlayCircleOutlined, ClockCircleOutlined } from '@ant-design/icons'

const { Text } = Typography

/**
 * Left-column sequential step list for the student practice dashboard.
 *
 * Props:
 *   steps                — ordered lab_steps rows
 *   currentStepId        — session.current_step_id (or null)
 *   countsByStep         — { [stepId]: number } of evidence counted
 *   isLeader             — boolean, gates the "Chọn" button
 *   onSelectStep(step)   — leader-only: make this step current
 *   readOnly             — boolean, hides all action buttons (expired/completed session)
 */
export default function StepList({
  steps,
  currentStepId,
  countsByStep,
  isLeader,
  onSelectStep,
  readOnly = false,
}) {
  function statusFor(step) {
    const got = countsByStep[step.id] || 0
    const done = step.required_count === 0
      ? step.id !== currentStepId && got === 0 && false // 'none' steps never auto-complete by count
      : got >= step.required_count
    if (done) return { tag: 'success', icon: <CheckCircleOutlined />, label: 'Đã đủ' }
    if (step.id === currentStepId) return { tag: 'processing', icon: <PlayCircleOutlined />, label: 'Đang làm' }
    return { tag: 'default', icon: <ClockCircleOutlined />, label: 'Chờ' }
  }

  return (
    <List
      header={<Text strong>Các bước thực hành</Text>}
      itemLayout="horizontal"
      dataSource={steps}
      renderItem={(step) => {
        const s = statusFor(step)
        const got = countsByStep[step.id] || 0
        return (
          <List.Item
            actions={
              !readOnly && isLeader && step.id !== currentStepId
                ? [<Button size="small" key="sel" onClick={() => onSelectStep?.(step)}>Chọn</Button>]
                : []
            }
          >
            <List.Item.Meta
              title={
                <span>
                  <Tag color={s.tag} icon={s.icon}>{s.label}</Tag>
                  Bước {step.step_order}. {step.title}
                </span>
              }
              description={
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {step.evidence_type === 'none'
                    ? 'Không cần bằng chứng'
                    : `${got} / ${step.required_count} · ${step.evidence_type}`}
                </Text>
              }
            />
          </List.Item>
        )
      }}
    />
  )
}
