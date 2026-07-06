package com.tomo.monetization.firstopen.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomo.monetization.ads.ui.NativeAdContent
import com.tomo.monetization.firstopen.FOViewModel
import com.tomo.monetization.firstopen.model.FOManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageScreen(viewModel: FOViewModel) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = state.value

    val isSelectLanguage by remember(uiState.languageList) {
        derivedStateOf { uiState.languageList.any { it.isSelected } }
    }

    LaunchedEffect(Unit) {
        // Load native 1 directly on LanguageScreen startup
        FOManager.foConfig.nativeLanguageGroup.loadAds(context)
    }

    LaunchedEffect(isSelectLanguage) {
        if (isSelectLanguage) {
            // Trigger load for native 2
            FOManager.foConfig.nativeLanguageGroup2?.loadAds(context)
            // Pre-load native ad obd
            FOManager.foConfig.onboardUiConfig.pages[0].nativeAdGroup?.loadAds(context)
        }
    }

    BackHandler {
    }

    val containerModifier = if (uiState.languageUiConfig.backgroundImage != null) {
        Modifier.fillMaxSize().paint(
            coil.compose.rememberAsyncImagePainter(model = uiState.languageUiConfig.backgroundImage),
            contentScale = ContentScale.Crop
        )
    } else {
        Modifier.fillMaxSize()
    }

    Scaffold(
        modifier = containerModifier,
        containerColor = uiState.languageUiConfig.backgroundColor ?: Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = uiState.languageTitle.ifEmpty { "Select language" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = uiState.languageUiConfig.titleColor,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                },
                actions = {
                    uiState.languageUiConfig.nextComposable(isSelectLanguage) {
                        viewModel.onDoneSelectLanguage()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
            )
        },
        bottomBar = {
            val currentAdGroup = if (isSelectLanguage && FOManager.foConfig.nativeLanguageGroup2 != null) {
                FOManager.foConfig.nativeLanguageGroup2!!
            } else {
                FOManager.foConfig.nativeLanguageGroup
            }

            androidx.compose.runtime.key(currentAdGroup) {
                NativeAdContent(
                    adGroup = currentAdGroup,
                    modifier = Modifier.fillMaxWidth(),
                    showAfterImpression = true,
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val native1Status by FOManager.foConfig.nativeLanguageGroup.statusFlow.collectAsStateWithLifecycle()
            val native1Enabled by FOManager.foConfig.nativeLanguageGroup.enabledFlow.collectAsStateWithLifecycle()
            
            var isNative1ClickUnlock by remember { androidx.compose.runtime.mutableStateOf(false) }

            LaunchedEffect(native1Status) {
                if (native1Status != com.tomo.monetization.ads.base.AdStatus.Loading && native1Status != com.tomo.monetization.ads.base.AdStatus.None) {
                    kotlinx.coroutines.delay(500)
                    isNative1ClickUnlock = true
                } else if (native1Status == com.tomo.monetization.ads.base.AdStatus.Loading) {
                    isNative1ClickUnlock = false
                }
            }

            val canSelect = !native1Enabled || isNative1ClickUnlock

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)
            ) {
                items(uiState.languageList.size) {
                    val item = uiState.languageList[it]
                    uiState.languageUiConfig.itemLanguageComposable(item, item.isSelected) {
                        if (canSelect) {
                            viewModel.onChangeLanguage(item)
                        }
                    }
                }
            }
        }
    }
}
