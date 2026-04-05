import { supabase } from './supabase'

export async function logActivity(action, details = {}) {
  const { error } = await supabase.rpc('log_activity', {
    p_action: action,
    p_platform: 'web',
    p_details: details,
  })
  if (error) console.error('[logActivity]', action, error.message, error)
}

export async function getUsers() {
  return supabase.rpc('admin_get_users')
}

export async function updateUserStatus(userId, status) {
  return supabase.rpc('admin_update_user', { target_id: userId, new_status: status })
}

export async function updateUserRole(userId, role) {
  return supabase.rpc('admin_update_user', { target_id: userId, new_role: role })
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
