package com.tomo.monetization.uninstall.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomo.monetization.R
import com.tomo.monetization.ads.ui.NativeAdContent
import com.tomo.monetization.firstopen.model.FOManager
import com.tomo.monetization.uninstall.UninstallEvent
import com.tomo.monetization.uninstall.UninstallStep
import com.tomo.monetization.uninstall.UninstallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UninstallScreen(
    viewModel: UninstallViewModel,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = state.value
    val config = remember { FOManager.uninstallConfig.uninstallUiConfig }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NativeAdContent(
                adGroup = config.nativeAdGroup,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                config.imageComposable()

                Text(
                    text = stringResource(config.titleTextId),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = stringResource(config.descriptionTextId),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.reasons.forEach {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = FOManager.uninstallConfig.bgItemColor ?: MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = it.first,
                                fontSize = 15.sp,
                                maxLines = 2,
                                fontWeight = FontWeight.SemiBold,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                modifier = Modifier
                                    .widthIn(min = 110.dp)
                                    .height(40.dp),
                                shape = MaterialTheme.shapes.medium,
                                onClick = {
                                    viewModel.pushEvent(UninstallEvent.NavToMain)
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = it.second,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FOManager.uninstallConfig.btnActionColor ?: MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 8.dp)
                        .padding(top = 8.dp)
                        .height(48.dp),
                    onClick = {
                        viewModel.pushEvent(UninstallEvent.NavToMain)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = stringResource(config.dontUninstallTextId),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FOManager.uninstallConfig.btnConfirmColor ?: MaterialTheme.colorScheme.primary,
                    )
                }

                TextButton(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(48.dp),
                    onClick = {
                        viewModel.updateStep(UninstallStep.QUESTION)
                    },
                ) {
                    Text(
                        text = stringResource(config.uninstallTextId),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            IconButton(
                onClick = {
                    viewModel.pushEvent(UninstallEvent.NavToMain)
                },
                modifier = Modifier.align(alignment = Alignment.TopStart)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "back btn",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}