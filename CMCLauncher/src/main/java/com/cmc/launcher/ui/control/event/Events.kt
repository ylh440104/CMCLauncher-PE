package com.cmc.launcher.ui.control.event

import com.movtery.inputmap.keycodes.ControlEventKeycode
import org.lwjgl.glfw.CallbackBridge

fun lwjglEvent(
    eventKey: String,
    isMouse: Boolean,
    isPressed: Boolean
) {
    val keycode: Int = ControlEventKeycode.getKeycodeFromEvent(eventKey)?.toInt() ?: return

    if (isMouse) {
        CallbackBridge.sendMouseButton(keycode, isPressed)
    } else {
        CallbackBridge.sendKeyPress(keycode, CallbackBridge.getCurrentMods(), isPressed)
        CallbackBridge.setModifiers(keycode, isPressed)
    }
}

const val LAUNCHER_EVENT_SWITCH_IME = "launcher.event.switch_ime"

const val LAUNCHER_EVENT_SWITCH_MENU = "launcher.event.switch_menu"

const val LAUNCHER_EVENT_SCROLL_UP = "launcher.event.scroll_up"

const val LAUNCHER_EVENT_SCROLL_UP_SINGLE = "launcher.event.scroll_up.single"

const val LAUNCHER_EVENT_SCROLL_DOWN = "launcher.event.scroll_down"

const val LAUNCHER_EVENT_SCROLL_DOWN_SINGLE = "launcher.event.scroll_down.single"

fun launcherEvent(
    eventKey: String,
    isPressed: Boolean,
    onSwitchIME: () -> Unit,
    onSwitchMenu: () -> Unit,
    onSingleScrollUp: () -> Unit,
    onSingleScrollDown: () -> Unit,
    onLongScrollUp: () -> Unit,
    onLongScrollUpCancel: () -> Unit,
    onLongScrollDown: () -> Unit,
    onLongScrollDownCancel: () -> Unit
) {
    if (eventKey.startsWith("GLFW_MOUSE_", false)) {

        lwjglEvent(eventKey = eventKey, isMouse = true, isPressed = isPressed)
    } else {
        if (isPressed) {
            when (eventKey) {
                LAUNCHER_EVENT_SWITCH_IME -> onSwitchIME()
                LAUNCHER_EVENT_SWITCH_MENU -> onSwitchMenu()
                LAUNCHER_EVENT_SCROLL_UP_SINGLE -> onSingleScrollUp()
                LAUNCHER_EVENT_SCROLL_DOWN_SINGLE -> onSingleScrollDown()
            }
        }
        when (eventKey) {
            LAUNCHER_EVENT_SCROLL_UP -> {
                if (isPressed) {
                    onLongScrollUp()
                } else {
                    onLongScrollUpCancel()
                }
            }
            LAUNCHER_EVENT_SCROLL_DOWN -> {
                if (isPressed) {
                    onLongScrollDown()
                } else {
                    onLongScrollDownCancel()
                }
            }
        }
    }
}
