import { useState, useEffect, useCallback } from 'react'
import { supabase } from '../services/supabase'

// For regular user — lists their own exports
export function useMyExports(userId) {
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    try {
      const { data, error: err } = await supabase.storage
        .from('exports')
        .list(userId, { sortBy: { column: 'created_at', order: 'desc' }, limit: 50 })
      if (err) throw err
      setFiles(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => { load() }, [load])

  const getDownloadUrl = useCallback(async (filename) => {
    const { data } = await supabase.storage
      .from('exports')
      .createSignedUrl(`${userId}/${filename}`, 3600) // 1 hour
    return data?.signedUrl ?? null
  }, [userId])

  return { files, loading, error, reload: load, getDownloadUrl }
}

// For admin — lists all exports from all users (requires Storage RLS to allow admin read)
export function useAllExports() {
  const [files, setFiles] = useState([])   // [{ userId, filename, ...meta }]
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // List top-level folders (user IDs)
      const { data: folders, error: fErr } = await supabase.storage
        .from('exports')
        .list('', { limit: 200 })
      if (fErr) throw fErr

      const allFiles = []
      // For each folder (userId), list files inside
      await Promise.all((folders || []).map(async (folder) => {
        const { data: userFiles } = await supabase.storage
          .from('exports')
          .list(folder.name, { sortBy: { column: 'created_at', order: 'desc' }, limit: 100 })
        ;(userFiles || []).forEach(f => {
          allFiles.push({ userId: folder.name, ...f })
        })
      }))

      // Sort by most recent first
      allFiles.sort((a, b) => new Date(b.created_at) - new Date(a.created_at))
      setFiles(allFiles)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const getDownloadUrl = useCallback(async (userId, filename) => {
    const { data } = await supabase.storage
      .from('exports')
      .createSignedUrl(`${userId}/${filename}`, 3600)
    return data?.signedUrl ?? null
  }, [])

  return { files, loading, error, reload: load, getDownloadUrl }
}
