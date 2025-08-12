# Incentivly Android SDK

A Kotlin Android library that passively monitors Google Play Billing purchases and reports them to your API. The SDK does not handle purchase flows; it only listens for and reports completed purchases.

## Features
- Play Billing monitoring (current purchases and history)
- API client (JSON over HTTPS)
- Automatic purchase reporting
- User registration and identifier updates
- Duplicate prevention with persistent storage
- Toggleable logging

## Requirements
- minSdk 21+
- target/compileSdk 34
- Kotlin 1.9.24+
- Gradle 8.0+
- Play Billing 7.0.0+

## Installation
Local module usage:
```kotlin
// settings.gradle.kts
include(":incentivly-android-sdk")

// app/build.gradle.kts
dependencies {
    implementation(project(":incentivly-android-sdk"))
}
```
If published via Maven:
```kotlin
dependencies {
    implementation("com.incentivly:sdk:1.0.0")
}
```
The library declares INTERNET permission in its manifest.

## Setup
Initialize early (e.g., Application.onCreate):
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        IncentivlySDK.initialize(this, loggingEnabled = true)
    }
}
```

## User registration
```kotlin
lifecycleScope.launch {
    val response = IncentivlySDK.shared.registerUser(
        devKey = "YOUR_DEV_KEY",
        userIdentifier = "optional_user_id"
    )
}
```

## Update user identifier
```kotlin
lifecycleScope.launch {
    IncentivlySDK.shared.updateUserIdentifier("new_user_id")
}
```

## Automatic purchase reporting
Once initialized and the user is registered, purchases are monitored and reported automatically. Implement your own purchase flow as usual; the SDK only observes and reports successful purchases.

## Manual payment reporting (optional)
```kotlin
lifecycleScope.launch {
    IncentivlySDK.shared.reportPayment(
        productId = "com.yourapp.product1",
        androidPurchaseToken = "purchase_token",
        androidOrderId = "order_id"
    )
}
```

## API endpoints (default)
- POST https://incentivly.com/api/register-user
- POST https://incentivly.com/api/update-user-identifier
- POST https://incentivly.com/api/report-payment

Note: For backend compatibility, the Android purchase token is sent as the field named `iosTransactionId` and the Android order ID is sent as `androidOrderId`. Both fields are required for payment reporting.

## License
MIT (or your chosen license)
