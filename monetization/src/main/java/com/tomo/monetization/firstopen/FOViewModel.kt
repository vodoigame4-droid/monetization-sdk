package com.tomo.monetization.firstopen

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.applovin.sdk.AppLovinPrivacySettings
import com.bytedance.sdk.openadsdk.api.PAGConstant
import com.google.ads.mediation.inmobi.InMobiConsent
import com.google.ads.mediation.pangle.PangleMediationAdapter
import com.inmobi.sdk.InMobiSdk
import com.mbridge.msdk.MBridgeConstans
import com.mbridge.msdk.out.MBridgeSDKFactory
import com.tomo.monetization.Monetization
import com.tomo.monetization.consent.ConsentManager
import com.tomo.monetization.firstopen.model.AppLanguage
import com.tomo.monetization.firstopen.model.FOManager
import com.tomo.monetization.firstopen.model.LanguageUiConfig
import com.tomo.monetization.firstopen.model.OnboardUiConfig
import com.tomo.monetization.util.EventTracking
import com.unity3d.ads.metadata.MetaData
import com.vungle.ads.VunglePrivacySettings
import com.tomo.monetization.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

internal class FOViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FOUiState())
    val uiState = _uiState.asStateFlow()

    private val _currentStep = MutableStateFlow(FOStep.SPLASH)
    val currentStep = _currentStep.asStateFlow()

    private val _initShortcut = MutableStateFlow(false)
    val initShortcut = _initShortcut.asStateFlow()

    private val _eventFlow = Channel<FirstOpenEvent>(Channel.BUFFERED)
    val eventFlow = _eventFlow.receiveAsFlow()

    private val _isInitCalled = AtomicBoolean(false)
    val isInitCalled: Boolean get() = _isInitCalled.get()

    val doneFirstOpen: Boolean
        get() = FOSharedPref.getInstance().doneFirstOpen()

    init {
        viewModelScope.launch {
            combine(
                FOManager.splashInitializedFlow,
                Monetization.adsInitFlow
            ) { splashInitialized, adsReady ->
                val elapsed = System.currentTimeMillis() - FOManager.splashStartTime
                Log.d("FOTimeline", "[${elapsed}ms] combine: splashInitialized=$splashInitialized, adsReady=$adsReady")
                splashInitialized && adsReady
            }
                .filter { it }
                .collect {
                    // Start FO
                    val elapsed = System.currentTimeMillis() - FOManager.splashStartTime
                    Log.d("FOTimeline", "[${elapsed}ms] ✅ BOTH ready → sending LoadInterSplash event")
                    val languageUiConfig = FOManager.foConfig.languageUiConfig
                    _uiState.update {
                        it.copy(
                            languageList = languageUiConfig.listLanguage,
                            languageUiConfig = languageUiConfig,
                            onboardUiConfig = FOManager.foConfig.onboardUiConfig
                        )
                    }
                    _eventFlow.send(FirstOpenEvent.LoadInterSplash)
                }
        }
    }

    fun updateStep(step: FOStep) {
        if (step == FOStep.LANGUAGE) {
            _initShortcut.value = true
            if (doneFirstOpen) {
                FOManager.foCallback.onFinished()
                return
            }
        }
        _currentStep.value = step
    }

    fun onBackStep() {
        when (currentStep.value) {
            FOStep.ONBOARDING -> updateStep(FOStep.LANGUAGE)
            else -> {}
        }
    }

    fun onDoneSelectLanguage() {
        _uiState.value.languageList.firstOrNull { it.isSelected }?.let { language ->
            val appLocale = LocaleListCompat.forLanguageTags(language.languageCode)
            AppCompatDelegate.setApplicationLocales(appLocale)
            FOManager.foCallback.onLanguageConfirm(language)
        }
        updateStep(FOStep.ONBOARDING)
    }

    fun onChangeLanguage(item: AppLanguage) {
        _uiState.update {
            it.copy(languageList = it.languageList.map { e ->
                e.copy(isSelected = item.languageCode == e.languageCode)
            })
        }
    }

    fun updateToolbarText(titleLabel: String) {
        _uiState.update {
            it.copy(languageTitle = titleLabel)
        }
    }

    fun onDoneOnboarding() = viewModelScope.launch(Dispatchers.IO) {
        FOSharedPref.getInstance().setDoneFirstOpen(true)
        EventTracking.logEvent("first_open_done")
        FOManager.foCallback.onFinished()
    }

    fun initAds(activity: Activity) = viewModelScope.launch {
        if (_isInitCalled.getAndSet(true)) return@launch

        ConsentManager.init(activity)
        ConsentManager.requestConsent(activity, BuildConfig.DEBUG, Monetization.consentTestDevice, false) {
            viewModelScope.launch {
                val elapsed1 = System.currentTimeMillis() - FOManager.splashStartTime
                Log.d("FOTimeline", "[${elapsed1}ms] ✅ Consent done → applySdkConsents...")
                applySdkConsents(activity)
                val elapsed2 = System.currentTimeMillis() - FOManager.splashStartTime
                Log.d("FOTimeline", "[${elapsed2}ms] ✅ applySdkConsents done → Monetization.init()...")
                withContext(Dispatchers.Main) {
                    Monetization.init(activity) {
                        val elapsed3 = System.currentTimeMillis() - FOManager.splashStartTime
                        Log.d("FOTimeline", "[${elapsed3}ms] ✅ MobileAds.initialize() DONE → adsInitFlow=true")
                        Monetization.updateAdsInit(true)
                    }
                }
            }
        }
    }

    private suspend fun applySdkConsents(context: Context) = withContext(Dispatchers.IO) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val purposeConsents = sharedPref.getString("IABTCF_PurposeConsents", "")
        val gdprApplies = sharedPref.getInt("IABTCF_gdprApplies", 0)
        val uspString = sharedPref.getString("IABUSPrivacy_String", null)

        val hasConsentForPurposeOne = purposeConsents?.isNotEmpty() == true && purposeConsents.first() == '1'
        val doNotSell = uspString?.let { it.length >= 3 && it[2] == 'Y' } ?: false

        // Applovin
        AppLovinPrivacySettings.setHasUserConsent(hasConsentForPurposeOne)
        AppLovinPrivacySettings.setDoNotSell(doNotSell)

        // Mintegral
        MBridgeSDKFactory.getMBridgeSDK().apply {
            setConsentStatus(
                context,
                if (hasConsentForPurposeOne) MBridgeConstans.IS_SWITCH_ON else MBridgeConstans.IS_SWITCH_OFF
            )
            setDoNotTrackStatus(context, doNotSell)
        }

        // Liftoff / Vungle
        VunglePrivacySettings.setGDPRStatus(hasConsentForPurposeOne, "3.2.0")
        VunglePrivacySettings.setCCPAStatus(doNotSell)

        // Pangle
        PangleMediationAdapter.setPAConsent(
            if (hasConsentForPurposeOne) PAGConstant.PAGPAConsentType.PAG_PA_CONSENT_TYPE_CONSENT
            else PAGConstant.PAGPAConsentType.PAG_PA_CONSENT_TYPE_NO_CONSENT
        )

        // InMobi
        try {
            val consentObject = JSONObject().apply {
                put(InMobiSdk.IM_GDPR_CONSENT_AVAILABLE, gdprApplies == 1)
                put("gdpr", if (hasConsentForPurposeOne) "1" else "0")
            }
            InMobiConsent.updateGDPRConsent(consentObject)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // Unity Ads
        MetaData(context).apply {
            this["gdpr.consent"] = hasConsentForPurposeOne
            this["privacy.consent"] = doNotSell
            commit()
        }
    }
}

internal data class FOUiState(
    val languageList: List<AppLanguage> = listOf(),
    val languageTitle: String = "",
    val languageUiConfig: LanguageUiConfig = LanguageUiConfig.defaultValue,
    val onboardUiConfig: OnboardUiConfig = OnboardUiConfig.defaultValue,
)

internal sealed class FirstOpenEvent {
    data object LoadInterSplash : FirstOpenEvent()
}

internal enum class FOStep {
    SPLASH, LANGUAGE, ONBOARDING
}
