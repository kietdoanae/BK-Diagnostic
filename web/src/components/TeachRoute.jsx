import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'

const TEACH_ROLES = ['instructor', 'moderator', 'admin']

export default function TeachRoute({ children }) {
  const { session, profile, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!session) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (!TEACH_ROLES.includes(profile?.role)) {
    return <Navigate to="/dashboard" state={{ deniedReason: 'teach-only' }} replace />
  }

  return children
}
