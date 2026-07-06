package com.tomo.monetization.ads.rewarded

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.rewarded.RewardItem

interface RewardedAdCallback {
    fun onUserEarnedReward(rewardItem: RewardItem) {}
    fun onNextAction(show: Boolean) {}
    fun onAdShowed(adId: String, adName: String) {}
    fun onAdFailedToShow(error: AdError?) {}
    fun onAdImpression(adId: String, adName: String) {}
    fun onAdClicked(adId: String, adName: String) {}
}