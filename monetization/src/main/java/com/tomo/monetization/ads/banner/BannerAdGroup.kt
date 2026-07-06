package com.tomo.monetization.ads.banner

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdView
import com.tomo.monetization.ads.app_open.AppOpenAdGroup
import com.tomo.monetization.ads.base.AdUnitGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tomo.monetization.Monetization

class BannerAdGroup(
    adUnits: List<BannerAdUnit>,
    private val name: String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AdUnitGroup<AdView, BannerAdUnit>(adUnits, coroutineScope) {
    companion object {
        private const val TAG = "BannerAdGroup"
    }

    constructor(
        vararg ids: Pair<String, String>,
        name: String,
        isCollapsible: Boolean = false,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ) : this(
        adUnits = ids.map { BannerAdUnit(it.first, it.second, isCollapsible) },
        name,
        coroutineScope,
    )

    private var loadJob: Job? = null

    fun loadAds(context: Context, width: Int, timeout: Long = 30_000L) {
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

        Log.d(TAG, "loadAds: group $name")

        loadJob = coroutineScope.launch {
            for (unit in adUnits) {
                val success = unit.loadAd(context, width, timeout)
                if (success) break
            }

            if (!isAdReady) {
                Log.d(TAG, "loadAds: group $name failed. Retry after 5s.")
                delay(5000)
                Log.d(TAG, "loadAds: Retrying group $name")
                for (unit in adUnits) {
                    val success = unit.loadAd(context, width, timeout)
                    if (success) break
                }
            }
        }
    }

    fun getLoadedAd(): AdView? {
        for (unit in adUnits) {
            unit.ad?.let { return it }
        }
        return null
    }

    fun releaseAll() {
        Log.d(TAG, "releaseAll")
        loadJob?.cancel()
        for (unit in adUnits) {
            Log.d(TAG, "destroying banner ads $name")
            unit.ad?.destroy()
            unit.release()
        }
    }

    fun clone(): BannerAdGroup {
        val newUnits = adUnits.map { BannerAdUnit(it.id, it.name, it.isCollapsible) }
        val newGroup = BannerAdGroup(newUnits, name, coroutineScope)
        newGroup.config(*adUnits.map { it.enabled }.toBooleanArray())
        return newGroup
    }
}
