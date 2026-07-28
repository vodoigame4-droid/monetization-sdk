# Monetization Module Guide

Integration guide for the Monetization module (**Ads, Analytics, Consent (CMP), and In-App Billing**) into the project.

---

## 1. Introduction

The `monetization` module provides ready-to-use wrappers for handling:
*   **Ads**: Banner, Interstitial, Native, Rewarded, and App Open Ads (with Waterfall support).
*   **Consent (CMP)**: User messaging platform / GDPR consent flow.
*   **Analytics**: Event tracking wrappers.
*   **In-App Billing (IAP & Subscriptions)**: Querying products, launching purchases, acknowledging purchases (`AcknowledgePurchase`), consuming items (`ConsumePurchase`), and tracking ad-free status.

---

## 2. In-App Billing (IAP & Subscriptions)

Google Play requires all purchases (subscriptions and non-consumables) to be **acknowledged** within 3 days. If a purchase is not acknowledged, Google Play automatically cancels the purchase and refunds the user.

### Step 1: Initialize Billing SDK
Initialize `AppBilling` in your `Application` or `SplashActivity`:

```kotlin
// Application.kt or SplashActivity.kt
AppBilling.init(applicationContext)
```

---

### Step 2: Check Purchased Status & Observe Ad-Free State
SDK maintains `isAdFreeFlow` (`StateFlow<Boolean>`) to track whether the user has active subscriptions or acknowledged purchases.

```kotlin
// 1. Check purchased status asynchronously (IO thread)
lifecycleScope.launch {
    val isPurchased: Boolean = AppBilling.checkPurchased()
    Log.d("Billing", "Is user ad-free: $isPurchased")
}

// 2. Observe ad-free state continuously
lifecycleScope.launch {
    AppBilling.isAdFreeFlow.collect { isAdFree ->
        if (isAdFree) {
            // Hide ads UI
        }
    }
}
```

---

### Step 3: Fetch Product Details (Subscriptions & In-App Items)
SDK simplifies querying product details into clean `IAPBillingItem` objects.

```kotlin
// Fetch Subscription products
lifecycleScope.launch {
    val subProductIds = listOf("sub_monthly_id", "sub_yearly_id")
    val subsList: List<IAPBillingItem> = AppBilling.getSubsProductsList(subProductIds)
    
    subsList.forEach { item ->
        Log.d("Billing", "ID: ${item.productId}, Price: ${item.formattedPrice}")
    }
}

// Fetch In-App products (Non-consumable or Consumable)
lifecycleScope.launch {
    val inAppProductIds = listOf("remove_ads_lifetime_id", "coin_pack_100")
    val inAppList: List<IAPBillingItem> = AppBilling.getInAppProductsList(inAppProductIds)
}
```

---

### Step 4: Launch Purchase Flow
To launch the Google Play billing UI for an item:

```kotlin
val itemToBuy: IAPBillingItem = subsList.first()

AppBilling.purchaseIAPBillingItem(
    activity = this@PaywallActivity,
    billingItem = itemToBuy
)
```

---

### Step 5: Listen to Purchase Updates, Acknowledge & Consume

Listen to `AppBilling.billingUpdateListener.purchaseUpdate` flow to handle transaction results.

> [!IMPORTANT]
> **Acknowledge Purchase Requirements:**
> *   **Subscriptions / Non-Consumables (e.g. Remove Ads)**: Must call `AppBilling.acknowledgePurchase(purchase)`.
> *   **Consumable In-Apps (e.g. Coins/Gems)**: Must call `AppBilling.consumePurchase(purchase)`.

