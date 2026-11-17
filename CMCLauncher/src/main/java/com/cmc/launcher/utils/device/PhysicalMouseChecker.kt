package com.cmc.launcher.utils.device

import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.cmc.launcher.utils.logging.Logger

private const val TAG = "PhysicalMouseChecker"

object PhysicalMouseChecker {

    var physicalMouseConnected = false
        private set

    fun initChecker(activity: Activity) {

        physicalMouseConnected = isPhysicalMouseConnected()
        Logger.info(TAG, "Initialization complete, physical mouse connection status: $physicalMouseConnected")

        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                if (deviceId.isMouseId()) {
                    Logger.info(TAG, "Physical mouse connected, deviceId: $deviceId")
                    physicalMouseConnected = true
                }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                if (deviceId.isMouseId()) {
                    Logger.info(TAG, "Physical mouse disconnected, deviceId: $deviceId")
                    physicalMouseConnected = false
                } else {
                    physicalMouseConnected = isPhysicalMouseConnected()
                    Logger.info(TAG, "Fallback check for physical mouse connection status: $physicalMouseConnected")
                }
            }

            override fun onInputDeviceChanged(deviceId: Int) {

            }
        }

        val inputManager = activity.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(listener, null)
    }
}

private fun Int.isMouseId(): Boolean {
    return InputDevice.getDevice(this)?.let { device ->
        device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
    } ?: false
}

private fun isPhysicalMouseConnected(): Boolean {
    return InputDevice.getDeviceIds()
        .takeIf { it.isNotEmpty() }
        ?.any { it.isMouseId() }
        ?: false
}
