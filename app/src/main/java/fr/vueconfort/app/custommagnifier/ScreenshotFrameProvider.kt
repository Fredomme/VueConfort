package fr.vueconfort.app.custommagnifier

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display

internal class ScreenshotFrameProvider(
    private val service: AccessibilityService
) {
    fun captureOnce(
        onFrame: (Bitmap) -> Unit,
        onFailure: (errorCode: Int?, throwable: Throwable?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFailure(null, UnsupportedOperationException("takeScreenshot requires Android 11"))
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
                        // A software copy is required: retaining the hardware-backed Bitmap until
                        // the next frame makes subsequent takeScreenshot() calls fail on the S25.
                        onFrame(hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false))
                    } catch (throwable: Throwable) {
                        onFailure(null, throwable)
                    } finally {
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    onFailure(errorCode, null)
                }
            }
        )
    }
}
