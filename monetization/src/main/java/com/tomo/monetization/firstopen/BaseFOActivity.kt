package com.tomo.monetization.firstopen

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.tomo.monetization.Monetization
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.interstitial.InterstitialAdCallback
import com.tomo.monetization.billing.AppBilling
import com.tomo.monetization.firstopen.model.FOManager
import com.tomo.monetization.firstopen.screen.LanguageScreen
import com.tomo.monetization.firstopen.screen.OnboardingScreen
import com.tomo.monetization.util.CommonUtil
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.collectLatestRepeatOnLifecycle
import com.tomo.monetization.util.collectRepeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

abstract class BaseFOActivity : AppCompatActivity() {
    companion object {
        private const val MAX_SPLASH_WAIT_MS = 20_000L
        private const val SPLASH_AD_SHOW_TIMEOUT_MS = 12_000L
        private const val BANNER_READY_TO_INTER_DELAY_MS = 1_000L
    }

    private val viewModel: FOViewModel by viewModels()
    private var startLoadAds = false
    private var splashJob: kotlinx.coroutines.Job? = null
    private var overallTimeoutJob: kotlinx.coroutines.Job? = null
    private var hasMovedPastSplash = false

    private fun triggerHandleSplash() {
        if (hasMovedPastSplash || !startLoadAds || viewModel.currentStep.value != FOStep.SPLASH) return
        splashJob?.cancel()
        splashJob = lifecycleScope.launch {
            handleSplash()
        }
    }

    private fun movePastSplash(reason: String, cancelActiveJob: Boolean) {
        if (hasMovedPastSplash || viewModel.currentStep.value != FOStep.SPLASH) return
        hasMovedPastSplash = true
        startLoadAds = false
        Log.w(
            "FOTimeline",
            "[${System.currentTimeMillis() - FOManager.splashStartTime}ms] movePastSplash() -> $reason"
        )
        if (cancelActiveJob) {
            splashJob?.cancel()
        }
        viewModel.updateStep(FOStep.LANGUAGE)
    }

    @androidx.compose.foundation.ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeConfig()
        if (!viewModel.isInitCalled) {
            viewModel.initAds(this)
        }
        setContent {
            ComposableFOTheme {
                val currentStep = viewModel.currentStep.collectAsState()

                FirstOpenNavGraph(
                    currentStep = currentStep.value,
                    viewModel = viewModel,
                    splashScreen = { ComposableSplash() }
                )
            }
        }

        collectRepeatOnLifecycle(viewModel.currentStep) { step ->
            val screenName = when (step) {
                FOStep.SPLASH -> "splash_scr"
                FOStep.LANGUAGE -> "language_scr"
                FOStep.ONBOARDING -> "onboarding_scr"
            }
            EventTracking.logScreen(screenName)
        }

        collectLatestRepeatOnLifecycle(viewModel.initShortcut) { startInitShortcut ->
            if (startInitShortcut) {
                setDynamicShortcut()
            }
        }

        collectRepeatOnLifecycle(viewModel.eventFlow) { event ->
            when (event) {
                is FirstOpenEvent.LoadInterSplash -> {
                    startLoadAds = true
                    // Start overall timeout SAU consent xong, không phải từ onCreate
                    overallTimeoutJob?.cancel()
                    overallTimeoutJob = lifecycleScope.launch {
                        delay(MAX_SPLASH_WAIT_MS)
                        movePastSplash("overall timeout ${MAX_SPLASH_WAIT_MS}ms", cancelActiveJob = true)
                    }
                    triggerHandleSplash()
                }
            }
        }

        collectRepeatOnLifecycle(FOManager.splashInitializedFlow) { done ->
            if (done) {
                viewModel.updateToolbarText(
                    titleLabel = getString(FOManager.foConfig.languageUiConfig.titleTextId),
                )
            }
        }

