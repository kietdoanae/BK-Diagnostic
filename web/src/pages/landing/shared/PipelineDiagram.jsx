import { motion } from 'framer-motion'
import { useInView } from 'react-intersection-observer'

const NODES = [
  { x: 80,  fill: '#EFF6FF', stroke: '#1565C0', icon: '🚗', label: 'Xe' },
  { x: 260, fill: '#FFF7ED', stroke: '#EA580C', icon: '🔌', label: 'MCP2515' },
  { x: 440, fill: '#F0FDF4', stroke: '#16A34A', icon: '⚙️', label: 'STM32' },
  { x: 620, fill: '#FAF5FF', stroke: '#7C3AED', icon: '🔗', label: 'CP2102' },
  { x: 800, fill: '#EFF6FF', stroke: '#1565C0', icon: '📱', label: 'App' },
  { x: 980, fill: '#DBEAFE', stroke: '#0A1E6E', icon: '☁️', label: 'Web' },
]

const EDGES = [
  { x1: 124, x2: 216, label: 'CAN' },
  { x1: 304, x2: 396, label: 'SPI' },
  { x1: 484, x2: 576, label: 'UART' },
  { x1: 664, x2: 756, label: 'USB' },
  { x1: 844, x2: 936, label: 'HTTPS' },
]

export default function PipelineDiagram() {
  const { ref, inView } = useInView({ triggerOnce: true, threshold: 0.3 })

  return (
    <figure ref={ref} style={{ margin: '24px 0 0', textAlign: 'center' }}>
      <svg viewBox="0 0 1080 220" style={{ maxWidth: '100%', height: 'auto' }}>
        <style>{`
          .pl-label { font: 700 13px 'Be Vietnam Pro', sans-serif; fill: #003291; }
          .pl-icon  { font: 600 24px 'Apple Color Emoji', 'Segoe UI Emoji', sans-serif; }
          .pl-edge  { font: 500 11px 'JetBrains Mono', monospace; fill: #6B7280; }
        `}</style>

        {NODES.map((n, i) => (
          <motion.g
            key={i}
            initial={{ opacity: 0, scale: 0.7 }}
            animate={inView ? { opacity: 1, scale: 1 } : {}}
            transition={{ delay: i * 0.15, duration: 0.4, ease: 'easeOut' }}
            style={{ transformOrigin: `${n.x}px 100px` }}
          >
            <circle cx={n.x} cy="100" r="44" fill={n.fill} stroke={n.stroke} strokeWidth="2" />
            <text x={n.x} y="92" textAnchor="middle" className="pl-icon">{n.icon}</text>
            <text x={n.x} y="170" textAnchor="middle" className="pl-label">{n.label}</text>
          </motion.g>
        ))}

        {EDGES.map((e, i) => (
          <g key={i}>
            <motion.path
              d={`M${e.x1} 100 L${e.x2} 100`}
              stroke="#9CA3AF" strokeWidth="2" strokeDasharray="4 4"
              initial={{ pathLength: 0 }}
              animate={inView ? { pathLength: 1 } : {}}
              transition={{ delay: 0.15 * (i + 1) + 0.1, duration: 0.3 }}
            />
            <motion.text
              x={(e.x1 + e.x2) / 2} y="92" textAnchor="middle" className="pl-edge"
              initial={{ opacity: 0 }}
              animate={inView ? { opacity: 1 } : {}}
              transition={{ delay: 0.15 * (i + 1) + 0.4, duration: 0.3 }}
            >{e.label}</motion.text>
          </g>
        ))}
      </svg>
      <figcaption style={{
        marginTop: 12,
        fontSize: 12,
        fontStyle: 'italic',
        color: 'var(--gold-500)',
        fontWeight: 500,
      }}>
        Hình 3 — Pipeline dữ liệu từ xe đến cloud.
      </figcaption>
    </figure>
  )
}
