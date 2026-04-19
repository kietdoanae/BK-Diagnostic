// SHA-256 over a deterministic JSON serialization of the given input.
// We hash {session_id, answers_json, timestamps} per Section 7.3 so the
// server can recompute and compare without re-rendering the PDF.
//
// Determinism requires stable key ordering. JSON.stringify is not stable for
// arbitrary objects, so we sort keys recursively before serializing.

function stableStringify(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value)
  if (Array.isArray(value)) {
    return '[' + value.map(stableStringify).join(',') + ']'
  }
  const keys = Object.keys(value).sort()
  return (
    '{' +
    keys
      .map((k) => JSON.stringify(k) + ':' + stableStringify(value[k]))
      .join(',') +
    '}'
  )
}

export async function sha256Hex(input) {
  const payload = typeof input === 'string' ? input : stableStringify(input)
  const bytes = new TextEncoder().encode(payload)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * Build the canonical hash-input object per spec Section 7.3:
 *   { session_id, answers_json, timestamps }
 * `timestamps` pulls the two timestamps that tie the report to an actual
 * session run (session.started_at, post_submission.finalized_at).
 */
export function buildHashInput({ sessionId, answers, startedAt, finalizedAt }) {
  return {
    session_id: sessionId,
    answers_json: answers || {},
    timestamps: {
      session_started_at: startedAt || null,
      post_finalized_at: finalizedAt || null,
    },
  }
}
