// Lab state machine (per-user, per-lab). See spec Section 5.2.
//
//   NOT_ASSIGNED
//        │
//        ▼
//   PRE_LAB_PENDING ───► PRE_LAB_FAILED (retry loops back)
//        │
//        ▼
//   PRE_LAB_PASSED
//        │
//        ▼  (leader starts, or member joins active session)
//   PRACTICE_ACTIVE
//        │
//        ▼  (session COMPLETED/EXPIRED, member hasn't submitted post-lab)
//   PRACTICE_DONE_POST_PENDING
//        │
//        ▼
//   COMPLETED  (report PDF generated)

export const LAB_STATES = {
  NOT_ASSIGNED: 'NOT_ASSIGNED',
  PRE_LAB_PENDING: 'PRE_LAB_PENDING',
  PRE_LAB_FAILED: 'PRE_LAB_FAILED',
  PRE_LAB_PASSED: 'PRE_LAB_PASSED',
  PRACTICE_ACTIVE: 'PRACTICE_ACTIVE',
  PRACTICE_DONE_POST_PENDING: 'PRACTICE_DONE_POST_PENDING',
  COMPLETED: 'COMPLETED',
}

/**
 * Pure function. Inputs are plain objects (DB rows or null).
 *
 *   membership        — { role, group_id, lab_id } or null
 *   latestPreQuiz     — lab_pre_quiz_submissions row or null
 *   activeSession     — lab_sessions row (status ACTIVE) or null
 *   lastSession       — most recent lab_sessions row (any status) or null
 *   myPostSubmission  — lab_post_submissions row for (user, lastSession) or null
 *   myReport          — lab_reports row for (user, lastSession) or null
 */
// Note: callers also pass `myPostSubmission`, but the state machine derives
// COMPLETED vs POST_PENDING from `myReport` alone, so it's intentionally not
// destructured here (extra props are silently ignored by the destructure).
export function computeLabState({
  membership,
  latestPreQuiz,
  activeSession,
  lastSession,
  myReport,
}) {
  if (!membership) return LAB_STATES.NOT_ASSIGNED

  // Pre-lab gate first — a member may see activeSession in their group but
  // if they haven't passed pre-lab they still belong in the PRE_LAB_* bucket
  // on the list view. The practice page itself lets them observe; it's the
  // post-lab submit that is gated.
  if (!latestPreQuiz) return LAB_STATES.PRE_LAB_PENDING
  if (!latestPreQuiz.passed) return LAB_STATES.PRE_LAB_FAILED

  if (activeSession) return LAB_STATES.PRACTICE_ACTIVE

  if (lastSession && ['COMPLETED', 'EXPIRED'].includes(lastSession.status)) {
    if (myReport) return LAB_STATES.COMPLETED
    return LAB_STATES.PRACTICE_DONE_POST_PENDING
  }

  return LAB_STATES.PRE_LAB_PASSED
}

/** Human label for the state tag in /labs. */
export function labStateLabel(state) {
  switch (state) {
    case LAB_STATES.NOT_ASSIGNED: return 'Chưa được gán nhóm'
    case LAB_STATES.PRE_LAB_PENDING: return 'Chưa làm pre-lab'
    case LAB_STATES.PRE_LAB_FAILED: return 'Pre-lab chưa đạt'
    case LAB_STATES.PRE_LAB_PASSED: return 'Sẵn sàng thực hành'
    case LAB_STATES.PRACTICE_ACTIVE: return 'Đang thực hành'
    case LAB_STATES.PRACTICE_DONE_POST_PENDING: return 'Cần làm post-lab'
    case LAB_STATES.COMPLETED: return 'Đã hoàn thành'
    default: return state
  }
}

/** Ant Design tag color per state. */
export function labStateTagColor(state) {
  switch (state) {
    case LAB_STATES.NOT_ASSIGNED: return 'default'
    case LAB_STATES.PRE_LAB_PENDING: return 'warning'
    case LAB_STATES.PRE_LAB_FAILED: return 'error'
    case LAB_STATES.PRE_LAB_PASSED: return 'processing'
    case LAB_STATES.PRACTICE_ACTIVE: return 'processing'
    case LAB_STATES.PRACTICE_DONE_POST_PENDING: return 'warning'
    case LAB_STATES.COMPLETED: return 'success'
    default: return 'default'
  }
}
