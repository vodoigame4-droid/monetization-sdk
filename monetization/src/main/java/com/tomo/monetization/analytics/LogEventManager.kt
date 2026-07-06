package com.tomo.monetization.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdapterResponseInfo
import com.google.android.gms.ads.ResponseInfo
import com.google.gson.Gson
import com.tomo.monetization.analytics.adjust.AdjustLog
import com.tomo.monetization.analytics.adjust.AdjustLogConfig
import com.tomo.monetization.analytics.facebook.FacebookAnalyticsUtil
import com.tomo.monetization.analytics.firebase.FirebaseAnalyticsUtil
import com.tomo.monetization.Monetization

@SuppressLint("StaticFieldLeak")
object LogEventManager {

    private const val TAG = "LogEventManager"

    private lateinit var context: Context

    // ------------------------------------------------
    // Init
    // ------------------------------------------------
    fun initLogEventManager(
        context: Context,
        adjustConfig: AdjustLogConfig?,
    ) {
        this.context = context.applicationContext

        adjustConfig?.let {
            AdjustLog.initAdjust(context, it.adjustToken, it.isSandbox, it.adImpressionEvent, it.adApplovinEvent)
        }
    }

    // ------------------------------------------------
    // Public APIs
    // ------------------------------------------------
    fun logPaidAdImpression(
        adValue: AdValue,
        adUnitId: String?,
        responseInfo: ResponseInfo,
        adType: AdRevenueAdType
    ) {
        Log.d(TAG, "logPaidAdImpression: ${Gson().toJson(responseInfo)}")

        val valueMicros = adValue.valueMicros.toFloat()
        val precision = adValue.precisionType
        val network = responseInfo.mediationAdapterClassName
        val loadedAdapter: AdapterResponseInfo? = responseInfo.loadedAdapterResponseInfo
        val adSourceName = loadedAdapter?.adSourceName ?: "AdMob"

        logEventWithAds(
            revenue = valueMicros,
            precision = precision,
            adUnitId = adUnitId,
            network = network,
            adType = adType,
            adSource = adSourceName
        )

        AdjustLog.logAdmobRevenue(adValue)

        if (Monetization.isFacebookLoggingEnabled) {
            FacebookAnalyticsUtil.logPaidAdValueAsPurchaseEvent(
                context, adValue.valueMicros.toDouble(), adValue.currencyCode
            )
        }

        Monetization.onPaidAdImpressionListener?.onPaidAdImpression(
            adValue, adUnitId, responseInfo, adType
        )
    }

    fun logClickAdsEvent(adUnitId: String?) {
        Log.d(TAG, "User click ad for ad unit $adUnitId.")
        val bundle = bundleOf("ad_unit_id" to adUnitId)

        if (Monetization.isFirebaseLoggingEnabled) {
            FirebaseAnalyticsUtil.logClickAdsEvent(bundle)
        }
        if (Monetization.isFacebookLoggingEnabled) {
            FacebookAnalyticsUtil.logClickAdsEvent(context, bundle)
        }
    }

    // ------------------------------------------------
    // Internal
    // ------------------------------------------------
    private fun logEventWithAds(
        revenue: Float,
        precision: Int,
        adUnitId: String?,
        network: String?,
        adType: AdRevenueAdType,
        adSource: String
    ) {
        Log.i(
            TAG, String.format(
                "Paid event of value %.0f microcents in currency USD of precision %s\noccurred for ad unit %s from ad network %s. adSource = %s.",
                revenue,
                precision,
                adUnitId,
                network,
                adSource
            )
        )

        val bundle = Bundle().apply {
            putDouble("valuemicros", revenue.toDouble())
            putString("currency", "USD")
            putInt("precision", precision)
            putString("adunitid", adUnitId)
            putString("network", network)
            putString("ad_format", adType.toString())
            putString("ad_source", adSource)
        }

        logPaidAdImpressionValue(
            revenue.toDouble(),
            precision,
            adUnitId,
            network,
            adType,
            adSource
        )

        if (Monetization.isFirebaseLoggingEnabled) {
            FirebaseAnalyticsUtil.logEventWithAds(bundle)
        }
        if (Monetization.isFacebookLoggingEnabled) {
            FacebookAnalyticsUtil.logEventWithAds(context, bundle)
            FacebookAnalyticsUtil.logTotalRevenue001Ad(context, bundle)
        }
    }

    private fun logPaidAdImpressionValue(
        value: Double,
        precision: Int,
        adUnitId: String?,
        network: String?,
        adType: AdRevenueAdType,
        adSource: String
    ) {
        val finalValue = value / 1_000_000.0

        val bundle = Bundle().apply {
            putDouble("value", finalValue)
            putString("currency", "USD")
            putString("fb_currency", "USD")
            putInt("precision", precision)
            putString("adunitid", adUnitId)
            putString("network", network)
            putString("ad_format", adType.toString())
            putString("ad_source", adSource)
        }

        AdjustLog.logPaidAdImpressionValue(finalValue, "USD")
        if (adSource.lowercase().contains("applovin"))  AdjustLog.logAdApplovinEvent()
        
        if (Monetization.isFirebaseLoggingEnabled) {
            FirebaseAnalyticsUtil.logPaidAdImpressionValue(bundle)
        }
        if (Monetization.isFacebookLoggingEnabled) {
            FacebookAnalyticsUtil.logPaidAdImpressionValue(context, bundle)
        }
    }
}
