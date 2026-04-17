import { supabase } from './supabase'

// ─── Labs ────────────────────────────────────────────────────────────────────

export async function listLabs() {
  return supabase
    .from('labs')
    .select('*')
    .order('order_index', { ascending: true })
    .order('created_at', { ascending: true })
}

export async function getLab(labId) {
  return supabase.from('labs').select('*').eq('id', labId).single()
}

export async function createLab(payload) {
  // payload: { code, title, description, order_index, pre_quiz_pass_threshold, is_published }
  return supabase.from('labs').insert(payload).select().single()
}

export async function updateLab(labId, patch) {
  return supabase.from('labs').update(patch).eq('id', labId).select().single()
}

export async function deleteLab(labId) {
  // ON DELETE CASCADE in schema removes steps + questions + groups + sessions
  return supabase.from('labs').delete().eq('id', labId)
}

// ─── Steps ───────────────────────────────────────────────────────────────────

export async function listSteps(labId) {
  return supabase
    .from('lab_steps')
    .select('*')
    .eq('lab_id', labId)
    .order('step_order', { ascending: true })
}

export async function createStep(payload) {
  // payload: { lab_id, step_order, title, instruction, evidence_type, required_count, hint }
  return supabase.from('lab_steps').insert(payload).select().single()
}

export async function updateStep(stepId, patch) {
  return supabase.from('lab_steps').update(patch).eq('id', stepId).select().single()
}

export async function deleteStep(stepId) {
  return supabase.from('lab_steps').delete().eq('id', stepId)
}

export async function reorderSteps(labId, orderedIds) {
  // orderedIds: array of step UUIDs in their new order. Two-phase to avoid
  // tripping the UNIQUE(lab_id, step_order) constraint:
  //  1) bump every row's step_order to a temp value above all current values
  //  2) write the final 1..N order
  const tempBase = 1000
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_steps')
      .update({ step_order: tempBase + i })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
    if (error) return { error }
  }
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_steps')
      .update({ step_order: i + 1 })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
    if (error) return { error }
  }
  return { error: null }
}

// ─── Questions ───────────────────────────────────────────────────────────────

export async function listQuestions(labId, phase /* 'pre_lab' | 'post_lab' */) {
  return supabase
    .from('lab_questions')
    .select('*')
    .eq('lab_id', labId)
    .eq('phase', phase)
    .order('question_order', { ascending: true })
}

export async function createQuestion(payload) {
  // payload: { lab_id, phase, question_order, question_type, question_text,
  //            options, correct_answer, points, hint }
  return supabase.from('lab_questions').insert(payload).select().single()
}

export async function updateQuestion(questionId, patch) {
  return supabase
    .from('lab_questions')
    .update(patch)
    .eq('id', questionId)
    .select()
    .single()
}

export async function deleteQuestion(questionId) {
  return supabase.from('lab_questions').delete().eq('id', questionId)
}

export async function reorderQuestions(labId, phase, orderedIds) {
  const tempBase = 1000
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_questions')
      .update({ question_order: tempBase + i })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
      .eq('phase', phase)
    if (error) return { error }
  }
  for (let i = 0; i < orderedIds.length; i++) {
    const { error } = await supabase
      .from('lab_questions')
      .update({ question_order: i + 1 })
      .eq('id', orderedIds[i])
      .eq('lab_id', labId)
      .eq('phase', phase)
    if (error) return { error }
  }
  return { error: null }
}

// ─── Groups ──────────────────────────────────────────────────────────────────

export async function listGroups(labId /* optional filter */) {
  let q = supabase
    .from('lab_groups')
    .select('id, lab_id, name, semester, created_at, lab:labs(code,title)')
    .order('created_at', { ascending: false })
  if (labId) q = q.eq('lab_id', labId)
  return q
}

export async function listGroupMembers(groupId) {
  // Note: lab_group_members.user_id → auth.users; profile fields live in
  // public.profiles. We join profiles for MSSV + full_name + username.
  return supabase
    .from('lab_group_members')
    .select(
      'group_id, user_id, role, profile:profiles!user_id(username, full_name, mssv, email)'
    )
    .eq('group_id', groupId)
}

export async function createGroup(payload) {
  // payload: { lab_id, name, semester }
  return supabase.from('lab_groups').insert(payload).select().single()
}

export async function updateGroup(groupId, patch) {
  return supabase.from('lab_groups').update(patch).eq('id', groupId).select().single()
}

