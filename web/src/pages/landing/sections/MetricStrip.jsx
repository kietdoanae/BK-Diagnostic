import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import AnimatedNumber from '../shared/AnimatedNumber'

export default function MetricStrip() {
  const { t } = useTranslation()
  const { ref, inView } = useInViewAnimation(0.3)

  const METRICS = [
    { value: '8+',  label: t('landing.metrics.brands'), icon: '🏭' },
    { value: '50+', label: t('landing.metrics.sensors'), icon: '📡' },
    { value: '6',   label: t('landing.metrics.sessions'), icon: '🔬' },
    { value: '3',   label: t('landing.metrics.components'), icon: '🔧' },
    { value: '5',   label: t('landing.metrics.protocols'), icon: '🔌' },
  ]

  return (
    <section style={{
      background: 'linear-gradient(135deg, var(--bk-navy-900) 0%, var(--bk-navy-700) 50%, #0a2a6e 100%)',
      padding: '48px 24px',
      position: 'relative',
      overflow: 'hidden',
    }}>
      {/* Decorative background pattern */}
      <div style={{
        position: 'absolute',
        inset: 0,
        backgroundImage: 'radial-gradient(circle at 20% 50%, rgba(21, 101, 192, 0.15) 0%, transparent 50%), radial-gradient(circle at 80% 50%, rgba(212, 160, 23, 0.1) 0%, transparent 50%)',
        pointerEvents: 'none',
      }} />

      <motion.div
        ref={ref}
        className="landing-container"
        initial="hidden"
        animate={inView ? 'visible' : 'hidden'}
        variants={fadeUpStagger}
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
          gap: 24,
          alignItems: 'center',
          position: 'relative',
          zIndex: 1,
        }}
      >
        {METRICS.map((m, i) => (
          <motion.div
            key={i}
            variants={fadeUpItem}
            whileHover={{ scale: 1.08, y: -4 }}
            transition={{ duration: 0.2 }}
            style={{
              borderRight: i < METRICS.length - 1 ? '1px solid rgba(255,255,255,0.1)' : 'none',
              padding: '8px 0',
              cursor: 'default',
            }}
          >
            <motion.div
              style={{ fontSize: 28, marginBottom: 4, textAlign: 'center' }}
              animate={{ scale: [1, 1.1, 1] }}
              transition={{ duration: 2, repeat: Infinity, delay: i * 0.3 }}
            >
              {m.icon}
            </motion.div>
            <AnimatedNumber
              value={m.value}
              label={m.label.split('\n').map((t, j) => (
                <span key={j} style={{ display: 'block' }}>{t}</span>
              ))}
            />
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}