```kotlin
// In ViewModel or PaywallActivity onCreate / lifecycleScope
lifecycleScope.launch {
    AppBilling.billingUpdateListener.purchaseUpdate.collect { update ->
        val (billingResult, purchases) = update ?: return@collect

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    
                    // Case A: Subscription or Non-Consumable Item -> Acknowledge Purchase
                    if (!purchase.isAcknowledged) {
                        lifecycleScope.launch {
                            val result = AppBilling.acknowledgePurchase(purchase)
                            if (result?.responseCode == BillingClient.BillingResponseCode.OK) {
                                Log.d("Billing", "Purchase acknowledged successfully!")
                                // Refresh ad-free status
                                AppBilling.checkPurchased()
                            }
                        }
                    }

                    // Case B: Consumable Item (Coins/Gems) -> Consume Purchase
                    /*
                    lifecycleScope.launch {
                        val consumeResult = AppBilling.consumePurchase(purchase)
                        if (consumeResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d("Billing", "Item consumed successfully, deliver rewards!")
                        }
                    }
                    */
                }
            }
        } else {
            Log.e("Billing", "Purchase failed: ${billingResult.debugMessage}")
        }
    }
}
```

---

## 3. Ads Integration (Step-by-Step)

### Step 1: Create AdsProvider
Create an `AdsProvider` singleton to manage all ad unit groups (Waterfall strategy):

```kotlin
// AdsProvider.kt
object AdsProvider {
    val interSplashAd = InterstitialAdGroup(
        BuildConfig.inter_splash_high_id to "inter_splash_high",
        BuildConfig.inter_splash_medium_id to "inter_splash_medium",
        BuildConfig.inter_splash_floor_id to "inter_splash_floor",
        name = "inter_splash"
    )

    val nativeOnboardAd = NativeAdGroup(
        BuildConfig.native_onboard_high_id to "native_onboard_high",
        BuildConfig.native_onboard_floor_id to "native_onboard_floor",
        name = "native_onboard",
        isFullScreen = false
    )
}
```

---

### Step 2: Configure via Remote Config & Complete Initialization
Fetch configuration flags from Remote Config, apply them using `.config()`, and call `FOManager.finishSplashInitialization()`.

```kotlin
fun onRemoteConfigReady(config: FirebaseRemoteConfig) {
    // 1. Enable/disable specific ad unit IDs in AdGroup
    AdsProvider.interSplashAd.config(
        config.getBoolean("enable_inter_splash_high"),
        config.getBoolean("enable_inter_splash_medium"),
        config.getBoolean("enable_inter_splash_floor")
    )

    AdsProvider.nativeOnboardAd.config(
        config.getBoolean("enable_native_onboard_high"),
        config.getBoolean("enable_native_onboard_floor")
    )

    // 2. Finish Splash Init -> Start First Open flow / Navigate to Main
    FOManager.finishSplashInitialization()
}
```

---

### Step 3: Load Ads

```kotlin
override fun onResume() {
    super.onResume()
    
    // Load App Open Resume Ad
    AppOpenResumeManager.loadAds(this) 
    
    // Pre-load Waterfall Ads
    AdsProvider.interSplashAd.loadAds(this)
    AdsProvider.nativeOnboardAd.loadAds(this)
}
```

---

### Step 4: Display Ads

#### Interstitial Ad
```kotlin
AdsProvider.interSplashAd.showAds(activity, object : InterstitialAdCallback {
    override fun onNextAction(show: Boolean) {
        navigateToNextScreen()
    }
})
```

#### Native Ad (Jetpack Compose)
```kotlin
NativeAdContent(
    adGroup = AdsProvider.nativeOnboardAd,
    modifier = Modifier.fillMaxWidth()
)
```

#### App Open Ad
```kotlin
AppOpenResumeManager.setUpAppOpenResume(
    adId = BuildConfig.app_open_id,
    name = "app_open_ad",
    activities = arrayOf(MainActivity::class)
)
```

---

## 4. Important Notes

*   **Acknowledge Deadline**: Google Play requires purchases to be acknowledged within **3 days**. Always ensure `AppBilling.acknowledgePurchase(purchase)` is called upon receiving a valid purchase.
*   **Finish Init**: Call `FOManager.finishSplashInitialization()` when Remote Config finishes on Splash screen.
*   **Waterfall Config Matching**: The number of boolean arguments in `adGroup.config(...)` must match the exact number of IDs declared in `AdsProvider`.

---
*Document updated for TomoDev Team.*
