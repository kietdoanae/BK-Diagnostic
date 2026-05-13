import { motion } from 'framer-motion'

/**
 * Header chuẩn cho mỗi section: eyebrow (uppercase nhỏ), title (lớn), sub (mô tả).
 *
 * @param eyebrow Chữ uppercase nhỏ phía trên title
 * @param title   Tiêu đề section
 * @param sub     Phụ đề mô tả ngắn (optional)
 * @param align   'center' | 'left' (default 'center')
 */
export default function SectionHeader({ eyebrow, title, sub, align = 'center' }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.5 }}
      transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
      style={{
        textAlign: align,
        marginBottom: 48,
        maxWidth: align === 'center' ? 720 : '100%',
        marginLeft: align === 'center' ? 'auto' : 0,
        marginRight: align === 'center' ? 'auto' : 0,
      }}
    >
      {eyebrow && (
        <motion.div
          initial={{ opacity: 0, x: align === 'left' ? -12 : 0 }}
          whileInView={{ opacity: 1, x: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.4, delay: 0.1 }}
          style={{
            color: 'var(--bk-blue-500)',
            fontWeight: 700,
            fontSize: 12,
            textTransform: 'uppercase',
            letterSpacing: 2,
            marginBottom: 10,
          }}
        >
          {eyebrow}
        </motion.div>
      )}
      <h2 style={{
        fontSize: 'clamp(24px, 4vw, 36px)',
        fontWeight: 800,
        margin: '0 0 12px',
        color: 'var(--ink-900)',
        letterSpacing: '-0.02em',
        lineHeight: 1.2,
      }}>
        {title}
      </h2>

      {/* Decorative underline */}
      {align === 'center' && (
        <motion.div
          initial={{ scaleX: 0 }}
          whileInView={{ scaleX: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6, delay: 0.2, ease: [0.22, 1, 0.36, 1] }}
          style={{
            width: 60,
            height: 3,
            background: 'linear-gradient(90deg, var(--bk-blue-500), var(--gold-500))',
            borderRadius: 2,
            margin: '0 auto 16px',
            transformOrigin: align === 'center' ? 'center' : 'left',
          }}
        />
      )}

      {sub && (
        <motion.p
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: 0.3 }}
          style={{
            fontSize: 16,
            lineHeight: 1.7,
            color: 'var(--ink-500)',
            margin: 0,
          }}
        >
          {sub}
        </motion.p>
      )}
    </motion.div>
  )
}