package com.tomo.monetization.ads.interstitial

import com.google.android.gms.ads.AdError

interface InterstitialAdCallback {
    fun onNextAction(show: Boolean) {}
    fun onAdShowed(adId: String, adName: String) {}
    fun onAdFailedToShow(error: AdError?) {}
    fun onAdImpression(adId: String, adName: String) {}
    fun onAdClicked(adId: String, adName: String) {}
}