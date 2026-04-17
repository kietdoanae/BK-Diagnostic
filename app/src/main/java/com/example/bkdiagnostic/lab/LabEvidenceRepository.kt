package com.example.bkdiagnostic.lab

import android.util.Log
import com.example.bkdiagnostic.communication.CanFrame
import com.example.bkdiagnostic.diagnostic.RawFrameEntry
import com.example.bkdiagnostic.supabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

private const val TAG = "LabEvidence"
private const val FLUSH_INTERVAL_MS = 2_000L
private const val FLUSH_THRESHOLD   = 100

@Serializable
private data class LabEvidenceRow(
    @SerialName("session_id")           val sessionId:          String,
    @SerialName("submitted_by")         val submittedBy:        String,
    @SerialName("evidence_type")        val evidenceType:       String,
    val payload:                                                 kotlinx.serialization.json.JsonObject,
    @SerialName("client_timestamp_ms")  val clientTimestampMs:  Long
)

object LabEvidenceRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val frameQueue = mutableListOf<CanFrame>()
    private var flushJob: Job? = null

    // ── Called by LabModeManager when session activates ───────────────────────

    fun onSessionActivated(sessionId: String) {
        flushJob?.cancel()
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushQueue(sessionId)
            }
        }
    }

    // ── Called by LabModeManager when session deactivates ─────────────────────

    fun onSessionDeactivated(sessionId: String) {
        flushJob?.cancel()
        flushJob = null
        // Final flush — fire-and-forget on IO
        scope.launch { flushQueue(sessionId) }
        scope.launch { mutex.withLock { frameQueue.clear() } }
    }

    // ── Real-time streaming path (called per-frame from DiagnosticViewModel) ──

    fun enqueueRawFrame(sessionId: String, frame: CanFrame) {
        scope.launch {
            val toInsert = mutex.withLock {
                frameQueue.add(frame)
                if (frameQueue.size >= FLUSH_THRESHOLD) {
                    frameQueue.toList().also { frameQueue.clear() }
                } else null
            }
            if (toInsert != null) doInsertRawFrames(sessionId, toInsert)
        }
    }

    // ── Active test path (called per-command from DiagnosticViewModel) ────────

    suspend fun pushActiveTest(sessionId: String, canId: Int, data: ByteArray) {
        val user = supabaseClient.auth.currentUserOrNull() ?: return
        runCatching {
            supabaseClient.postgrest["lab_evidence"].insert(
                LabEvidenceRow(
                    sessionId         = sessionId,
                    submittedBy       = user.id,
                    evidenceType      = "active_test",
                    payload           = buildJsonObject {
                        put("can_id", "0x%03X".format(canId))
                        put("data",   data.joinToString(" ") { "%02X".format(it) })
                    },
                    clientTimestampMs = System.currentTimeMillis()
                )
            )
        }.onFailure { Log.e(TAG, "active_test insert failed: ${it.message}") }
    }

    // ── Batch export path (called from uploadExportToStorage) ─────────────────

    suspend fun pushRawFrameBatch(sessionId: String, frames: List<RawFrameEntry>) {
        if (frames.isEmpty()) return
        val user = supabaseClient.auth.currentUserOrNull() ?: return
        frames.chunked(100).forEach { chunk ->
            runCatching {
                supabaseClient.postgrest["lab_evidence"].insert(
                    LabEvidenceRow(
                        sessionId         = sessionId,
                        submittedBy       = user.id,
                        evidenceType      = "raw_frame",
                        payload           = buildJsonObject {
                            put("source", "csv_export")
                            put("frames", buildJsonArray {
                                chunk.forEach { entry ->
                                    add(buildJsonObject {
                                        put("seq",     entry.seq)
                                        put("can_id",  "0x%03X".format(entry.canId))
                                        put("ts_ms",   entry.timestampMs)
                                        put("data",    entry.rawBytes.joinToString(" ") { "%02X".format(it) })
                                        put("decoded", entry.decoded)
                                    })
                                }
                            })
                        },
                        clientTimestampMs = System.currentTimeMillis()
                    )
                )
            }.onFailure { Log.e(TAG, "batch insert chunk failed: ${it.message}") }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private suspend fun flushQueue(sessionId: String) {
        val toInsert = mutex.withLock {
            if (frameQueue.isEmpty()) return
            frameQueue.toList().also { frameQueue.clear() }
        }
        doInsertRawFrames(sessionId, toInsert)
    }

    private suspend fun doInsertRawFrames(sessionId: String, frames: List<CanFrame>) {
        val user = supabaseClient.auth.currentUserOrNull() ?: return
        runCatching {
            supabaseClient.postgrest["lab_evidence"].insert(
                LabEvidenceRow(
                    sessionId         = sessionId,
                    submittedBy       = user.id,
                    evidenceType      = "raw_frame",
                    payload           = buildJsonObject {
                        put("frames", buildJsonArray {
                            frames.forEach { frame ->
                                add(buildJsonObject {
                                    put("can_id", "0x%03X".format(frame.id))
                                    put("dlc",    frame.dlc)
                                    put("data",   frame.effectiveData()
                                            .joinToString(" ") { "%02X".format(it) })
                                    put("ts_ms",  System.currentTimeMillis())
                                })
                            }
                        })
                    },
                    clientTimestampMs = System.currentTimeMillis()
                )
            )
        }.onFailure { Log.e(TAG, "raw_frame insert failed (${frames.size} frames): ${it.message}") }
    }
}
