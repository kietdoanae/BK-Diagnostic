package com.example.bkdiagnostic

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Ghi log hoạt động người dùng lên bảng activity_logs của Supabase.
 * Fire-and-forget: không block UI, lỗi ghi log không crash app.
 */
object ActivityLogger {

    private const val TAG = "ActivityLogger"

    // ── Hằng số action ────────────────────────────────────────────────────────
    const val LOGIN              = "LOGIN"
    const val LOGIN_FAILED       = "LOGIN_FAILED"
    const val LOGOUT             = "LOGOUT"
    const val REGISTER           = "REGISTER"
    const val PASSWORD_RESET     = "PASSWORD_RESET"
    // Navigation / Feature
    const val FEATURE_OPEN       = "FEATURE_OPEN"
    const val BRAND_SELECTED     = "BRAND_SELECTED"
    const val BRAND_COMING_SOON  = "BRAND_COMING_SOON"
    const val MODEL_SELECTED     = "MODEL_SELECTED"
    // Diagnostic
    const val DIAGNOSTIC_START   = "DIAGNOSTIC_START"
    const val DIAGNOSTIC_STOP    = "DIAGNOSTIC_STOP"
    const val LIVE_DATA_START    = "LIVE_DATA_START"
    const val LIVE_DATA_STOP     = "LIVE_DATA_STOP"
    const val DTC_READ           = "DTC_READ"
    const val DTC_CLEAR          = "DTC_CLEAR"
    const val ACTIVE_TEST_RUN    = "ACTIVE_TEST_RUN"
    const val RAW_EXPORT         = "RAW_EXPORT"

    // ── Scope riêng để không phụ thuộc vào ViewModel lifecycle ───────────────
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Data class để insert vào Supabase ────────────────────────────────────
    @Serializable
    private data class ActivityLogEntry(
        @SerialName("user_id")  val userId:   String,
        val username:   String,
        val action:     String,
        val details:    JsonObject,
        val platform:   String = "app"
    )

    /**
     * Ghi log với user_id và username tường minh.
     * Dùng khi đã có thông tin user (vd: sau login thành công).
     */
    fun log(
        userId: String,
        username: String,
        action: String,
        details: JsonObject = buildJsonObject {}
    ) {
        scope.launch {
            runCatching {
                supabaseClient.postgrest["activity_logs"].insert(
                    ActivityLogEntry(
                        userId   = userId,
                        username = username,
                        action   = action,
                        details  = details
                    )
                )
                Log.d(TAG, "Logged: $action | user=$username")
            }.onFailure { e ->
                Log.e(TAG, "Failed to insert log [$action] user=$username: ${e.message}", e)
            }
        }
    }

    /**
     * Ghi log tự động lấy user từ session hiện tại.
     * Dùng từ bất kỳ đâu khi không có UserProfile trong tay.
     * Lưu ý: nếu không có session (chưa login), log sẽ bị bỏ qua.
     */
    fun logCurrent(
        action: String,
        details: JsonObject = buildJsonObject {}
    ) {
        scope.launch {
            runCatching {
                val user = supabaseClient.auth.currentUserOrNull()
                if (user == null) {
                    Log.w(TAG, "logCurrent[$action]: no active session, skipping")
                    return@runCatching
                }
                val meta = user.userMetadata?.toString() ?: ""
                val username = Regex(""""username"\s*:\s*"([^"]+)"""")
                    .find(meta)?.groupValues?.get(1)
                    ?: user.email?.substringBefore("@")
                    ?: "unknown"
                supabaseClient.postgrest["activity_logs"].insert(
                    ActivityLogEntry(
                        userId   = user.id,
                        username = username,
                        action   = action,
                        details  = details
                    )
                )
                Log.d(TAG, "Logged: $action | user=$username")
            }.onFailure { e ->
                Log.e(TAG, "Failed to insert log [$action]: ${e.message}", e)
            }
        }
    }

    /**
     * Ghi log cho trường hợp không có session (vd: login failed).
     * Dùng user_id = "anonymous" để bypass RLS — cần policy riêng hoặc service role.
     * Hiện tại: chỉ log ra Logcat, không insert vào DB.
     */
    fun logAnonymous(action: String, details: JsonObject = buildJsonObject {}) {
        Log.i(TAG, "Anonymous event: $action | details=$details")
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    fun diagnosticStart(userId: String, username: String, brand: String, model: String) =
        log(userId, username, DIAGNOSTIC_START, buildJsonObject {
            put("brand", brand)
            put("model", model)
        })

    fun diagnosticStop(userId: String, username: String, brand: String, model: String) =
        log(userId, username, DIAGNOSTIC_STOP, buildJsonObject {
            put("brand", brand)
            put("model", model)
        })

    fun activeTestRun(userId: String, username: String, testName: String, result: String) =
        log(userId, username, ACTIVE_TEST_RUN, buildJsonObject {
            put("test", testName)
            put("result", result)
        })

    fun rawExport(userId: String, username: String, frameCount: Int) =
        log(userId, username, RAW_EXPORT, buildJsonObject {
            put("frames", frameCount)
        })

    // ── Helpers dùng logCurrent (không cần UserProfile) ───────────────────────

    fun featureOpen(featureName: String) =
        logCurrent(FEATURE_OPEN, buildJsonObject { put("feature", featureName) })

    fun brandSelected(brandName: String) =
        logCurrent(BRAND_SELECTED, buildJsonObject { put("brand", brandName) })

    fun brandComingSoon(brandName: String) =
        logCurrent(BRAND_COMING_SOON, buildJsonObject { put("brand", brandName) })

    fun modelSelected(brandName: String, modelName: String, category: String, years: String) =
        logCurrent(MODEL_SELECTED, buildJsonObject {
            put("brand", brandName)
            put("model", modelName)
            put("category", category)
            put("years", years)
        })

    fun liveDataStart(brandId: String, modelId: String) =
        logCurrent(LIVE_DATA_START, buildJsonObject {
            put("brand", brandId)
            put("model", modelId)
        })

    fun liveDataStop(brandId: String, modelId: String) =
        logCurrent(LIVE_DATA_STOP, buildJsonObject {
            put("brand", brandId)
            put("model", modelId)
        })

    fun dtcRead(brandId: String, modelId: String, dtcCount: Int) =
        logCurrent(DTC_READ, buildJsonObject {
            put("brand", brandId)
            put("model", modelId)
            put("dtc_count", dtcCount)
        })

    fun dtcClear(brandId: String, modelId: String) =
        logCurrent(DTC_CLEAR, buildJsonObject {
            put("brand", brandId)
            put("model", modelId)
        })

    fun activeTest(brandId: String, modelId: String, testName: String) =
        logCurrent(ACTIVE_TEST_RUN, buildJsonObject {
            put("brand", brandId)
            put("model", modelId)
            put("test", testName)
        })
}