        // Overall timeout đã chuyển vào LoadInterSplash event để không race với consent form
    }

    private suspend fun handleSplash() {
        val interSplash = FOManager.foConfig.interSplashGroup
        val bannerSplash = FOManager.foConfig.bannerSplashGroup

        if (bannerSplash != null && bannerSplash.enabled) {
            if (bannerSplash.statusFlow.value == AdStatus.None) {
                Log.d(
                    "FOTimeline",
                    "[${System.currentTimeMillis() - FOManager.splashStartTime}ms] handleSplash() -> trigger banner preload"
                )
                bannerSplash.loadAds(this@BaseFOActivity, resources.configuration.screenWidthDp)
            }

        }

        val e0 = System.currentTimeMillis() - FOManager.splashStartTime
        Log.d("FOTimeline", "[${e0}ms] handleSplash() -> enabled=${interSplash.enabled}")
        if (interSplash.enabled) {
            Log.d("FOTimeline", "[${e0}ms] handleSplash() -> loadAds() start")
            Monetization.disableDistanceTimeOnce()
            interSplash.loadAds(this@BaseFOActivity, FOManager.foConfig.splashTimeOut, false)
            val e1 = System.currentTimeMillis() - FOManager.splashStartTime
            Log.d(
                "FOTimeline",
                "[${e1}ms] handleSplash() -> waiting statusFlow (timeout=${FOManager.foConfig.splashTimeOut}ms)..."
            )
            val result = withTimeoutOrNull(FOManager.foConfig.splashTimeOut) {
                interSplash.statusFlow.first {
                    it == AdStatus.Ready || it == AdStatus.Failure
                }
            }
            val e2 = System.currentTimeMillis() - FOManager.splashStartTime
            Log.d("FOTimeline", "[${e2}ms] handleSplash() -> inter result=$result")
            if (result == AdStatus.Ready) {
                if (bannerSplash != null && bannerSplash.enabled) {
                    val currentBannerStatus = bannerSplash.statusFlow.value
                    val remainingOverallWait =
                        (MAX_SPLASH_WAIT_MS - (System.currentTimeMillis() - FOManager.splashStartTime))
                            .coerceAtLeast(0L)
                    val bannerGateResult = when (currentBannerStatus) {
                        AdStatus.Shown, AdStatus.Failure -> currentBannerStatus
                        else -> withTimeoutOrNull(remainingOverallWait) {
                            bannerSplash.statusFlow.first {
                                it == AdStatus.Shown || it == AdStatus.Failure
                            }
                        }
                    }
                    val bannerElapsed = System.currentTimeMillis() - FOManager.splashStartTime
                    Log.d("FOTimeline", "[${bannerElapsed}ms] handleSplash() -> banner gate result=$bannerGateResult")
                    if (bannerGateResult == AdStatus.Shown) {
                        Log.d(
                            "FOTimeline",
                            "[${bannerElapsed}ms] handleSplash() -> banner shown, delaying ${BANNER_READY_TO_INTER_DELAY_MS}ms before inter"
                        )
                        delay(BANNER_READY_TO_INTER_DELAY_MS)
                    }
                }

                val beforeShowElapsed = System.currentTimeMillis() - FOManager.splashStartTime
                val remainingMinTime = FOManager.foConfig.splashMinTime - beforeShowElapsed
                if (remainingMinTime > 0) {
                    Log.d(
                        "FOTimeline",
                        "[${beforeShowElapsed}ms] handleSplash() -> waiting ${remainingMinTime}ms to meet splashMinTime..."
                    )
                    delay(remainingMinTime)
                }

                val eFinish = System.currentTimeMillis() - FOManager.splashStartTime
                Log.d("FOTimeline", "[${eFinish}ms] handleSplash() -> showSplashAds()")
                val showFallbackJob = lifecycleScope.launch {
                    delay(SPLASH_AD_SHOW_TIMEOUT_MS)
                    movePastSplash(
                        "interstitial callback timeout ${SPLASH_AD_SHOW_TIMEOUT_MS}ms",
                        cancelActiveJob = true
                    )
                }
                interSplash.showSplashAds(this@BaseFOActivity, object : InterstitialAdCallback {
                    override fun onNextAction(show: Boolean) {
                        showFallbackJob.cancel()
                        movePastSplash("interstitial finished show=$show", cancelActiveJob = true)
                    }

                    override fun onAdFailedToShow(error: com.google.android.gms.ads.AdError?) {
                        showFallbackJob.cancel()
                        movePastSplash("interstitial failed to show", cancelActiveJob = true)
                    }

                    override fun onAdImpression(adId: String, adName: String) {
                        val e3 = System.currentTimeMillis() - FOManager.splashStartTime
                        Log.d("FOTimeline", "[${e3}ms] handleSplash() -> inter impression shown")
                        startLoadAds = false
                    }
                })
            } else {
                Log.d("FOTimeline", "[${e2}ms] handleSplash() -> inter not ready, waiting for splashMinTime...")
                val beforeShowElapsed = System.currentTimeMillis() - FOManager.splashStartTime
                val remainingMinTime = FOManager.foConfig.splashMinTime - beforeShowElapsed
                if (remainingMinTime > 0) {
                    delay(remainingMinTime)
                }
                movePastSplash("interstitial not ready", cancelActiveJob = false)
            }
        } else {
            Log.d("FOTimeline", "[${e0}ms] handleSplash() -> disabled, skip -> go LANGUAGE")
            movePastSplash("interstitial disabled", cancelActiveJob = false)
        }
    }

    @Composable
    abstract fun ComposableFOTheme(content: @Composable () -> Unit)

    @Composable
    abstract fun ComposableSplash()

    abstract fun setDynamicShortcut()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (FOManager.splashInitializedFlow.value) {
            viewModel.updateToolbarText(
                titleLabel = getString(FOManager.foConfig.languageUiConfig.titleTextId),
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (AppBilling.isPurchasedAdFree) {
                CommonUtil.showNavigationBars(window)
            } else {
                CommonUtil.hideNavigationBars(window)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        triggerHandleSplash()
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
internal fun FirstOpenNavGraph(
    currentStep: FOStep,
    viewModel: FOViewModel,
    splashScreen: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            if (targetState > initialState) {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
            } else {
                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> width } + fadeOut()
            }
        },
        label = "FOTransition"
    ) { step ->
        when (step) {
            FOStep.SPLASH -> splashScreen()
            FOStep.LANGUAGE -> LanguageScreen(viewModel)
            FOStep.ONBOARDING -> OnboardingScreen(viewModel)
        }
    }
}

fun ComponentActivity.setEdgeToEdgeConfig() {
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}
