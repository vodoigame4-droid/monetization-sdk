package com.tomo.monetization.firstopen.model

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tomo.monetization.ads.banner.BannerAdGroup
import com.tomo.monetization.ads.interstitial.InterstitialAdGroup
import com.tomo.monetization.ads.natives.NativeAdGroup

data class FOConfig(
    val bannerSplashGroup: BannerAdGroup? = null,
    val interSplashGroup: InterstitialAdGroup,
    val nativeLanguageGroup: NativeAdGroup,
    val nativeLanguageGroup2: NativeAdGroup? = null,
    val languageUiConfig: LanguageUiConfig,
    val onboardUiConfig: OnboardUiConfig,
    val splashTimeOut: Long = 15_000L,
    val splashMinTime: Long = 5_000L,
)

data class LanguageUiConfig(
    val listLanguage: List<AppLanguage>,
    val titleColor: Color,
    val titleTextId: Int,
    val nextComposable: @Composable ((Boolean, () -> Unit) -> Unit),
    val itemLanguageComposable: @Composable ((AppLanguage, Boolean, () -> Unit) -> Unit),
    val backgroundColor: Color? = null,
    val backgroundImage: Int? = null,
) {
    companion object {
        val defaultValue = LanguageUiConfig(
            listLanguage = emptyList(),
            titleColor = Color.Black,
            titleTextId = 0,
            nextComposable = { _, _ -> Box(modifier = Modifier.fillMaxWidth()) },
            itemLanguageComposable = { _, _, _ -> Box(modifier = Modifier.fillMaxWidth()) },
            backgroundColor = null,
            backgroundImage = null
        )
    }
}

data class OnboardUiConfig(
    val pages: List<OnboardPageConfig>,
    val obdFullPositions: Set<Int> = setOf(2),
    val backgroundColor: Color? = null,
    val backgroundBrush: Brush? = null,
    val backgroundImage: Int? = null,
    val animSwipeComposable: @Composable BoxScope.() -> Unit,
) {
    companion object {
        val defaultValue = OnboardUiConfig(
            pages = emptyList(),
            animSwipeComposable = { Box(modifier = Modifier) }
        )
    }
}

data class OnboardPageConfig(
    val nativeAdGroup: NativeAdGroup? = null,
    val composableContent: @Composable ((Boolean, () -> Unit) -> Unit),
)
