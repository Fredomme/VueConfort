package fr.vueconfort.app.mediaprojection

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal class GpuProjectionRenderer(
    private val captureWidth: Int,
    private val captureHeight: Int,
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val outputTexture: SurfaceTexture,
    private val onInputReady: (Surface) -> Unit,
    private val onMetrics: (RenderMetrics) -> Unit
) {
    private val thread = HandlerThread("VueConfortProjectionGL")
    private lateinit var handler: Handler
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var inputTextureId = 0
    private var inputTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var program = 0
    private val textureMatrix = FloatArray(16)
    private var running = false
    private var paused = false
    private var startedAt = 0L
    private var frames = 0L
    private var lastFrameAt = 0L
    private var minFpsWindow = Float.MAX_VALUE
    private var windowStartedAt = 0L
    private var windowFrames = 0
    private var positionLocation = 0
    private var textureLocation = 0
    private var matrixLocation = 0
    private var samplerLocation = 0
    @Volatile private var zoom = 2f
    @Volatile private var sourceOffsetX = 0f
    @Volatile private var sourceOffsetY = 0f

    private val vertexBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val textureBuffer = floatBuffer(FloatArray(8))
    private val textureCoordinates = FloatArray(8)

    fun start() {
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            initEgl()
            running = true
            startedAt = SystemClock.elapsedRealtime()
            windowStartedAt = startedAt
            createInputTexture()
        }
    }

    fun setPaused(value: Boolean) { paused = value }
    fun setZoom(value: Float) { zoom = value.coerceIn(1.25f, 5f) }
    fun moveSource(dx: Float, dy: Float) {
        sourceOffsetX = (sourceOffsetX + dx).coerceIn(-0.35f, 0.35f)
        sourceOffsetY = (sourceOffsetY + dy).coerceIn(-0.20f, 0.20f)
    }
    fun recenter() { sourceOffsetX = 0f; sourceOffsetY = 0f }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(eglDisplay, IntArray(2), 0, IntArray(2), 0))
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        check(EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, IntArray(1), 0))
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0], outputTexture, intArrayOf(EGL14.EGL_NONE), 0
        )
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        textureLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        matrixLocation = GLES20.glGetUniformLocation(program, "uTextureMatrix")
        samplerLocation = GLES20.glGetUniformLocation(program, "uTexture")
    }

    private fun createInputTexture() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        inputTextureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        inputTexture = SurfaceTexture(inputTextureId).apply {
            setDefaultBufferSize(captureWidth, captureHeight)
            setOnFrameAvailableListener({ handler.post(::renderFrame) }, handler)
        }
        inputSurface = Surface(inputTexture).also(onInputReady)
    }

    private fun renderFrame() {
        if (!running) return
        inputTexture?.updateTexImage()
        inputTexture?.getTransformMatrix(textureMatrix)
        if (paused) return
        val now = SystemClock.elapsedRealtime()
        val visibleWidth = (1f / zoom).coerceIn(0.2f, 0.8f)
        val visibleHeight = (0.5f / zoom).coerceIn(0.1f, 0.4f)
        val centerX = (0.5f + sourceOffsetX).coerceIn(visibleWidth / 2f, 1f - visibleWidth / 2f)
        val centerY = (0.75f + sourceOffsetY).coerceIn(0.5f + visibleHeight / 2f, 1f - visibleHeight / 2f)
        val left = centerX - visibleWidth / 2f
        val right = centerX + visibleWidth / 2f
        val top = centerY - visibleHeight / 2f
        val bottom = centerY + visibleHeight / 2f
        textureBuffer.position(0)
        textureCoordinates[0] = left
        textureCoordinates[1] = bottom
        textureCoordinates[2] = right
        textureCoordinates[3] = bottom
        textureCoordinates[4] = left
        textureCoordinates[5] = top
        textureCoordinates[6] = right
        textureCoordinates[7] = top
        textureBuffer.put(textureCoordinates).position(0)

        GLES20.glViewport(0, 0, outputWidth.coerceAtLeast(1), outputHeight.coerceAtLeast(1))
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        GLES20.glEnableVertexAttribArray(textureLocation)
        GLES20.glUniformMatrix4fv(matrixLocation, 1, false, textureMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureId)
        GLES20.glUniform1i(samplerLocation, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)

        frames++
        windowFrames++
        lastFrameAt = now
        val windowElapsed = now - windowStartedAt
        if (windowElapsed >= 1_000) {
            if (windowFrames > 1) {
                minFpsWindow = minOf(minFpsWindow, windowFrames * 1000f / windowElapsed)
            }
            windowFrames = 0
            windowStartedAt = now
        }
        if (frames == 1L || frames % 120L == 0L) emitMetrics(now)
    }

    private fun emitMetrics(now: Long = SystemClock.elapsedRealtime()) {
        val elapsed = (now - startedAt).coerceAtLeast(1)
        onMetrics(RenderMetrics(frames, frames * 1000f / elapsed, minFpsWindow.takeIf { it.isFinite() } ?: 0f))
    }

    fun stop() {
        if (!thread.isAlive) return
        handler.post {
            if (!running) return@post
            running = false
            emitMetrics()
            inputTexture?.setOnFrameAvailableListener(null)
            inputSurface?.release()
            inputTexture?.release()
            if (inputTextureId != 0) GLES20.glDeleteTextures(1, intArrayOf(inputTextureId), 0)
            if (program != 0) GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(eglDisplay)
            outputTexture.release()
            thread.quitSafely()
        }
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
            val status = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { GLES20.glGetShaderInfoLog(it) }
        }
        val vertexShader = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fragmentShader = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer = ByteBuffer
        .allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values).position(0)
        }

    data class RenderMetrics(val frames: Long, val averageFps: Float, val minimumFps: Float)

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTextureMatrix;
            varying vec2 vTexCoord;
            void main() { gl_Position = aPosition; vTexCoord = (uTextureMatrix * vec4(aTexCoord, 0.0, 1.0)).xy; }
        """
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;
            void main() { gl_FragColor = texture2D(uTexture, vTexCoord); }
        """
    }
}