export async function deleteGroup(groupId) {
  return supabase.from('lab_groups').delete().eq('id', groupId)
}

export async function addGroupMember(groupId, userId, role /* 'leader'|'member' */) {
  return supabase
    .from('lab_group_members')
    .insert({ group_id: groupId, user_id: userId, role })
}

export async function removeGroupMember(groupId, userId) {
  return supabase
    .from('lab_group_members')
    .delete()
    .eq('group_id', groupId)
    .eq('user_id', userId)
}

export async function setGroupLeader(groupId, newLeaderUserId) {
  // Two-step because of partial unique index "one leader per group":
  //  1) demote every leader in this group to member
  //  2) promote the chosen user
  const { error: e1 } = await supabase
    .from('lab_group_members')
    .update({ role: 'member' })
    .eq('group_id', groupId)
    .eq('role', 'leader')
  if (e1) return { error: e1 }
  return supabase
    .from('lab_group_members')
    .update({ role: 'leader' })
    .eq('group_id', groupId)
    .eq('user_id', newLeaderUserId)
}

// MSSV / name autocomplete — staff-only (admin_get_users RPC already exists
// and returns mssv/full_name/username/email).
export async function searchProfilesByMssvOrName(query, limit = 20) {
  // Reuse existing admin_get_users RPC then filter client-side. The dataset
  // is small (course-scale), so client filter is fine and avoids needing a
  // new server-side RPC.
  const { data, error } = await supabase.rpc('admin_get_users')
  if (error) return { data: [], error }
  const q = (query || '').trim().toLowerCase()
  if (!q) return { data: data.slice(0, limit), error: null }
  const filtered = data.filter(
    (u) =>
      (u.mssv || '').toLowerCase().includes(q) ||
      (u.full_name || '').toLowerCase().includes(q) ||
      (u.username || '').toLowerCase().includes(q) ||
      (u.email || '').toLowerCase().includes(q)
  )
  return { data: filtered.slice(0, limit), error: null }
}

// Bulk-import helper used by GroupBulkImport. Accepts a parsed
// {groupName, semester, leaderMssv, memberMssvs[]} array; returns per-row
// {ok, error, groupId} so the UI can show a result table.
export async function bulkImportGroups(labId, rows) {
  const { data: profiles, error: pErr } = await supabase.rpc('admin_get_users')
  if (pErr) return { results: [], error: pErr }
  const byMssv = new Map(
    profiles.filter((p) => p.mssv).map((p) => [p.mssv.trim(), p])
  )

  const results = []
  for (const row of rows) {
    try {
      const leader = byMssv.get(row.leaderMssv?.trim())
      if (!leader) throw new Error(`Leader MSSV not found: ${row.leaderMssv}`)
      const members = (row.memberMssvs || []).map((m) => {
        const p = byMssv.get(m.trim())
        if (!p) throw new Error(`Member MSSV not found: ${m}`)
        return p
      })

      const { data: g, error: gErr } = await supabase
        .from('lab_groups')
        .insert({ lab_id: labId, name: row.groupName, semester: row.semester })
        .select()
        .single()
      if (gErr) throw gErr

      const { error: lErr } = await supabase
        .from('lab_group_members')
        .insert({ group_id: g.id, user_id: leader.id, role: 'leader' })
      if (lErr) throw lErr

      for (const m of members) {
        if (m.id === leader.id) continue
        const { error: mErr } = await supabase
          .from('lab_group_members')
          .insert({ group_id: g.id, user_id: m.id, role: 'member' })
        if (mErr) throw mErr
      }
      results.push({ ok: true, groupName: row.groupName, groupId: g.id })
    } catch (e) {
      results.push({ ok: false, groupName: row.groupName, error: e.message })
    }
  }
  return { results, error: null }
}

// ─── Sessions ────────────────────────────────────────────────────────────────

export async function listSessions({ labId, status, fromDate, toDate } = {}) {
  let q = supabase
    .from('lab_sessions')
    .select(
      'id, session_code, status, started_at, ended_at, expires_at, current_step_id, ' +
        'lab:labs(id,code,title), group:lab_groups(id,name,semester), ' +
        'started_by_profile:profiles!started_by(username,full_name,mssv)'
    )
    .order('started_at', { ascending: false })
  if (labId) q = q.eq('lab_id', labId)
  if (status) q = q.eq('status', status)
  if (fromDate) q = q.gte('started_at', fromDate)
  if (toDate) q = q.lte('started_at', toDate)
  return q
}

