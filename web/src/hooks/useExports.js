import { useState, useEffect, useCallback } from 'react'
import { supabase } from '../services/supabase'

// Regular user — their own export records from DB
export function useMyExports(userId) {
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    try {
      const { data, error: err } = await supabase
        .from('export_records')
        .select('*')
        .eq('user_id', userId)
        .order('created_at', { ascending: false })
        .limit(50)
      if (err) throw err
      setRecords(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => { load() }, [load])

  const getDownloadUrl = useCallback(async (storagePath) => {
    const { data } = await supabase.storage
      .from('exports')
      .createSignedUrl(storagePath, 3600)
    return data?.signedUrl ?? null
  }, [])

  return { records, loading, error, reload: load, getDownloadUrl }
}

// Admin — all export records via RPC
export function useAllExports() {
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [filterBrand, setFilterBrand] = useState(null)
  const [filterUser, setFilterUser] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data, error: err } = await supabase.rpc('get_export_records', {
        p_limit: 200,
        p_offset: 0,
        p_brand: filterBrand || null,
        p_user: filterUser || null,
      })
      if (err) throw err
      setRecords(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [filterBrand, filterUser])

  useEffect(() => { load() }, [load])

  const getDownloadUrl = useCallback(async (storagePath) => {
    const { data } = await supabase.storage
      .from('exports')
      .createSignedUrl(storagePath, 3600)
    return data?.signedUrl ?? null
  }, [])

  return { records, loading, error, reload: load, getDownloadUrl, filterBrand, setFilterBrand, filterUser, setFilterUser }
}
