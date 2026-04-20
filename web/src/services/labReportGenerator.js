// Orchestrates: DOM element → Blob → hash → upload → lab_reports row.
// Called from LabReportPage after the off-screen template finishes mounting.
//
// We dynamic-import html2pdf.js so the 250 kB payload is only paid when the
// student clicks "Tạo PDF", not on initial /report load.

import { buildReportFilename } from './labReportFilename'
import { sha256Hex, buildHashInput } from './labReportHash'
import { insertLabReport, uploadLabReport } from './labApi'

const HTML2PDF_OPTIONS = {
  margin: [20, 20, 20, 20], // mm; matches the .page padding in pdfStyles.js
  filename: 'lab-report.pdf', // overridden below
  image: { type: 'jpeg', quality: 0.95 },
  html2canvas: {
    scale: 2,              // 2× for crisp Arial/Times at 10–12pt
    useCORS: true,
    logging: false,
    backgroundColor: '#ffffff',
  },
  jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
  pagebreak: { mode: ['css', 'legacy'] }, // respect .page `page-break-after`
}

/**
 * @param {Object} args
 * @param {HTMLElement} args.element  The off-screen <div id="lab-report-root">
 * @param {Object}   args.data        Output of fetchLabReportData
 * @param {string}   args.userId
 * @param {string}   args.sessionId
 * @returns {Promise<{ blob, blobUrl, contentHash, storagePath, filename, reportRow }>}
 */
export async function generateAndUploadReport({ element, data, userId, sessionId }) {
  const html2pdfMod = await import('html2pdf.js')
  const html2pdf = html2pdfMod.default || html2pdfMod

  // 1. Render DOM → jsPDF doc
  const worker = html2pdf()
    .set(HTML2PDF_OPTIONS)
    .from(element)

  // After toPdf we stamp "Page X / Y" + student info into every page via
  // jsPDF's API — html2canvas cannot do per-page headers on its own.
  const pdf = await worker.toPdf().get('pdf')
  const totalPages = pdf.internal.getNumberOfPages()
  for (let i = 1; i <= totalPages; i++) {
    pdf.setPage(i)
    pdf.setFontSize(9)
    pdf.setTextColor(90)
    pdf.setFont('helvetica', 'normal')
    pdf.text(
      `Trang ${i} / ${totalPages} · ${data.student?.full_name || ''} · ${data.student?.mssv || ''}`,
      105, 290, { align: 'center' }
    )
    pdf.text(
      `Session ${data.session?.session_code || ''}`,
      200, 290, { align: 'right' }
    )
  }

  const blob = pdf.output('blob')
  const blobUrl = URL.createObjectURL(blob)

  // 2. Compute SHA-256 over canonical hash input (Section 7.3)
  const contentHash = await sha256Hex(
    buildHashInput({
      sessionId,
      answers: data.postSubmission?.answers,
      startedAt: data.session?.started_at,
      finalizedAt: data.postSubmission?.finalized_at,
    })
  )

  // 3. Upload Blob
  const storagePath = `${userId}/${sessionId}.pdf`
  const { error: upErr } = await uploadLabReport(storagePath, blob)
  if (upErr) throw new Error(`Upload thất bại: ${upErr.message}`)

  // 4. Insert lab_reports row (upsert on UNIQUE(user_id,session_id))
  const { data: reportRow, error: rowErr } = await insertLabReport({
    userId,
    sessionId,
    pdfStoragePath: storagePath,
    contentHash,
    fileSizeBytes: blob.size,
  })
  if (rowErr) throw new Error(`Lưu metadata thất bại: ${rowErr.message}`)

  // 5. Filename for the local download
  const dateStr = new Date().toISOString().slice(0, 10)
  const filename = buildReportFilename({
    labCode: data.lab?.code || 'LAB00',
    mssv: data.student?.mssv || 'unknown',
    fullName: data.student?.full_name || 'unknown',
    date: dateStr,
  })

  return { blob, blobUrl, contentHash, storagePath, filename, reportRow }
}
