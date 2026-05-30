package com.bikebike.app.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.bikebike.app.ble.*
import com.bikebike.app.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class BikeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "BikeViewModel"
    private val settings = AppSettings(application)

    // Identity state
    private val _identityLoaded = MutableStateFlow(settings.hasIdentity())
    val identityLoaded: StateFlow<Boolean> = _identityLoaded.asStateFlow()

    private val _identityInfo = MutableStateFlow(
        if (settings.hasIdentity()) "${settings.bikeName} (${settings.bikeMac})" else ""
    )
    val identityInfo: StateFlow<String> = _identityInfo.asStateFlow()

    // Log toggle
    private val _logEnabled = MutableStateFlow(settings.logEnabled)
    val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()

    private val bleManager = BleManager(application).apply {
        onDeviceFound = { device ->
            val current = _scannedDevices.value.toMutableList()
            val idx = current.indexOfFirst { it.address == device.address }
            if (idx >= 0) current[idx] = device else current.add(device)
            _scannedDevices.value = current
        }

        onConnectionStateChanged = { state ->
            _connectionState.value = state
        }

        onDataReceived = { data ->
            parseNotification(data)
        }

        onLog = { line ->
            if (_logEnabled.value) {
                val current = _logLines.value.toMutableList()
                current.add(line)
                if (current.size > 500) {
                    _logLines.value = current.takeLast(500)
                } else {
                    _logLines.value = current
                }
            }
        }
    }

    // Init: load identity if available
    init {
        val packets = settings.loadHandshakePackets()
        if (packets.isNotEmpty()) {
            bleManager.setHandshakePackets(packets)
        }
    }

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Scanned devices
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    // Scanning state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Bike metrics
    private val _metrics = MutableStateFlow(BikeMetricsData())
    val metrics: StateFlow<BikeMetricsData> = _metrics.asStateFlow()

    // Current resistance
    private val _currentResistance = MutableStateFlow(1)
    val currentResistance: StateFlow<Int> = _currentResistance.asStateFlow()

    // Log lines for UI display
    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    // Speed calculation state
    private var prevDistance: Int = 0
    private var prevDuration: Int = 0

    // ======================== Public API ========================

    fun startScan() {
        _scannedDevices.value = emptyList()
        _isScanning.value = true
        bleManager.startScan()
        Handler(Looper.getMainLooper()).postDelayed({
            _isScanning.value = false
        }, 11_000)
    }

    fun connectToDevice(device: ScannedDevice) {
        settings.lastDeviceAddress = device.address
        bleManager.connect(device.address)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun setResistance(level: Int) {
        if (level < 1 || level > 24) return
        bleManager.setResistance(level)
        _currentResistance.value = level
    }

    fun setLogEnabled(enabled: Boolean) {
        _logEnabled.value = enabled
        settings.logEnabled = enabled
        if (!enabled) {
            _logLines.value = emptyList()
        }
    }

    fun clearLog() {
        _logLines.value = emptyList()
    }

    /**
     * Import identity.json from URI (file picker result)
     */
    fun importIdentity(uri: Uri): Result<Int> {
        return try {
            val ctx = getApplication<Application>()
            val json = ctx.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            } ?: throw Exception("Cannot open file")

            val obj = JSONObject(json)
            val bikeName = obj.optString("bike_name", "Unknown")
            val bikeMac = obj.optString("bike_mac", "")
            val arr = obj.optJSONArray("handshake_packets")
                ?: throw Exception("No handshake_packets found")

            val packets = (0 until arr.length()).map { arr.getString(it) }
            if (packets.isEmpty()) throw Exception("handshake_packets is empty")

            settings.saveIdentity(bikeName, bikeMac, packets)
            bleManager.setHandshakePackets(packets)

            _identityLoaded.value = true
            _identityInfo.value = "$bikeName ($bikeMac)"

            Result.success(packets.size)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            Result.failure(e)
        }
    }

    // ======================== Notification Parsing ========================

    private fun parseNotification(data: ByteArray) {
        try {
            val marker = KeepBleConstants.DATA_MARKER
            val markerIdx = findSubsequence(data, marker)
            if (markerIdx < 0) {
                Log.d(TAG, "No data marker found in notification")
                return
            }

            val startPtr = markerIdx + marker.size
            if (startPtr >= data.size) return

            val pbData = data.copyOfRange(startPtr, data.size - 2)
            val fields = decodeProtobuf(pbData)

            val rpm = (fields[6] ?: 0L).toInt()
            val power = (fields[7] ?: 0L).toInt()
            val duration = (fields[3] ?: 0L).toInt()
            val distance = (fields[2] ?: 0L).toInt()
            val resistance = (fields[5] ?: 1L).toInt()
            val calories = (fields[4] ?: 0L).toFloat()
            val statusCode = (fields[8] ?: 0L).toInt()

            val speed = if (duration > prevDuration && distance >= prevDistance) {
                val deltaD = (distance - prevDistance).toFloat()
                val deltaT = (duration - prevDuration).toFloat()
                if (deltaT > 0) (deltaD / deltaT) * 3.6f else 0f
            } else 0f
            prevDistance = distance
            prevDuration = duration

            val finalRpm = if (statusCode == 3) rpm else 0
            val finalPower = if (statusCode == 3) power else 0
            val finalSpeed = if (statusCode == 3) speed else 0f

            _metrics.value = BikeMetricsData(
                rpm = finalRpm,
                power = finalPower,
                duration = duration,
                distance = distance,
                resistance = resistance,
                calories = calories,
                speed = finalSpeed,
                statusCode = statusCode,
            )
            _currentResistance.value = resistance
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    private fun decodeProtobuf(data: ByteArray): Map<Int, Long> {
        val results = mutableMapOf<Int, Long>()
        var ptr = 0
        while (ptr < data.size) {
            val tag = data[ptr].toInt() and 0xFF
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            ptr++
            when (wireType) {
                0 -> {
                    var result = 0L; var shift = 0
                    while (ptr < data.size) {
                        val b = data[ptr].toInt() and 0xFF
                        result = result or ((b.toLong() and 0x7F) shl shift)
                        ptr++
                        if (b and 0x80 == 0) break
                        shift += 7
                    }
                    results[fieldNum] = result
                }
                2 -> {
                    var len = 0L; var shift = 0
                    while (ptr < data.size) {
                        val b = data[ptr].toInt() and 0xFF
                        len = len or ((b.toLong() and 0x7F) shl shift)
                        ptr++
                        if (b and 0x80 == 0) break
                        shift += 7
                    }
                    ptr += len.toInt()
                }
                else -> ptr++
            }
        }
        return results
    }

    private fun findSubsequence(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        for (i in 0..(haystack.size - needle.size)) {
            var found = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { found = false; break }
            }
            if (found) return i
        }
        return -1
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
