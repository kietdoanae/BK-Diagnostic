import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const AVATARS = ['team/kiet.jpg', 'team/duy.jpg', 'team/hoang.jpg']

const CARD_GRADIENTS = [
  'linear-gradient(135deg, rgba(21,101,192,0.06), rgba(212,160,23,0.04))',
  'linear-gradient(135deg, rgba(212,160,23,0.06), rgba(22,163,74,0.04))',
  'linear-gradient(135deg, rgba(22,163,74,0.06), rgba(0,50,145,0.04))',
]

export default function Team() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

  const MEMBERS = t('landing.team.members', { returnObjects: true })

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="team"
      className="landing-section"
      style={{ background: 'var(--paper-soft)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow={t('landing.team.eyebrow')}
          title={t('landing.team.title')}
          sub={t('landing.team.sub')}
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
            marginBottom: 32,
          }}
        >
          {MEMBERS.map((m, i) => (
            <motion.div
              key={i}
              variants={fadeUpItem}
              whileHover={{ y: -8, boxShadow: '0 20px 40px rgba(0,0,0,0.08)' }}
              transition={{ duration: 0.3 }}
              style={{
                background: CARD_GRADIENTS[i % CARD_GRADIENTS.length],
                borderRadius: 'var(--radius-card)',
                padding: 28,
                border: '1px solid var(--rule)',
                textAlign: 'center',
                position: 'relative',
                overflow: 'hidden',
                cursor: 'default',
              }}
            >
              {/* Decorative top accent */}
              <div style={{
                position: 'absolute',
                top: 0,
                left: '30%',
                right: '30%',
                height: 3,
                background: 'linear-gradient(90deg, transparent, var(--bk-blue-500), transparent)',
                borderRadius: '0 0 4px 4px',
              }} />

              <motion.div
                whileHover={{ scale: 1.08 }}
                transition={{ duration: 0.3 }}
                style={{ width: 88, height: 88, margin: '0 auto 16px' }}
              >
                <PlaceholderImage path={AVATARS[i]} alt={m.name} ratio="1/1" />
              </motion.div>
              <h3 style={{
                margin: '8px 0 4px',
                fontSize: 17,
                color: 'var(--ink-900)',
                fontWeight: 700,
              }}>{m.name}</h3>
              <div style={{
                color: 'var(--bk-blue-500)',
                fontSize: 13,
                fontWeight: 700,
                marginBottom: 10,
                fontFamily: 'var(--font-mono)',
              }}>{t('landing.team.mssvLabel')}: {m.mssv}</div>
              <div style={{
                fontSize: 12,
                color: 'var(--ink-500)',
                lineHeight: 1.7,
              }}>
                {m.role}<br />{m.work}
              </div>
            </motion.div>
          ))}
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6, delay: 0.2 }}
          style={{
            background: 'linear-gradient(135deg, var(--bk-blue-100), rgba(212, 160, 23, 0.08))',
            borderLeft: '4px solid var(--bk-navy-700)',
            borderRadius: 'var(--radius-card)',
            padding: '24px 28px',
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Decorative pattern */}
          <div style={{
            position: 'absolute',
            top: -20,
            right: -20,
            width: 100,
            height: 100,
            borderRadius: '50%',
            background: 'rgba(21, 101, 192, 0.05)',
            pointerEvents: 'none',
          }} />

          <div style={{ fontSize: 12, color: 'var(--bk-navy-700)', fontWeight: 700, marginBottom: 6, letterSpacing: 1, textTransform: 'uppercase' }}>
            {t('landing.team.advisorLabel')}
          </div>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--ink-900)', marginBottom: 4 }}>
            {t('landing.team.advisorName')}
          </div>
          <div style={{ fontSize: 13, color: 'var(--ink-500)' }}>
            {t('landing.team.advisorAffiliation')}
          </div>
        </motion.div>
      </div>
    </motion.section>
  )
}