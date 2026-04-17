import { useEffect, useState, useRef } from 'react'
import {
  Modal,
  Form,
  Input,
  Select,
  Space,
  Button,
  Tag,
  List,
  Typography,
  Popconfirm,
  message,
} from 'antd'
import { CrownOutlined, DeleteOutlined } from '@ant-design/icons'
import {
  createGroup,
  updateGroup,
  listGroupMembers,
  addGroupMember,
  removeGroupMember,
  setGroupLeader,
  searchProfilesByMssvOrName,
} from '../../services/labApi'

const { Text } = Typography

export default function GroupForm({ open, labId, group, labs, onClose, onSaved }) {
  const [form] = Form.useForm()
  const isEdit = !!group

  const [members, setMembers] = useState([])
  const [memberLoading, setMemberLoading] = useState(false)
  const [searchOpts, setSearchOpts] = useState([])
  const [searchVal, setSearchVal] = useState(null)
  const debounceRef = useRef(null)

  async function loadMembers(gid) {
    setMemberLoading(true)
    const { data, error } = await listGroupMembers(gid)
    setMemberLoading(false)
    if (error) message.error(error.message)
    else setMembers(data || [])
  }

  useEffect(() => {
    if (open) {
      form.resetFields()
      if (group) {
        form.setFieldsValue({
          lab_id: group.lab_id,
          name: group.name,
          semester: group.semester || '',
        })
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadMembers(group.id)
      } else {
        form.setFieldsValue({ lab_id: labId || undefined })
        setMembers([])
      }
    }
  }, [open, group, labId, form])

  function onSearch(value) {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      const { data } = await searchProfilesByMssvOrName(value, 20)
      setSearchOpts(
        (data || []).map((u) => ({
          value: u.id,
          label: `${u.mssv || '—'} · ${u.full_name || u.username || u.email}`,
          raw: u,
        }))
      )
    }, 200)
  }

  async function handleAddMember() {
    if (!searchVal || !group) return
    if (members.some((m) => m.user_id === searchVal)) {
      message.warning('Sinh viên này đã có trong nhóm')
      return
    }
    const role = members.length === 0 ? 'leader' : 'member'
    const { error } = await addGroupMember(group.id, searchVal, role)
    if (error) {
      message.error(error.message)
      return
    }
    message.success('Đã thêm thành viên')
    setSearchVal(null)
    loadMembers(group.id)
  }

  async function handleRemove(userId) {
    const { error } = await removeGroupMember(group.id, userId)
    if (error) message.error(error.message)
    else loadMembers(group.id)
  }

  async function handlePromote(userId) {
    const { error } = await setGroupLeader(group.id, userId)
    if (error) message.error(error.message)
    else {
      message.success('Đã chuyển leader')
      loadMembers(group.id)
    }
  }

  async function handleSaveMeta() {
    const values = await form.validateFields()
    const { data, error } = isEdit
      ? await updateGroup(group.id, values)
      : await createGroup(values)
    if (error) {
      message.error(error.message)
      return
    }
    message.success(isEdit ? 'Đã cập nhật' : 'Đã tạo nhóm')
    onSaved?.(data)
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `Chỉnh sửa nhóm: ${group.name}` : 'Tạo nhóm mới'}
      onCancel={onClose}
      onOk={handleSaveMeta}
      okText="Lưu"
      cancelText="Đóng"
      width={780}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="lab_id"
          label="Lab"
          rules={[{ required: true, message: 'Chọn lab' }]}
        >
          <Select
            placeholder="Chọn lab"
            options={(labs || []).map((l) => ({
              value: l.id,
              label: `${l.code} — ${l.title}`,
            }))}
            disabled={isEdit}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên nhóm"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input placeholder="Nhóm 1 · Tổ A · Sáng thứ 2" />
        </Form.Item>
        <Form.Item name="semester" label="Học kỳ (tùy chọn)">
          <Input placeholder="HK2-2025-2026" />
        </Form.Item>
      </Form>

      {isEdit && (
        <>
          <Text strong>Thành viên ({members.length})</Text>
          <Space style={{ display: 'flex', marginTop: 8 }}>
            <Select
              showSearch
              placeholder="Tìm theo MSSV / tên / email…"
              filterOption={false}
              onSearch={onSearch}
              value={searchVal}
              onChange={(v) => setSearchVal(v)}
              options={searchOpts}
              style={{ width: 480 }}
              notFoundContent={null}
              allowClear
            />
            <Button type="primary" onClick={handleAddMember} disabled={!searchVal}>
              Thêm
            </Button>
          </Space>

          <List
            style={{ marginTop: 12 }}
            loading={memberLoading}
            dataSource={members}
            locale={{ emptyText: 'Chưa có thành viên' }}
            renderItem={(m) => (
              <List.Item
                actions={[
                  m.role !== 'leader' && (
                    <Button
                      key="leader"
                      size="small"
                      icon={<CrownOutlined />}
                      onClick={() => handlePromote(m.user_id)}
                    >
                      Đặt làm leader
                    </Button>
                  ),
                  <Popconfirm
                    key="del"
                    title="Xóa thành viên này khỏi nhóm?"
                    okText="Xóa"
                    cancelText="Hủy"
                    onConfirm={() => handleRemove(m.user_id)}
                  >
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>,
                ].filter(Boolean)}
              >
                <Space>
                  {m.role === 'leader' && <Tag color="gold">Leader</Tag>}
                  <Text code>{m.profile?.mssv || '—'}</Text>
                  <Text>{m.profile?.full_name || m.profile?.username || '—'}</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {m.profile?.email}
                  </Text>
                </Space>
              </List.Item>
            )}
          />
        </>
      )}
    </Modal>
  )
}