export async function getSession(sessionId) {
  return supabase
    .from('lab_sessions')
    .select(
      'id, session_code, status, started_at, ended_at, expires_at, current_step_id, ' +
        'lab:labs(id,code,title), group:lab_groups(id,name,semester)'
    )
    .eq('id', sessionId)
    .single()
}

export async function listSessionEvidence(sessionId) {
  return supabase
    .from('lab_evidence')
    .select(
      'id, step_id, submitted_by, evidence_type, payload, client_timestamp_ms, created_at, ' +
        'submitter:profiles!submitted_by(username,full_name,mssv)'
    )
    .eq('session_id', sessionId)
    .order('created_at', { ascending: true })
}

export async function forceEndSession(sessionId) {
  return supabase
    .from('lab_sessions')
    .update({ status: 'CANCELLED', ended_at: new Date().toISOString() })
    .eq('id', sessionId)
}

export async function resetSessionStep(sessionId) {
  return supabase
    .from('lab_sessions')
    .update({ current_step_id: null, step_started_at: null })
    .eq('id', sessionId)
}

// ─── Submissions ─────────────────────────────────────────────────────────────

export async function listPostSubmissions({ labId, sessionId } = {}) {
  let q = supabase
    .from('lab_post_submissions')
    .select(
      'id, user_id, session_id, is_draft, submitted_at, updated_at, teacher_comment, ' +
        'session:lab_sessions(id, session_code, lab_id, group_id, ' +
        '  lab:labs(code,title), group:lab_groups(name,semester)), ' +
        'profile:profiles!user_id(username,full_name,mssv,email)'
    )
    .order('updated_at', { ascending: false })
  if (sessionId) q = q.eq('session_id', sessionId)
  if (labId) {
    // Filter by lab via the joined session — needs server-side filter; cheapest
    // approach is two-step: fetch sessions for the lab then filter client-side.
    const { data: sessions, error: se } = await supabase
      .from('lab_sessions')
      .select('id')
      .eq('lab_id', labId)
    if (se) return { data: null, error: se }
    const ids = (sessions || []).map((s) => s.id)
    if (ids.length === 0) return { data: [], error: null }
    q = q.in('session_id', ids)
  }
  return q
}

export async function getPostSubmission(userId, sessionId) {
  return supabase
    .from('lab_post_submissions')
    .select('*')
    .eq('user_id', userId)
    .eq('session_id', sessionId)
    .single()
}

export async function setTeacherComment(submissionId, comment) {
  return supabase
    .from('lab_post_submissions')
    .update({ teacher_comment: comment })
    .eq('id', submissionId)
}

export async function listPreQuizSubmissions({ labId, userId } = {}) {
  let q = supabase
    .from('lab_pre_quiz_submissions')
    .select(
      'id, user_id, lab_id, score_percent, passed, attempt_number, submitted_at, ' +
        'profile:profiles!user_id(username,full_name,mssv)'
    )
    .order('submitted_at', { ascending: false })
  if (labId) q = q.eq('lab_id', labId)
  if (userId) q = q.eq('user_id', userId)
  return q
}

// ─── Reports / PDFs ──────────────────────────────────────────────────────────

export async function listReports({ labId, sessionId } = {}) {
  let q = supabase
    .from('lab_reports')
    .select(
      'id, user_id, session_id, pdf_storage_path, content_hash, file_size_bytes, generated_at, ' +
        'profile:profiles!user_id(username,full_name,mssv), ' +
        'session:lab_sessions(id, session_code, lab_id, lab:labs(code,title))'
    )
    .order('generated_at', { ascending: false })
  if (sessionId) q = q.eq('session_id', sessionId)
  if (labId) {
    const { data: sessions, error: se } = await supabase
      .from('lab_sessions')
      .select('id')
      .eq('lab_id', labId)
    if (se) return { data: null, error: se }
    const ids = (sessions || []).map((s) => s.id)
    if (ids.length === 0) return { data: [], error: null }
    q = q.in('session_id', ids)
  }
  return q
}

export async function getReportSignedUrl(storagePath, expiresIn = 60) {
  return supabase.storage
    .from('lab-reports')
    .createSignedUrl(storagePath, expiresIn)
}

export async function downloadReportBlob(storagePath) {
  return supabase.storage.from('lab-reports').download(storagePath)
}
