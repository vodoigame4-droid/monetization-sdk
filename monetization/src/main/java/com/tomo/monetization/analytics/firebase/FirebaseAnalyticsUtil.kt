package com.tomo.monetization.analytics.firebase

import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

object FirebaseAnalyticsUtil {
    private const val TAG = "FirebaseAnalyticsUtil"

    private val firebaseAnalytics: FirebaseAnalytics by lazy {
        Firebase.analytics
    }

    // ------------------------------------------------
    // Base
    // ------------------------------------------------
    fun logEventTracking(eventName: String, bundle: Bundle?) {
        firebaseAnalytics.logEvent(eventName, bundle)
    }

    // ------------------------------------------------
    // Ads
    // ------------------------------------------------
    fun logEventWithAds(params: Bundle?) {
        logEventTracking("paid_ad_impression", params)
    }

    fun logPaidAdImpressionValue(bundle: Bundle?) {
        logEventTracking("paid_ad_impression_value", bundle)
    }

    fun logClickAdsEvent(bundle: Bundle?) {
        firebaseAnalytics.logEvent("event_user_click_ads", bundle)
    }

    // ------------------------------------------------
    // Purchase
    // ------------------------------------------------
    fun logConfirmPurchaseGoogle(orderId: String?, purchaseId: String?, purchaseToken: String) {
        val tokenPart1: String
        val tokenPart2: String

        if (purchaseToken.length > 100) {
            tokenPart1 = purchaseToken.take(100)
            tokenPart2 = purchaseToken.substring(100)
        } else {
            tokenPart1 = purchaseToken
            tokenPart2 = "EMPTY"
        }

        val bundle = Bundle().apply {
            putString("purchase_order_id", orderId)
            putString("purchase_package_id", purchaseId)
            putString("purchase_token_part_1", tokenPart1)
            putString("purchase_token_part_2", tokenPart2)
        }

        firebaseAnalytics.logEvent("confirm_purchased_with_google", bundle)
        Log.d(TAG, "logConfirmPurchaseGoogle: tracked")
    }

    fun logRevenuePurchase(value: Double) {
        val bundle = Bundle().apply {
            putDouble("value", value)
            putString("currency", "USD")
        }
        firebaseAnalytics.logEvent("user_purchased_value", bundle)
    }

    fun logPurchaseFreeTrail(productId: String?) {
        val bundle = Bundle().apply {
            putString("productId", productId)
        }
        firebaseAnalytics.logEvent("user_purchased_free_trail", bundle)
    }
}