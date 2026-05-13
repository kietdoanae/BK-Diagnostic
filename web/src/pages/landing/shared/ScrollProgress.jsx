import { motion, useScroll, useSpring } from 'framer-motion'

/**
 * Thanh tiến trình cuộn hiển thị ở đầu trang.
 * Tự động animate khi người dùng cuộn trang.
 */
export default function ScrollProgress() {
  const { scrollYProgress } = useScroll()
  const scaleX = useSpring(scrollYProgress, {
    stiffness: 100,
    damping: 30,
    restDelta: 0.001,
  })

  return (
    <motion.div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        height: 3,
        background: 'linear-gradient(90deg, var(--bk-blue-500), var(--gold-500), var(--bk-navy-700))',
        transformOrigin: '0%',
        scaleX,
        zIndex: 9999,
      }}
    />
  )
}