package fr.vueconfort.app.core

import android.util.Log
import androidx.annotation.StringRes
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.R

enum class ErrorCategory {
    MISSING_PERMISSION, SERVICE_INACTIVE, OVERLAY_UNAVAILABLE,
    MAGNIFICATION_UNAVAILABLE, EXTRACTION_IMPOSSIBLE, RESULT_NOT_SAVED,
    INVALID_DATA, STORAGE_INACCESSIBLE, UNSUPPORTED_DEVICE, UNKNOWN
}

data class UserFacingError(
    val category: ErrorCategory,
    val code: String,
    @StringRes val messageRes: Int,
    @StringRes val actionRes: Int? = null,
    val technicalDetail: String? = null
)

object ErrorReporter {
    @Volatile var lastError: UserFacingError? = null
        private set

    fun report(error: UserFacingError) {
        lastError = error
        Log.e("VueConfortApp", "category=${error.category} code=${error.code} action=${error.actionRes ?: "none"}")
    }

    fun from(category: ErrorCategory, code: String, throwable: Throwable? = null): UserFacingError {
        val message = when (category) {
            ErrorCategory.MISSING_PERMISSION -> R.string.error_missing_permission
            ErrorCategory.SERVICE_INACTIVE -> R.string.error_service_inactive
            ErrorCategory.OVERLAY_UNAVAILABLE -> R.string.error_overlay
            ErrorCategory.MAGNIFICATION_UNAVAILABLE -> R.string.error_magnification
            ErrorCategory.EXTRACTION_IMPOSSIBLE -> R.string.error_extraction
            ErrorCategory.RESULT_NOT_SAVED -> R.string.error_result_not_saved
            ErrorCategory.INVALID_DATA -> R.string.error_invalid_data
            ErrorCategory.STORAGE_INACCESSIBLE -> R.string.error_storage
            ErrorCategory.UNSUPPORTED_DEVICE -> R.string.error_unsupported_device
            ErrorCategory.UNKNOWN -> R.string.error_unknown
        }
        return UserFacingError(
            category, code, message,
            if (category in setOf(ErrorCategory.MISSING_PERMISSION, ErrorCategory.SERVICE_INACTIVE)) R.string.open_settings else R.string.retry,
            throwable?.takeIf { BuildConfig.DEBUG }?.javaClass?.simpleName
        ).also(::report)
    }

    fun clear() { lastError = null }
}
