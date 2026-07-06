package com.tomo.monetization.ads.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.annotation.LayoutRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.tomo.monetization.R
import com.tomo.monetization.ads.app_open.AppOpenResumeManager
import com.tomo.monetization.ads.banner.BannerAdGroup
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.util.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BannerAdContent(
    adGroup: BannerAdGroup,
    modifier: Modifier = Modifier,
    @LayoutRes shimmerRes: Int = R.layout.layout_banner_ad_shimmer,
    autoDestroy: Boolean = true
) {
    val activity = LocalActivity.current ?: return

    var loadedAd by remember { mutableStateOf<AdView?>(null) }
    var lifecycleState by remember { mutableStateOf(Lifecycle.State.INITIALIZED) }
    val adStatus by adGroup.statusFlow.collectAsState()
    val enabled by adGroup.enabledFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val transitionSpec: AnimatedContentTransitionScope<AdView?>.() -> ContentTransform = {
        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
    }

    BoxWithConstraints(modifier = modifier) {
        val containerWidthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(containerWidthDp, activity) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                activity, containerWidthDp
            )
        }
        val expectedHeightPx = remember(adSize) {
            adSize.getHeightInPixels(activity)
        }

        DisposableEffect(adGroup) {
            onDispose {
                if (autoDestroy) {
                    adGroup.releaseAll()
                    loadedAd = null
                }
            }
        }

        OnLifecycleEvent { _, event ->
            lifecycleState = event.targetState
            if (event == Lifecycle.Event.ON_STOP && autoDestroy) {
                adGroup.releaseAll()
                loadedAd = null
            }
        }

        LaunchedEffect(adGroup, containerWidthDp) {
            snapshotFlow { Pair(adStatus, lifecycleState) }
                .collect { (status, state) ->
                    if (state.isAtLeast(Lifecycle.State.RESUMED)) {
                        delay(500)
                        val currentLoaded = adGroup.getLoadedAd()
                        loadedAd = currentLoaded
                        if (currentLoaded == null &&
                            status != AdStatus.Failure &&
                            status != AdStatus.Loading &&
                            enabled &&
                            !AppOpenResumeManager.isAppOpenAdShowing
                        ) {
                            adGroup.loadAds(activity, containerWidthDp)
                        }
                    }
                }
        }

        if (!enabled || adStatus == AdStatus.Failure) return@BoxWithConstraints

        AnimatedContent(
            targetState = loadedAd,
            transitionSpec = transitionSpec,
            modifier = Modifier.fillMaxWidth(),
            label = "BannerAdAnimation"
        ) { currentAd ->
            currentAd?.let { ad ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.7.dp)
                            .background(Color.LightGray)
                    )
                    AdViewContainer(ad, expectedHeightPx, scope)
                }
            } ?: run {
                AndroidView(
                    factory = { ctx ->
                        val layout = FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                expectedHeightPx
                            )
                        }
                        val inflater = LayoutInflater.from(ctx)
                        val shimmer = inflater.inflate(shimmerRes, layout, false)
                        layout.addView(shimmer)
                        layout
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F0F0))
                )
            }
        }
    }
}

@Composable
fun AdViewContainer(
    ad: AdView,
    expectedHeightPx: Int,
    scope: CoroutineScope
) {
    val jobRef = remember { arrayOf<Job?>(null) }
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    expectedHeightPx
                )
                (ad.parent as? ViewGroup)?.removeView(ad)
                addView(ad)
            }
        },
        update = { _ ->
            jobRef[0]?.cancel()
            jobRef[0] = scope.launch {
                delay(200)
                ad.rootView?.requestLayout()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F0F0))
    )
}
