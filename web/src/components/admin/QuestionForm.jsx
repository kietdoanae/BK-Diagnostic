import { useEffect } from 'react'
import {
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Button,
  Typography,
  message,
} from 'antd'
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons'
import { createQuestion, updateQuestion } from '../../services/labApi'

const { Text } = Typography

const QUESTION_TYPES = [
  { value: 'multiple_choice', label: 'Multiple Choice' },
  { value: 'free_text', label: 'Free Text' },
  { value: 'image_upload', label: 'Image Upload' },
]

export default function QuestionForm({
  open,
  labId,
  phase,
  question,
  nextOrder,
  onClose,
  onSaved,
}) {
  const [form] = Form.useForm()
  const isEdit = !!question
  const qType = Form.useWatch('question_type', form) ?? 'multiple_choice'

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (question) {
        const optsObj = question.options || {}
        const opts = Object.entries(optsObj).map(([k, v]) => ({ key: k, text: v }))
        form.setFieldsValue({
          question_order: question.question_order,
          question_type: question.question_type,
          question_text: question.question_text,
          options: opts.length ? opts : [{ key: 'A', text: '' }],
          correct_answer: question.correct_answer || '',
          points: question.points ?? 1,
          hint: question.hint || '',
        })
      } else {
        form.setFieldsValue({
          question_order: nextOrder,
          question_type: 'multiple_choice',
          options: [
            { key: 'A', text: '' },
            { key: 'B', text: '' },
          ],
          points: 1,
        })
      }
    }
  }, [open, question, nextOrder, form])

  async function handleOk() {
    const values = await form.validateFields()
    const payload = {
      lab_id: labId,
      phase,
      question_order: values.question_order,
      question_type: values.question_type,
      question_text: values.question_text,
      points: values.points,
      hint: values.hint || null,
      options: null,
      correct_answer: null,
    }
    if (values.question_type === 'multiple_choice') {
      const optsObj = {}
      for (const o of values.options || []) {
        if (o.key && o.text) optsObj[o.key] = o.text
      }
      payload.options = optsObj
      payload.correct_answer = values.correct_answer || null
    }
    const { data, error } = isEdit
      ? await updateQuestion(question.id, payload)
      : await createQuestion(payload)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật câu hỏi' : 'Đã tạo câu hỏi')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={
        isEdit
          ? `Chỉnh sửa câu hỏi #${question.question_order} (${phase})`
          : `Thêm câu hỏi (${phase})`
      }
      onCancel={onClose}
      onOk={handleOk}
      okText="Lưu"
      cancelText="Hủy"
      width={720}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Space>
          <Form.Item name="question_order" label="Thứ tự" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="question_type" label="Loại câu hỏi" rules={[{ required: true }]}>
            <Select
              style={{ width: 220 }}
              options={QUESTION_TYPES}
            />
          </Form.Item>
          <Form.Item name="points" label="Điểm" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: 80 }} />
          </Form.Item>
        </Space>

        <Form.Item
          name="question_text"
          label="Nội dung câu hỏi"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input.TextArea rows={3} />
        </Form.Item>

        {qType === 'multiple_choice' && (
          <>
            <Text strong>Các phương án</Text>
            <Form.List name="options">
              {(fields, { add, remove }) => (
                <div style={{ marginTop: 8 }}>
                  {fields.map((field) => (
                    <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }}>
                      <Form.Item
                        name={[field.name, 'key']}
                        rules={[{ required: true, message: 'Key' }]}
                        style={{ width: 80, marginBottom: 0 }}
                      >
                        <Input placeholder="A" maxLength={4} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'text']}
                        rules={[{ required: true, message: 'Nội dung' }]}
                        style={{ width: 460, marginBottom: 0 }}
                      >
                        <Input placeholder="Nội dung lựa chọn" />
                      </Form.Item>
                      <Button
                        type="text"
                        danger
                        icon={<MinusCircleOutlined />}
                        onClick={() => remove(field.name)}
                      />
                    </Space>
                  ))}
                  <Button
                    block
                    icon={<PlusOutlined />}
                    onClick={() => add({ key: '', text: '' })}
                  >
                    Thêm phương án
                  </Button>
                </div>
              )}
            </Form.List>
            <Form.Item
              name="correct_answer"
              label="Đáp án đúng (key)"
              rules={[{ required: true, message: 'Bắt buộc với MC' }]}
              style={{ marginTop: 12 }}
            >
              <Input placeholder="A" style={{ width: 100 }} />
            </Form.Item>
          </>
        )}

        <Form.Item name="hint" label="Gợi ý (tùy chọn)">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
