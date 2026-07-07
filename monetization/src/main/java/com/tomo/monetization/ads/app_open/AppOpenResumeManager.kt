package com.tomo.monetization.ads.app_open

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.tomo.monetization.Monetization
import com.tomo.monetization.AdType
import com.tomo.monetization.util.launchWhenResumed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

object AppOpenResumeManager : Application.ActivityLifecycleCallbacks, LifecycleEventObserver {
    private const val TAG = "AppOpenManager"
    
    private val _isAppOpenAdShowing = MutableStateFlow(false)
    val isAppOpenAdShowingFlow = _isAppOpenAdShowing.asStateFlow()

    var isAppOpenAdShowing: Boolean
        get() = _isAppOpenAdShowing.value
        set(value) {
            _isAppOpenAdShowing.value = value
        }

    private var adGroup: AppOpenAdGroup? = null
    private var disableAppOpenResumeOnce = false
    private var enableAppOpenResume = true
    private var showAdCallback: FullScreenContentCallback? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var loadAdJob: Job? = null
    private var showAdActivities: List<KClass<out Activity>> = emptyList()

    private val currentActivity: Activity?
        get() = currentActivityRef?.get()

    fun init(context: Context) {
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setUpAppOpenResume(
        adId: String,
        name: String,
        enabled: Boolean = true,
        vararg activities: KClass<out Activity>
    ) {
        adGroup = AppOpenAdGroup(
            adUnits = listOf(AppOpenAdUnit(adId, name)),
            name = name,
        ).apply {
            config(enabled)
        }

        showAdActivities = activities.toList()
    }

    fun enableAppOpenResume() {
        enableAppOpenResume = true
        disableAppOpenResumeOnce = false
    }

    fun disableAppOpenResume() {
        enableAppOpenResume = false
        disableAppOpenResumeOnce = false
    }

    fun disableAppOpenResumeOnce() {
        Log.d(TAG, "disableAppOpenResumeOnce")
        disableAppOpenResumeOnce = true
    }

    fun setShowAdCallback(callback: FullScreenContentCallback) {
        showAdCallback = callback
    }

    fun loadAds(context: Context) {
        adGroup?.loadAds(context)
    }

    fun showAds(activity: Activity) {
        if (!activity.hasWindowFocus()) {
            isAppOpenAdShowing = false
            return
        }

        Monetization.calDistanceTime(
            type = AdType.APP_OPEN,
            onDismiss = {
                isAppOpenAdShowing = false
                Log.d(TAG, "showAds: group open ad skip distance time")
            }, showAds = {
                adGroup?.showAds(
                activity = activity,
                callback = object : AppOpenAdCallback {
                    override fun onAdClosed() {
                        showAdCallback?.onAdDismissedFullScreenContent()
                    }

                    override fun onNextAction(show: Boolean) {
                        if (!show) isAppOpenAdShowing = false
                    }

                    override fun onAdShowed(adId: String, adName: String) {
                        showAdCallback?.onAdShowedFullScreenContent()
                    }

                    override fun onAdFailedToShow(error: AdError?) {
                        error?.let { showAdCallback?.onAdFailedToShowFullScreenContent(it) }
                    }

                    override fun onAdImpression(adId: String, adName: String) {
                        showAdCallback?.onAdImpression()
                        isAppOpenAdShowing = false
                    }

                    override fun onAdClicked(adId: String, adName: String) {
                        showAdCallback?.onAdClicked()
                    }
                }
            ) ?: run {
                isAppOpenAdShowing = false
            }
        })
    }

    // --------------------------------------------------------------------
    // Activity lifecycle
    // --------------------------------------------------------------------
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d(TAG, "onActivityCreated: ${activity::class.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d(TAG, "onActivityStarted: ${activity::class.simpleName}")
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d(TAG, "onActivityResumed: ${activity::class.simpleName}")
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        Log.d(TAG, "onActivityPaused: ${activity::class.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d(TAG, "onActivityStopped: ${activity::class.simpleName}")
        // no longer pre-loading ads here
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d(TAG, "onActivitySaveInstanceState: ${activity::class.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "onActivityDestroyed: ${activity::class.simpleName}")
        if (currentActivity == activity) {
            currentActivityRef = null
            loadAdJob?.cancel()
        }
    }

    // --------------------------------------------------------------------
    // Process lifecycle
    // --------------------------------------------------------------------
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Log.d(TAG, "onStateChanged: $event")

        if (event != Lifecycle.Event.ON_START) return
        val activity = currentActivity ?: return

        if ((adGroup == null || adGroup?.enabled == true) && enableAppOpenResume) {
            // Fix: Clarify operator precedence with explicit parentheses
            val allow = (showAdActivities.isEmpty() && activity::class.simpleName == "MainActivity") ||
                    showAdActivities.contains(activity::class)

            if (!allow) return

            if (!disableAppOpenResumeOnce) {
                Log.d(TAG, "AppOpenResume prepare load and show")
                isAppOpenAdShowing = true
                loadAdJob?.cancel()
                loadAdJob = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                    loadAds(activity)
                    adGroup?.statusFlow?.first { it == com.tomo.monetization.ads.base.AdStatus.Ready || it == com.tomo.monetization.ads.base.AdStatus.Failure }?.let { status ->
                        if (status == com.tomo.monetization.ads.base.AdStatus.Ready) {
                            showAds(activity)
                        } else {
                            isAppOpenAdShowing = false
                        }
                    } ?: run {
                        isAppOpenAdShowing = false
                    }
                }
            } else {
                Log.d(TAG, "AppOpenResume disabled once")
                disableAppOpenResumeOnce = false
            }
        }
    }
}
