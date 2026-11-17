package com.cmc.launcher.ui.control.mouse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cmc.launcher.bridge.CURSOR_DISABLED
import com.cmc.launcher.bridge.CURSOR_ENABLED
import com.cmc.launcher.bridge.ZLBridgeStates
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.MouseControlMode
import com.cmc.launcher.setting.enums.MouseTouchBlockMode
import com.cmc.launcher.ui.control.gamepad.GamepadStickCameraListener
import com.cmc.launcher.utils.device.PhysicalMouseChecker
import com.cmc.launcher.utils.file.ifExists
import com.cmc.launcher.viewmodel.GamepadViewModel

typealias CursorMode = Int

@Composable
fun SwitchableMouseLayout(
    modifier: Modifier = Modifier,
    screenSize: IntSize,
    cursorMode: CursorMode,
    controlMode: MouseControlMode = AllSettings.mouseControlMode.state,
    touchBlockMode: MouseTouchBlockMode = AllSettings.mouseTouchBlockMode.state,
    enableMouseClick: Boolean = AllSettings.enableMouseClick.state,
    longPressTimeoutMillis: Long = AllSettings.mouseLongPressDelay.state.toLong(),
    requestPointerCapture: Boolean = !AllSettings.physicalMouseMode.state,
    hideMouseInClickMode: Boolean = AllSettings.hideMouse.state,
    gamepadViewModel: GamepadViewModel? = null,
    onTouch: () -> Unit = {},
    onMouse: () -> Unit = {},
    onTap: (Offset) -> Unit = {},
    onCapturedTap: (Offset) -> Unit = {},
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onCapturedLongPress: () -> Unit = {},
    onCapturedLongPressEnd: () -> Unit = {},
    onPointerMove: (Offset) -> Unit = {},
    onCapturedMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (button: Int, pressed: Boolean) -> Unit = { _, _ -> },
    isMoveOnlyPointer: (PointerId) -> Boolean = { false },
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    enableScrollGesture: Boolean = false,
    onScrollGesture: (Offset) -> Unit = {},
    mouseSize: Dp = AllSettings.mouseSize.state.dp,
    cursorSensitivity: Int = AllSettings.cursorSensitivity.state,
    gamepadCursorSensitivity: Int = AllSettings.gamepadCursorSensitivity.state,
    gamepadCameraSensitivity: Int = AllSettings.gamepadCameraSensitivity.state
) {
    val screenWidth: Float = screenSize.width.toFloat()
    val screenHeight: Float = screenSize.height.toFloat()
    val centerPos = Offset(screenWidth / 2f, screenHeight / 2f)

    val speedFactor = remember(cursorSensitivity) { cursorSensitivity / 100f }
    val gamepadCursorSpeedFactor = remember(gamepadCursorSensitivity) { 19f * (gamepadCursorSensitivity / 100f) }
    val gamepadCameraSpeedFactor = remember(gamepadCameraSensitivity) { 18f * (gamepadCameraSensitivity / 100f) }

    val lastVirtualMousePos = remember { object { var value: Offset? = null } }

    val isCaptured by remember(cursorMode) {
        mutableStateOf(
            value = cursorMode == CURSOR_DISABLED
        )
    }

    var isPhysicalMouseMode by remember {
        mutableStateOf(
            if (PhysicalMouseChecker.physicalMouseConnected) {
                !requestPointerCapture
            } else {
                false
            }
        )
    }

    fun checkPhysicalMouseMode(using: Boolean) {
        isPhysicalMouseMode = !requestPointerCapture && using
    }

    var showMousePointer by remember {
        mutableStateOf(requestPointerCapture)
    }
    fun updateMousePointer(show: Boolean) {
        showMousePointer = show
    }
    LaunchedEffect(cursorMode, hideMouseInClickMode) {
        updateMousePointer(
            show = if (cursorMode == CURSOR_ENABLED) {
                when {

                    PhysicalMouseChecker.physicalMouseConnected && isPhysicalMouseMode -> requestPointerCapture

                    controlMode == MouseControlMode.CLICK -> !hideMouseInClickMode

                    else -> controlMode == MouseControlMode.SLIDE
                }
            } else false
        )
    }

    val requestPointerCapture1 by remember(isCaptured) {
        mutableStateOf(
            value = if (isCaptured) true
            else requestPointerCapture
        )
    }

    val textInputActive = rememberTextInputActive()
    val capturePointer = isCaptured || (requestPointerCapture1 && !textInputActive)

    fun updatePointerPos(pos: Offset) {
        lastVirtualMousePos.value = pos
        onPointerMove(pos)
    }
    var pointerPosition by remember {
        mutableStateOf(centerPos)
    }
    LaunchedEffect(isCaptured) {
        val pos = lastVirtualMousePos.value?.takeIf {

            isPhysicalMouseMode
        } ?: centerPos
        if (!isCaptured) updatePointerPos(pos)
        pointerPosition = pos
    }

    gamepadViewModel?.let { viewModel ->
        val isGrabbing = cursorMode == CURSOR_DISABLED
        GamepadStickCameraListener(
            gamepadViewModel = viewModel,
            isGrabbing = isGrabbing,
            onOffsetEvent = { offset ->
                if (isGrabbing) {
                    updateMousePointer(false)
                    onCapturedMove(
                        Offset(
                            x = offset.x * gamepadCameraSpeedFactor,
                            y = offset.y * gamepadCameraSpeedFactor
                        )
                    )
                } else {
                    updateMousePointer(true)
                    val newOffset = Offset(
                        x = (pointerPosition.x + (offset.x * gamepadCursorSpeedFactor)).coerceIn(0f, screenWidth),
                        y = (pointerPosition.y + (offset.y * gamepadCursorSpeedFactor)).coerceIn(0f, screenHeight)
                    )
                    pointerPosition = newOffset
                    updatePointerPos(newOffset)
                }
            }
        )
    }

    Box(modifier = modifier) {
        val cursorShape by ZLBridgeStates.cursorShape.collectAsStateWithLifecycle()

        if (showMousePointer) {
            MousePointer(
                modifier = Modifier.mouseFixedPosition(
                    mouseSize = mouseSize,
                    cursorShape = cursorShape,
                    pointerPosition = pointerPosition
                ),
                cursorShape = cursorShape,
                mouseSize = mouseSize,
                mouseFile = getMouseFile(cursorShape).ifExists(),
            )
        }

        TouchpadLayout(
            modifier = Modifier.fillMaxSize(),
            controlMode = if (cursorMode == CURSOR_ENABLED) {
                controlMode
            } else {

                MouseControlMode.SLIDE
            },
            touchBlockMode = touchBlockMode,
            cursorMode = cursorMode,
            enableMouseClick = enableMouseClick,
            longPressTimeoutMillis = longPressTimeoutMillis,
            requestPointerCapture = capturePointer,
            pointerIcon = cursorShape.composeIcon,
            onTouch = {
                onTouch()
                checkPhysicalMouseMode(false)
            },
            onMouse = {
                onMouse()
                checkPhysicalMouseMode(true)
            },
            onTap = { fingerPos ->
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        onCapturedTap(fingerPos)
                    }
                    CURSOR_ENABLED -> {
                        onTap(
                            if (controlMode == MouseControlMode.CLICK) {
                                updateMousePointer(!isCaptured && !hideMouseInClickMode)

                                pointerPosition = fingerPos
                                fingerPos
                            } else {
                                pointerPosition
                            }
                        )
                    }
                }
            },
            onLongPress = {
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        onCapturedLongPress()
                    }
                    CURSOR_ENABLED -> {
                        onLongPress()
                    }
                }
            },
            onLongPressEnd = {
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        onCapturedLongPressEnd()
                    }
                    CURSOR_ENABLED -> {
                        onLongPressEnd()
                    }
                }
            },
            onPointerMove = { offset, isMoveOnly ->
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        updateMousePointer(false)
                        onCapturedMove(offset)
                    }
                    CURSOR_ENABLED -> {
                        pointerPosition = if (isMoveOnly || controlMode == MouseControlMode.SLIDE) {
                            updateMousePointer(true)
                            Offset(
                                x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                                y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                            )
                        } else {
                            updateMousePointer(!hideMouseInClickMode)

                            offset
                        }
                        updatePointerPos(pointerPosition)
                    }
                }
            },
            onMouseMove = { offset ->
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        updateMousePointer(false)
                        onCapturedMove(offset)
                    }
                    CURSOR_ENABLED -> {
                        if (capturePointer) {
                            updateMousePointer(true)
                            pointerPosition = Offset(
                                x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                                y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                            )
                            updatePointerPos(pointerPosition)
                        } else {

                            updateMousePointer(false)
                            pointerPosition = offset
                            updatePointerPos(pointerPosition)
                        }
                    }
                }
            },
            onMouseScroll = onMouseScroll,
            onMouseButton = onMouseButton,
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer,
            enableScrollGesture = enableScrollGesture && cursorMode == CURSOR_ENABLED,
            onScrollGesture = onScrollGesture,
            requestFocusKey = cursorMode
        )
    }
}
