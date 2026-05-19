import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

/**
 * Showcase the vehicle support story: one shipped model (Ford Ranger 2019)
 * rendered as a hero card with a subtle aurora glow, plus a horizontally
 * marquee-ing strip of "coming soon" brands underneath.
 */
export default function VehicleSupport() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()
  const ready = t('landing.vehicles.ready', { returnObjects: true })
  const roadmap = t('landing.vehicles.roadmap', { returnObjects: true })
  // Duplicate for seamless loop
  const roadmapRun = [...roadmap, ...roadmap]

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="vehicles"
      className="landing-section"
      style={{
        background:
          'radial-gradient(1200px 600px at 50% 0%, rgba(124, 58, 237, 0.10) 0%, transparent 60%), var(--paper)',
        position: 'relative',
      }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.vehicles.eyebrow')}
          title={t('landing.vehicles.title')}
          sub={t('landing.vehicles.sub')}
          align="center"
        />

        {/* ───────── Ford Ranger spotlight card ───────── */}
        <motion.div
          variants={fadeUpItem}
          whileHover={{ y: -4 }}
          transition={{ type: 'spring', stiffness: 280, damping: 24 }}
          style={{
            position: 'relative',
            margin: '36px auto 0',
            maxWidth: 880,
            padding: 2,
            borderRadius: 22,
            background:
              'conic-gradient(from 0deg at 50% 50%, rgba(21,101,192,0.6), rgba(124,58,237,0.6), rgba(212,160,23,0.6), rgba(21,101,192,0.6))',
            overflow: 'hidden',
          }}
        >
          {/* Animated rotating glow ring */}
          <motion.div
            aria-hidden
            animate={{ rotate: 360 }}
            transition={{ duration: 16, repeat: Infinity, ease: 'linear' }}
            style={{
              position: 'absolute',
              inset: -80,
              background:
                'conic-gradient(from 0deg, rgba(21,101,192,0.35) 0%, transparent 30%, rgba(124,58,237,0.35) 60%, transparent 90%)',
              filter: 'blur(40px)',
              pointerEvents: 'none',
            }}
          />

          <div
            style={{
              position: 'relative',
              background: 'linear-gradient(135deg, #0B1430 0%, #1A1F4F 100%)',
              borderRadius: 20,
              padding: '36px 40px',
              color: '#fff',
              overflow: 'hidden',
            }}
          >
            {/* Grid overlay */}
            <div
              aria-hidden
              style={{
                position: 'absolute',
                inset: 0,
                backgroundImage:
                  'linear-gradient(rgba(255,255,255,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.04) 1px, transparent 1px)',
                backgroundSize: '32px 32px',
                opacity: 0.7,
                pointerEvents: 'none',
              }}
            />

            <div
              style={{
                position: 'relative',
                display: 'grid',
                gridTemplateColumns: 'auto 1fr',
                gap: 28,
                alignItems: 'center',
              }}
              className="vehicle-grid"
            >
              {/* Status badge */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '6px 12px',
                    borderRadius: 999,
                    background: 'rgba(102, 187, 106, 0.18)',
                    border: '1px solid rgba(102, 187, 106, 0.45)',
                    width: 'fit-content',
                  }}
                >
                  <motion.span
                    animate={{ scale: [1, 1.35, 1], opacity: [1, 0.55, 1] }}
                    transition={{ duration: 1.8, repeat: Infinity }}
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      background: '#66BB6A',
                      boxShadow: '0 0 12px #66BB6A',
                    }}
                  />
                  <span
                    style={{
                      color: '#A5D6A7',
                      fontSize: 10,
                      fontWeight: 800,
                      letterSpacing: 1.4,
                    }}
                  >
                    {ready.label}
                  </span>
                </div>
                <div
                  style={{
                    fontSize: 11,
                    color: 'rgba(255,255,255,0.55)',
                    letterSpacing: 2,
                    fontWeight: 700,
                  }}
                >
                  {ready.year}
                </div>
              </div>

              {/* Brand + model */}
              <div>
                <div
                  style={{
                    fontSize: 14,
                    color: 'rgba(255,255,255,0.6)',
                    fontWeight: 600,
                    letterSpacing: 1.5,
                    marginBottom: 4,
                  }}
                >
                  {ready.brand.toUpperCase()}
                </div>
                <div
                  style={{
                    fontSize: 'clamp(28px, 4vw, 36px)',
                    fontWeight: 800,
                    lineHeight: 1.1,
                    marginBottom: 18,
                    background: 'linear-gradient(135deg, #fff 0%, #BFDBFE 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                  }}
                >
                  {ready.model}
                </div>

                {/* Tags row */}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {ready.tags.map((tag, i) => (
                    <motion.span
                      key={i}
                      whileHover={{ scale: 1.05, y: -2 }}
                      style={{
                        padding: '6px 12px',
                        borderRadius: 8,
                        background: 'rgba(255,255,255,0.06)',
                        border: '1px solid rgba(255,255,255,0.12)',
                        fontSize: 12,
                        fontWeight: 600,
                        color: '#fff',
                        fontFamily:
                          i === 1
                            ? 'var(--font-mono, ui-monospace, Menlo, monospace)'
                            : 'inherit',
                      }}
                    >
                      {tag}
                    </motion.span>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </motion.div>

        {/* ───────── Roadmap marquee ───────── */}
        <div style={{ marginTop: 48 }}>
          <div
            style={{
              textAlign: 'center',
              fontSize: 11,
              fontWeight: 800,
              letterSpacing: 2.4,
              color: 'var(--ink-500)',
              marginBottom: 18,
            }}
          >
            {t('landing.vehicles.roadmapLabel')}
          </div>

          <div
            style={{
              position: 'relative',
              overflow: 'hidden',
              maskImage:
                'linear-gradient(90deg, transparent, #000 10%, #000 90%, transparent)',
              WebkitMaskImage:
                'linear-gradient(90deg, transparent, #000 10%, #000 90%, transparent)',
            }}
          >
            <motion.div
              animate={{ x: ['0%', '-50%'] }}
              transition={{ duration: 28, repeat: Infinity, ease: 'linear' }}
              style={{ display: 'flex', gap: 14, width: 'max-content' }}
            >
              <motion.div
                variants={fadeUpStagger}
                style={{ display: 'flex', gap: 14 }}
              >
                {roadmapRun.map((v, i) => (
                  <div
                    key={i}
                    style={{
                      padding: '10px 18px',
                      borderRadius: 999,
                      background: 'var(--paper)',
                      border: '1px dashed var(--rule)',
                      color: 'var(--ink-700)',
                      fontSize: 13,
                      fontWeight: 600,
                      whiteSpace: 'nowrap',
                      flexShrink: 0,
                    }}
                  >
                    {v}
                  </div>
                ))}
              </motion.div>
            </motion.div>
          </div>
        </div>
      </div>

      <style>{`
        @media (max-width: 720px) {
          .vehicle-grid {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>
    </motion.section>
  )
}
