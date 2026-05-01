import { motion } from 'framer-motion'
import { useInViewAnimation, fadeUp } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'
import contextDiagramSvg from '../../../assets/svg/context-diagram.svg'

export default function Context() {
  const { ref, inView } = useInViewAnimation()

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      className="landing-section"
      style={{ background: 'var(--paper)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow="BỐI CẢNH"
          title="Vì sao cần đồ án này?"
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 48,
          alignItems: 'center',
        }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              Các thiết bị chẩn đoán xe thương mại (Autel, Launch, Bosch ESI…) có giá thành cao
              và đóng kín mã nguồn. Sinh viên ngành Kỹ thuật Ô tô khó tiếp cận tầng thấp của giao
              thức CAN, không thể quan sát trực tiếp khung dữ liệu thô hay can thiệp vào thuật
              toán giải mã.
            </p>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              Đồ án xây dựng một hệ thống mở cho phòng thí nghiệm: phần cứng tự thiết kế dùng
              linh kiện phổ thông, mã nguồn được cung cấp đầy đủ, đi kèm tài liệu thực hành 6
              buổi học từ cơ bản đến nâng cao.
            </p>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              Phạm vi triển khai bao gồm xe Ford Ranger 2.0L Bi-Turbo (2018–2024) làm đối tượng
              thực nghiệm, hỗ trợ chuẩn OBD-II và mở rộng sang UDS Mode 22 với các DID đặc thù
              của Ford.
            </p>
          </div>

          <figure style={{ margin: 0, textAlign: 'center' }}>
            <img src={contextDiagramSvg} alt="Sơ đồ bối cảnh: Vấn đề → Giải pháp → Mục tiêu"
              style={{ maxWidth: '100%', height: 'auto' }} />
            <figcaption style={{
              marginTop: 12,
              fontSize: 12,
              fontStyle: 'italic',
              color: 'var(--gold-500)',
              fontWeight: 500,
            }}>
              Hình 2 — Sơ đồ bối cảnh: Vấn đề → Giải pháp → Mục tiêu.
            </figcaption>
          </figure>
        </div>
      </div>
    </motion.section>
  )
}
