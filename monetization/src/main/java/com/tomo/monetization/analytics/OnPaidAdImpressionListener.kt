package com.tomo.monetization.analytics

import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo

/**
 * Interface definition for a callback to be invoked when an ad paid impression occurs.
 * Allows the application layer to intercept ad revenue data in real-time and log to custom analytics
 * (e.g. AppsFlyer, custom servers, etc.).
 */
interface OnPaidAdImpressionListener {
    fun onPaidAdImpression(
        adValue: AdValue,
        adUnitId: String?,
        responseInfo: ResponseInfo,
        adType: AdRevenueAdType
    )
}
