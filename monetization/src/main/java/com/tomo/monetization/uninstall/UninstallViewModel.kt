package com.tomo.monetization.uninstall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomo.monetization.firstopen.model.FOManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class UninstallViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UninstallUiState(
        reasons = if (FOManager.isInitialized) FOManager.uninstallConfig.uninstallUiConfig.reasons else emptyList(),
        questions = if (FOManager.isInitialized) FOManager.uninstallConfig.questionUiConfig.questions else emptyList()
    ))
    val uiState = _uiState.asStateFlow()

    private val _currentStep = MutableStateFlow(UninstallStep.UNINSTALL)
    val currentStep = _currentStep.asStateFlow()

    private val _eventFlow = Channel<UninstallEvent>(Channel.BUFFERED)
    val eventFlow = _eventFlow.receiveAsFlow()

    fun updateStep(step: UninstallStep) {
        _currentStep.value = step
    }

    fun onBackStep() {
        when (currentStep.value) {
            UninstallStep.UNINSTALL -> pushEvent(UninstallEvent.NavToMain)
            UninstallStep.QUESTION -> updateStep(UninstallStep.UNINSTALL)
            UninstallStep.THANK_YOU -> updateStep(UninstallStep.QUESTION)
        }
    }

    fun toggleSelectQuestion(value: Pair<String, Boolean>) {
        _uiState.update {
            it.copy(questions = it.questions.map { q ->
                if (q.first == value.first) {
                    q.copy(second = !q.second)
                } else q.copy(second = false)
            })
        }
        _uiState.update {
            it.copy(isEnableUninstall = it.questions.any { e -> e.second })
        }
    }

    fun pushEvent(event: UninstallEvent) {
        viewModelScope.launch {
            _eventFlow.send(event)
        }
    }
}

internal sealed class UninstallEvent  {
    data object NavToMain : UninstallEvent()

    data object NavToSettings : UninstallEvent()
}

internal data class UninstallUiState(
    val isEnableUninstall: Boolean = false,
    val reasons: List<Pair<String, String>> = listOf(),
    val questions: List<Pair<String, Boolean>> = listOf()
)

internal enum class UninstallStep {
    UNINSTALL, QUESTION, THANK_YOU
}
