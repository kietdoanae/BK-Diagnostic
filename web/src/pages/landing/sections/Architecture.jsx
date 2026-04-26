import { motion } from 'framer-motion'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import PipelineDiagram from '../shared/PipelineDiagram'
import iconHardware from '../../../assets/svg/three-pillars/icon-hardware.svg'
import iconMobile from '../../../assets/svg/three-pillars/icon-mobile.svg'
import iconWeb from '../../../assets/svg/three-pillars/icon-web.svg'

const PILLARS = [
  {
    icon: iconHardware,
    title: 'PHẦN CỨNG',
    tech: 'STM32F103 + MCP2515 + CP2102',
    desc: 'Đọc & giải mã khung CAN từ ECU',
  },
  {
    icon: iconMobile,
    title: 'MOBILE APP',
    tech: 'Android Kotlin · Jetpack Compose',
    desc: 'Hiển thị live data, ghi log, chế độ Lab',
  },
  {
    icon: iconWeb,
    title: 'WEB PLATFORM',
    tech: 'React + Supabase · Ant Design 5',
    desc: 'Quản trị người dùng và hệ thống Lab cho giảng viên',
  },
]

export default function Architecture() {
  const { ref, inView } = useInViewAnimation()

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
          eyebrow="KIẾN TRÚC"
          title="Ba trụ cột tích hợp"
          sub="Phần cứng đo lường, ứng dụng di động hiển thị, web platform quản trị — kết nối qua giao thức CAN, USB và HTTPS."
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
            <motion.div key={i} variants={fadeUpItem} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              border: '1px solid var(--rule)',
              padding: 28,
              textAlign: 'center',
              transition: 'transform 200ms ease-out, box-shadow 200ms ease-out',
              cursor: 'default',
            }}
            onMouseEnter={e => {
              e.currentTarget.style.transform = 'translateY(-4px)'
              e.currentTarget.style.boxShadow = '0 12px 24px rgba(0,0,0,0.08)'
            }}
            onMouseLeave={e => {
              e.currentTarget.style.transform = 'translateY(0)'
              e.currentTarget.style.boxShadow = 'none'
            }}>
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
