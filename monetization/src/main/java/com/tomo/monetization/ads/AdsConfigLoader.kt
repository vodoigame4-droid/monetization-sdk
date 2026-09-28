package com.tomo.monetization.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.tomo.monetization.Monetization
import com.tomo.monetization.AdType
import com.tomo.monetization.ads.app_open.AppOpenAdGroup
import com.tomo.monetization.ads.app_open.AppOpenAdUnit
import com.tomo.monetization.ads.app_open.AppOpenResumeManager
import com.tomo.monetization.ads.banner.BannerAdGroup
import com.tomo.monetization.ads.banner.BannerAdUnit
import com.tomo.monetization.ads.interstitial.InterstitialAdGroup
import com.tomo.monetization.ads.interstitial.InterstitialAdUnit
import com.tomo.monetization.ads.natives.NativeAdGroup
import com.tomo.monetization.ads.natives.NativeAdUnit
import com.tomo.monetization.ads.rewarded.RewardedAdGroup
import com.tomo.monetization.ads.rewarded.RewardedAdUnit
import org.json.JSONObject
import kotlin.reflect.KClass

object AdsConfigLoader {
    private const val TAG = "AdsConfigLoader"

    /**
     * Parses the JSON configuration string (typically retrieved from Firebase Remote Config)
     * and dynamically initializes all ad groups with customized AdMob keys.
     * Supports multiple instances for each ad type (e.g. multiple banners, natives, etc.)
     * as well as nested advanced parameters (collapsible banners, video reload flags, individual unit statuses).
     */
    fun loadConfigFromJson(
        context: Context, 
        jsonString: String,
        resumeActivities: List<KClass<out Activity>> = emptyList()
    ) {
        try {
            val root = JSONObject(jsonString)

            // 1. Configure global ads enable & distance times
            val adsEnable = root.optBoolean("ads_enable", true)
            val interDistance = root.optInt("interstitial_distance_time", 15)
            val appOpenDistance = root.optInt("app_open_distance_time", 30)

            Monetization.updateAdsEnable(adsEnable, interDistance)
            Monetization.updateDistanceTime(AdType.INTERSTITIAL, interDistance)
            Monetization.updateDistanceTime(AdType.APP_OPEN, appOpenDistance)

            val adGroupsJson = root.optJSONObject("ad_groups") ?: return
            val keys = adGroupsJson.keys()

            // 2. Iterate dynamically over all configured ad keys
            while (keys.hasNext()) {
                val key = keys.next()
                val groupJson = adGroupsJson.optJSONObject(key) ?: continue
                val type = groupJson.optString("type", "").uppercase()
                val groupEnabled = groupJson.optBoolean("enabled", true)
                val adUnitsArray = groupJson.optJSONArray("ad_units")

                when (type) {
                    "BANNER" -> {
                        val adUnitsList = mutableListOf<BannerAdUnit>()
                        val unitEnabledList = mutableListOf<Boolean>()
                        if (adUnitsArray != null) {
                            for (i in 0 until adUnitsArray.length()) {
                                val unitObj = adUnitsArray.optJSONObject(i) ?: continue
                                val id = unitObj.optString("id")
                                val name = unitObj.optString("name")
                                val unitEnabled = unitObj.optBoolean("enabled", groupEnabled)
                                val isCollapsible = unitObj.optBoolean("is_collapsible", false)
                                if (id.isNotBlank()) {
                                    adUnitsList.add(BannerAdUnit(id, name, isCollapsible))
                                    unitEnabledList.add(unitEnabled)
                                }
                            }
                        }
                        if (adUnitsList.isNotEmpty()) {
                            val group = BannerAdGroup(adUnitsList, key)
                            group.config(*unitEnabledList.toBooleanArray())
                            AdsProvider.banners[key] = group
                            Log.d(TAG, "Configured BANNER group: $key with ${adUnitsList.size} units")
                        }
                    }

                    "NATIVE" -> {
                        val adUnitsList = mutableListOf<NativeAdUnit>()
                        val unitEnabledList = mutableListOf<Boolean>()
                        if (adUnitsArray != null) {
                            for (i in 0 until adUnitsArray.length()) {
                                val unitObj = adUnitsArray.optJSONObject(i) ?: continue
                                val id = unitObj.optString("id")
                                val name = unitObj.optString("name")
                                val unitEnabled = unitObj.optBoolean("enabled", groupEnabled)
                                if (id.isNotBlank()) {
                                    adUnitsList.add(NativeAdUnit(id, name))
                                    unitEnabledList.add(unitEnabled)
                                }
                            }
                        }
                        if (adUnitsList.isNotEmpty()) {
                            val layoutType = groupJson.optString("layout_type", "DEFAULT")
                            val isFullScreen = layoutType.uppercase() == "OBD_FULL" || groupJson.optBoolean("is_fullscreen", false)
                            val reloadAfterVideo = groupJson.optBoolean("reload_after_video", true)
                            val group = NativeAdGroup(adUnitsList, key, isFullScreen = isFullScreen, reloadAfterVideo = reloadAfterVideo)
                            group.config(*unitEnabledList.toBooleanArray())
                            AdsProvider.natives[key] = group
                            Log.d(TAG, "Configured NATIVE group: $key with ${adUnitsList.size} units")
                        }
                    }

                    "INTERSTITIAL" -> {
                        val adUnitsList = mutableListOf<InterstitialAdUnit>()
                        val unitEnabledList = mutableListOf<Boolean>()
                        if (adUnitsArray != null) {
                            for (i in 0 until adUnitsArray.length()) {
                                val unitObj = adUnitsArray.optJSONObject(i) ?: continue
                                val id = unitObj.optString("id")
                                val name = unitObj.optString("name")
                                val unitEnabled = unitObj.optBoolean("enabled", groupEnabled)
                                if (id.isNotBlank()) {
                                    adUnitsList.add(InterstitialAdUnit(id, name))
                                    unitEnabledList.add(unitEnabled)
                                }
                            }
                        }
                        if (adUnitsList.isNotEmpty()) {
                            val group = InterstitialAdGroup(adUnitsList, key)
                            group.config(*unitEnabledList.toBooleanArray())
                            AdsProvider.interstitials[key] = group
                            Log.d(TAG, "Configured INTERSTITIAL group: $key with ${adUnitsList.size} units")
                        }
                    }

                    "REWARDED" -> {
                        val adUnitsList = mutableListOf<RewardedAdUnit>()
                        val unitEnabledList = mutableListOf<Boolean>()
                        if (adUnitsArray != null) {
                            for (i in 0 until adUnitsArray.length()) {
                                val unitObj = adUnitsArray.optJSONObject(i) ?: continue
                                val id = unitObj.optString("id")
                                val name = unitObj.optString("name")
                                val unitEnabled = unitObj.optBoolean("enabled", groupEnabled)
                                if (id.isNotBlank()) {
                                    adUnitsList.add(RewardedAdUnit(id, name))
                                    unitEnabledList.add(unitEnabled)
                                }
                            }
                        }
                        if (adUnitsList.isNotEmpty()) {
                            val group = RewardedAdGroup(adUnitsList, key)
                            group.config(*unitEnabledList.toBooleanArray())
                            AdsProvider.rewardeds[key] = group
                            Log.d(TAG, "Configured REWARDED group: $key with ${adUnitsList.size} units")
                        }
                    }

                    "APP_OPEN" -> {
                        val adUnitsList = mutableListOf<AppOpenAdUnit>()
                        val unitEnabledList = mutableListOf<Boolean>()
                        var firstAdId = ""
                        if (adUnitsArray != null) {
                            for (i in 0 until adUnitsArray.length()) {
                                val unitObj = adUnitsArray.optJSONObject(i) ?: continue
                                val id = unitObj.optString("id")
                                val name = unitObj.optString("name")
                                val unitEnabled = unitObj.optBoolean("enabled", groupEnabled)
                                if (id.isNotBlank()) {
                                    if (firstAdId.isEmpty()) firstAdId = id
                                    adUnitsList.add(AppOpenAdUnit(id, name))
                                    unitEnabledList.add(unitEnabled)
                                }
                            }
                        }
                        if (adUnitsList.isNotEmpty()) {
                            val group = AppOpenAdGroup(adUnitsList, key)
                            group.config(*unitEnabledList.toBooleanArray())
                            AdsProvider.appOpens[key] = group
                            Log.d(TAG, "Configured APP_OPEN group: $key with ${adUnitsList.size} units")

                            // Automatically setup App Open Resume Manager with the parsed adGroup (supporting Normal, 2F, and MF)
                            AppOpenResumeManager.setUpAppOpenResume(
                                adGroup = group,
                                activities = resumeActivities
                            )
                        }
                    }
                }
            }

            Log.i(TAG, "All Ads groups dynamically configured successfully from JSON!")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ads config JSON: ${e.message}", e)
        }
    }
}
