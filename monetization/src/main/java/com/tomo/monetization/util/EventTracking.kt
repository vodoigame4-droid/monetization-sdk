package com.tomo.monetization.util

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ParametersBuilder
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

object EventTracking {
    private const val TAG = "EventTracking"

    fun logScreen(screenName: String) {
        Log.d(TAG, "logScreen: $screenName")
        try {
            Firebase.analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            }
        } catch (_: Exception) {
        }
    }

    fun logEvent(name: String) {
        Log.d(TAG, "logEvent: $name")
        try {
            Firebase.analytics.logEvent(name, null)
        } catch (_: Exception) {
        }
    }

    fun logEvent(name: String, block: ParametersBuilder.() -> Unit) {
        val builder = ParametersBuilder()
        builder.block()
        Log.d(TAG, "logEvent: $name with ${builder.bundle}")
        try {
            Firebase.analytics.logEvent(name, builder.bundle)
        } catch (_: Exception) {
        }
    }
}