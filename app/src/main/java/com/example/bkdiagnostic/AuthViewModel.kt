package com.example.bkdiagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
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
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
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
                // redirectUrl riêng cho reset password để app phân biệt với signup confirmation
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

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun mapErrorMessage(rawMessage: String?): String {
        return when {
            rawMessage == null -> "Đã xảy ra lỗi. Vui lòng thử lại."
            rawMessage.contains("Invalid login credentials", ignoreCase = true) ->
                "Email hoặc mật khẩu không đúng."
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
            rawMessage.contains("network", ignoreCase = true) ||
            rawMessage.contains("connect", ignoreCase = true) ->
                "Không thể kết nối mạng. Kiểm tra lại kết nối."
            else -> rawMessage
        }
    }
}
