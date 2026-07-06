package com.tomo.monetization.uninstall.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomo.monetization.R
import com.tomo.monetization.ads.ui.NativeAdContent
import com.tomo.monetization.firstopen.model.FOManager
import com.tomo.monetization.uninstall.UninstallEvent
import com.tomo.monetization.uninstall.UninstallViewModel
import kotlinx.coroutines.delay

@Composable
internal fun ThankYouScreen(
    viewModel: UninstallViewModel
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val config = remember { FOManager.uninstallConfig.thankYouUiConfig }

    var timeLeft by remember { mutableIntStateOf(3) }
    LaunchedEffect(timeLeft) {
        while (timeLeft > 0) {
            delay(1000L)
            timeLeft--
            if (timeLeft == 0) {
                viewModel.pushEvent(UninstallEvent.NavToSettings)
            }
        }
    }

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
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                config.imageComposable()

                Text(
                    text = stringResource(config.titleTextId),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = stringResource(config.descriptionTextId),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            IconButton(
                onClick = {
                    viewModel.onBackStep()
                },
                modifier = Modifier.align(alignment = Alignment.TopStart)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "back btn",
                    modifier = Modifier.size(24.dp)
                )
            }

            if (timeLeft <= 0) {
                IconButton(
                    onClick = {
                        viewModel.pushEvent(UninstallEvent.NavToMain)
                    },
                    modifier = Modifier.align(alignment = Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_home_24dp),
                        contentDescription = "back btn",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
