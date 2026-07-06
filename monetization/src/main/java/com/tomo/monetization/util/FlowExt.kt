package com.tomo.monetization.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun <T> LifecycleOwner.collectLatestRepeatOnLifecycle(
    flow: Flow<T>,
    lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend CoroutineScope.(T) -> Unit
): Job {
    return lifecycleScope.launch(Dispatchers.Main) {
        repeatOnLifecycle(lifecycleState) {
            flow.collectLatest {
                action.invoke(this, it)
            }
        }
    }
}

fun <T> LifecycleOwner.collectRepeatOnLifecycle(
    flow: Flow<T>,
    lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend CoroutineScope.(T) -> Unit
): Job {
    return lifecycleScope.launch(Dispatchers.Main) {
        repeatOnLifecycle(lifecycleState) {
            flow.collect {
                action.invoke(this, it)
            }
        }
    }
}
