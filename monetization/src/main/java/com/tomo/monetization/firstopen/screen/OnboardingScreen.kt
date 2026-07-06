package com.tomo.monetization.firstopen.screen

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomo.monetization.ads.base.AdStatus
import com.tomo.monetization.ads.ui.NativeAdContent
import com.tomo.monetization.ads.ui.NativeAdObdFull
import com.tomo.monetization.firstopen.FOViewModel
import com.tomo.monetization.firstopen.model.FOManager
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.conditional
import kotlinx.coroutines.launch

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
internal fun OnboardingScreen(viewModel: FOViewModel) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = state.value
    val baseConfig = remember { uiState.onboardUiConfig }
    
    val config = remember(baseConfig) {
        val filteredPages = mutableListOf<com.tomo.monetization.firstopen.model.OnboardPageConfig>()
        val filteredFullPositions = mutableSetOf<Int>()
        baseConfig.pages.forEachIndexed { index, page ->
            if (index in baseConfig.obdFullPositions) {
                if (page.nativeAdGroup?.enabled == true) {
                    filteredFullPositions.add(filteredPages.size)
                    filteredPages.add(page)
                }
            } else {
                filteredPages.add(page)
            }
        }
        baseConfig.copy(
            pages = filteredPages,
            obdFullPositions = filteredFullPositions
        )
    }

    val adFailures = remember { mutableStateMapOf<Int, Boolean>() }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { config.pages.size })
    val context = LocalContext.current
    val firstFullPos = remember(config) { config.obdFullPositions.minOrNull() ?: -1 }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                Log.d("OnboardingScreen", "Page changed: $page")
                FOManager.foCallback.onOnboardPageChanged(page)
                val screenName = if (page in config.obdFullPositions) {
                    config.pages[config.pages.size - 1].nativeAdGroup?.loadAds(context)
                    "onboarding_full_scr"
                } else {
                    val pageNum = if (config.obdFullPositions.isEmpty()) {
                        page + 1
                    } else if (page < firstFullPos) {
                        page + 1
                    } else {
                        page
                    }
                    if (pageNum == 1) {
                        config.pages[0].nativeAdGroup?.let { adGroup ->
                            if (adGroup.status == AdStatus.Failure) {
                                adGroup.loadAds(context)
                            }
                        }
                        // Preload ads for all fullscreen positions
                        config.obdFullPositions.forEach { fullPos ->
                            config.pages[fullPos].nativeAdGroup?.loadAds(context)
                        }
                    }
                    "onboarding_${pageNum}_scr"
                }
                EventTracking.logScreen(screenName)
            }
    }

    BackHandler {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(bottom = paddingValues.calculateBottomPadding())
                .fillMaxSize()
        ) { index ->
            val adGroup = config.pages[index].nativeAdGroup
            if (index in config.obdFullPositions && adGroup != null && adGroup.enabled) {
                val adStatus by adGroup.statusFlow.collectAsState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .conditional(config.backgroundColor != null) {
                            background(config.backgroundColor!!)
                        }
                        .conditional(config.backgroundBrush != null) {
                            background(config.backgroundBrush!!)
                        }
                ) {
                    NativeAdObdFull(
                        adGroup = adGroup,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = paddingValues.calculateTopPadding()),
                        contentDefault = {
                            config.pages[index].composableContent(
                                !adFailures.getOrDefault(index, false)
                            ) {
                                coroutineScope.launch {
                                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    } else {
                                        viewModel.onDoneOnboarding()
                                    }
                                }
                            }
                        },
                        showAfterImpression = true,
                        requestLayoutIndex = index,
                        onAdFailedToLoad = { adFailures[index] = it }
                    )
                    if (adStatus == AdStatus.Shown || adStatus == AdStatus.Ready) {
                        config.animSwipeComposable(this)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .conditional(config.backgroundColor != null) {
                            background(config.backgroundColor!!)
                        }
                        .conditional(config.backgroundBrush != null) {
                            background(config.backgroundBrush!!)
                        }
                ) {
                    if (config.backgroundImage != null) {
                        coil.compose.AsyncImage(
                            model = config.backgroundImage,
                            contentDescription = "background image",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            config.pages[index].composableContent(
                                !adFailures.getOrDefault(index, false)
                            ) {
                                coroutineScope.launch {
                                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    } else {
                                        viewModel.onDoneOnboarding()
                                    }
                                }
                            }
                        }
                        config.pages[index].nativeAdGroup?.let { adGroup ->
                            NativeAdContent(
                                adGroup = adGroup,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .conditional(config.backgroundColor != null) {
                                        background(config.backgroundColor!!)
                                    },
                                showAfterImpression = true,
                                requestLayoutIndex = index,
                                onAdFailedToLoad = { adFailures[index] = it }
                            )
                        }
                    }
                }
            }
        }
    }
}