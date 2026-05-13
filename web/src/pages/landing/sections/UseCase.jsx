import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

const STEP_COLORS = [
  { bg: 'linear-gradient(135deg, rgba(21,101,192,0.1), rgba(21,101,192,0.02))', border: 'var(--bk-blue-500)' },
  { bg: 'linear-gradient(135deg, rgba(212,160,23,0.1), rgba(212,160,23,0.02))', border: 'var(--gold-500)' },
  { bg: 'linear-gradient(135deg, rgba(22,163,74,0.1), rgba(22,163,74,0.02))', border: 'var(--green-600)' },
  { bg: 'linear-gradient(135deg, rgba(0,50,145,0.1), rgba(0,50,145,0.02))', border: 'var(--bk-navy-700)' },
  { bg: 'linear-gradient(135deg, rgba(21,101,192,0.1), rgba(212,160,23,0.05))', border: 'var(--bk-blue-500)' },
  { bg: 'linear-gradient(135deg, rgba(212,160,23,0.1), rgba(22,163,74,0.05))', border: 'var(--gold-500)' },
]

export default function UseCase() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

  const STEPS = t('landing.useCase.steps', { returnObjects: true })

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="lab"
      className="landing-section"
      style={{ background: 'var(--paper-soft)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.useCase.eyebrow')}
          title={t('landing.useCase.title')}
          sub={t('landing.useCase.sub')}
        />

        <motion.div
          variants={fadeUpStagger}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.2 }}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
            gap: 20,
            position: 'relative',
          }}
        >
          {STEPS.map((s, i) => {
            const colors = STEP_COLORS[i % STEP_COLORS.length]
            return (
              <motion.div
                key={s.num}
                variants={fadeUpItem}
                whileHover={{ y: -8, boxShadow: '0 16px 32px rgba(0,0,0,0.08)' }}
                transition={{ duration: 0.3 }}
                style={{
                  background: colors.bg,
                  borderRadius: 'var(--radius-card)',
                  padding: 24,
                  border: '1px solid var(--rule)',
                  position: 'relative',
                  textAlign: 'center',
                  cursor: 'default',
                  overflow: 'hidden',
                }}
              >
                {/* Top accent */}
                <div style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  height: 3,
                  background: `linear-gradient(90deg, transparent, ${colors.border}, transparent)`,
                }} />

                <motion.div
                  whileHover={{ scale: 1.1, rotate: 5 }}
                  style={{
                    width: 40, height: 40,
                    borderRadius: '50%',
                    background: `linear-gradient(135deg, var(--bk-navy-700), var(--bk-blue-500))`,
                    color: '#fff',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontWeight: 800, fontSize: 15,
                    margin: '0 auto 12px',
                    boxShadow: '0 4px 12px rgba(0, 50, 145, 0.2)',
                  }}
                >{s.num}</motion.div>
                <motion.div
                  style={{ fontSize: 32, marginBottom: 10 }}
                  animate={{ scale: [1, 1.08, 1] }}
                  transition={{ duration: 3, repeat: Infinity, delay: i * 0.4 }}
                >{s.icon}</motion.div>
                <h4 style={{
                  margin: '0 0 8px',
                  fontSize: 12,
                  color: 'var(--bk-navy-700)',
                  fontWeight: 800,
                  letterSpacing: 1,
                }}>{s.title}</h4>
                <p style={{
                  fontSize: 12,
                  color: 'var(--ink-500)',
                  lineHeight: 1.6,
                  margin: 0,
                }}>{s.desc}</p>
              </motion.div>
            )
          })}
        </motion.div>
      </div>
    </motion.section>
  )
}