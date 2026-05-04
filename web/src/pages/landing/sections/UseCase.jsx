import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

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
          viewport={{ once: true, amount: 0.3 }}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
            gap: 16,
          }}
        >
          {STEPS.map((s) => (
            <motion.div key={s.num} variants={fadeUpItem} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              padding: 20,
              border: '1px solid var(--rule)',
              position: 'relative',
              textAlign: 'center',
            }}>
              <div style={{
                width: 36, height: 36,
                borderRadius: '50%',
                background: 'var(--bk-navy-700)',
                color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontWeight: 800, fontSize: 14,
                margin: '0 auto 12px',
              }}>{s.num}</div>
              <div style={{ fontSize: 28, marginBottom: 8 }}>{s.icon}</div>
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
                lineHeight: 1.5,
                margin: 0,
              }}>{s.desc}</p>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </motion.section>
  )
}