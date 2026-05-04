import { useEffect, useState } from 'react'
import {
  Card,
  Button,
  Table,
  Tag,
  Space,
  Typography,
  Drawer,
  Tabs,
  Popconfirm,
  message,
  Input,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SearchOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  listLabs,
  deleteLab,
  listSteps,
  listQuestions,
} from '../../services/labApi'
import LabForm from '../../components/admin/LabForm'
import StepList from '../../components/admin/StepList'
import QuestionList from '../../components/admin/QuestionList'

const { Text, Title } = Typography

export default function LabsAdminTab() {
  const { t } = useTranslation()
  const [labs, setLabs] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [labFormOpen, setLabFormOpen] = useState(false)
  const [editingLab, setEditingLab] = useState(null)
  const [drawerLab, setDrawerLab] = useState(null)
  const [steps, setSteps] = useState([])
  const [preQs, setPreQs] = useState([])
  const [postQs, setPostQs] = useState([])

  async function reload() {
    setLoading(true)
    const { data, error } = await listLabs()
    setLoading(false)
    if (error) message.error(error.message)
    else setLabs(data || [])
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    reload()
  }, [])

  async function reloadDrawer(labId) {
    const [{ data: s }, { data: pre }, { data: post }] = await Promise.all([
      listSteps(labId),
      listQuestions(labId, 'pre_lab'),
      listQuestions(labId, 'post_lab'),
    ])
    setSteps(s || [])
    setPreQs(pre || [])
    setPostQs(post || [])
  }

  async function openDrawer(lab) {
    setDrawerLab(lab)
    await reloadDrawer(lab.id)
  }

  async function handleDelete(lab) {
    const { error } = await deleteLab(lab.id)
    if (error) message.error(error.message)
    else {
      message.success(t('admin.lab.deleted'))
      reload()
    }
  }

  const filtered = labs.filter((l) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      l.code.toLowerCase().includes(q) || l.title.toLowerCase().includes(q)
    )
  })

  const columns = [
    {
      title: t('common.code'),
      dataIndex: 'code',
      render: (v) => <Text code>{v}</Text>,
    },
    { title: t('common.title'), dataIndex: 'title' },
    {
      title: t('admin.labsTab.orderCol'),
      dataIndex: 'order_index',
      width: 80,
    },
    {
      title: t('admin.labsTab.passPct'),
      dataIndex: 'pre_quiz_pass_threshold',
      width: 90,
    },
    {
      title: t('common.status'),
      dataIndex: 'is_published',
      width: 110,
      render: (v) =>
        v ? <Tag color="success">{t('admin.labsTab.published')}</Tag> : <Tag>{t('admin.labsTab.draft')}</Tag>,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      align: 'right',
      render: (_, lab) => (
        <Space>
          <Button size="small" onClick={() => openDrawer(lab)}>
            {t('admin.labsTab.manageContent')}
          </Button>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingLab(lab)
              setLabFormOpen(true)
            }}
          >
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('admin.lab.deleteConfirm')}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(lab)}
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
        }}
      >
        <Input
          prefix={<SearchOutlined />}
          placeholder={t('admin.labsTab.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 280 }}
          allowClear
        />
        <Button icon={<ReloadOutlined />} onClick={reload}>
          {t('common.refresh')}
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          style={{ marginLeft: 'auto' }}
          onClick={() => {
            setEditingLab(null)
            setLabFormOpen(true)
          }}
        >
          {t('admin.lab.createBtn')}
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

      <LabForm
        open={labFormOpen}
        lab={editingLab}
        onClose={() => setLabFormOpen(false)}
        onSaved={() => {
          setLabFormOpen(false)
          reload()
        }}
      />

      <Drawer
        open={!!drawerLab}
        onClose={() => setDrawerLab(null)}
        title={
          drawerLab
            ? t('admin.labsTab.drawerTitle', { code: drawerLab.code, title: drawerLab.title })
            : ''
        }
        width={960}
        destroyOnClose
      >
        {drawerLab && (
          <Tabs
            items={[
              {
                key: 'steps',
                label: `${t('admin.lab.stepsTab')} (${steps.length})`,
                children: (
                  <StepList
                    labId={drawerLab.id}
                    steps={steps}
                    onChanged={() => reloadDrawer(drawerLab.id)}
                  />
                ),
              },
              {
                key: 'pre',
                label: `${t('admin.lab.preTab')} (${preQs.length})`,
                children: (
                  <QuestionList
                    labId={drawerLab.id}
                    phase="pre_lab"
                    questions={preQs}
                    onChanged={() => reloadDrawer(drawerLab.id)}
                  />
                ),
              },
              {
                key: 'post',
                label: `${t('admin.lab.postTab')} (${postQs.length})`,
                children: (
                  <QuestionList
                    labId={drawerLab.id}
                    phase="post_lab"
                    questions={postQs}
                    onChanged={() => reloadDrawer(drawerLab.id)}
                  />
                ),
              },
            ]}
          />
        )}
      </Drawer>
    </Card>
  )
}
