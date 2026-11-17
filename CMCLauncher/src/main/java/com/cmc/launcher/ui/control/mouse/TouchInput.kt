package com.cmc.launcher.ui.control.mouse

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cmc.launcher.bridge.CURSOR_DISABLED
import com.cmc.launcher.bridge.CURSOR_ENABLED
import com.cmc.launcher.game.sdl.SdlBridge
import com.cmc.launcher.setting.enums.MouseControlMode
import com.cmc.launcher.setting.enums.MouseTouchBlockMode
import com.cmc.launcher.ui.components.FocusableBox
import com.cmc.launcher.ui.control.input.TouchCharInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

private data class DragState(
    var isDragging: Boolean = false,
    var longPressTriggered: Boolean = false,
    val startPosition: Offset
)

@Composable
fun rememberTextInputActive(): Boolean {

    val composeFocusCount by SdlBridge.composeFocus.collectAsStateWithLifecycle()
    return remember(composeFocusCount) {
        TouchCharInput.isActive() || SDLActivity.isUsingSDLTextEdit()
    }
}

private const val SCROLL_GESTURE_DOWN_WINDOW_MILLIS = 200L

private fun isHalfScreenBlocked(
    mode: MouseTouchBlockMode,
    cursorMode: CursorMode,
    position: Offset,
    layoutWidth: Int
): Boolean {

    if (cursorMode != CURSOR_DISABLED) return false
    if (layoutWidth <= 0) return false

    val halfWidth = layoutWidth / 2f
    return when (mode) {
        MouseTouchBlockMode.NONE -> false
        MouseTouchBlockMode.LEFT -> position.x < halfWidth
        MouseTouchBlockMode.RIGHT -> position.x >= halfWidth
    }
}

private val SCROLL_GESTURE_SCROLL_DISTANCE = 6.dp

