package com.tomo.monetization.ads.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.eventFlow
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.tomo.monetization.R
import com.tomo.monetization.ads.app_open.AppOpenResumeManager
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.natives.NativeAdGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
internal fun NativeAdObdFull(
    adGroup: NativeAdGroup,
    modifier: Modifier = Modifier,
    contentDefault: @Composable () -> Unit,
    @LayoutRes layoutRes: Int = R.layout.layout_native_ad_obd_full,
    @LayoutRes shimmerRes: Int = R.layout.layout_native_ad_obd_full_shimmer,
    autoLoadAd: Boolean = true,
    reloadOnClick: Boolean = true,
    showAfterImpression: Boolean = false,
    requestLayoutIndex: Int = 0,
    shimmerWhenLoading: Boolean = false,
    reloadOnLifecycleEvent: Lifecycle.Event? = null,
    onAdFailedToLoad: ((Boolean) -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val activity = LocalActivity.current ?: return

    val adStatus by adGroup.statusFlow.collectAsState()
    val enabled by adGroup.enabledFlow.collectAsState()
    val clicked by adGroup.clickedFlow.collectAsState()
    val isAppOpenAdShowing by AppOpenResumeManager.isAppOpenAdShowingFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var firstTimeInitialized by remember { mutableStateOf(false) }

    if (!firstTimeInitialized) {
        if ((adStatus == AdStatus.Ready || (adStatus == AdStatus.Shown && showAfterImpression)) && nativeAd == null) {
            nativeAd = adGroup.getLoadedAd()
        }
        firstTimeInitialized = true
    }

    LaunchedEffect(adStatus) {
        if (adStatus == AdStatus.Ready || (adStatus == AdStatus.Shown && showAfterImpression)) {
            nativeAd = adGroup.getLoadedAd()
        } else if (adStatus == AdStatus.Loading && shimmerWhenLoading) {
            nativeAd = null
        }
        onAdFailedToLoad?.invoke(!enabled || adStatus == AdStatus.Failure)
    }

    LaunchedEffect(clicked) {
        if (clicked && reloadOnClick) {
            delay(2_000L)
            adGroup.loadAds(activity)
            adGroup.clickedFlow.value = false
        }
    }

    reloadOnLifecycleEvent?.let { event ->
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.eventFlow
                .filter { it == event }
                .drop(1)
                .collectLatest {
                    delay(500L)
                    if (!AppOpenResumeManager.isAppOpenAdShowing) adGroup.loadAds(activity)
                }
        }
    }

    if (!enabled || adStatus == AdStatus.Failure) {
        contentDefault()
    } else {
        nativeAd?.let { ad ->
            AndroidView(
                factory = { ctx ->
                    val layout = FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            if (adGroup.isFullScreen) ViewGroup.LayoutParams.MATCH_PARENT
                            else ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val inflater = LayoutInflater.from(ctx)
                    val adView = inflater.inflate(layoutRes, layout, false) as NativeAdView
                    populateLayoutObdFullNativeAdView(ad, adView, adGroup, onClose)
                    layout.addView(adView)
                    layout
                },
                update = { layout ->
                    (layout.getChildAt(0) as? NativeAdView)?.let { adView ->
                        populateLayoutObdFullNativeAdView(ad, adView, adGroup, onClose)
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
                            if (adGroup.isFullScreen) ViewGroup.LayoutParams.MATCH_PARENT
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
            LaunchedEffect(isAppOpenAdShowing) {
                if (autoLoadAd && !isAppOpenAdShowing) {
                    if (adGroup.status != AdStatus.Shown || !showAfterImpression) {
                        adGroup.loadAds(activity)
                    }
                }
            }
        }
    }
}

private fun populateLayoutObdFullNativeAdView(nativeAd: NativeAd, adView: NativeAdView, adGroup: NativeAdGroup, onClose: (() -> Unit)?) {
    val adMedia = adView.findViewById<MediaView>(R.id.adMedia)
    val adHeadline = adView.findViewById<TextView>(R.id.adHeadline)
    val adCallToAction = adView.findViewById<Button>(R.id.adCallToAction)
    val adBody = adView.findViewById<TextView>(R.id.adBody)
    val adAdvertiser = adView.findViewById<TextView>(R.id.adAdvertiser)
    val adAppIcon = adView.findViewById<ImageView>(R.id.adAppIcon)
    val btnObdClose = adView.findViewById<ImageView>(R.id.btnObdClose)

    if (onClose != null) {
        btnObdClose?.isVisible = true
        btnObdClose?.setOnClickListener { onClose() }
    } else {
        btnObdClose?.isVisible = false
    }

    adView.mediaView = adMedia
    adView.headlineView = adHeadline
    adView.callToActionView = adCallToAction
    adView.bodyView = adBody
    adView.iconView = adAppIcon
    adView.advertiserView = adAdvertiser

    adHeadline.text = nativeAd.headline
    nativeAd.mediaContent?.let {
        adMedia.mediaContent = it
        adMedia.isVisible = true
        it.videoController.let { controller ->
            if (controller.hasVideoContent()) {
                controller.videoLifecycleCallbacks = object : VideoController.VideoLifecycleCallbacks() {
                    override fun onVideoEnd() {
                        adView.context?.let { ctx -> adGroup.loadAds(ctx) }
                    }
                }
            }
        }
    }
    nativeAd.callToAction?.let {
        adCallToAction.isVisible = true
        adCallToAction.text = nativeAd.callToAction
    } ?: run {
        adCallToAction.isVisible = false
    }

    nativeAd.body?.let {
        adBody.isVisible = true
        adBody.text = nativeAd.body
    } ?: run {
        adBody.isInvisible = true
    }

    nativeAd.advertiser?.let {
        adAdvertiser.isVisible = true
        adAdvertiser.text = nativeAd.advertiser
    } ?: run {
        adAdvertiser.isInvisible = true
    }

    nativeAd.icon?.let {
        adAppIcon.setImageDrawable(nativeAd.icon?.drawable)
        adAppIcon.isVisible = true
    } ?: run {
        adAppIcon.isVisible = false
    }

    adView.setNativeAd(nativeAd)
}
