import { useEffect, useState } from 'react'
import { getProfile, onAuthStateChange, logout as authLogout } from '../services/auth'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }) {
  const [session, setSession] = useState(undefined) // undefined = loading
  const [profile, setProfile] = useState(null)
  const [profileError, setProfileError] = useState(null)

  async function loadProfile(userId) {
    setProfileError(null)
    const { data, error } = await getProfile(userId)
    if (error) {
      setProfileError(error.message)
      setProfile(null)
    } else {
      setProfile(data ?? null)
    }
  }

  useEffect(() => {
    const { data: { subscription } } = onAuthStateChange((event, s) => {
      if (event === 'PASSWORD_RECOVERY') {
        // Don't treat recovery as a normal login — just hold the session
        setSession(s)
        return
      }
      setSession(s)
      if (s?.user) loadProfile(s.user.id)
      else {
        setProfile(null)
        setProfileError(null)
      }
    })

    return () => subscription.unsubscribe()
  }, [])

  async function logout() {
    await authLogout()
    setSession(null)
    setProfile(null)
    setProfileError(null)
  }

  const value = {
    session,
    profile,
    role: profile?.role ?? 'user',
    loading: session === undefined,
    profileError,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

