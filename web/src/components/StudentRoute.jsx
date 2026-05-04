import { useMemo } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '../hooks/useAuth'
import UpdateMSSVModal from './UpdateMSSVModal'

const STUDENT_ROLES = ['student', 'instructor', 'moderator', 'admin']

export default function StudentRoute({ children }) {
  const { session, profile, loading } = useAuth()
  const location = useLocation()

  // Derive modal state from profile — no useEffect needed
  const modalOpen = useMemo(
    () => profile?.role === 'student' && !profile?.mssv,
    [profile?.role, profile?.mssv]
  )

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

  function handleMssvSuccess() {
    // Force reload to pick up new profile data
    window.location.reload()
  }

  return (
    <>
      {children}
      <UpdateMSSVModal open={modalOpen} onSuccess={handleMssvSuccess} />
    </>
  )
}
