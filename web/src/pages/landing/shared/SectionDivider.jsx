import { motion } from 'framer-motion'

/**
 * Divider trang trí giữa các section.
 * Tạo hiệu ứng chuyển cảnh mượt mà giữa các phần.
 */
export default function SectionDivider({ variant = 'wave', flip = false }) {
  const dividers = {
    wave: (
      <svg viewBox="0 0 1440 80" fill="none" preserveAspectRatio="none" style={{ width: '100%', height: 80, display: 'block', transform: flip ? 'scaleY(-1)' : 'none' }}>
        <path d="M0,40 C360,80 720,0 1080,40 C1260,60 1380,50 1440,40 L1440,80 L0,80 Z" fill="var(--paper-soft)" />
        <path d="M0,50 C360,80 720,10 1080,50 C1260,70 1380,60 1440,50 L1440,80 L0,80 Z" fill="var(--paper-soft)" opacity="0.5" />
      </svg>
    ),
    tilt: (
      <svg viewBox="0 0 1440 60" fill="none" preserveAspectRatio="none" style={{ width: '100%', height: 60, display: 'block', transform: flip ? 'scaleY(-1)' : 'none' }}>
        <path d="M0,0 L1440,60 L1440,60 L0,60 Z" fill="var(--paper-soft)" />
      </svg>
    ),
    curve: (
      <svg viewBox="0 0 1440 80" fill="none" preserveAspectRatio="none" style={{ width: '100%', height: 80, display: 'block', transform: flip ? 'scaleY(-1)' : 'none' }}>
        <path d="M0,80 Q720,0 1440,80 L1440,80 L0,80 Z" fill="var(--paper-soft)" />
      </svg>
    ),
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      whileInView={{ opacity: 1 }}
      viewport={{ once: true }}
      transition={{ duration: 0.6 }}
      style={{
        lineHeight: 0,
        marginTop: flip ? 0 : -1,
        marginBottom: flip ? -1 : 0,
        background: flip ? 'var(--paper)' : 'transparent',
      }}
    >
      {dividers[variant] || dividers.wave}
    </motion.div>
  )
}