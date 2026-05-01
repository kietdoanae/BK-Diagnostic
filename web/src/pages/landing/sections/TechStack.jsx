import { motion } from 'framer-motion'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

const CLUSTERS = [
  {
    icon: '⚡',
    title: 'PHẦN CỨNG',
    items: ['STM32 HAL · ngôn ngữ C', 'MCP2515 · TJA1050', 'CAN ISO 11898-1', 'STM32CubeIDE'],
  },
  {
    icon: '📱',
    title: 'MOBILE',
    items: ['Kotlin 2.2 · Coroutines', 'Jetpack Compose · Material 3', 'usb-serial-for-android', 'Supabase Auth + Realtime'],
  },
  {
    icon: '🌐',
    title: 'WEB',
    items: ['React 19 + Vite 8', 'Ant Design 6', 'Framer Motion 11', 'React Router 7'],
  },
  {
    icon: '☁️',
    title: 'BACKEND',
    items: ['Supabase Postgres', 'Row-Level Security (RLS)', 'Storage + Edge Functions', 'PostgREST + Realtime'],
  },
]

export default function TechStack() {
  const { ref, inView } = useInViewAnimation()

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
          eyebrow="TECH STACK"
          title="Công nghệ sử dụng"
          sub="Lựa chọn công nghệ ưu tiên mã nguồn mở, cộng đồng lớn và phù hợp môi trường giáo dục."
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
