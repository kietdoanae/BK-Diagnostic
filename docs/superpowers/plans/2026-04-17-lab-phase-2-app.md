# Lab Phase 2 — Android App Lab Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Lab Mode to the Android app — 6-digit session pairing, real-time CAN evidence streaming to Supabase `lab_evidence`, persistent banner overlay, and tagging of active-test commands.

**Architecture:** `LabModeManager` (object singleton, `StateFlow<LabModeState>`) is the single source of truth for session state. `LabEvidenceRepository` (object singleton) receives frames/commands and inserts them into `lab_evidence` via the existing `supabaseClient`. UI observes `LabModeManager.state` via `collectAsStateWithLifecycle()`. The banner is a `Box` overlay in `MainActivity` — no Scaffold restructuring needed.

**Tech Stack:** Kotlin, Jetpack Compose, supabase-kt (`postgrest`, `auth`), `kotlinx.coroutines` (Mutex, SupervisorJob), `kotlinx.serialization.json` (buildJsonObject / buildJsonArray)

**Prerequisite:** Phase 1 DB migrations are applied — `lab_evidence`, `lab_sessions` tables and `validate_lab_code` RPC exist in Supabase.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `lab/LabModeState.kt` | **Create** | Sealed class `LabModeState` (Inactive / Active) + `ValidateLabCodeResponse` DTO |
| `lab/LabModeManager.kt` | **Create** | Object singleton — `StateFlow<LabModeState>`, `activate(code)`, `deactivate()` |
| `lab/LabEvidenceRepository.kt` | **Create** | Object singleton — frame queue + 2s/100-frame flush, `pushActiveTest`, `pushRawFrameBatch` |
| `ui/screens/LabModeScreen.kt` | **Create** | Compose screen — 6-digit input, RPC validate, active-state panel |
| `ui/components/LabModeBanner.kt` | **Create** | 32dp persistent overlay shown when `LabModeState.Active` |
| `ui/screens/DiagnosticScreen.kt` | **Modify** | Add `onLabModeClick` param + Lab Mode card in `DiagnosticHub` |
| `MainActivity.kt` | **Modify** | Wrap Scaffold with `Box` + `LabModeBanner`; add `lab_mode` route; thread `onLabModeClick` |
| `diagnostic/DiagnosticViewModel.kt` | **Modify** | `init` feeds `LabEvidenceRepository.enqueueRawFrame`; `sendActiveTestCommand` pushes `active_test` evidence |
| `ui/screens/RawMonitorScreen.kt` | **Modify** | `uploadExportToStorage` + call site pass `labSessionId`; call `LabEvidenceRepository.pushRawFrameBatch` |

---

## Task 1: LabModeState sealed class + ValidateLabCodeResponse DTO

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/lab/LabModeState.kt`

- [ ] **Step 1.1: Create the file**

```kotlin
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
```

- [ ] **Step 1.2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no errors in `lab/LabModeState.kt`)

- [ ] **Step 1.3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/lab/LabModeState.kt
git commit -m "feat(lab): add LabModeState sealed class + ValidateLabCodeResponse DTO"
```

---

## Task 2: LabEvidenceRepository

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/lab/LabEvidenceRepository.kt`

- [ ] **Step 2.1: Create the repository object**

```kotlin
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
import kotlinx.coroutines.cancel
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
```

- [ ] **Step 2.2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2.3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/lab/LabEvidenceRepository.kt
git commit -m "feat(lab): add LabEvidenceRepository with 2s/100-frame batching"
```

---

## Task 3: LabModeManager

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/lab/LabModeManager.kt`

- [ ] **Step 3.1: Create the manager object**

```kotlin
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
```

- [ ] **Step 3.2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3.3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/lab/LabModeManager.kt
git commit -m "feat(lab): add LabModeManager singleton with activate/deactivate via RPC"
```

---

## Task 4: LabModeScreen

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/ui/screens/LabModeScreen.kt`

- [ ] **Step 4.1: Create the Compose screen**

```kotlin
package com.example.bkdiagnostic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
import com.example.bkdiagnostic.ui.components.AppTopBar
import kotlinx.coroutines.launch

