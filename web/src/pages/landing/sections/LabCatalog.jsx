import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

/**
 * Six-lab curriculum showcase.  Each lab is a tilt-on-hover card with a
 * difficulty meter built from filled / hollow pills.  Cards stagger-fade
 * in on viewport entry.
 */

const LEVEL_COLOR = (level) => {
  if (level <= 1) return '#10B981' // emerald
  if (level === 2) return '#3B82F6' // blue
  if (level === 3) return '#7C3AED' // violet
  if (level === 4) return '#F59E0B' // amber
  return '#EF4444' // red — capstone
}

function DifficultyMeter({ level, levelLabel }) {
  const color = LEVEL_COLOR(level)
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <span
        style={{
          fontSize: 10,
          color: 'var(--ink-500)',
          fontWeight: 700,
          letterSpacing: 1.2,
        }}
      >
        {levelLabel}
      </span>
      <div style={{ display: 'flex', gap: 3 }}>
        {[1, 2, 3, 4, 5].map((i) => (
          <motion.span
            key={i}
            initial={{ scaleX: 0 }}
            whileInView={{ scaleX: 1 }}
            viewport={{ once: true }}
            transition={{ delay: 0.05 * i, duration: 0.3 }}
            style={{
              display: 'inline-block',
              width: 18,
              height: 4,
              borderRadius: 2,
              background: i <= level ? color : 'rgba(0,0,0,0.07)',
              transformOrigin: 'left',
              boxShadow: i <= level ? `0 0 6px ${color}66` : 'none',
            }}
          />
        ))}
      </div>
    </div>
  )
}

export default function LabCatalog() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()
  const items = t('landing.labCatalog.items', { returnObjects: true })
  const levelLabel = t('landing.labCatalog.levelLabel')

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="lab"
      className="landing-section"
      style={{
        background:
          'linear-gradient(180deg, var(--paper) 0%, var(--paper-soft) 100%)',
        position: 'relative',
      }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.labCatalog.eyebrow')}
          title={t('landing.labCatalog.title')}
          sub={t('landing.labCatalog.sub')}
          align="center"
        />

        <motion.div
          variants={fadeUpStagger}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.15 }}
          style={{
            marginTop: 40,
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: 18,
          }}
        >
          {items.map((lab) => {
            const color = LEVEL_COLOR(lab.level)
            const isFlagship = lab.code === 'LAB-03'
            const isCapstone = lab.code === 'LAB-06'
            return (
              <motion.div
                key={lab.code}
                variants={fadeUpItem}
                whileHover={{ y: -6, rotate: -0.4 }}
                transition={{ type: 'spring', stiffness: 260, damping: 22 }}
                style={{
                  position: 'relative',
                  background: 'var(--paper)',
                  borderRadius: 16,
                  padding: 22,
                  border: `1px solid ${
                    isFlagship || isCapstone ? color + '55' : 'var(--rule)'
                  }`,
                  boxShadow:
                    isFlagship || isCapstone
                      ? `0 8px 28px -12px ${color}44`
                      : '0 1px 2px rgba(0,0,0,0.04)',
                  overflow: 'hidden',
                  cursor: 'default',
                }}
              >
                {/* Top accent bar */}
                <div
                  style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    right: 0,
                    height: 3,
                    background: `linear-gradient(90deg, ${color}, ${color}33)`,
                  }}
                />

                {/* Flagship / Capstone badge */}
                {(isFlagship || isCapstone) && (
                  <motion.div
                    animate={{ opacity: [1, 0.65, 1] }}
                    transition={{ duration: 2.4, repeat: Infinity }}
                    style={{
                      position: 'absolute',
                      top: 14,
                      right: 14,
                      padding: '3px 8px',
                      borderRadius: 6,
                      background: color,
                      color: '#fff',
                      fontSize: 9,
                      fontWeight: 800,
                      letterSpacing: 1.2,
                    }}
                  >
                    {isFlagship ? 'FLAGSHIP' : 'CAPSTONE'}
                  </motion.div>
                )}

                {/* Lab code */}
                <div
                  style={{
                    fontFamily: 'var(--font-mono, ui-monospace, Menlo, monospace)',
                    fontSize: 11,
                    color: color,
                    fontWeight: 700,
                    letterSpacing: 1.5,
                    marginBottom: 8,
                  }}
                >
                  {lab.code}
                </div>

                {/* Title */}
                <h3
                  style={{
                    margin: '0 0 10px',
                    fontSize: 15,
                    fontWeight: 700,
                    color: 'var(--ink-900)',
                    lineHeight: 1.35,
                    minHeight: 40,
                  }}
                >
                  {lab.title}
                </h3>

                {/* Description */}
                <p
                  style={{
                    margin: '0 0 16px',
                    fontSize: 12.5,
                    color: 'var(--ink-700)',
                    lineHeight: 1.6,
                  }}
                >
                  {lab.desc}
                </p>

                {/* Difficulty meter */}
                <DifficultyMeter level={lab.level} levelLabel={levelLabel} />
              </motion.div>
            )
          })}
        </motion.div>
      </div>
    </motion.section>
  )
}
