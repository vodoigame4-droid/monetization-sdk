package com.tomo.monetization.ads.rewarded

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.tomo.monetization.NetworkManager
import com.tomo.monetization.ads.app_open.AppOpenResumeManager
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.base.AdUnit
import com.tomo.monetization.analytics.AdRevenueAdType
import com.tomo.monetization.analytics.LogEventManager
import com.tomo.monetization.billing.AppBilling
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.PrepareLoadingAdsDialog
import com.tomo.monetization.util.launchWhenResumed
import com.tomo.monetization.util.AdLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class RewardedAdUnit(id: String, name: String) : AdUnit<RewardedAd>(id, name) {
    companion object {
        const val TAG = "RewardedAdUnit"
    }

    suspend fun loadAd(context: Context, timeout: Long): Boolean {
        Log.d(TAG, "loadAd: $name $id")

        if (!enabled || AppBilling.isPurchasedAdFree) {
            updateStatus(AdStatus.Failure)
            AdLogger.logLoadFailed("REWARDED", name, id, "Ad is disabled or user is Premium")
            Log.d(TAG, "loadAd: $name $id is disabled")
            return false
        }

        if (!NetworkManager.isNetworkConnected()) {
            updateStatus(AdStatus.Failure)
            AdLogger.logLoadFailed("REWARDED", name, id, "No internet connection")
            Log.d(TAG, "loadAd: $name $id no internet")
            return false
        }

        if (!shouldLoadAd()) {
            AdLogger.logLoadFailed("REWARDED", name, id, "Ad already loaded/loading and doesn't need reload")
            Log.d(TAG, "loadAd: $name $id doesn't need to be loaded")
            return true
        }

        AdLogger.logLoadStart("REWARDED", name, id)
        Log.d(TAG, "loadAd: $name $id loading")
        updateStatus(AdStatus.Loading)

        return withContext(Dispatchers.Main) {
            val ad = withTimeoutOrNull(timeout) {
                internalLoadAd(context)
            }

            if (ad != null) {
                ad.setImmersiveMode(true)
                this@RewardedAdUnit.ad = ad
                adLoadedTimestamp = System.currentTimeMillis()
                updateStatus(AdStatus.Ready)
                true
            } else {
                updateStatus(AdStatus.Failure)
                false
            }
        }
    }

    fun showAd(
        activity: Activity,
        onAdShowed: () -> Unit,
        onAdClosed: () -> Unit,
        onAdClicked: () -> Unit,
        onAdFailedToShow: (AdError?) -> Unit,
        onAdImpression: () -> Unit,
        onUserEarnedReward: (RewardItem) -> Unit,
    ) {
        Log.d(TAG, "showAd: $name $id")

        when (status) {
            AdStatus.None,
            AdStatus.Loading -> {
                Log.d(TAG, "showAd: $name $id not ready")
            }

            AdStatus.Failure -> {
                Log.d(TAG, "showAd: $name $id failed to load")
            }

            AdStatus.Ready -> {
                val ad = ad ?: run {
                    onAdFailedToShow(AdError(0, "Reward ad is null", ""))
                    return
                }

                val loadingDialog = PrepareLoadingAdsDialog(activity).apply { show() }

                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdClicked() {
                        Log.d(TAG, "onAdClicked: $name $id")
                        EventTracking.logEvent("${name}_click")
                        LogEventManager.logClickAdsEvent(ad.adUnitId)
                        AppOpenResumeManager.disableAppOpenResumeOnce()
                        onAdClicked()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "onAdClosed: $name $id")
                        dismissLoadAdsDialog(activity, loadingDialog)
                        onAdClosed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        AdLogger.logShowFailed("REWARDED", name, id, "${adError.code} - ${adError.message}")
                        Log.d(TAG, "onAdFailedToShow: $name $id")
                        dismissLoadAdsDialog(activity, loadingDialog)
                        onAdFailedToShow(adError)
                    }

                    override fun onAdImpression() {
                        AdLogger.logImpression("REWARDED", name, id)
                        Log.d(TAG, "onAdImpression: $name $id")
                        EventTracking.logEvent("${name}_view")
                        updateStatus(AdStatus.Shown)
                        onAdImpression()
                    }

                    override fun onAdShowedFullScreenContent() {
                        AdLogger.logShowed("REWARDED", name, id)
                        Log.d(TAG, "onAdShowed: $name $id")
                        activity.launchWhenResumed {
                            delay(3000)
                            loadingDialog.hideLoadingAdsText()
                        }
                        onAdShowed()
                    }
                }

                activity.launchWhenResumed {
                    delay(150L)
                    withContext(Dispatchers.Main) {
                        ad.show(activity) {
                            Log.d(TAG, "onUserEarnedReward: $name $id")
                            onUserEarnedReward(it)
                        }
                    }
                }
            }

            AdStatus.Shown -> {
                Log.e(TAG, "showAd: $name $id already shown")
                onAdFailedToShow(AdError(0, "Reward ad is null", ""))
            }
        }
    }

    private suspend fun internalLoadAd(context: Context): RewardedAd? = suspendCancellableCoroutine { cont ->
        val request = AdRequest.Builder()
            .setHttpTimeoutMillis(30_000)
            .build()
        EventTracking.logEvent("${name}_request")

        RewardedAd.load(context, id, request, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                EventTracking.logEvent("${name}_loaded")
                AdLogger.logLoaded("REWARDED", name, id)
                Log.d(TAG, "onAdLoaded: $name $id")
                ad.setOnPaidEventListener { value ->
                    LogEventManager.logPaidAdImpression(
                        value,
                        id,
                        ad.responseInfo,
                        AdRevenueAdType.REWARDED
                    )
                }
                if (cont.isActive) cont.resume(ad)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                EventTracking.logEvent("${name}_failed")
                AdLogger.logLoadFailed("REWARDED", name, id, "${error.code} - ${error.message}")
                Log.e(TAG, "onAdFailedToLoad: $name $id ${error.message}")
                if (cont.isActive) cont.resume(null)
            }
        })

        cont.invokeOnCancellation {
            Log.e(TAG, "loadAd cancelled: $name $id")
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun dismissLoadAdsDialog(activity: Activity, dialog: PrepareLoadingAdsDialog) {
        try {
            if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing) {
                dialog.dismiss()
            }
        } catch (_: Exception) {
        }
    }
}
