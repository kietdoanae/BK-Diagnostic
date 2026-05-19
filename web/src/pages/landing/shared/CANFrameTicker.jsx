import { motion } from 'framer-motion'

/**
 * Infinite-scrolling strip of CAN bus frames.  Each frame is rendered as a
 * "chip" with an ID, hex data and a tiny coloured dot.  Purely decorative —
 * the values are real Ford Ranger frames from our active_test config so the
 * page tells a coherent story without faking data.
 *
 * Designed to sit as a thin band between sections; ~52px tall.
 */

const FRAMES = [
  { id: '0x3B3', data: '44 88 C0 0C E6 00 03 3A', tag: 'WAKE', color: '#7C3AED' },
  { id: '0x201', data: '00 00 00 0D AC 00 00 00', tag: 'RPM', color: '#EF5350' },
  { id: '0x202', data: '00 00 00 00 F0 00 27 10', tag: 'SPD', color: '#42A5F5' },
  { id: '0x3B4', data: '88 07 00 00 00 00 00 00', tag: 'TPMS', color: '#FFA726' },
  { id: '0x416', data: '40 00 02 12 00 50 80 02', tag: 'ABS', color: '#FFA726' },
  { id: '0x04C', data: '40 AF FF 12 00 50 00 02', tag: 'SRS', color: '#E53935' },
  { id: '0x421', data: '0C 4A 06 12 00 50 00 02', tag: 'MIL', color: '#FFA726' },
  { id: '0x3C3', data: '01 48 10 01 10 00 00 02', tag: 'HBM', color: '#42A5F5' },
  { id: '0x416', data: '40 00 02 12 00 E3 80 02', tag: 'ESP', color: '#FFA726' },
  { id: '0x3B3', data: '44 88 C0 0C 10 50 40 02', tag: 'RIGHT', color: '#66BB6A' },
]

// Duplicate the array so the marquee can scroll seamlessly.
const RUN = [...FRAMES, ...FRAMES]

export default function CANFrameTicker() {
  return (
    <div
      style={{
        position: 'relative',
        background: 'linear-gradient(180deg, #060B1E 0%, #0B1430 100%)',
        borderTop: '1px solid rgba(255,255,255,0.06)',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
        padding: '14px 0',
        overflow: 'hidden',
        maskImage:
          'linear-gradient(90deg, transparent 0%, #000 8%, #000 92%, transparent 100%)',
        WebkitMaskImage:
          'linear-gradient(90deg, transparent 0%, #000 8%, #000 92%, transparent 100%)',
      }}
      aria-hidden
    >
      {/* Subtle grid backdrop */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage:
            'linear-gradient(rgba(124, 58, 237, 0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(124, 58, 237, 0.06) 1px, transparent 1px)',
          backgroundSize: '40px 40px',
          opacity: 0.6,
          pointerEvents: 'none',
        }}
      />

      <motion.div
        animate={{ x: ['0%', '-50%'] }}
        transition={{ duration: 40, repeat: Infinity, ease: 'linear' }}
        style={{
          display: 'flex',
          gap: 16,
          width: 'max-content',
          willChange: 'transform',
          position: 'relative',
        }}
      >
        {RUN.map((f, i) => (
          <div
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '8px 16px',
              borderRadius: 999,
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(255,255,255,0.08)',
              backdropFilter: 'blur(6px)',
              fontSize: 12,
              fontFamily: 'var(--font-mono, ui-monospace, Menlo, monospace)',
              whiteSpace: 'nowrap',
              flexShrink: 0,
            }}
          >
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: f.color,
                boxShadow: `0 0 10px ${f.color}`,
                flexShrink: 0,
              }}
            />
            <span
              style={{
                color: f.color,
                fontWeight: 700,
                letterSpacing: 0.6,
                fontSize: 10,
              }}
            >
              {f.tag}
            </span>
            <span style={{ color: 'rgba(255,255,255,0.55)' }}>ID</span>
            <span style={{ color: 'rgba(255,255,255,0.95)', fontWeight: 600 }}>{f.id}</span>
            <span style={{ color: 'rgba(255,255,255,0.35)' }}>·</span>
            <span style={{ color: 'rgba(255,255,255,0.7)' }}>{f.data}</span>
          </div>
        ))}
      </motion.div>
    </div>
  )
}
