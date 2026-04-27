import { supabase } from './supabase'

/**
 * One-shot fetcher: returns every field the PDF template needs, so the
 * template stays a pure function of its props.
 *
 * Returns:
 *   { lab, student, group, session, steps, preQuiz, postSubmission,
 *     evidenceByStep, topCanIds, questions }
 *
 * Errors short-circuit — the first failing query returns { data: null, error }.
 * Call sites render the Alert on error and bail.
 */
export async function fetchLabReportData(userId, sessionId) {
  // 1. Session (+ lab + group) — one hop gives us most of the cover page.
  const { data: session, error: sErr } = await supabase
    .from('lab_sessions')
    .select(
      'id, session_code, status, started_at, ended_at, expires_at, ' +
        'current_step_id, started_by, lab_id, group_id, ' +
        'lab:labs(id, code, title, description, pre_quiz_pass_threshold), ' +
        'group:lab_groups(id, name, semester)'
    )
    .eq('id', sessionId)
    .single()
  if (sErr) return { data: null, error: sErr }

  // 2. Student profile.
  const { data: student, error: pErr } = await supabase
    .from('profiles')
    .select('id, username, full_name, mssv')
    .eq('id', userId)
    .single()
  if (pErr) return { data: null, error: pErr }

  // 3. Everything else in parallel — independent reads.
  const [stepsRes, preQuizRes, postRes, questionsRes, evidenceRes] =
    await Promise.all([
      supabase
        .from('lab_steps')
        .select('id, step_order, title, instruction, evidence_type, required_count, hint')
        .eq('lab_id', session.lab_id)
        .order('step_order', { ascending: true }),
      supabase
        .from('lab_pre_quiz_submissions')
        .select('id, score_percent, passed, attempt_number, submitted_at, answers')
        .eq('user_id', userId)
        .eq('lab_id', session.lab_id)
        .order('submitted_at', { ascending: false })
        .limit(1)
        .maybeSingle(),
      supabase
        .from('lab_post_submissions')
        .select('id, answers, uploaded_images, is_draft, submitted_at')
        .eq('user_id', userId)
        .eq('session_id', sessionId)
        .maybeSingle(),
      supabase
        .from('lab_questions')
        .select('id, question_order, question_type, question_text, options, correct_answer, stage, points')
        .eq('lab_id', session.lab_id)
        .order('question_order', { ascending: true }),
      supabase
        .from('lab_evidence')
        .select('id, step_id, evidence_type, payload, captured_at')
        .eq('session_id', sessionId)
        .order('captured_at', { ascending: true }),
    ])

  for (const r of [stepsRes, preQuizRes, postRes, questionsRes, evidenceRes]) {
    if (r.error) return { data: null, error: r.error }
  }

  // Bucket evidence by step_id so the template can render a per-step sample.
  const evidenceByStep = {}
  for (const e of evidenceRes.data || []) {
    const key = e.step_id || 'unassigned'
    ;(evidenceByStep[key] ||= []).push(e)
  }

  // Top-10 CAN IDs across all frame evidence. Frames look like
  // { canId: '0x7E8', data: '...', ts: ... } — we count by canId and keep
  // one sample payload per id.
  const canCounter = new Map()
  for (const e of evidenceRes.data || []) {
    if (e.evidence_type !== 'raw_frame' && e.evidence_type !== 'frame_batch') continue
    const frames = e.evidence_type === 'frame_batch' ? (e.payload?.frames || []) : [e.payload]
    for (const f of frames) {
      const id = f?.canId || f?.can_id
      if (!id) continue
      const cur = canCounter.get(id) || { canId: id, count: 0, sample: f }
      cur.count++
      canCounter.set(id, cur)
    }
  }
  const topCanIds = [...canCounter.values()]
    .sort((a, b) => b.count - a.count)
    .slice(0, 10)

  return {
    data: {
      lab: session.lab,
      student,
      group: session.group,
      session,
      steps: stepsRes.data || [],
      preQuiz: preQuizRes.data || null,
      postSubmission: postRes.data || null,
      questions: questionsRes.data || [],
      evidenceByStep,
      topCanIds,
    },
    error: null,
  }
}