private val LabAmber = Color(0xFFF59E0B)
private val LabAmberLight = Color(0xFFFEF3C7)

@Composable
fun LabModeScreen(onBack: () -> Unit) {
    val state by LabModeManager.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is LabModeState.Inactive -> LabModeEntryPanel(onBack = onBack)
        is LabModeState.Active   -> LabModeActivePanel(active = s, onBack = onBack)
    }
}

// ── Entry panel — 6-digit code input ─────────────────────────────────────────

@Composable
private fun LabModeEntryPanel(onBack: () -> Unit) {
    var code       by remember { mutableStateOf("") }
    var isLoading  by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    val scope      = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppTopBar(
                title    = "Lab Mode",
                subtitle = "TR4021 Diagnostics Lab",
                onBack   = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F4F9))
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint     = LabAmber,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text       = "Enter Session Code",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF1A1A2E)
            )
            Text(
                text      = "Ask your group leader for the 6-digit code",
                fontSize  = 13.sp,
                color     = Color(0xFF666680),
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value         = code,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { code = it; errorMsg = null } },
                label         = { Text("6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                isError       = errorMsg != null,
                supportingText = errorMsg?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                modifier      = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (code.length != 6) { errorMsg = "Code must be exactly 6 digits"; return@Button }
                    isLoading = true
                    scope.launch {
                        val result = LabModeManager.activate(code)
                        isLoading = false
                        result.onFailure { e ->
                            errorMsg = e.message?.takeIf { it.isNotBlank() }
                                ?: "Invalid or expired code. Check with your group leader."
                        }
                        // On success LabModeManager.state switches to Active → recompose shows ActivePanel
                    }
                },
                enabled  = code.length == 6 && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LabAmber)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Activate Lab Mode", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Active panel — session info + exit ───────────────────────────────────────

@Composable
private fun LabModeActivePanel(active: LabModeState.Active, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppTopBar(
                title    = "Lab Mode",
                subtitle = active.labTitle,
                onBack   = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F4F9))
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LabAmberLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Science, null, tint = LabAmber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("LAB MODE ACTIVE", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF92400E))
                    }
                    InfoRow(label = "Lab",     value = active.labTitle)
                    InfoRow(label = "Group",   value = active.groupName)
                    InfoRow(label = "Code",    value = active.sessionCode)
                    InfoRow(label = "Expires", value = active.expiresAt.take(19).replace("T", " "))
                }
            }

            Text(
                text     = "CAN frames and active-test commands are now being tagged with this session and uploaded to the lab evidence table.",
                fontSize = 12.sp,
                color    = Color(0xFF666680),
                lineHeight = 18.sp
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick  = { LabModeManager.deactivate(); onBack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
            ) {
                Text("Exit Lab Mode", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text("$label: ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
        Text(value, fontSize = 13.sp, color = Color(0xFF78350F))
    }
}
```

- [ ] **Step 4.2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4.3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/LabModeScreen.kt
git commit -m "feat(lab): add LabModeScreen with 6-digit entry and active state panel"
```

---

## Task 5: LabModeBanner component

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/ui/components/LabModeBanner.kt`

- [ ] **Step 5.1: Create the banner composable**

```kotlin
package com.example.bkdiagnostic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bkdiagnostic.lab.LabModeState

private val BannerBg   = Color(0xFFF59E0B)
private val BannerText = Color.White

/**
 * Persistent 32dp banner shown at the top of every screen when Lab Mode is active.
 * Tap → [onManage] callback (navigates to LabModeScreen).
 */
@Composable
fun LabModeBanner(
    state:    LabModeState,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state !is LabModeState.Active) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(BannerBg)
            .clickable(onClick = onManage)
            .statusBarsPadding()          // stay below the system status bar
            .padding(horizontal = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Science,
            contentDescription = null,
            tint     = BannerText,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text       = "LAB MODE ACTIVE · ${state.labTitle} · ${state.sessionCode} · Tap to manage",
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = BannerText,
            maxLines   = 1
        )
    }
}
```

- [ ] **Step 5.2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5.3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/components/LabModeBanner.kt
git commit -m "feat(lab): add LabModeBanner persistent overlay component"
```

