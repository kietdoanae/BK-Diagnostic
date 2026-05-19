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
  const SPOTLIGHT = t('landing.mobile.activeTestSpotlight', { returnObjects: true })

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

        {/* ═════════ Active Test SPOTLIGHT card ═════════ */}
        <motion.div
          variants={fadeUpItem}
          whileHover={{ y: -3 }}
          transition={{ type: 'spring', stiffness: 280, damping: 24 }}
          style={{
            position: 'relative',
            marginBottom: 32,
            padding: 2,
            borderRadius: 22,
            background:
              'conic-gradient(from 90deg, rgba(124,58,237,0.65), rgba(21,101,192,0.65), rgba(212,160,23,0.5), rgba(124,58,237,0.65))',
            overflow: 'hidden',
          }}
        >
          {/* Rotating aurora behind */}
          <motion.div
            aria-hidden
            animate={{ rotate: 360 }}
            transition={{ duration: 22, repeat: Infinity, ease: 'linear' }}
            style={{
              position: 'absolute',
              inset: -120,
              background:
                'conic-gradient(from 0deg, rgba(124,58,237,0.35) 0%, transparent 40%, rgba(21,101,192,0.35) 70%, transparent 100%)',
              filter: 'blur(60px)',
              pointerEvents: 'none',
            }}
          />

          <div
            style={{
              position: 'relative',
              background: 'linear-gradient(135deg, #0A0F26 0%, #1B1547 100%)',
              borderRadius: 20,
              padding: '32px 36px',
              color: '#fff',
              overflow: 'hidden',
            }}
          >
            {/* Decorative dotted grid */}
            <div
              aria-hidden
              style={{
                position: 'absolute',
                inset: 0,
                backgroundImage: 'radial-gradient(rgba(255,255,255,0.06) 1px, transparent 1px)',
                backgroundSize: '22px 22px',
                opacity: 0.55,
                pointerEvents: 'none',
              }}
            />

            <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 18 }}>
              {/* Badge */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <motion.span
                  animate={{ opacity: [1, 0.5, 1] }}
                  transition={{ duration: 1.8, repeat: Infinity }}
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    background: '#A78BFA',
                    boxShadow: '0 0 12px #A78BFA',
                  }}
                />
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 800,
                    letterSpacing: 2,
                    color: '#C4B5FD',
                  }}
                >
                  {SPOTLIGHT.badge}
                </span>
              </div>

              {/* Title */}
              <h3
                style={{
                  margin: 0,
                  fontSize: 'clamp(22px, 3vw, 28px)',
                  fontWeight: 800,
                  lineHeight: 1.2,
                  background: 'linear-gradient(135deg, #fff 0%, #DDD6FE 100%)',
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                }}
              >
                {SPOTLIGHT.title}
              </h3>

              {/* Description */}
              <p
                style={{
                  margin: 0,
                  fontSize: 14,
                  lineHeight: 1.65,
                  color: 'rgba(255,255,255,0.78)',
                  maxWidth: 680,
                }}
              >
                {SPOTLIGHT.desc}
              </p>

              {/* Stats row */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
                  gap: 12,
                  marginTop: 6,
                }}
              >
                {SPOTLIGHT.stats.map((s, i) => (
                  <motion.div
                    key={i}
                    variants={fadeUpItem}
                    whileHover={{ scale: 1.04 }}
                    style={{
                      padding: '14px 14px',
                      background: 'rgba(255,255,255,0.04)',
                      border: '1px solid rgba(255,255,255,0.08)',
                      borderRadius: 12,
                      backdropFilter: 'blur(8px)',
                    }}
                  >
                    <div
                      style={{
                        fontFamily: 'var(--font-mono, ui-monospace, Menlo, monospace)',
                        fontSize: 24,
                        fontWeight: 800,
                        color: '#fff',
                        lineHeight: 1,
                        marginBottom: 4,
                      }}
                    >
                      {s.value}
                    </div>
                    <div
                      style={{
                        fontSize: 10.5,
                        color: 'rgba(255,255,255,0.55)',
                        fontWeight: 600,
                        letterSpacing: 0.5,
                        lineHeight: 1.3,
                      }}
                    >
                      {s.label}
                    </div>
                  </motion.div>
                ))}
              </div>

              {/* Highlights bullets */}
              <ul
                style={{
                  margin: '8px 0 0',
                  padding: 0,
                  listStyle: 'none',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                }}
              >
                {SPOTLIGHT.highlights.map((h, i) => (
                  <li
                    key={i}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 10,
                      fontSize: 13,
                      color: 'rgba(255,255,255,0.85)',
                      lineHeight: 1.6,
                    }}
                  >
                    <span style={{ color: '#A78BFA', fontWeight: 700, flexShrink: 0 }}>›</span>
                    <span>{h}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </motion.div>

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
                <motion.div key={i} variants={fadeUpItem} whileHover={{ x: 4 }} style={{ display: 'flex', gap: 12, padding: '4px 0', borderRadius: 8, transition: 'background 0.2s ease' }}>
                  <motion.span
                    style={{ fontSize: 22, flexShrink: 0, lineHeight: 1 }}
                    whileHover={{ scale: 1.2, rotate: 10 }}
                  >{f.icon}</motion.span>
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
            <PlaceholderImage path="app/screen-data-logger.png"   alt="Data Logger" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-actuator-test.png" alt="Actuator Test" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-lab-session.png"   alt="Lab Session" ratio="9/19.5"
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
              <motion.div key={i} variants={fadeUpItem} whileHover={{ scale: 1.03, y: -2 }} transition={{ duration: 0.2 }} style={{
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