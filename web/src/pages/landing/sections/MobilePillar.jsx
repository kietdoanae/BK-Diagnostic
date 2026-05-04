import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

export default function MobilePillar() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

  const FEATURES = t('landing.mobile.features', { returnObjects: true })
  const LAB_STEPS = t('landing.mobile.labSteps', { returnObjects: true })

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="mobile"
      className="landing-section"
      style={{ background: 'var(--paper-soft)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.mobile.eyebrow')}
          title={t('landing.mobile.title')}
          sub={t('landing.mobile.sub')}
          align="left"
        />

        {/* Hàng trên: Tính năng + 3 screenshot */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '7fr 5fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 48,
        }} className="responsive-grid">
          <div style={{
            background: 'var(--paper)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>{t('landing.mobile.featuresTitle')}</h3>
            <motion.div
              variants={fadeUpStagger}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.3 }}
              style={{ display: 'flex', flexDirection: 'column', gap: 16 }}
            >
              {FEATURES.map((f, i) => (
                <motion.div key={i} variants={fadeUpItem} style={{ display: 'flex', gap: 12 }}>
                  <span style={{ fontSize: 22, flexShrink: 0, lineHeight: 1 }}>{f.icon}</span>
                  <div>
                    <strong style={{ color: 'var(--ink-900)', fontSize: 14, display: 'block', marginBottom: 2 }}>
                      {f.title}
                    </strong>
                    <span style={{ color: 'var(--ink-500)', fontSize: 13, lineHeight: 1.6 }}>
                      {f.desc}
                    </span>
                  </div>
                </motion.div>
              ))}
            </motion.div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <PlaceholderImage path="app/screen-live-data.png"  alt="Live Dashboard" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-dtc-list.png"   alt="DTC List" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-lab-session.png" alt="Lab Session" ratio="9/19.5"
              caption={t('landing.mobile.screensCaption')} />
          </div>
        </div>

        {/* Lab Mode sub-section */}
        <div style={{
          background: 'var(--bk-blue-100)',
          borderRadius: 'var(--radius-card)',
          padding: 32,
          border: '1px solid var(--rule)',
        }}>
          <h3 style={{
            margin: '0 0 8px',
            fontSize: 20,
            color: 'var(--bk-navy-700)',
          }}>{t('landing.mobile.labTitle')}</h3>
          <p style={{ fontSize: 14, color: 'var(--ink-700)', lineHeight: 1.7, marginBottom: 24, maxWidth: 720 }}>
            {t('landing.mobile.labDesc')}
          </p>

          <motion.div
            variants={fadeUpStagger}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, amount: 0.3 }}
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
              gap: 12,
            }}
          >
            {LAB_STEPS.map((s, i) => (
              <motion.div key={i} variants={fadeUpItem} style={{
                background: 'var(--paper)',
                borderRadius: 999,
                padding: '14px 18px',
                display: 'flex',
                alignItems: 'center',
                gap: 12,
              }}>
                <span style={{
                  background: 'var(--bk-navy-700)',
                  color: '#fff',
                  width: 28, height: 28, borderRadius: '50%',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontWeight: 700, fontSize: 13, flexShrink: 0,
                }}>{s.num}</span>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-900)' }}>{s.label}</div>
                  <div style={{ fontSize: 11, color: 'var(--ink-500)' }}>{s.sub}</div>
                </div>
              </motion.div>
            ))}
          </motion.div>
        </div>
      </div>
    </motion.section>
  )
}