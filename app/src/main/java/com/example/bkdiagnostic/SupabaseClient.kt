package com.example.bkdiagnostic

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

const val SUPABASE_URL = "https://ylspcqbwupnqskqemmiv.supabase.co"
const val SUPABASE_KEY = "sb_publishable_CiNkaDLXwDkO16g5QYjSmQ_Qh_ZEOWt"

// lazy: khởi tạo lần đầu được gọi, thread-safe (SYNCHRONIZED mode mặc định)
// → tránh block Main Thread khi app mới start
val supabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth) {
            scheme = "bkdiagnostic"
            host = "auth"
        }
        install(Postgrest)
    }
}
