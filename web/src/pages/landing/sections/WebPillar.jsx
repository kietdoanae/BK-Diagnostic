import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const TAB_IMG_MAP = {
  labs: 'web/dashboard-labs.png',
  groups: 'web/dashboard-groups.png',
  sessions: 'web/dashboard-sessions.png',
  exports: 'web/dashboard-exports.png',
  logs: 'web/dashboard-logs.png',
}

export default function WebPillar() {
  const [activeTab, setActiveTab] = useState('sessions')
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

  const FOR_TEACHER = t('landing.webPillar.forTeacher', { returnObjects: true })
  const FOR_ADMIN = t('landing.webPillar.forAdmin', { returnObjects: true })
  const TABS = t('landing.webPillar.tabs', { returnObjects: true })

  const tab = TABS.find(t => t.key === activeTab) ?? TABS[2]

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="web"
      className="landing-section"
      style={{ background: 'var(--paper)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.webPillar.eyebrow')}
          title={t('landing.webPillar.title')}
          sub={t('landing.webPillar.sub')}
          align="left"
        />

        {/* Hàng trên: Screenshot + 2 group bullet */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '5fr 7fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 48,
        }} className="responsive-grid">
          <PlaceholderImage
            path="web/dashboard-sessions.png"
            alt={t('landing.webPillar.imgAlt')}
            ratio="16/10"
            caption={t('landing.webPillar.imgCaption')}
          />

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{
              margin: '0 0 12px',
              fontSize: 13,
              color: 'var(--bk-blue-500)',
              letterSpacing: 2,
              fontWeight: 800,
            }}>{t('landing.webPillar.forTeacherLabel')}</h3>
            <motion.ul
              variants={fadeUpStagger}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.3 }}
              style={{ margin: '0 0 24px', padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}
            >
              {FOR_TEACHER.map((item, i) => (
                <motion.li key={i} variants={fadeUpItem} whileHover={{ x: 4 }} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{item}</span>
                </motion.li>
              ))}
            </motion.ul>

            <div style={{ height: 1, background: 'var(--rule)', margin: '12px 0 24px' }} />

            <h3 style={{
              margin: '0 0 12px',
              fontSize: 13,
              color: 'var(--bk-blue-500)',
              letterSpacing: 2,
              fontWeight: 800,
            }}>{t('landing.webPillar.forAdminLabel')}</h3>
            <motion.ul
              variants={fadeUpStagger}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.3 }}
              style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}
            >
              {FOR_ADMIN.map((item, i) => (
                <motion.li key={i} variants={fadeUpItem} whileHover={{ x: 4 }} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{item}</span>
                </motion.li>
              ))}
            </motion.ul>
          </div>
        </div>

        {/* Tab showcase */}
        <div style={{
          background: 'var(--paper-soft)',
          borderRadius: 'var(--radius-card)',
          padding: 24,
          border: '1px solid var(--rule)',
        }}>
          <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--rule)', marginBottom: 24, overflowX: 'auto' }}>
            {TABS.map(t => (
              <button
                key={t.key}
                onClick={() => setActiveTab(t.key)}
                style={{
                  padding: '12px 20px',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: 14,
                  fontWeight: activeTab === t.key ? 700 : 500,
                  color: activeTab === t.key ? 'var(--bk-navy-700)' : 'var(--ink-500)',
                  borderBottom: activeTab === t.key ? '2px solid var(--bk-navy-700)' : '2px solid transparent',
                  transition: 'all 200ms ease-out',
                  whiteSpace: 'nowrap',
                }}
              >{t.label}</button>
            ))}
          </div>

          <AnimatePresence mode="wait">
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
            >
              <PlaceholderImage path={TAB_IMG_MAP[activeTab] || TAB_IMG_MAP.sessions} alt={`Tab ${tab.label}`} ratio="16/9" />
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </motion.section>
  )
}