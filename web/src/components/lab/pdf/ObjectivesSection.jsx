import MDEditor from '@uiw/react-md-editor'

// Static L.O. mapping per spec Section 1.
// (If later you want per-lab L.O. mapping, move this table to a DB column and
//  pass it as a prop. For now, one static map is enough for the 2 pilot labs.)
const LO_MAP = {
  LAB01: [
    { code: 'L.O.2', desc: 'Giải thích cấu trúc khung CAN và các lệnh OBD-II chuẩn.' },
    { code: 'L.O.5', desc: 'Sử dụng công cụ chẩn đoán để đọc PID và DTC.' },
  ],
  LAB02: [
    { code: 'L.O.3', desc: 'Diễn giải dữ liệu cảm biến live và các mã lỗi hệ thống.' },
    { code: 'L.O.6', desc: 'Thực hiện active test an toàn và phân tích kết quả.' },
  ],
}

export default function ObjectivesSection({ lab }) {
  const mapping = LO_MAP[lab?.code] || []
  return (
    <section className="page">
      <h2>1. Mục tiêu bài thực hành</h2>
      <div data-color-mode="light">
        <MDEditor.Markdown source={lab?.description || '_(Không có mô tả)_'} />
      </div>

      <h3>1.1. Liên hệ chuẩn đầu ra (Learning Outcomes)</h3>
      {mapping.length === 0 ? (
        <p className="muted">Chưa khai báo L.O. mapping cho lab này.</p>
      ) : (
        <table>
          <thead>
            <tr><th style={{ width: '25mm' }}>Mã L.O.</th><th>Mô tả</th></tr>
          </thead>
          <tbody>
            {mapping.map((m) => (
              <tr key={m.code}>
                <td><strong>{m.code}</strong></td>
                <td>{m.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
