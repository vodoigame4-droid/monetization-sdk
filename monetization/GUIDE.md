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

// Khai báo phân loại sản phẩm INAPP (nên gọi ngay sau init)
AppBilling.registerProducts(
    lifetime = listOf("remove_ads_lifetime_id"),   // gói vĩnh viễn -> tính ad-free, cấm consume
    consumable = listOf("coin_pack_100"),          // gói tiêu hao   -> không tính ad-free
)
```

> [!IMPORTANT]
> `registerProducts` quyết định 3 hành vi:
> * Chỉ `lifetime` (và subscription) mới bật `isAdFreeFlow`. Nếu **không khai báo**, SDK giữ hành vi cũ là *mọi* purchase INAPP đều được coi là ad-free — mua gói coin cũng tắt quảng cáo.
> * Sản phẩm `consumable` **không** bị `checkPurchased()` auto-acknowledge (consume đã bao hàm acknowledge).
> * Sản phẩm `lifetime` bị `consumePurchase()` **từ chối** để tránh vô tình xoá quyền vĩnh viễn của user.

---

### Step 2: Check Purchased Status & Observe Ad-Free State
SDK maintains `isAdFreeFlow` (`StateFlow<Boolean>`) to track whether the user has active subscriptions or acknowledged purchases.

> [!NOTE]
> **Auto-Recovery & Slow Test Card Handling:**
> Calling `AppBilling.checkPurchased()` will automatically auto-acknowledge any valid unacknowledged purchases (`PURCHASED` state) — such as when a user exits the app right after paying or when a **Slow Test Card / Pending Payment** is approved by Google Play.

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

    inAppList.forEach { item ->
        // formattedPrice = giá thực tế phải trả
        // originalPriceFormat = giá gốc (chỉ có khi offer đang giảm giá) -> dùng để gạch ngang
        Log.d("Billing", "${item.productId}: ${item.formattedPrice} (was ${item.originalPriceFormat})")
    }
}
```

> [!NOTE]
> Khác biệt giữa SUBS và INAPP trong `IAPBillingItem`:
> | Field | SUBS | INAPP |
> |---|---|---|
> | `basePlanId` | id của base plan | luôn `""` |
> | `recurrenceMode` | 1 = infinite, 2 = finite | luôn `3` (`IAPBillingItem.NON_RECURRING`) |
> | `offerPriceFormat` | giá phase khuyến mãi | luôn `""` |
> | `originalPriceFormat` | `""` | giá gốc khi offer đang giảm giá |
> | `purchaseOptionId` | `null` | id purchase option (Billing 8+) |
>
> Một sản phẩm INAPP có nhiều purchase option sẽ trả về nhiều `IAPBillingItem` cùng `productId`.
> Luôn truyền nguyên object `IAPBillingItem` vào `purchaseIAPBillingItem` — nó mang theo `offerToken`
> cần thiết để mở đúng offer.

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

### Step 6: Server-side Verification / Backend API Callback (Optional)
If your app needs to send `purchaseToken`, `orderId`, or `products` to your backend server whenever a purchase is successfully acknowledged (either on a new purchase or when auto-acknowledged during restore):

```kotlin
// Register global callback (e.g. in Application.onCreate)
AppBilling.onPurchaseAcknowledged = { purchase ->
    Log.d("Billing", "Acknowledged Purchase: ${purchase.products}, Token: ${purchase.purchaseToken}")
    // Call your Backend API here:
    // apiRepository.verifyPurchaseOnServer(purchase.purchaseToken, purchase.orderId)
}
```
```

---

### Step 7: Đọc gói Lifetime & chống consume nhầm

Sau khi đã khai báo `AppBilling.registerProducts(lifetime = ...)` ở Step 1:

```kotlin
// 1. Trạng thái sở hữu gói vĩnh viễn (được cập nhật mỗi lần checkPurchased())
lifecycleScope.launch {
    AppBilling.checkPurchased()
    if (AppBilling.isLifetimePurchased) {
        // ẩn nút mua lifetime trên paywall
    }
}

// 2. Observe liên tục
lifecycleScope.launch {
    AppBilling.isLifetimePurchasedFlow.collect { owned -> /* ... */ }
}

// 3. Lấy purchase gốc (purchaseToken, orderId) để verify với backend
lifecycleScope.launch {
    val lifetimePurchases: List<Purchase> = AppBilling.getLifetimePurchases()
}
```

**Chống consume:** `AppBilling.consumePurchase(purchase)` sẽ từ chối mọi purchase thuộc nhóm
`lifetime` và trả về `BillingResponseCode.DEVELOPER_ERROR` kèm debug message, thay vì gọi
`consumeAsync`. Nhờ đó vòng lặp xử lý `purchaseUpdate` chung cho cả coin lẫn lifetime không thể vô
tình xoá quyền vĩnh viễn của user.

---

### Step 8: Consume gói Lifetime để test (DEBUG ONLY)

Gói lifetime là non-consumable nên chỉ mua được **một lần** trên mỗi tài khoản. Để test lại luồng
mua, cần consume purchase token đó:

```kotlin
if (BuildConfig.DEBUG) {
    lifecycleScope.launch {
        // Consume tất cả gói lifetime đang sở hữu
        val consumed: List<String> = AppBilling.consumeLifetimePurchaseForTesting()

        // Hoặc chỉ một product id cụ thể
        // val consumed = AppBilling.consumeLifetimePurchaseForTesting("remove_ads_lifetime_id")

        Log.d("Billing", "Đã reset: $consumed")   // rỗng = không sở hữu gói nào
    }
}
```

Hàm này tự chạy lại `checkPurchased()` sau khi consume, nên `isAdFreeFlow` / `isLifetimePurchasedFlow`
được đồng bộ ngay.

> [!WARNING]
> `consumeLifetimePurchaseForTesting()` là hàm **duy nhất** vượt qua được lớp bảo vệ ở Step 7. Luôn
> bọc trong `BuildConfig.DEBUG` — gọi nhầm trên bản release sẽ xoá quyền vĩnh viễn của user thật và
> Google Play không hoàn tác được.
>
> Lưu ý: chỉ áp dụng cho **INAPP**. Subscription không consume được — muốn reset subscription phải
> huỷ ở Play Store (test subscription tự hết hạn sau vài phút).

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

    val appOpenResumeAd = AppOpenAdGroup(
        BuildConfig.app_open_high_id to "app_open_high",
        BuildConfig.app_open_floor_id to "app_open_floor",
        name = "app_open_resume"
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

#### App Open Ad (Supports Normal, 2F, MF)
```kotlin
// Method 1: Using AdGroup (Supports 2F / MF Waterfall)
AppOpenResumeManager.setUpAppOpenResume(
    adGroup = AdsProvider.appOpenResumeAd,
    activities = arrayOf(MainActivity::class)
)

// Method 2: Single Ad Unit ID (Normal)
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
