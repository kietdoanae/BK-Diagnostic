package com.example.bkdiagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object LoginSuccess : AuthUiState()
    object RegisterSuccess : AuthUiState()
    object EmailSent : AuthUiState()
    object PasswordUpdated : AuthUiState()
    object LoggedOut : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    init {
        // Tải profile nếu đã có session (vd: sau splash screen)
        viewModelScope.launch { loadUserProfile() }
    }

    fun login(emailOrUsername: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val resolvedEmail = resolveEmail(emailOrUsername)
                    ?: throw Exception("Không tìm thấy tài khoản với username này.")
                supabaseClient.auth.signInWith(Email) {
                    this.email = resolvedEmail
                    this.password = password
                }
                loadUserProfile()
                _uiState.value = AuthUiState.LoginSuccess
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapErrorMessage(e.message))
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                supabaseClient.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    data = buildJsonObject {
                        put("username", username)
                    }
                }
                _uiState.value = AuthUiState.RegisterSuccess
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapErrorMessage(e.message))
            }
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                supabaseClient.auth.resetPasswordForEmail(
                    email = email,
                    redirectUrl = "bkdiagnostic://reset"
                )
                _uiState.value = AuthUiState.EmailSent
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapErrorMessage(e.message))
            }
        }
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                supabaseClient.auth.updateUser {
                    password = newPassword
                }
                _uiState.value = AuthUiState.PasswordUpdated
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapErrorMessage(e.message))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { supabaseClient.auth.signOut() }
            _userProfile.value = null
            _uiState.value = AuthUiState.LoggedOut
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    // ── Tải profile từ bảng profiles ─────────────────────────────────────────
    private suspend fun loadUserProfile() {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return
        runCatching {
            val profile = supabaseClient.postgrest["profiles"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                    limit(1)
                }
                .decodeSingle<UserProfile>()
            _userProfile.value = profile
        }
    }

    // ── Tra email từ username nếu input không chứa @ ──────────────────────────
    private suspend fun resolveEmail(input: String): String? {
        if (input.contains("@")) return input
        return runCatching {
            // Gọi Postgres function SECURITY DEFINER để lấy email từ username
            val raw = supabaseClient.postgrest
                .rpc("get_email_by_username", buildJsonObject { put("p_username", input) })
                .data
            // Postgres trả về JSON string có dấu ngoặc kép: "\"user@example.com\""
            raw.trim().removeSurrounding("\"")
        }.getOrNull()
    }

    private fun mapErrorMessage(rawMessage: String?): String {
        return when {
            rawMessage == null -> "Đã xảy ra lỗi. Vui lòng thử lại."
            rawMessage.contains("Invalid login credentials", ignoreCase = true) ->
                "Email/Username hoặc mật khẩu không đúng."
            rawMessage.contains("User already registered", ignoreCase = true) ->
                "Email này đã được đăng ký."
            rawMessage.contains("Password should be at least", ignoreCase = true) ->
                "Mật khẩu phải có ít nhất 6 ký tự."
            rawMessage.contains("Unable to validate email", ignoreCase = true) ->
                "Email không hợp lệ."
            rawMessage.contains("Email not confirmed", ignoreCase = true) ||
            rawMessage.contains("email_not_confirmed", ignoreCase = true) ->
                "Email chưa được xác nhận. Vui lòng kiểm tra hộp thư và nhấn vào link xác nhận."
            rawMessage.contains("over_email_send_rate_limit", ignoreCase = true) ||
            rawMessage.contains("email rate limit", ignoreCase = true) ->
                "Quá nhiều yêu cầu gửi email. Vui lòng thử lại sau vài phút."
            rawMessage.contains("Không tìm thấy tài khoản", ignoreCase = true) ->
                rawMessage
            rawMessage.contains("network", ignoreCase = true) ||
            rawMessage.contains("connect", ignoreCase = true) ->
                "Không thể kết nối mạng. Kiểm tra lại kết nối."
            else -> rawMessage
        }
    }
}
