import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'

const STUDENT_ROLES = ['student', 'instructor', 'moderator', 'admin']

export default function StudentRoute({ children }) {
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

  if (!STUDENT_ROLES.includes(profile?.role)) {
    return <Navigate to="/dashboard" state={{ deniedReason: 'student-only' }} replace />
  }

  return children
}
