package com.cmc.launcher.ui.control.gamepad

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movtery.inputmap.keycodes.ControlEventKeycode
import com.movtery.layer_controller.event.ClickEvent
import com.cmc.launcher.game.keycodes.mapToControlEvent
import com.cmc.launcher.game.sdl.SdlBridge
import com.cmc.launcher.game.sdl.handleGamepadMotionEvent
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.GamepadInputMode
import com.cmc.launcher.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN_SINGLE
import com.cmc.launcher.ui.control.event.LAUNCHER_EVENT_SCROLL_UP_SINGLE
import com.cmc.launcher.ui.control.joystick.allAction
import com.cmc.launcher.ui.control.joystick.directionMapping
import com.cmc.launcher.viewmodel.GamepadRemapperViewModel
import com.cmc.launcher.viewmodel.GamepadViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.libsdl.app.SDLActivity
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SimpleGamepadCapture(
    gamepadViewModel: GamepadViewModel
) {
    val view = LocalView.current
    val remapperViewModel: GamepadRemapperViewModel = viewModel()

    GamepadRemapperDialog(
        operation = remapperViewModel.uiOperation,
        changeOperation = { remapperViewModel.uiOperation = it },
        remapperViewModel = remapperViewModel,
        steps = buildRemapperSteps(
            a = true, b = true, x = true, y = true,
            start = true, select = true,
            leftJoystick = true, leftJoystickButton = true,
            rightJoystick = true, rightJoystickButton = true,
            leftShoulder = true, rightShoulder = true,
            leftTrigger = true, rightTrigger = true,
            dpad = true
        )
    )

    val uiOperation by rememberUpdatedState(remapperViewModel.uiOperation)
    fun isBinding() = uiOperation != GamepadRemapOperation.None

    DisposableEffect(view, gamepadViewModel) {
        val listener: (KeyEvent) -> Unit = { event ->
            if (isBinding()) {
                remapperViewModel.sendEvent(
                    GamepadRemapperViewModel.Event.Button(event.keyCode, event)
                )
            } else {
                val deviceName = event.getDeviceName()
                val remapper = remapperViewModel.findMapping(deviceName)
                if (remapper == null) {
                    remapperViewModel.startRemapperUI(deviceName)
                } else {
                    remapper.handleKeyEventInput(event, gamepadViewModel)
                }
            }
        }
        gamepadViewModel.registerKeyListener(listener)
        onDispose {
            gamepadViewModel.unregisterKeyListener(listener)
        }
    }

    DisposableEffect(view, gamepadViewModel) {
        val motionListener = View.OnGenericMotionListener { motionView, event ->
            if (event.isGamepadEvent() && AllSettings.gamepadControl.state && gamepadViewModel.checkModePrompt()) {

                true
            } else if (isBinding()) {
                remapperViewModel.sendEvent(
                    GamepadRemapperViewModel.Event.Axis(event)
                )
                true
            } else if (event.isGamepadEvent() && event.action == MotionEvent.ACTION_MOVE) {
                if (AllSettings.gamepadControl.state && AllSettings.gamepadInputMode.state == GamepadInputMode.SdlDirect) {
                    if (SdlBridge.sdlEnabled) {
                        gamepadViewModel.notifyActivity()
                        handleGamepadMotionEvent(event)
                        try {
                            SDLActivity.forwardGenericMotionToSDL(motionView, event)
                        } catch (_: UnsatisfiedLinkError) {

                        }
                    }
                    true
                } else {
                    val deviceName = event.getDeviceName()
                    val remapper = remapperViewModel.findMapping(deviceName)
                    if (remapper == null) {
                        remapperViewModel.startRemapperUI(deviceName)
                    } else {
                        remapper.handleMotionEventInput(event, gamepadViewModel)
                    }
                    true
                }
            } else false
        }

        view.setOnGenericMotionListener(motionListener)

        onDispose {
            view.setOnGenericMotionListener(null)
        }
    }

    LaunchedEffect(gamepadViewModel.gamepadEngaged) {
        withContext(Dispatchers.Default) {
            var lastPollTime = 0L
            while (true) {
                try {
                    ensureActive()
                    if (isBinding()) break

                    val pollLevel = gamepadViewModel.checkGamepadActive()
                    if (pollLevel == GamepadViewModel.PollLevel.Close) break

                    val now = System.nanoTime()
                    val deltaMs = if (lastPollTime == 0L) 0.0 else (now - lastPollTime) / 1_000_000.0
                    lastPollTime = now

                    gamepadViewModel.pollJoystick(deltaMs)
                    delay(pollLevel.delayMs.milliseconds)
                } catch (_: CancellationException) {
                    break
                }
            }
        }
    }
}

