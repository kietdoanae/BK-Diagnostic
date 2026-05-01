import { motion } from 'framer-motion'
import { useInViewAnimation, fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import SectionHeader from '../shared/SectionHeader'

const STEPS = [
  { num: 1, icon: '👨‍🏫', title: 'GV TẠO SESSION',  desc: 'Giảng viên chọn Lab, mở phiên mới, hệ thống sinh mã 6 ký tự.' },
  { num: 2, icon: '🎓', title: 'SV THAM GIA',     desc: 'Sinh viên nhập mã session trên app/web bằng tài khoản MSSV.' },
  { num: 3, icon: '📝', title: 'PRE-QUIZ',        desc: '10 câu trắc nghiệm kiểm tra kiến thức nền (5 phút).' },
  { num: 4, icon: '🔧', title: 'THỰC HÀNH',       desc: 'Cắm cáp USB → mạch → xe. Làm theo hướng dẫn từng bước, evidence gửi real-time về dashboard.' },
  { num: 5, icon: '✅', title: 'POST-LAB',        desc: '5 câu rút kinh nghiệm sau buổi học (3 phút).' },
  { num: 6, icon: '📄', title: 'BÁO CÁO PDF',     desc: 'Hệ thống sinh báo cáo gồm pre/post score, evidence, chữ ký số. Lưu vào "Báo cáo của tôi" và DB của GV.' },
]

export default function UseCase() {
  const { ref, inView } = useInViewAnimation()

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      id="lab"
      className="landing-section"
      style={{ background: 'var(--paper-soft)' }}
    >
      <div className="landing-container">
        <SectionHeader
          eyebrow="USE CASE"
          title="Một buổi học diễn ra thế nào"
          sub="Quy trình 6 bước chuẩn hóa, từ lúc giảng viên mở phiên đến khi báo cáo PDF được lưu vào hệ thống."
        />

        <motion.div
          variants={fadeUpStagger}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.3 }}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
            gap: 16,
          }}
        >
          {STEPS.map((s, i) => (
            <motion.div key={s.num} variants={fadeUpItem} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              padding: 20,
              border: '1px solid var(--rule)',
              position: 'relative',
              textAlign: 'center',
            }}>
              <div style={{
                width: 36, height: 36,
                borderRadius: '50%',
                background: 'var(--bk-navy-700)',
                color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontWeight: 800, fontSize: 14,
                margin: '0 auto 12px',
              }}>{s.num}</div>
              <div style={{ fontSize: 28, marginBottom: 8 }}>{s.icon}</div>
              <h4 style={{
                margin: '0 0 8px',
                fontSize: 12,
                color: 'var(--bk-navy-700)',
                fontWeight: 800,
                letterSpacing: 1,
              }}>{s.title}</h4>
              <p style={{
                fontSize: 12,
                color: 'var(--ink-500)',
                lineHeight: 1.5,
                margin: 0,
              }}>{s.desc}</p>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </motion.section>
  )
}
