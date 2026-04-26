import AnimatedNumber from '../shared/AnimatedNumber'

const METRICS = [
  { value: '8+',  label: 'Hãng xe\nhỗ trợ' },
  { value: '50+', label: 'Thông số\ncảm biến' },
  { value: '6',   label: 'Buổi học\nthực hành' },
  { value: '3',   label: 'Thành phần\ntích hợp' },
  { value: '5',   label: 'Giao thức\nchuẩn đoán' },
]

export default function MetricStrip() {
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
