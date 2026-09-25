package fr.vueconfort.app.nativevision

/** Re-observing the phone must not rewrite who acted or turn an unknown state into a success. */
internal fun reconcileNativeVerification(
    previous: NativeVisionApplicationResult?, observed: NativeVisionApplicationResult,
    profileEnabled: Boolean, automaticControlAvailable: Boolean, restorationAvailable: Boolean
): NativeVisionApplicationResult {
    val sameRequest = previous != null && previous.capability == observed.capability &&
        previous.profileRevision == observed.profileRevision && previous.requested == observed.requested
    if (!sameRequest) return observed
    checkNotNull(previous)
    if (profileEnabled && previous.status == NativeVisionApplicationStatus.APPLIED_LAB && automaticControlAvailable &&
        observed.confirmation == NativeVisionConfirmation.READ_BACK_CONFIRMED) {
        return observed.copy(status = previous.status, engine = previous.engine, restorationAvailable = restorationAvailable)
    }
    if (previous.confirmation == NativeVisionConfirmation.USER_CONFIRMED && observed.applied == null &&
        observed.status == NativeVisionApplicationStatus.NEEDS_USER_ACTION) {
        return observed.copy(confirmation = NativeVisionConfirmation.USER_CONFIRMED,
            provenance = previous.provenance, reason = previous.reason)
    }
    return observed
}
