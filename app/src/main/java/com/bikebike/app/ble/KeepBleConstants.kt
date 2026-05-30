package com.bikebike.app.ble

import java.util.UUID

/**
 * Keep bike BLE constants and protocol definitions
 */
object KeepBleConstants {
    // Service UUID for Keep C1 mini / C2 Lite
    val SERVICE_UUID: UUID = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb")

    // Characteristic UUID for communication
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

    // Device name prefix for scanning
    const val DEVICE_NAME_PREFIX = "Keep"

    // Protocol constants (mirrored from Rust keep-core)
    const val FRAME_MAGIC_0: Byte = 0xA5.toByte()
    const val FRAME_MAGIC_1: Byte = 0xA5.toByte()
    const val FRAME_MAGIC_2: Byte = 0xA0.toByte()

    // Data marker in notifications: B5 31 30 36 2F 37 FF
    val DATA_MARKER = byteArrayOf(
        0xB5.toByte(), 0x31, 0x30, 0x36, 0x2F, 0x37, 0xFF.toByte()
    )
}

/**
 * BLE connection state
 */
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
}

/**
 * Scanned BLE device info
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

/**
 * Parsed bike metrics from notification
 */
data class BikeMetricsData(
    val rpm: Int = 0,
    val power: Int = 0,
    val duration: Int = 0,
    val distance: Int = 0,
    val resistance: Int = 1,
    val calories: Float = 0f,
    val speed: Float = 0f,
    val statusCode: Int = 0,
)
