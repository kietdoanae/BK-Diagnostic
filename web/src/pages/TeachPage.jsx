import { useEffect, useState } from 'react'
import { Tabs, Card, Typography, Space, Statistic, Row, Col } from 'antd'
import {
  ReadOutlined,
  FileTextOutlined,
  TeamOutlined,
  ExperimentOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'
import AppLayout from '../components/AppLayout'
import LabsAdminTab from './admin/LabsAdminTab'
import GroupsAdminTab from './admin/GroupsAdminTab'
import SessionsAdminTab from './admin/SessionsAdminTab'
import SubmissionsAdminTab from './admin/SubmissionsAdminTab'
import { useAuth } from '../hooks/useAuth'
import {
  listLabs,
  listGroups,
  listSessions,
  listPostSubmissions,
} from '../services/labApi'

const { Title, Text } = Typography

/**
 * Hero stats — số liệu tổng quan ở đầu trang
 * Mục đích: cho giảng viên thấy ngay quy mô đang quản lý.
 */
function TeachStatsHero({ profile }) {
  const [stats, setStats] = useState({
    labs: null,
    groups: null,
    activeSessions: null,
    submissions: null,
  })

  useEffect(() => {
    let cancelled = false
    async function load() {
      const [labsRes, groupsRes, sessionsRes, subsRes] = await Promise.all([
        listLabs(),
        listGroups(null),
        listSessions({ status: 'ACTIVE' }),
        listPostSubmissions({}),
      ])
      if (cancelled) return
      setStats({
        labs:           labsRes.data?.length ?? 0,
        groups:         groupsRes.data?.length ?? 0,
        activeSessions: sessionsRes.data?.length ?? 0,
        submissions:    subsRes.data?.length ?? 0,
      })
    }
    load()
    return () => { cancelled = true }
  }, [])

  const items = [
    { label: 'Bài Lab',        value: stats.labs,           icon: <ExperimentOutlined />, color: '#1565C0' },
    { label: 'Nhóm thực hành', value: stats.groups,         icon: <TeamOutlined />,       color: '#0097A7' },
    { label: 'Phiên đang chạy',value: stats.activeSessions, icon: <PlayCircleOutlined />, color: '#2E7D32' },
    { label: 'Bài nộp',        value: stats.submissions,    icon: <CheckCircleOutlined />, color: '#6A1B9A' },
  ]

  return (
    <Card
      style={{
        background: 'linear-gradient(135deg, #0A1E6E 0%, #1565C0 50%, #1E88E5 100%)',
        border: 'none',
        borderRadius: 20,
        marginBottom: 24,
      }}
      styles={{ body: { padding: '24px 28px' } }}
    >
      <Space direction="vertical" size={4} style={{ marginBottom: 18 }}>
        <Text style={{ color: 'rgba(255,255,255,0.65)', fontSize: 12, letterSpacing: 1 }}>
          GIẢNG VIÊN ·{' '}
          <span style={{ fontWeight: 600 }}>
            {profile?.full_name || profile?.username || 'Instructor'}
          </span>
        </Text>
        <Title level={2} style={{ color: 'white', margin: 0, fontWeight: 800 }}>
          Phần giảng dạy
        </Title>
        <Text style={{ color: 'rgba(255,255,255,0.78)' }}>
          Tạo và quản lý nội dung Lab · xem báo cáo sinh viên · quản lý nhóm thực hành
        </Text>
      </Space>

      <Row gutter={[16, 16]}>
        {items.map((it) => (
          <Col xs={12} sm={12} md={6} key={it.label}>
            <div
              style={{
                background: 'rgba(255,255,255,0.10)',
                border: '1px solid rgba(255,255,255,0.18)',
                borderRadius: 14,
                padding: '14px 16px',
                backdropFilter: 'blur(8px)',
              }}
            >
              <Space size={10} style={{ width: '100%' }}>
                <div
                  style={{
                    width: 38,
                    height: 38,
                    borderRadius: 10,
                    background: 'rgba(255,255,255,0.18)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'white',
                    fontSize: 18,
                  }}
                >
                  {it.icon}
                </div>
                <div>
                  <div style={{ color: 'rgba(255,255,255,0.65)', fontSize: 11, letterSpacing: 0.5 }}>
                    {it.label.toUpperCase()}
                  </div>
                  <div style={{ color: 'white', fontSize: 22, fontWeight: 800, lineHeight: 1.1 }}>
                    {it.value === null ? '…' : it.value}
                  </div>
                </div>
              </Space>
            </div>
          </Col>
        ))}
      </Row>
    </Card>
  )
}

export default function TeachPage() {
  const { profile } = useAuth()

  const items = [
    {
      key: 'labs',
      label: (
        <Space>
          <ReadOutlined /> Quản lý Lab
        </Space>
      ),
      children: <LabsAdminTab />,
    },
    {
      key: 'groups',
      label: (
        <Space>
          <TeamOutlined /> Quản lý nhóm
        </Space>
      ),
      children: <GroupsAdminTab />,
    },
    {
      key: 'sessions',
      label: (
        <Space>
          <PlayCircleOutlined /> Phiên thực hành
        </Space>
      ),
      children: <SessionsAdminTab />,
    },
    {
      key: 'reports',
      label: (
        <Space>
          <FileTextOutlined /> Báo cáo sinh viên
        </Space>
      ),
      children: <SubmissionsAdminTab />,
    },
  ]

  return (
    <AppLayout>
      <div style={{ maxWidth: 1400, margin: '0 auto', padding: '20px 24px' }}>
        <TeachStatsHero profile={profile} />
        <Tabs
          defaultActiveKey="labs"
          items={items}
          size="large"
          tabBarStyle={{
            marginBottom: 16,
            paddingLeft: 4,
          }}
        />
      </div>
    </AppLayout>
  )
}
