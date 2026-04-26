/**
 * Hiển thị số nổi bật. Phase 1: render tĩnh, phase 2 sẽ thêm count-up animation.
 *
 * @param value   Số hoặc text (vd: "8+", "50+")
 * @param label   Nhãn dưới số (uppercase)
 */
export default function AnimatedNumber({ value, label }) {
  return (
    <div style={{ textAlign: 'center', padding: '0 8px' }}>
      <div style={{
        fontSize: 'clamp(32px, 5vw, 48px)',
        fontWeight: 900,
        color: '#FFFFFF',
        lineHeight: 1,
        marginBottom: 6,
      }}>
        {value}
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
