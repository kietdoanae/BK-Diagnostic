package com.example.bkdiagnostic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val role: String = "user",
    val status: String = "active",
    val mssv: String? = null,
    @SerialName("full_name")
    val fullName: String? = null
) {
    val isAdmin: Boolean       get() = role.equals("admin",     ignoreCase = true)
    val isModerator: Boolean   get() = role.equals("moderator", ignoreCase = true)
    val canViewRawFrame: Boolean get() = isAdmin || isModerator
    val isBlocked: Boolean     get() = status.lowercase() in setOf("banned", "suspended", "inactive", "pending")
    val blockedMessage: String get() = when (status.lowercase()) {
        "banned"    -> "Your account has been permanently banned. Contact support if you think this is a mistake."
        "suspended" -> "Your account has been temporarily suspended. Please contact support."
        "inactive"  -> "Your account is inactive. Please contact support to reactivate."
        "pending"   -> "Your account is pending approval by an administrator. Please check back later."
        else        -> "Your account is not active."
    }
}

@Serializable
data class EmailLookupResult(
    val email: String
)
