import { useTranslation } from 'react-i18next'
import AnimatedNumber from '../shared/AnimatedNumber'

export default function MetricStrip() {
  const { t } = useTranslation()

  const METRICS = [
    { value: '8+',  label: t('landing.metrics.brands') },
    { value: '50+', label: t('landing.metrics.sensors') },
    { value: '6',   label: t('landing.metrics.sessions') },
    { value: '3',   label: t('landing.metrics.components') },
    { value: '5',   label: t('landing.metrics.protocols') },
  ]

  return (
    <section style={{
      background: 'var(--bk-navy-700)',
      padding: '40px 24px',
    }}>
      <div className="landing-container" style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
        gap: 24,
        alignItems: 'center',
      }}>
        {METRICS.map((m, i) => (
          <div key={i} style={{
            borderRight: i < METRICS.length - 1 ? '1px solid rgba(255,255,255,0.15)' : 'none',
          }}>
            <AnimatedNumber
              value={m.value}
              label={m.label.split('\n').map((t, j) => (
                <span key={j} style={{ display: 'block' }}>{t}</span>
              ))}
            />
          </div>
        ))}
      </div>
    </section>
  )
}