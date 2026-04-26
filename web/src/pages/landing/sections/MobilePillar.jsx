import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const FEATURES = [
  ['⏱', 'Live Data', 'Hiển thị 50+ thông số sensor cập nhật mỗi 500 ms, biểu diễn dạng gauge và biểu đồ thời gian thực.'],
  ['⚠', 'Mã lỗi DTC', 'Quét và giải mã mã lỗi P/C/B/U từ tất cả ECU, xóa DTC và tắt đèn Check Engine.'],
  ['💻', 'Thông tin ECU', 'Đọc VIN, phiên bản phần mềm và mã hiệu chuẩn từ các module: động cơ, hộp số, ABS, túi khí, BCM.'],
  ['📈', 'Data Logger', 'Ghi log dữ liệu ra file CSV để phân tích offline sau khi chạy thử nghiệm.'],
  ['🔧', 'Actuator Test', 'Kích hoạt cơ cấu chấp hành (van EGR, bơm nhiên liệu, quạt điện…) qua UDS Mode 31.'],
  ['📡', 'Raw CAN Monitor', 'Xem byte CAN thô song song với kết quả giải mã, công cụ debug cho kỹ sư.'],
]

const LAB_STEPS = [
  { num: '1', label: 'Pre-quiz', sub: '10 câu, 5 phút' },
  { num: '2', label: 'Thực hành', sub: '15-30 phút' },
  { num: '3', label: 'Post-lab', sub: '5 câu, 3 phút' },
  { num: '4', label: 'Báo cáo PDF', sub: 'sinh tự động' },
]

export default function MobilePillar() {
  return (
    <section id="mobile" className="landing-section" style={{ background: 'var(--paper-soft)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TRỤ CỘT 2 · MOBILE APP"
          title="Ứng dụng Android chẩn đoán & học tập"
          sub="Giao tiếp với mạch CAN qua USB-OTG, hiển thị live data, đọc mã lỗi và hỗ trợ chế độ Lab cho lớp học."
          align="left"
        />

        {/* Hàng trên: Tính năng + 3 screenshot */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '7fr 5fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 48,
        }} className="responsive-grid">
          <div style={{
            background: 'var(--paper)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>Tính năng chính</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {FEATURES.map(([icon, title, desc], i) => (
                <div key={i} style={{ display: 'flex', gap: 12 }}>
                  <span style={{ fontSize: 22, flexShrink: 0, lineHeight: 1 }}>{icon}</span>
                  <div>
                    <strong style={{ color: 'var(--ink-900)', fontSize: 14, display: 'block', marginBottom: 2 }}>
                      {title}
                    </strong>
                    <span style={{ color: 'var(--ink-500)', fontSize: 13, lineHeight: 1.6 }}>
                      {desc}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <PlaceholderImage path="app/screen-live-data.png"  alt="Live Dashboard" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-dtc-list.png"   alt="DTC List" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-lab-session.png" alt="Lab Session" ratio="9/19.5"
              caption="Hình 5 — Ba màn hình chính của ứng dụng." />
          </div>
        </div>

        {/* Lab Mode sub-section */}
        <div style={{
          background: 'var(--bk-blue-100)',
          borderRadius: 'var(--radius-card)',
          padding: 32,
          border: '1px solid var(--rule)',
        }}>
          <h3 style={{
            margin: '0 0 8px',
            fontSize: 20,
            color: 'var(--bk-navy-700)',
          }}>Lab Mode — Chế độ học tập</h3>
          <p style={{ fontSize: 14, color: 'var(--ink-700)', lineHeight: 1.7, marginBottom: 24, maxWidth: 720 }}>
            Khi giảng viên kích hoạt một buổi học, ứng dụng tự chuyển sang chế độ Lab. Sinh viên
            đăng nhập bằng MSSV, làm pre-quiz, thực hành theo hướng dẫn, rồi hoàn thành post-lab.
            Toàn bộ thao tác CAN được ghi nhận làm bằng chứng (evidence) gửi real-time về dashboard
            giảng viên. Báo cáo PDF sinh tự động cuối buổi.
          </p>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
            gap: 12,
          }}>
            {LAB_STEPS.map((s, i) => (
              <div key={i} style={{
                background: 'var(--paper)',
                borderRadius: 999,
                padding: '14px 18px',
                display: 'flex',
                alignItems: 'center',
                gap: 12,
              }}>
                <span style={{
                  background: 'var(--bk-navy-700)',
                  color: '#fff',
                  width: 28, height: 28, borderRadius: '50%',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontWeight: 700, fontSize: 13, flexShrink: 0,
                }}>{s.num}</span>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-900)' }}>{s.label}</div>
                  <div style={{ fontSize: 11, color: 'var(--ink-500)' }}>{s.sub}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
