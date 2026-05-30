package com.bikebike.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * App-wide settings persisted via SharedPreferences.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bikebike_settings", Context.MODE_PRIVATE)

    // ======================== Log Toggle ========================

    var logEnabled: Boolean
        get() = prefs.getBoolean("log_enabled", true)
        set(value) = prefs.edit().putBoolean("log_enabled", value).apply()

    // ======================== Language ========================

    enum class Language(val code: String) {
        SYSTEM("system"),
        ZH("zh"),
        EN("en");

        companion object {
            fun fromCode(code: String): Language =
                entries.find { it.code == code } ?: SYSTEM
        }
    }

    var language: Language
        get() = Language.fromCode(prefs.getString("language", "system") ?: "system")
        set(value) = prefs.edit().putString("language", value.code).apply()

    // ======================== Identity ========================

    var bikeName: String
        get() = prefs.getString("bike_name", "") ?: ""
        set(value) = prefs.edit().putString("bike_name", value).apply()

    var bikeMac: String
        get() = prefs.getString("bike_mac", "") ?: ""
        set(value) = prefs.edit().putString("bike_mac", value).apply()

    var handshakePacketsJson: String
        get() = prefs.getString("handshake_packets", "[]") ?: "[]"
        set(value) = prefs.edit().putString("handshake_packets", value).apply()

    fun saveIdentity(name: String, mac: String, packets: List<String>) {
        prefs.edit().apply {
            putString("bike_name", name)
            putString("bike_mac", mac)
            putString("handshake_packets", JSONObject().apply {
                put("packets", org.json.JSONArray(packets))
            }.toString())
            apply()
        }
    }

    fun loadHandshakePackets(): List<String> {
        val json = handshakePacketsJson
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("packets") ?: return emptyList()
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasIdentity(): Boolean = bikeMac.isNotEmpty() && loadHandshakePackets().isNotEmpty()

    // ======================== Last Connected ========================

    var lastDeviceAddress: String
        get() = prefs.getString("last_device_address", "") ?: ""
        set(value) = prefs.edit().putString("last_device_address", value).apply()
}
