import { useState, useEffect, useMemo } from 'react'
import { getUsers, updateUserStatus, updateUserRole } from '../services/api'

export function useUsers() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [search, setSearch] = useState('')
  const [filterRole, setFilterRole] = useState('')
  const [filterStatus, setFilterStatus] = useState('')
  const [sortField, setSortField] = useState('created_at')
  const [sortOrder, setSortOrder] = useState('descend')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  async function load() {
    setLoading(true)
    const { data, error: err } = await getUsers()
    setLoading(false)
    if (err) setError(err.message)
    else setUsers(data ?? [])
  }

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!cancelled) await load()
    })()
    return () => { cancelled = true }
  }, [])

  const filtered = useMemo(() => {
    let result = [...users]
    if (search) {
      const q = search.toLowerCase()
      result = result.filter(u =>
        u.username?.toLowerCase().includes(q) ||
        u.email?.toLowerCase().includes(q) ||
        u.full_name?.toLowerCase().includes(q)
      )
    }
    if (filterRole) result = result.filter(u => u.role === filterRole)
    if (filterStatus) result = result.filter(u => u.status === filterStatus)
    result.sort((a, b) => {
      const av = a[sortField] ?? '', bv = b[sortField] ?? ''
      const cmp = av < bv ? -1 : av > bv ? 1 : 0
      return sortOrder === 'ascend' ? cmp : -cmp
    })
    return result
  }, [users, search, filterRole, filterStatus, sortField, sortOrder])

  const stats = useMemo(() => ({
    total: users.length,
    admins: users.filter(u => u.role === 'admin').length,
    active: users.filter(u => u.status === 'active').length,
    banned: users.filter(u => u.status === 'banned' || u.status === 'suspended').length,
  }), [users])

  async function changeStatus(userId, status) {
    const { error: err } = await updateUserStatus(userId, status)
    if (!err) setUsers(prev => prev.map(u => u.id === userId ? { ...u, status } : u))
    return err
  }

  async function changeRole(userId, role) {
    const { error: err } = await updateUserRole(userId, role)
    if (!err) setUsers(prev => prev.map(u => u.id === userId ? { ...u, role } : u))
    return err
  }

  return {
    users: filtered, allUsers: users, loading, error, stats,
    search, setSearch, filterRole, setFilterRole, filterStatus, setFilterStatus,
    sortField, setSortField, sortOrder, setSortOrder,
    page, setPage, pageSize, setPageSize,
    changeStatus, changeRole, reload: load,
  }
}
