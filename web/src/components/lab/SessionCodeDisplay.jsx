import { useEffect, useState } from 'react'
import { Card, Typography, Tag } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'

const { Text, Title } = Typography

function formatCountdown(msRemaining) {
  if (msRemaining <= 0) return 'Đã hết hạn'
  const total = Math.floor(msRemaining / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/**
 * Fixed-position card in the bottom-right showing the 6-digit session code
 * and a live countdown to `expires_at`.
 *
 * Props:
 *   code       — string, "482913"
 *   expiresAt  — ISO string
 *   status     — session status (hides display when not ACTIVE)
 */
export default function SessionCodeDisplay({ code, expiresAt, status }) {
  // Lazy initial value + interval update. `useState(() => …)` defers the
  // impure Date.now() call so the eslint react-hooks rule doesn't flag it.
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  if (status !== 'ACTIVE') return null

  const remaining = new Date(expiresAt).getTime() - now
  const expired = remaining <= 0

  return (
    <Card
      size="small"
      style={{
        position: 'fixed',
        right: 24,
        bottom: 24,
        zIndex: 100,
        boxShadow: '0 8px 24px rgba(0,0,0,0.18)',
        borderRadius: 14,
        minWidth: 220,
      }}
      styles={{ body: { padding: 14 } }}
    >
      <Text type="secondary" style={{ fontSize: 11, letterSpacing: 1 }}>MÃ SESSION</Text>
      <Title level={3} style={{ margin: '4px 0 8px', letterSpacing: 4, fontFamily: 'monospace' }}>
        {code}
      </Title>
      <Tag color={expired ? 'error' : 'processing'} icon={<ClockCircleOutlined />}>
        {formatCountdown(remaining)}
      </Tag>
    </Card>
  )
}
