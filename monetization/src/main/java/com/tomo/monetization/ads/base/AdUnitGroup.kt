package com.tomo.monetization.ads.base

import com.tomo.monetization.Monetization
import com.tomo.monetization.billing.AppBilling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

abstract class AdUnitGroup<O, T : AdUnit<O>>(
    val adUnits: List<T>,
    coroutineScope: CoroutineScope
) {
    val enabledFlow: StateFlow<Boolean>
    val statusFlow: StateFlow<AdStatus>
    val skipFlow: StateFlow<Boolean>

    init {
        val adUnitEnabledFlow = combine(adUnits.map { it.enabledFlow }) { booleans ->
            booleans.any { it }
        }
        val adsReadyFlow = combine(Monetization.adsInitFlow, Monetization.adsEnableFlow) { booleans ->
            booleans.all { it }
        }
        enabledFlow = combine(
            adUnitEnabledFlow,
            adsReadyFlow,
            AppBilling.isAdFreeFlow
        ) { adUnitEnabled, adsReady, isAdFree ->
            adUnitEnabled && adsReady && !isAdFree
        }.stateIn(coroutineScope, SharingStarted.Eagerly, enabled)

        val statusFlows = adUnits.map { it.statusFlow }
        statusFlow = combine(statusFlows) { statuses ->
            getCombinedAdStatus(statuses.toList())
        }.stateIn(coroutineScope, SharingStarted.Eagerly, status)

        skipFlow = combine(enabledFlow, statusFlow) { enabled, status ->
            !enabled || status == AdStatus.Failure
        }.stateIn(coroutineScope, SharingStarted.Eagerly, skip)
    }

    val enabled: Boolean
        get() {
            if (AppBilling.isAdFreeFlow.value) return false
            if (!Monetization.adsInitFlow.value) return false
            if (!Monetization.adsEnableFlow.value) return false
            if (adUnits.isEmpty()) return false
            return adUnits.any { it.enabled }
        }

    val status: AdStatus
        get() = getCombinedAdStatus(adUnits.map { it.status })

    val skip: Boolean
        get() = !enabled || status == AdStatus.Failure

    fun config(vararg enabled: Boolean) {
        require(enabled.size == adUnits.size) {
            "config and ad unit lists must have the same size"
        }
        adUnits.forEachIndexed { index, adUnit ->
            adUnit.config(enabled[index])
        }
    }

    val isAdLoading: Boolean get() = status == AdStatus.Loading

    val isAdReady: Boolean get() = status == AdStatus.Ready

    val isAdFailed: Boolean get() = status == AdStatus.Failure

    val isAdShown: Boolean get() = status == AdStatus.Shown

    private fun getCombinedAdStatus(statuses: List<AdStatus>): AdStatus {
        var result = statuses.first()
        for (i in statuses.indices) {
            result = when (result) {
                AdStatus.None -> when (statuses[i]) {
                    AdStatus.Ready -> AdStatus.Ready
                    AdStatus.Loading -> AdStatus.Loading
                    AdStatus.Shown -> AdStatus.Shown
                    else -> AdStatus.None
                }

                AdStatus.Loading -> AdStatus.Loading
                AdStatus.Ready -> AdStatus.Ready
                AdStatus.Failure -> when (statuses[i]) {
                    AdStatus.Ready -> AdStatus.Ready
                    AdStatus.Loading -> AdStatus.Loading
                    AdStatus.Shown -> AdStatus.Shown
                    AdStatus.Failure -> AdStatus.Failure
                    else -> AdStatus.None
                }

                AdStatus.Shown -> AdStatus.Shown
            }
        }
        return result
    }
}
