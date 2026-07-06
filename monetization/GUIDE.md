# Monetization Module Guide

Integration guide for the Monetization module (Ads, Analytics, Billing) into the project.

## 1. Introduction
The `monetization` module provides ready-to-use wrappers for handling Ads (Banner, Interstitial, Native, Rewarded, App Open), Consent (CMP), and Analytics.
The module handles SDK initialization and Consent Requests (CMP) automatically.

## 2. Integration Steps (Step-by-Step)

### Step 1: Create AdsProvider
Create an `AdsProvider` object (Singleton) to manage all ad IDs in the app. This makes management easier and allows calling ads from anywhere.

Each `AdGroup` can contain multiple IDs (Waterfall) to optimize fill rates.

```kotlin
// AdsProvider.kt
object AdsProvider {
    // Defines a Waterfall: High -> Medium -> Low
    // Ads load sequentially: If High fails/disabled -> load Medium -> ...
    // If an ad loads successfully, it stops and uses that ad.
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
        isFullScreen = false // Set true if it is a full screen native ad
    )
    
    // ... Other ad types
}
```

### Step 2: Init Configuration & Remote Config
During initialization (e.g., in `SplashActivity` or `FirstOpenViewModel`), you need to fetch the configuration from the server (Firebase Remote Config) to enable/disable specific ad IDs.

After configuration is complete, you **MUST** call `FOManager.finishSplashInitialization()` to signal that the initialization process is finished and to start the First Open flow (or navigate to Main).

```kotlinfun onRemoteConfigReady(config: FirebaseRemoteConfig) {
    
    // 1. Config enable/disable ad IDs in AdGroup
    // You must pass as many boolean values as there are IDs in the AdGroup
    AdsProvider.interSplashAd.config(
        config.getBoolean("enable_inter_splash_high"),   // ID 1
        config.getBoolean("enable_inter_splash_medium"), // ID 2
        config.getBoolean("enable_inter_splash_floor")   // ID 3
    )

    AdsProvider.nativeOnboardAd.config(
        config.getBoolean("enable_native_onboard_high"),
        config.getBoolean("enable_native_onboard_floor")
    )

    // 2. Global Config (Optional)
    // Monetization.updateAdsEnable(config.getBoolean("enable_all_ads"))

    // 3. Finish Init -> Start app flow
    FOManager.finishSplashInitialization()
}
// Example in ViewModel handling Splash/FirstOpen

```

**How it works:**
*   **Sequential Loading**: `AdGroup` will load each Unit ID sequentially in the order declared.
*   **Enable/Disable**: If a Unit ID is disabled (via the `config` function), the SDK will skip it and try the next Unit ID.
*   **Stop on Success**: As soon as an ad loads successfully, the loading process stops.

### Step 3: Load Ads
Call `loadAds` at appropriate times (e.g., `onResume` of an Activity, or after init configuration is done).

```kotlin
// MainActivity.kt
override fun onResume() {
    super.onResume()
    
    // Load App Open Ad (Automatically shows when available)
    AppOpenResumeManager.loadAds(this) 
    
    // Pre-load other ads
    AdsProvider.interSplashAd.loadAds(this)
    AdsProvider.nativeOnboardAd.loadAds(this)
}
```

### Step 4: Display Ads

#### 1. Interstitial Ad
```kotlin
AdsProvider.interSplashAd.showAds(activity, object : InterstitialAdCallback {
    override fun onNextAction(show: Boolean) {
        // Callback when ad is closed or failed to show -> Navigate to next screen
        navigateToNextScreen()
    }
})
```

#### 2. Native Ad (Compose)
```kotlin
NativeAdContent(
    adGroup = AdsProvider.nativeOnboardAd,
    modifier = Modifier.fillMaxWidth()
)
```

#### 3. App Open Ad
Setup in `Application` or `MainActivity` for automatic management:
```kotlin
AppOpenResumeManager.setUpAppOpenResume(
    adId = BuildConfig.app_open_id,
    name = "app_open_ad",
    activities = arrayOf(MainActivity::class)
)
```

## 3. Important Notes
- **Finish Init**: You must call `FOManager.finishSplashInitialization()` for App Open Ad and First Open flows to work correctly, as it marks the moment "Splash is done".
- **Config param matching**: The number of parameters in `adGroup.config(...)` must match exactly the number of IDs declared in `AdsProvider`. An Exception will be thrown if they do not match.

---
*Document created for TomoDev Team.*
