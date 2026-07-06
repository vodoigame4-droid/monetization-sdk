package com.tomo.monetization.ads.rewarded

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.rewarded.RewardedAd
import com.tomo.monetization.AdType
import com.tomo.monetization.Monetization
import com.tomo.monetization.ads.app_open.AppOpenAdGroup
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.base.AdUnitGroup
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.AdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class RewardedAdGroup(
    adUnits: List<RewardedAdUnit>,
    private val name: String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
): AdUnitGroup<RewardedAd, RewardedAdUnit>(adUnits, coroutineScope) {
    companion object {
        private const val TAG = "RewardedAdGroup"
    }

    constructor(
        vararg ids: Pair<String, String>,
        name: String,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ) : this(
        adUnits = ids.map { RewardedAdUnit(it.first, it.second) },
        name,
        coroutineScope,
    )

    fun loadAds(context: Context, timeout: Long = 30_000L) {
        if (!enabled) {
            AdLogger.logLoadFailed("REWARDED", name, "", "Group is disabled")
            Log.d(TAG, "loadAds: group $name is not enabled")
            return
        }

        if (isAdLoading || isAdReady) {
            AdLogger.logLoadFailed("REWARDED", name, "", "Ads in this group are already loading or ready (status = $status)")
            Log.d(TAG, "loadAds: group $name is either loading or ready: status=$status")
            return
        }

        Monetization.calDistanceTime(
            type = AdType.REWARDED,
            onDismiss = {
                AdLogger.logLoadFailed("REWARDED", name, "", "Skip loading because gap/distance time has not passed yet.")
                Log.d(TAG, "loadAds: group $name skip distance time")
            },
            showAds = {
                Log.d(TAG, "loadAds: group $name")
                coroutineScope.launch {
                    for (unit in adUnits) {
                        val success = unit.loadAd(context, timeout)
                        if (success) break
                    }

                    if (!isAdReady) {
                        Log.d(TAG, "loadAds: group $name failed. Retry after 5s.")
                        delay(5000)
                        Log.d(TAG, "loadAds: Retrying group $name")
                        for (unit in adUnits) {
                            val success = unit.loadAd(context, timeout)
                            if (success) break
                        }
                    }
                }
            }
        )
    }

    fun showAds(
        activity: Activity,
        callback: RewardedAdCallback,
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
                type = AdType.REWARDED,
                onDismiss = {
                    Log.d(TAG, "showAds: group $name skip distance time")
                    callback.onNextAction(false)
                },
                showAds = {
                    adToShow.showAd(
                        activity,
                        onAdClosed = {
                            callback.onNextAction(true)
                            Monetization.updateRecentlyTimeShowAds(AdType.REWARDED)
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
                            EventTracking.logScreen("rewarded_ad_scr")
                            callback.onAdImpression(adToShow.id, adToShow.name)
                        },
                        onUserEarnedReward = {
                            Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onUserEarnedReward")
                            callback.onUserEarnedReward(it)
                        }
                    )
                }
            )
        } ?: run {
            loadAds(activity)
        }
    }

    fun loadAndShow(
        activity: Activity,
        callback: RewardedAdCallback,
        timeout: Long = 10_000L
    ) {
        if (!enabled) {
            AdLogger.logLoadFailed("REWARDED", name, "", "Group is disabled")
            callback.onNextAction(false)
            return
        }

        if (!Monetization.isDistanceTimePassed(AdType.REWARDED)) {
            AdLogger.logLoadFailed("REWARDED", name, "", "Skip loading because gap/distance time has not passed yet.")
            Log.d(TAG, "loadAndShow: skip loading because distance time is not passed")
            callback.onNextAction(false)
            return
        }

        if (status == AdStatus.Ready) {
            showAds(activity, callback)
            return
        }

        val loadingDialog = com.tomo.monetization.util.PrepareLoadingAdsDialog(activity).apply {
            show()
        }

        coroutineScope.launch(Dispatchers.Main) {
            loadAds(activity, timeout = timeout)

            val startTime = System.currentTimeMillis()
            var isAdLoaded = false

            while (System.currentTimeMillis() - startTime < timeout) {
                if (status == AdStatus.Ready) {
                    isAdLoaded = true
                    break
                }
                if (status == AdStatus.Failure) {
                    break
                }
                delay(100)
            }

            try {
                if (loadingDialog.isShowing) {
                    loadingDialog.dismiss()
                }
            } catch (_: Exception) {}

            if (isAdLoaded) {
                showAds(activity, callback)
            } else {
                callback.onNextAction(false)
            }
        }
    }
}
