package com.tomo.monetization.ads.app_open

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.appopen.AppOpenAd
import com.tomo.monetization.AdType
import com.tomo.monetization.Monetization
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.base.AdUnitGroup
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.AdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class AppOpenAdGroup(
    adUnits: List<AppOpenAdUnit>,
    private val name: String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AdUnitGroup<AppOpenAd, AppOpenAdUnit>(adUnits, coroutineScope) {
    companion object {
        private const val TAG = "AppOpenAdGroup"
    }

    fun loadAds(context: Context, timeout: Long = 30_000L) {
        if (!enabled) {
            AdLogger.logLoadFailed("APP_OPEN", name, "", "Group is disabled")
            Log.d(TAG, "loadAds: group $name is not enabled")
            return
        }

        if (Monetization.isPremium()){
            AdLogger.logLoadFailed("APP_OPEN", name, "", "User is premium, skipping ads")
            Log.d(TAG, "loadAds: group $name, User isPremium")
            return
        }

        if (isAdLoading || isAdReady) {
            AdLogger.logLoadFailed("APP_OPEN", name, "", "Ads in this group are already loading or ready (status = $status)")
            Log.d(TAG, "loadAds: group $name is either loading or ready: status=$status")
            return
        }

        Monetization.calDistanceTime(
            type = AdType.APP_OPEN,
            onDismiss = {
                AdLogger.logLoadFailed("APP_OPEN", name, "", "Skip loading because gap/distance time has not passed yet.")
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
        callback: AppOpenAdCallback,
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
                },
                onAdShowed = {
                    Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdShowed")
                    callback.onAdShowed(adToShow.id, adToShow.name)
                },
                onAdClosed = {
                    Log.d(TAG, "showAds: group ${adToShow.name} ${adToShow.id} onAdClosed")
                    Monetization.updateRecentlyTimeShowAds(AdType.APP_OPEN)
                    callback.onAdClosed()
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
                    EventTracking.logScreen("appopen_ad_scr")
                    callback.onAdImpression(adToShow.id, adToShow.name)
                }
            )
        } ?: run {
            loadAds(activity)
        }
    }
}