---

## Task 6: DiagnosticScreen — add Lab Mode card

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/screens/DiagnosticScreen.kt`

- [ ] **Step 6.1: Add `onLabModeClick` param to `DiagnosticScreen` and thread it to `DiagnosticHub`**

In `DiagnosticScreen.kt`, change the function signature from:
```kotlin
fun DiagnosticScreen(
    brandId: String,
    modelId: String,
    onBack: () -> Unit,
    application: android.app.Application,
    isAdmin: Boolean = false,
    diagSettings: DiagnosticsSettings = DiagnosticsSettings()
)
```
to:
```kotlin
fun DiagnosticScreen(
    brandId: String,
    modelId: String,
    onBack: () -> Unit,
    application: android.app.Application,
    isAdmin: Boolean = false,
    diagSettings: DiagnosticsSettings = DiagnosticsSettings(),
    onLabModeClick: () -> Unit = {}
)
```

Then find the `DiagnosticHub(...)` call inside `DiagnosticScreen` and add the param:
```kotlin
DiagView.HUB ->
    DiagnosticHub(
        viewModel      = viewModel,
        onBack         = onBack,
        onNavigate     = { currentView = it },
        isAdmin        = isAdmin,
        onLabModeClick = onLabModeClick   // ← add this line
    )
```

- [ ] **Step 6.2: Add `onLabModeClick` to `DiagnosticHub` signature**

Change `DiagnosticHub` signature from:
```kotlin
private fun DiagnosticHub(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit,
    onNavigate: (DiagView) -> Unit,
    isAdmin: Boolean = false
)
```
to:
```kotlin
private fun DiagnosticHub(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit,
    onNavigate: (DiagView) -> Unit,
    isAdmin: Boolean = false,
    onLabModeClick: () -> Unit = {}
)
```

- [ ] **Step 6.3: Add the Lab Mode card in `DiagnosticHub`**

Inside `DiagnosticHub`, add the Lab Mode card after the Active Test card and before the `ComingSoonCard` rows. Also add the required import for `LabModeManager` and `LabModeState`. The complete addition goes right after the Active Test `DiagnosticFunctionCard` block (around line 188):

```kotlin
// ── New imports needed at file top ────────────────────────────────────────────
// import androidx.lifecycle.compose.collectAsStateWithLifecycle  (already present)
// import com.example.bkdiagnostic.lab.LabModeManager
// import com.example.bkdiagnostic.lab.LabModeState
// import androidx.compose.material.icons.filled.Science
```

Inside the `DiagnosticHub` composable body, add after collecting `connectionState` and `message`:
```kotlin
val labModeState by LabModeManager.state.collectAsStateWithLifecycle()
val isLabActive  = labModeState is LabModeState.Active
```

Then add the card after the Active Test card:
```kotlin
// Lab Mode card
DiagnosticFunctionCard(
    icon        = Icons.Default.Science,
    title       = if (isLabActive) "Lab Mode — Active" else "Lab Mode",
    description = if (isLabActive) "Session in progress · Tap to manage"
                  else "Join a TR4021 lab session by entering a 6-digit code",
    accentColor = Color(0xFFF59E0B),
    enabled     = true,
    actionIcon  = Icons.Default.Science,
    actionLabel = if (isLabActive) "Manage" else "Enter",
    onClick     = onLabModeClick
)
```

- [ ] **Step 6.4: Add missing imports to DiagnosticScreen.kt**

At the top of `DiagnosticScreen.kt`, add:
```kotlin
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
import androidx.compose.material.icons.filled.Science
```

- [ ] **Step 6.5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6.6: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/DiagnosticScreen.kt
git commit -m "feat(lab): add Lab Mode card to DiagnosticHub"
```

---

