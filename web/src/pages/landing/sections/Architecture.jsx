import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import PipelineDiagram from '../shared/PipelineDiagram'
import iconHardware from '../../../assets/svg/three-pillars/icon-hardware.svg'
import iconMobile from '../../../assets/svg/three-pillars/icon-mobile.svg'
import iconWeb from '../../../assets/svg/three-pillars/icon-web.svg'

export default function Architecture() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

  const PILLARS = [
    {
      icon: iconHardware,
      title: t('landing.architecture.pillars.hardware.title'),
      tech: t('landing.architecture.pillars.hardware.tech'),
      desc: t('landing.architecture.pillars.hardware.desc'),
    },
    {
      icon: iconMobile,
      title: t('landing.architecture.pillars.mobile.title'),
      tech: t('landing.architecture.pillars.mobile.tech'),
      desc: t('landing.architecture.pillars.mobile.desc'),
    },
    {
      icon: iconWeb,
      title: t('landing.architecture.pillars.web.title'),
      tech: t('landing.architecture.pillars.web.tech'),
      desc: t('landing.architecture.pillars.web.desc'),
    },
  ]

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="kien-truc"
      className="landing-section"
      style={{ background: 'var(--paper-soft)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.architecture.eyebrow')}
          title={t('landing.architecture.title')}
          sub={t('landing.architecture.sub')}
        />

        <motion.div
          variants={fadeUpStagger}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.3 }}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
            gap: 24,
          }}
        >
          {PILLARS.map((p, i) => (
            <motion.div
              key={i}
              variants={fadeUpItem}
              whileHover={{ y: -4, boxShadow: '0 12px 24px rgba(0,0,0,0.08)' }}
              transition={{ duration: 0.2 }}
              style={{
                background: 'var(--paper)',
                borderRadius: 'var(--radius-card)',
                border: '1px solid var(--rule)',
                padding: 28,
                textAlign: 'center',
              }}
            >
              <img src={p.icon} alt="" style={{ width: 56, height: 56, marginBottom: 16 }} />
              <h3 style={{
                fontSize: 14,
                fontWeight: 800,
                color: 'var(--bk-navy-700)',
                letterSpacing: 2,
                margin: '0 0 8px',
              }}>{p.title}</h3>
              <div style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 12,
                color: 'var(--ink-500)',
                marginBottom: 12,
              }}>{p.tech}</div>
              <p style={{ fontSize: 13, color: 'var(--ink-700)', lineHeight: 1.6, margin: 0 }}>
                {p.desc}
              </p>
            </motion.div>
          ))}
        </motion.div>

        <PipelineDiagram />
      </div>
    </motion.section>
  )
}