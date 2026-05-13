package com.example.bkdiagnostic.diagnostic

import com.example.bkdiagnostic.communication.CanFrame
import com.example.bkdiagnostic.lab.LabEvidenceRepository
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Hướng (chiều) của 1 CAN frame trong log thống nhất.
 *
 *  - [TX] = Android **gửi** ra bus (Live Data request, DTC, Active Test, Gauge stream, manual)
 *  - [RX] = Android **nhận** từ bus (response của ECU, broadcast khác)
 */
enum class Direction { TX, RX }

/**
 * Singleton lưu trữ tất cả CAN frames đã đi qua app (cả 2 chiều).
 *
 *  Dùng làm **single source of truth** cho:
 *    - RawMonitor UI (Monitor tab + Send CAN tab)
 *    - CSV export
 *    - Supabase upload
 *
 *  Thread-safe: dùng `ConcurrentLinkedDeque` + `AtomicInteger`.
 *  Memory: 20,000 entries × ~64 byte/entry ≈ 1.2 MB RAM.
 *
 *  Gauge streaming có cơ chế **delta logging** — chỉ ghi khi giá trị thay đổi
 *  ≥ ngưỡng để tránh ngập log với data lặp lại.
 */
object UnifiedRawFrameStore {

    /** Maximum entries giữ trong ring buffer */
    const val MAX_LOG = 20_000

    /** Gauge delta threshold — chỉ log nếu RPM/Speed thay đổi ≥ giá trị này */
    private const val GAUGE_DELTA_THRESHOLD = 50

    /** Số frame tối thiểu để emit snapshot mới (giảm recomposition) */
    private const val SNAPSHOT_BATCH = 10

    // ── Storage ──────────────────────────────────────────────────────────────

    private val deque = ConcurrentLinkedDeque<RawFrameEntry>()
    private val _flow = MutableStateFlow<List<RawFrameEntry>>(emptyList())
    val flow: StateFlow<List<RawFrameEntry>> = _flow.asStateFlow()

    private val seqCounter = AtomicInteger(0)

    // ── Gauge delta state ────────────────────────────────────────────────────

    @Volatile private var lastGaugeRpm:   Int = Int.MIN_VALUE
    @Volatile private var lastGaugeSpeed: Int = Int.MIN_VALUE

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Ghi 1 frame nhận về từ CAN bus (chiều RX).
     */
    fun addRx(frame: CanFrame, decoded: String = "") {
        addEntry(
            direction = Direction.RX,
            canId     = frame.id,
            rawBytes  = frame.effectiveData(),
            decoded   = decoded,
            source    = "bus"
        )
    }

    /**
     * Ghi 1 frame Android gửi ra (chiều TX).
     *
     * @param source  Phân loại nguồn gốc: "live_data" / "dtc_read" / "dtc_clear"
     *                / "active_test" / "manual" / "gauge_start" / "gauge_stop"
     */
    fun addTx(frame: CanFrame, source: String, decoded: String = "") {
        addEntry(
            direction = Direction.TX,
            canId     = frame.id,
            rawBytes  = frame.effectiveData(),
            decoded   = decoded,
            source    = source
        )
    }

    /**
     * Gauge streaming delta logging — chỉ ghi khi value thay đổi ≥ threshold.
     *
     *  - START/STOP frame luôn được ghi (force = true)
     *  - Slider mid-drag: chỉ ghi nếu |new - last| ≥ 50
     *
     * Trả về true nếu entry đã được ghi.
     */
    fun addGaugeFrame(
        frame: CanFrame,
        kind: GaugeKind,
        currentValue: Int,
        force: Boolean = false,
    ): Boolean {
        val last = when (kind) {
            GaugeKind.RPM   -> lastGaugeRpm
            GaugeKind.SPEED -> lastGaugeSpeed
        }
        val changed = last == Int.MIN_VALUE ||
                      abs(currentValue - last) >= GAUGE_DELTA_THRESHOLD
        if (!force && !changed) return false

        // Cập nhật state
        when (kind) {
            GaugeKind.RPM   -> lastGaugeRpm   = currentValue
            GaugeKind.SPEED -> lastGaugeSpeed = currentValue
        }

        val label = when (kind) {
            GaugeKind.RPM   -> "Gauge RPM=$currentValue"
            GaugeKind.SPEED -> "Gauge SPEED=$currentValue km/h"
        }
        val src = when {
            force                   -> "gauge_event"   // START / STOP / reset
            else                    -> "gauge_delta"
        }
        addEntry(
            direction = Direction.TX,
            canId     = frame.id,
            rawBytes  = frame.effectiveData(),
            decoded   = label,
            source    = src
        )
        return true
    }

    /** Reset gauge delta tracking — gọi khi START hoặc STOP gauge stream. */
    fun resetGaugeDelta() {
        lastGaugeRpm   = Int.MIN_VALUE
        lastGaugeSpeed = Int.MIN_VALUE
    }

    /** Xoá toàn bộ log. Gọi từ UI khi user nhấn CLEAR. */
    fun clear() {
        deque.clear()
        seqCounter.set(0)
        resetGaugeDelta()
        _flow.value = emptyList()
    }

    /** Snapshot list hiện tại (thread-safe). */
    fun snapshot(): List<RawFrameEntry> = deque.toList()

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun addEntry(
        direction: Direction,
        canId: Int,
        rawBytes: ByteArray,
        decoded: String,
        source: String,
    ) {
        val entry = RawFrameEntry(
            seq         = seqCounter.incrementAndGet(),
            timestampMs = System.currentTimeMillis(),
            direction   = direction,
            canId       = canId,
            rawBytes    = rawBytes,
            decoded     = decoded,
            source      = source
        )
        // O(1) trim oldest if at capacity
        while (deque.size >= MAX_LOG) {
            deque.pollFirst()
        }
        deque.addLast(entry)

        // Emit snapshot mỗi 10 entries hoặc khi log còn rỗng
        if (entry.seq % SNAPSHOT_BATCH == 0 || _flow.value.isEmpty()) {
            _flow.value = deque.toList()
        }

        // Forward to Supabase (Lab evidence) if session is active.
        // LabEvidenceRepository tự batch ở mức 100 frame để giảm số insert.
        val labState = LabModeManager.state.value
        if (labState is LabModeState.Active) {
            LabEvidenceRepository.enqueueEntry(labState.sessionId, entry)
        }
    }

    /** Force emit snapshot — dùng khi UI cần fresh data ngay (vd: trước export). */
    fun forceEmitSnapshot() {
        _flow.value = deque.toList()
    }
}

enum class GaugeKind { RPM, SPEED }
