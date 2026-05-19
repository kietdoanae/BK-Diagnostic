import { useState, useEffect } from 'react'
import { motion, useScroll, useMotionValueEvent, AnimatePresence } from 'framer-motion'
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
  const [scrolled, setScrolled] = useState(false)
  const [activeSection, setActiveSection] = useState('#tong-quan')
  const { scrollY, scrollYProgress } = useScroll()
  const { t } = useTranslation()

  const NAV_ITEMS = [
    { href: '#tong-quan',  label: t('landing.nav.overview') },
    { href: '#kien-truc',  label: t('landing.nav.architecture') },
    { href: '#mobile',     label: t('landing.nav.mobile') },
    { href: '#vehicles',   label: t('landing.nav.vehicles') },
    { href: '#web',        label: t('landing.nav.web') },
    { href: '#lab',        label: t('landing.nav.lab') },
    { href: '#team',       label: t('landing.nav.team') },
  ]

  // Track scroll for glass morphism effect
  useMotionValueEvent(scrollY, 'change', (latest) => {
    setScrolled(latest > 50)
  })

  // Track active section on scroll
  useEffect(() => {
    const handleScroll = () => {
      const sections = NAV_ITEMS.map(item => item.href.replace('#', ''))
      for (let i = sections.length - 1; i >= 0; i--) {
        const el = document.getElementById(sections[i])
        if (el) {
          const rect = el.getBoundingClientRect()
          if (rect.top <= 100) {
            setActiveSection(`#${sections[i]}`)
            return
          }
        }
      }
      setActiveSection('#tong-quan')
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  return (
    <>
      <motion.nav
        initial={{ y: -80 }}
        animate={{ y: 0 }}
        transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
        style={{
          position: 'fixed', top: 0, left: 0, right: 0, zIndex: 50,
          background: scrolled ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.92)',
          backdropFilter: scrolled ? 'blur(20px) saturate(180%)' : 'blur(8px)',
          WebkitBackdropFilter: scrolled ? 'blur(20px) saturate(180%)' : 'blur(8px)',
          borderBottom: scrolled ? '1px solid rgba(229,231,235,0.8)' : '1px solid var(--rule)',
          boxShadow: scrolled ? '0 4px 30px rgba(0,0,0,0.06)' : 'none',
          height: scrolled ? 56 : 64,
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '0 24px',
          transition: 'height 0.3s ease, background 0.3s ease, box-shadow 0.3s ease',
        }}
      >
        {/* Logo */}
        <motion.div
          style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}
          whileHover={{ scale: 1.02 }}
          onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
        >
          <motion.img
            src={logoSvg}
            alt="BK Diagnostic"
            style={{ width: scrolled ? 30 : 36, height: scrolled ? 30 : 36 }}
            animate={{ rotate: [0, 0, 0] }}
            transition={{ duration: 0.3 }}
          />
          <motion.span
            style={{
              fontWeight: 700,
              color: 'var(--bk-navy-700)',
              fontSize: scrolled ? 15 : 17,
              transition: 'font-size 0.3s ease',
            }}
          >
            BK Diagnostic
          </motion.span>
        </motion.div>

        {/* Desktop Nav */}
        <div className="nav-desktop" style={{ display: 'none', gap: 4, alignItems: 'center' }}>
          {NAV_ITEMS.map(it => {
            const isActive = activeSection === it.href
            return (
              <motion.a
                key={it.href}
                href={it.href}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                style={{
                  color: isActive ? 'var(--bk-navy-700)' : 'var(--ink-700)',
                  fontSize: 13,
                  fontWeight: isActive ? 700 : 500,
                  textDecoration: 'none',
                  padding: '6px 14px',
                  borderRadius: 8,
                  background: isActive ? 'var(--bk-blue-100)' : 'transparent',
                  position: 'relative',
                  transition: 'all 0.2s ease',
                }}
              >
                {it.label}
                {isActive && (
                  <motion.div
                    layoutId="nav-indicator"
                    style={{
                      position: 'absolute',
                      bottom: -2,
                      left: '20%',
                      right: '20%',
                      height: 2,
                      background: 'var(--bk-blue-500)',
                      borderRadius: 1,
                    }}
                    transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                  />
                )}
              </motion.a>
            )
          })}
        </div>

        {/* Right actions */}
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
              <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                <Button
                  type="primary"
                  icon={<DashboardOutlined />}
                  onClick={() => navigate('/dashboard')}
                  style={{
                    height: 40,
                    fontWeight: 600,
                    borderRadius: 10,
                    background: 'linear-gradient(135deg, var(--bk-navy-700), var(--bk-blue-500))',
                    border: 'none',
                    boxShadow: '0 2px 8px rgba(0, 50, 145, 0.2)',
                  }}
                >
                  {t('nav.dashboard')}
                </Button>
              </motion.div>
            </>
          ) : (
            <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
              <Button
                type="primary"
                icon={<LoginOutlined />}
                onClick={() => navigate('/login')}
                style={{
                  height: 40,
                  fontWeight: 600,
                  borderRadius: 10,
                  background: 'linear-gradient(135deg, var(--bk-navy-700), var(--bk-blue-500))',
                  border: 'none',
                  boxShadow: '0 2px 8px rgba(0, 50, 145, 0.2)',
                }}
              >
                {t('auth.btnSignIn')}
              </Button>
            </motion.div>
          )}
          <Button
            className="nav-mobile-btn"
            icon={<MenuOutlined />}
            onClick={() => setDrawerOpen(true)}
            style={{ display: 'inline-flex' }}
          />
        </div>
      </motion.nav>

      {/* Mobile Drawer */}
      <Drawer
        placement="right"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={280}
      >
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.3 }}
          style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
        >
          {NAV_ITEMS.map((it, i) => {
            const isActive = activeSection === it.href
            return (
              <motion.a
                key={it.href}
                href={it.href}
                onClick={() => setDrawerOpen(false)}
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.05 }}
                style={{
                  color: isActive ? 'var(--bk-navy-700)' : 'var(--ink-700)',
                  fontSize: 16,
                  fontWeight: isActive ? 700 : 500,
                  textDecoration: 'none',
                  padding: '12px 16px',
                  borderRadius: 10,
                  background: isActive ? 'var(--bk-blue-100)' : 'transparent',
                  borderLeft: isActive ? '3px solid var(--bk-blue-500)' : '3px solid transparent',
                  transition: 'all 0.2s ease',
                }}
              >
                {it.label}
              </motion.a>
            )
          })}
        </motion.div>
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