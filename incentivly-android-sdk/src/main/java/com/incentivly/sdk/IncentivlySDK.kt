package com.incentivly.sdk

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Main SDK class for handling revenue sharing with Google Play Billing integration
 */
class IncentivlySDK private constructor(appContext: Context) {

    companion object {
        @Volatile
        private var instance: IncentivlySDK? = null

        @JvmStatic
        fun initialize(context: Context, loggingEnabled: Boolean = false): IncentivlySDK {
            val appContext = context.applicationContext
            return synchronized(this) {
                if (instance == null) {
                    instance = IncentivlySDK(appContext)
                    IncentivlyLogger.shared.setLoggingEnabled(loggingEnabled)
                    IncentivlyLogger.shared.logInfo("🚀 Incentivly SDK initialized")
                    instance!!.billingManager.startMonitoring(appContext)
                    IncentivlyLogger.shared.logInfo("📡 Purchase monitoring initiated")
                } else {
                    IncentivlyLogger.shared.logInfo("⚠️ Incentivly SDK already initialized, skipping")
                    IncentivlyLogger.shared.setLoggingEnabled(loggingEnabled)
                }
                instance!!
            }
        }

        @JvmStatic
        val shared: IncentivlySDK
            get() = instance ?: throw IllegalStateException("IncentivlySDK is not initialized. Call initialize(context) first.")
    }

    private val prefs: SharedPreferences = appContext.getSharedPreferences("incentivly_prefs", Context.MODE_PRIVATE)
    private val apiClient = ApiClient()
    internal val billingManager = BillingManager(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val keyUserIdentifier = "Incentivly_UserIdentifier"
    private val keyIsRegistered = "Incentivly_IsRegistered"
    private val keyDevKey = "Incentivly_DevKey"

    fun setLoggingEnabled(enabled: Boolean) {
        IncentivlyLogger.shared.setLoggingEnabled(enabled)
    }

    // region Registration

    @JvmOverloads
    suspend fun registerUser(devKey: String, userIdentifier: String? = null): UserRegistrationResponse =
        withContext(Dispatchers.IO) {
            if (isUserRegistered()) {
                val storedId = getUserIdentifier()
                IncentivlyLogger.shared.logInfo("User already registered with identifier: ${storedId ?: "unknown"}")
                return@withContext UserRegistrationResponse(
                    success = true,
                    userIdentifier = storedId,
                    influencerId = null,
                    referralId = null,
                    message = "User already registered"
                )
            }

            try {
                val response = apiClient.registerUser(devKey, userIdentifier)
                if (response.success) {
                    val toStore = userIdentifier ?: response.userIdentifier
                    storeUserIdentifier(toStore)
                    storeDevKey(devKey)
                    markUserAsRegistered()
                    IncentivlyLogger.shared.logSuccess("User registered successfully with identifier: ${toStore ?: "unknown"}")
                }
                response
            } catch (t: Throwable) {
                IncentivlyLogger.shared.logError("Failed to register user", t)
                throw t
            }
        }

    suspend fun updateUserIdentifier(newUserIdentifier: String): UpdateUserIdentifierResponse =
        withContext(Dispatchers.IO) {
            val currentUserIdentifier = getUserIdentifier() ?: throw UserError.UserNotRegistered
            val devKey = getDevKey() ?: throw UserError.DevKeyNotFound

            try {
                val response = apiClient.updateUserIdentifier(currentUserIdentifier, newUserIdentifier, devKey)
                if (response.success) {
                    storeUserIdentifier(newUserIdentifier)
                    IncentivlyLogger.shared.logSuccess("User identifier updated successfully from '$currentUserIdentifier' to '$newUserIdentifier'")
                    IncentivlyLogger.shared.logInfo("Updated ${response.registrationsUpdated ?: 0} registration(s) and ${response.paymentsUpdated ?: 0} payment(s)")
                }
                response
            } catch (t: Throwable) {
                IncentivlyLogger.shared.logError("Failed to update user identifier", t)
                throw t
            }
        }

    // endregion

    // region Payment reporting

    suspend fun reportPayment(productId: String, androidPurchaseToken: String, androidOrderId: String): PaymentReportResponse =
        withContext(Dispatchers.IO) {
            val userIdentifier = getUserIdentifier() ?: throw PaymentError.UserNotRegistered
            val devKey = getDevKey() ?: throw PaymentError.DevKeyNotFound

            if (!Transactions.shouldProcess(androidPurchaseToken)) {
                throw PaymentError.TransactionAlreadyProcessed
            }

            try {
                val response = apiClient.reportPayment(userIdentifier, productId, androidPurchaseToken, androidOrderId, devKey)
                if (response.success) {
                    Transactions.saveProcessed(androidPurchaseToken)
                    IncentivlyLogger.shared.logSuccess("Payment reported successfully with ID: ${response.paymentId ?: "unknown"}")
                }
                response
            } catch (t: Throwable) {
                Transactions.addReportAttempt(androidPurchaseToken)
                IncentivlyLogger.shared.logError("Failed to report payment", t)
                throw t
            }
        }

    // endregion

    fun getUserIdentifier(): String? = prefs.getString(keyUserIdentifier, null)
    internal fun getDevKey(): String? = prefs.getString(keyDevKey, null)
    fun isUserRegistered(): Boolean = prefs.getBoolean(keyIsRegistered, false)

    private fun storeUserIdentifier(id: String?) {
        prefs.edit().putString(keyUserIdentifier, id).apply()
    }

    private fun storeDevKey(devKey: String) {
        prefs.edit().putString(keyDevKey, devKey).apply()
    }

    private fun markUserAsRegistered() {
        prefs.edit().putBoolean(keyIsRegistered, true).apply()
    }
}

// region Errors

sealed class PaymentError(message: String? = null) : Exception(message) {
    data object UserNotRegistered : PaymentError("User must be registered before reporting payments")
    data object DevKeyNotFound : PaymentError("Developer key not found. Please register user first.")
    data object InvalidTransactionId : PaymentError("Invalid transaction ID provided.")
    data object TransactionAlreadyProcessed : PaymentError("This transaction has already been processed.")
}

sealed class UserError(message: String? = null) : Exception(message) {
    data object UserNotRegistered : UserError("User must be registered before updating identifier")
    data object DevKeyNotFound : UserError("Developer key not found. Please register user first.")
}

// endregion