@Composable
private fun GamepadEventListener(
    gamepadViewModel: GamepadViewModel,
    listener: (GamepadViewModel.Event) -> Unit,
    onDisposeCallback: (() -> Unit)? = null
) {
    DisposableEffect(gamepadViewModel) {
        gamepadViewModel.addEventListener(listener)
        onDispose {
            onDisposeCallback?.invoke()
            gamepadViewModel.removeEventListener(listener)
        }
    }
}

@Composable
fun GamepadOnActionListener(
    gamepadViewModel: GamepadViewModel,
    onAction: () -> Unit
) {
    val currentOnAction by rememberUpdatedState(onAction)

    DisposableEffect(gamepadViewModel) {
        val listener: () -> Unit = {
            currentOnAction()
        }
        gamepadViewModel.registerActionListener(listener)
        onDispose {
            gamepadViewModel.unregisterActionListener(listener)
        }
    }
}

@Composable
fun GamepadKeyListener(
    gamepadViewModel: GamepadViewModel,
    isGrabbing: Boolean,
    onKeyEvent: (targets: List<ClickEvent>, pressed: Boolean) -> Unit
) {
    fun guessEvent(event: String): ClickEvent {
        return when (event) {
            ControlEventKeycode.GLFW_MOUSE_BUTTON_LEFT,
            ControlEventKeycode.GLFW_MOUSE_BUTTON_RIGHT,
            ControlEventKeycode.GLFW_MOUSE_BUTTON_MIDDLE
                -> ClickEvent(type = ClickEvent.Type.LauncherEvent, event)

            SPECIAL_KEY_MOUSE_SCROLL_UP -> ClickEvent(type = ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_UP_SINGLE)
            SPECIAL_KEY_MOUSE_SCROLL_DOWN -> ClickEvent(type = ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_DOWN_SINGLE)

            else -> ClickEvent(type = ClickEvent.Type.Key, event)
        }
    }

    val inGame by rememberUpdatedState(isGrabbing)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)

    val lastPressKey = remember { mutableStateMapOf<Int, List<ClickEvent>>() }
    val lastPressDpad = remember { mutableStateMapOf<DpadDirection, List<ClickEvent>>() }

    GamepadEventListener(
        gamepadViewModel = gamepadViewModel,
        listener = { event ->
            when (event) {
                is GamepadViewModel.Event.Button -> {
                    if (!event.pressed) {

                        lastPressKey[event.code]?.let { lastEvents ->
                            currentOnKeyEvent(lastEvents, false)
                            lastPressKey.remove(event.code)
                        }
                    } else {
                        gamepadViewModel.currentMapping?.findByCode(event.code, inGame)?.let { targets ->
                            val currentEvents = targets.map { guessEvent(it) }
                            lastPressKey[event.code] = currentEvents
                            currentOnKeyEvent(currentEvents, true)
                        }
                    }
                }
                is GamepadViewModel.Event.Dpad -> {
                    if (!event.pressed) {
                        lastPressDpad[event.direction]?.let { lastEvents ->
                            currentOnKeyEvent(lastEvents, false)
                            lastPressDpad.remove(event.direction)
                        }
                    } else {
                        gamepadViewModel.currentMapping?.findByDpad(event.direction, inGame)?.let { targets ->
                            val currentEvents = targets.map { guessEvent(it) }
                            lastPressDpad[event.direction] = currentEvents
                            currentOnKeyEvent(currentEvents, true)
                        }
                    }
                }
                else -> {}
            }
        },
        onDisposeCallback = {

            lastPressKey.forEach { (_, events) ->
                currentOnKeyEvent(events, false)
            }

            lastPressDpad.forEach { (_, events) ->
                currentOnKeyEvent(events, false)
            }

            lastPressKey.clear()
            lastPressDpad.clear()
        }
    )
}