@Composable
fun TouchpadLayout(
    modifier: Modifier = Modifier,
    controlMode: MouseControlMode = MouseControlMode.SLIDE,
    touchBlockMode: MouseTouchBlockMode = MouseTouchBlockMode.NONE,
    cursorMode: CursorMode = CURSOR_ENABLED,
    enableMouseClick: Boolean = true,
    longPressTimeoutMillis: Long = -1L,
    requestPointerCapture: Boolean = true,
    pointerIcon: PointerIcon = PointerIcon.Default,
    onTouch: () -> Unit = {},
    onMouse: () -> Unit = {},
    onTap: (Offset) -> Unit = {},
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onPointerMove: (Offset, isMoveOnly: Boolean) -> Unit = { _, _ -> },
    onMouseMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (button: Int, pressed: Boolean) -> Unit = { _, _ -> },
    isMoveOnlyPointer: (PointerId) -> Boolean = { false },
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    enableScrollGesture: Boolean = false,
    onScrollGesture: (Offset) -> Unit = {},
    inputChange: Array<out Any> = arrayOf(Unit),
    requestFocusKey: Any? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val composeFocusCount by SdlBridge.composeFocus.collectAsStateWithLifecycle()

    val currentOnTouch by rememberUpdatedState(onTouch)
    val currentControlMode by rememberUpdatedState(controlMode)
    val currentTouchBlockMode by rememberUpdatedState(touchBlockMode)
    val currentCursorMode by rememberUpdatedState(cursorMode)
    val currentEnableMouseClick by rememberUpdatedState(enableMouseClick)
    val currentLongPressTimeoutMillis by rememberUpdatedState(longPressTimeoutMillis)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnPointerMove by rememberUpdatedState(onPointerMove)
    val currentEnableScrollGesture by rememberUpdatedState(enableScrollGesture)
    val currentOnScrollGesture by rememberUpdatedState(onScrollGesture)
    val currentScrollGestureDistancePx by rememberUpdatedState(
        with(LocalDensity.current) { SCROLL_GESTURE_SCROLL_DISTANCE.toPx() }
    )

    FocusableBox(
        modifier = modifier
            .hoverable(interactionSource)
            .pointerHoverIcon(pointerIcon)
            .pointerInput(*inputChange) {
                coroutineScope {

                    val occupiedPointers = mutableSetOf<PointerId>()

                    var activePointer: PointerId? = null
                    val longPressJobs = mutableMapOf<PointerId, Job>()

                    val dragStates = mutableMapOf<PointerId, DragState>()

                    val moveOnlyPointers = mutableSetOf<PointerId>()

                    val scrollGesturePointers = mutableSetOf<PointerId>()

                    var scrollGestureActive = false

                    var scrollGestureRemainder = Offset.Zero

                    var activePointerDownTime = 0L

                    fun resetTouchState() {
                        activePointer = null
                        dragStates.clear()
                        longPressJobs.values.forEach { it.cancel() }
                        longPressJobs.clear()
                        occupiedPointers.forEach { onReleasePointer(it) }
                        occupiedPointers.clear()
                        moveOnlyPointers.clear()
                        scrollGesturePointers.clear()
                        scrollGestureActive = false
                        scrollGestureRemainder = Offset.Zero
                    }

                    awaitPointerEventScope {
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes
                                    .filter { it.changedToDown() }
                                    .forEach { change ->
                                        if (change.type != PointerType.Touch) {
                                            return@forEach
                                        }

                                        if (isHalfScreenBlocked(currentTouchBlockMode, currentCursorMode, change.position, size.width)) {
                                            return@forEach
                                        }

                                        currentOnTouch()

                                        val pointerId = change.id

                                        val isMoveOnly = isMoveOnlyPointer(pointerId)

                                        if (isMoveOnly) {

                                            if (activePointer == null) {
                                                activePointer = pointerId
                                                dragStates[pointerId] = DragState(startPosition = change.position)
                                            } else {

                                                moveOnlyPointers.add(pointerId)
                                            }
                                        } else if (activePointer == null && !change.isConsumed) {

                                            if (pointerId !in occupiedPointers) {
                                                onOccupiedPointer(pointerId)
                                                occupiedPointers.add(pointerId)
                                            }

                                            activePointer = pointerId
                                            activePointerDownTime = change.uptimeMillis

                                            dragStates[pointerId] =
                                                DragState(startPosition = change.position)

                                            if (currentControlMode == MouseControlMode.SLIDE && currentEnableMouseClick) {
                                                longPressJobs[pointerId] = launch {

                                                    val timeout =
                                                        if (currentLongPressTimeoutMillis > 0) {
                                                            currentLongPressTimeoutMillis
                                                        } else {
                                                            viewConfiguration.longPressTimeoutMillis
                                                        }
                                                    delay(timeout.milliseconds)

                                                    if (activePointer == pointerId && dragStates[pointerId]?.isDragging != true) {
                                                        dragStates[pointerId]?.longPressTriggered = true
                                                        currentOnLongPress()
                                                    }
                                                }
                                            }

                                            if (currentControlMode == MouseControlMode.CLICK) {

                                                currentOnPointerMove(change.position, false)
                                            }
                                        } else if (currentEnableScrollGesture && scrollGesturePointers.isEmpty() && !change.isConsumed) {

                                            activePointer?.takeIf { it != pointerId }?.let { first ->
                                                if (!isMoveOnlyPointer(first)
                                                    && change.uptimeMillis - activePointerDownTime <= SCROLL_GESTURE_DOWN_WINDOW_MILLIS
                                                    && dragStates[first]?.let { !it.isDragging && !it.longPressTriggered } == true
                                                ) {
                                                    longPressJobs.remove(first)?.cancel()

                                                    dragStates[first]?.isDragging = true

                                                    if (pointerId !in occupiedPointers) {
                                                        onOccupiedPointer(pointerId)
                                                        occupiedPointers.add(pointerId)
                                                    }
                                                    scrollGesturePointers.add(first)
                                                    scrollGesturePointers.add(pointerId)
                                                    scrollGestureActive = true
                                                    scrollGestureRemainder = Offset.Zero
                                                }
                                            }
                                        }
                                    }

                                if (scrollGestureActive) {
                                    var deltaSum = Offset.Zero
                                    event.changes
                                        .filter { it.id in scrollGesturePointers && it.positionChanged() && !it.isConsumed }
                                        .forEach { change ->
                                            deltaSum += change.positionChange()
                                            change.consume()
                                        }

                                    if (deltaSum != Offset.Zero) {
                                        val scaled = deltaSum / scrollGesturePointers.size.coerceAtLeast(1).toFloat()
                                        val hScroll = scaled.x / currentScrollGestureDistancePx + scrollGestureRemainder.x
                                        val vScroll = scaled.y / currentScrollGestureDistancePx + scrollGestureRemainder.y
                                        val hRound = hScroll.toInt()
                                        val vRound = vScroll.toInt()

                                        if (hRound != 0 || vRound != 0) {
                                            currentOnScrollGesture(Offset(hScroll, vScroll))
                                        }
                                        scrollGestureRemainder = Offset(
                                            x = hScroll - hRound,
                                            y = vScroll - vRound
                                        )
                                    }
                                }

                                activePointer?.takeIf { it !in scrollGesturePointers }?.let { pointerId ->
                                    event.changes
                                        .firstOrNull { it.id == pointerId && it.positionChanged() && !it.isConsumed }
                                        ?.let { moveChange ->
                                            val dragState = dragStates[pointerId] ?: return@let

                                            val isMoveOnly = isMoveOnlyPointer(pointerId)

                                            if (isMoveOnly) {
                                                dragState.isDragging = true
                                                val delta = moveChange.positionChange()
                                                currentOnPointerMove(delta, true)
                                            } else {
                                                when (currentControlMode) {
                                                    MouseControlMode.SLIDE -> {
                                                        if (currentEnableMouseClick) {
                                                            val distanceFromStart =
                                                                (moveChange.position - dragState.startPosition).getDistance()

                                                            if (distanceFromStart > viewConfiguration.touchSlop && !dragState.isDragging) {

                                                                dragState.isDragging = true
                                                                longPressJobs.remove(pointerId)
                                                                    ?.cancel()
                                                            }

                                                            if (dragState.isDragging || dragState.longPressTriggered) {
                                                                val delta = moveChange.positionChange()
                                                                currentOnPointerMove(delta, false)
                                                            }
                                                        } else {
                                                            dragState.isDragging = true
                                                            val delta = moveChange.positionChange()
                                                            currentOnPointerMove(delta, false)
                                                        }
                                                    }

                                                    MouseControlMode.CLICK -> {
                                                        if (!dragState.longPressTriggered) {
                                                            dragState.longPressTriggered = true
                                                            longPressJobs.remove(pointerId)?.cancel()
                                                            currentOnLongPress()
                                                        }
                                                        currentOnPointerMove(moveChange.position, false)
                                                    }
                                                }
                                            }

                                            moveChange.consume()
                                        }
                                }

                                event.changes
                                    .filter { moveOnlyPointers.contains(it.id) && it.positionChanged() && !it.isConsumed }
                                    .forEach { moveChange ->
                                        val pointerId = moveChange.id
                                        val dragState = dragStates[pointerId]
                                        if (dragState != null) {
                                            dragState.isDragging = true
                                            val delta = moveChange.positionChange()
                                            currentOnPointerMove(delta, true)
                                            moveChange.consume()
                                        }
                                    }

                                event.changes
                                    .filter { it.changedToUpIgnoreConsumed() }
                                    .forEach { change ->
                                        val pointerId = change.id

                                        val isMoveOnly = isMoveOnlyPointer(pointerId)

                                        longPressJobs.remove(pointerId)?.cancel()
                                        val dragState = dragStates.remove(pointerId)

                                        if (pointerId in scrollGesturePointers) {

                                            scrollGesturePointers.remove(pointerId)
                                            scrollGestureActive = false
                                            if (pointerId == activePointer) {
                                                activePointer = null
                                            }
                                        } else if (pointerId == activePointer) {
                                            if (!isMoveOnly) {
                                                if (dragState?.longPressTriggered == true) {
                                                    currentOnLongPressEnd()
                                                } else {
                                                    when (currentControlMode) {
                                                        MouseControlMode.SLIDE -> {
                                                            if (currentEnableMouseClick && dragState?.isDragging != true) {
                                                                currentOnTap(change.position)
                                                            }
                                                        }

                                                        MouseControlMode.CLICK -> {

                                                            currentOnTap(change.position)
                                                        }
                                                    }
                                                }
                                            }

                                            activePointer = null
                                        }

                                        moveOnlyPointers.remove(pointerId)

                                        if (!isMoveOnly && pointerId in occupiedPointers) {
                                            occupiedPointers.remove(pointerId)
                                            onReleasePointer(pointerId)
                                        }
                                    }

                                if (!event.changes.any { it.pressed }) {
                                    resetTouchState()
                                }
                            }
                        } finally {
                            resetTouchState()
                        }
                    }
                }
            }
            .then(
                Modifier.mouseEventModifier(
                    disabled = requestPointerCapture,
                    inputChange = inputChange,
                    onMouse = onMouse,
                    onMouseMove = onMouseMove,
                    onMouseScroll = onMouseScroll,
                    onMouseButton = onMouseButton
                )
            ),
        requestKey = requestFocusKey to composeFocusCount,
        canRequestFocus = { !SDLActivity.isUsingSDLTextEdit() && !TouchCharInput.isActive() }
    )

    SimpleMouseCapture(
        enabled = requestPointerCapture,
        onMouse = onMouse,
        onMouseMove = onMouseMove,
        onMouseScroll = onMouseScroll,
        onMouseButton = onMouseButton
    )
}

