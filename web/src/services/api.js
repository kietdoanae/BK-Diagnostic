import { supabase } from './supabase'

export async function getUsers() {
  return supabase
    .from('profiles')
    .select('id, username, full_name, email, role, status, created_at, last_sign_in_at')
    .order('created_at', { ascending: false })
}

export async function updateUserStatus(userId, status) {
  return supabase.from('profiles').update({ status }).eq('id', userId)
}

export async function updateUserRole(userId, role) {
  return supabase.from('profiles').update({ role }).eq('id', userId)
}

export async function getLogs({ limit = 50, offset = 0, action = null, platform = null } = {}) {
  return supabase.rpc('get_activity_logs', {
    p_limit: limit,
    p_offset: offset,
    p_action: action,
    p_platform: platform,
  })
}

export async function getLogStats() {
  return supabase.rpc('get_log_stats')
}
