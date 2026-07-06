package com.tomo.monetization.ads

import com.tomo.monetization.ads.app_open.AppOpenAdGroup
import com.tomo.monetization.ads.app_open.AppOpenAdUnit
import com.tomo.monetization.ads.banner.BannerAdGroup
import com.tomo.monetization.ads.banner.BannerAdUnit
import com.tomo.monetization.ads.interstitial.InterstitialAdGroup
import com.tomo.monetization.ads.interstitial.InterstitialAdUnit
import com.tomo.monetization.ads.natives.NativeAdGroup
import com.tomo.monetization.ads.natives.NativeAdUnit
import com.tomo.monetization.ads.rewarded.RewardedAdGroup
import com.tomo.monetization.ads.rewarded.RewardedAdUnit

object AdsProvider {

    // Dynamic Maps for multi-ads support
    val banners = mutableMapOf<String, BannerAdGroup>()
    val natives = mutableMapOf<String, NativeAdGroup>()
    val interstitials = mutableMapOf<String, InterstitialAdGroup>()
    val rewardeds = mutableMapOf<String, RewardedAdGroup>()
    val appOpens = mutableMapOf<String, AppOpenAdGroup>()

    // Lookup helpers (auto-cloning View-based ads to prevent screen view conflicts)
    fun getBanner(key: String): BannerAdGroup? = banners[key]?.clone()
    fun getNative(key: String): NativeAdGroup? = natives[key]?.clone()
    fun getInterstitial(key: String): InterstitialAdGroup? = interstitials[key]
    fun getRewarded(key: String): RewardedAdGroup? = rewardeds[key]
    fun getAppOpen(key: String): AppOpenAdGroup? = appOpens[key]

    // Backward-compatible properties pointing to default test keys
    val interAd: InterstitialAdGroup
        get() = interstitials["inter_test"] ?: interstitials.values.firstOrNull() ?: interAdFallback
    val rewardedAd: RewardedAdGroup
        get() = rewardeds["rewarded_test"] ?: rewardeds.values.firstOrNull() ?: rewardedAdFallback
    val bannerAd: BannerAdGroup
        get() = banners["banner_test"] ?: banners.values.firstOrNull() ?: bannerAdFallback
    val nativeAd: NativeAdGroup
        get() = natives["native_test"] ?: natives.values.firstOrNull() ?: nativeAdFallback
    val appOpenAd: AppOpenAdGroup
        get() = appOpens["app_open_test"] ?: appOpens.values.firstOrNull() ?: appOpenAdFallback

    // Default Fallback objects to prevent any NPE before JSON loads
    private val interAdFallback by lazy {
        InterstitialAdGroup("ca-app-pub-3940256099942544/1033173712" to "inter_test", name = "inter_test")
    }
    private val rewardedAdFallback by lazy {
        RewardedAdGroup("ca-app-pub-3940256099942544/5224354917" to "rewarded_test", name = "rewarded_test")
    }
    private val bannerAdFallback by lazy {
        BannerAdGroup("ca-app-pub-3940256099942544/6300978111" to "banner_test", name = "banner_test")
    }
    private val nativeAdFallback by lazy {
        NativeAdGroup("ca-app-pub-3940256099942544/2247696110" to "native_test", name = "native_test")
    }
    private val appOpenAdFallback by lazy {
        AppOpenAdGroup(
            adUnits = listOf(
                AppOpenAdUnit("ca-app-pub-3940256099942544/9257395921", "app_open_test")
            ),
            name = "app_open_test"
        )
    }

    fun initDefault() {
        if (interstitials.isEmpty()) interstitials["inter_test"] = interAdFallback
        if (rewardeds.isEmpty()) rewardeds["rewarded_test"] = rewardedAdFallback
        if (banners.isEmpty()) banners["banner_test"] = bannerAdFallback
        if (natives.isEmpty()) natives["native_test"] = nativeAdFallback
        if (appOpens.isEmpty()) appOpens["app_open_test"] = appOpenAdFallback
    }
}