## Task 7: MainActivity — LabModeBanner overlay + lab_mode route

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/MainActivity.kt`

- [ ] **Step 7.1: Add required imports**

At the top of `MainActivity.kt`, add:
```kotlin
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.ui.components.LabModeBanner
import com.example.bkdiagnostic.ui.screens.LabModeScreen
```

- [ ] **Step 7.2: Collect labModeState inside `BKDiagnosticTheme { ... }`**

Inside the `setContent { BKDiagnosticTheme { ... } }` block, right after `val navController = rememberNavController()`, add:
```kotlin
val labModeState by LabModeManager.state.collectAsStateWithLifecycle()
```

- [ ] **Step 7.3: Wrap Scaffold with Box overlay**

Change this structure:
```kotlin
Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    NavHost(...) { ... }
}
```
to:
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(...) { ... }
    }
    LabModeBanner(
        state    = labModeState,
        onManage = { navController.navigate("lab_mode") }
    )
}
```

- [ ] **Step 7.4: Add `lab_mode` composable route to NavHost**

Inside the `NavHost { ... }` block, add after the existing routes:
```kotlin
composable("lab_mode") {
    LabModeScreen(onBack = { navController.popBackStack() })
}
```

- [ ] **Step 7.5: Thread `onLabModeClick` to `DiagnosticScreen`**

Find the existing `composable("diagnostic/{brandId}/{modelId}")` block and add the `onLabModeClick` parameter:
```kotlin
DiagnosticScreen(
    brandId        = brandId,
    modelId        = modelId,
    onBack         = { navController.popBackStack() },
    application    = application,
    isAdmin        = canViewRawFrame,
    diagSettings   = diagSettings,
    onLabModeClick = { navController.navigate("lab_mode") }   // ← add this
)
```

- [ ] **Step 7.6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7.7: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/MainActivity.kt
git commit -m "feat(lab): add LabModeBanner overlay and lab_mode nav route in MainActivity"
```

---

## Task 8: DiagnosticViewModel — raw frame streaming + active_test evidence

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/diagnostic/DiagnosticViewModel.kt`

- [ ] **Step 8.1: Add imports**

At the top of `DiagnosticViewModel.kt`, add:
```kotlin
import com.example.bkdiagnostic.lab.LabEvidenceRepository
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
```

- [ ] **Step 8.2: Feed raw frames to LabEvidenceRepository in `init`**

Find the `init` block:
```kotlin
init {
    viewModelScope.launch {
        usbManager.canFrames.collect { frame ->
            dispatchFrame(frame)
            addToRawLog(frame)
        }
    }
}
```

Change it to:
```kotlin
init {
    viewModelScope.launch {
        usbManager.canFrames.collect { frame ->
            dispatchFrame(frame)
            addToRawLog(frame)
            // Lab: enqueue frame for evidence collection when session is active
            val labState = LabModeManager.state.value
            if (labState is LabModeState.Active) {
                LabEvidenceRepository.enqueueRawFrame(labState.sessionId, frame)
            }
        }
    }
}
```

- [ ] **Step 8.3: Push active_test evidence in `sendActiveTestCommand`**

Find the existing function:
```kotlin
fun sendActiveTestCommand(canId: Int, data: ByteArray) {
    viewModelScope.launch(Dispatchers.IO) {
        val frame = CanFrame(
            id = canId,
            dlc = data.size.coerceAtMost(8),
            data = ByteArray(8).also { buf -> data.copyInto(buf) }
        )
        usbManager.sendFrame(frame)
    }
}
```

Change it to:
```kotlin
fun sendActiveTestCommand(canId: Int, data: ByteArray) {
    viewModelScope.launch(Dispatchers.IO) {
        val frame = CanFrame(
            id  = canId,
            dlc = data.size.coerceAtMost(8),
            data = ByteArray(8).also { buf -> data.copyInto(buf) }
        )
        usbManager.sendFrame(frame)
        // Lab: push active_test evidence when session is active
        val labState = LabModeManager.state.value
        if (labState is LabModeState.Active) {
            LabEvidenceRepository.pushActiveTest(labState.sessionId, canId, data)
        }
    }
}
```

- [ ] **Step 8.4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8.5: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/diagnostic/DiagnosticViewModel.kt
git commit -m "feat(lab): feed raw frames and active-test commands to LabEvidenceRepository"
```

---

## Task 9: RawMonitorScreen — tag export with lab evidence

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/screens/RawMonitorScreen.kt`

- [ ] **Step 9.1: Add imports**

