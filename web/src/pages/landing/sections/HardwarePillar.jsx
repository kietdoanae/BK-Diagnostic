import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const COMPONENTS = [
  'STM32F103C8T6 — Vi điều khiển Cortex-M3, 72 MHz, 64 KB Flash',
  'MCP2515 — Bộ điều khiển CAN 2.0B, giao tiếp SPI',
  'TJA1050 — CAN transceiver chuẩn ISO 11898, tốc độ tới 1 Mbps',
  'CP2102 — Cầu USB-UART Silicon Labs, driver có sẵn Android',
  'Khối nguồn — 12 V DC → 5 V (LM7805) → 3.3 V (AMS1117)',
]

const SPECS = [
  ['CAN baudrate',     '250 / 500 kbps (auto-detect)'],
  ['UART baud',        '115200 8N1'],
  ['Frame protocol',   '[0xAA][TYPE][LEN][PAYLOAD][XOR][0x55]'],
  ['Nguồn vào',        '12 V DC (cigarette lighter / OBD-II pin 16)'],
  ['Dòng tiêu thụ',    '~120 mA active, ~25 mA idle'],
  ['Kích thước PCB',   '50 × 40 × 18 mm (kèm vỏ in 3D)'],
]

const PROTOCOLS = [
  'OBD-II Mode 01 — Live data (tiêu chuẩn ISO 15765-4)',
  'OBD-II Mode 03/04 — Đọc / xóa mã lỗi DTC',
  'UDS ISO 14229 — Diagnostic & Communication Management',
  'Ford UDS Mode 22 — Read Data By Identifier (DID độc quyền)',
  'ISO-TP 15765-2 — Truyền payload >8 byte qua CAN',
]

export default function HardwarePillar() {
  return (
    <section className="landing-section" style={{ background: 'var(--paper)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TRỤ CỘT 1 · PHẦN CỨNG"
          title="Mạch giao tiếp CAN bus tự thiết kế"
          sub="Bộ chuyển đổi CAN ↔ USB với khung dữ liệu nhị phân, xác thực checksum và auto-detect baudrate."
          align="left"
        />

        {/* Hàng trên: Ảnh + Thành phần */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '5fr 7fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 32,
        }} className="responsive-grid">
          <PlaceholderImage
            path="hardware/pcb-top.jpg"
            alt="Mạch in giao tiếp CAN bus, kích thước 50×40 mm"
            ratio="4/3"
            caption="Hình 4 — Mạch in giao tiếp CAN bus, kích thước 50×40 mm."
          />

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>Thành phần</h3>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 12 }}>
              {COMPONENTS.map((c, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{c}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Bảng spec */}
        <div style={{
          background: 'var(--paper-soft)',
          borderRadius: 'var(--radius-card)',
          padding: 24,
          border: '1px solid var(--rule)',
          marginBottom: 32,
        }}>
          <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>Bảng thông số kỹ thuật</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <tbody>
              {SPECS.map(([k, v], i) => (
                <tr key={i} style={{ borderBottom: i < SPECS.length - 1 ? '1px solid var(--rule)' : 'none' }}>
                  <td style={{ padding: '12px 0', color: 'var(--ink-500)', width: '35%', fontWeight: 500 }}>{k}</td>
                  <td style={{ padding: '12px 0', color: 'var(--ink-900)', fontFamily: i === 2 ? 'var(--font-mono)' : 'inherit', fontSize: i === 2 ? 13 : 14 }}>{v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 2 thẻ ngang: Frame protocol | Giao thức hỗ trợ */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: 24,
        }}>
          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 24,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 16, color: 'var(--ink-900)' }}>Khung dữ liệu UART</h3>
            <pre style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 13,
              background: 'var(--paper)',
              padding: 14,
              borderRadius: 8,
              border: '1px solid var(--rule)',
              color: 'var(--ink-900)',
              margin: '0 0 12px',
              overflow: 'auto',
            }}>{`[0xAA] [TYPE] [LEN]
[PAYLOAD…] [XOR] [0x55]`}</pre>
            <p style={{ fontSize: 13, color: 'var(--ink-500)', lineHeight: 1.6, margin: 0 }}>
              XOR checksum xuyên suốt PAYLOAD đảm bảo phát hiện lỗi truyền. SOF/EOF cố định
              (0xAA / 0x55) cho phép đồng bộ lại nhanh khi mất frame.
            </p>
          </div>

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 24,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 16, color: 'var(--ink-900)' }}>Giao thức hỗ trợ</h3>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {PROTOCOLS.map((p, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 13, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--bk-blue-500)' }}>●</span>
                  <span>{p}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      <style>{`
        @media (max-width: 767px) {
          .landing-container .responsive-grid { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </section>
  )
}
