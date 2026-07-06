package com.tomo.monetization.analytics.adjust

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustAttribution
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.AdjustThirdPartySharing
import com.adjust.sdk.LogLevel
import com.google.android.gms.ads.AdValue
import com.google.firebase.Firebase
import com.google.firebase.analytics.ParametersBuilder
import com.google.firebase.analytics.analytics

object AdjustLog {
    private const val TAG = "AdjustLog"
    private var isInitialized = false
    private var adImpressionEvent: String? = null
    private var adApplovinEvent: String? = null

    class AdjustLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { Adjust.onResume() }
        override fun onActivityPaused(activity: Activity) { Adjust.onPause() }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
    }

    // ----------------------------------
    // Init Adjust
    // ----------------------------------
    fun initAdjust(
        context: Context,
        adjustToken: String,
        isSandbox: Boolean,
        adImpressionEvent: String?,
        adApplovinEvent: String?,
    ) {
        this.adImpressionEvent = adImpressionEvent
        this.adApplovinEvent = adApplovinEvent
        val environment = if (isSandbox) AdjustConfig.ENVIRONMENT_SANDBOX else AdjustConfig.ENVIRONMENT_PRODUCTION
        val config = AdjustConfig(context, adjustToken, environment).apply {
            setLogLevel(if (isSandbox) LogLevel.VERBOSE else LogLevel.ERROR)
            enablePreinstallTracking()
            enableSendingInBackground()
//            setFbAppId(FacebookSdk.getApplicationId())
            setOnAttributionChangedListener { attribution ->
                handleAttribution(attribution)
            }

            setOnEventTrackingSucceededListener {
                Log.d(TAG, "Event success: $it")
            }

            setOnEventTrackingFailedListener {
                Log.d(TAG, "Event failure: $it")
            }

            setOnSessionTrackingSucceededListener {
                Log.d(TAG, "Session success: $it")
            }

            setOnSessionTrackingFailedListener {
                Log.d(TAG, "Session failure: $it")
            }
        }
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(AdjustLifecycleCallbacks())
        Adjust.trackThirdPartySharing(AdjustThirdPartySharing(true))
        Adjust.initSdk(config)
        isInitialized = true
    }

    // ----------------------------------
    // Attribution
    // ----------------------------------
    private fun handleAttribution(attribution: AdjustAttribution) {
        Log.d(TAG, "Attribution callback called!")
        Log.d(TAG, "Attribution: $attribution")

        Adjust.getAdid { adjustId ->
            Log.d(TAG, "adjustId: $adjustId")
            val userIdBuilder = ParametersBuilder().apply {
                param("adjust_id", adjustId)
            }
            Log.d("EventTracking", "logEvent: user_id_mapping with ${userIdBuilder.bundle}")
            logFirebaseEvent("user_id_mapping", userIdBuilder)

            val userAttrBuilder = ParametersBuilder().apply {
                param("mmp", "Adjust")
                param("id", adjustId)
                attribution.network?.let { param("network", it) }
                attribution.campaign?.let { param("campaign", it) }
                attribution.adgroup?.let { param("adgroup", it) }
                attribution.creative?.let { param("creative", it) }
                attribution.trackerToken?.let { param("trackerToken", it) }
                attribution.trackerName?.let { param("trackerName", it) }
                attribution.clickLabel?.let { param("clickLabel", it) }
                attribution.costType?.let { param("costType", it) }
                attribution.costCurrency?.let { param("costCurrency", it) }
            }
            Log.d("EventTracking", "logEvent: user_attributions with ${userAttrBuilder.bundle}")
            logFirebaseEvent("user_attributions", userAttrBuilder)
        }
    }

    private fun logFirebaseEvent(
        event: String,
        params: ParametersBuilder
    ) {
        try {
            Firebase.analytics.logEvent(event, params.bundle)
        } catch (_: Exception) {
        }
    }

    // ----------------------------------
    // Revenue tracking
    // ----------------------------------
    fun logAdmobRevenue(adValue: AdValue) {
        if (isInitialized) {

            val value =  adValue.valueMicros / 1000000.0
            val adjustAdRevenue = AdjustAdRevenue("admob_sdk")
            adjustAdRevenue.setRevenue(value, adValue.currencyCode)
            Adjust.trackAdRevenue(adjustAdRevenue)

            // AdjustEvent
            val adjustEvent = AdjustEvent("ac85e5")
            adjustEvent.setRevenue(value, adValue.currencyCode)
            Adjust.trackEvent(adjustEvent)
        }
    }

    fun logPaidAdImpressionValue(revenue: Double, currency: String?) {
        if (isInitialized && adImpressionEvent != null) {
            val event = AdjustEvent(adImpressionEvent)
            event.setRevenue(revenue, currency)
            Adjust.trackEvent(event)
        }
    }

    fun logAdApplovinEvent() {
        if (isInitialized && adApplovinEvent != null) {
            val event = AdjustEvent(adApplovinEvent)
            Adjust.trackEvent(event)
        }
    }
}
