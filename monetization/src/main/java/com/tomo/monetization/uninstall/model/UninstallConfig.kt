package com.tomo.monetization.uninstall.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tomo.monetization.ads.interstitial.InterstitialAdGroup
import com.tomo.monetization.ads.natives.NativeAdGroup

data class UninstallConfig(
    val themeContent: @Composable (@Composable () -> Unit) -> Unit,
    val bgItemColor: Color? = null,
    val btnConfirmColor: Color? = null,
    val btnActionColor: Color? = null,
    val interUninstallGroup: InterstitialAdGroup,
    val uninstallUiConfig: UninstallUiConfig,
    val questionUiConfig: QuestionUiConfig,
    val thankYouUiConfig: ThankYouUiConfig
)

data class UninstallUiConfig(
    val nativeAdGroup: NativeAdGroup,
    val titleTextId: Int,
    val descriptionTextId: Int,
    val dontUninstallTextId: Int,
    val uninstallTextId: Int,
    val imageComposable: @Composable () -> Unit,
    val reasons: List<Pair<String, String>>
)

data class QuestionUiConfig(
    val nativeAdGroup: NativeAdGroup,
    val titleTextId: Int,
    val cancelTextId: Int,
    val uninstallTextId: Int,
    val questions: List<Pair<String, Boolean>>
)

data class ThankYouUiConfig(
    val nativeAdGroup: NativeAdGroup,
    val titleTextId: Int,
    val descriptionTextId: Int,
    val imageComposable: @Composable () -> Unit,
)
