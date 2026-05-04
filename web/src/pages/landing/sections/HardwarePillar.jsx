import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

export default function HardwarePillar() {
  const { ref, inView } = useInViewAnimation()
  const { t } = useTranslation()

  const COMPONENTS = t('landing.hardware.components', { returnObjects: true })
  const PROTOCOLS = t('landing.hardware.protocols', { returnObjects: true })

  const SPECS = [
    [t('landing.hardware.specs.canBaudrate'), t('landing.hardware.specs.canBaudrateVal')],
    [t('landing.hardware.specs.uartBaud'), t('landing.hardware.specs.uartBaudVal')],
    [t('landing.hardware.specs.frameProtocol'), t('landing.hardware.specs.frameProtocolVal')],
    [t('landing.hardware.specs.powerIn'), t('landing.hardware.specs.powerInVal')],
    [t('landing.hardware.specs.currentDraw'), t('landing.hardware.specs.currentDrawVal')],
    [t('landing.hardware.specs.pcbSize'), t('landing.hardware.specs.pcbSizeVal')],
  ]

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
          eyebrow={t('landing.hardware.eyebrow')}
          title={t('landing.hardware.title')}
          sub={t('landing.hardware.sub')}
          align="left"
        />

        {/* Hàng trên: Ảnh + Thành phần */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '5fr 7fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 32,
        }} className="responsive-grid">
          <PlaceholderImage
            path="hardware/pcb-top.jpg"
            alt={t('landing.hardware.imgAlt')}
            ratio="4/3"
            caption={t('landing.hardware.imgCaption')}
          />

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>{t('landing.hardware.componentsTitle')}</h3>
            <motion.ul
              variants={fadeUpStagger}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.3 }}
              style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 12 }}
            >
              {COMPONENTS.map((c, i) => (
                <motion.li key={i} variants={fadeUpItem} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{c}</span>
                </motion.li>
              ))}
            </motion.ul>
          </div>
        </div>

        {/* Bảng spec */}
        <div style={{
          background: 'var(--paper-soft)',
          borderRadius: 'var(--radius-card)',
          padding: 24,
          border: '1px solid var(--rule)',
          marginBottom: 32,
        }}>
          <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>{t('landing.hardware.specsTitle')}</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <tbody>
              {SPECS.map(([k, v], i) => (
                <tr key={i} style={{ borderBottom: i < SPECS.length - 1 ? '1px solid var(--rule)' : 'none' }}>
                  <td style={{ padding: '12px 0', color: 'var(--ink-500)', width: '35%', fontWeight: 500 }}>{k}</td>
                  <td style={{ padding: '12px 0', color: 'var(--ink-900)', fontFamily: i === 2 ? 'var(--font-mono)' : 'inherit', fontSize: i === 2 ? 13 : 14 }}>{v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 2 thẻ ngang: Frame protocol | Giao thức hỗ trợ */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: 24,
        }}>
          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 24,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 16, color: 'var(--ink-900)' }}>{t('landing.hardware.uartTitle')}</h3>
            <pre style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 13,
              background: 'var(--paper)',
              padding: 14,
              borderRadius: 8,
              border: '1px solid var(--rule)',
              color: 'var(--ink-900)',
              margin: '0 0 12px',
              overflow: 'auto',
            }}>{t('landing.hardware.uartCode')}</pre>
            <p style={{ fontSize: 13, color: 'var(--ink-500)', lineHeight: 1.6, margin: 0 }}>
              {t('landing.hardware.uartDesc')}
            </p>
          </div>

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 24,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 16, color: 'var(--ink-900)' }}>{t('landing.hardware.protocolsTitle')}</h3>
            <motion.ul
              variants={fadeUpStagger}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.3 }}
              style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}
            >
              {PROTOCOLS.map((p, i) => (
                <motion.li key={i} variants={fadeUpItem} style={{ display: 'flex', gap: 10, fontSize: 13, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--bk-blue-500)' }}>●</span>
                  <span>{p}</span>
                </motion.li>
              ))}
            </motion.ul>
          </div>
        </div>
      </div>

      <style>{`
        @media (max-width: 767px) {
          .landing-container .responsive-grid { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </motion.section>
  )
}