import { useState } from 'react'
import { Layout, Menu, Avatar, Dropdown, Button, Space, Typography, Tag } from 'antd'
import {
  DashboardOutlined,
  CrownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ApartmentOutlined,
  ExperimentOutlined,
  HistoryOutlined,
  ReadOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
  TeamOutlined,
  StarFilled,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { logActivity } from '../services/api'

const { Sider, Header, Content } = Layout
const { Text } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']

function avatarColor(username = '') {
  let h = 0
  for (const c of username) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

/** Role badge styling — màu nổi bật cho từng vai trò để user nhận ra ngay. */
function roleBadge(role) {
  switch (role) {
    case 'admin':
      return { label: 'Admin',      color: '#F5B700', bg: 'rgba(245,183,0,0.18)',  textColor: '#FFD54F', icon: <StarFilled /> }
    case 'moderator':
      return { label: 'Moderator',  color: '#42A5F5', bg: 'rgba(66,165,245,0.20)', textColor: '#90CAF9', icon: <SafetyCertificateOutlined /> }
    case 'instructor':
    case 'teacher':
      return { label: 'Giảng viên', color: '#26A69A', bg: 'rgba(38,166,154,0.20)', textColor: '#80CBC4', icon: <ReadOutlined /> }
    case 'student':
      return { label: 'Sinh viên',  color: '#5BC8F5', bg: 'rgba(91,200,245,0.20)', textColor: '#81D4FA', icon: <ExperimentOutlined /> }
    default:
      return { label: 'User',       color: '#B0BEC5', bg: 'rgba(255,255,255,0.10)', textColor: '#CFD8DC', icon: <UserOutlined /> }
  }
}

export default function AppLayout({ children }) {
  const [collapsed, setCollapsed] = useState(true)
  const { profile, role, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const username = profile?.username ?? '…'
  const fullName = profile?.full_name || profile?.username || '…'
  const initial = (fullName[0] || username[0] || 'U').toUpperCase()
  const bg = avatarColor(username)
  const badge = roleBadge(role)

  const STUDENT_PLUS = ['student', 'instructor', 'moderator', 'admin']
  const TEACH_PLUS = ['instructor', 'moderator', 'admin']
  const ADMIN_PLUS = ['moderator', 'admin']

  const menuItems = [
    { key: '/dashboard',  icon: <DashboardOutlined />,  label: 'Dashboard',         show: true },
    { key: '/labs',       icon: <ExperimentOutlined />, label: 'Labs',              show: STUDENT_PLUS.includes(role) },
    { key: '/my-reports', icon: <HistoryOutlined />,    label: 'Báo cáo của tôi',   show: STUDENT_PLUS.includes(role) },
    { key: '/wiring',     icon: <ApartmentOutlined />,  label: 'Wiring Diagram',    show: true },
    { key: '/teach',      icon: <ReadOutlined />,       label: 'Giảng dạy',         show: TEACH_PLUS.includes(role) },
    { key: '/admin',      icon: <CrownOutlined />,      label: 'Admin Panel',       show: ADMIN_PLUS.includes(role) },
  ].filter(item => item.show).map(({ show, ...rest }) => rest)

  const userMenuItems = [
    { key: 'logout', icon: <LogoutOutlined />, label: 'Sign Out', danger: true },
  ]

  async function handleUserMenu({ key }) {
    if (key === 'logout') { await logActivity('LOGOUT'); await logout(); navigate('/') }
  }

  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f7fa' }}>
      <Sider
        collapsed={collapsed}
        trigger={null}
        style={{
          background: 'linear-gradient(180deg,#001f6b 0%,#003291 40%,#0a4db5 100%)',
          boxShadow: '4px 0 20px rgba(0,0,50,0.2)',
          borderRadius: '0 18px 18px 0',
          overflow: 'hidden',
          margin: 0,
          position: 'sticky',
          top: 0,
          height: '100vh',
          display: 'flex',
          flexDirection: 'column',
        }}
        width={220}
      >
        {/* Logo */}
        <div style={{ padding: collapsed ? '18px 0' : '18px 16px', display: 'flex', alignItems: 'center', gap: 10, justifyContent: collapsed ? 'center' : 'flex-start', borderBottom: '1px solid rgba(255,255,255,0.10)' }}>
          <div
            style={{
              width: 38,
              height: 38,
              borderRadius: 10,
              flexShrink: 0,
              background: 'rgba(255,255,255,0.10)',
              border: '1px solid rgba(255,255,255,0.20)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 4px 14px rgba(0,0,40,0.35)',
            }}
          >
            <img
              src="/icon-on-light.svg"
              alt="BK Diagnostic"
              style={{ width: 26, height: 26 }}
              onError={(e) => { e.currentTarget.src = '/icon.svg' }}
            />
          </div>
          {!collapsed && (
            <div style={{ minWidth: 0 }}>
              <Text strong style={{ color: '#fff', fontSize: 15, whiteSpace: 'nowrap', letterSpacing: 0.3, display: 'block' }}>BK Diagnostic</Text>
              <Text style={{ color: 'rgba(255,255,255,0.55)', fontSize: 10.5, letterSpacing: 0.6 }}>HCMUT · TR4021</Text>
            </div>
          )}
        </div>

        {/* Menu */}
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'transparent', borderRight: 'none', flex: 1, marginTop: 8 }}
        />

        {/* User info card at bottom — nổi bật, dễ thấy role */}
        <div style={{ padding: collapsed ? '10px 8px 14px' : '10px 12px 14px', borderTop: '1px solid rgba(255,255,255,0.10)' }}>
          <div
            style={{
              background: 'linear-gradient(135deg, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.05) 100%)',
              border: '1px solid rgba(255,255,255,0.14)',
              borderRadius: 12,
              padding: collapsed ? '10px 4px' : '12px 12px',
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              justifyContent: collapsed ? 'center' : 'flex-start',
              boxShadow: '0 4px 12px rgba(0,0,30,0.25)',
            }}
          >
            <div style={{ position: 'relative', flexShrink: 0 }}>
              <Avatar
                size={collapsed ? 32 : 38}
                style={{
                  background: bg,
                  fontWeight: 800,
                  fontSize: collapsed ? 14 : 15,
                  border: '2px solid rgba(255,255,255,0.25)',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
                }}
              >
                {initial}
              </Avatar>
              {/* Online dot */}
              <div
                style={{
                  position: 'absolute',
                  right: -2,
                  bottom: -2,
                  width: 11,
                  height: 11,
                  borderRadius: '50%',
                  background: '#4CAF50',
                  border: '2px solid #003291',
                }}
              />
            </div>
            {!collapsed && (
              <div style={{ minWidth: 0, flex: 1 }}>
                <Text
                  strong
                  style={{
                    color: '#fff',
                    fontSize: 13.5,
                    display: 'block',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    letterSpacing: 0.2,
                    lineHeight: 1.25,
                  }}
                >
                  {fullName}
                </Text>
                <Text
                  style={{
                    color: 'rgba(255,255,255,0.55)',
                    fontSize: 10.5,
                    display: 'block',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    marginBottom: 4,
                  }}
                >
                  @{username}
                </Text>
                <Tag
                  bordered={false}
                  style={{
                    margin: 0,
                    background: badge.bg,
                    color: badge.textColor,
                    fontWeight: 700,
                    fontSize: 10.5,
                    padding: '2px 8px',
                    lineHeight: 1.5,
                    borderRadius: 6,
                    border: `1px solid ${badge.color}33`,
                    letterSpacing: 0.4,
                  }}
                  icon={badge.icon}
                >
                  {badge.label}
                </Tag>
              </div>
            )}
          </div>
        </div>
      </Sider>

      <Layout>
        <Header style={{ background: '#fff', padding: '0 20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #f0f0f0', position: 'sticky', top: 0, zIndex: 10 }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenu }} placement="bottomRight">
            <Space size={10} style={{ cursor: 'pointer', padding: '4px 10px 4px 4px', borderRadius: 24, transition: 'background 0.15s' }}>
              <Avatar style={{ background: bg, fontWeight: 700 }}>{initial}</Avatar>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', lineHeight: 1.2 }}>
                <Text style={{ fontWeight: 600, fontSize: 13 }}>{fullName}</Text>
                <Tag
                  bordered={false}
                  style={{
                    margin: 0,
                    marginTop: 2,
                    background: `${badge.color}1A`,
                    color: badge.color,
                    fontWeight: 700,
                    fontSize: 10,
                    padding: '0 6px',
                    lineHeight: '16px',
                    borderRadius: 4,
                  }}
                  icon={badge.icon}
                >
                  {badge.label}
                </Tag>
              </div>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ padding: 24, background: '#f5f7fa' }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  )
}
