package com.tomo.monetization.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.tomo.monetization.ads.banner.BannerAdGroup
import com.tomo.monetization.ads.base.AdStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MonetizationBannerView @JvmOverloads constructor(
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

    fun setAdGroup(adGroup: BannerAdGroup, widthDp: Int = 320) {
        observeJob?.cancel()
        val currentScope = scope ?: return
        observeJob = currentScope.launch {
            val activity = context as? Activity ?: return@launch

            combine(adGroup.statusFlow, adGroup.enabledFlow) { status, enabled ->
                if (adGroup.getLoadedAd() == null &&
                    status != AdStatus.Failure &&
                    status != AdStatus.Loading &&
                    enabled
                ) {
                    adGroup.loadAds(activity, widthDp)
                }
                status
            }.collectLatest { status ->
                if (status == AdStatus.Ready) {
                    val adView = adGroup.getLoadedAd()
                    if (adView != null) {
                        removeAllViews()
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        addView(adView)
                    }
                } else if (status == AdStatus.Failure) {
                    removeAllViews()
                }
            }
        }
    }
}
