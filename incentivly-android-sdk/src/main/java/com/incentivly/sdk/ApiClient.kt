package com.incentivly.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal class ApiClient {
    private val baseUrl: String = "https://incentivly.com/api"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun registerUser(devKey: String, userIdentifier: String?): UserRegistrationResponse =
        sendRequest(
            endpoint = "/register-user",
            method = "POST",
            body = UserRegistrationRequest(devKey, userIdentifier),
            responseDeserializer = { json.decodeFromString(UserRegistrationResponse.serializer(), it) }
        )

    suspend fun updateUserIdentifier(currentUserIdentifier: String, newUserIdentifier: String, devKey: String): UpdateUserIdentifierResponse =
        sendRequest(
            endpoint = "/update-user-identifier",
            method = "POST",
            body = UpdateUserIdentifierRequest(currentUserIdentifier, newUserIdentifier, devKey),
            responseDeserializer = { json.decodeFromString(UpdateUserIdentifierResponse.serializer(), it) }
        )

    suspend fun reportPayment(userIdentifier: String, productId: String, androidPurchaseToken: String, androidOrderId: String, devKey: String): PaymentReportResponse =
        sendRequest(
            endpoint = "/report-payment",
            method = "POST",
            body = PaymentReportRequest(userIdentifier, productId, androidPurchaseToken, androidOrderId, devKey),
            responseDeserializer = { json.decodeFromString(PaymentReportResponse.serializer(), it) }
        )

    private suspend fun <TRequest : Any, TResponse : Any> sendRequest(
        endpoint: String,
        method: String,
        body: TRequest?,
        responseDeserializer: (String) -> TResponse
    ): TResponse = withContext(Dispatchers.IO) {
        val url = URL(baseUrl + endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            doInput = true
            doOutput = body != null
            setRequestProperty("Content-Type", "application/json")
        }

        val requestBody = body?.let { json.encodeToString(getSerializer(it), it) }
        IncentivlyLogger.shared.logRequest(method, url.toString(), emptyMap(), requestBody)

        if (requestBody != null) {
            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }
        }

        val statusCode = conn.responseCode
        val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.use { s ->
            BufferedReader(InputStreamReader(s)).use { it.readText() }
        } ?: ""

        IncentivlyLogger.shared.logResponse(statusCode, emptyMap(), responseText, null)

        if (statusCode !in 200..299) {
            throw ApiError.ServerError(statusCode)
        }

        try {
            responseDeserializer(responseText)
        } catch (t: Throwable) {
            IncentivlyLogger.shared.logError("Failed to decode response", t)
            throw ApiError.EncodingError
        } finally {
            conn.disconnect()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getSerializer(value: T) = when (value) {
        is UserRegistrationRequest -> UserRegistrationRequest.serializer()
        is UpdateUserIdentifierRequest -> UpdateUserIdentifierRequest.serializer()
        is PaymentReportRequest -> PaymentReportRequest.serializer()
        else -> throw IllegalArgumentException("Unsupported request type: ${value::class.java}")
    } as kotlinx.serialization.KSerializer<T>
}

@Serializable
internal data class UserRegistrationRequest(
    val devKey: String,
    val userIdentifier: String? = null
)

@Serializable
internal data class UpdateUserIdentifierRequest(
    val currentUserIdentifier: String,
    val newUserIdentifier: String,
    val devKey: String
)

@Serializable
internal data class PaymentReportRequest(
    val userIdentifier: String,
    val productId: String,
    @SerialName("iosTransactionId")
    val androidPurchaseToken: String,
    @SerialName("androidOrderId")
    val androidOrderId: String,
    val devKey: String
)

@Serializable
data class UserRegistrationResponse(
    val success: Boolean,
    val userIdentifier: String? = null,
    val influencerId: String? = null,
    val referralId: String? = null,
    val message: String? = null
)

@Serializable
data class UpdateUserIdentifierResponse(
    val success: Boolean,
    val message: String? = null,
    val registrationsUpdated: Int? = null,
    val paymentsUpdated: Int? = null
)

@Serializable
data class PaymentReportResponse(
    val success: Boolean,
    val message: String? = null,
    val paymentId: String? = null
)

sealed class ApiError(message: String? = null) : Exception(message) {
    data object InvalidUrl : ApiError()
    data object InvalidResponse : ApiError()
    data class ServerError(val code: Int) : ApiError("Server error: $code")
    data object EncodingError : ApiError()
}


