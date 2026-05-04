import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import contextDiagramSvg from '../../../assets/svg/context-diagram.svg'

export default function Context() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

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
          eyebrow={t('landing.context.eyebrow')}
          title={t('landing.context.title')}
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 48,
          alignItems: 'center',
        }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              {t('landing.context.p1')}
            </p>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              {t('landing.context.p2')}
            </p>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              {t('landing.context.p3')}
            </p>
          </div>

          <figure style={{ margin: 0, textAlign: 'center' }}>
            <img src={contextDiagramSvg} alt={t('landing.context.imgAlt')}
              style={{ maxWidth: '100%', height: 'auto' }} />
            <figcaption style={{
              marginTop: 12,
              fontSize: 12,
              fontStyle: 'italic',
              color: 'var(--gold-500)',
              fontWeight: 500,
            }}>
              {t('landing.context.imgCaption')}
            </figcaption>
          </figure>
        </div>
      </div>
    </motion.section>
  )
}