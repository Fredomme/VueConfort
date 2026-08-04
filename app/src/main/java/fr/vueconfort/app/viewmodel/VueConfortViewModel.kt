package fr.vueconfort.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.vueconfort.app.data.VisualProfileRepository
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.AutomationRule
import fr.vueconfort.app.model.AutomationStatus
import fr.vueconfort.app.model.UserVisualContext
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.assessment.VisualComfortAssessment
import fr.vueconfort.app.assessment.StandardizedAssessmentReport
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class VueConfortViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        VisualProfileRepository(application)

    val profile: StateFlow<VisualProfile> =
        repository.profile.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VisualProfile()
        )

    val userContext: StateFlow<UserVisualContext> =
        repository.userContext.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserVisualContext()
        )

    val assistProfiles: StateFlow<List<AssistProfile>> =
        repository.assistProfiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AssistProfile.defaults()
        )

    val activeAssistProfile: StateFlow<AssistProfile> =
        repository.activeAssistProfile.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AssistProfile.defaults().first()
        )

    val automationRules: StateFlow<List<AutomationRule>> =
        repository.automationRules.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val automationStatus: StateFlow<AutomationStatus> =
        repository.automationStatus.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AutomationStatus()
        )

    val visualAssessments: StateFlow<List<VisualComfortAssessment>> =
        repository.visualAssessments.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val standardizedAssessments: StateFlow<List<StandardizedAssessmentReport>> =
        repository.standardizedAssessments.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val onboardingCompleted: StateFlow<Boolean?> =
        repository.onboardingCompleted.map<Boolean, Boolean?> { it }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    init {
        viewModelScope.launch { repository.ensureAssistProfilesMigrated() }
    }

    fun activateAssistProfile(id: String) {
        viewModelScope.launch { repository.activateAssistProfile(id) }
    }

    fun saveAssistProfile(profile: AssistProfile) {
        viewModelScope.launch { repository.upsertAssistProfile(profile) }
    }

    fun createAssistProfile(from: AssistProfile? = null) {
        val base = from ?: activeAssistProfile.value
        saveAssistProfile(
            base.copy(
                id = "user_${UUID.randomUUID()}",
                name = if (from == null) "Nouveau profil" else "${base.name} copie",
                description = "Profil utilisateur",
                predefined = false,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun deleteAssistProfile(id: String) {
        viewModelScope.launch { repository.deleteAssistProfile(id) }
    }

    fun restoreAssistProfile(id: String) {
        viewModelScope.launch { repository.restorePredefinedProfile(id) }
    }

    fun saveAutomationRule(rule: AutomationRule) {
        viewModelScope.launch { repository.upsertAutomationRule(rule) }
    }

    fun deleteAutomationRule(id: String) {
        viewModelScope.launch { repository.deleteAutomationRule(id) }
    }

    fun pauseAutomation(durationMillis: Long?) {
        viewModelScope.launch { repository.setManualPause(durationMillis) }
    }

    fun saveVisualAssessment(value: VisualComfortAssessment) {
        viewModelScope.launch { repository.saveVisualAssessment(value) }
    }

    fun deleteVisualAssessment(id: String) {
        viewModelScope.launch { repository.deleteVisualAssessment(id) }
    }

    fun saveStandardizedAssessment(value: StandardizedAssessmentReport) {
        viewModelScope.launch { repository.saveStandardizedAssessment(value) }
    }

    fun deleteStandardizedAssessment(id: String) {
        viewModelScope.launch { repository.deleteStandardizedAssessment(id) }
    }

    fun saveProfile(profile: VisualProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
        }
    }

    fun updateProfile(
        transform: (VisualProfile) -> VisualProfile
    ) {
        saveProfile(
            transform(profile.value)
        )
    }

    fun saveUserContext(
        userContext: UserVisualContext
    ) {
        viewModelScope.launch {
            repository.saveUserContext(userContext)
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            repository.reset()
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch { repository.setOnboardingCompleted(completed) }
    }

    fun resetAssistProfiles() {
        viewModelScope.launch { repository.resetAssistProfiles() }
    }

    fun clearAssessmentHistory() {
        viewModelScope.launch { repository.clearAssessmentHistory() }
    }

    fun clearAutomationRules() {
        viewModelScope.launch { repository.clearAutomationRules() }
    }
}
