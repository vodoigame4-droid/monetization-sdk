package com.tomo.monetization.firstopen.model

import android.util.Log
import com.tomo.monetization.AppRemoteConfig
import com.tomo.monetization.Monetization
import com.tomo.monetization.uninstall.model.UninstallConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FOManager {
    private val _splashInitializedFlow = MutableStateFlow(false)
    val splashInitializedFlow = _splashInitializedFlow.asStateFlow()

    lateinit var foConfig: FOConfig
    lateinit var foCallback: FOCallback
    lateinit var uninstallConfig: UninstallConfig

    val isInitialized: Boolean
        get() = ::uninstallConfig.isInitialized

    fun startFlow(config: FOConfig, callback: FOCallback, uninstallConfig: UninstallConfig) {
        Log.d("InitialAds", "Start Flow")
        this.foConfig = config
        this.foCallback = callback
        this.uninstallConfig = uninstallConfig
    }

    fun finishSplashInitialization() {
        val elapsed = System.currentTimeMillis() - splashStartTime
        Log.d("FOTimeline", "[${elapsed}ms] finishSplashInitialization() called")
        _splashInitializedFlow.value = true
    }

    var splashStartTime: Long = 0L

    fun waitSplashInitialization() {
        splashStartTime = System.currentTimeMillis()
        Log.d("FOTimeline", "[0ms] waitSplashInitialization() - START")
        _splashInitializedFlow.value = false
        Monetization.recentlyTimeShowAds.clear()
        AppRemoteConfig.fetchRemoteConfig()
    }
}