At the top of `RawMonitorScreen.kt`, add:
```kotlin
import com.example.bkdiagnostic.lab.LabEvidenceRepository
import com.example.bkdiagnostic.lab.LabModeManager
import com.example.bkdiagnostic.lab.LabModeState
```

- [ ] **Step 9.2: Add `labSessionId` parameter to `uploadExportToStorage`**

Find the function signature:
```kotlin
private suspend fun uploadExportToStorage(
    filename:    String,
    bytes:       ByteArray,
    brandId:     String,
    modelId:     String,
    displayName: String,
    frameCount:  Int
)
```

Change it to:
```kotlin
private suspend fun uploadExportToStorage(
    filename:     String,
    bytes:        ByteArray,
    brandId:      String,
    modelId:      String,
    displayName:  String,
    frameCount:   Int,
    labSessionId: String? = null
)
```

- [ ] **Step 9.3: Update the call site to pass `labSessionId`**

Find the `onExport` lambda in `RawMonitorScreen` that calls `uploadExportToStorage`. It currently looks like:
```kotlin
if (result != null) {
    CoroutineScope(Dispatchers.IO).launch {
        uploadExportToStorage(
            filename    = result.filename,
            bytes       = result.bytes,
            brandId     = viewModel.brandId,
            modelId     = viewModel.modelId,
            displayName = viewModel.protocolConfig?.displayName
                          ?: "${viewModel.brandId} ${viewModel.modelId}",
            frameCount  = displayLog.size
        )
    }
}
```

Change it to:
```kotlin
if (result != null) {
    CoroutineScope(Dispatchers.IO).launch {
        val labState     = LabModeManager.state.value
        val labSessionId = (labState as? LabModeState.Active)?.sessionId
        uploadExportToStorage(
            filename     = result.filename,
            bytes        = result.bytes,
            brandId      = viewModel.brandId,
            modelId      = viewModel.modelId,
            displayName  = viewModel.protocolConfig?.displayName
                           ?: "${viewModel.brandId} ${viewModel.modelId}",
            frameCount   = displayLog.size,
            labSessionId = labSessionId
        )
        // Also push the exported frames as lab evidence batch
        if (labSessionId != null) {
            LabEvidenceRepository.pushRawFrameBatch(labSessionId, displayLog)
        }
    }
}
```

