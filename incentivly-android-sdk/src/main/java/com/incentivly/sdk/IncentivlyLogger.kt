package com.incentivly.sdk

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

public class IncentivlyLogger private constructor() {
    companion object {
        @JvmStatic
        val shared: IncentivlyLogger by lazy { IncentivlyLogger() }
        private const val TAG: String = "IncentivlySDK"
    }

    private var isEnabled: Boolean = false
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun setLoggingEnabled(enabled: Boolean) {
        isEnabled = enabled
        log("Logging ${if (enabled) "enabled" else "disabled"}")
    }

    fun isLoggingEnabled(): Boolean = isEnabled

    fun log(message: String) {
        if (!isEnabled) return
        val timestamp = dateFormatter.format(Date())
        Log.i(TAG, "[$timestamp] $message")
    }

    fun logRequest(method: String, url: String, headers: Map<String, String>? = null, body: String? = null) {
        if (!isEnabled) return
        log("🌐 API REQUEST:")
        log("   Method: $method")
        log("   URL: $url")
        headers?.let {
            log("   Headers:")
            for ((key, value) in it) {
                val masked = if (key.lowercase(Locale.US).contains("authorization")) "***" else value
                log("     $key: $masked")
            }
        }
        body?.let { log("   Body: $it") }
    }

    fun logResponse(statusCode: Int, headers: Map<String, String>? = null, body: String? = null, error: Throwable? = null) {
        if (!isEnabled) return
        log("📡 API RESPONSE:")
        log("   Status Code: $statusCode")
        headers?.let {
            log("   Headers:")
            for ((key, value) in it) {
                log("     $key: $value")
            }
        }
        body?.let { log("   Body: $it") }
        error?.let { log("   Error: ${it.localizedMessage}") }
    }

    fun logError(message: String, error: Throwable? = null, context: String? = null) {
        if (!isEnabled) return
        log("❌ ERROR: $message")
        context?.let { log("   Context: $it") }
        error?.let { log("   Error: ${it.localizedMessage}") }
    }

    fun logSuccess(message: String) {
        if (!isEnabled) return
        log("✅ SUCCESS: $message")
    }

    fun logInfo(message: String) {
        if (!isEnabled) return
        log("ℹ️ INFO: $message")
    }
}


