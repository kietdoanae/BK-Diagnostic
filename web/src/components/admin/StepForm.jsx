import { useEffect } from 'react'
import { Modal, Form, Input, InputNumber, Select, message } from 'antd'
import MarkdownEditor from './MarkdownEditor'
import { createStep, updateStep } from '../../services/labApi'

const EVIDENCE_TYPES = [
  { value: 'raw_frames', label: 'raw_frames (CAN frames từ app)' },
  { value: 'active_test', label: 'active_test (lệnh actuator)' },
  { value: 'screenshot', label: 'screenshot (ảnh upload từ web)' },
  { value: 'none', label: 'none (mốc xác nhận, không cần evidence)' },
]

export default function StepForm({ open, labId, step, nextOrder, onClose, onSaved }) {
  const [form] = Form.useForm()
  const isEdit = !!step

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (step) {
        form.setFieldsValue({
          step_order: step.step_order,
          title: step.title,
          instruction: step.instruction,
          evidence_type: step.evidence_type,
          required_count: step.required_count ?? 0,
          hint: step.hint || '',
        })
      } else {
        form.setFieldsValue({
          step_order: nextOrder,
          evidence_type: 'raw_frames',
          required_count: 0,
        })
      }
    }
  }, [open, step, nextOrder, form])

  async function handleOk() {
    const values = await form.validateFields()
    const payload = { ...values, lab_id: labId }
    const { data, error } = isEdit
      ? await updateStep(step.id, values)
      : await createStep(payload)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật step' : 'Đã tạo step')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `Chỉnh sửa step #${step.step_order}` : 'Thêm step mới'}
      onCancel={onClose}
      onOk={handleOk}
      okText="Lưu"
      cancelText="Hủy"
      width={760}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="step_order"
          label="Thứ tự (step_order)"
          rules={[{ required: true }]}
          tooltip="Có thể tinh chỉnh sau bằng kéo-thả ở danh sách step"
        >
          <InputNumber min={1} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item
          name="title"
          label="Tiêu đề step"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="instruction"
          label="Hướng dẫn (markdown)"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <MarkdownEditor height={220} />
        </Form.Item>
        <Form.Item
          name="evidence_type"
          label="Loại bằng chứng"
          rules={[{ required: true }]}
        >
          <Select options={EVIDENCE_TYPES} />
        </Form.Item>
        <Form.Item
          name="required_count"
          label="Số lượng tối thiểu (required_count)"
          tooltip="Đặt 0 cho evidence_type = none"
        >
          <InputNumber min={0} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="hint" label="Gợi ý (tùy chọn)">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
