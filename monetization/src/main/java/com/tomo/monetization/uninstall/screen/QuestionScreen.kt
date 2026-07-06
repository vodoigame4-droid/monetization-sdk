package com.tomo.monetization.uninstall.screen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomo.monetization.Monetization
import com.tomo.monetization.R
import com.tomo.monetization.ads.interstitial.InterstitialAdCallback
import com.tomo.monetization.ads.ui.NativeAdContent
import com.tomo.monetization.firstopen.model.FOManager
import com.tomo.monetization.uninstall.UninstallEvent
import com.tomo.monetization.uninstall.UninstallStep
import com.tomo.monetization.uninstall.UninstallViewModel
import com.tomo.monetization.util.findActivity
import com.tomo.monetization.util.noRippleClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuestionScreen(
    viewModel: UninstallViewModel,
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = state.value
    val config = remember { FOManager.uninstallConfig.questionUiConfig }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.onBackStep() }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "back btn",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(config.titleTextId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
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
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.questions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = FOManager.uninstallConfig.bgItemColor ?: MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(8.dp)
                                .noRippleClickable {
                                    viewModel.toggleSelectQuestion(it)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = it.first,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp)
                            )

                            Icon(
                                painter = painterResource(
                                    id = if (it.second) R.drawable.ic_question_checked
                                    else R.drawable.ic_not_check
                                ),
                                contentDescription = "checkbox icon",
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(bottom = 16.dp, top = 12.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = {
                            viewModel.pushEvent(UninstallEvent.NavToMain)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = stringResource(config.cancelTextId),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = {
                            if (!uiState.isEnableUninstall) {
                                Toast.makeText(context, "Pick a reason to start the next action.", Toast.LENGTH_SHORT).show()
                            } else {
                                context.findActivity()?.let { act ->
                                    Monetization.disableDistanceTimeOnce()
                                    FOManager.uninstallConfig.interUninstallGroup.showAds(act, object : InterstitialAdCallback {
                                        override fun onNextAction(show: Boolean) {
                                            viewModel.updateStep(UninstallStep.THANK_YOU)
                                        }
                                    })
                                }
                            }
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
                            text = stringResource(config.uninstallTextId),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FOManager.uninstallConfig.btnConfirmColor ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
