package com.tomo.monetization.analytics.facebook

import android.content.Context
import android.os.Bundle
import com.facebook.appevents.AppEventsLogger
import java.math.BigDecimal
import java.util.Currency

object FacebookAnalyticsUtil {

    private inline fun withLogger(context: Context, block: (AppEventsLogger) -> Unit) {
        runCatching {
            AppEventsLogger.newLogger(context.applicationContext)
        }.getOrNull()?.let { logger ->
            runCatching { block(logger) }
        }
    }

    // ------------------------------------------------
    // Ads
    // ------------------------------------------------
    fun logEventWithAds(context: Context, params: Bundle?) {
        withLogger(context) { it.logEvent("paid_ad_impression", params) }
    }

    fun logPaidAdImpressionValue(context: Context, bundle: Bundle?) {
        withLogger(context) { it.logEvent("paid_ad_impression_value", bundle) }
    }

    fun logClickAdsEvent(context: Context, bundle: Bundle?) {
        withLogger(context) { it.logEvent("event_user_click_ads", bundle) }
    }

    // ------------------------------------------------
    // Revenue
    // ------------------------------------------------
    fun logCurrentTotalRevenueAd(context: Context, eventName: String, bundle: Bundle) {
        withLogger(context) { it.logEvent(eventName, bundle) }
    }

    fun logTotalRevenue001Ad(context: Context, bundle: Bundle) {
        withLogger(context) { it.logEvent("paid_ad_impression_value_001", bundle) }
    }

    /**
     * Facebook expects purchase value in standard currency unit.
     * revenue is in micros → divide by 1_000_000
     */
    fun logPaidAdValueAsPurchaseEvent(
        context: Context,
        revenue: Double,
        currencyCode: String
    ) {
        withLogger(context) {
            it.logPurchase(
                BigDecimal.valueOf(revenue / 1_000_000.0),
                Currency.getInstance(currencyCode)
            )
        }
    }
}
