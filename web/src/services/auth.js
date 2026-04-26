import { supabase } from './supabase'

export async function getSession() {
  const { data } = await supabase.auth.getSession()
  return data?.session ?? null
}

export async function resolveEmail(identifier) {
  if (identifier.includes('@')) return identifier
  try {
    const { data, error } = await supabase.rpc('get_email_by_username', { p_username: identifier })
    if (error || !data) return null
    return String(data).replace(/^"|"$/g, '').trim()
  } catch {
    return null
  }
}

export async function login(identifier, password) {
  const email = await resolveEmail(identifier)
  if (!email) return { data: null, error: { message: 'No account found with this username.' } }
  return supabase.auth.signInWithPassword({ email, password })
}

export async function logout() {
  return supabase.auth.signOut()
}

export async function register(email, password, username, mssv) {
  const data = { username }
  if (mssv) data.mssv = mssv
  return supabase.auth.signUp({ email, password, options: { data } })
}

export async function forgotPassword(email, redirectTo) {
  return supabase.auth.resetPasswordForEmail(email, { redirectTo })
}

export async function resetPassword(newPassword) {
  return supabase.auth.updateUser({ password: newPassword })
}

export async function getProfile(userId) {
  return supabase.from('profiles').select('*').eq('id', userId).maybeSingle()
}

export function onAuthStateChange(callback) {
  return supabase.auth.onAuthStateChange(callback)
}
