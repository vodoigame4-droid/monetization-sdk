package com.tomo.monetization.consent

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.tomo.monetization.util.EventTracking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object ConsentManager {
    private const val TAG = "ConsentManager"

    private var consentInformation: ConsentInformation? = null
    private val requestInFlight = AtomicBoolean(false)
    private val pendingCallbacks = CopyOnWriteArrayList<(Boolean) -> Unit>()

    val canRequestAds: Boolean
        get() = safeCanRequestAds()

    var hasShowConsentForm: Boolean = false
        private set

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun init(context: Context) {
        runCatching {
            consentInformation = UserMessagingPlatform.getConsentInformation(context.applicationContext)
        }.onFailure { throwable ->
            logSdkException("getConsentInformation", throwable)
        }
    }

    fun requestConsent(
        activity: Activity,
        enableDebug: Boolean,
        testDevice: String,
        resetData: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        pendingCallbacks += onResult
        ensureInitialized(activity)

        val consentInfo = consentInformation
        if (consentInfo == null) {
            resolvePendingCallbacks(false)
            return
        }

        if (requestInFlight.getAndSet(true)) {
            Log.d(TAG, "Consent request already in flight, callback queued.")
            return
        }

        hasShowConsentForm = false
        val paramsBuilder = ConsentRequestParameters.Builder()

        if (enableDebug) {
            runCatching {
                val debugBuilder = ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                if (testDevice.isNotBlank()) {
                    debugBuilder.addTestDeviceHashedId(testDevice)
                }
                paramsBuilder.setConsentDebugSettings(debugBuilder.build())
            }.onFailure { throwable ->
                logSdkException("buildDebugSettings", throwable)
            }
        }

        val params = runCatching { paramsBuilder.build() }
            .onFailure { throwable -> logSdkException("buildConsentParams", throwable) }
            .getOrNull()

        if (params == null) {
            resolvePendingCallbacks(safeCanRequestAds())
            return
        }

        if (resetData) {
            runCatching { consentInfo.reset() }
                .onFailure { throwable -> logSdkException("resetConsent", throwable) }
        }

        try {
            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    onConsentInfoUpdated(activity)
                },
                { error ->
                    logRequestError(error)
                    resolvePendingCallbacks(safeCanRequestAds())
                }
            )
        } catch (throwable: Throwable) {
            logSdkException("requestConsentInfoUpdate", throwable)
            resolvePendingCallbacks(safeCanRequestAds())
        }

        if (safeCanRequestAds()) {
            resolvePendingCallbacks(true)
        }
    }

    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissedListener: OnConsentFormDismissedListener,
    ) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener)
        } catch (throwable: Throwable) {
            logSdkException("showPrivacyOptionsForm", throwable)
            onConsentFormDismissedListener.onConsentFormDismissed(null)
        }
    }

    val consentResult: Boolean
        get() = safeCanRequestAds()

    private fun onConsentInfoUpdated(activity: Activity) {
        if (isPrivacyOptionsRequired && !safeCanRequestAds()) {
            EventTracking.logScreen("ump_consent_form")
            hasShowConsentForm = true
        }

        try {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null) {
                    logFormError(error)
                }
                if (hasShowConsentForm) {
                    EventTracking.logScreen("splash_scr")
                }
                logConsentResult()
                resolvePendingCallbacks(safeCanRequestAds())
            }
        } catch (throwable: Throwable) {
            logSdkException("loadAndShowConsentFormIfRequired", throwable)
            resolvePendingCallbacks(safeCanRequestAds())
        }
    }

    private fun ensureInitialized(context: Context) {
        if (consentInformation != null) return
        init(context)
    }

    private fun resolvePendingCallbacks(result: Boolean) {
        requestInFlight.set(false)
        if (pendingCallbacks.isEmpty()) return

        val callbacks = pendingCallbacks.toList()
        pendingCallbacks.clear()
        callbacks.forEach { callback ->
            runCatching { callback(result) }
                .onFailure { throwable -> Log.e(TAG, "Consent callback crashed", throwable) }
        }
    }

    private fun safeCanRequestAds(): Boolean {
        return runCatching { consentInformation?.canRequestAds() == true }
            .onFailure { throwable -> logSdkException("canRequestAds", throwable) }
            .getOrDefault(false)
    }

    private fun logRequestError(error: FormError) {
        Log.e(TAG, "requestUMP: ${error.errorCode} ${error.message}")
        EventTracking.logEvent("ump_request_failed") {
            param("error_code", error.errorCode.toLong())
            param("error_msg", error.message ?: "")
        }
    }

    private fun logFormError(error: FormError) {
        Log.e(TAG, "loadForm: ${error.errorCode} ${error.message}")
        EventTracking.logEvent("ump_consent_failed") {
            param("error_code", error.errorCode.toLong())
            param("error_msg", error.message ?: "")
        }
    }

    private fun logConsentResult() {
        EventTracking.logEvent("ump_consent_result") {
            param("consent", consentResult.toString())
        }
    }

    private fun logSdkException(operation: String, throwable: Throwable) {
        Log.e(TAG, "UMP operation failed: $operation", throwable)
        EventTracking.logEvent("ump_sdk_exception") {
            param("operation", operation)
            param("error_type", throwable.javaClass.simpleName)
            param("error_msg", throwable.message ?: "")
        }
    }
}
