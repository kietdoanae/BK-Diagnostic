export default function DeclarationSection({ student }) {
  const today = new Date().toLocaleDateString('vi-VN')
  return (
    <section className="page">
      <h2>5. Cam kết của sinh viên</h2>
      <p>
        Tôi xin cam đoan báo cáo này do chính tôi thực hiện trong phiên thực
        hành đã ghi nhận ở trên. Dữ liệu bằng chứng (raw frames, DTC, ảnh chụp
        màn hình) được thu thập trực tiếp từ thiết bị BK Diagnostic và không bị
        chỉnh sửa. Các câu trả lời phân tích là của cá nhân tôi. Nếu phát hiện
        sao chép hoặc ngụy tạo dữ liệu, tôi hoàn toàn chịu trách nhiệm và chấp
        nhận bị hủy kết quả.
      </p>

      <div className="declaration-sig">
        <div className="sig-box">
          <div>Ngày {today}</div>
          <div className="sig-line" />
          <div><strong>{student?.full_name || '—'}</strong></div>
          <div>MSSV: {student?.mssv || '—'}</div>
        </div>
      </div>
    </section>
  )
}
