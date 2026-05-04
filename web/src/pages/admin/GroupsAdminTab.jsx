import { useEffect, useState } from 'react'
import {
  Card,
  Button,
  Table,
  Tag,
  Space,
  Typography,
  Popconfirm,
  message,
  Select,
  Input,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  ImportOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  listGroups,
  deleteGroup,
  listLabs,
  listGroupMembers,
} from '../../services/labApi'
import GroupForm from '../../components/admin/GroupForm'
import GroupBulkImport from '../../components/admin/GroupBulkImport'

const { Text } = Typography

export default function GroupsAdminTab() {
  const { t } = useTranslation()
  const [labs, setLabs] = useState([])
  const [groups, setGroups] = useState([])
  const [memberCache, setMemberCache] = useState({})
  const [loading, setLoading] = useState(true)
  const [filterLab, setFilterLab] = useState(null)
  const [search, setSearch] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingGroup, setEditingGroup] = useState(null)
  const [bulkOpen, setBulkOpen] = useState(false)

  async function reload() {
    setLoading(true)
    const [{ data: gs, error }, { data: ls }] = await Promise.all([
      listGroups(filterLab),
      listLabs(),
    ])
    setLoading(false)
    if (error) {
      message.error(error.message)
      return
    }
    setLabs(ls || [])
    setGroups(gs || [])
    const memberEntries = await Promise.all(
      (gs || []).map(async (g) => {
        const { data } = await listGroupMembers(g.id)
        return [g.id, data || []]
      })
    )
    setMemberCache(Object.fromEntries(memberEntries))
  }

  useEffect(() => {
    reload()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterLab])

  async function handleDelete(g) {
    const { error } = await deleteGroup(g.id)
    if (error) message.error(error.message)
    else {
      message.success(t('admin.group.deleted'))
      reload()
    }
  }

  const filtered = groups.filter((g) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      g.name.toLowerCase().includes(q) ||
      (g.semester || '').toLowerCase().includes(q) ||
      (g.lab?.code || '').toLowerCase().includes(q)
    )
  })

  const columns = [
    {
      title: t('admin.group.lab'),
      dataIndex: ['lab', 'code'],
      width: 200,
      render: (v, r) => (
        <div>
          <Text code>{v}</Text>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{r.lab?.title}</div>
        </div>
      ),
    },
    { title: t('admin.group.name'), dataIndex: 'name' },
    { title: t('admin.group.semester'), dataIndex: 'semester', width: 140 },
    {
      title: t('admin.group.members'),
      key: 'members',
      render: (_, g) => {
        const ms = memberCache[g.id] || []
        if (ms.length === 0) return <Text type="secondary">{t('admin.group.membersEmpty')}</Text>
        return (
          <Space direction="vertical" size={2}>
            {ms.map((m) => {
              const name = m.profile?.full_name || m.profile?.username || '—'
              const mssv = m.profile?.mssv
              const isLeader = m.role === 'leader'
              return (
                <span key={m.user_id} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  {isLeader && <Tag color="gold" style={{ margin: 0 }}>👑 {t('labSession.leaderLabel')}</Tag>}
                  <Text>{name}</Text>
                  {mssv && (
                    <Text type="secondary" style={{ fontSize: 12 }}>[{mssv}]</Text>
                  )}
                </span>
              )
            })}
          </Space>
        )
      },
    },
    {
      title: t('common.actions'),
      key: 'actions',
      align: 'right',
      width: 180,
      render: (_, g) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingGroup(g)
              setFormOpen(true)
            }}
          >
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('admin.group.deleteConfirm')}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(g)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card style={{ borderRadius: 16 }} styles={{ body: { padding: 0 } }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Select
          placeholder={t('admin.group.filterLab')}
          value={filterLab || undefined}
          onChange={(v) => setFilterLab(v ?? null)}
          allowClear
          style={{ width: 280 }}
          options={labs.map((l) => ({
            value: l.id,
            label: `${l.code} — ${l.title}`,
          }))}
        />
        <Input
          prefix={<SearchOutlined />}
          placeholder={t('admin.group.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 260 }}
          allowClear
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          {t('common.refresh')}
        </Button>
        <Button
          icon={<ImportOutlined />}
          onClick={() => setBulkOpen(true)}
          style={{ marginLeft: 'auto' }}
        >
          {t('admin.group.bulkImport')}
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingGroup(null)
            setFormOpen(true)
          }}
        >
          {t('admin.group.createBtn')}
        </Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={filtered}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 'max-content' }}
      />

      <GroupForm
        open={formOpen}
        labId={filterLab}
        labs={labs}
        group={editingGroup}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false)
          reload()
        }}
      />

      <GroupBulkImport
        open={bulkOpen}
        labs={labs}
        onClose={() => setBulkOpen(false)}
        onImported={() => {
          setBulkOpen(false)
          reload()
        }}
      />
    </Card>
  )
}
