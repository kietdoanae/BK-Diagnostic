import { useState } from 'react'
import { Card, Button, Upload, Space, Typography, Empty, Alert, message } from 'antd'
import { UploadOutlined, PlayCircleOutlined, StopOutlined, CheckOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import EvidenceLiveCounter from './EvidenceLiveCounter'
import { uploadScreenshotEvidence } from '../../services/labApi'

const { Title, Text } = Typography

/**
 * Right-column detail for the currently selected step.
 *
 * Props:
 *   step, session, isLeader, userId,
 *   countForStep  — number (from useLiveEvidence.countsByStep)
 *   onStart, onEnd  — leader-only callbacks (call RPCs in parent)
 */
export default function StepDetail({
  step,
  session,
  isLeader,
  userId,
  countForStep,
  onStart,
  onEnd,
  readOnly = false,
}) {
  const [uploading, setUploading] = useState(false)

  if (!step) {
    return (
      <Card>
        <Empty description={
          isLeader
            ? 'Chọn một bước ở bên trái để bắt đầu.'
            : 'Leader chưa chọn bước nào.'
        } />
      </Card>
    )
  }

  const isCurrent = session?.current_step_id === step.id

  async function handleUpload(file) {
    setUploading(true)
    const { error } = await uploadScreenshotEvidence({
      sessionId: session.id,
      stepId: step.id,
      userId,
      file,
    })
    setUploading(false)
    if (error) {
      message.error(`Upload lỗi: ${error.message}`)
      return Upload.LIST_IGNORE
    }
    message.success('Đã upload bằng chứng')
    return false // prevent default upload
  }

  return (
    <Card>
      <Title level={4}>
        Bước {step.step_order}. {step.title}
      </Title>
      <Text type="secondary">Loại bằng chứng: <Text code>{step.evidence_type}</Text></Text>

      <div data-color-mode="light" style={{ marginTop: 16 }}>
        <MDEditor.Markdown source={step.instruction || ''} />
      </div>

      {step.hint && (
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          message={<span>💡 {step.hint}</span>}
        />
      )}

      <div style={{ marginTop: 20 }}>
        <EvidenceLiveCounter got={countForStep} required={step.required_count} />
      </div>

      {!readOnly && (
        <Space style={{ marginTop: 24, width: '100%' }} wrap>
          {isLeader && !isCurrent && (
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={() => onStart?.(step)}
            >
              Bắt đầu bước này
            </Button>
          )}
          {isLeader && isCurrent && (
            <Button
              danger
              icon={<StopOutlined />}
              onClick={() => onEnd?.()}
            >
              Kết thúc bước
            </Button>
          )}
          {isCurrent && step.evidence_type === 'screenshot' && (
            <Upload
              accept="image/png,image/jpeg"
              showUploadList={false}
              beforeUpload={handleUpload}
            >
              <Button loading={uploading} icon={<UploadOutlined />}>
                Upload screenshot
              </Button>
            </Upload>
          )}
          {isCurrent && step.evidence_type === 'none' && isLeader && (
            <Button
              icon={<CheckOutlined />}
              onClick={() => onEnd?.()}
            >
              Đánh dấu hoàn tất
            </Button>
          )}
        </Space>
      )}
    </Card>
  )
}
