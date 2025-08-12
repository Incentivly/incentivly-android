package com.incentivly.sdk

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import org.json.JSONArray

internal object Transactions {
    private const val KEY_PROCESSED = "Incentivly_ProcessedTransactions"
    private const val KEY_ATTEMPTS = "Incentivly_TransactionReportAttempts"
    private const val MAX_ATTEMPTS = 5

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!this::prefs.isInitialized) {
            prefs = context.getSharedPreferences("incentivly_prefs", Context.MODE_PRIVATE)
        }
    }

    fun shouldProcess(purchaseToken: String): Boolean {
        val processed = getProcessed()
        val attempts = getAttempts()
        return !processed.contains(purchaseToken) && (attempts[purchaseToken] ?: 0) < MAX_ATTEMPTS
    }

    fun saveProcessed(purchaseToken: String) {
        val processed = getProcessed().toMutableSet()
        processed.add(purchaseToken)
        putProcessed(processed)
        IncentivlyLogger.shared.logInfo("💾 Processed transaction (token: $purchaseToken) saved to storage")
    }

    fun addReportAttempt(purchaseToken: String) {
        val attempts = getAttempts().toMutableMap()
        attempts[purchaseToken] = (attempts[purchaseToken] ?: 0) + 1
        putAttempts(attempts)
        IncentivlyLogger.shared.logInfo("💾 Increased transaction report attempts count to ${attempts[purchaseToken]} for token: $purchaseToken")
    }

    private fun getProcessed(): Set<String> {
        val raw = prefs.getString(KEY_PROCESSED, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            set
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun putProcessed(set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        prefs.edit().putString(KEY_PROCESSED, arr.toString()).apply()
    }

    private fun getAttempts(): Map<String, Int> {
        val raw = prefs.getString(KEY_ATTEMPTS, null) ?: return emptyMap()
        return try {
            val obj = org.json.JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optInt(key, 0)
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun putAttempts(map: Map<String, Int>) {
        val obj = org.json.JSONObject()
        for ((k, v) in map) obj.put(k, v)
        prefs.edit().putString(KEY_ATTEMPTS, obj.toString()).apply()
    }

    @VisibleForTesting
    fun clearAllForTests() {
        if (this::prefs.isInitialized) {
            prefs.edit().remove(KEY_PROCESSED).remove(KEY_ATTEMPTS).apply()
        }
    }
}


