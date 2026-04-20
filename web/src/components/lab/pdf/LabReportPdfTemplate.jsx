import { forwardRef } from 'react'
import { PDF_STYLES } from './pdfStyles'
import CoverSection from './CoverSection'
import ObjectivesSection from './ObjectivesSection'
import PreQuizSection from './PreQuizSection'
import PracticeSummarySection from './PracticeSummarySection'
import PostLabSection from './PostLabSection'
import DeclarationSection from './DeclarationSection'

/**
 * Pure presentational root. All inputs come from `data` (output of
 * fetchLabReportData). `hashPreview` is the first 16 hex chars of
 * content_hash, shown in the header strip per Section 7.1 footer spec.
 *
 * Uses forwardRef so the generator can pass the ref into html2pdf().from(el).
 */
const LabReportPdfTemplate = forwardRef(function LabReportPdfTemplate(
  { data, hashPreview },
  ref
) {
  const { lab, student, group, session, steps, preQuiz, postSubmission,
    questions, evidenceByStep, topCanIds } = data

  return (
    <div id="lab-report-root" ref={ref}>
      <style>{PDF_STYLES}</style>

      <CoverSection
        lab={lab} student={student} group={group}
        session={session} postSubmission={postSubmission}
      />
      <ObjectivesSection lab={lab} />
      <PreQuizSection preQuiz={preQuiz} questions={questions} lab={lab} />
      <PracticeSummarySection
        session={session} steps={steps}
        evidenceByStep={evidenceByStep} topCanIds={topCanIds}
      />
      <PostLabSection questions={questions} postSubmission={postSubmission} />
      <DeclarationSection student={student} />

      <div style={{ textAlign: 'center', fontSize: '9pt', color: '#555', marginTop: '6mm' }}>
        {student?.full_name} · {student?.mssv} · Hash <code>{hashPreview}</code>
      </div>
    </div>
  )
})

export default LabReportPdfTemplate
