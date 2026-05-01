import { useState } from 'react'
import { motion, useScroll } from 'framer-motion'
import { Button, Avatar, Drawer } from 'antd'
import { MenuOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../../hooks/useAuth'
import logoSvg from '../../../assets/svg/bk-diagnostic-logo.svg'

const NAV_ITEMS = [
  { href: '#tong-quan',  label: 'Tổng quan' },
  { href: '#kien-truc',  label: 'Kiến trúc' },
  { href: '#mobile',     label: 'Mobile App' },
  { href: '#web',        label: 'Web Platform' },
  { href: '#lab',        label: 'Lab' },
  { href: '#team',       label: 'Team' },
]

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100']
function avatarColor(u = '') {
  let h = 0; for (const c of u) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

export default function Navbar() {
  const { session, profile } = useAuth()
  const navigate = useNavigate()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const { scrollYProgress } = useScroll()

  const username = profile?.username ?? session?.user?.email?.split('@')[0] ?? 'User'
  const initial = username[0]?.toUpperCase() ?? 'U'

  return (
    <>
      <nav style={{
        position: 'fixed', top: 0, left: 0, right: 0, zIndex: 50,
        background: 'rgba(255,255,255,0.92)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        borderBottom: '1px solid var(--rule)',
        height: 64,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 24px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <img src={logoSvg} alt="BK Diagnostic" style={{ width: 36, height: 36 }} />
          <span style={{ fontWeight: 700, color: 'var(--bk-navy-700)', fontSize: 17 }}>
            BK Diagnostic
          </span>
        </div>

        <div className="nav-desktop" style={{ display: 'none', gap: 28 }}>
          {NAV_ITEMS.map(it => (
            <a key={it.href} href={it.href} style={{
              color: 'var(--ink-700)', fontSize: 14, fontWeight: 500, textDecoration: 'none',
            }}>{it.label}</a>
          ))}
        </div>

        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {session ? (
            <>
              <Avatar
                size={32}
                style={{ background: avatarColor(username), fontWeight: 700, cursor: 'pointer' }}
                onClick={() => navigate('/dashboard')}
              >{initial}</Avatar>
              <Button type="primary" onClick={() => navigate('/dashboard')}>Bảng điều khiển</Button>
            </>
          ) : (
            <Button type="primary" onClick={() => navigate('/login')}>Đăng nhập</Button>
          )}
          <Button
            className="nav-mobile-btn"
            icon={<MenuOutlined />}
            onClick={() => setDrawerOpen(true)}
            style={{ display: 'inline-flex' }}
          />
        </div>

        <motion.div
          style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: 2,
            background: 'var(--bk-blue-500)',
            transformOrigin: '0 0',
            scaleX: scrollYProgress,
          }}
        />
      </nav>

      <Drawer
        placement="right"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={260}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {NAV_ITEMS.map(it => (
            <a key={it.href} href={it.href} onClick={() => setDrawerOpen(false)} style={{
              color: 'var(--ink-700)', fontSize: 16, fontWeight: 500, textDecoration: 'none',
              padding: '8px 0', borderBottom: '1px solid var(--rule)',
            }}>{it.label}</a>
          ))}
        </div>
      </Drawer>

      <style>{`
        @media (min-width: 768px) {
          .nav-desktop { display: flex !important; }
          .nav-mobile-btn { display: none !important; }
        }
      `}</style>
    </>
  )
}
