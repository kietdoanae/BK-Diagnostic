package com.example.bkdiagnostic

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient

// ⚠️ Thay thế 2 giá trị bên dưới bằng thông tin từ Supabase Dashboard của bạn
// Project Settings → API → Project URL và anon/public key
const val SUPABASE_URL = "https://ylspcqbwupnqskqemmiv.supabase.co"
const val SUPABASE_KEY = "sb_publishable_CiNkaDLXwDkO16g5QYjSmQ_Qh_ZEOWt"

val supabaseClient = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_KEY
) {
    install(Auth) {
        // Deep link scheme để xử lý email confirmation trên Android
        scheme = "bkdiagnostic"
        host = "auth"
    }
}
