import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const AVATARS = ['team/kiet.jpg', 'team/duy.jpg', 'team/hoang.jpg']

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

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 24,
          marginBottom: 32,
        }}>
          {MEMBERS.map((m, i) => (
            <div key={i} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              padding: 24,
              border: '1px solid var(--rule)',
              textAlign: 'center',
            }}>
              <div style={{ width: 80, height: 80, margin: '0 auto 12px' }}>
                <PlaceholderImage path={AVATARS[i]} alt={m.name} ratio="1/1" />
              </div>
              <h3 style={{ margin: '8px 0 4px', fontSize: 16, color: 'var(--ink-900)' }}>{m.name}</h3>
              <div style={{
                color: 'var(--bk-blue-500)',
                fontSize: 13,
                fontWeight: 700,
                marginBottom: 8,
              }}>{t('landing.team.mssvLabel')}: {m.mssv}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-500)', lineHeight: 1.6 }}>
                {m.role}<br />{m.work}
              </div>
            </div>
          ))}
        </div>

        <div style={{
          background: 'var(--bk-blue-100)',
          borderLeft: '4px solid var(--bk-navy-700)',
          borderRadius: 'var(--radius-card)',
          padding: '20px 24px',
        }}>
          <div style={{ fontSize: 13, color: 'var(--bk-navy-700)', fontWeight: 700, marginBottom: 4 }}>
            {t('landing.team.advisorLabel')}
          </div>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink-900)', marginBottom: 4 }}>
            {t('landing.team.advisorName')}
          </div>
          <div style={{ fontSize: 13, color: 'var(--ink-500)' }}>
            {t('landing.team.advisorAffiliation')}
          </div>
        </div>
      </div>
    </motion.section>
  )
}