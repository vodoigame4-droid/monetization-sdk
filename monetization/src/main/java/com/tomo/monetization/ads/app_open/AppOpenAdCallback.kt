package com.tomo.monetization.ads.app_open

import com.google.android.gms.ads.AdError

interface AppOpenAdCallback {
    fun onNextAction(show: Boolean) {}
    fun onAdClosed() {}
    fun onAdShowed(adId: String, adName: String) {}
    fun onAdFailedToShow(error: AdError?) {}
    fun onAdImpression(adId: String, adName: String) {}
    fun onAdClicked(adId: String, adName: String) {}
}
