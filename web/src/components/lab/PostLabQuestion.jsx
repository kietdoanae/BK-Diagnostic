import { useState } from 'react'
import { Card, Upload, Button, Typography, Tag, Space, message } from 'antd'
import { UploadOutlined, DeleteOutlined } from '@ant-design/icons'
import MDEditor from '@uiw/react-md-editor'
import { supabase } from '../../services/supabase'

const { Text, Title } = Typography

const MAX_IMG_BYTES = 5 * 1024 * 1024

/**
 * Single post-lab question card. Supports free_text (markdown) and
 * image_upload. Value shape:
 *   free_text:     string (markdown)
 *   image_upload:  { path, original_name, size }
 */
export default function PostLabQuestion({
  question,
  value,
  onChange,
  userId,
  sessionId,
  children, // slot for EvidenceInlineViewer
}) {
  const [uploading, setUploading] = useState(false)

  async function handleImageUpload(file) {
    if (file.size > MAX_IMG_BYTES) {
      message.error('File quá 5MB')
      return Upload.LIST_IGNORE
    }
    setUploading(true)
    const ext = (file.name.split('.').pop() || 'png').toLowerCase()
    const path = `${userId}/${sessionId}/post/${question.id}-${Date.now()}.${ext}`
    const { error } = await supabase.storage
      .from('lab-images')
      .upload(path, file, { contentType: file.type || 'image/png' })
    setUploading(false)
    if (error) {
      message.error(error.message)
      return Upload.LIST_IGNORE
    }
    onChange?.({ path, original_name: file.name, size: file.size })
    message.success('Đã upload')
    return false
  }

  function clearImage() {
    onChange?.(null)
  }

  return (
    <Card style={{ marginBottom: 16 }}>
      <Space style={{ marginBottom: 8 }}>
        <Tag color="geekblue">Câu {question.question_order}</Tag>
        <Tag>{question.points} điểm</Tag>
        <Tag color={question.question_type === 'image_upload' ? 'purple' : 'default'}>
          {question.question_type}
        </Tag>
      </Space>
      <Title level={5} style={{ marginTop: 0 }}>{question.question_text}</Title>
      {question.hint && <Text type="secondary">💡 {question.hint}</Text>}

      <div style={{ marginTop: 16 }} data-color-mode="light">
        {question.question_type === 'free_text' && (
          <MDEditor
            value={value || ''}
            onChange={(v) => onChange?.(v ?? '')}
            height={220}
            preview="edit"
          />
        )}
        {question.question_type === 'image_upload' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            {value?.path ? (
              <Space>
                <Text>📎 {value.original_name}</Text>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={clearImage}>
                  Xóa
                </Button>
              </Space>
            ) : (
              <Upload
                accept="image/png,image/jpeg"
                showUploadList={false}
                beforeUpload={handleImageUpload}
              >
                <Button loading={uploading} icon={<UploadOutlined />}>
                  Upload hình (PNG/JPG, &lt;5MB)
                </Button>
              </Upload>
            )}
          </Space>
        )}
      </div>

      {children && <div style={{ marginTop: 16 }}>{children}</div>}
    </Card>
  )
}
