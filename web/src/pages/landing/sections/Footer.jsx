import { Link } from 'react-router-dom'
import logoSvg from '../../../assets/svg/bk-diagnostic-logo.svg'

const NAV_LINKS = [
  ['#tong-quan',  'Tổng quan'],
  ['#kien-truc',  'Kiến trúc'],
  ['#mobile',     'Mobile App'],
  ['#web',        'Web Platform'],
  ['#lab',        'Lab System'],
  ['#team',       'Team'],
]

export default function Footer() {
  return (
    <footer style={{
      background: 'var(--bk-navy-900)',
      color: '#fff',
      padding: '56px 24px 32px',
    }}>
      <div className="landing-container">
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 32,
          marginBottom: 32,
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <img src={logoSvg} alt="BK Diagnostic" style={{ width: 36, height: 36 }} />
              <span style={{ fontWeight: 700, fontSize: 17, color: '#fff' }}>BK Diagnostic</span>
            </div>
            <p style={{
              fontSize: 13,
              color: 'rgba(255,255,255,0.65)',
              lineHeight: 1.7,
              margin: 0,
              maxWidth: 280,
            }}>
              Hệ thống chẩn đoán xe và đào tạo CAN bus.<br />
              Đồ án tốt nghiệp ngành Kỹ thuật Ô tô.
            </p>
          </div>

          <div>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>Điều hướng</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {NAV_LINKS.map(([href, label]) => (
                <li key={href}>
                  <a href={href} style={{
                    color: 'rgba(255,255,255,0.75)',
                    fontSize: 14,
                    textDecoration: 'none',
                  }}>{label}</a>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>Liên kết</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <li><Link to="/login" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Đăng nhập</Link></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Tài liệu kỹ thuật</a></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Source code</a></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Liên hệ</a></li>
            </ul>
          </div>

          <div>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>Trường</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>ĐH Bách khoa TP.HCM</li>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>Khoa Kỹ thuật Giao thông</li>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>Bộ môn Kỹ thuật Ô tô</li>
            </ul>
          </div>
        </div>

        <div style={{
          borderTop: '1px solid rgba(255,255,255,0.15)',
          paddingTop: 20,
          textAlign: 'center',
          fontSize: 12,
          color: 'rgba(255,255,255,0.5)',
        }}>
          © 2026 BK Diagnostic · Đồ án tốt nghiệp · Trường ĐH Bách khoa, ĐHQG-HCM · Khoa Kỹ thuật Giao thông
        </div>
      </div>
    </footer>
  )
}
