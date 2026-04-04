import { useState, useEffect, useCallback } from 'react'
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

  const load = useCallback(async () => {
    setLoading(true)
    const [logsRes, statsRes] = await Promise.all([
      getLogs({ limit: 100, action: filterAction || null, platform: filterPlatform || null }),
      getLogStats(),
    ])
    setLoading(false)
    if (logsRes.data) setLogs(logsRes.data)
    if (statsRes.data) setStats(statsRes.data)
  }, [filterAction, filterPlatform])

  useEffect(() => { load() }, [load])

  useEffect(() => {
    const channel = supabase
      .channel('activity_logs_changes')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'activity_logs' }, () => { load() })
      .subscribe(status => setIsLive(status === 'SUBSCRIBED'))
    return () => { supabase.removeChannel(channel) }
  }, [load])

  const filteredLogs = filterUser
    ? logs.filter(l => l.username?.toLowerCase().includes(filterUser.toLowerCase()))
    : logs

  return {
    logs: filteredLogs, stats, loading, isLive,
    filterPlatform, setFilterPlatform,
    filterAction, setFilterAction,
    filterUser, setFilterUser,
    reload: load,
  }
}
