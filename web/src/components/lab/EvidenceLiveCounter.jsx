import { Progress, Space, Typography } from 'antd'
import { FireOutlined } from '@ant-design/icons'

const { Text } = Typography

/**
 * Ring-style live counter for the current step.
 *
 *   got / required → percent; required=0 shows "N/A" fill.
 */
export default function EvidenceLiveCounter({ got, required }) {
  if (required === 0) {
    return (
      <Space>
        <FireOutlined style={{ color: '#9ca3af' }} />
        <Text type="secondary">Không yêu cầu bằng chứng</Text>
      </Space>
    )
  }
  const percent = Math.min(100, Math.round((got / required) * 100))
  const done = got >= required
  return (
    <Space size={16} align="center">
      <Progress
        type="circle"
        percent={percent}
        size={72}
        status={done ? 'success' : 'active'}
        format={() => `${got}/${required}`}
      />
      <div>
        <Text strong style={{ fontSize: 14, display: 'block' }}>
          {done ? 'Đã đủ bằng chứng' : 'Đang thu thập...'}
        </Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          Cập nhật realtime từ app
        </Text>
      </div>
    </Space>
  )
}
