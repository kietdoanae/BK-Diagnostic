import { useEffect, useState } from 'react'
import { useInView } from 'react-intersection-observer'

/**
 * Số count-up khi vào viewport. Nếu value chứa ký tự không phải số (vd "8+"),
 * tách phần số để animate, giữ phần text.
 */
export default function AnimatedNumber({ value, label }) {
  const { ref, inView } = useInView({ triggerOnce: true, threshold: 0.5 })
  const [display, setDisplay] = useState(0)

  // Tách: "8+" → numeric=8, suffix="+"
  const match = String(value).match(/^(\d+)(.*)$/)
  const numeric = match ? parseInt(match[1], 10) : null
  const suffix  = match ? match[2] : value

  useEffect(() => {
    if (!inView || numeric === null) return
    const duration = 900
    const start = performance.now()
    let raf
    const tick = (now) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3) // easeOutCubic
      setDisplay(Math.floor(eased * numeric))
      if (t < 1) raf = requestAnimationFrame(tick)
      else setDisplay(numeric)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [inView, numeric])

  return (
    <div ref={ref} style={{ textAlign: 'center', padding: '0 8px' }}>
      <div style={{
        fontSize: 'clamp(32px, 5vw, 48px)',
        fontWeight: 900,
        color: '#FFFFFF',
        lineHeight: 1,
        marginBottom: 6,
      }}>
        {numeric === null ? value : `${display}${suffix}`}
      </div>
      <div style={{
        fontSize: 11,
        color: 'rgba(255,255,255,0.7)',
        textTransform: 'uppercase',
        letterSpacing: 2,
        fontWeight: 600,
        lineHeight: 1.4,
      }}>
        {label}
      </div>
    </div>
  )
}
