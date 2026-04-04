import { Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'

export default function AdminRoute({ children }) {
  const { session, role, loading } = useAuth()
  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 80 }}><Spin size="large" /></div>
  if (!session) return <Navigate to="/login" replace />
  if (role !== 'admin' && role !== 'moderator') return <Navigate to="/dashboard" replace />
  return children
}