@Composable
private fun SimpleMouseCapture(
    enabled: Boolean,
    onMouse: () -> Unit,
    onMouseMove: (Offset) -> Unit,
    onMouseScroll: (Offset) -> Unit,
    onMouseButton: (button: Int, pressed: Boolean) -> Unit
) {
    val view by rememberUpdatedState(LocalView.current)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnMouseMove by rememberUpdatedState(onMouseMove)
    val currentOnMouseScroll by rememberUpdatedState(onMouseScroll)
    val currentOnMouseButton by rememberUpdatedState(onMouseButton)

    fun syncCaptureState() {
        if (enabled) {
            if (view.hasWindowFocus()) {
                view.requestPointerCapture()
            }
        } else {
            view.releasePointerCapture()
        }
    }

    val composeFocus by SdlBridge.composeFocus.collectAsStateWithLifecycle()
    LaunchedEffect(composeFocus) {
        syncCaptureState()
    }

    DisposableEffect(view, enabled) {
        view.setOnCapturedPointerListener(null)
        val physicalButtons = CapturedMouseButtons { button, pressed ->
            currentOnMouseButton(button, pressed)
        }

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (enabled && hasFocus) {
                view.requestPointerCapture()
            } else if (!hasFocus) {
                physicalButtons.reset()
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        syncCaptureState()

        if (enabled) {

            var touchpadLastPos: Offset? = null
            val twoFingerGesture = TouchpadTwoFingerGesture()

            var touchpadLeftPressed = false

            var touchpadLeftSuppressed = false

            val pointerListener = View.OnCapturedPointerListener { _, event ->
                val isTouchpad = event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
                if (!isTouchpad) {
                    physicalButtons.onEvent(event.actionMasked, event.actionButton, event.buttonState)
                }

                if (isTouchpad) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_HOVER_ENTER -> touchpadLastPos = Offset(event.x, event.y)

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_POINTER_UP,
                        MotionEvent.ACTION_HOVER_EXIT -> touchpadLastPos = null
                    }
                }

                if (isTouchpad) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN ->
                            if (event.pointerCount >= 2 && !twoFingerGesture.isActive()) {
                                twoFingerGesture.start(event)
                                if (touchpadLeftPressed) {

                                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, false)
                                    touchpadLeftPressed = false
                                    touchpadLeftSuppressed = true
                                }
                            }

                        MotionEvent.ACTION_MOVE ->
                            if (twoFingerGesture.isActive()) {
                                if (event.pointerCount >= 2) {
                                    twoFingerGesture.onMove(event)?.let { notches ->
                                        if (notches.x != 0f || notches.y != 0f) {
                                            currentOnMouseScroll(notches)
                                        }
                                    }
                                }

                                return@OnCapturedPointerListener true
                            }

                        MotionEvent.ACTION_POINTER_UP ->
                            if (twoFingerGesture.isActive() && twoFingerGesture.onFingerUp(event)) {
                                currentOnMouseButton(MotionEvent.BUTTON_SECONDARY, true)
                                currentOnMouseButton(MotionEvent.BUTTON_SECONDARY, false)
                            }

                        MotionEvent.ACTION_UP ->
                            if (twoFingerGesture.isActive()) {
                                twoFingerGesture.end()
                                if (touchpadLeftSuppressed) {
                                    touchpadLeftSuppressed = false
                                    return@OnCapturedPointerListener true
                                }
                            }

                        MotionEvent.ACTION_CANCEL ->
                            if (twoFingerGesture.isActive()) {
                                twoFingerGesture.end()
                                touchpadLeftPressed = false
                                touchpadLeftSuppressed = false
                            }
                    }
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                        var deltaX = 0f
                        var deltaY = 0f

                        val relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                        val relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                        if (isTouchpad) {
                            if (relX != 0f || relY != 0f) {

                                deltaX += relX
                                deltaY += relY
                            } else {
                                touchpadLastPos?.let { last ->
                                    deltaX += event.x - last.x
                                    deltaY += event.y - last.y
                                }
                                touchpadLastPos = Offset(event.x, event.y)
                            }
                        } else {
                            deltaX += if (relX != 0f) relX else event.x
                            deltaY += if (relY != 0f) relY else event.y
                        }

                        val historySize = event.historySize
                        for (i in 0 until historySize) {
                            deltaX += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
                            deltaY += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
                        }

                        currentOnMouse()
                        currentOnMouseMove(Offset(deltaX, deltaY))
                        true
                    }
                    MotionEvent.ACTION_SCROLL -> {
                        currentOnMouseScroll(
                            Offset(
                                event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                                event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                            )
                        )
                        true
                    }
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                        if (isTouchpad && event.actionMasked == MotionEvent.ACTION_DOWN) {
                            touchpadLeftPressed = true
                            touchpadLeftSuppressed = false
                        }
                        if (isTouchpad) currentOnMouseButton(event.actionButton, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE -> {
                        if (isTouchpad && event.actionMasked == MotionEvent.ACTION_UP) {
                            touchpadLeftPressed = false
                        }
                        if (isTouchpad) currentOnMouseButton(event.actionButton, false)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> !isTouchpad
                    else -> false
                }
            }

            view.setOnCapturedPointerListener(pointerListener)
        } else {
            view.setOnCapturedPointerListener(null)
        }

        onDispose {
            physicalButtons.reset()
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            view.setOnCapturedPointerListener(null)
        }
    }
}

