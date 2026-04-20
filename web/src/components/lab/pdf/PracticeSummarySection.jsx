// Session metadata + per-step timing + top-10 CAN IDs + evidence sample
// (3–5 frames per step, NOT a full dump per spec Section 7.1).

function fmt(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('vi-VN')
}

function durationMs(fromIso, toIso) {
  if (!fromIso || !toIso) return null
  const ms = new Date(toIso).getTime() - new Date(fromIso).getTime()
  return ms >= 0 ? ms : null
}

function fmtDuration(ms) {
  if (ms == null) return '—'
  const s = Math.round(ms / 1000)
  const m = Math.floor(s / 60)
  const rem = s % 60
  return `${m}m ${String(rem).padStart(2, '0')}s`
}

function briefPayload(payload) {
  // Shrink a raw frame / active-test / DTC payload to one-line text.
  if (!payload) return ''
  if (payload.data) return `data=${payload.data}`
  if (payload.dtc) return `DTC=${payload.dtc}`
  if (payload.command) return `cmd=${payload.command}`
  return JSON.stringify(payload).slice(0, 60)
}

export default function PracticeSummarySection({ session, steps, evidenceByStep, topCanIds }) {
  return (
    <section className="page">
      <h2>3. Tổng kết phiên thực hành</h2>
      <table>
        <tbody>
          <tr><th style={{ width: '40mm' }}>Mã session</th>
              <td><code>{session?.session_code || '—'}</code></td></tr>
          <tr><th>Trạng thái</th><td>{session?.status || '—'}</td></tr>
          <tr><th>Bắt đầu</th><td>{fmt(session?.started_at)}</td></tr>
          <tr><th>Kết thúc</th><td>{fmt(session?.ended_at)}</td></tr>
          <tr><th>Thời lượng</th>
              <td>{fmtDuration(durationMs(session?.started_at, session?.ended_at))}</td></tr>
        </tbody>
      </table>

      <h3>3.1. Bảng thời gian theo bước</h3>
      <table>
        <thead>
          <tr>
            <th style={{ width: '8mm' }}>#</th>
            <th>Bước</th>
            <th style={{ width: '22mm' }}>Loại evidence</th>
            <th style={{ width: '22mm' }}>Yêu cầu</th>
            <th style={{ width: '22mm' }}>Thu được</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((st) => {
            const count = (evidenceByStep[st.id] || []).length
            return (
              <tr key={st.id}>
                <td>{st.step_order}</td>
                <td><strong>{st.title}</strong></td>
                <td>{st.evidence_type}</td>
                <td>{st.required_count ?? '—'}</td>
                <td>{count}</td>
              </tr>
            )
          })}
        </tbody>
      </table>

      <h3>3.2. Top-10 CAN ID quan sát được</h3>
      {topCanIds.length === 0 ? (
        <p className="muted">Không ghi nhận khung CAN.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th style={{ width: '30mm' }}>CAN ID</th>
              <th style={{ width: '22mm' }}>Số khung</th>
              <th>Mẫu payload</th>
            </tr>
          </thead>
          <tbody>
            {topCanIds.map((c) => (
              <tr key={c.canId}>
                <td><code>{c.canId}</code></td>
                <td>{c.count}</td>
                <td><code>{briefPayload(c.sample)}</code></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3>3.3. Mẫu evidence theo bước (tối đa 5 bản ghi/bước)</h3>
      {steps.map((st) => {
        const rows = (evidenceByStep[st.id] || []).slice(0, 5)
        if (rows.length === 0) return null
        return (
          <div key={st.id} style={{ marginBottom: '4mm' }}>
            <div><strong>Bước {st.step_order}: {st.title}</strong></div>
            <table>
              <thead>
                <tr>
                  <th style={{ width: '32mm' }}>Thời điểm</th>
                  <th style={{ width: '26mm' }}>Loại</th>
                  <th>Nội dung tóm tắt</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => (
                  <tr key={e.id}>
                    <td>{fmt(e.captured_at)}</td>
                    <td>{e.evidence_type}</td>
                    <td><code>{briefPayload(e.payload)}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      })}
    </section>
  )
}
