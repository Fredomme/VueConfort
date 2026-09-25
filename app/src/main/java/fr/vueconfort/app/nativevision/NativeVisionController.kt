package fr.vueconfort.app.nativevision

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.data.VisualProfileRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** One application owner; leaving a screen never creates a second writer or loses rollback data. */
data class NativeVisionUiState(
    val profile: NativeVisionProfile = NativeVisionProfile(),
    val capabilities: NativeVisionCapabilities? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val restoring: Boolean = false,
    val pendingRestoration: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val submittedCommands: Long = 0,
    val executedBatches: Long = 0,
    val lastCommandReadbackMillis: Long? = null
)

class NativeVisionController private constructor(context: Context) {
    private val app = context.applicationContext
    private val repository = VisualProfileRepository(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val environment = NativeVisionEnvironment(app)
    private val samsung = SamsungNativeVisionAdapter(app)
    private val android = NativeMagnificationSession(app)
    val variant = if (BuildConfig.NATIVE_VISION_LAB) NativeVisionVariant.LAB else NativeVisionVariant.COMMERCIAL
    private fun capabilities() = NativeVisionCapabilityResolver().resolve(environment.snapshot(variant, VariantNativeVisionFactory.hasLabAccess(app)))
    private val lab = VariantNativeVisionFactory.create(app, ::capabilities)
    private val mutex = Mutex()
    private val stateLock = Any()
    private val generation = AtomicLong()
    private val plans = Channel<Plan>(Channel.CONFLATED)
    private val _state = MutableStateFlow(NativeVisionUiState())
    val state: StateFlow<NativeVisionUiState> = _state.asStateFlow()
    private data class Plan(val generation: Long, val requested: NativeVisionRequestedState)

    init {
        scope.launch {
            try {
                repository.equalizerProfile.collect { saved ->
                    var refreshAfterInitialLoad = false
                    synchronized(stateLock) {
                        _state.update { current ->
                            when {
                                !current.loaded -> {
                                    refreshAfterInitialLoad = true
                                    current.copy(profile = saved?.nativeVision ?: NativeVisionProfile(), loaded = true)
                                }
                                current.saving || current.restoring -> current
                                // Native updates are owned and published by this controller. The
                                // collector only initializes; old emissions cannot resurrect a deletion.
                                else -> current
                            }
                        }
                    }
                    // Constructor/ON_RESUME refreshes can finish before DataStore's first
                    // emission. Always revalidate saved receipts after that emission too.
                    if (refreshAfterInitialLoad) refresh()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(error = "Impossible de lire votre profil. Aucun réglage n’a été modifié.", loaded = false) } }
        }
        scope.launch {
            for (first in plans) {
                // A single pending full snapshot, not one queued write per touch event.
                delay(70)
                var latest = first
                while (true) { latest = plans.tryReceive().getOrNull() ?: break }
                mutex.withLock {
                    if (latest.generation != generation.get()) return@withLock
                    execute(latest)
                }
            }
        }
        refresh()
    }

    fun change(capability: NativeVisionCapability, value: NativeVisionValue) = synchronized(stateLock) {
        val current = _state.value
        if (!current.loaded || current.restoring) return
        if (!value.isValidFor(capability)) {
            _state.update { it.copy(error = "Ce réglage n’est pas pris en charge.") }; return
        }
        val request = current.profile.requested.copy(
            values = current.profile.requested.values + (capability to value),
            revision = current.profile.requested.revision + 1,
            updatedAtMillis = System.currentTimeMillis(), provenance = "USER_NATIVE_CONTROLS"
        )
        val next = generation.incrementAndGet()
        _state.value = current.copy(profile = current.profile.copy(enabled = true, requested = request),
            saving = true, error = null, message = null, submittedCommands = current.submittedCommands + 1)
        plans.trySend(Plan(next, request))
        Unit
    }

    /** A knob changes one field of the latest value, never a stale value captured by Compose. */
    fun <T : NativeVisionValue> update(capability: NativeVisionCapability, fallback: T, transform: (T) -> T) {
        synchronized(stateLock) {
            val latest = _state.value.profile.requested.values[capability]
            @Suppress("UNCHECKED_CAST")
            val base = if (latest != null && fallback.javaClass.isInstance(latest)) latest as T else fallback
            change(capability, transform(base))
        }
    }

    private suspend fun execute(plan: Plan) {
        val started = SystemClock.elapsedRealtime()
        try {
            val caps = capabilities()
            // Durable intent precedes effect. Equalizer render revision and bilan remain untouched.
            repository.updateNativeVision {
                if (plan.generation != generation.get()) it
                else it.copy(enabled = true, requested = plan.requested, capabilities = caps)
            }
            if (plan.generation != generation.get()) return
            val results = mutableListOf<NativeVisionApplicationResult>()
            val mag = plan.requested.values.filterKeys { it == NativeVisionCapability.MAGNIFICATION }
            if (mag.isNotEmpty()) results += android.apply(plan.requested.copy(values = mag), plan.requested.revision)
            val others = plan.requested.copy(values = plan.requested.values - NativeVisionCapability.MAGNIFICATION)
            if (plan.generation != generation.get()) return
            val labValues = if (lab == null) emptyMap() else others.values.filterKeys { capability ->
                caps[capability]?.let { it.canApplyAutomatically && it.engine == NativeVisionEngine.SAMSUNG_LAB } == true
            }
            if (labValues.isNotEmpty()) results += lab!!.apply(others.copy(values = labValues), plan.requested.revision) {
                plan.generation == generation.get()
            }
            if (plan.generation != generation.get()) return
            val guided = others.values - labValues.keys
            if (guided.isNotEmpty()) results += samsung.apply(others.copy(values = guided), plan.requested.revision)
            if (plan.generation != generation.get()) return
            val saved = repository.updateNativeVision {
                if (plan.generation != generation.get() || it.requested != plan.requested) it
                else it.copy(applied = NativeVisionAppliedState(results.associateBy { result -> result.capability }))
            }
            publishIfCurrent(plan.generation) { it.copy(profile = saved.nativeVision, capabilities = caps, saving = false,
                pendingRestoration = hasRestoration(), executedBatches = it.executedBatches + 1,
                lastCommandReadbackMillis = SystemClock.elapsedRealtime() - started,
                message = if (results.any { r -> r.status == NativeVisionApplicationStatus.APPLIED_LAB })
                    "Réglage Samsung appliqué et relu. Il reste actif après fermeture."
                    else "Préférences enregistrées sur ce téléphone.") }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            publishIfCurrent(plan.generation) { it.copy(saving = false,
                pendingRestoration = hasRestoration(), error = "Le réglage n’a pas été confirmé. Vérifiez son état ou restaurez le réglage précédent.") }
        }
    }

    fun refresh() = scope.launch {
        mutex.withLock {
            val refreshGeneration = generation.get()
            try {
                val caps = capabilities()
                publishIfCurrent(refreshGeneration) { it.copy(capabilities = caps, pendingRestoration = hasRestoration()) }
                val current = _state.value
                if (!current.loaded || current.saving || current.restoring) return@withLock
                // Reading capabilities alone never creates a personal profile.
                if (repository.equalizerProfile.first() == null) return@withLock
                if (refreshGeneration != generation.get()) return@withLock
                val verified = samsung.verify(current.profile.requested.copy(
                    values = current.profile.requested.values - NativeVisionCapability.MAGNIFICATION), current.profile.requested.revision)
                val magValue = AndroidNativeVisionAdapter().readMagnificationState()?.asValue()
                val results = current.profile.applied.results.toMutableMap()
                for (result in verified) {
                    val old = results[result.capability]
                    results[result.capability] = reconcileNativeVerification(old, result,
                        current.profile.enabled, caps[result.capability]?.canApplyAutomatically == true, hasRestoration())
                }
                results[NativeVisionCapability.MAGNIFICATION]?.let { old ->
                    if (magValue != old.applied || !AndroidNativeVisionAdapter().isControllerConnected()) results[old.capability] = old.copy(
                        status = NativeVisionApplicationStatus.NEEDS_USER_ACTION, applied = magValue,
                        confirmation = NativeVisionConfirmation.UNCONFIRMED, timestampMillis = System.currentTimeMillis(),
                        reason = "Le grossissement a changé ou son service n’est plus connecté.")
                }
                val saved = repository.updateNativeVision { latest ->
                    if (refreshGeneration != generation.get() || latest.requested != current.profile.requested) latest
                    else latest.copy(capabilities = caps, applied = NativeVisionAppliedState(results))
                }
                publishIfCurrent(refreshGeneration) { if (it.saving || it.restoring) it else it.copy(profile = saved.nativeVision) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishIfCurrent(refreshGeneration) { it.copy(error = "Impossible de vérifier tous les réglages. Aucune nouvelle activation n’a été demandée.") } }
        }
    }

    fun configure(capability: NativeVisionCapability): Boolean = samsung.openSettings(capability)

    fun confirmManually(capability: NativeVisionCapability) = scope.launch {
        var confirmationGeneration = generation.get()
        try { mutex.withLock {
            confirmationGeneration = generation.get()
            val current = _state.value
            if (!current.loaded || current.saving || current.restoring) return@withLock
            val requested = current.profile.requested.values[capability] ?: return@withLock
            // A known mismatch cannot be overridden by an unverifiable success label.
            if (samsung.read(capability).value != null) return@withLock
            if (repository.equalizerProfile.first() == null || confirmationGeneration != generation.get()) return@withLock
            val guided = samsung.apply(current.profile.requested.copy(values = mapOf(capability to requested)))
                .singleOrNull()?.takeIf { it.status == NativeVisionApplicationStatus.NEEDS_USER_ACTION } ?: return@withLock
            val result = guided.copy(
                provenance = "USER_CONFIRMED", reason = "Configuration confirmée par vous ; état non vérifié par l’application.",
                timestampMillis = System.currentTimeMillis(), profileRevision = current.profile.requested.revision,
                confirmation = NativeVisionConfirmation.USER_CONFIRMED)
            val saved = repository.updateNativeVision {
                if (confirmationGeneration != generation.get() || it.requested != current.profile.requested) it
                else it.copy(applied = NativeVisionAppliedState(it.applied.results + (capability to result)))
            }
            publishIfCurrent(confirmationGeneration) { it.copy(profile = saved.nativeVision) }
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishIfCurrent(confirmationGeneration) {
            it.copy(error = "La confirmation n’a pas pu être enregistrée. Réessayez après avoir vérifié le réglage.")
        } }
    }

    /** Invalidate the pending snapshot before waiting for an in-flight bounded transaction. */
    fun disable(restore: Boolean): Job {
        val barrierGeneration = beginBarrier()
        return scope.launch { mutex.withLock {
            try {
                val current = _state.value.profile
                val outcomes = if (restore) listOfNotNull(
                    lab?.restore(current.requested.revision, current.requested), android.restore(current.requested.revision)
                ) else listOf(NativeVisionRestorationOutcome(
                    success = listOf(lab?.keepCurrentState() ?: true, android.keepCurrentState()).all { it },
                    pending = hasRestoration(), results = emptyList()))
                val success = outcomes.all { it.success }
                val updated = current.copy(enabled = false,
                    preferences = current.preferences.copy(disableBehavior = if (restore) NativeVisionDisableBehavior.RESTORE_PREVIOUS else NativeVisionDisableBehavior.KEEP_CURRENT),
                    applied = NativeVisionAppliedState())
                val saved = if (repository.equalizerProfile.first() == null) updated else repository.updateNativeVision { latest ->
                    latest.copy(enabled = false, preferences = updated.preferences, applied = NativeVisionAppliedState())
                }.nativeVision
                publishIfCurrent(barrierGeneration) { it.copy(profile = saved, restoring = false, pendingRestoration = hasRestoration(),
                    message = if (success) (if (restore) "Vos réglages précédents sont restaurés." else "Réglages conservés. VueConfort ne les pilote plus.") else null,
                    error = if (success) null else outcomes.flatMap { it.results }.firstOrNull { r -> r.status == NativeVisionApplicationStatus.REJECTED || r.status == NativeVisionApplicationStatus.ERROR }?.reason
                        ?: "La restauration n’est pas terminée. Vos changements extérieurs sont conservés.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishIfCurrent(barrierGeneration) { it.copy(restoring = false, pendingRestoration = hasRestoration(), error = "La restauration n’a pas pu être terminée. Réessayez après avoir vérifié les autorisations.") } }
        } }
    }

    /** Cancels pending intent, waits for bounded in-flight writes, then removes the one stored profile. */
    suspend fun deletePersonalProfile() {
        val barrierGeneration = beginBarrier()
        try {
            mutex.withLock {
                repository.deleteEqualizerProfile()
                publishIfCurrent(barrierGeneration) { it.copy(profile = NativeVisionProfile(),
                    loaded = true, restoring = false, saving = false, pendingRestoration = hasRestoration(),
                    message = "Profil supprimé. Les réglages actuels du téléphone sont conservés.", error = null) }
            }
        } finally {
            publishIfCurrent(barrierGeneration) { it.copy(restoring = false, saving = false) }
        }
    }

    private fun beginBarrier(): Long = synchronized(stateLock) {
        val next = generation.incrementAndGet()
        while (plans.tryReceive().isSuccess) { /* Drop pending snapshots before waiting for the writer. */ }
        _state.update { it.copy(restoring = true, saving = false, error = null) }
        next
    }

    private fun publishIfCurrent(expected: Long, transform: (NativeVisionUiState) -> NativeVisionUiState) {
        synchronized(stateLock) {
            if (generation.get() == expected) _state.update(transform)
        }
    }

    private fun hasRestoration() = (lab?.hasPendingRestoration() == true) || android.hasPendingRestoration()

    companion object {
        @Volatile private var instance: NativeVisionController? = null
        fun get(context: Context): NativeVisionController = instance ?: synchronized(this) {
            instance ?: NativeVisionController(context).also { instance = it }
        }
    }
}

class NativeVisionViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = NativeVisionController.get(application)
    val state = controller.state
    val isLab get() = controller.variant == NativeVisionVariant.LAB
    fun change(capability: NativeVisionCapability, value: NativeVisionValue) = controller.change(capability, value)
    fun <T : NativeVisionValue> update(capability: NativeVisionCapability, fallback: T, transform: (T) -> T) = controller.update(capability, fallback, transform)
    fun refresh() { controller.refresh() }
    fun configure(capability: NativeVisionCapability) = controller.configure(capability)
    fun confirm(capability: NativeVisionCapability) { controller.confirmManually(capability) }
    fun disable(restore: Boolean) { controller.disable(restore) }
}