internal class CapturedMouseButtons(private val onMouseButton: (Int, Boolean) -> Unit) {
    private var heldButtons = 0

    fun onEvent(action: Int, actionButton: Int, buttonState: Int) {
        if (action == MotionEvent.ACTION_CANCEL) {
            reset()
            return
        }

        val buttons = when (action) {
            MotionEvent.ACTION_BUTTON_PRESS -> buttonState or actionButton
            MotionEvent.ACTION_BUTTON_RELEASE -> buttonState and actionButton.inv()
            else -> buttonState
        }
        update(buttons and SUPPORTED_BUTTONS)
    }

    fun reset() {
        update(0)
    }

    private fun update(buttons: Int) {
        val changed = heldButtons xor buttons
        heldButtons = buttons

        fun dispatchButton(button: Int) {
            if (changed and button != 0) {
                onMouseButton(button, buttons and button != 0)
            }
        }

        dispatchButton(MotionEvent.BUTTON_PRIMARY)
        dispatchButton(MotionEvent.BUTTON_SECONDARY)
        dispatchButton(MotionEvent.BUTTON_TERTIARY)
        dispatchButton(MotionEvent.BUTTON_BACK)
        dispatchButton(MotionEvent.BUTTON_FORWARD)
    }

    companion object {
        private const val SUPPORTED_BUTTONS = MotionEvent.BUTTON_PRIMARY or
            MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY or
            MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD
    }
}

