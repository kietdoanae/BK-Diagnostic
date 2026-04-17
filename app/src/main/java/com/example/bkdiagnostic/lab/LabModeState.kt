package com.example.bkdiagnostic.lab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class LabModeState {
    object Inactive : LabModeState()

    data class Active(
        val sessionId: String,
        val labTitle: String,
        val groupName: String,
        val sessionCode: String,
        val expiresAt: String
    ) : LabModeState()
}

@Serializable
data class ValidateLabCodeResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("lab_id")     val labId: String,
    @SerialName("lab_title")  val labTitle: String,
    @SerialName("group_name") val groupName: String,
    @SerialName("expires_at") val expiresAt: String
)
