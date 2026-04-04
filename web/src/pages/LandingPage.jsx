import { Button, Typography, Row, Col, Card, Avatar, Space, Tag } from 'antd'
import {
  ClockCircleOutlined, WarningOutlined, LaptopOutlined,
  LineChartOutlined, ToolOutlined, ApiOutlined,
} from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

const { Text, Title, Paragraph } = Typography

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100','#4527A0','#00695C']
function avatarColor(u = '') {
  let h = 0; for (const c of u) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

function Navbar({ session, profile, onSignOut }) {
  const navigate = useNavigate()
  const username = profile?.username ?? session?.user?.email?.split('@')[0] ?? 'User'
  const initial = username[0]?.toUpperCase() ?? 'U'
  const bg = avatarColor(username)

  return (
    <nav style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 50, background: 'rgba(255,255,255,0.92)', backdropFilter: 'blur(8px)', borderBottom: '1px solid #f0f0f0', height: 64, display: 'flex', alignItems: 'center', padding: '0 24px', justifyContent: 'space-between' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 36, height: 36, borderRadius: 8 }} alt="logo" />
        <Text strong style={{ color: '#003291', fontSize: 17 }}>BK Diagnostic</Text>
      </div>
      <Space size={24} style={{ display: 'flex' }}>
        <a href="#features" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Features</a>
        <a href="#hardware" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Hardware</a>
        <a href="#technology" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Technology</a>
        <a href="#team" style={{ color: '#4b5563', fontSize: 14, fontWeight: 500 }}>Team</a>
      </Space>
      <Space>
        {session ? (
          <>
            <Avatar style={{ background: bg, fontWeight: 700, cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>{initial}</Avatar>
            <Text strong style={{ color: '#374151' }}>{username}</Text>
            <Button type="primary" onClick={() => navigate('/dashboard')}>Dashboard</Button>
            <Button danger onClick={onSignOut}>Sign Out</Button>
          </>
        ) : (
          <Button type="primary" onClick={() => navigate('/login')}>Sign In</Button>
        )}
      </Space>
    </nav>
  )
}

const FEATURES = [
  { icon: <ClockCircleOutlined style={{ fontSize: 24, color: '#1565C0' }} />, bg: '#eff6ff', title: 'Real-Time Live Data', desc: 'Displays RPM, vehicle speed, engine temperature, engine load, and 15+ sensor parameters continuously updated through an animated gauge interface.' },
  { icon: <WarningOutlined style={{ fontSize: 24, color: '#ea580c' }} />, bg: '#fff7ed', title: 'Read & Clear Fault Codes (DTC)', desc: 'Scan and decode OBD2 fault codes (P/C/B/U codes) from all ECUs. Clear DTCs and reset the Check Engine warning light in a single action.' },
  { icon: <LaptopOutlined style={{ fontSize: 24, color: '#16a34a' }} />, bg: '#f0fdf4', title: 'ECU & VIN Information', desc: 'Read vehicle VIN, ECU software version, and calibration numbers from modules: engine, transmission, ABS, airbag, and BCM.' },
  { icon: <LineChartOutlined style={{ fontSize: 24, color: '#7c3aed' }} />, bg: '#faf5ff', title: 'Data Graph & Logger', desc: 'Plot real-time graphs for multiple parameters simultaneously. Log data to CSV files for offline analysis after a test drive.' },
  { icon: <ToolOutlined style={{ fontSize: 24, color: '#dc2626' }} />, bg: '#fef2f2', title: 'Actuator Tests', desc: 'Activate and test actuators: EGR valve, fuel pump, electric fan, ABS system — via UDS Mode 31 (Routine Control).' },
  { icon: <ApiOutlined style={{ fontSize: 24, color: '#475569' }} />, bg: '#f8fafc', title: 'Raw Frame Monitor', desc: 'View raw CAN bytes side-by-side with decoded results — a debug tool for engineers to verify the correctness of the data decoding pipeline.', badge: 'Admin only' },
]

const PIPELINE = [
  { emoji: '🚗', label: 'Vehicle', sub: 'CAN Bus\n250/500 kbps', color: '#1565C0', bg: '#eff6ff' },
  { emoji: '🔌', label: 'MCP2515', sub: 'CAN Controller\nSPI interface', color: '#ea580c', bg: '#fff7ed' },
  { emoji: '⚙️', label: 'STM32', sub: 'Frame protocol\nBinary framing', color: '#16a34a', bg: '#f0fdf4' },
  { emoji: '🔗', label: 'CP2102', sub: 'USB-UART Bridge\nSilicon Labs', color: '#7c3aed', bg: '#faf5ff' },
  { emoji: '📱', label: 'Android App', sub: 'USB Host Mode\nDecode + Display', color: '#1565C0', bg: '#eff6ff' },
]

const TECH = [
  { emoji: '🤖', name: 'Android', desc: 'Kotlin · Jetpack Compose\nMaterial 3 · API 24+' },
  { emoji: '☁️', name: 'Supabase', desc: 'Auth · PostgreSQL\nUser management' },
  { emoji: '🔌', name: 'USB Serial', desc: 'usb-serial-for-android\nCP2102 · UART' },
  { emoji: '📡', name: 'CAN Bus', desc: 'ISO 15765-4\nSAE J1979 · UDS' },
]

const TEAM = [
  { name: 'Đoàn Anh Kiệt', id: '2211716', role: 'Team Leader\nAndroid app · Protocol', avatar: 'https://i.ibb.co/Lz2Rx3Z7/avt-k.jpg', isImg: true },
  { name: 'Trần Phan Duy', id: '22xxxxxx', role: 'Member\nSTM32 · Hardware', initial: 'B', bg: '#1d4ed8' },
  { name: 'Trương Việt Hoàng', id: '22xxxxxx', role: 'Member\nOBD2 Protocol · Testing', initial: 'C', bg: '#4f46e5' },
]

export default function LandingPage() {
  const { session, profile, logout } = useAuth()
  const navigate = useNavigate()

  async function handleSignOut() { await logout(); navigate('/') }

  return (
    <div style={{ fontFamily: "'Inter', sans-serif", background: '#fff', color: '#1f2937' }}>
      <Navbar session={session} profile={profile} onSignOut={handleSignOut} />

      {/* Hero */}
      <section style={{ paddingTop: 128, paddingBottom: 96, background: 'linear-gradient(135deg,#0A1E6E 0%,#1565C0 50%,#1E88E5 100%)', color: '#fff' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto', padding: '0 24px', display: 'flex', alignItems: 'center', gap: 48, flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 280 }}>
            <Tag style={{ background: 'rgba(255,255,255,0.15)', border: 'none', color: 'rgba(255,255,255,0.9)', fontWeight: 700, letterSpacing: 2, marginBottom: 20, borderRadius: 20, padding: '4px 12px', fontSize: 11 }}>
              AUTOMOTIVE ENGINEERING PROJECT · HCMUT
            </Tag>
            <Title level={1} style={{ color: '#fff', fontSize: 48, lineHeight: 1.1, margin: '0 0 20px' }}>
              Intelligent Vehicle<br /><span style={{ color: '#bfdbfe' }}>Diagnostic System</span>
            </Title>
            <Paragraph style={{ color: 'rgba(255,255,255,0.8)', fontSize: 17, lineHeight: 1.7, marginBottom: 32, maxWidth: 500 }}>
              Android application that reads CAN bus data directly from vehicles through the{' '}
              <strong style={{ color: '#fff' }}>MCP2515 → STM32 → CP2102</strong> hardware interface,
              supporting OBD2, Ford UDS Mode&nbsp;22, and other OEM protocols.
            </Paragraph>
            <Space size={12}>
              <Button size="large" style={{ background: '#fff', color: '#003291', fontWeight: 700, border: 'none', borderRadius: 12 }} onClick={() => document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' })}>Explore Features</Button>
              <Button size="large" ghost style={{ borderRadius: 12, fontWeight: 600 }} onClick={() => document.getElementById('hardware')?.scrollIntoView({ behavior: 'smooth' })}>View Hardware Architecture</Button>
            </Space>
          </div>
          {/* Phone mockup */}
          <div style={{ width: 220, flexShrink: 0 }}>
            <div style={{ background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.2)', borderRadius: 28, padding: 16 }}>
              <div style={{ background: '#111827', borderRadius: 20, padding: 12, aspectRatio: '9/18', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 10, fontWeight: 600 }}>Ford Ranger · Live Data</Text>
                  <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#4ade80' }} />
                </div>
                <div style={{ background: 'rgba(107,114,128,0.4)', borderRadius: 12, padding: 12, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                  <div style={{ width: 64, height: 32, borderTop: 0, borderLeft: '4px solid #60a5fa', borderRight: '4px solid #60a5fa', borderBottom: '4px solid transparent', borderRadius: '50% 50% 0 0 / 100% 100% 0 0' }} />
                  <Text style={{ color: '#93c5fd', fontSize: 11, fontWeight: 700, marginTop: 4 }}>1,724 RPM</Text>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, flex: 1 }}>
                  {[['Speed','62 km/h','#86efac'],['Temp','91 °C','#fde047'],['Engine Load','43 %','#93c5fd'],['Throttle','28 %','#d8b4fe']].map(([l,v,c]) => (
                    <div key={l} style={{ background: 'rgba(107,114,128,0.4)', borderRadius: 8, padding: 8 }}>
                      <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: 9 }}>{l}</Text>
                      <div style={{ color: c, fontSize: 13, fontWeight: 700, marginTop: 4 }}>{v}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Stats Bar */}
      <section style={{ background: '#003291', padding: '32px 24px' }}>
        <Row justify="center" gutter={[32, 16]} style={{ maxWidth: 1152, margin: '0 auto' }}>
          {[['8+','Supported Brands'],['50+','Sensors & Parameters'],['500ms','Data Update Cycle'],['OBD2','ISO 15765-4 / SAE J1979']].map(([v, l]) => (
            <Col key={l} style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 32, fontWeight: 900, color: '#bfdbfe' }}>{v}</div>
              <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13 }}>{l}</Text>
            </Col>
          ))}
        </Row>
      </section>

      {/* Features */}
      <section id="features" style={{ padding: '80px 24px', background: '#f9fafb' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 56 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>Capabilities</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Full Diagnostic Feature Set</Title>
            <Text type="secondary">From real-time data streaming to fault code analysis and actuator testing</Text>
          </div>
          <Row gutter={[24, 24]}>
            {FEATURES.map(({ icon, bg, title, desc, badge }) => (
              <Col xs={24} md={8} key={title}>
                <Card hoverable style={{ borderRadius: 20, height: '100%', border: '1px solid #f0f0f0' }}>
                  <div style={{ width: 48, height: 48, background: bg, borderRadius: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>{icon}</div>
                  <Title level={5} style={{ margin: '0 0 8px' }}>{title}</Title>
                  <Text type="secondary" style={{ fontSize: 13, lineHeight: 1.7 }}>{desc}</Text>
                  {badge && <Tag style={{ marginTop: 8, borderRadius: 20, background: '#f1f5f9', color: '#64748b', border: 'none', fontSize: 11, fontWeight: 600 }}>{badge}</Tag>}
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      </section>

      {/* Hardware Pipeline */}
      <section id="hardware" style={{ padding: '80px 24px', background: '#fff' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 56 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>System Architecture</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Data Transmission Pipeline</Title>
            <Text type="secondary">CAN bus data from the vehicle passes through a hardware chain before being processed and displayed by the app</Text>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flexWrap: 'wrap', gap: 0 }}>
            {PIPELINE.map(({ emoji, label, sub, color, bg }, i) => (
              <div key={label} style={{ display: 'flex', alignItems: 'center' }}>
                <div style={{ border: `2px solid ${color}`, background: bg, borderRadius: 20, padding: 20, width: 130, textAlign: 'center' }}>
                  <div style={{ fontSize: 28, marginBottom: 6 }}>{emoji}</div>
                  <Text strong style={{ color, display: 'block', fontSize: 13 }}>{label}</Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>{sub.split('\n').map((t, j) => <span key={j}>{t}<br /></span>)}</Text>
                </div>
                {i < PIPELINE.length - 1 && <div style={{ width: 32, height: 1, background: '#d1d5db', flexShrink: 0 }} />}
              </div>
            ))}
          </div>
          <Row gutter={[24, 24]} style={{ marginTop: 48 }}>
            <Col xs={24} md={8}>
              <div style={{ background: '#f9fafb', borderRadius: 14, padding: 20, border: '1px solid #f0f0f0' }}>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>Frame Protocol</Text>
                <code style={{ fontSize: 12, color: '#4b5563', background: '#fff', display: 'block', padding: 12, borderRadius: 8, border: '1px solid #e5e7eb', lineHeight: 1.8 }}>
                  [0xAA][TYPE][LEN]<br />[PAYLOAD...][XOR][0x55]
                </code>
                <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>Binary framing with XOR checksum ensures data integrity</Text>
              </div>
            </Col>
            <Col xs={24} md={8}>
              <div style={{ background: '#f9fafb', borderRadius: 14, padding: 20, border: '1px solid #f0f0f0' }}>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>Supported Protocols</Text>
                {[['#60a5fa','OBD2 Mode 01 — Live Data (standard)'],['#4ade80','OBD2 Mode 03/04 — DTC Read/Clear'],['#fb923c','Ford UDS Mode 22 — Proprietary PIDs'],['#d1d5db','More OEMs (Toyota, Hyundai…)']].map(([c, t]) => (
                  <div key={t} style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: c, flexShrink: 0 }} />
                    <Text style={{ fontSize: 12 }}>{t}</Text>
                  </div>
                ))}
              </div>
            </Col>
            <Col xs={24} md={8}>
              <div style={{ background: '#f9fafb', borderRadius: 14, padding: 20, border: '1px solid #f0f0f0' }}>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>Ford — Proprietary PIDs</Text>
                {['Turbo Boost (DID 0x1046)','DPF Pressure (DID 0x1030)','EGR Rate (DID 0x2EF1)','Trans. Temp (DID 0x1940)'].map(t => (
                  <div key={t} style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#60a5fa', flexShrink: 0 }} />
                    <Text style={{ fontSize: 12 }}>{t}</Text>
                  </div>
                ))}
              </div>
            </Col>
          </Row>
        </div>
      </section>

      {/* Technology Stack */}
      <section id="technology" style={{ padding: '80px 24px', background: '#f9fafb' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 48 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>Tech Stack</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Technologies Used</Title>
          </div>
          <Row gutter={[16, 16]} justify="center">
            {TECH.map(({ emoji, name, desc }) => (
              <Col xs={12} md={6} key={name}>
                <Card hoverable style={{ borderRadius: 16, textAlign: 'center', border: '1px solid #f0f0f0' }}>
                  <div style={{ fontSize: 36, marginBottom: 12 }}>{emoji}</div>
                  <Text strong style={{ fontSize: 15, display: 'block' }}>{name}</Text>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>{desc.split('\n').map((t, i) => <span key={i}>{t}<br /></span>)}</Text>
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      </section>

      {/* Team */}
      <section id="team" style={{ padding: '80px 24px', background: '#fff' }}>
        <div style={{ maxWidth: 960, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 48 }}>
            <Text style={{ color: '#1565C0', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', letterSpacing: 2 }}>Team</Text>
            <Title level={2} style={{ margin: '8px 0' }}>Project Members</Title>
            <Text type="secondary">Course project — Faculty of Transportation Engineering, HCMUT</Text>
          </div>
          <Row gutter={[24, 24]}>
            {TEAM.map(({ name, id, role: r, avatar, isImg, initial, bg }) => (
              <Col xs={24} md={8} key={name}>
                <Card hoverable style={{ borderRadius: 20, textAlign: 'center', border: '1px solid #f0f0f0' }}>
                  {isImg
                    ? <img src={avatar} alt={name} style={{ width: 80, height: 80, borderRadius: '50%', objectFit: 'cover', border: '2px solid #003291', marginBottom: 16 }} />
                    : <Avatar size={80} style={{ background: bg, fontWeight: 700, fontSize: 32, marginBottom: 16 }}>{initial}</Avatar>
                  }
                  <Title level={5} style={{ margin: 0 }}>{name}</Title>
                  <Text style={{ color: '#1565C0', fontSize: 13, fontWeight: 600, display: 'block', marginTop: 4 }}>ID: {id}</Text>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>{r.split('\n').map((t, i) => <span key={i}>{t}<br /></span>)}</Text>
                </Card>
              </Col>
            ))}
          </Row>
          <div style={{ marginTop: 32, background: '#eff6ff', borderRadius: 16, padding: 20, border: '1px solid #dbeafe', textAlign: 'center' }}>
            <Text style={{ color: '#1565C0', fontSize: 14, fontWeight: 500 }}>
              🎓 Supervisor: <strong>M.S. Phạm Trần Đăng Quang</strong> — Automotive Engineering Department, Faculty of Transportation Engineering
            </Text>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer style={{ background: '#003291', color: '#fff', padding: '48px 24px' }}>
        <div style={{ maxWidth: 1152, margin: '0 auto' }}>
          <Row justify="space-between" gutter={[32, 32]} style={{ marginBottom: 32 }}>
            <Col>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                <img src="https://i.ibb.co/Z0Xc41Z/logo.png" style={{ width: 32, height: 32, borderRadius: 8 }} alt="logo" />
                <Text strong style={{ color: '#fff', fontSize: 17 }}>BK Diagnostic</Text>
              </div>
              <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: 13, maxWidth: 300, display: 'block', lineHeight: 1.7 }}>
                Intelligent vehicle diagnostic system — Automotive Engineering Project<br />
                Ho Chi Minh City University of Technology (HCMUT)
              </Text>
            </Col>
            <Col>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 48px' }}>
                {[['#features','Features'],['#hardware','Hardware'],['#technology','Technology'],['#team','Team']].map(([href, label]) => (
                  <a key={href} href={href} style={{ color: 'rgba(255,255,255,0.7)', fontSize: 14, textDecoration: 'none' }}>{label}</a>
                ))}
                <Link to="/login" style={{ color: 'rgba(255,255,255,0.7)', fontSize: 14 }}>Sign In</Link>
              </div>
            </Col>
          </Row>
          <div style={{ borderTop: '1px solid rgba(255,255,255,0.15)', paddingTop: 24, textAlign: 'center' }}>
            <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>
              © 2026 BK Diagnostic · Ho Chi Minh City University of Technology · Faculty of Transportation Engineering
            </Text>
          </div>
        </div>
      </footer>
    </div>
  )
}
