package com.tomo.monetization.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isNotEmpty
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tomo.monetization.R
import com.tomo.monetization.ads.banner.BannerAdGroup
import com.tomo.monetization.ads.natives.NativeAdGroup
import com.tomo.monetization.ads.ui.BannerAdContent
import com.tomo.monetization.ads.ui.NativeAdContent

fun AppCompatActivity.showBannerAd(
    adGroup: BannerAdGroup,
    frameLayout: FrameLayout,
) {
    try {
        if (frameLayout.isNotEmpty()) {
            frameLayout.removeAllViews()
        }
        val composeView = ComposeView(this).apply {
            setContent {
                BannerAdContent(
                    adGroup = adGroup,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        frameLayout.addView(composeView)
    } catch (e: Exception) {
        e.printStackTrace()
        frameLayout.visibility = android.view.View.GONE
    }
}

fun AppCompatActivity.showNativeAd(
    adGroup: NativeAdGroup,
    frameLayout: FrameLayout,
    @LayoutRes layoutRes: Int?,
    @LayoutRes shimmerRes: Int?,
    onAdFailedToLoad: ((Boolean) -> Unit)? = null,
) {
    try {
        if (frameLayout.isNotEmpty()) {
            frameLayout.removeAllViews()
        }
        val composeView = ComposeView(this).apply {
            setContent {
                NativeAdContent(
                    adGroup = adGroup,
                    modifier = Modifier.fillMaxWidth(),
                    layoutRes ?: R.layout.layout_native_ad_default,
                    shimmerRes ?: R.layout.layout_native_ad_default_shimmer,
                    onAdFailedToLoad = onAdFailedToLoad
                )
            }
        }
        frameLayout.addView(composeView)
    } catch (e: Exception) {
        e.printStackTrace()
        frameLayout.visibility = android.view.View.GONE
    }
}

@Composable
internal fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}