@Composable
fun GamepadStickCameraListener(
    gamepadViewModel: GamepadViewModel,
    isGrabbing: Boolean,
    onOffsetEvent: (Offset) -> Unit
) {
    val currentIsGrabbing by rememberUpdatedState(isGrabbing)
    val joystickControlMode by rememberUpdatedState(AllSettings.joystickControlMode.state)
    val onOffsetEvent1 by rememberUpdatedState(onOffsetEvent)

    GamepadEventListener(
        gamepadViewModel = gamepadViewModel,
        listener = { event ->
            if (event is GamepadViewModel.Event.StickOffset) {
                if (currentIsGrabbing) {
                    val cameraStick = when (joystickControlMode) {
                        JoystickMode.RightMovement -> JoystickType.Left
                        JoystickMode.LeftMovement -> JoystickType.Right
                    }
                    if (event.joystickType == cameraStick) {
                        onOffsetEvent1(event.offset)
                    }
                } else {
                    onOffsetEvent1(event.offset)
                }
            }
        }
    )
}

@Composable
fun GamepadStickMovementListener(
    gamepadViewModel: GamepadViewModel,
    isGrabbing: Boolean,
    onKeyEvent: (event: ClickEvent, pressed: Boolean) -> Unit
) {
    val currentIsGrabbing by rememberUpdatedState(isGrabbing)
    val joystickControlMode by rememberUpdatedState(AllSettings.joystickControlMode.state)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)

    val allPressEvent = remember { mutableStateSetOf<String>() }

    fun sendKeyEvent(
        mcKey: String,
        defaultValue: String,
        pressed: Boolean
    ) {
        mapToControlEvent(mcKey, defaultValue)?.let { event ->
            if (pressed) {
                allPressEvent.add(event)
            } else {
                allPressEvent.remove(event)
            }

            currentOnKeyEvent(
                ClickEvent(type = ClickEvent.Type.Key, event),
                pressed
            )
        }
    }

    fun clearPressedEvent() {
        if (allPressEvent.isNotEmpty()) {
            allPressEvent.forEach { event ->
                currentOnKeyEvent(
                    ClickEvent(type = ClickEvent.Type.Key, event),
                    false
                )
            }
            allPressEvent.clear()
        }
    }

    GamepadEventListener(
        gamepadViewModel = gamepadViewModel,
        listener = { event ->
            if (event is GamepadViewModel.Event.StickDirection) {
                if (!currentIsGrabbing) {
                    clearPressedEvent()
                    return@GamepadEventListener
                }

                val movementStick = when (joystickControlMode) {
                    JoystickMode.RightMovement -> JoystickType.Right
                    JoystickMode.LeftMovement -> JoystickType.Left
                }

                if (event.joystickType != movementStick) return@GamepadEventListener

                allAction.forEach { (key, defaultValue) ->
                    sendKeyEvent(key, defaultValue, false)
                }

                directionMapping[event.direction]?.forEach { (key, defaultValue) ->
                    sendKeyEvent(key, defaultValue, true)
                }
            }
        },
        onDisposeCallback = {
            clearPressedEvent()
        }
    )
}

fun MotionEvent.getDeviceName(): String {
    return device.descriptor
}

fun KeyEvent.getDeviceName(): String {
    return device.descriptor
}

fun MotionEvent.isGamepadEvent(): Boolean {
    return isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_DPAD) ||
            isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)
}

fun MotionEvent.isJoystickMoving(): Boolean {
    return isFromSource(InputDevice.SOURCE_JOYSTICK) && action == MotionEvent.ACTION_MOVE
}

fun KeyEvent.isGamepadKeyEvent(): Boolean {
    val isGamepad = isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            (device != null && device.supportsSource(InputDevice.SOURCE_GAMEPAD))

    return isGamepad || isDpadKeyEvent()
}

private fun KeyEvent.isDpadKeyEvent(): Boolean {
    return (isFromSource(InputDevice.SOURCE_GAMEPAD) && isFromSource(InputDevice.SOURCE_DPAD)) &&
            device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
}

fun InputDevice?.isGamepadDevice(): Boolean {
    if (this == null) return false
    return this.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            this.supportsSource(InputDevice.SOURCE_JOYSTICK)
}

fun MotionEvent.findTriggeredAxis(): Int? {
    return supportedAxis.find { axis ->
        getAxisValue(axis) >= 0.85
    }
}
