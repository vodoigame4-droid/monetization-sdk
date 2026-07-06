package com.tomo.monetization.ads.base

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class AdUnit<T>(val id: String, val name: String) {
    @Volatile var ad: T? = null
    @Volatile var adLoadedTimestamp: Long = 0L
    open val adTimeExpiration: Long = 60 * 60 * 1000L

    private val _statusFlow = MutableStateFlow(AdStatus.None)
    val statusFlow: StateFlow<AdStatus> = _statusFlow.asStateFlow()

    private val _enabledFlow = MutableStateFlow(true)
    val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

    val status: AdStatus get() = statusFlow.value

    val enabled: Boolean get() = enabledFlow.value

    fun config(enabled: Boolean) {
        _enabledFlow.value = enabled
    }

    fun updateStatus(status: AdStatus) {
        _statusFlow.value = status
    }

    fun shouldLoadAd(): Boolean {
        return when (status) {
            AdStatus.None, AdStatus.Failure -> true
            AdStatus.Loading -> false
            AdStatus.Ready -> isExpired()
            AdStatus.Shown -> true
        }
    }

    fun isExpired(): Boolean {
        return System.currentTimeMillis() - adLoadedTimestamp > adTimeExpiration
    }

    open fun release() {
        ad = null
        _statusFlow.value = AdStatus.None
    }
}