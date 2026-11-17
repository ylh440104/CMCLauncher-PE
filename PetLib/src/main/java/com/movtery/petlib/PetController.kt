package com.movtery.petlib

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import androidx.annotation.FloatRange
import androidx.core.graphics.createBitmap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI

private const val TAG = "PetLib"

private const val SURFACE_FAILURE_LIMIT = 10

private const val MIN_FRAME_INTERVAL_NANOS = 1_000_000_000L / 60L * 9L / 10L

private fun wrapPi(angle: Float): Float {
    val twoPi = (2 * PI).toFloat()
    val a = angle % twoPi
    return when {
        a > PI.toFloat() -> a - twoPi
        a < -PI.toFloat() -> a + twoPi
        else -> a
    }
}

class PetController(
    skin: ByteArray,
    arm: ArmModel?,
    private val defaultYaw: Float = 0f
) : AutoCloseable {
    private val thread = HandlerThread("PetLibRender").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var ptr: Long = PetLibNative.nativeCreate(skin, ArmModel.codeOf(arm))

    @Volatile
    private var released = false

    @Volatile
    private var yawTarget = 0f

    private var frameCallback: Choreographer.FrameCallback? = null

    private var backBuffer: FrameBuffer? = null
    private var frontBuffer: FrameBuffer? = null

    private val overwritePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    private var surfaceFailures = 0
    private var lastRenderNanos = 0L
    private var lastSlowLogNanos = 0L
    private var attachedSurface: Surface? = null
    private val surfaceRef = AtomicReference<Surface?>(null)
    private val frameInFlight = AtomicBoolean(false)
    private val widthHint = AtomicInteger(0)
    private val heightHint = AtomicInteger(0)

    init {
        setModelYaw(defaultYaw)
    }

    fun setSkin(
        skin: ByteArray,
        arm: ArmModel?,
        onError: ((String) -> Unit)? = null
    ) {
        post {
            PetLibNative.nativeSetSkin(ptr, skin, ArmModel.codeOf(arm))?.let { error ->
                Log.w(TAG, "Skin rejected: $error")
                onError?.invoke(error)
            }
        }
    }

    fun setCape(
        cape: ByteArray?,
        onError: ((String) -> Unit)? = null
    ) {
        post {
            PetLibNative.nativeSetCape(ptr, cape)?.let { error ->
                Log.w(TAG, "Cape rejected: $error")
                onError?.invoke(error)
            }
        }
    }

    fun setLighting(
        azimuth: Float,
        elevation: Float,
        brightness: Float,
        softness: Float
    ) {
        post { PetLibNative.nativeSetLighting(ptr, azimuth, elevation, brightness, softness) }
    }

    fun setSuperSample(@FloatRange(0.5, 2.0) value: Float) {
        post { PetLibNative.nativeSetSuperSample(ptr, value) }
    }

    fun setOuterLayer(enabled: Boolean) {
        post { PetLibNative.nativeSetOuterLayer(ptr, enabled) }
    }

    fun setTau(
        gaze: Float,
        yaw: Float,
        anim: Float,
        recenter: Float
    ) {
        post { PetLibNative.nativeSetTau(ptr, gaze, yaw, anim, recenter) }
    }

    fun setHeadRange(
        x: Float,
        y: Float
    ) {
        post { PetLibNative.nativeSetHeadRange(ptr, x, y) }
    }

    fun setSquashDepth(@FloatRange(.0, 30.0) depth: Float) {
        post { PetLibNative.nativeSetSquashDepth(ptr, depth) }
    }

    fun setGaze(target: Pair<Float, Float>?) {
        post {
            if (target == null) {
                PetLibNative.nativeSetGaze(ptr, 0f, 0f, false)
            } else {
                PetLibNative.nativeSetGaze(ptr, target.first, target.second, true)
            }
        }
    }

    fun setModelYaw(yaw: Float) {
        yawTarget = yaw
        post { PetLibNative.nativeSetYaw(ptr, yaw) }
    }

    fun rotateYaw(delta: Float) {
        val next = yawTarget + delta
        yawTarget = next
        post { PetLibNative.nativeSetYaw(ptr, next) }
    }

    fun recenterYaw() {
        post {
            val current = PetLibNative.nativeGetYaw(ptr)
            val next = current + wrapPi(defaultYaw - current)
            yawTarget = next
            PetLibNative.nativeSetYaw(ptr, next)
        }
    }

    fun getModelYaw(onResult: (Float) -> Unit) {
        post { onResult(PetLibNative.nativeGetYaw(ptr)) }
    }

    fun playAnimation(animation: PetAnimation, speed: Float = 1f, headFollow: Boolean = true) {
        post { PetLibNative.nativePlayAnimation(ptr, animation.id, speed, headFollow) }
    }

    fun stopAnimation() {
        post { PetLibNative.nativeStopAnimation(ptr) }
    }

    fun squashTap(now: Float) {
        post { PetLibNative.nativeSquashTap(ptr, now) }
    }

    fun playSwing(now: Float) {
        post { PetLibNative.nativePlaySwing(ptr, now) }
    }

    fun playInteract() {
        post { PetLibNative.nativePlayInteract(ptr) }
    }

    fun hitTest(x: Float, y: Float, onResult: (Boolean) -> Unit) {
        post { onResult(PetLibNative.nativeHitTest(ptr, x, y)) }
    }

    internal fun attachSurface(surface: Surface, width: Int, height: Int) {

        post {
            widthHint.set(width)
            heightHint.set(height)

            if (attachedSurface === surface && surfaceRef.get() != null) {
                startFrameLoop()
                return@post
            }

            surfaceRef.set(surface)
            attachedSurface = surface
            surfaceFailures = 0
            lastRenderNanos = 0L
            startFrameLoop()
        }
    }

    internal fun updateSurfaceSize(width: Int, height: Int) {
        widthHint.set(width)
        heightHint.set(height)
    }

    internal fun detachSurface() {
        post {
            if (surfaceRef.getAndSet(null) != null) {
                attachedSurface = null
            }
            stopFrameLoop()
        }
    }

    override fun close() {
        if (released) return
        released = true

        mainHandler.post {
            frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
            frameCallback = null
        }
        handler.post {
            surfaceRef.set(null)
            attachedSurface = null
            PetLibNative.nativeDestroy(ptr)
            ptr = 0
            backBuffer = null
            frontBuffer = null
        }
        handler.looper.quitSafely()
    }

    private fun post(block: () -> Unit) {
        if (released) return

        handler.post {
            if (!released && ptr != 0L) block()
        }
    }

    private fun startFrameLoop() {
        mainHandler.post {
            if (frameCallback != null || released) return@post
            val choreographer = Choreographer.getInstance()
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(nanos: Long) {
                    if (frameCallback !== this || released) return
                    if (frameInFlight.compareAndSet(false, true)) {
                        handler.post {
                            try {
                                renderOneFrame()
                            } finally {
                                frameInFlight.set(false)
                            }
                        }
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            frameCallback = callback
            choreographer.postFrameCallback(callback)
        }
    }

    private fun stopFrameLoop() {
        mainHandler.post {
            frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
            frameCallback = null
        }
    }

    private fun onSurfaceFailure(surface: Surface, error: Exception? = null) {
        surfaceFailures++
        if (error != null) {
            Log.w(TAG, "Frame failed: $error")
        }
        if (surfaceFailures >= SURFACE_FAILURE_LIMIT && surfaceRef.compareAndSet(surface, null)) {

            attachedSurface = null
            stopFrameLoop()
            Log.w(TAG, "Surface unusable after $surfaceFailures consecutive failures; frame loop stopped")
        }
    }

    private fun renderOneFrame() {
        val surface = surfaceRef.get() ?: return
        val width = widthHint.get()
        val height = heightHint.get()
        if (width <= 0 || height <= 0) return

        val startedNanos = System.nanoTime()
        if (lastRenderNanos != 0L && startedNanos - lastRenderNanos < MIN_FRAME_INTERVAL_NANOS) {
            return
        }

        val dt = if (lastRenderNanos == 0L) {
            1f / 60f
        } else {
            ((startedNanos - lastRenderNanos) / 1_000_000_000f).coerceIn(0.001f, 0.25f)
        }
        lastRenderNanos = startedNanos

        val canvas = try {
            surface.lockCanvas(null)
        } catch (e: IllegalStateException) {
            onSurfaceFailure(surface, e)
            return
        } catch (e: IllegalArgumentException) {
            onSurfaceFailure(surface, e)
            return
        } ?: run {
            onSurfaceFailure(surface)
            return
        }
        try {
            var back = backBuffer
            if (back == null || back.bitmap.width != width || back.bitmap.height != height) {
                back = FrameBuffer(createBitmap(width, height))
                backBuffer = back
            }

            PetLibNative.nativeRender(ptr, width, height, startedNanos / 1_000_000_000f, dt, back.bitmap)

            val previousFront = frontBuffer
            frontBuffer = back
            backBuffer = previousFront
            canvas.drawBitmap(back.bitmap, 0f, 0f, overwritePaint)
            surface.unlockCanvasAndPost(canvas)
        } catch (e: IllegalArgumentException) {

            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (suppressed: Exception) {
                e.addSuppressed(suppressed)
            }
            onSurfaceFailure(surface, e)
            return
        } catch (e: IllegalStateException) {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (suppressed: Exception) {
                e.addSuppressed(suppressed)
            }
            onSurfaceFailure(surface, e)
            return
        }
        surfaceFailures = 0

        val costMs = (System.nanoTime() - startedNanos) / 1_000_000L
        if (costMs > 100 && startedNanos - lastSlowLogNanos > 3_000_000_000L) {
            lastSlowLogNanos = startedNanos
            Log.i(TAG, "Slow frame: ${costMs}ms (surface ${width}x${height})")
        }
    }

    private class FrameBuffer(val bitmap: Bitmap)
}
