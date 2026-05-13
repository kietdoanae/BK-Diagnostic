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
    // Phase 2: queue entries (not raw frames) to preserve direction + source
    private val entryQueue = mutableListOf<com.example.bkdiagnostic.diagnostic.RawFrameEntry>()
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
        // Final flush then clear — sequential to avoid losing queued frames
        scope.launch {
            flushQueue(sessionId)
            mutex.withLock { entryQueue.clear() }
        }
    }

    // ── Real-time streaming path (called per-entry from UnifiedRawFrameStore) ──

    /**
     * Enqueue 1 entry (TX hoặc RX) cho lab session đang active.
     * Auto-flush khi queue đạt FLUSH_THRESHOLD.
     */
    fun enqueueEntry(sessionId: String, entry: com.example.bkdiagnostic.diagnostic.RawFrameEntry) {
        scope.launch {
            val toInsert = mutex.withLock {
                entryQueue.add(entry)
                if (entryQueue.size >= FLUSH_THRESHOLD) {
                    entryQueue.toList().also { entryQueue.clear() }
                } else null
            }
            if (toInsert != null) doInsertEntries(sessionId, toInsert)
        }
    }

    /**
     * @deprecated Backwards-compat shim — chuyển sang [enqueueEntry] có direction tag.
     */
    @Deprecated("Use enqueueEntry(sessionId, RawFrameEntry) to preserve direction tag")
    fun enqueueRawFrame(sessionId: String, frame: CanFrame) {
        // Map sang RX entry mặc định (giữ behavior cũ trước Phase 2)
        val entry = com.example.bkdiagnostic.diagnostic.RawFrameEntry(
            seq = 0,
            timestampMs = System.currentTimeMillis(),
            direction = com.example.bkdiagnostic.diagnostic.Direction.RX,
            canId = frame.id,
            rawBytes = frame.effectiveData(),
            decoded = "",
            source = "bus"
        )
        enqueueEntry(sessionId, entry)
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
                                        put("dir",     entry.direction.name)
                                        put("src",     entry.source)
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
            if (entryQueue.isEmpty()) return
            entryQueue.toList().also { entryQueue.clear() }
        }
        doInsertEntries(sessionId, toInsert)
    }

    private suspend fun doInsertEntries(
        sessionId: String,
        entries: List<com.example.bkdiagnostic.diagnostic.RawFrameEntry>
    ) {
        val user = supabaseClient.auth.currentUserOrNull() ?: return
        runCatching {
            supabaseClient.postgrest["lab_evidence"].insert(
                LabEvidenceRow(
                    sessionId         = sessionId,
                    submittedBy       = user.id,
                    evidenceType      = "raw_frame",
                    payload           = buildJsonObject {
                        put("frames", buildJsonArray {
                            entries.forEach { entry ->
                                add(buildJsonObject {
                                    put("seq",     entry.seq)
                                    put("dir",     entry.direction.name)
                                    put("src",     entry.source)
                                    put("can_id",  "0x%03X".format(entry.canId))
                                    put("dlc",     entry.rawBytes.size)
                                    put("data",    entry.rawBytes.joinToString(" ") { "%02X".format(it) })
                                    put("ts_ms",   entry.timestampMs)
                                    put("decoded", entry.decoded)
                                })
                            }
                        })
                    },
                    clientTimestampMs = System.currentTimeMillis()
                )
            )
        }.onFailure { Log.e(TAG, "raw_frame insert failed (${entries.size} entries): ${it.message}") }
    }
}
