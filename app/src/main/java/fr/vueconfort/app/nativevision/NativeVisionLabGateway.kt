package fr.vueconfort.app.nativevision

data class NativeVisionRestorationOutcome(
    val success: Boolean,
    val pending: Boolean,
    val results: List<NativeVisionApplicationResult>
)

/** The commercial factory supplies no gateway. No settings names or raw writes are exposed to UI. */
interface NativeVisionLabGateway {
    fun apply(request: NativeVisionRequestedState, profileRevision: Long, isCurrent: () -> Boolean): List<NativeVisionApplicationResult>
    fun restore(profileRevision: Long, request: NativeVisionRequestedState): NativeVisionRestorationOutcome
    fun keepCurrentState(): Boolean
    fun hasPendingRestoration(): Boolean
}
