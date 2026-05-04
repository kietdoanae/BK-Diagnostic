import { useState, useEffect, useCallback, useRef } from 'react'
import { getLogs, getLogStats } from '../services/api'
import { supabase } from '../services/supabase'

export function useLogs() {
  const [logs, setLogs] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [filterPlatform, setFilterPlatform] = useState('')
  const [filterAction, setFilterAction] = useState('')
  const [filterUser, setFilterUser] = useState('')
  const [isLive, setIsLive] = useState(false)
  const hasData = useRef(false)

  // showSpinner=true on first load / filter change; false for background refresh
  const load = useCallback(async (showSpinner = true) => {
    if (showSpinner) setLoading(true)
    const [logsRes, statsRes] = await Promise.all([
      getLogs({ limit: 100, action: filterAction || null, platform: filterPlatform || null }),
      getLogStats(),
    ])
    if (showSpinner) setLoading(false)
    if (logsRes.error) console.error('[useLogs] getLogs:', logsRes.error.message)
    if (statsRes.error) console.error('[useLogs] getLogStats:', statsRes.error.message)
    if (logsRes.data) { setLogs(logsRes.data); hasData.current = true }
    if (statsRes.data) setStats(statsRes.data)
  }, [filterAction, filterPlatform])

  // Initial load & re-load when filters change
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!cancelled) await load(true)
    })()
    return () => { cancelled = true }
  }, [load])

  // Realtime: fires when Supabase Replication is enabled for activity_logs table
  useEffect(() => {
    const channel = supabase
      .channel('activity_logs_changes')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'activity_logs' },
        () => { load(false) })
      .subscribe(status => setIsLive(status === 'SUBSCRIBED'))
    return () => { supabase.removeChannel(channel) }
  }, [load])

  // Polling fallback: refresh every 10s silently in case Realtime misses events
  useEffect(() => {
    const id = setInterval(() => { load(false) }, 10000)
    return () => clearInterval(id)
  }, [load])

  const filteredLogs = filterUser
    ? logs.filter(l => l.username?.toLowerCase().includes(filterUser.toLowerCase()))
    : logs

  return {
    logs: filteredLogs, stats, loading, isLive,
    filterPlatform, setFilterPlatform,
    filterAction, setFilterAction,
    filterUser, setFilterUser,
    reload: () => load(true),
  }
}
