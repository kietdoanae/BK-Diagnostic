package com.example.bkdiagnostic.lab

import android.util.Log
import com.example.bkdiagnostic.supabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "LabModeManager"

object LabModeManager {

    private val _state = MutableStateFlow<LabModeState>(LabModeState.Inactive)
    val state: StateFlow<LabModeState> = _state.asStateFlow()

    val currentSessionId: String?
        get() = (_state.value as? LabModeState.Active)?.sessionId

    // ── Called from LabModeScreen on button press ─────────────────────────────

    /** Validates [code] via RPC. Returns [Result.success] on success, [Result.failure] with
     *  a user-readable message on validation error or network failure. */
    suspend fun activate(code: String): Result<Unit> {
        return runCatching {
            val response = supabaseClient.postgrest
                .rpc("validate_lab_code", buildJsonObject { put("code", code) })
                .decodeAs<ValidateLabCodeResponse>()

            _state.value = LabModeState.Active(
                sessionId   = response.sessionId,
                labTitle    = response.labTitle,
                groupName   = response.groupName,
                sessionCode = code,
                expiresAt   = response.expiresAt
            )
            LabEvidenceRepository.onSessionActivated(response.sessionId)
            Log.d(TAG, "Lab Mode activated: session=${response.sessionId} lab=${response.labTitle}")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "activate failed: ${e.message}", e)
        }
    }

    // ── Called from LabModeScreen exit button or on app destroy ──────────────

    fun deactivate() {
        val sid = currentSessionId ?: return
        LabEvidenceRepository.onSessionDeactivated(sid)
        _state.value = LabModeState.Inactive
        Log.d(TAG, "Lab Mode deactivated")
    }
}
