package com.incentivly.sdk

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class BillingManager(private val sdk: IncentivlySDK) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private var isStarted: Boolean = false
    private var periodicJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startMonitoring(context: Context) {
        if (isStarted) {
            IncentivlyLogger.shared.logInfo("⚠️ Purchase monitoring already started, skipping")
            return
        }

        Transactions.init(context.applicationContext)

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        connectAndQuery()
        startPeriodicChecks()
        isStarted = true
        IncentivlyLogger.shared.logInfo("✅ Purchase monitoring started")
    }

    private fun connectAndQuery() {
        val client = billingClient ?: return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases()
                } else {
                    IncentivlyLogger.shared.logError("Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection later
                scope.launch {
                    delay(2000)
                    connectAndQuery()
                }
            }
        })
    }

    private fun startPeriodicChecks() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (true) {
                delay(1_000) // 1 second like iOS example
                queryExistingPurchases()
            }
        }
    }

    private fun queryExistingPurchases() {
        val client = billingClient ?: return
        if (!sdk.isUserRegistered()) return

        // Query current purchases (subs + inapp) asynchronously
        try {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { billingResult, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchasesList)
                } else {
                    IncentivlyLogger.shared.logError("queryPurchasesAsync SUBS failed: ${billingResult.debugMessage}")
                }
            }
        } catch (_: Throwable) {}

        try {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { billingResult, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchasesList)
                } else {
                    IncentivlyLogger.shared.logError("queryPurchasesAsync INAPP failed: ${billingResult.debugMessage}")
                }
            }
        } catch (_: Throwable) {}

        // Also query purchase history as a fallback
        try {
            client.queryPurchaseHistoryAsync(
                QueryPurchaseHistoryParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { billingResult, records ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handleHistory(records)
                } else {
                    IncentivlyLogger.shared.logError("queryPurchaseHistoryAsync SUBS failed: ${billingResult.debugMessage}")
                }
            }
        } catch (_: Throwable) {}

        try {
            client.queryPurchaseHistoryAsync(
                QueryPurchaseHistoryParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { billingResult, records ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handleHistory(records)
                } else {
                    IncentivlyLogger.shared.logError("queryPurchaseHistoryAsync INAPP failed: ${billingResult.debugMessage}")
                }
            }
        } catch (_: Throwable) {}
    }

    private fun handlePurchases(purchases: List<Purchase>?) {
        purchases?.forEach { purchase ->
            scope.launch {
                processPurchase(purchase)
            }
        }
    }

    private suspend fun processPurchase(purchase: Purchase) {
        if (!sdk.isUserRegistered()) return

        IncentivlyLogger.shared.logInfo("💳 Processing purchase: ${purchase.products.joinToString()} (token: ${purchase.purchaseToken}, time: ${purchase.purchaseTime})")
        try {
            val productId = purchase.products.firstOrNull() ?: return
            sdk.reportPayment(productId = productId, androidPurchaseToken = purchase.purchaseToken)
            IncentivlyLogger.shared.logInfo("✅ Purchase reported successfully: $productId")
        } catch (t: Throwable) {
            IncentivlyLogger.shared.logError("Failed to report purchase", t)
        }
    }

    private fun handleHistory(history: List<com.android.billingclient.api.PurchaseHistoryRecord>?) {
        history?.forEach { record ->
            scope.launch {
                if (Transactions.shouldProcess(record.purchaseToken)) {
                    try {
                        val productId = record.products.firstOrNull() ?: return@launch
                        sdk.reportPayment(productId = productId, androidPurchaseToken = record.purchaseToken)
                        IncentivlyLogger.shared.logInfo("✅ History purchase reported successfully: $productId")
                    } catch (t: Throwable) {
                        IncentivlyLogger.shared.logError("Failed to report history purchase", t)
                    }
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            IncentivlyLogger.shared.logError("onPurchasesUpdated error: ${billingResult.debugMessage}")
        }
    }
}


