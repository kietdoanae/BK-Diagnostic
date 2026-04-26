import { Button } from 'antd'
import PlaceholderImage from '../shared/PlaceholderImage'
import heroBgSvg from '../../../assets/svg/hero-bg-mesh.svg'

function scrollTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

export default function Hero() {
  return (
    <section id="tong-quan" style={{
      position: 'relative',
      paddingTop: 128,
      paddingBottom: 64,
      backgroundImage: `url(${heroBgSvg})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
    }}>
      <div className="landing-container" style={{ textAlign: 'center' }}>
        <div style={{
          display: 'inline-block',
          background: 'var(--bk-blue-100)',
          color: 'var(--bk-navy-700)',
          padding: '6px 16px',
          borderRadius: 20,
          fontSize: 11,
          fontWeight: 700,
          letterSpacing: 2,
          marginBottom: 24,
        }}>
          ĐỒ ÁN TỐT NGHIỆP · HCMUT · 2026
        </div>

        <h1 style={{
          fontSize: 'clamp(36px, 6vw, 56px)',
          fontWeight: 800,
          lineHeight: 1.15,
          margin: '0 0 20px',
          color: 'var(--ink-900)',
          letterSpacing: '-0.02em',
          maxWidth: 800,
          marginLeft: 'auto',
          marginRight: 'auto',
        }}>
          Hệ thống chẩn đoán xe<br />và đào tạo CAN bus
        </h1>

        <p style={{
          fontSize: 'clamp(15px, 2vw, 18px)',
          lineHeight: 1.7,
          color: 'var(--ink-700)',
          maxWidth: 720,
          margin: '0 auto 32px',
        }}>
          Nền tảng tích hợp ba thành phần — phần cứng nhúng, ứng dụng Android và web platform —
          phục vụ giảng dạy giao thức CAN bus và quy trình chẩn đoán xe ô tô cho sinh viên
          ngành Kỹ thuật Ô tô.
        </p>

        <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 56 }}>
          <Button
            type="primary"
            size="large"
            onClick={() => scrollTo('kien-truc')}
            style={{ borderRadius: 'var(--radius-btn)', fontWeight: 700, height: 48, padding: '0 24px' }}
          >
            Khám phá kiến trúc →
          </Button>
          <Button
            size="large"
            onClick={() => scrollTo('lab')}
            style={{ borderRadius: 'var(--radius-btn)', fontWeight: 600, height: 48, padding: '0 24px' }}
          >
            Xem hệ thống Lab
          </Button>
        </div>

        <PlaceholderImage
          path="hero/sa-ban-overview.jpg"
          alt="Sa bàn thực hành CAN bus tại phòng lab Bộ môn Kỹ thuật Ô tô"
          ratio="16/9"
          caption="Hình 1 — Sa bàn thực hành CAN bus tại phòng lab Bộ môn Kỹ thuật Ô tô, Khoa Kỹ thuật Giao thông HCMUT."
        />
      </div>
    </section>
  )
}
