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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object LoginSuccess : AuthUiState()
    object RegisterSuccess : AuthUiState()
    object EmailSent : AuthUiState()
    object PasswordUpdated : AuthUiState()
    object ProfileUpdated : AuthUiState()
    object LoggedOut : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@Serializable
private data class ProfileUsernameUpdate(@SerialName("username") val username: String)

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
                    ?: throw Exception("No account found with this username.")
                supabaseClient.auth.signInWith(Email) {
                    this.email = resolvedEmail
                    this.password = password
                }
                loadUserProfile()

                // Block non-active accounts
                val profile = _userProfile.value
                if (profile != null && profile.isBlocked) {
                    runCatching { supabaseClient.auth.signOut() }
                    _userProfile.value = null
                    _uiState.value = AuthUiState.Error(profile.blockedMessage)
                    return@launch
                }

                // Log login success
                _userProfile.value?.let { p ->
                    ActivityLogger.log(p.id, p.username, ActivityLogger.LOGIN,
                        buildJsonObject { put("method", if (emailOrUsername.contains("@")) "email" else "username") })
                }

                _uiState.value = AuthUiState.LoginSuccess
            } catch (e: Exception) {
                // Login failed = không có session → dùng logAnonymous
                ActivityLogger.logAnonymous(ActivityLogger.LOGIN_FAILED,
                    buildJsonObject { put("reason", mapErrorMessage(e.message)) })
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
                ActivityLogger.logCurrent(ActivityLogger.REGISTER,
                    buildJsonObject { put("username", username) })
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
                // Password reset = user chưa đăng nhập → dùng logAnonymous
                ActivityLogger.logAnonymous(ActivityLogger.PASSWORD_RESET,
                    buildJsonObject { put("email", email) })
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
            // Log trước khi signOut (sau signOut sẽ không còn session)
            _userProfile.value?.let { p ->
                ActivityLogger.log(p.id, p.username, ActivityLogger.LOGOUT)
            }
            runCatching { supabaseClient.auth.signOut() }
            _userProfile.value = null
            _uiState.value = AuthUiState.LoggedOut
        }
    }

    fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@launch
                supabaseClient.postgrest["profiles"]
                    .update(ProfileUsernameUpdate(newUsername)) {
                        filter { eq("id", userId) }
                    }
                loadUserProfile()
                _uiState.value = AuthUiState.ProfileUpdated
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapErrorMessage(e.message))
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    // ── Reload profile (gọi từ bên ngoài sau khi session thay đổi) ──────────
    fun reloadProfile() {
        viewModelScope.launch { loadUserProfile() }
    }

    // ── Tải profile từ bảng profiles ─────────────────────────────────────────
    private suspend fun loadUserProfile() {
        val user = supabaseClient.auth.currentUserOrNull() ?: return
        val userId = user.id

        // Use decodeList + firstOrNull instead of decodeSingle:
        //   decodeSingle throws when no row exists → getOrElse fallback always
        //   forces role = "user", so admins without a profiles row lose their role.
        //   decodeList returns an empty list on zero rows — no exception thrown.
        val found = runCatching {
            supabaseClient.postgrest["profiles"]
                .select {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<UserProfile>()
                .firstOrNull()
        }.getOrNull()   // null only on a real network/parse error

        _userProfile.value = found ?: run {
            // No profile row — build a minimal fallback from auth metadata
            val meta = user.userMetadata?.toString() ?: ""
            val username = Regex(""""username"\s*:\s*"([^"]+)"""")
                .find(meta)?.groupValues?.get(1)
                ?: user.email?.substringBefore("@")
                ?: "User"
            UserProfile(id = userId, username = username, role = "user")
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
            rawMessage == null -> "An error occurred. Please try again."
            rawMessage.contains("Invalid login credentials", ignoreCase = true) ->
                "Incorrect email/username or password."
            rawMessage.contains("User already registered", ignoreCase = true) ->
                "This email is already registered."
            rawMessage.contains("Password should be at least", ignoreCase = true) ->
                "Password must be at least 6 characters."
            rawMessage.contains("Unable to validate email", ignoreCase = true) ->
                "Invalid email address."
            rawMessage.contains("Email not confirmed", ignoreCase = true) ||
            rawMessage.contains("email_not_confirmed", ignoreCase = true) ->
                "Email not confirmed. Please check your inbox and click the confirmation link."
            rawMessage.contains("over_email_send_rate_limit", ignoreCase = true) ||
            rawMessage.contains("email rate limit", ignoreCase = true) ->
                "Too many requests. Please try again in a few minutes."
            rawMessage.contains("No account found", ignoreCase = true) ->
                rawMessage
            rawMessage.contains("network", ignoreCase = true) ||
            rawMessage.contains("connect", ignoreCase = true) ->
                "Unable to connect to the network. Check your connection."
            else -> rawMessage
        }
    }
}
