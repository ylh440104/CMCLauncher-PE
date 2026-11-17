package com.movtery.petlib

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI

enum class PetTapReaction {

    None,

    Squash,

    Interact
}

@Composable
fun PetPreview(
    controller: PetController,
    modifier: Modifier = Modifier,
    tapReaction: PetTapReaction = PetTapReaction.Squash,
    dragYaw: Boolean = true,
) {
    val gestureModifier = Modifier.let {
        if (tapReaction != PetTapReaction.None) it.then(Modifier.pointerInput(controller, tapReaction) {
            detectTapGestures(
                onPress = { offset ->
                    if (size.width > 0 && size.height > 0) {
                        controller.hitTest(
                            offset.x / size.width,
                            offset.y / size.height
                        ) { hit ->
                            if (hit) {
                                when (tapReaction) {
                                    PetTapReaction.Squash -> controller.squashTap(System.nanoTime() / 1_000_000_000f)
                                    PetTapReaction.Interact -> controller.playInteract()
                                    PetTapReaction.None -> Unit
                                }
                            }
                        }
                    }
                }
            )
        }) else it
    }.let {
        if (dragYaw) it.then(Modifier.pointerInput(controller) {

            detectDragGestures { change, dragAmount ->
                change.consume()
                if (size.width > 0) {
                    controller.rotateYaw(dragAmount.x / size.width * 2f * PI.toFloat())
                }
            }
        }) else it
    }

    var textureView by remember { mutableStateOf<TextureView?>(null) }
    AndroidView(
        modifier = modifier.then(gestureModifier),
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false
                textureView = this
            }
        }
    )

    DisposableEffect(controller, textureView) {
        val view = textureView
        if (view == null) {
            onDispose { }
        } else {
            val listener = TextureLifecycle(controller)
            view.surfaceTextureListener = listener

            val attachStateListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val st = (v as? TextureView)?.surfaceTexture ?: return
                    listener.ensureAttached(st, v.width, v.height)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    controller.detachSurface()
                }
            }
            view.addOnAttachStateChangeListener(attachStateListener)

            view.surfaceTexture?.let { st ->
                listener.ensureAttached(st, view.width, view.height)
            }
            onDispose {
                view.removeOnAttachStateChangeListener(attachStateListener)
                view.surfaceTextureListener = null
                controller.detachSurface()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller, textureView) {
        val observer = LifecycleEventObserver { _, event ->
            val view = textureView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    controller.detachSurface()
                }
                Lifecycle.Event.ON_RESUME -> {
                    val st = view.surfaceTexture ?: return@LifecycleEventObserver
                    view.surfaceTextureListener?.let { listener ->
                        (listener as? TextureLifecycle)?.ensureAttached(
                            st, view.width, view.height
                        )
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private class TextureLifecycle(private val controller: PetController) :
    TextureView.SurfaceTextureListener {
    private var surface: Surface? = null

    fun ensureAttached(st: SurfaceTexture, width: Int, height: Int) {
        val existing = surface
        if (existing != null) {
            controller.attachSurface(existing, width, height)
        } else {
            Surface(st).let { wrapped ->
                surface = wrapped
                controller.attachSurface(wrapped, width, height)
            }
        }
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        ensureAttached(st, width, height)
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        controller.updateSurfaceSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        controller.detachSurface()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
}
