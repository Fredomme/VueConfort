package fr.vueconfort.app.calibration

import androidx.lifecycle.ViewModel
import fr.vueconfort.app.model.VisualProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CalibrationUiState(
    val session: CalibrationSession = CalibrationSession(),
    val currentProfile: VisualProfile = VisualProfile(),
    val currentTrialIndex: Int = 0,
    val started: Boolean = false,
    val completed: Boolean = false
) {
    val currentTrial: CalibrationTrial?
        get() = session.trials.getOrNull(currentTrialIndex)

    val progress: Float
        get() {
            if (session.trials.isEmpty()) {
                return 0f
            }

            if (completed) {
                return 1f
            }

            return currentTrialIndex.toFloat() /
                session.trials.size.toFloat()
        }

    val answeredTrialCount: Int
        get() = session.trials.count {
            it.choice != null
        }

    val totalTrialCount: Int
        get() = session.trials.size
}

class CalibrationViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(CalibrationUiState())

    val uiState: StateFlow<CalibrationUiState> =
        _uiState.asStateFlow()

    fun start(
        baseProfile: VisualProfile
    ) {
        val trials =
            CalibrationEngine.createTrials(baseProfile)

        _uiState.value = CalibrationUiState(
            session = CalibrationSession(
                startedAtMillis = System.currentTimeMillis(),
                trials = trials
            ),
            currentProfile = baseProfile,
            currentTrialIndex = 0,
            started = true,
            completed = false
        )
    }

    fun choose(
        choice: CalibrationChoice,
        responseTimeMillis: Long
    ) {
        val currentState = _uiState.value
        val currentTrial =
            currentState.currentTrial ?: return

        if (currentState.completed) {
            return
        }

        val answeredTrial =
            CalibrationEngine.recordChoice(
                trial = currentTrial,
                choice = choice,
                responseTimeMillis =
                    responseTimeMillis.coerceAtLeast(0L)
            )

        val updatedTrials =
            currentState.session.trials.toMutableList().apply {
                this[currentState.currentTrialIndex] =
                    answeredTrial
            }

        val updatedProfile =
            CalibrationEngine.applyChoice(
                currentProfile =
                    currentState.currentProfile,
                trial = currentTrial,
                choice = choice
            )

        val nextIndex =
            currentState.currentTrialIndex + 1

        val isCompleted =
            nextIndex >= updatedTrials.size

        if (isCompleted) {
            val completedSession =
                CalibrationEngine.completeSession(
                    session = currentState.session.copy(
                        trials = updatedTrials
                    ),
                    resultingProfile = updatedProfile
                )

            _uiState.value = currentState.copy(
                session = completedSession,
                currentProfile =
                    completedSession.resultingProfile
                        ?: updatedProfile,
                currentTrialIndex =
                    updatedTrials.size,
                completed = true
            )
        } else {
            _uiState.value = currentState.copy(
                session = currentState.session.copy(
                    trials = updatedTrials
                ),
                currentProfile = updatedProfile,
                currentTrialIndex = nextIndex,
                completed = false
            )
        }
    }

    fun restart() {
        val currentProfile =
            _uiState.value.currentProfile

        start(currentProfile)
    }

    fun restartFrom(
        baseProfile: VisualProfile
    ) {
        start(baseProfile)
    }

    fun reset() {
        _uiState.value =
            CalibrationUiState()
    }
}
