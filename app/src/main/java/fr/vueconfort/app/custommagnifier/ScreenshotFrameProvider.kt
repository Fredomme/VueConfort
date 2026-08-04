package fr.vueconfort.app.custommagnifier

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display

internal class ScreenshotFrameProvider(
    private val service: AccessibilityService
) {
    fun captureOnce(callback: (Result<Bitmap>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(Result.failure(UnsupportedOperationException("takeScreenshot requires Android 11")))
            return
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?: error("HardwareBuffer could not be wrapped")
                        callback(Result.success(hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)))
                    } catch (throwable: Throwable) {
                        callback(Result.failure(throwable))
                    } finally {
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    callback(Result.failure(ScreenshotException(errorCode)))
                }
            }
        )
    }
}

internal class ScreenshotException(val errorCode: Int) :
    IllegalStateException("Accessibility screenshot failed: $errorCode")
