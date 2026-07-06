package com.tomo.monetization.ads.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.annotation.LayoutRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.tomo.monetization.R
import com.tomo.monetization.ads.natives.NativeAdPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LazyGridState.isItemVisible(index: Int): State<Boolean> {
    return remember(this) {
        derivedStateOf { layoutInfo.visibleItemsInfo.any { it.index == index } }
    }
}

@Composable
fun LazyListState.isItemVisible(index: Int): State<Boolean> {
    return remember(this) {
        derivedStateOf { layoutInfo.visibleItemsInfo.any { it.index == index } }
    }
}

/**
 * @param adPool The shared NativeAdPool to get ads from
 * @param isVisible Whether this item is currently visible (use extension functions like LazyGridState.isItemVisible or LazyListState.isItemVisible)
 * @param modifier Modifier for the composable
 * @param layoutRes Layout resource for the native ad view
 * @param shimmerRes Layout resource for the shimmer loading view
 * @param isFullScreen Whether the ad should use MATCH_PARENT for height
 * @param visibilityThresholdMs Debounce: wait for item to be visible this long before loading ad
 * @param retryDelayMs Delay before retrying to get ad from pool (ms)
 * @param reloadOnClick Whether to reload ad when clicked
 * @param onAdLoaded Callback when ad is successfully loaded and displayed
 */
@Composable
fun NativeAdPoolContent(
    adPool: NativeAdPool,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    @LayoutRes layoutRes: Int = R.layout.layout_native_ad_default,
    @LayoutRes shimmerRes: Int = R.layout.layout_native_ad_default_shimmer,
    isFullScreen: Boolean = false,
    visibilityThresholdMs: Long = 500L,
    retryDelayMs: Long = 300L,
    requestLayoutIndex: Int = 0,
    reloadOnClick: Boolean = true,
    onAdLoaded: (() -> Unit)? = null,
) {
    val activity = LocalActivity.current ?: return

    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val clicked by adPool.clickedFlow.collectAsState()
    val scope = rememberCoroutineScope()

    // Only get ad when item has been visible for threshold duration (debounce for fast scroll)
    LaunchedEffect(isVisible) {
        if (isVisible && nativeAd == null) {
            // Wait for visibility threshold before loading ad
            delay(visibilityThresholdMs)
            
            // Keep trying until we get an ad
            while (nativeAd == null) {
                val ad = adPool.getAd(activity)
                if (ad != null) {
                    nativeAd = ad
                    onAdLoaded?.invoke()
                    return@LaunchedEffect
                }
                
                // Wait and retry - pool will auto-refill
                delay(retryDelayMs)
            }
        }
    }

    // Handle click reload - get next ad from pool
    LaunchedEffect(clicked) {
        if (clicked && reloadOnClick) {
            delay(2_000L)
            // Destroy old ad
            nativeAd?.destroy()
            nativeAd = null
            // Get next ad from pool
            while (nativeAd == null) {
                val ad = adPool.getAd(activity)
                if (ad != null) {
                    nativeAd = ad
                    onAdLoaded?.invoke()
                    break
                }
                delay(retryDelayMs)
            }
            adPool.clickedFlow.value = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    nativeAd?.let { ad ->
        AndroidView(
            factory = { ctx ->
                val layout = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        if (isFullScreen) ViewGroup.LayoutParams.MATCH_PARENT
                        else ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val inflater = LayoutInflater.from(ctx)
                val adView = inflater.inflate(layoutRes, layout, false) as NativeAdView
                populateNativeAdView(ad, adView)
                layout.addView(adView)
                layout
            },
            update = { layout ->
                (layout.getChildAt(0) as? NativeAdView)?.let { adView ->
                    populateNativeAdView(ad, adView)
                }

                if (requestLayoutIndex >= 0) {
                    scope.launch {
                        delay(500L)
                        layout.rootView.requestLayout()
                    }
                }
            },
            modifier = modifier
        )
    } ?: run {
        AndroidView(
            factory = { ctx ->
                val layout = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        if (isFullScreen) ViewGroup.LayoutParams.MATCH_PARENT
                        else ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val inflater = LayoutInflater.from(ctx)
                val shimmer = inflater.inflate(shimmerRes, layout, false)
                layout.addView(shimmer)
                layout
            },
            modifier = modifier
        )
    }
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    val adMedia = adView.findViewById<MediaView?>(R.id.adMedia)
    val adHeadline = adView.findViewById<TextView?>(R.id.adHeadline)
    val adCallToAction = adView.findViewById<Button?>(R.id.adCallToAction)
    val adBody = adView.findViewById<TextView?>(R.id.adBody)
    val adAdvertiser = adView.findViewById<TextView?>(R.id.adAdvertiser)
    val adAppIcon = adView.findViewById<ImageView?>(R.id.adAppIcon)

    adView.mediaView = adMedia
    adView.headlineView = adHeadline
    adView.callToActionView = adCallToAction
    adView.iconView = adAppIcon

    adHeadline?.text = nativeAd.headline

    adMedia?.let {
        nativeAd.mediaContent?.let { content ->
            adMedia.mediaContent = content
            adMedia.isVisible = true
        }
    }

    adCallToAction?.let {
        nativeAd.callToAction?.let { cta ->
            adCallToAction.isVisible = true
            adCallToAction.text = cta
        } ?: run {
            adCallToAction.isVisible = false
        }
    }

    adAppIcon?.let {
        nativeAd.icon?.let { icon ->
            adAppIcon.setImageDrawable(icon.drawable)
            adAppIcon.isVisible = true
        } ?: run {
            adAppIcon.isVisible = false
        }
    }

    adBody?.let {
        nativeAd.body?.let { body ->
            adBody.isVisible = true
            adBody.text = body
        } ?: run {
            adBody.isInvisible = true
        }
    }

    adAdvertiser?.let {
        nativeAd.advertiser?.let { advertiser ->
            adAdvertiser.isVisible = true
            adAdvertiser.text = advertiser
        } ?: run {
            adAdvertiser.isInvisible = true
        }
    }

    adView.setNativeAd(nativeAd)
}
