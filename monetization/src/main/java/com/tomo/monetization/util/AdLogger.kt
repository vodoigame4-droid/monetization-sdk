package com.tomo.monetization.util

import android.util.Log

object AdLogger {
    private const val TAG = "MonetizationSDK"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    fun logLoadStart(adType: String, adName: String, adId: String) {
        Log.i(TAG, "⚡ [LOAD START] ➡️ Type: $adType | Name: $adName | ID: $adId")
    }

    fun logLoaded(adType: String, adName: String, adId: String) {
        Log.i(TAG, "✅ [LOAD SUCCESS] ➡️ Type: $adType | Name: $adName | ID: $adId")
    }

    fun logLoadFailed(adType: String, adName: String, adId: String, error: String) {
        Log.e(TAG, "❌ [LOAD FAILED] ➡️ Type: $adType | Name: $adName | ID: $adId | Reason: $error")
    }

    fun logImpression(adType: String, adName: String, adId: String) {
        Log.i(TAG, "👀 [IMPRESSION] ➡️ Type: $adType | Name: $adName | ID: $adId")
    }

    fun logShowed(adType: String, adName: String, adId: String) {
        Log.i(TAG, "🎬 [SHOW SUCCESS] ➡️ Type: $adType | Name: $adName | ID: $adId")
    }

    fun logShowFailed(adType: String, adName: String, adId: String, error: String) {
        Log.e(TAG, "⚠️ [SHOW FAILED] ➡️ Type: $adType | Name: $adName | ID: $adId | Reason: $error")
    }
}
