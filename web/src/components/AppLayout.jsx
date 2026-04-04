import { useState } from 'react'
import { Layout, Menu, Avatar, Dropdown, Button, Space, Typography } from 'antd'
import {
  DashboardOutlined,
  CrownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

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

  const menuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
    ...(role === 'admin' || role === 'moderator'
      ? [{ key: '/admin', icon: <CrownOutlined />, label: 'Admin Panel' }]
      : []),
  ]

  const userMenuItems = [
    { key: 'logout', icon: <LogoutOutlined />, label: 'Sign Out', danger: true },
  ]

  async function handleUserMenu({ key }) {
    if (key === 'logout') { await logout(); navigate('/') }
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsed={collapsed}
        trigger={null}
        style={{ background: '#003291' }}
        width={200}
      >
        <div style={{ padding: collapsed ? '16px 8px' : '16px', display: 'flex', alignItems: 'center', gap: 8, borderBottom: '1px solid rgba(255,255,255,0.1)', marginBottom: 8 }}>
          <img src="https://i.ibb.co/Z0Xc41Z/logo.png" alt="logo" style={{ width: 32, height: 32, borderRadius: 8, flexShrink: 0 }} />
          {!collapsed && <Text strong style={{ color: '#fff', fontSize: 14, whiteSpace: 'nowrap' }}>BK Diagnostic</Text>}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: '#003291', borderRight: 'none' }}
        />
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
