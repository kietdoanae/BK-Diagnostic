import { useState } from 'react'
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const FOR_TEACHER = [
  'Tạo Lab và Session — Cấu hình bài học, mở phiên cho lớp',
  'Quản lý nhóm sinh viên — Tạo nhóm, gán SV vào nhóm',
  'Theo dõi real-time — Xem evidence của SV trong khi học',
  'Review submission — Chấm điểm pre/post-quiz',
  'Tải báo cáo PDF — Export báo cáo cuối buổi',
]

const FOR_ADMIN = [
  'Quản lý người dùng — Tạo/khóa tài khoản, gán role',
  'Activity Logs live — Stream log đăng nhập, thao tác CAN',
  'CSV Exports archive — Tải file CAN log do app upload',
  'Wiring diagram — Sơ đồ đấu nối tham khảo',
]

const TABS = [
  { key: 'labs',     label: 'Labs',     img: 'web/dashboard-labs.png' },
  { key: 'groups',   label: 'Groups',   img: 'web/dashboard-groups.png' },
  { key: 'sessions', label: 'Sessions', img: 'web/dashboard-sessions.png' },
  { key: 'exports',  label: 'Exports',  img: 'web/dashboard-exports.png' },
  { key: 'logs',     label: 'Logs',     img: 'web/dashboard-logs.png' },
]

export default function WebPillar() {
  const [activeTab, setActiveTab] = useState('sessions')
  const tab = TABS.find(t => t.key === activeTab) ?? TABS[2]

  return (
    <section id="web" className="landing-section" style={{ background: 'var(--paper)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TRỤ CỘT 3 · WEB PLATFORM"
          title="Cổng quản trị & học liệu trực tuyến"
          sub="Web app dành cho giảng viên tổ chức buổi học, theo dõi tiến trình và xuất báo cáo; dành cho admin quản lý người dùng, log hoạt động và file CSV xuất từ thiết bị."
          align="left"
        />

        {/* Hàng trên: Screenshot + 2 group bullet */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '5fr 7fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 48,
        }} className="responsive-grid">
          <PlaceholderImage
            path="web/dashboard-sessions.png"
            alt="Trang Admin Dashboard, tab quản lý phiên học"
            ratio="16/10"
            caption="Hình 6 — Trang Admin Dashboard, tab quản lý phiên học."
          />

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{
              margin: '0 0 12px',
              fontSize: 13,
              color: 'var(--bk-blue-500)',
              letterSpacing: 2,
              fontWeight: 800,
            }}>CHO GIẢNG VIÊN</h3>
            <ul style={{ margin: '0 0 24px', padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {FOR_TEACHER.map((t, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{t}</span>
                </li>
              ))}
            </ul>

            <div style={{ height: 1, background: 'var(--rule)', margin: '12px 0 24px' }} />

            <h3 style={{
              margin: '0 0 12px',
              fontSize: 13,
              color: 'var(--bk-blue-500)',
              letterSpacing: 2,
              fontWeight: 800,
            }}>CHO ADMIN</h3>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {FOR_ADMIN.map((t, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{t}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Tab showcase */}
        <div style={{
          background: 'var(--paper-soft)',
          borderRadius: 'var(--radius-card)',
          padding: 24,
          border: '1px solid var(--rule)',
        }}>
          <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--rule)', marginBottom: 24, overflowX: 'auto' }}>
            {TABS.map(t => (
              <button
                key={t.key}
                onClick={() => setActiveTab(t.key)}
                style={{
                  padding: '12px 20px',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: 14,
                  fontWeight: activeTab === t.key ? 700 : 500,
                  color: activeTab === t.key ? 'var(--bk-navy-700)' : 'var(--ink-500)',
                  borderBottom: activeTab === t.key ? '2px solid var(--bk-navy-700)' : '2px solid transparent',
                  transition: 'all 200ms ease-out',
                  whiteSpace: 'nowrap',
                }}
              >{t.label}</button>
            ))}
          </div>

          <PlaceholderImage path={tab.img} alt={`Tab ${tab.label}`} ratio="16/9" />
        </div>
      </div>
    </section>
  )
}
