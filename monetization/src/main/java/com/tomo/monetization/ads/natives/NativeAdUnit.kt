package com.tomo.monetization.ads.natives

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY
import com.tomo.monetization.NetworkManager
import com.tomo.monetization.ads.app_open.AppOpenResumeManager
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.base.AdUnit
import com.tomo.monetization.analytics.AdRevenueAdType
import com.tomo.monetization.analytics.LogEventManager
import com.tomo.monetization.billing.AppBilling
import com.tomo.monetization.util.EventTracking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class NativeAdUnit(id: String, name: String) : AdUnit<NativeAd>(id, name) {

    companion object {
        const val TAG = "NativeAdUnit"
    }

    suspend fun loadAd(context: Context, timeout: Long, onClick: () -> Unit): Boolean {
        Log.d(TAG, "loadAd: $name $id")

        if (!enabled || AppBilling.isPurchasedAdFree) {
            updateStatus(AdStatus.Failure)
            Log.d(TAG, "loadAd: $name $id is disabled")
            return false
        }

        if (!NetworkManager.isNetworkConnected()) {
            updateStatus(AdStatus.Failure)
            Log.d(TAG, "loadAd: $name $id no internet")
            return false
        }

        if (!shouldLoadAd()) {
            Log.d(TAG, "loadAd: $name $id doesn't need to be loaded")
            return true
        }

        Log.d(TAG, "loadAd: $name $id loading")
        updateStatus(AdStatus.Loading)

        return withContext(Dispatchers.Main) {
            val ad = withTimeoutOrNull(timeout) {
                internalLoadAd(context, onClick)
            }

            if (ad != null) {
                this@NativeAdUnit.ad?.destroy()
                this@NativeAdUnit.ad = ad
                adLoadedTimestamp = System.currentTimeMillis()
                updateStatus(AdStatus.Ready)
                true
            } else {
                updateStatus(AdStatus.Failure)
                false
            }
        }
    }

    private suspend fun internalLoadAd(context: Context, onClick: () -> Unit): NativeAd? = suspendCancellableCoroutine { cont ->
        val adLoader = AdLoader.Builder(context, id).forNativeAd { nativeAd ->
            Log.d(TAG, "onAdLoaded: $name $id")
            EventTracking.logEvent("${name}_loaded")
            nativeAd.setOnPaidEventListener { adValue ->
                nativeAd.responseInfo?.let {
                    LogEventManager.logPaidAdImpression(
                        adValue,
                        id,
                        it,
                        AdRevenueAdType.NATIVE
                    )
                }
            }
            if (cont.isActive) cont.resume(nativeAd)
        }.withAdListener(object : AdListener() {
            override fun onAdClicked() {
                Log.d(TAG, "onAdClicked: $name $id")
                onClick?.invoke()
                EventTracking.logEvent("${name}_click")
                LogEventManager.logClickAdsEvent(id)
                AppOpenResumeManager.disableAppOpenResumeOnce()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                EventTracking.logEvent("${name}_failed")
                Log.e(TAG, "onAdFailedToLoad: $name $id ${error.message}")
                if (cont.isActive) cont.resume(null)
            }

            override fun onAdImpression() {
                Log.d(TAG, "onAdImpression: $name $id")
                EventTracking.logEvent("${name}_view")
                updateStatus(AdStatus.Shown)
            }
        }).withNativeAdOptions(NativeAdOptions.Builder()
            .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
            .setMediaAspectRatio(NATIVE_MEDIA_ASPECT_RATIO_ANY)
            .build()
        ).build()

        EventTracking.logEvent("${name}_request")
        adLoader.loadAd(AdRequest.Builder().setHttpTimeoutMillis(30_000).build())

        cont.invokeOnCancellation {
            Log.e(TAG, "loadAd cancelled: $name $id")
            if (cont.isActive) cont.resume(null)
        }
    }
}