private class TouchpadTwoFingerGesture {
    private var active = false
    private var tapCandidate = true
    private var downTime = 0L
    private var startCentroid: Offset? = null
    private var lastCentroid: Offset? = null
    private var remainderX = 0f
    private var remainderY = 0f
    private var tapSlop = DEFAULT_TAP_SLOP
    private var scrollNotch = DEFAULT_SCROLL_NOTCH

    fun isActive() = active

    fun start(event: MotionEvent) {
        active = true
        tapCandidate = true
        downTime = event.eventTime
        remainderX = 0f
        remainderY = 0f

        event.device?.let { device ->
            device.getMotionRange(MotionEvent.AXIS_X)?.let {
                val width = it.max - it.min
                if (width > 0f) tapSlop = width * 0.02f
            }
            device.getMotionRange(MotionEvent.AXIS_Y)?.let {
                val height = it.max - it.min
                if (height > 0f) scrollNotch = height * 0.12f
            }
        }

        startCentroid = centroid(event)
        lastCentroid = startCentroid
    }

    fun onMove(event: MotionEvent): Offset? {
        val centroid = centroid(event)
        val last = lastCentroid
        lastCentroid = centroid
        val start = startCentroid
        if (last == null || start == null) return null

        if (tapCandidate && hypot(centroid.x - start.x, centroid.y - start.y) > tapSlop) {
            tapCandidate = false
        }
        if (tapCandidate) return null

        remainderX += centroid.x - last.x
        remainderY -= centroid.y - last.y
        val notchesX = (remainderX / scrollNotch).toInt()
        val notchesY = (remainderY / scrollNotch).toInt()
        remainderX -= notchesX * scrollNotch
        remainderY -= notchesY * scrollNotch
        return Offset(notchesX.toFloat(), notchesY.toFloat())
    }

