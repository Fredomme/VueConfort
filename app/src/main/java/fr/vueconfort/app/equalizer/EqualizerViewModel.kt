package fr.vueconfort.app.equalizer

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import fr.vueconfort.app.data.VisualProfileRepository
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.nativevision.NativeVisionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EqualizerUiState(
    val draft: EqualizerProfile = EqualizerProfile(),
    val savedProfile: EqualizerProfile? = null,
    val confirmedPrescription: OpticalPrescription? = null,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val hasSavedProfile: Boolean get() = savedProfile != null
    val hasUnsavedChanges: Boolean get() =
        !draft.sameUserChoices(savedProfile ?: EqualizerProfile(provenance = draft.provenance))
}

internal fun EqualizerProfile.sameRenderInputs(other: EqualizerProfile): Boolean =
    sameUserChoices(other) && confirmedBilan == other.confirmedBilan

/** Keep draw revisions monotonic even when DataStore emits unrelated loupe/reader changes. */
internal fun EqualizerUiState.withPersistedSnapshot(
    profile: EqualizerProfile?,
    bilan: OpticalPrescription?,
    restoredDraft: EqualizerProfile? = null
): EqualizerUiState {
    val reference = ConfirmedBilanReference.from(bilan)
    val desired = when {
        !loaded -> restoredDraft ?: profile ?: draft
        !busy && !hasUnsavedChanges -> profile ?: EqualizerProfile()
        else -> draft
    }
    val target = desired.copy(confirmedBilan = reference)
    val nextDraft = when {
        !loaded -> if (desired.confirmedBilan == reference) desired else target.copy(
            revision = desired.revision + 1, calculated = null, applied = null
        )
        draft.sameRenderInputs(target) -> draft
        else -> target.copy(
            revision = maxOf(draft.revision, target.revision) + 1, calculated = null, applied = null
        )
    }
    return copy(draft = nextDraft, savedProfile = profile, confirmedPrescription = bilan, loaded = true)
}

/** Draft survives rotation; saved profile is an awaited, atomic disk operation. */
class EqualizerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = VisualProfileRepository(application)
    private val restoredDraft = savedStateHandle.get<Bundle>(DRAFT_STATE)
        ?.getString(DRAFT_VALUE)?.let(EqualizerProfileCodec::decode)
    private val _uiState = MutableStateFlow(EqualizerUiState(draft = restoredDraft ?: EqualizerProfile()))
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()
    private var latestDrawnRecord: EqualizerRenderRecord? = null

    init {
        // Serialization runs when Android saves state, never once per slider movement.
        savedStateHandle.setSavedStateProvider(DRAFT_STATE) {
            Bundle().apply { putString(DRAFT_VALUE, EqualizerProfileCodec.encode(_uiState.value.draft)) }
        }
        viewModelScope.launch {
            try {
                combine(repository.equalizerProfile, repository.opticalPrescription) { profile, bilan ->
                    profile to bilan?.takeIf { it.isValid && it.confirmedByUser }
                }.collect { (profile, bilan) ->
                    publish(_uiState.value.withPersistedSnapshot(profile, bilan, restoredDraft))
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                _uiState.update { it.copy(loaded = false, error = "Impossible de lire le profil enregistré. Réouvre l’application pour réessayer.") }
            }
        }
    }

    fun updatePreferences(change: (EqualizerPreferences) -> EqualizerPreferences) {
        mutateDraft { current ->
            val preferences = change(current.preferences).validated()
            if (preferences == current.preferences) current else current.copy(
                preferences = preferences, revision = current.revision + 1, calculated = null, applied = null
            )
        }
    }

    fun selectScene(scene: EqualizerScene) {
        mutateDraft { current ->
            if (scene == current.scene) current else current.copy(scene = scene, revision = current.revision + 1, applied = null)
                // Existing calculated parameters, if any, are bound to their input revision too.
                .copy(calculated = null)
        }
    }

    /** Call for the adjusted preview only, once this exact revision has actually reached drawing. */
    fun recordApplied(record: EqualizerRenderRecord) {
        val current = _uiState.value
        if (!current.loaded || current.busy || record.sourceRevision != current.draft.revision) return
        if (record == latestDrawnRecord) return
        val updated = current.draft.copy(applied = record)
        // A draw receipt does not trigger another composition/draw. It is committed only on Save.
        if (runCatching { updated.validated() }.isSuccess) latestDrawnRecord = record
    }

    fun save(): Job? = perform("Impossible d’enregistrer le profil. Tes modifications restent disponibles.") {
        val draft = _uiState.value.draft
        val receipt = latestDrawnRecord?.takeIf { it.sourceRevision == draft.revision } ?: draft.applied
        val saved = repository.saveEqualizerProfile(draft.copy(applied = receipt))
        val current = _uiState.value
        val sameInputs = current.draft.sameRenderInputs(saved)
        val nextDraft = if (sameInputs && saved.revision == current.draft.revision) saved else saved.copy(
            revision = if (sameInputs) maxOf(saved.revision, current.draft.revision)
                else maxOf(saved.revision, current.draft.revision) + 1,
            calculated = null, applied = null
        )
        publish(current.copy(draft = nextDraft, savedProfile = saved, message = "Profil enregistré.", error = null))
    }

    fun cancelChanges() {
        val current = _uiState.value
        if (!current.loaded || current.busy) return
        publish(current.copy(
            draft = (current.savedProfile ?: EqualizerProfile()).copy(
                confirmedBilan = ConfirmedBilanReference.from(current.confirmedPrescription),
                revision = current.draft.revision + 1,
                calculated = null, applied = null
            ),
            message = "Modifications annulées.", error = null
        ))
    }

    /** Reset is a draft operation. It does not delete the saved profile or the confirmed bilan. */
    fun resetToNeutral() {
        mutateDraft { current ->
            current.copy(preferences = EqualizerPreferences.Neutral, revision = current.revision + 1, calculated = null, applied = null)
        }
        if (_uiState.value.loaded && !_uiState.value.busy) {
            _uiState.update { it.copy(message = "Réglages neutres. Enregistre pour les conserver.") }
        }
    }

    fun deleteProfile(): Job? = perform("Impossible de supprimer le profil. Réessaie dans un instant.") {
        NativeVisionController.get(getApplication()).deletePersonalProfile()
        val current = _uiState.value
        publish(current.copy(
                draft = EqualizerProfile(revision = current.draft.revision + 1, confirmedBilan = ConfirmedBilanReference.from(current.confirmedPrescription)),
                savedProfile = null, message = "Profil supprimé. Ton bilan est conservé.", error = null
        ))
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    private fun mutateDraft(change: (EqualizerProfile) -> EqualizerProfile) {
        val current = _uiState.value
        if (!current.loaded || current.busy) return
        publish(current.copy(draft = change(current.draft), message = null, error = null))
    }

    private fun publish(next: EqualizerUiState) {
        val previous = _uiState.value.draft
        if (previous.revision != next.draft.revision || !previous.sameRenderInputs(next.draft)) latestDrawnRecord = null
        _uiState.value = next
    }

    private fun perform(errorMessage: String, operation: suspend () -> Unit): Job? {
        val current = _uiState.value
        if (!current.loaded || current.busy) return null
        _uiState.value = current.copy(busy = true, message = null, error = null)
        return viewModelScope.launch {
            try {
                operation()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                _uiState.update { it.copy(error = errorMessage) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        private const val DRAFT_STATE = "equalizer_draft"
        private const val DRAFT_VALUE = "profile"
    }
}
