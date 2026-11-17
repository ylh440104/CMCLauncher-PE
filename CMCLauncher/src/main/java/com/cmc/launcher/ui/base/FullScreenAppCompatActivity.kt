package com.cmc.launcher.ui.base

import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.abs

abstract class FullScreenAppCompatActivity : AbstractAppCompatActivity() {

    private var correctTouchCoordinates = false

    protected open fun isIgnoreNotch(): Boolean = true

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen()
    }

    @CallSuper
    override fun onPostResume() {
        super.onPostResume()
        applyFullscreen()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return super.dispatchTouchEvent(event)
        }

        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            correctTouchCoordinates = hasCoordinateSpaceMismatch(event)
        } else if (!correctTouchCoordinates &&
            action == MotionEvent.ACTION_POINTER_DOWN && hasCoordinateSpaceMismatch(event)) {

            correctTouchCoordinates = true
        }

        val handled = if (correctTouchCoordinates) {
            val corrected = event.copyWithConsistentCoordinates()
            try {
                super.dispatchTouchEvent(corrected)
            } finally {
                corrected.recycle()
            }
        } else {
            super.dispatchTouchEvent(event)
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            correctTouchCoordinates = false
        }
        return handled
    }

    private fun hasCoordinateSpaceMismatch(event: MotionEvent): Boolean {
        val origin = IntArray(2)
        window.decorView.getLocationOnScreen(origin)
        if (abs(event.rawX - (event.x + origin[0])) > 1f) return true
        if (abs(event.rawY - (event.y + origin[1])) > 1f) return true
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {

            for (index in 1 until event.pointerCount) {
                if (abs(event.getRawX(index) - (event.getX(index) + origin[0])) > 1f) return true
                if (abs(event.getRawY(index) - (event.getY(index) + origin[1])) > 1f) return true
            }
        }
        return false
    }

    private fun MotionEvent.copyWithConsistentCoordinates(): MotionEvent {
        val origin = IntArray(2)
        window.decorView.getLocationOnScreen(origin)
        val properties = Array(pointerCount) { MotionEvent.PointerProperties() }
        val coordinates = Array(pointerCount) { MotionEvent.PointerCoords() }
        for (index in 0 until pointerCount) {
            getPointerProperties(index, properties[index])
            getPointerCoords(index, coordinates[index])

            coordinates[index].x += origin[0]
            coordinates[index].y += origin[1]
        }
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            pointerCount,
            properties,
            coordinates,
            metaState,
            buttonState,
            xPrecision,
            yPrecision,
            deviceId,
            edgeFlags,
            source,
            flags,
        )
    }

    private fun applyFullscreen() {
        val decorView = window.decorView
        val visibilityChangeListener = OnSystemUiVisibilityChangeListener { visibility: Int ->
            if (!isInMultiWindowMode) {
                if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            )
                }
            } else {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        decorView.setOnSystemUiVisibilityChangeListener(visibilityChangeListener)
        visibilityChangeListener.onSystemUiVisibilityChange(decorView.systemUiVisibility)

        refreshIgnoreNotch()
    }

    fun refreshIgnoreNotch() {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            val mode = if (isIgnoreNotch()) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            val params = window.attributes

            if (params.layoutInDisplayCutoutMode != mode) {
                params.layoutInDisplayCutoutMode = mode

                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

                window.attributes = params
            }
        }
    }
}

@Composable
fun ObserveFullScreenSetting(fullScreen: Boolean) {
    val activity = LocalActivity.current as? FullScreenAppCompatActivity ?: return
    LaunchedEffect(fullScreen) {
        activity.refreshIgnoreNotch()
    }
}
