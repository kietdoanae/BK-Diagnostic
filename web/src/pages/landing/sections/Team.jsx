import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const MEMBERS = [
  {
    name: 'Đoàn Anh Kiệt',
    mssv: '2211716',
    role: 'Trưởng nhóm',
    work: 'Mobile App · Giao thức UART',
    avatar: 'team/kiet.jpg',
  },
  {
    name: 'Trần Phan Duy',
    mssv: '22xxxxxx',
    role: 'Thành viên',
    work: 'Phần cứng · Firmware STM32',
    avatar: 'team/duy.jpg',
  },
  {
    name: 'Trương Việt Hoàng',
    mssv: '22xxxxxx',
    role: 'Thành viên',
    work: 'Giao thức OBD2 · Kiểm thử hệ thống',
    avatar: 'team/hoang.jpg',
  },
]

export default function Team() {
  return (
    <section id="team" className="landing-section" style={{ background: 'var(--paper-soft)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TEAM"
          title="Nhóm thực hiện đồ án"
          sub="Đồ án tốt nghiệp năm học 2025–2026, Bộ môn Kỹ thuật Ô tô, Khoa Kỹ thuật Giao thông, ĐH Bách khoa TP.HCM."
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 24,
          marginBottom: 32,
        }}>
          {MEMBERS.map((m, i) => (
            <div key={i} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              padding: 24,
              border: '1px solid var(--rule)',
              textAlign: 'center',
            }}>
              <div style={{ width: 80, height: 80, margin: '0 auto 12px' }}>
                <PlaceholderImage path={m.avatar} alt={m.name} ratio="1/1" />
              </div>
              <h3 style={{ margin: '8px 0 4px', fontSize: 16, color: 'var(--ink-900)' }}>{m.name}</h3>
              <div style={{
                color: 'var(--bk-blue-500)',
                fontSize: 13,
                fontWeight: 700,
                marginBottom: 8,
              }}>MSSV: {m.mssv}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-500)', lineHeight: 1.6 }}>
                {m.role}<br />{m.work}
              </div>
            </div>
          ))}
        </div>

        <div style={{
          background: 'var(--bk-blue-100)',
          borderLeft: '4px solid var(--bk-navy-700)',
          borderRadius: 'var(--radius-card)',
          padding: '20px 24px',
        }}>
          <div style={{ fontSize: 13, color: 'var(--bk-navy-700)', fontWeight: 700, marginBottom: 4 }}>
            🎓 Giảng viên hướng dẫn
          </div>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink-900)', marginBottom: 4 }}>
            ThS. Phạm Trần Đăng Quang
          </div>
          <div style={{ fontSize: 13, color: 'var(--ink-500)' }}>
            Bộ môn Kỹ thuật Ô tô · Khoa Kỹ thuật Giao thông · Trường Đại học Bách khoa, ĐHQG-HCM
          </div>
        </div>
      </div>
    </section>
  )
}
