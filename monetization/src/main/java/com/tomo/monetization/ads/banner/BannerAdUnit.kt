package com.tomo.monetization.ads.banner

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
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

class BannerAdUnit(id: String, name: String, val isCollapsible: Boolean = false) : AdUnit<AdView>(id, name) {

    companion object {
        const val TAG = "BannerAdUnit"
    }

    suspend fun loadAd(context: Context, width: Int, timeout: Long): Boolean {
        Log.d(TAG, "loadAd: $name $id")

        if (!enabled || AppBilling.isPurchasedAdFree || id.isBlank()) {
            updateStatus(AdStatus.Failure)
            Log.d(TAG, "loadAd: $name $id is disabled or blank")
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
                internalLoadAd(context, width)
            }

            if (ad != null) {
                this@BannerAdUnit.ad?.destroy()
                this@BannerAdUnit.ad = ad
                adLoadedTimestamp = System.currentTimeMillis()
                updateStatus(AdStatus.Ready)
                true
            } else {
                updateStatus(AdStatus.Failure)
                false
            }
        }
    }

    private suspend fun internalLoadAd(context: Context, width: Int): AdView? = suspendCancellableCoroutine { cont ->
        val id = this@BannerAdUnit.id
        val adView = AdView(context).apply {
            adUnitId = id
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))

            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    EventTracking.logEvent("${name}_loaded")
                    Log.d(TAG, "onAdLoaded: $name $id")
                    if (cont.isActive) cont.resume(this@apply)
                }

                override fun onAdClicked() {
                    Log.d(TAG, "onAdClicked: $name $id")
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
            }

            setOnPaidEventListener { adValue ->
                responseInfo?.let {
                    LogEventManager.logPaidAdImpression(
                        adValue,
                        id,
                        it,
                        AdRevenueAdType.BANNER
                    )
                }
            }
        }

        EventTracking.logEvent("${name}_request")

        val request = if (!isCollapsible) {
            AdRequest.Builder()
        } else {
            getCollapsibleRequest()
        }.setHttpTimeoutMillis(30_000).build()

        adView.loadAd(request)

        cont.invokeOnCancellation {
            Log.e(TAG, "loadAd cancelled: $name $id")
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun getCollapsibleRequest(): AdRequest.Builder {
        val bundle = Bundle().apply {
            putString("collapsible", "bottom")
        }
        return AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, bundle)
    }
}
