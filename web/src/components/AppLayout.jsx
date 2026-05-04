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
  HomeOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../hooks/useAuth'
import { logActivity } from '../services/api'
import UserBadge from './UserBadge'
import LanguageSwitcher from './LanguageSwitcher'

const { Sider, Header, Content } = Layout
const { Text } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']

function avatarColor(username = '') {
  let h = 0
  for (const c of username) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

/** Role badge styling — labels dùng i18n key, gọi trong component bằng t(badge.labelKey). */
function roleBadge(role) {
  switch (role) {
    case 'admin':
      return { labelKey: 'role.admin',      color: '#F5B700', bg: 'rgba(245,183,0,0.18)',  textColor: '#FFD54F', icon: <StarFilled /> }
    case 'moderator':
      return { labelKey: 'role.moderator',  color: '#42A5F5', bg: 'rgba(66,165,245,0.20)', textColor: '#90CAF9', icon: <SafetyCertificateOutlined /> }
    case 'instructor':
    case 'teacher':
      return { labelKey: 'role.instructor', color: '#26A69A', bg: 'rgba(38,166,154,0.20)', textColor: '#80CBC4', icon: <ReadOutlined /> }
    case 'student':
      return { labelKey: 'role.student',    color: '#5BC8F5', bg: 'rgba(91,200,245,0.20)', textColor: '#81D4FA', icon: <ExperimentOutlined /> }
    default:
      return { labelKey: 'role.user',       color: '#B0BEC5', bg: 'rgba(255,255,255,0.10)', textColor: '#CFD8DC', icon: <UserOutlined /> }
  }
}

export default function AppLayout({ children }) {
  const [collapsed, setCollapsed] = useState(true)
  const { profile, role, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation()

  const username = profile?.username ?? '…'
  const fullName = profile?.full_name || profile?.username || '…'
  const initial = (fullName[0] || username[0] || 'U').toUpperCase()
  const bg = avatarColor(username)
  const badge = roleBadge(role)

  const STUDENT_PLUS = ['student', 'instructor', 'moderator', 'admin']
  const TEACH_PLUS = ['instructor', 'moderator', 'admin']
  const ADMIN_PLUS = ['moderator', 'admin']

  // Helper: chỉ tạo group nếu có item visible bên trong (collapsed mode chỉ hiện divider).
  function makeGroup(label, items) {
    const visibleItems = items.filter((it) => it.show).map(({ show, ...rest }) => rest)
    if (visibleItems.length === 0) return null
    if (collapsed) {
      // Khi sidebar thu gọn: chỉ render items + 1 divider phân tách, không hiện label
      return [{ type: 'divider', style: { margin: '6px 12px', borderColor: 'rgba(255,255,255,0.10)' } }, ...visibleItems]
    }
    return [{ type: 'group', label, children: visibleItems }]
  }

  const menuItems = [
    ...(makeGroup(t('nav.section.overview'), [
      { key: '/dashboard', icon: <DashboardOutlined />, label: t('nav.dashboard'), show: true },
    ]) ?? []),
    ...(makeGroup(t('nav.section.practice'), [
      { key: '/labs',       icon: <ExperimentOutlined />, label: t('nav.labs'),          show: STUDENT_PLUS.includes(role) },
      { key: '/my-reports', icon: <HistoryOutlined />,    label: t('nav.myReports'),     show: STUDENT_PLUS.includes(role) },
      { key: '/wiring',     icon: <ApartmentOutlined />,  label: t('nav.wiringDiagram'), show: true },
    ]) ?? []),
    ...(makeGroup(t('nav.section.admin'), [
      { key: '/teach', icon: <ReadOutlined />,  label: t('nav.teach'),       show: TEACH_PLUS.includes(role) },
      { key: '/admin', icon: <CrownOutlined />, label: t('nav.adminPanel'),  show: ADMIN_PLUS.includes(role) },
    ]) ?? []),
  ]

  const userMenuItems = [
    { key: 'home',   icon: <HomeOutlined />,   label: t('nav.home') },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: t('nav.logout'), danger: true },
  ]

  async function handleUserMenu({ key }) {
    if (key === 'home')   { navigate('/'); return }
    if (key === 'logout') { await logActivity('LOGOUT'); await logout(); navigate('/') }
  }

  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f7fa' }}>
      <Sider
        collapsed={collapsed}
        trigger={null}
        breakpoint="lg"
        collapsedWidth={64}
        onBreakpoint={(broken) => setCollapsed(broken)}
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
        {/* Logo — click để về trang chủ */}
        <div
          onClick={() => navigate('/')}
          title="Về trang chủ"
          style={{
            padding: collapsed ? '18px 0' : '18px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            justifyContent: collapsed ? 'center' : 'flex-start',
            borderBottom: '1px solid rgba(255,255,255,0.10)',
            cursor: 'pointer',
            transition: 'background 0.15s',
          }}
          onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.04)' }}
          onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
        >
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
              <Text strong style={{ color: '#fff', fontSize: 15, whiteSpace: 'nowrap', letterSpacing: 0.3, display: 'block' }}>{t('app.name')}</Text>
              <Text style={{ color: 'rgba(255,255,255,0.55)', fontSize: 10.5, letterSpacing: 0.6 }}>{t('app.tagline')}</Text>
            </div>
          )}
        </div>

        {/* Menu — group sections + compact item style */}
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => key && key.startsWith('/') && navigate(key)}
          className="bk-sidebar-menu"
          style={{ background: 'transparent', borderRight: 'none', flex: 1, marginTop: 4, overflowY: 'auto' }}
        />
        <style>{`
          /* Group section label: nhỏ, uppercase, màu mờ */
          .bk-sidebar-menu .ant-menu-item-group-title {
            color: rgba(255,255,255,0.40) !important;
            font-size: 10px !important;
            font-weight: 700 !important;
            letter-spacing: 1.2px !important;
            padding: 14px 24px 4px !important;
            text-transform: uppercase;
          }
          /* Item: bớt padding, nhỏ hơn */
          .bk-sidebar-menu .ant-menu-item {
            height: 38px !important;
            line-height: 38px !important;
            margin: 2px 8px !important;
            padding-left: 16px !important;
            border-radius: 8px !important;
            width: auto !important;
          }
          .bk-sidebar-menu .ant-menu-item .ant-menu-title-content {
            font-size: 13.5px;
            font-weight: 500;
          }
          .bk-sidebar-menu .ant-menu-item-selected {
            background: linear-gradient(90deg, #1565C0 0%, #1E88E5 100%) !important;
            box-shadow: 0 2px 8px rgba(21,101,192,0.4);
          }
        `}</style>

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
                  {t(badge.labelKey)}
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
          <Space size={12}>
            <LanguageSwitcher compact />
            <Button
              type="primary"
              icon={<HomeOutlined />}
              onClick={() => navigate('/')}
              style={{ height: 40, fontWeight: 600, borderRadius: 10 }}
              className="bk-home-btn"
            >
              <span className="bk-home-label">{t('nav.home')}</span>
            </Button>
            <style>{`
              @media (max-width: 600px) {
                .bk-home-label { display: none; }
                .bk-home-btn { padding: 0 12px !important; }
              }
            `}</style>
            <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenu }} placement="bottomRight">
              <div>
                <UserBadge profile={profile} role={role} size={36} onClick={() => {}} />
              </div>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ padding: 24, background: '#f5f7fa' }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  )
}
