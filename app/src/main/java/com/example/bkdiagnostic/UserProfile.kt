package com.example.bkdiagnostic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val role: String = "user"
) {
    val isAdmin: Boolean get() = role == "admin"
}

@Serializable
data class EmailLookupResult(
    val email: String
)
