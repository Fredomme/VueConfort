package fr.vueconfort.app.nativevision

import fr.vueconfort.app.magnifier.ScreenMagnifierService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** A reading of the existing controller, never a second magnifier or an inferred profile. */
data class NativeMagnificationSnapshot(
    val enabled: Boolean,
    val scale: Float?,
    val centerX: Float?,
    val centerY: Float?,
    val mode: NativeMagnificationMode?,
    val activationExact: Boolean,
) {
    val restorable: Boolean get() = activationExact && mode != null &&
        scale != null && scale.isFinite() && scale in 1f..8f &&
        ((!enabled && centerX == null && centerY == null) ||
            (centerX != null && centerX.isFinite() && centerX >= 0f &&
                centerY != null && centerY.isFinite() && centerY >= 0f))

    val inactiveCenterUnobservable: Boolean get() = !enabled && centerX == null && centerY == null

    fun matches(other: NativeMagnificationSnapshot): Boolean {
        if (!restorable || !other.restorable || enabled != other.enabled || mode != other.mode ||
            activationExact != other.activationExact) return false
        if (enabled) return close(scale, other.scale, 0.001f) && close(centerX, other.centerX, 1f) && close(centerY, other.centerY, 1f)
        // Android may drop a viewport on OFF. Confirm the exact inactive state without inventing
        // a centre, but keep the raw snapshots unchanged in the journal for a later ON restoration.
        return scale == other.scale && (inactiveCenterUnobservable || other.inactiveCenterUnobservable ||
            (close(centerX, other.centerX, 1f) && close(centerY, other.centerY, 1f)))
    }

    /** OFF may have no viewport. Only a requested ON can introduce an observed public viewport centre. */
    fun targetFor(requested: NativeVisionValue.Magnification,
                  viewportCenter: Pair<Float, Float>? = null): NativeMagnificationSnapshot? {
        if (!restorable || !requested.isValidFor(NativeVisionCapability.MAGNIFICATION)) return null
        val center = when {
            requested.centerX != null && requested.centerY != null -> requested.centerX to requested.centerY
            centerX != null && centerY != null -> centerX to centerY
            requested.enabled -> viewportCenter
            else -> null
        }
        return copy(enabled = requested.enabled, scale = requested.scale, mode = requested.mode,
            centerX = center?.first, centerY = center?.second).takeIf { it.restorable }
    }

    fun asValue(): NativeVisionValue.Magnification? =
        if (restorable && scale!! in 1f..8f) NativeVisionValue.Magnification(
            enabled = enabled, scale = scale, centerX = centerX, centerY = centerY, mode = mode!!,
        ) else null

    private fun close(a: Float?, b: Float?, tolerance: Float) =
        if (a == null || b == null) a == b else abs(a - b) <= tolerance
}

/** Public Android APIs only. Settings that need a system preference change remain user-guided. */
class AndroidNativeVisionAdapter {
    fun readMagnificationState(): NativeMagnificationSnapshot? =
        ScreenMagnifierService.nativeVisionMagnificationSnapshot()

    fun isControllerConnected(): Boolean = ScreenMagnifierService.nativeVisionControllerConnected()

    internal fun prepareMagnificationTarget(requested: NativeVisionValue.Magnification,
                                            before: NativeMagnificationSnapshot): NativeMagnificationSnapshot? =
        before.targetFor(requested, if (requested.enabled && requested.centerX == null && before.centerX == null)
            ScreenMagnifierService.nativeVisionViewportCenter() else null)

    suspend fun apply(
        request: NativeVisionRequestedState,
        profileRevision: Long = request.revision,
    ): List<NativeVisionApplicationResult> = request.values.filterKeys {
        it == NativeVisionCapability.MAGNIFICATION
    }.map { (_, value) -> applyMagnification(value, profileRevision) }

    suspend fun applyMagnification(
        requested: NativeVisionValue,
        profileRevision: Long,
        expectedBefore: NativeMagnificationSnapshot? = null,
        preparedTarget: NativeMagnificationSnapshot? = null,
    ): NativeVisionApplicationResult = withContext(Dispatchers.Main.immediate) {
        val value = requested as? NativeVisionValue.Magnification
        if (value == null || !value.isValidFor(NativeVisionCapability.MAGNIFICATION)) return@withContext result(
            requested, NativeVisionApplicationStatus.REJECTED, profileRevision,
            "Le grossissement demandé n’est pas valide.",
        )
        val before = readMagnificationState() ?: return@withContext result(
            requested, NativeVisionApplicationStatus.NEEDS_USER_PERMISSION, profileRevision,
            "Activez le service VueConfort dans les réglages d’accessibilité.",
        )
        if (!before.restorable) return@withContext result(
            requested, NativeVisionApplicationStatus.NEEDS_USER_ACTION, profileRevision,
            "Android ne fournit pas un état complet à restaurer. Utilisez les commandes de la loupe existante.",
        )
        if (expectedBefore != null && !expectedBefore.matches(before)) return@withContext result(
            requested, NativeVisionApplicationStatus.REJECTED, profileRevision,
            "Le grossissement a changé pendant la préparation. Aucune commande n’a été envoyée.",
        )
        // The session passes the exact target already saved to its write-ahead journal.
        val target = if (preparedTarget == null) prepareMagnificationTarget(value, before) else {
            val preparedCenter = if (preparedTarget.centerX != null && preparedTarget.centerY != null)
                preparedTarget.centerX to preparedTarget.centerY else null
            preparedTarget.takeIf { it == before.targetFor(value, preparedCenter) }
        }
        if (target == null) return@withContext result(requested, NativeVisionApplicationStatus.NEEDS_USER_ACTION,
            profileRevision, "Le centre de la fenêtre Android n’est pas disponible. Aucune commande n’a été envoyée.")
        setAndVerify(target, requested, profileRevision, restoring = false)
    }

