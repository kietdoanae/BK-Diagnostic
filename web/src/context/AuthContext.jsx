import { createContext, useContext, useEffect, useState } from 'react'
import { getSession, getProfile, onAuthStateChange, logout as authLogout } from '../services/auth'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(undefined) // undefined = loading
  const [profile, setProfile] = useState(null)

  async function loadProfile(userId) {
    const { data } = await getProfile(userId)
    setProfile(data ?? null)
  }

  useEffect(() => {
    getSession().then(s => {
      setSession(s)
      if (s?.user) loadProfile(s.user.id)
    })

    const { data: { subscription } } = onAuthStateChange((event, s) => {
      setSession(s)
      if (s?.user) loadProfile(s.user.id)
      else setProfile(null)
    })

    return () => subscription.unsubscribe()
  }, [])

  async function logout() {
    await authLogout()
    setSession(null)
    setProfile(null)
  }

  const value = { session, profile, role: profile?.role ?? 'user', loading: session === undefined, logout }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuthContext() {
  return useContext(AuthContext)
}
