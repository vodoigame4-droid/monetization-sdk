package com.tomo.monetization

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.tomo.monetization.analytics.LogEventManager
import com.tomo.monetization.analytics.adjust.AdjustLogConfig
import com.tomo.monetization.firstopen.FOSharedPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

enum class AdType {
    INTERSTITIAL, REWARDED, APP_OPEN
}

object Monetization {
    private const val TAG = "Monetization"
    lateinit var appContext: Context
    private var enableInitMediation = true
    private var testDeviceIds: List<String>? = null
    private var disableDistanceTimeOnce = false
    private var isPremiumProvider: () -> Boolean = { false }

    private val _adsInitFlow = MutableStateFlow(false)
    val adsInitFlow: StateFlow<Boolean> = _adsInitFlow.asStateFlow()

    private val _adsEnableFlow = MutableStateFlow(false)
    val adsEnableFlow: StateFlow<Boolean> = _adsEnableFlow.asStateFlow()

    var consentTestDevice: String = "35A9CE9AEA90668EC7EF210A548361D9"
    internal var recentlyTimeShowAds = mutableMapOf<AdType, LocalDateTime>()
    internal var distanceTimeShowAds = mutableMapOf(
        AdType.INTERSTITIAL to 15,
        AdType.REWARDED to 0,
        AdType.APP_OPEN to 15
    )

    fun isPremium(): Boolean = isPremiumProvider()

    fun updateDistanceTime(type: AdType, distance: Int) {
        distanceTimeShowAds[type] = distance
    }

    fun updateAdsEnable(enable: Boolean, distance: Int) {
        _adsEnableFlow.value = enable
        distanceTimeShowAds[AdType.INTERSTITIAL] = distance
    }

    fun updateAdsInit(init: Boolean) {
        _adsInitFlow.value = init
    }

    fun calDistanceTime(
        type: AdType,
        onDismiss: () -> Unit,
        showAds: () -> Unit,
    ) {
        if (!disableDistanceTimeOnce) {
            val lastShow = recentlyTimeShowAds[type]
            if (lastShow != null) {
                val distanceTime = distanceTimeShowAds[type] ?: 30
                val distanceShowAds = abs(Duration.between(lastShow, LocalDateTime.now()).seconds)
                if (distanceShowAds > distanceTime) {
                    showAds.invoke()
                } else {
                    onDismiss.invoke()
                }
            } else {
                showAds.invoke()
            }
        } else {
            disableDistanceTimeOnce = false
            showAds.invoke()
        }
    }

    internal fun updateRecentlyTimeShowAds(type: AdType) {
        recentlyTimeShowAds[type] = LocalDateTime.now()
    }

    fun disableDistanceTimeOnce() {
        disableDistanceTimeOnce = true
    }

    fun isDistanceTimePassed(type: AdType): Boolean {
        if (disableDistanceTimeOnce) return true
        val lastShow = recentlyTimeShowAds[type] ?: return true
        val distanceTime = distanceTimeShowAds[type] ?: 30
        val distanceShowAds = java.lang.Math.abs(java.time.Duration.between(lastShow, java.time.LocalDateTime.now()).seconds)
        return distanceShowAds > distanceTime
    }

    fun config(
        context: Context,
        adjustConfig: AdjustLogConfig? = null,
        testDeviceIds: List<String>? = null,
        consentTestDevice: String = "",
        enableInitMediation: Boolean = false,
        isPremiumProvider: () -> Boolean = { false }
    ) {
        Log.d("", "Monetization ver: 1.1.12")
        appContext = context
        FOSharedPref.initPrefs(context)
        LogEventManager.initLogEventManager(context, adjustConfig)
        this.consentTestDevice = consentTestDevice
        this.testDeviceIds = testDeviceIds
        this.enableInitMediation = enableInitMediation
        this.isPremiumProvider = isPremiumProvider
    }

    fun init(context: Context, onComplete: () -> Unit) {
        val builder = RequestConfiguration.Builder()
        testDeviceIds?.let {
            builder.setTestDeviceIds(it)
        }

        MobileAds.setRequestConfiguration(builder.build())
        if (!enableInitMediation) {
            MobileAds.disableMediationAdapterInitialization(context)
        }

        onComplete()
        Log.d(TAG, "Starting MobileAds.initialize asynchronously in background...")
        MobileAds.initialize(context.applicationContext) { status ->
            status.adapterStatusMap.forEach { (name, adapterStatus) ->
                Log.d(TAG, "Adapter name: $name, State: ${adapterStatus.initializationState}, Description: ${adapterStatus.description}, Latency: ${adapterStatus.latency}")
            }
        }
    }

    val ads: com.tomo.monetization.ads.AdsProvider
        get() = com.tomo.monetization.ads.AdsProvider

    fun loadConfigFromJson(
        context: Context, 
        jsonString: String,
        resumeActivities: List<kotlin.reflect.KClass<out android.app.Activity>> = emptyList()
    ) {
        com.tomo.monetization.ads.AdsConfigLoader.loadConfigFromJson(context, jsonString, resumeActivities)
    }
}