- [ ] **Step 9.4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9.5: Build the full APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 9.6: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/RawMonitorScreen.kt
git commit -m "feat(lab): tag CSV export with lab_session_id; push batch evidence on export"
```

---

## Task 10: Manual Test Checklist

Install the debug APK on a device connected to the STM32 hardware and a Supabase project with Phase 1 migrations applied.

**Prerequisite setup:**
- Log in as a test user who belongs to a `lab_group` that has a `lab_session` in `ACTIVE` status.
- Note the 6-digit `session_code` of that session.

### 10.1 LabModeManager — activation

- [ ] Open app, navigate to any diagnostic screen
- [ ] Verify no banner is visible (Lab Mode is inactive)
- [ ] Navigate to Lab Mode screen via the Hub card
- [ ] Enter fewer than 6 digits → "Activate" button should be disabled
- [ ] Enter 6 digits that do NOT match any active session → tap Activate → error message appears (not a crash)
- [ ] Enter the correct 6-digit code → tap Activate → Active panel appears showing lab title, group name, code, expires_at
- [ ] Banner appears at the top of the screen: "LAB MODE ACTIVE · {lab title} · {code} · Tap to manage"

### 10.2 LabModeBanner — navigation

- [ ] Navigate back to DashboardScreen → banner still visible
- [ ] Navigate to DiagnosticScreen → banner still visible
- [ ] Tap the banner → returns to LabModeScreen showing Active panel

### 10.3 DiagnosticScreen — card state

- [ ] Open DiagnosticHub while Lab Mode is inactive → card shows "Lab Mode" / "Enter"
- [ ] Activate Lab Mode → go back to DiagnosticHub → card shows "Lab Mode — Active" / "Manage"

### 10.4 LabEvidenceRepository — raw frame streaming

- [ ] With Lab Mode active, connect USB to STM32 and start receiving CAN frames in RawMonitorScreen
- [ ] Wait ~10 seconds (at least 2 flush cycles)
- [ ] In Supabase Studio, run:
  ```sql
  SELECT id, evidence_type, created_at,
         jsonb_array_length(payload->'frames') AS frame_count
  FROM lab_evidence
  WHERE session_id = '<your-session-id>'
    AND evidence_type = 'raw_frame'
  ORDER BY created_at DESC
  LIMIT 5;
  ```
- [ ] Expect: rows with `evidence_type = 'raw_frame'` and `frame_count > 0`
- [ ] `step_id` column should be non-null if the web has `current_step_id` set, or null if no step is active

### 10.5 LabEvidenceRepository — active test evidence

- [ ] With Lab Mode active, navigate to ActiveTestScreen and tap any warning icon
- [ ] In Supabase Studio, run:
  ```sql
  SELECT id, evidence_type, payload, created_at
  FROM lab_evidence
  WHERE session_id = '<your-session-id>'
    AND evidence_type = 'active_test'
  ORDER BY created_at DESC
  LIMIT 3;
  ```
- [ ] Expect: row with `evidence_type = 'active_test'`, `payload` containing `can_id` and `data`

### 10.6 RawMonitorScreen — export + batch push

- [ ] With Lab Mode active and frames visible in RawMonitorScreen, tap Export CSV
- [ ] Verify CSV saves locally (existing behavior unchanged)
- [ ] In Supabase Studio, run:
  ```sql
  SELECT id, evidence_type, payload->>'source' AS source, created_at
  FROM lab_evidence
  WHERE session_id = '<your-session-id>'
    AND evidence_type = 'raw_frame'
    AND payload->>'source' = 'csv_export'
  ORDER BY created_at DESC
  LIMIT 3;
  ```
- [ ] Expect: row(s) with `source = 'csv_export'`

### 10.7 Deactivation

- [ ] Tap banner → LabModeScreen → tap "Exit Lab Mode"
- [ ] Banner disappears
- [ ] Lab Mode card in DiagnosticHub reverts to "Enter" state
- [ ] Re-entering any screen works normally (no crashes)
- [ ] CAN frames received after deactivation do NOT create new `lab_evidence` rows (verify in Supabase Studio)

### 10.8 App restart

- [ ] With Lab Mode active, kill and relaunch the app
- [ ] Lab Mode should be inactive (code cleared from memory — intentional per spec)
- [ ] Banner is not shown after restart

---

## Self-Review Notes

**Spec coverage check:**

| Spec requirement | Covered by |
|---|---|
| LabModeManager singleton StateFlow | Task 1, 3 |
| LabEvidenceRepository insert + batch 2s/100 | Task 2 |
| LabModeScreen 6-digit input + validate_lab_code | Task 4 |
| LabModeBanner persistent overlay | Task 5 |
| DiagnosticScreen Lab Mode card | Task 6 |
| MainActivity wrap with LabModeBanner | Task 7 |
| RawMonitorScreen.uploadExportToStorage lab tagging | Task 9 |
| DiagnosticViewModel.sendActiveTestCommand push evidence | Task 8 |
| Test checklist (manual) | Task 10 |
| Session code cleared on restart | Covered by object singleton (in-memory only, no persistence) |
| RLS INSERT-only (app never SELECTs lab_evidence) | LabEvidenceRepository only calls insert — no select |

**Type consistency check:**
- `LabModeState.Active.sessionId: String` ← used as `sessionId: String` in all `LabEvidenceRepository` functions ✓
- `ValidateLabCodeResponse` fields match `LabModeState.Active` constructor args ✓
- `RawFrameEntry` (imported from `com.example.bkdiagnostic.diagnostic`) used in `pushRawFrameBatch` ✓
- `CanFrame` (imported from `com.example.bkdiagnostic.communication`) used in `enqueueRawFrame` and `doInsertRawFrames` ✓
- `LabModeBanner` receives `LabModeState` (not `LabModeState.Active`) and guards internally ✓
- `DiagnosticScreen.onLabModeClick` default = `{}` maintains backward compatibility ✓
