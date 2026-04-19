import { useEffect, useState, useMemo } from 'react'
import { supabase } from '../services/supabase'
import { listEvidenceForSession } from '../services/labApi'

/**
 * Loads all evidence for a session, then subscribes to INSERTs on lab_evidence
 * filtered by session_id so the practice dashboard updates live.
 *
 *   { loading, error, evidence, countsByStep }
 *
 *   countsByStep — { [step_id]: number } for easy badge rendering.
 */
export function useLiveEvidence(sessionId) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [evidence, setEvidence] = useState([])

  // Initial load
  useEffect(() => {
    if (!sessionId) return
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      const { data, error: err } = await listEvidenceForSession(sessionId)
      if (cancelled) return
      if (err) setError(err.message)
      else setEvidence(data || [])
      setLoading(false)
    }
    load()
    return () => { cancelled = true }
  }, [sessionId])

  // Realtime INSERT subscription
  useEffect(() => {
    if (!sessionId) return
    const channel = supabase
      .channel(`lab-evidence-${sessionId}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'lab_evidence',
          filter: `session_id=eq.${sessionId}`,
        },
        (payload) => {
          setEvidence((prev) => {
            if (prev.some((e) => e.id === payload.new.id)) return prev
            return [...prev, payload.new]
          })
        }
      )
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [sessionId])

  const countsByStep = useMemo(() => {
    const out = {}
    for (const e of evidence) {
      if (!e.step_id) continue
      out[e.step_id] = (out[e.step_id] || 0) + 1
    }
    return out
  }, [evidence])

  return { loading, error, evidence, countsByStep }
}
