import { useEffect, useState } from 'react'
import MDEditor from '@uiw/react-md-editor'
import { supabase } from '../../../services/supabase'

// Converts a storage-path URL (`bucket/key`) into a data URI.
// Called once per image at render-time; results cached in state so the
// page doesn't refetch when the parent re-renders during html2pdf capture.
async function pathToDataUri(bucket, path) {
  const { data, error } = await supabase.storage.from(bucket).download(path)
  if (error || !data) return null
  return await new Promise((resolve) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result)
    r.readAsDataURL(data)
  })
}

export default function PostLabSection({ questions, postSubmission }) {
  const postQs = (questions || []).filter((q) => q.phase === 'post_lab')
  const answers = postSubmission?.answers || {}
  const uploads = postSubmission?.uploaded_images || []
  // Index uploaded images by question_id for O(1) lookup.
  const imgByQ = Object.fromEntries(uploads.map((u) => [u.question_id, u]))

  const [dataUris, setDataUris] = useState({})

  useEffect(() => {
    let cancelled = false
    async function run() {
      const next = {}
      for (const up of uploads) {
        if (!up?.path) continue
        const uri = await pathToDataUri('lab-images', up.path)
        if (cancelled) return
        if (uri) next[up.question_id] = uri
      }
      if (!cancelled) setDataUris(next)
    }
    run()
    return () => { cancelled = true }
  // uploads comes from postSubmission prop; identity stable per render call.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [postSubmission?.id])

  if (!postSubmission || postSubmission.is_draft) {
    return (
      <section className="page">
        <h2>4. Phân tích post-lab</h2>
        <p className="muted">Chưa có bài nộp post-lab cuối cùng.</p>
      </section>
    )
  }

  return (
    <section className="page">
      <h2>4. Phân tích post-lab</h2>
      {postQs.map((q, i) => {
        const a = answers[q.id]
        const img = imgByQ[q.id]
        return (
          <div key={q.id} style={{ marginBottom: '6mm' }}>
            <h3>Câu {i + 1}. {q.question_text}</h3>

            {q.question_type === 'image_upload' ? (
              dataUris[q.id] ? (
                <img className="post-image" src={dataUris[q.id]} alt={`answer-${i}`} />
              ) : (
                <p className="muted">[Ảnh đang tải{img ? '' : ': không có file'}]</p>
              )
            ) : (
              <div data-color-mode="light">
                <MDEditor.Markdown source={typeof a === 'string' ? a : '_(Không có trả lời)_'} />
              </div>
            )}
          </div>
        )
      })}
    </section>
  )
}
