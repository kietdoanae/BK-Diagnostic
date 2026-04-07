package com.example.bkdiagnostic.communication

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quản lý kết nối USB Serial với module CP2102 (STM32 → CP2102 → USB → Android).
 *
 * Sử dụng singleton qua [getInstance] — lấy từ [BKDiagnosticApp.usbSerialManager].
 *
 * Luồng sử dụng:
 *   1. Gọi [connect] khi thiết bị USB được cắm vào hoặc khi người dùng nhấn "Kết nối"
 *   2. Lắng nghe [connectionState] để biết trạng thái
 *   3. Lắng nghe [canFrames] để nhận CAN frame từ STM32
 *   4. Gọi [sendFrame] để gửi lệnh lên STM32 (ví dụ: OBD2 request)
 *   5. Gọi [disconnect] khi không cần nữa
 */
class UsbSerialManager private constructor(private val context: Context) {

    // ── Trạng thái kết nối ──────────────────────────────────────────────────

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Searching : ConnectionState()
        data class AwaitingPermission(val device: UsbDevice) : ConnectionState()
        data class Connected(val deviceName: String, val baudRate: Int) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    val isConnected get() = _state.value is ConnectionState.Connected

    // ── Luồng CAN frames đến ────────────────────────────────────────────────

    private val _canFrames = MutableSharedFlow<CanFrame>(extraBufferCapacity = 128)
    val canFrames: SharedFlow<CanFrame> = _canFrames.asSharedFlow()

    private val _rawFrames = MutableSharedFlow<ParsedFrame>(extraBufferCapacity = 64)
    val rawFrames: SharedFlow<ParsedFrame> = _rawFrames.asSharedFlow()

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    // ── Preferred settings (updated by SettingsViewModel) ───────────────────

    /** Tốc độ UART giữa Android → CP2102 → STM32. Mặc định 115200. */
    var preferredBaudRate: Int = 115200

    /** Tốc độ CAN bus gửi cho STM32 ngay sau khi kết nối. Mặc định 500 kbps. */
    var preferredCanKbps: Int = 500

    /**
     * Tự kết nối lại khi cổng serial bị ngắt (transient disconnect).
     * Lưu ý: chỉ hoạt động khi thiết bị vẫn còn cắm — không phát hiện
     * unplug/replug (cần ACTION_USB_DEVICE_ATTACHED cho trường hợp đó).
     */
    var autoReconnect: Boolean = false

    // ── Internal ─────────────────────────────────────────────────────────────

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serialPort: UsbSerialPort? = null
    private val parser = FrameProtocol.StreamParser()
    private var receiverRegistered = false

    private val ACTION_USB_PERMISSION = "com.example.bkdiagnostic.USB_PERMISSION"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (granted && device != null) {
                openPort(device)
            } else {
                _state.value = ConnectionState.Error("Quyền truy cập USB bị từ chối.")
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Tìm CP2102 đang cắm và yêu cầu kết nối.
     * Nếu chưa có quyền USB → hộp thoại hệ thống sẽ xuất hiện.
     * Nếu không tìm thấy thiết bị tương thích → [ConnectionState.Error].
     */
    fun connect(baudRate: Int = preferredBaudRate, canBaudKbps: Int = preferredCanKbps) {
        _state.value = ConnectionState.Searching

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            _state.value = ConnectionState.Error("Không tìm thấy thiết bị USB Serial (CP2102).")
            return
        }

        val driver = drivers.first()
        val device = driver.device

        registerPermissionReceiver()

        if (usbManager.hasPermission(device)) {
            openPort(device, baudRate, canBaudKbps)
        } else {
            _state.value = ConnectionState.AwaitingPermission(device)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, 0,
                Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, pi)
        }
    }

    /** Ngắt kết nối và dọn dẹp */
    fun disconnect() {
        runCatching { serialPort?.close() }
        serialPort = null
        parser.reset()
        _state.value = ConnectionState.Disconnected
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(permissionReceiver) }
            receiverRegistered = false
        }
    }

    /**
     * Gửi 1 CAN frame lên STM32 (sẽ được STM32 đặt lên CAN bus).
     * Không chờ phản hồi — phản hồi sẽ đến qua [canFrames].
     */
    suspend fun sendFrame(frame: CanFrame) = withContext(Dispatchers.IO) {
        val bytes = FrameProtocol.encodeSendCan(frame)
        runCatching { serialPort?.write(bytes, 200) }
    }

    /** Gửi lệnh đặt tốc độ CAN bus cho STM32 (gọi trước khi bắt đầu chẩn đoán) */
    suspend fun setCanBaud(baudKbps: Int) = withContext(Dispatchers.IO) {
        val bytes = FrameProtocol.encodeSetBaud(baudKbps)
        runCatching { serialPort?.write(bytes, 200) }
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    private fun openPort(device: UsbDevice, baudRate: Int = preferredBaudRate,
                          canBaudKbps: Int = preferredCanKbps) {
        scope.launch {
            runCatching {
                val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                val driver = drivers.firstOrNull { it.device == device }
                    ?: throw Exception("Không tìm thấy driver cho thiết bị.")

                val connection = usbManager.openDevice(driver.device)
                    ?: throw Exception("Không mở được kết nối USB.")

                val port = driver.ports[0]
                port.open(connection)
                port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                serialPort = port

                _state.value = ConnectionState.Connected(
                    deviceName = device.productName ?: "CP2102",
                    baudRate = baudRate
                )

                // Đặt tốc độ CAN bus theo setting (mặc định 500 kbps)
                delay(200)
                val baudCmd = FrameProtocol.encodeSetBaud(canBaudKbps)
                port.write(baudCmd, 500)

                // Bắt đầu vòng lặp đọc
                startReadLoop(port)
            }.onFailure { e ->
                _state.value = ConnectionState.Error(e.message ?: "Lỗi kết nối USB.")
            }
        }
    }

    private fun startReadLoop(port: UsbSerialPort) {
        scope.launch {
            val buf = ByteArray(256)
            while (isActive && serialPort != null) {
                runCatching {
                    val len = port.read(buf, 100)
                    if (len > 0) {
                        val frames = parser.feed(buf.copyOf(len))
                        frames.forEach { pf -> handleParsedFrame(pf) }
                    }
                }.onFailure {
                    // Cổng bị đóng hoặc thiết bị rút ra
                    serialPort = null
                    _state.value = ConnectionState.Disconnected
                    if (autoReconnect) {
                        delay(2000)
                        connect()   // dùng lại preferredBaudRate / preferredCanKbps
                    }
                    return@launch
                }
            }
        }
    }

    private suspend fun handleParsedFrame(pf: ParsedFrame) {
        _rawFrames.emit(pf)          // emit all frames for external observers (e.g. CanSenderViewModel)
        when (pf.type) {
            FrameProtocol.TYPE_CAN_RX -> {
                val frame = FrameProtocol.parseCanPayload(pf.payload) ?: return
                _canFrames.emit(frame)
            }
            FrameProtocol.TYPE_ERROR -> {
                // Handled by rawFrames subscribers (e.g. CanSenderViewModel)
            }
            // ACK and STATUS: handled by rawFrames subscribers
        }
    }

    private fun registerPermissionReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
        receiverRegistered = true
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    // ── Singleton ────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: UsbSerialManager? = null

        fun getInstance(context: Context): UsbSerialManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsbSerialManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
