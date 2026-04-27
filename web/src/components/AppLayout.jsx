import { useState } from 'react'
import { Layout, Menu, Avatar, Dropdown, Button, Space, Typography } from 'antd'
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

export default function AppLayout({ children }) {
  const [collapsed, setCollapsed] = useState(true)
  const { profile, role, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const username = profile?.username ?? '…'
  const initial = username[0]?.toUpperCase() ?? 'U'
  const bg = avatarColor(username)

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
        <div style={{ padding: collapsed ? '20px 0' : '20px 16px', display: 'flex', alignItems: 'center', gap: 10, justifyContent: collapsed ? 'center' : 'flex-start', borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          <img src="https://i.ibb.co/Z0Xc41Z/logo.png" alt="logo" style={{ width: 34, height: 34, borderRadius: 9, flexShrink: 0, boxShadow: '0 2px 8px rgba(0,0,0,0.3)' }} />
          {!collapsed && <Text strong style={{ color: '#fff', fontSize: 15, whiteSpace: 'nowrap', letterSpacing: 0.3 }}>BK Diagnostic</Text>}
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

        {/* User info at bottom */}
        <div style={{ borderTop: '1px solid rgba(255,255,255,0.08)', padding: collapsed ? '12px 0' : '12px 16px', display: 'flex', alignItems: 'center', gap: 10, justifyContent: collapsed ? 'center' : 'flex-start' }}>
          <Avatar size={32} style={{ background: bg, fontWeight: 700, fontSize: 14, flexShrink: 0 }}>{initial}</Avatar>
          {!collapsed && (
            <div style={{ minWidth: 0 }}>
              <Text strong style={{ color: '#fff', fontSize: 13, display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{username}</Text>
              <Text style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11 }}>{role}</Text>
            </div>
          )}
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
            <Space style={{ cursor: 'pointer' }}>
              <Avatar style={{ background: bg, fontWeight: 700 }}>{initial}</Avatar>
              <Text style={{ fontWeight: 600 }}>{username}</Text>
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
