import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

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
          viewport={{ once: true, amount: 0.3 }}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: 24,
          }}
        >
          {CLUSTERS.map((c, i) => (
            <motion.div key={i} variants={fadeUpItem} whileHover={{ y: -4 }} style={{
              background: 'var(--paper-soft)',
              borderRadius: 'var(--radius-card)',
              padding: 24,
              border: '1px solid var(--rule)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
                <span style={{ fontSize: 24 }}>{c.icon}</span>
                <h3 style={{
                  margin: 0,
                  fontSize: 13,
                  color: 'var(--bk-navy-700)',
                  letterSpacing: 2,
                  fontWeight: 800,
                }}>{c.title}</h3>
              </div>
              <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
                {c.items.map((item, j) => (
                  <li key={j} style={{
                    fontSize: 13,
                    color: 'var(--ink-700)',
                    paddingLeft: 12,
                    borderLeft: '2px solid var(--bk-blue-100)',
                  }}>{item}</li>
                ))}
              </ul>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </motion.section>
  )
}