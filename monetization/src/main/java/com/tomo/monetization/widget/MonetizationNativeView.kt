package com.tomo.monetization.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.tomo.monetization.R
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.natives.NativeAdGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MonetizationNativeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scope: CoroutineScope? = null
    private var observeJob: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDetachedFromWindow() {
        observeJob?.cancel()
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    fun setAdGroup(
        adGroup: NativeAdGroup,
        @LayoutRes layoutRes: Int = R.layout.layout_native_ad_default,
        @LayoutRes shimmerRes: Int = R.layout.layout_native_ad_default_shimmer
    ) {
        observeJob?.cancel()
        val currentScope = scope ?: return

        val inflater = LayoutInflater.from(context)
        removeAllViews()
        inflater.inflate(shimmerRes, this, true)

        observeJob = currentScope.launch {
            val activity = context as? Activity ?: return@launch

            combine(adGroup.statusFlow, adGroup.enabledFlow) { status, enabled ->
                if (adGroup.getLoadedAd() == null &&
                    status != AdStatus.Failure &&
                    status != AdStatus.Loading &&
                    enabled
                ) {
                    adGroup.loadAds(activity)
                }
                status
            }.collectLatest { status ->
                if (status == AdStatus.Ready) {
                    val nativeAd = adGroup.getLoadedAd()
                    if (nativeAd != null) {
                        removeAllViews()
                        val adView = inflater.inflate(layoutRes, this@MonetizationNativeView, false) as NativeAdView
                        populateNativeAdView(nativeAd, adView)
                        addView(adView)
                    }
                } else if (status == AdStatus.Failure) {
                    removeAllViews()
                }
            }
        }
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.adHeadline)
        adView.bodyView = adView.findViewById(R.id.adBody)
        adView.callToActionView = adView.findViewById(R.id.adCallToAction)
        adView.mediaView = adView.findViewById(R.id.adMedia)
        adView.iconView = adView.findViewById(R.id.adAppIcon)

        (adView.headlineView as? TextView)?.text = nativeAd.headline

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            if (adView.callToActionView is Button) {
                (adView.callToActionView as Button).text = nativeAd.callToAction
            } else if (adView.callToActionView is TextView) {
                (adView.callToActionView as TextView).text = nativeAd.callToAction
            }
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            adView.iconView?.visibility = View.VISIBLE
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
        }

        adView.setNativeAd(nativeAd)
    }
}
