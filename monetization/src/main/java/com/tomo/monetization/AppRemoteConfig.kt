package com.tomo.monetization

import com.tomo.monetization.firstopen.model.FOManager

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AppRemoteConfig {
    private const val TAG = "AppRemoteConfig"

    private val _isRemoteConfigReady = MutableStateFlow(false)
    val isRemoteConfigReady = _isRemoteConfigReady.asStateFlow()

    val config: FirebaseRemoteConfig get() = Firebase.remoteConfig

    fun fetchRemoteConfig() {
        _isRemoteConfigReady.value = false
        val e0 = System.currentTimeMillis() - FOManager.splashStartTime
        Log.d("FOTimeline", "[${e0}ms] fetchRemoteConfig() → START")
        CoroutineScope(Dispatchers.IO).launch {
            setupRemoteConfig()
            val e1 = System.currentTimeMillis() - FOManager.splashStartTime
            Log.d("FOTimeline", "[${e1}ms] ✅ fetchRemoteConfig() → DONE")
            _isRemoteConfigReady.value = true
        }
    }

    private suspend fun setupRemoteConfig() {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Starting fetch")
            Firebase.remoteConfig.apply {
                val configSettings = FirebaseRemoteConfigSettings.Builder()
                    .setFetchTimeoutInSeconds(30)
                    .setMinimumFetchIntervalInSeconds(0)
                    .build()
                setConfigSettingsAsync(configSettings)
                setDefaultsAsync(R.xml.remote_config_defaults)
                fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Remote config activated")
                    } else {
                        Log.d(TAG, "Remote config fetch failed")
                    }

                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }
}