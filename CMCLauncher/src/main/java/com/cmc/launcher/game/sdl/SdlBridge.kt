package com.cmc.launcher.game.sdl

import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.Surface
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.annotation.MainThread
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.control.gamepad.isGamepadDevice
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.libsdl.app.HIDDeviceManager
import org.libsdl.app.SDL
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager
import org.libsdl.app.SDLSurface
import org.lwjgl.glfw.CallbackBridge
import java.lang.ref.WeakReference

@Keep
object SdlBridge {
    private const val TAG = "SdlBridge"

    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    private val _composeFocus = MutableStateFlow(0)
    val composeFocus = _composeFocus.asStateFlow()

    private var activityRef: WeakReference<Activity>? = null
    private var layoutRef: WeakReference<ViewGroup>? = null
    private var currentSurface: Surface? = null

    private var currentSource: Any? = null

    private var surfaceGeneration = 0L
    private var jniReady = false
    private var sdlInitialized = false

    @Volatile
    private var hidAcquired = false

    @Volatile
    private var inputListenerRegistered = false

    @JvmStatic
    @Synchronized
    fun setupJNI(): Boolean {
        if (jniReady) {
            return true
        }
        SDL.setupJNI()
        jniReady = true

        acquireHIDDeviceManager()

        if (SDLControllerManager.pollInputDevicesSafely("jni-setup")) {
            Logger.info(TAG, "Input devices synced after SDL JNI setup")
        }
        return true
    }

    private fun acquireHIDDeviceManager() {
        if (hidAcquired) return
        val context = SDL.getContext() ?: return
        hidAcquired = true
        try {
            HIDDeviceManager.acquire(context)
            Logger.info(TAG, "HIDDeviceManager acquired, USB/BT hotplug monitoring enabled")
        } catch (t: Throwable) {

            Logger.warning(TAG, "HIDDeviceManager acquire failed", t)
        }
    }

    private fun registerInputDeviceListener(context: Context) {
        if (inputListenerRegistered) return
        val inputManager = context.getSystemService(InputManager::class.java) ?: return
        inputListenerRegistered = true

        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                syncInputDevices("added", deviceId)
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                syncInputDevices("removed", deviceId)
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                syncInputDevices("changed", deviceId)
            }
        }
        inputManager.registerInputDeviceListener(listener, null)
    }

    private fun syncInputDevices(action: String, deviceId: Int) {

        if (!sdlEnabled) return
        val device = InputDevice.getDevice(deviceId)
        if (device != null && !device.isGamepadDevice()) return

        Logger.info(TAG, "Input device $action: ${device?.descriptor ?: deviceId}")
        SDLControllerManager.pollInputDevicesSafely(action)
    }

    @JvmStatic
    @Synchronized
    fun markSdlInitialized(): Boolean {
        if (sdlInitialized) return false
        sdlInitialized = true
        return true
    }

    @JvmStatic
    @Synchronized
    fun clearSdlInitialized() {
        sdlInitialized = false
    }

    @JvmStatic
    @Volatile
    var sdlEnabled: Boolean = false
        set(value) {
            field = value
            _enabled.value = value
        }

    @JvmStatic
    fun getSdlImeAutoShowEnabled(): Boolean = AllSettings.sdlAutoShowIme.state

    @JvmStatic
    @MainThread
    fun prepareSurface(activity: Activity, surface: Surface, layout: ViewGroup?, source: Any? = null) {
        activityRef = WeakReference(activity)
        layoutRef = WeakReference(layout)
        currentSurface = surface
        currentSource = source
        surfaceGeneration++

        registerInputDeviceListener(activity)

        if (SDLActivity.getSDLSurface() == null) {
            SDL.initialize()
            SDL.setContext(activity)
            SDLActivity.externalInitialize(SDLSurface(activity), layout, surface)
        } else {
            SDLSurface.setNativeSurface(surface)
        }
    }

    @JvmStatic
    @MainThread
    fun registerSurface(activity: Activity, surface: Surface, layout: ViewGroup?) {
        activityRef = WeakReference(activity)
        layoutRef = WeakReference(layout)
        currentSurface = surface
    }

    @JvmStatic
    fun requestComposeFocus() {
        _composeFocus.update { it + 1 }
    }

    @JvmStatic
    @MainThread
    fun beginSurfaceDestroy(source: Any?, surface: Surface?): Boolean {
        return source != null && currentSource === source && surface != null && currentSurface === surface
    }

    @JvmStatic
    @MainThread
    fun unregisterSurface(surface: Surface?) {
        if (surface != null && currentSurface === surface) {
            currentSurface = null
            currentSource = null
        }
    }

    @JvmStatic
    @MainThread
    @Synchronized
    fun reset() {
        currentSurface = null
        currentSource = null
        surfaceGeneration = 0L
        activityRef = null
        layoutRef = null
        jniReady = false
        sdlInitialized = false
        sdlEnabled = false
        CallbackBridge.clearSdlBridgeState()
        SDLSurface.clearNativeSurface()
        SDL.initialize()
    }

    @JvmStatic
    external fun initializeControllerSubsystems()

    @JvmStatic
    external fun isSdlRenderActive(): Boolean

    @JvmStatic
    external fun setNativeTextInputActive(active: Boolean): Boolean

    @JvmStatic
    external fun isNativeTextInputActive(): Boolean
}
