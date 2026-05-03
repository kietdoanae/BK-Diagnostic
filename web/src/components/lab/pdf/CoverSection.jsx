// Cover page — HCMUT header + course + lab + student + group + dates.
// Pure presentational. All data arrives via props; no hooks.

function formatVN(dt) {
  if (!dt) return '—'
  const d = typeof dt === 'string' ? new Date(dt) : dt
  return d.toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export default function CoverSection({ lab, student, group, session, postSubmission }) {
  const practiceDate = session?.started_at ? formatVN(session.started_at) : '—'
  const submittedAt = postSubmission?.submitted_at
    ? formatVN(postSubmission.submitted_at)
    : '—'

  return (
    <section className="page cover">
      <img className="cover-logo" src="/logo.png" alt="HCMUT" />
      <div style={{ fontSize: '11pt' }}>
        ĐẠI HỌC QUỐC GIA TP.HCM · TRƯỜNG ĐẠI HỌC BÁCH KHOA
      </div>
      <div style={{ fontSize: '11pt', marginBottom: '10mm' }}>
        KHOA KỸ THUẬT GIAO THÔNG
      </div>

      <h1>BÁO CÁO THỰC HÀNH</h1>
      <div style={{ fontSize: '14pt', marginTop: '4mm' }}>
        TR4021 — Chẩn đoán ô tô
      </div>
      <div style={{ fontSize: '14pt', marginTop: '2mm', fontWeight: 700 }}>
        {lab?.code} · {lab?.title}
      </div>

      <div className="cover-meta">
        <table style={{ fontFamily: '"Times New Roman", serif', fontSize: '12pt', border: 0 }}>
          <tbody>
            <tr><td style={{ border: 0, width: '40mm' }}>Sinh viên</td>
                <td style={{ border: 0 }}><strong>{student?.full_name || '—'}</strong></td></tr>
            <tr><td style={{ border: 0 }}>MSSV</td>
                <td style={{ border: 0 }}>{student?.mssv || '—'}</td></tr>
            <tr><td style={{ border: 0 }}>Nhóm</td>
                <td style={{ border: 0 }}>{group?.name || '—'}</td></tr>
            <tr><td style={{ border: 0 }}>Học kỳ</td>
                <td style={{ border: 0 }}>{group?.semester || '—'}</td></tr>
            <tr><td style={{ border: 0 }}>Ngày thực hành</td>
                <td style={{ border: 0 }}>{practiceDate}</td></tr>
            <tr><td style={{ border: 0 }}>Nộp báo cáo</td>
                <td style={{ border: 0 }}>{submittedAt}</td></tr>
            <tr><td style={{ border: 0 }}>Mã session</td>
                <td style={{ border: 0 }}><code>{session?.session_code || '—'}</code></td></tr>
          </tbody>
        </table>
      </div>
    </section>
  )
}