    /** Refuse to overwrite a setting subsequently changed using the loupe or Android. */
    suspend fun restoreMagnification(
        before: NativeMagnificationSnapshot,
        expectedCurrent: NativeMagnificationSnapshot,
        profileRevision: Long,
        finalStep: Boolean = true,
    ): NativeVisionApplicationResult = withContext(Dispatchers.Main.immediate) {
        val requested = before.asValue() ?: NativeVisionValue.Magnification(false, 1f)
        if (!before.restorable) return@withContext result(
            requested, NativeVisionApplicationStatus.REJECTED, profileRevision,
            "L’état précédent n’est pas assez complet pour une restauration exacte.",
        )
        val current = readMagnificationState() ?: return@withContext result(
            requested, NativeVisionApplicationStatus.NEEDS_USER_PERMISSION, profileRevision,
            "Le service de grossissement n’est plus connecté. Aucune restauration n’a été imposée.",
        )
        if (!expectedCurrent.matches(current)) return@withContext result(
            requested, NativeVisionApplicationStatus.REJECTED, profileRevision,
            "Le grossissement a été modifié ailleurs. Votre nouveau réglage est conservé.",
        )
        val result = setAndVerify(before, requested, profileRevision, restoring = finalStep,
            verificationAttempts = if (finalStep) 15 else 30)
        if (!finalStep && result.status == NativeVisionApplicationStatus.APPLIED_AUTO) result.copy(
            reason = "Le mode d’affichage initial a été préparé et relu ; la restauration n’est pas encore terminée.",
            restorationAvailable = true) else result
    }

    private suspend fun setAndVerify(
        target: NativeMagnificationSnapshot,
        requested: NativeVisionValue,
        revision: Long,
        restoring: Boolean,
        verificationAttempts: Int = 15,
    ): NativeVisionApplicationResult {
        val accepted = runCatching {
            ScreenMagnifierService.applyNativeVisionMagnification(target)
        }.getOrDefault(false)
        if (!accepted) return result(requested, NativeVisionApplicationStatus.REJECTED, revision,
            "Android a refusé cette commande de grossissement.")
        repeat(verificationAttempts) {
            val actual = readMagnificationState()
            if (actual != null && target.matches(actual)) return result(
                requested, NativeVisionApplicationStatus.APPLIED_AUTO, revision,
                (if (restoring) "Le grossissement précédent a été restauré et relu." else
                    "Le contrôleur Android a appliqué le grossissement ; son état a été relu.") +
                    if (target.inactiveCenterUnobservable || actual.inactiveCenterUnobservable)
                        " Activation, mode et facteur confirmés ; le centre de la fenêtre désactivée n’est pas observable, sans coordonnées inventées."
                    else "",
                actual.asValue(), restorationAvailable = !restoring,
            )
            delay(80)
        }
        return result(requested, NativeVisionApplicationStatus.ERROR, revision,
            "La commande a été acceptée, mais son état final n’a pas été confirmé. Vérifiez le grossissement.",
            applied = readMagnificationState()?.asValue(), restorationAvailable = true)
    }

    private fun result(
        requested: NativeVisionValue,
        status: NativeVisionApplicationStatus,
        revision: Long,
        reason: String,
        applied: NativeVisionValue? = null,
        restorationAvailable: Boolean = false,
    ) = NativeVisionApplicationResult(
        capability = NativeVisionCapability.MAGNIFICATION,
        requested = requested, status = status, applied = applied,
        engine = NativeVisionEngine.ANDROID_PUBLIC,
        provenance = "AccessibilityService.MagnificationController; état relu dans le service VueConfort existant",
        reason = reason, timestampMillis = System.currentTimeMillis(), profileRevision = revision,
        restorationAvailable = restorationAvailable,
        confirmation = if (status == NativeVisionApplicationStatus.APPLIED_AUTO)
            NativeVisionConfirmation.READ_BACK_CONFIRMED else NativeVisionConfirmation.UNCONFIRMED,
    )
}
