package com.tomo.monetization.ads.natives

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.nativead.NativeAd
import com.tomo.monetization.ads.app_open.AppOpenAdGroup
import com.tomo.monetization.ads.base.AdUnitGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.tomo.monetization.Monetization

class NativeAdGroup(
    adUnits: List<NativeAdUnit>,
    private val name: String,
    val isFullScreen: Boolean,
    val reloadAfterVideo: Boolean = true,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AdUnitGroup<NativeAd, NativeAdUnit>(adUnits, coroutineScope) {
    companion object {
        private const val TAG = "NativeAdGroup"
    }

    constructor(
        vararg ids: Pair<String, String>,
        name: String,
        isFullScreen: Boolean = false,
        reloadAfterVideo: Boolean = true,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ) : this(
        adUnits = ids.map { NativeAdUnit(it.first, it.second) },
        name,
        isFullScreen,
        reloadAfterVideo,
        coroutineScope,
    )

    private var loadJob: Job? = null
    val clickedFlow = MutableStateFlow(false)

    fun loadAds(context: Context, timeout: Long = 30_000L) {
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
                val success = unit.loadAd(context, timeout, :: handleInternalClick)
                if (success) break
            }
        }
    }

    fun getLoadedAd(): NativeAd? {
        for (unit in adUnits) {
            unit.ad?.let {
                return if (unit.isExpired()) null else unit.ad
            }
        }
        return null
    }

    private fun handleInternalClick() {
        clickedFlow.value = true
    }

    fun resetClicked() {
        clickedFlow.value = false
    }

    fun releaseAll() {
        Log.d(TAG, "releaseAll")
        loadJob?.cancel()
        for (unit in adUnits) {
            Log.d(TAG, "destroying native ads $name")
            unit.ad?.destroy()
            unit.release()
        }
    }

    fun clone(): NativeAdGroup {
        val newUnits = adUnits.map { NativeAdUnit(it.id, it.name) }
        val newGroup = NativeAdGroup(newUnits, name, isFullScreen, reloadAfterVideo, coroutineScope)
        newGroup.config(*adUnits.map { it.enabled }.toBooleanArray())
        return newGroup
    }
}
