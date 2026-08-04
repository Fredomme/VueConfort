package fr.vueconfort.app.mediaprojection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import fr.vueconfort.app.magnifier.ScreenMagnifierService

class MediaProjectionMagnifierService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var renderer: GpuProjectionRenderer? = null
    private var overlay: GpuMagnifierOverlay? = null
    private var stopping = false
    private var paused = false
    private var pendingZoom = 2f
    private var pendingOptical = OpticalGpuParameters()
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection.onStop")
            mainHandler.post { stopExperiment(stopProjection = false) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopExperiment(true)
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_START -> startExperiment(intent)
        }
        return START_NOT_STICKY
    }

    private fun startExperiment(intent: Intent) {
        if (projection != null || stopping) return
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: return stopExperiment(false)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val manager = getSystemService(MediaProjectionManager::class.java)
        val activeProjection = manager.getMediaProjection(resultCode, data) ?: return stopExperiment(false)
        activeProjection.registerCallback(projectionCallback, mainHandler)
        projection = activeProjection
        val accessibilityService = ScreenMagnifierService.activeInstanceForExperiment()
            ?: return stopExperiment(true)
        overlay = GpuMagnifierOverlay(
            service = accessibilityService,
            onSurfaceReady = { output, width, height -> createGpuPipeline(output, width, height) },
            onClose = { stopExperiment(true) },
            onPauseChanged = { setPaused(it) },
            onZoomChanged = { pendingZoom = it; renderer?.setZoom(it) },
            onOpticalChanged = { pendingOptical = it; renderer?.setOptical(it) },
            onMoveSource = { x, y -> renderer?.moveSource(x, y) },
            onRecenter = { renderer?.recenter() }
        ).also { it.show() }
    }

    private fun createGpuPipeline(output: android.graphics.SurfaceTexture, outputWidth: Int, outputHeight: Int) {
        if (stopping || renderer != null) return
        val (displayWidth, displayHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
                .let { it.width() to it.height() }
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        val captureWidth = (displayWidth * 0.75f).toInt().coerceAtLeast(2)
        val captureHeight = (displayHeight * 0.75f).toInt().coerceAtLeast(2)
        val density = resources.displayMetrics.densityDpi
        renderer = GpuProjectionRenderer(
            captureWidth,
            captureHeight,
            outputWidth,
            outputHeight,
            output,
            onInputReady = { input ->
                virtualDisplay = projection?.createVirtualDisplay(
                    "VueConfortMagnifier",
                    captureWidth,
                    captureHeight,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    input,
                    null,
                    mainHandler
                )
            },
            onMetrics = { metrics ->
                Log.i(TAG, "frames=${metrics.frames} avgFps=${metrics.averageFps} minFps=${metrics.minimumFps}")
            }
        ).also { it.setZoom(pendingZoom); it.setOptical(pendingOptical); it.start() }
    }

    private fun setPaused(value: Boolean) {
        paused = value
        renderer?.setPaused(value)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun stopExperiment(stopProjection: Boolean) {
        if (stopping) return
        stopping = true
        virtualDisplay?.release()
        virtualDisplay = null
        renderer?.stop()
        renderer = null
        overlay?.close()
        overlay = null
        projection?.unregisterCallback(projectionCallback)
        if (stopProjection) projection?.stop()
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopExperiment(true)
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Loupe VueConfort fluide", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(): Notification {
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = if (paused) "Reprendre" else "Pause"
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Loupe VueConfort active")
            .setContentText("Flux visuel temporaire traité localement")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, toggleLabel, serviceIntent(toggleAction, 1)).build())
            .addAction(Notification.Action.Builder(null, "Fermer", serviceIntent(ACTION_STOP, 2)).build())
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, MediaProjectionMagnifierService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_START = "fr.vueconfort.app.debug.mediaprojection.START"
        const val ACTION_STOP = "fr.vueconfort.app.debug.mediaprojection.STOP"
        const val ACTION_PAUSE = "fr.vueconfort.app.debug.mediaprojection.PAUSE"
        const val ACTION_RESUME = "fr.vueconfort.app.debug.mediaprojection.RESUME"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "vueconfort_projection_experiment"
        private const val NOTIFICATION_ID = 4812
        private const val TAG = "VueConfortProjection"
    }
}