    fun onFingerUp(event: MotionEvent): Boolean {
        val isTap = active && tapCandidate && event.eventTime - downTime <= TAP_TIMEOUT_MS
        if (isTap) tapCandidate = false
        return isTap
    }

    fun end() {
        active = false
        lastCentroid = null
    }

    private fun centroid(event: MotionEvent): Offset {
        var x = 0f
        var y = 0f
        for (i in 0 until event.pointerCount) {
            x += event.getX(i)
            y += event.getY(i)
        }
        return Offset(x / event.pointerCount, y / event.pointerCount)
    }

    companion object {

        private const val DEFAULT_TAP_SLOP = 12f

        private const val DEFAULT_SCROLL_NOTCH = 60f

        private const val TAP_TIMEOUT_MS = 300L
    }
}

private fun Modifier.mouseEventModifier(
    disabled: Boolean,
    inputChange: Array<out Any> = arrayOf(Unit),
    onMouse: () -> Unit = {},
    onMouseMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (Int, Boolean) -> Unit = { _, _ -> },
) = composed(
    inspectorInfo = {
        name = "mouseEventModifier"
        properties["keys"] = inputChange
    }
) {
    val currentDisabled by rememberUpdatedState(disabled)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnMouseMove by rememberUpdatedState(onMouseMove)
    val currentOnMouseScroll by rememberUpdatedState(onMouseScroll)
    val currentOnMouseButton by rememberUpdatedState(onMouseButton)

    var lastButtons by remember(*inputChange) {

        mutableIntStateOf(0)
    }

    pointerInteropFilter { event ->
        if (currentDisabled) {
            return@pointerInteropFilter false
        }

        val isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        val isStylus = event.isFromSource(InputDevice.SOURCE_STYLUS)

        if (!isMouse && !isStylus) {
            return@pointerInteropFilter false
        }

        currentOnMouse()

        val buttons = event.buttonState
        val changed = lastButtons xor buttons

        fun dispatchButton(button: Int) {
            if (changed and button != 0) {
                val pressed = buttons and button != 0
                currentOnMouseButton(button, pressed)
            }
        }

        dispatchButton(MotionEvent.BUTTON_PRIMARY)
        dispatchButton(MotionEvent.BUTTON_SECONDARY)
        dispatchButton(MotionEvent.BUTTON_TERTIARY)
        dispatchButton(MotionEvent.BUTTON_BACK)
        dispatchButton(MotionEvent.BUTTON_FORWARD)
        dispatchButton(MotionEvent.BUTTON_STYLUS_SECONDARY)

        lastButtons = buttons

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                currentOnMouseMove(
                    Offset(x = event.x, y = event.y)
                )
            }

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isStylus) {
                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, true)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if (isStylus) {
                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, false)
                }
            }

            MotionEvent.ACTION_SCROLL -> {
                currentOnMouseScroll(
                    Offset(
                        x = event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        y = event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    )
                )
            }
        }

        true
    }
}
