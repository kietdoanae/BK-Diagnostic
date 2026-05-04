import { useState } from 'react'
import { motion, useScroll } from 'framer-motion'
import { Button, Drawer } from 'antd'
import { MenuOutlined, DashboardOutlined, LoginOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../../hooks/useAuth'
import UserBadge from '../../../components/UserBadge'
import LanguageSwitcher from '../../../components/LanguageSwitcher'
import logoSvg from '../../../assets/svg/bk-diagnostic-logo.svg'

export default function Navbar() {
  const { session, profile, role } = useAuth()
  const navigate = useNavigate()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const { scrollYProgress } = useScroll()
  const { t } = useTranslation()

  const NAV_ITEMS = [
    { href: '#tong-quan',  label: t('landing.nav.overview') },
    { href: '#kien-truc',  label: t('landing.nav.architecture') },
    { href: '#mobile',     label: t('landing.nav.mobile') },
    { href: '#web',        label: t('landing.nav.web') },
    { href: '#lab',        label: t('landing.nav.lab') },
    { href: '#team',       label: t('landing.nav.team') },
  ]

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

        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <LanguageSwitcher compact />
          {session ? (
            <>
              <UserBadge
                profile={profile}
                role={role}
                onClick={() => navigate('/dashboard')}
                size={36}
              />
              <Button
                type="primary"
                icon={<DashboardOutlined />}
                onClick={() => navigate('/dashboard')}
                style={{ height: 40, fontWeight: 600, borderRadius: 10 }}
              >
                {t('nav.dashboard')}
              </Button>
            </>
          ) : (
            <Button
              type="primary"
              icon={<LoginOutlined />}
              onClick={() => navigate('/login')}
              style={{ height: 40, fontWeight: 600, borderRadius: 10 }}
            >
              {t('auth.btnSignIn')}
            </Button>
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