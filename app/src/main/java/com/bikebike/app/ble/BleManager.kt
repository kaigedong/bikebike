package com.bikebike.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * Manages BLE connection to Keep bike.
 *
 * Architecture: This class handles only the BLE transport layer.
 * All protocol parsing and command building is done in Rust (keep-core).
 * For MVP, we include a minimal Kotlin protocol fallback until Rust FFI is wired up.
 */
class BleManager(private val context: Context) {

    private val TAG = "BleManager"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    // State
    private var isScanning = false
    private var isConnected = false
    private var seq: Byte = 0
    private var appCounter: Int = 0xA400
    private var ctrlCounter: Int = 0x06

    // Handshake packets loaded from identity
    private var handshakePackets: List<ByteArray> = emptyList()

    // Callbacks
    var onDeviceFound: ((ScannedDevice) -> Unit)? = null
    var onConnectionStateChanged: ((ConnectionState) -> Unit)? = null
    var onDataReceived: ((ByteArray) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    // Heartbeat
    private var heartbeatRunnable: Runnable? = null
    private val HEARTBEAT_INTERVAL_MS = 1000L

    // ======================== Scanning ========================

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning || bluetoothLeScanner == null) return

        log("Starting BLE scan for Keep devices...")
        isScanning = true
        onConnectionStateChanged?.invoke(ConnectionState.SCANNING)

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(KeepBleConstants.SERVICE_UUID))
            .build()

        // Also scan by name prefix as fallback
        val nameFilter = ScanFilter.Builder()
            .setDeviceName(KeepBleConstants.DEVICE_NAME_PREFIX)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner.startScan(
            listOf(scanFilter, nameFilter),
            scanSettings,
            scanCallback
        )

        // Stop scan after 10 seconds
        handler.postDelayed({
            stopScan()
        }, 10_000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        bluetoothLeScanner?.stopScan(scanCallback)
        log("BLE scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Unknown"
            val address = device.address
            val rssi = result.rssi

            if (name.startsWith(KeepBleConstants.DEVICE_NAME_PREFIX, ignoreCase = true)) {
                log("Found Keep device: $name ($address) RSSI=$rssi")
                onDeviceFound?.invoke(ScannedDevice(name, address, rssi))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scan failed: error $errorCode")
            isScanning = false
            onConnectionStateChanged?.invoke(ConnectionState.DISCONNECTED)
        }
    }

    // ======================== Connection ========================

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        onConnectionStateChanged?.invoke(ConnectionState.CONNECTING)
        log("Connecting to $address...")

        val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
            log("Failed to get remote device")
            onConnectionStateChanged?.invoke(ConnectionState.DISCONNECTED)
            return
        }

        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopHeartbeat()
        bluetoothGatt?.let { gatt ->
            try {
                notifyCharacteristic?.let { char ->
                    gatt.setCharacteristicNotification(char, false)
                }
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                log("Disconnect error: ${e.message}")
            }
        }
        bluetoothGatt = null
        notifyCharacteristic = null
        isConnected = false
        onConnectionStateChanged?.invoke(ConnectionState.DISCONNECTED)
        log("Disconnected")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("BLE connected, discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("BLE disconnected (status=$status)")
                    stopHeartbeat()
                    isConnected = false
                    onConnectionStateChanged?.invoke(ConnectionState.DISCONNECTED)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed: $status")
                return
            }

            // Find Keep service
            val service = gatt.getService(KeepBleConstants.SERVICE_UUID)
            if (service == null) {
                log("Keep service not found!")
                gatt.disconnect()
                return
            }
            log("Found Keep service: ${service.uuid}")

            // Find characteristic
            val char = service.getCharacteristic(KeepBleConstants.CHARACTERISTIC_UUID)
            if (char == null) {
                log("Keep characteristic not found!")
                gatt.disconnect()
                return
            }
            notifyCharacteristic = char
            log("Found characteristic: ${char.uuid}")

            // Enable notifications
            gatt.setCharacteristicNotification(char, true)

            val descriptor = char.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
                log("Notification descriptor written")
            }

            isConnected = true
            onConnectionStateChanged?.invoke(ConnectionState.CONNECTED)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val hex = value.toHexString()
            log("RX <- $hex")
            onDataReceived?.invoke(value)
        }

        @Deprecated("Use overloaded method with ByteArray for API 33+")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Fallback for older API
            val value = characteristic.value
            val hex = value.toHexString()
            log("RX <- $hex")
            onDataReceived?.invoke(value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            log("Descriptor write status: $status")

            // After notifications are enabled, send handshake
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handler.post {
                    sendHandshake()
                    startHeartbeat()
                }
            }
        }
    }

    // ======================== Handshake ========================

    /**
     * Set handshake packets (hex strings) loaded from identity.json
     */
    fun setHandshakePackets(packets: List<String>) {
        handshakePackets = packets.map { hexToBytes(it) }
        log("Loaded ${packets.size} handshake packets")
    }

    @SuppressLint("MissingPermission")
    private fun sendHandshake() {
        if (handshakePackets.isEmpty()) {
            log("WARNING: No handshake packets loaded! Connection may not work.")
            log("TIP: Load identity.json with handshake_packets first.")
            return
        }

        log("Sending ${handshakePackets.size} handshake packets...")
        seq = 0
        appCounter = 0xA400

        for ((index, pkt) in handshakePackets.withIndex()) {
            val hex = pkt.toHexString()
            log("Handshake TX -> [$index] $hex")
            writeRaw(pkt)
            Thread.sleep(50) // 50ms between packets
        }
        log("Handshake complete")
    }

    // ======================== Heartbeat ========================

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    sendHeartbeat()
                    handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
                }
            }
        }
        handler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
        log("Heartbeat started")
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let {
            handler.removeCallbacks(it)
            heartbeatRunnable = null
        }
    }

    /**
     * Send heartbeat frame: [A5 A5 A0] [seq] [len] [src_id][dst_sub][msg_type][app_counter][session][field_cnt][01][CMD_2F37]
     * When active, includes calories as extra field.
     */
    @SuppressLint("MissingPermission")
    fun sendHeartbeat(active: Boolean = false, calories: Int = 0) {
        // TODO: Replace with Rust keep-core call once UniFFI is wired up
        val cmd = byteArrayOf(0xB5.toByte(), 0x31, 0x30, 0x36, 0x2F, 0x37)

        val cnt = appCounter and 0xFFFF
        appCounter = (appCounter + 257) and 0xFFFF

        val session = if (active) byteArrayOf(0x04, 0x00) else byteArrayOf(0x00, 0x00)

        val payload = byteArrayOf(
            // src_id
            0x32, 0x16, 0xEF.toByte(), 0x23,
            // dst_sub
            0x55, 0x01,
            // msg_type
            0x93.toByte(),
            // app_counter (LE)
            (cnt and 0xFF).toByte(), ((cnt shr 8) and 0xFF).toByte(),
            // session
            session[0], session[1],
            // field_cnt
            0x00,
            // fixed
            0x01,
            // cmd
            *cmd,
        )

        val body = buildFrame(payload)
        writeRaw(body)
    }

    // ======================== Control Commands ========================

    /**
     * Set resistance level (1-24)
     */
    @SuppressLint("MissingPermission")
    fun setResistance(level: Int) {
        if (level < 1 || level > 24) {
            log("Invalid resistance: $level (must be 1-24)")
            return
        }

        ctrlCounter++
        val cnt = ctrlCounter

        // Build control payload
        val payload = byteArrayOf(
            // src_id
            0x32, 0x16, 0xEF.toByte(), 0x23,
            // dst_sub_ctrl
            0x55, 0x03,
            // msg_type
            0xB0.toByte(),
            // counter bytes
            (cnt and 0xFF).toByte(), ((cnt + 9) and 0xFF).toByte(),
            // cmd body
            0x04, 0x00, 0x00, 0x02,
            // CMD prefix
            0xB5.toByte(), 0x31, 0x30, 0x36, 0x2F, 0x36,
            // FF separator + resistance varint
            0xFF.toByte(), 0x08, level.toByte(),
        )

        val frame = buildFrame(payload)
        val hex = frame.toHexString()
        log("Set resistance $level: TX -> $hex")
        writeRaw(frame)
    }

    // ======================== Frame Building ========================

    private fun buildFrame(payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        // Magic
        body.write(byteArrayOf(0xA5.toByte(), 0xA5.toByte(), 0xA0.toByte()))
        // Seq
        body.write(byteArrayOf(seq))
        seq = ((seq.toInt() + 1) and 0xFF).toByte()
        // Payload length (LE)
        body.write(byteArrayOf(
            (payload.size and 0xFF).toByte(),
            ((payload.size shr 8) and 0xFF).toByte()
        ))
        // Payload
        body.write(payload)

        val bodyBytes = body.toByteArray()
        // CRC16
        val crc = crc16(bodyBytes)
        body.write(byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte()
        ))

        return body.toByteArray()
    }

    /**
     * CRC16-CCITT (same algorithm as BikeCon)
     */
    private fun crc16(data: ByteArray): Int {
        var crc = 0x0000
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            for (i in 0 until 8) {
                crc = if (crc and 0x8000 != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    // ======================== Raw Write ========================

    @SuppressLint("MissingPermission")
    private fun writeRaw(data: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val char = notifyCharacteristic ?: return

        val writeChar = BluetoothGattCharacteristic(
            char.uuid,
            char.properties,
            char.permissions
        ).also {
            it.value = data
        }

        // Use writeType WRITE_NO_RESPONSE for Keep protocol
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        char.value = data
        gatt.writeCharacteristic(char)
    }

    // ======================== Utilities ========================

    private fun log(msg: String) {
        Log.d(TAG, msg)
        onLog?.invoke("[${System.currentTimeMillis() % 100000}] $msg")
    }

    fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4)
                    + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}

/**
 * Simple byte array output stream helper
 */
private class ByteArrayOutputStream {
    private val buffer = mutableListOf<Byte>()

    fun write(bytes: ByteArray) {
        buffer.addAll(bytes.toList())
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}
