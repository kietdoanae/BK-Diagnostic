import { useEffect } from 'react'
import { Modal, Form, Input, InputNumber, Switch, message } from 'antd'
import MarkdownEditor from './MarkdownEditor'
import { createLab, updateLab } from '../../services/labApi'

/**
 * Modal form for creating/editing a Lab.
 * Props:
 *   open      — boolean
 *   lab       — existing lab object (for edit) or null (for create)
 *   onClose() — close without saving
 *   onSaved(lab) — called after successful create/update with the saved row
 */
export default function LabForm({ open, lab, onClose, onSaved }) {
  const [form] = Form.useForm()
  const isEdit = !!lab

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (lab) {
        form.setFieldsValue({
          code: lab.code,
          title: lab.title,
          description: lab.description || '',
          order_index: lab.order_index ?? 0,
          pre_quiz_pass_threshold: lab.pre_quiz_pass_threshold ?? 70,
          is_published: !!lab.is_published,
        })
      } else {
        form.setFieldsValue({
          order_index: 0,
          pre_quiz_pass_threshold: 70,
          is_published: false,
        })
      }
    }
  }, [open, lab, form])

  async function handleOk() {
    const values = await form.validateFields()
    const { data, error } = isEdit
      ? await updateLab(lab.id, values)
      : await createLab(values)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật lab' : 'Đã tạo lab')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `Chỉnh sửa lab: ${lab.code}` : 'Tạo lab mới'}
      onCancel={onClose}
      onOk={handleOk}
      okText="Lưu"
      cancelText="Hủy"
      width={760}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="code"
          label="Mã lab"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input placeholder="LAB-01-CAN-OBD2" />
        </Form.Item>
        <Form.Item
          name="title"
          label="Tiêu đề"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input placeholder="CAN BUS & OBD2 Fundamentals" />
        </Form.Item>
        <Form.Item name="description" label="Mô tả (markdown)">
          <MarkdownEditor height={200} />
        </Form.Item>
        <Form.Item name="order_index" label="Thứ tự hiển thị">
          <InputNumber min={0} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item
          name="pre_quiz_pass_threshold"
          label="Ngưỡng đậu pre-quiz (%)"
          rules={[{ required: true }]}
        >
          <InputNumber min={0} max={100} style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="is_published" label="Đã publish?" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  )
}
