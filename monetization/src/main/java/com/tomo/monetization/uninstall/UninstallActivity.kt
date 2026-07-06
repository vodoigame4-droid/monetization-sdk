package com.tomo.monetization.uninstall

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.core.net.toUri
import com.tomo.monetization.billing.AppBilling
import com.tomo.monetization.firstopen.model.FOManager
import com.tomo.monetization.firstopen.setEdgeToEdgeConfig
import com.tomo.monetization.uninstall.screen.QuestionScreen
import com.tomo.monetization.uninstall.screen.ThankYouScreen
import com.tomo.monetization.uninstall.screen.UninstallScreen
import com.tomo.monetization.util.CommonUtil
import com.tomo.monetization.util.EventTracking
import com.tomo.monetization.util.collectRepeatOnLifecycle

class UninstallActivity : AppCompatActivity() {
    private val viewModel: UninstallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FOManager.isInitialized) {
            finish()
            return
        }
        setEdgeToEdgeConfig()
        setContent {
            FOManager.uninstallConfig.themeContent {
                val currentStep = viewModel.currentStep.collectAsState()

                UninstallNavGraph(
                    currentStep = currentStep.value,
                    viewModel = viewModel,
                )
            }
        }

        collectRepeatOnLifecycle(viewModel.currentStep) { step ->
            val screenName = when (step) {
                UninstallStep.UNINSTALL -> "uninstall_scr"
                UninstallStep.QUESTION -> "question_scr"
                UninstallStep.THANK_YOU -> "thank_you_scr"
            }
            EventTracking.logScreen(screenName)
        }

        collectRepeatOnLifecycle(viewModel.eventFlow) { event ->
            when (event) {
                is UninstallEvent.NavToMain -> {
                    FOManager.foCallback.onFinished()
                    finishAffinity()
                }
                is UninstallEvent.NavToSettings -> {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${packageName}".toUri()
                        )
                    )
                }
            }
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
        FOManager.uninstallConfig.interUninstallGroup.loadAds(this)
    }
}

@Composable
internal fun UninstallNavGraph(
    currentStep: UninstallStep,
    viewModel: UninstallViewModel,
) {
    BackHandler {
        viewModel.onBackStep()
    }

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
        label = "UninstallTransition"
    ) { step ->
        when (step) {
            UninstallStep.UNINSTALL -> {
                UninstallScreen(viewModel)
            }

            UninstallStep.QUESTION -> {
                QuestionScreen(viewModel)
            }

            UninstallStep.THANK_YOU -> {
                ThankYouScreen(viewModel)
            }
        }
    }
}