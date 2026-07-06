package com.tomo.monetization.ads.interstitial

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.tomo.monetization.AdType
import com.tomo.monetization.Monetization
import com.tomo.monetization.ads.app_open.AppOpenAdGroup
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.base.AdUnitGroup
import com.tomo.monetization.util.EventTracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class InterstitialAdGroup(
    adUnits: List<InterstitialAdUnit>,
    private val name: String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
): AdUnitGroup<InterstitialAd, InterstitialAdUnit>(adUnits, coroutineScope) {
    companion object {
        private const val TAG = "InterstitialAdGroup"
    }

    constructor(
        vararg ids: Pair<String, String>,
        name: String,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ) : this(
        adUnits = ids.map { InterstitialAdUnit(it.first, it.second) },
        name,
        coroutineScope,
    )

    fun loadAds(context: Context, timeout: Long = 30_000L, retryFail: Boolean = true) {
        if (!enabled) {
            Log.d(TAG, "loadAds: group $name is not enabled")
            return
        }

        if (Monetization.isPremium()){
            Log.d(TAG, "loadAds: group $name, User isPremium")
            return
        }

        if (isAdLoading || isAdReady) {
            Log.d(TAG, "loadAds: group $name is either loading or ready: status=$status")
            return
        }

        Monetization.calDistanceTime(
            type = AdType.INTERSTITIAL,
            onDismiss = {
                Log.d(TAG, "loadAds: group $name skip distance time")
            },
            showAds = {
                Log.d(TAG, "loadAds: group $name")

                coroutineScope.launch {
                    val startTime = System.currentTimeMillis()
                    for (unit in adUnits) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val remainingTimeout = timeout - elapsed
                        if (remainingTimeout < 1000L) {
                            Log.d(TAG, "loadAds: not enough remaining time ($remainingTimeout ms), skip loading next unit")
                            break
                        }
                        val success = unit.loadAd(context, remainingTimeout)
                        if (success) break
                    }

                    if (!isAdReady && retryFail) {
                        Log.d(TAG, "loadAds: group $name failed. Retry after 5s.")
                        delay(5000)
                        Log.d(TAG, "loadAds: Retrying group $name")
                        val retryStartTime = System.currentTimeMillis()
                        for (unit in adUnits) {
                            val elapsed = System.currentTimeMillis() - retryStartTime
                            val remainingTimeout = timeout - elapsed
                            if (remainingTimeout < 1000L) {
                                break
                            }
                            val success = unit.loadAd(context, remainingTimeout)
                            if (success) break
                        }
                    }
                }
            }
        )
    }

    fun showAds(
        activity: Activity,
        callback: InterstitialAdCallback,
    ) {
        Log.d(TAG, "showAds: group $name")

        if (!enabled) {
            Log.e(TAG, "showAds: group ${adUnits.joinToString(",") { it.name }} disabled")
            callback.onNextAction(false)
            return
        }

        if (status != AdStatus.Ready) {
            Log.e(TAG, "showAds: group $name not ready")
            callback.onNextAction(false)
            return
        }

        adUnits.firstOrNull { it.status == AdStatus.Ready }?.let { adToShow ->
            Monetization.calDistanceTime(
                type = AdType.INTERSTITIAL,
                onDismiss = {
                Log.d(TAG, "showAds: group $name skip distance time")
                callback.onNextAction(false)
            }, showAds = {
                adToShow.showAd(
                    activity,
                    onNextAction = {
                        callback.onNextAction(it)
                        if (it) Monetization.updateRecentlyTimeShowAds(AdType.INTERSTITIAL)
                    },
                    onAdShowed = {
                        Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdShowed")
                        callback.onAdShowed(adToShow.id, adToShow.name)
                    },
                    onAdClicked = {
                        Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdClicked")
                        callback.onAdClicked(adToShow.id, adToShow.name)
                    },
                    onAdFailedToShow = {
                        Log.e(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdFailedToShow: ${it?.message}")
                        callback.onAdFailedToShow(it)
                    },
                    onAdImpression = {
                        Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdImpression")
                        EventTracking.logScreen("inter_ad_scr")
                        callback.onAdImpression(adToShow.id, adToShow.name)
                    }
                )
            })
        } ?: run {
            loadAds(activity)
        }
    }

    fun showSplashAds(
        activity: Activity,
        callback: InterstitialAdCallback,
    ) {
        Log.d(TAG, "showAds: group $name")

        if (!enabled) {
            Log.e(TAG, "showAds: group ${adUnits.joinToString(",") { it.name }} disabled")
            callback.onNextAction(false)
            return
        }

        if (status != AdStatus.Ready) {
            Log.e(TAG, "showAds: group $name not ready")
            callback.onNextAction(false)
            return
        }

        adUnits.firstOrNull { it.status == AdStatus.Ready }?.let { adToShow ->
            adToShow.showAd(
                activity,
                onNextAction = {
                    callback.onNextAction(it)
                    if (it) Monetization.updateRecentlyTimeShowAds(AdType.INTERSTITIAL)
                },
                onAdShowed = {
                    Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdShowed")
                    callback.onAdShowed(adToShow.id, adToShow.name)
                },
                onAdClicked = {
                    Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdClicked")
                    callback.onAdClicked(adToShow.id, adToShow.name)
                },
                onAdFailedToShow = {
                    Log.e(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdFailedToShow: ${it?.message}")
                    callback.onAdFailedToShow(it)
                },
                onAdImpression = {
                    Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdImpression")
                    EventTracking.logScreen("inter_splash_scr")
                    callback.onAdImpression(adToShow.id, adToShow.name)
                }
            )
        }
    }
}
