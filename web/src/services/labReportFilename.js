// Builds TR4021_LAB{NN}_{MSSV}_{HọTên}_{YYYY-MM-DD}.pdf per Section 7.4.
// Vietnamese diacritics are stripped so the filename survives filesystems
// and email clients that misencode NFC/NFD.
//
// Example: buildReportFilename({ labCode: 'LAB01', mssv: '2210xxxx',
//   fullName: 'Nguyễn Hoàng Kiệt', date: '2026-04-17' })
//   → 'TR4021_LAB01_2210xxxx_NguyenHoangKiet_2026-04-17.pdf'

// NFD decomposes characters into base + combining marks, then we drop the
// combining-mark range (U+0300–U+036F). `đ`/`Đ` do not decompose in NFD,
// so they need an explicit table.
const MANUAL_MAP = { đ: 'd', Đ: 'D' }

export function stripVietnameseDiacritics(input) {
  if (!input) return ''
  const decomposed = input.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  return decomposed.replace(/[đĐ]/g, (c) => MANUAL_MAP[c] ?? c)
}

export function buildReportFilename({ labCode, mssv, fullName, date }) {
  if (!labCode || !mssv || !fullName || !date) {
    throw new Error('buildReportFilename: labCode, mssv, fullName, date are all required')
  }
  // Remove whitespace from the name: "Nguyễn Hoàng Kiệt" → "NguyenHoangKiet"
  const asciiName = stripVietnameseDiacritics(fullName).replace(/\s+/g, '')
  // Also scrub anything outside [A-Za-z0-9] that might have snuck in (extended
  // punctuation, en-dash in typed names, etc.) — keep the filename stable.
  const safeName = asciiName.replace(/[^A-Za-z0-9]/g, '')
  const safeMssv = String(mssv).replace(/[^A-Za-z0-9]/g, '')
  return `TR4021_${labCode}_${safeMssv}_${safeName}_${date}.pdf`
}
