package com.bikebike.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.bikebike.app.ble.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for bike data and BLE connection state.
 * Bridges the UI and BLE layer.
 */
class BikeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "BikeViewModel"

    private val bleManager = BleManager(application).apply {
        onDeviceFound = { device ->
            val current = _scannedDevices.value.toMutableList()
            // Update or add device
            val idx = current.indexOfFirst { it.address == device.address }
            if (idx >= 0) {
                current[idx] = device
            } else {
                current.add(device)
            }
            _scannedDevices.value = current
        }

        onConnectionStateChanged = { state ->
            _connectionState.value = state
        }

        onDataReceived = { data ->
            parseNotification(data)
        }

        onLog = { line ->
            val current = _logLines.value.toMutableList()
            current.add(line)
            // Keep last 200 lines
            if (current.size > 200) {
                _logLines.value = current.takeLast(200)
            } else {
                _logLines.value = current
            }
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

        // Auto-stop scanning state after scan completes
        kotlinx.coroutines.MainScope().launch {
            kotlinx.coroutines.delay(11_000)
            _isScanning.value = false
        }
    }

    fun connectToDevice(device: ScannedDevice) {
        // TODO: Load handshake packets from app storage
        // For now, try without handshake (will work for FTMS devices)
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

    /**
     * Load handshake packets from identity JSON
     */
    fun loadHandshakePackets(packets: List<String>) {
        bleManager.setHandshakePackets(packets)
    }

    // ======================== Notification Parsing ========================

    /**
     * Parse Keep notification data.
     *
     * For MVP, this is a Kotlin implementation that mirrors the Rust keep-core logic.
     * Once UniFFI is wired up, this will call into Rust instead.
     */
    private fun parseNotification(data: ByteArray) {
        try {
            // Find data marker: B5 31 30 36 2F 37 FF
            val marker = KeepBleConstants.DATA_MARKER
            val markerIdx = findSubsequence(data, marker)

            if (markerIdx < 0) {
                Log.d(TAG, "No data marker found in notification")
                return
            }

            // Extract protobuf data after marker, skip last 2 bytes
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

            // Calculate speed from deltas
            val speed = if (duration > prevDuration && distance >= prevDistance) {
                val deltaD = (distance - prevDistance).toFloat()
                val deltaT = (duration - prevDuration).toFloat()
                if (deltaT > 0) (deltaD / deltaT) * 3.6f else 0f
            } else {
                0f
            }
            prevDistance = distance
            prevDuration = duration

            // Only report rpm/power when active (status=3)
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

            // Update resistance from bike feedback
            _currentResistance.value = resistance

        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    /**
     * Simple protobuf varint decoder
     */
    private fun decodeProtobuf(data: ByteArray): Map<Int, Long> {
        val results = mutableMapOf<Int, Long>()
        var ptr = 0

        while (ptr < data.size) {
            val tag = data[ptr].toInt() and 0xFF
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            ptr++

            when (wireType) {
                0 -> { // Varint
                    var result = 0L
                    var shift = 0
                    while (ptr < data.size) {
                        val b = data[ptr].toInt() and 0xFF
                        result = result or ((b.toLong() and 0x7F) shl shift)
                        ptr++
                        if (b and 0x80 == 0) break
                        shift += 7
                    }
                    results[fieldNum] = result
                }
                2 -> { // Length-delimited
                    var len = 0L
                    var shift = 0
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
                if (haystack[i + j] != needle[j]) {
                    found = false
                    break
                }
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
