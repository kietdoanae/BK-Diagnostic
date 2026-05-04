import { Avatar, Tag, Typography } from 'antd'
import {
  StarFilled,
  SafetyCertificateOutlined,
  ReadOutlined,
  ExperimentOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'

const { Text } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']

export function avatarColor(username = '') {
  let h = 0
  for (const c of username) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

/** Role badge styling — labels dùng i18n key, gọi trong component bằng t(badge.labelKey). */
export function roleBadge(role) {
  switch (role) {
    case 'admin':
      return { labelKey: 'role.admin',      color: '#F5B700', icon: <StarFilled /> }
    case 'moderator':
      return { labelKey: 'role.moderator',  color: '#42A5F5', icon: <SafetyCertificateOutlined /> }
    case 'instructor':
    case 'teacher':
      return { labelKey: 'role.instructor', color: '#26A69A', icon: <ReadOutlined /> }
    case 'student':
      return { labelKey: 'role.student',    color: '#0288D1', icon: <ExperimentOutlined /> }
    default:
      return { labelKey: 'role.user',       color: '#78909C', icon: <UserOutlined /> }
  }
}

/**
 * Badge user dùng chung — hiển thị avatar + tên + role tag.
 *
 *   <UserBadge profile={profile} role={role} onClick={...} compact={false} dark={false} />
 *
 * @param compact — chỉ hiện avatar (cho mobile / sidebar collapsed)
 * @param dark    — chế độ tối (text trắng) cho overlay trên gradient
 * @param size    — đường kính avatar (mặc định 36)
 */
export default function UserBadge({
  profile,
  role = 'user',
  onClick,
  compact = false,
  dark = false,
  size = 36,
  showOnlineDot = false,
}) {
  const { t } = useTranslation()
  const username = profile?.username ?? '…'
  const fullName = profile?.full_name || profile?.username || '…'
  const initial = (fullName[0] || username[0] || 'U').toUpperCase()
  const bg = avatarColor(username)
  const badge = roleBadge(role)

  const nameColor = dark ? '#fff' : '#1A1A2E'
  const subColor  = dark ? 'rgba(255,255,255,0.55)' : '#6B7280'

  return (
    <div
      onClick={onClick}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        cursor: onClick ? 'pointer' : 'default',
        padding: '4px 8px 4px 4px',
        borderRadius: 24,
      }}
    >
      <div style={{ position: 'relative', flexShrink: 0 }}>
        <Avatar
          size={size}
          style={{
            background: bg,
            fontWeight: 800,
            fontSize: size <= 32 ? 13 : 15,
            border: dark ? '2px solid rgba(255,255,255,0.25)' : '2px solid rgba(255,255,255,0.85)',
            boxShadow: '0 2px 6px rgba(0,0,0,0.18)',
          }}
        >
          {initial}
        </Avatar>
        {showOnlineDot && (
          <div
            style={{
              position: 'absolute',
              right: -2,
              bottom: -2,
              width: 11,
              height: 11,
              borderRadius: '50%',
              background: '#4CAF50',
              border: dark ? '2px solid #003291' : '2px solid #fff',
            }}
          />
        )}
      </div>
      {!compact && (
        <div className="bk-user-badge-text" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', minWidth: 0, lineHeight: 1.2 }}>
          <Text
            strong
            style={{
              color: nameColor,
              fontSize: 13.5,
              maxWidth: 160,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {fullName}
          </Text>
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
            {t(badge.labelKey)}
          </Tag>
        </div>
      )}
      <style>{`
        @media (max-width: 600px) {
          .bk-user-badge-text { display: none !important; }
        }
      `}</style>
    </div>
  )
}
