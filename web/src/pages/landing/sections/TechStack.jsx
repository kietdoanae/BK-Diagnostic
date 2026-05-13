import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

const GRADIENT_COLORS = [
  'linear-gradient(135deg, rgba(21,101,192,0.08), rgba(212,160,23,0.05))',
  'linear-gradient(135deg, rgba(212,160,23,0.08), rgba(22,163,74,0.05))',
  'linear-gradient(135deg, rgba(22,163,74,0.08), rgba(21,101,192,0.05))',
  'linear-gradient(135deg, rgba(0,50,145,0.08), rgba(212,160,23,0.05))',
]

const ACCENT_COLORS = ['var(--bk-blue-500)', 'var(--gold-500)', 'var(--green-600)', 'var(--bk-navy-700)']

export default function TechStack() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

  const CLUSTERS = t('landing.techStack.clusters', { returnObjects: true })

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      className="landing-section"
      style={{ background: 'var(--paper)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.techStack.eyebrow')}
          title={t('landing.techStack.title')}
          sub={t('landing.techStack.sub')}
        />

        <motion.div
          variants={fadeUpStagger}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.2 }}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: 24,
          }}
        >
          {CLUSTERS.map((c, i) => (
            <motion.div
              key={i}
              variants={fadeUpItem}
              whileHover={{ y: -8, boxShadow: '0 20px 40px rgba(0,0,0,0.08)' }}
              transition={{ duration: 0.3 }}
              style={{
                background: GRADIENT_COLORS[i % GRADIENT_COLORS.length],
                borderRadius: 'var(--radius-card)',
                padding: 28,
                border: '1px solid var(--rule)',
                position: 'relative',
                overflow: 'hidden',
                cursor: 'default',
              }}
            >
              {/* Decorative accent line */}
              <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: 3,
                background: `linear-gradient(90deg, ${ACCENT_COLORS[i % ACCENT_COLORS.length]}, transparent)`,
                borderRadius: 'var(--radius-card) var(--radius-card) 0 0',
              }} />

              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
                <motion.span
                  style={{
                    fontSize: 28,
                    width: 44,
                    height: 44,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: 12,
                    background: 'rgba(255,255,255,0.8)',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                  }}
                  whileHover={{ scale: 1.15, rotate: 10 }}
                >
                  {c.icon}
                </motion.span>
                <h3 style={{
                  margin: 0,
                  fontSize: 13,
                  color: 'var(--bk-navy-700)',
                  letterSpacing: 2,
                  fontWeight: 800,
                }}>{c.title}</h3>
              </div>
              <ul style={{
                margin: 0,
                padding: 0,
                listStyle: 'none',
                display: 'flex',
                flexDirection: 'column',
                gap: 10,
              }}>
                {c.items.map((item, j) => (
                  <motion.li
                    key={j}
                    initial={{ opacity: 0, x: -10 }}
                    whileInView={{ opacity: 1, x: 0 }}
                    viewport={{ once: true }}
                    transition={{ delay: j * 0.05 + 0.2 }}
                    style={{
                      fontSize: 13,
                      color: 'var(--ink-700)',
                      paddingLeft: 14,
                      borderLeft: `2px solid ${ACCENT_COLORS[i % ACCENT_COLORS.length]}`,
                      lineHeight: 1.5,
                    }}
                  >
                    {item}
                  </motion.li>
                ))}
              </ul>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </motion.section>
  )
}