package com.example.bkdiagnostic

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

// URL và KEY được đọc từ local.properties qua BuildConfig
// → file local.properties KHÔNG được commit lên GitHub
val supabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl  = BuildConfig.SUPABASE_URL,
        supabaseKey  = BuildConfig.SUPABASE_KEY
    ) {
        install(Auth) {
            scheme = "bkdiagnostic"
            host = "auth"
        }
        install(Postgrest)
        install(Storage)
    }
}
