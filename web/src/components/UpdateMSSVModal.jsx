import { useState } from 'react'
import { Modal, Form, Input, message } from 'antd'
import { supabase } from '../services/supabase'

export default function UpdateMSSVModal({ open, onSuccess }) {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

  async function handleSubmit() {
    try {
      const values = await form.validateFields()
      setLoading(true)
      const { error } = await supabase.rpc('update_profile_fields', {
        p_mssv: values.mssv,
        p_full_name: values.full_name || ''
      })
      if (error) {
        message.error('Lỗi: ' + error.message)
        return
      }
      message.success('Đã cập nhật MSSV')
      form.resetFields()
      onSuccess?.()
    } catch (err) {
      // form validation error
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      open={open}
      title="Cập nhật mã số sinh viên"
      okText="Cập nhật"
      cancelButtonProps={{ style: { display: 'none' } }}
      closable={false}
      maskClosable={false}
      confirmLoading={loading}
      onOk={handleSubmit}
    >
      <p style={{ marginBottom: 16, color: '#6B7280' }}>
        Để truy cập phần Lab, vui lòng nhập MSSV của bạn. <strong>MSSV không thể thay đổi sau khi lưu.</strong>
      </p>
      <Form form={form} layout="vertical">
        <Form.Item
          name="mssv"
          label="Mã số sinh viên"
          rules={[
            { required: true, message: 'MSSV là bắt buộc' },
            { pattern: /^\d{7,8}$/, message: 'MSSV phải là 7-8 chữ số' }
          ]}
        >
          <Input placeholder="VD: 2052345" maxLength={8} />
        </Form.Item>
        <Form.Item
          name="full_name"
          label="Họ và tên (tùy chọn)"
        >
          <Input placeholder="VD: Nguyễn Văn A" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
