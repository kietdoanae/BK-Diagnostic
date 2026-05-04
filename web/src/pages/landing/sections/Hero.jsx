import { Button } from 'antd'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import PlaceholderImage from '../shared/PlaceholderImage'
import { fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import heroBgSvg from '../../../assets/svg/hero-bg-mesh.svg'

function scrollTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

export default function Hero() {
  const { t } = useTranslation()

  return (
    <section id="tong-quan" style={{
      position: 'relative',
      paddingTop: 128,
      paddingBottom: 64,
      backgroundImage: `url(${heroBgSvg})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
    }}>
      <motion.div
        className="landing-container"
        style={{ textAlign: 'center' }}
        initial="hidden"
        animate="visible"
        variants={fadeUpStagger}
      >
        <motion.div variants={fadeUpItem} style={{
          display: 'inline-block',
          background: 'var(--bk-blue-100)',
          color: 'var(--bk-navy-700)',
          padding: '6px 16px',
          borderRadius: 20,
          fontSize: 11,
          fontWeight: 700,
          letterSpacing: 2,
          marginBottom: 24,
        }}>
          {t('landing.hero.badge')}
        </motion.div>

        <motion.h1 variants={fadeUpItem} style={{
          fontSize: 'clamp(36px, 6vw, 56px)',
          fontWeight: 800,
          lineHeight: 1.15,
          margin: '0 0 20px',
          color: 'var(--ink-900)',
          letterSpacing: '-0.02em',
          maxWidth: 800,
          marginLeft: 'auto',
          marginRight: 'auto',
        }}>
          {t('landing.hero.title').split('\n').map((line, i) => (
            <span key={i}>{line}{i === 0 && <br />}</span>
          ))}
        </motion.h1>

        <motion.p variants={fadeUpItem} style={{
          fontSize: 'clamp(15px, 2vw, 18px)',
          lineHeight: 1.7,
          color: 'var(--ink-700)',
          maxWidth: 720,
          margin: '0 auto 32px',
        }}>
          {t('landing.hero.desc')}
        </motion.p>

        <motion.div variants={fadeUpItem} style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 56 }}>
          <Button
            type="primary"
            size="large"
            onClick={() => scrollTo('kien-truc')}
            style={{ borderRadius: 'var(--radius-btn)', fontWeight: 700, height: 48, padding: '0 24px' }}
          >
            {t('landing.hero.btnExplore')}
          </Button>
          <Button
            size="large"
            onClick={() => scrollTo('lab')}
            style={{ borderRadius: 'var(--radius-btn)', fontWeight: 600, height: 48, padding: '0 24px' }}
          >
            {t('landing.hero.btnLab')}
          </Button>
        </motion.div>

        <motion.div variants={fadeUpItem}>
          <PlaceholderImage
            path="hero/sa-ban-overview.jpg"
            alt={t('landing.hero.imgAlt')}
            ratio="16/9"
            caption={t('landing.hero.imgCaption')}
          />
        </motion.div>
      </motion.div>
    </section>
  )
}