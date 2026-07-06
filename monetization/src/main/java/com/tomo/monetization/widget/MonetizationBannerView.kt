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
    private var pendingAdGroup: BannerAdGroup? = null
    private var pendingWidthDp: Int = 320

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        if (pendingAdGroup != null) {
            startObserving()
        }
    }

    override fun onDetachedFromWindow() {
        observeJob?.cancel()
        scope?.cancel()
        scope = null
        val activity = getActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            pendingAdGroup?.release()
        }
        super.onDetachedFromWindow()
    }

    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    fun setAdGroup(adGroup: BannerAdGroup, widthDp: Int = 320) {
        pendingAdGroup = adGroup
        pendingWidthDp = widthDp
        if (isAttachedToWindow) {
            startObserving()
        }
    }

    private fun startObserving() {
        val adGroup = pendingAdGroup ?: return
        val widthDp = pendingWidthDp
        observeJob?.cancel()
        val currentScope = scope ?: return
        observeJob = currentScope.launch {
            val activity = getActivity(context)
            if (activity == null) {
                android.util.Log.w("MonetizationBannerView", "startObserving: cannot find Activity context for Banner ad group")
                return@launch
            }

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
