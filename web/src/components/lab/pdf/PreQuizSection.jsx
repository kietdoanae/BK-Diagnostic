// Pre-quiz table: question, chosen answer, correct/incorrect, score + verdict.
// `preQuiz.answers` is stored as { [questionId]: answerKey } (see submit_pre_quiz RPC).
// `questions` is the full question bank filtered to the pre-lab phase.

function answerLabel(question, key) {
  if (!question?.options || key == null) return '—'
  const opts = question.options

  // Object form (actual schema, used by PreQuizRunner): { A: 'text', B: 'text' }
  if (!Array.isArray(opts) && typeof opts === 'object') {
    const text = opts[key]
    return text ? `${key}. ${text}` : String(key)
  }

  // Array form fallback (legacy/alternate shape): [{ key: 'A', text: '…' }]
  if (Array.isArray(opts)) {
    const found = opts.find((o) => o?.key === key)
    return found ? `${found.key}. ${found.text}` : String(key)
  }

  return String(key)
}

export default function PreQuizSection({ preQuiz, questions, lab }) {
  const preQs = (questions || []).filter((q) => q.phase === 'pre_lab')

  if (!preQuiz) {
    return (
      <section className="page">
        <h2>2. Kết quả pre-lab quiz</h2>
        <p className="muted">Chưa có kết quả pre-lab cho lần nộp này.</p>
      </section>
    )
  }

  const threshold = lab?.pre_quiz_pass_threshold ?? 70
  const verdict = preQuiz.passed ? 'ĐẠT' : 'CHƯA ĐẠT'

  return (
    <section className="page">
      <h2>2. Kết quả pre-lab quiz</h2>
      <p>
        Lần thứ <strong>{preQuiz.attempt_number}</strong> · Điểm{' '}
        <strong>{Number(preQuiz.score_percent).toFixed(1)}%</strong> · Ngưỡng đậu{' '}
        {threshold}% → <strong>{verdict}</strong>
      </p>

      <table>
        <thead>
          <tr>
            <th style={{ width: '10mm' }}>#</th>
            <th>Câu hỏi</th>
            <th style={{ width: '55mm' }}>Trả lời</th>
            <th style={{ width: '15mm' }}>Kết quả</th>
          </tr>
        </thead>
        <tbody>
          {preQs.map((q, i) => {
            const chosen = preQuiz.answers?.[q.id]
            const correct = chosen === q.correct_answer
            return (
              <tr key={q.id}>
                <td>{i + 1}</td>
                <td>{q.question_text}</td>
                <td>{answerLabel(q, chosen)}</td>
                <td className={correct ? 'tick' : 'cross'}>
                  {correct ? '✓' : '✗'}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}
