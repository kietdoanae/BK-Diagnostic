import { useEffect, useState, useCallback } from 'react'
import { supabase } from '../services/supabase'
import {
  rpcSetCurrentStep,
  rpcEndCurrentStep,
  rpcCompleteLabSession,
} from '../services/labApi'

/**
 * Fetches a session by id, subscribes to UPDATE events so current_step_id +
 * status stay fresh, and exposes leader actions.
 *
 *   { session, loading, error, startStep, endStep, completeSession, reload }
 */
export function useLabSession(sessionId) {
  const [session, setSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    if (!sessionId) return
    const { data, error: err } = await supabase
      .from('lab_sessions')
      .select(
        'id, session_code, status, current_step_id, step_started_at, ' +
          'started_at, ended_at, expires_at, started_by, lab_id, group_id'
      )
      .eq('id', sessionId)
      .maybeSingle()
    if (err) setError(err.message)
    else setSession(data)
    setLoading(false)
  }, [sessionId])

  useEffect(() => {
    let cancelled = false
    async function run() {
      if (!sessionId) return
      const { data, error: err } = await supabase
        .from('lab_sessions')
        .select(
          'id, session_code, status, current_step_id, step_started_at, ' +
            'started_at, ended_at, expires_at, started_by, lab_id, group_id'
        )
        .eq('id', sessionId)
        .maybeSingle()
      if (cancelled) return
      if (err) setError(err.message)
      else setSession(data)
      setLoading(false)
    }
    run()
    return () => { cancelled = true }
  }, [sessionId])

  useEffect(() => {
    if (!sessionId) return
    const channel = supabase
      .channel(`lab-session-${sessionId}`)
      .on(
        'postgres_changes',
        {
          event: 'UPDATE',
          schema: 'public',
          table: 'lab_sessions',
          filter: `id=eq.${sessionId}`,
        },
        (payload) => {
          setSession((prev) => ({ ...(prev || {}), ...payload.new }))
        }
      )
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [sessionId])

  const startStep = useCallback(
    async (stepId) => {
      const { error: err } = await rpcSetCurrentStep(sessionId, stepId)
      if (err) return { ok: false, error: err.message }
      await reload()
      return { ok: true }
    },
    [sessionId, reload]
  )

  const endStep = useCallback(async () => {
    const { error: err } = await rpcEndCurrentStep(sessionId)
    if (err) return { ok: false, error: err.message }
    await reload()
    return { ok: true }
  }, [sessionId, reload])

  const completeSession = useCallback(async () => {
    const { error: err } = await rpcCompleteLabSession(sessionId)
    if (err) return { ok: false, error: err.message }
    await reload()
    return { ok: true }
  }, [sessionId, reload])

  return { session, loading, error, startStep, endStep, completeSession, reload }
}
