package com.tomo.monetization.util

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gets a lifecycle-aware CoroutineScope from an Activity.
 * Falls back to a supervised scope if Activity doesn't implement LifecycleOwner.
 */
fun Activity.getLifecycleScope(): CoroutineScope {
    return (this as? LifecycleOwner)?.lifecycleScope 
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)
}

/**
 * Launches a coroutine in the Activity's lifecycle scope.
 * The coroutine will be cancelled when the Activity is destroyed.
 */
inline fun Activity.launchWhenResumed(crossinline block: suspend CoroutineScope.() -> Unit) {
    (this as? LifecycleOwner)?.lifecycleScope?.launch {
        block()
    } ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
        block()
    }
}
