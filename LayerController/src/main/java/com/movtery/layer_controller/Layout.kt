package com.movtery.layer_controller

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.JoystickWidgetRenderer
import com.movtery.layer_controller.layout.TextButton
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableJoystickStyle
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.observable.PointerEventBus
import com.movtery.layer_controller.observable.TouchProcessor
import com.movtery.layer_controller.utils.getWidgetPosition

@Composable
fun ControlBoxLayout(
    modifier: Modifier = Modifier,
    observedLayout: ObservableControlLayout? = null,
    eventHandler: EventHandler = EventHandler(),
    isCursorGrabbing: Boolean,
    checkOccupiedPointers: (PointerId) -> Boolean,
    opacity: () -> Float = { 1f },
    markPointerAsMoveOnly: (PointerId) -> Unit = {},
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    hideLayerWhen: HideLayerWhen = HideLayerWhen.None,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit
) {
    when {
        observedLayout == null -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.BottomCenter
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.padding(all = 16.dp)
                )
            }
        }
        else -> {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                key(observedLayout.hashCode()) {
                    BoxWithConstraints(
                        modifier = modifier
                    ) {
                        BaseControlBoxLayout(
                            modifier = Modifier.fillMaxSize(),
                            observedLayout = observedLayout,
                            eventHandler = eventHandler,
                            checkOccupiedPointers = checkOccupiedPointers,
                            opacity = opacity,
                            markPointerAsMoveOnly = markPointerAsMoveOnly,
                            onOccupiedPointer = onOccupiedPointer,
                            onReleasePointer = onReleasePointer,
                            isCursorGrabbing = isCursorGrabbing,
                            hideLayerWhen = hideLayerWhen,
                            isDark = isDark,
                            content = content
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.BaseControlBoxLayout(
    modifier: Modifier = Modifier,
    observedLayout: ObservableControlLayout,
    eventHandler: EventHandler,
    checkOccupiedPointers: (PointerId) -> Boolean,
    opacity: () -> Float,
    markPointerAsMoveOnly: (PointerId) -> Unit,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit,
    isCursorGrabbing: Boolean,
    hideLayerWhen: HideLayerWhen,
    isDark: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val layers by observedLayout.layers.collectAsStateWithLifecycle()
    val reversedLayers = remember(layers) { layers.reversed() }
    val styles by observedLayout.styles.collectAsStateWithLifecycle()
    val joystickStyles by observedLayout.joystickStyles.collectAsStateWithLifecycle()

    val currentCheckOccupiedPointers by rememberUpdatedState(checkOccupiedPointers)
    val currentIsCursorGrabbing by rememberUpdatedState(isCursorGrabbing)
    val currentHideLayerWhen by rememberUpdatedState(hideLayerWhen)

    val density = LocalDensity.current
    val screenSize = remember(maxWidth, maxHeight) {
        with(density) {
            IntSize(
                width = maxWidth.roundToPx(),
                height = maxHeight.roundToPx()
            )
        }
    }

    val pointerEventBus = remember { PointerEventBus() }
    pointerEventBus.checkOccupiedPointers = currentCheckOccupiedPointers
    pointerEventBus.markPointerAsMoveOnly = markPointerAsMoveOnly

    val touchProcessor = remember(screenSize) {
        TouchProcessor(eventHandler) { widget ->
            getWidgetPosition(widget, widget.internalRenderSize, screenSize)
        }
    }

    val currentTouchProcessor by rememberUpdatedState(touchProcessor)
    val currentMarkPointerAsMoveOnly by rememberUpdatedState(markPointerAsMoveOnly)

    Box(
        modifier = modifier
            .pointerInput(reversedLayers, hideLayerWhen) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)

                        event.changes.forEach { change ->
                            val pointerId = change.id

                            if (!change.pressed) {
                                pointerEventBus.endPointer(pointerId).forEach { widget ->

                                    widget.onReleaseEvent(eventHandler, reversedLayers)
                                }
                                return@forEach
                            }

                            if (change.isConsumed || currentCheckOccupiedPointers(pointerId)) {
                                return@forEach
                            }

                            val visibleWidgets = collectVisibleWidgets(
                                layers = layers,
                                hideLayerWhen = currentHideLayerWhen,
                                isCursorGrabbing = currentIsCursorGrabbing,
                            )

                            currentTouchProcessor.processFrame(
                                session = pointerEventBus,
                                change = change,
                                visibleWidgets = visibleWidgets,
                                allLayers = reversedLayers,
                                consumeEvent = { it.consume() },
                                markPointerAsMoveOnly = currentMarkPointerAsMoveOnly,
                            )
                        }
                    }
                }
            }
    ) {
        content()

        ControlsRendererLayer(
            isDark = isDark,
            opacity = opacity,
            layers = reversedLayers,
            styles = styles,
            joystickStyles = joystickStyles,
            screenSize = screenSize,
            pointerEventBus = pointerEventBus,
            eventHandler = eventHandler,
            isCursorGrabbing = currentIsCursorGrabbing,
            hideLayerWhen = currentHideLayerWhen,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer
        )
    }
}

@Composable
private fun ControlsRendererLayer(
    isDark: Boolean,
    opacity: () -> Float,
    layers: List<ObservableControlLayer>,
    styles: List<ObservableButtonStyle>,
    joystickStyles: List<ObservableJoystickStyle>,
    screenSize: IntSize,
    pointerEventBus: PointerEventBus,
    eventHandler: EventHandler,
    isCursorGrabbing: Boolean,
    hideLayerWhen: HideLayerWhen,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit
) {
    Layout(
        modifier = Modifier.alpha(alpha = opacity()),
        content = {

            layers.forEach { layer ->
                val layerVisibility = checkLayerVisibility(
                    layer = layer,
                    hideLayerWhen = hideLayerWhen,
                    isCursorGrabbing = isCursorGrabbing,
                    visibilityType = layer.visibilityType
                )
                val normalButtons by layer.normalButtons.collectAsStateWithLifecycle()
                val textBoxes by layer.textBoxes.collectAsStateWithLifecycle()
                val joystickButtons by layer.joystickButtons.collectAsStateWithLifecycle()

                textBoxes.forEach { data ->
                    TextButton(
                        isEditMode = false,
                        data = data,
                        allStyles = styles,
                        screenSize = screenSize,
                        isDark = isDark,
                        visible = layerVisibility && checkVisibility(isCursorGrabbing, data.visibilityType),
                        getOtherWidgets = { emptyList() },
                        snapThresholdValue = 4.dp,
                        eventHandler = eventHandler
                    )
                }

                normalButtons.forEach { data ->
                    TextButton(
                        isEditMode = false,
                        data = data,
                        allStyles = styles,
                        screenSize = screenSize,
                        isDark = isDark,
                        visible = layerVisibility && checkVisibility(isCursorGrabbing, data.visibilityType),
                        getOtherWidgets = { emptyList() },
                        snapThresholdValue = 4.dp,
                        eventHandler = eventHandler
                    )
                }

                joystickButtons.forEach { data ->
                    JoystickWidgetRenderer(
                        data = data,
                        joystickStyles = joystickStyles,
                        screenSize = screenSize,
                        isDark = isDark,
                        visible = layerVisibility && checkVisibility(isCursorGrabbing, data.visibilityType),
                        pointerEventBus = pointerEventBus,
                        eventHandler = eventHandler,
                        reversedLayers = layers,
                        onOccupiedPointer = onOccupiedPointer,
                        onReleasePointer = onReleasePointer
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        var index = 0
        fun ObservableWidget.putSize() {
            if (index < placeables.size) {
                val placeable = placeables[index]
                this.internalRenderSize = IntSize(placeable.width, placeable.height)
                index++
            }
        }

        layers.fastForEach { layer ->
            layer.textBoxes.value.fastForEach { it.putSize() }
            layer.normalButtons.value.fastForEach { it.putSize() }
            layer.joystickButtons.value.fastForEach { it.putSize() }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            var placeableIndex = 0
            fun ObservableWidget.place() {
                if (placeableIndex < placeables.size) {
                    val placeable = placeables[placeableIndex]
                    val position = getWidgetPosition(
                        data = this,
                        widgetSize = IntSize(placeable.width, placeable.height),
                        screenSize = screenSize
                    )
                    placeable.place(position.x.toInt(), position.y.toInt())
                    placeableIndex++
                }
            }

            layers.fastForEach { layer ->
                layer.textBoxes.value.fastForEach { it.place() }
                layer.normalButtons.value.fastForEach { it.place() }
                layer.joystickButtons.value.fastForEach { it.place() }
            }
        }
    }
}

private fun collectVisibleWidgets(
    layers: List<ObservableControlLayer>,
    hideLayerWhen: HideLayerWhen,
    isCursorGrabbing: Boolean,
): List<ObservableWidget> {
    return layers
        .filter { layer ->
            checkLayerVisibility(
                layer = layer,
                hideLayerWhen = hideLayerWhen,
                isCursorGrabbing = isCursorGrabbing,
                visibilityType = layer.visibilityType,
            )
        }
        .flatMap { layer ->

            layer.normalButtons.value.reversed()
        }
        .filter { widget ->
            widget.canTouch() && checkVisibility(
                isCursorGrabbing = isCursorGrabbing,
                visibilityType = widget.onCheckVisibilityType()
            )
        }
}

private fun checkLayerVisibility(
    layer: ObservableControlLayer,
    hideLayerWhen: HideLayerWhen,
    isCursorGrabbing: Boolean,
    visibilityType: VisibilityType
): Boolean {
    if (layer.hide || !checkVisibility(isCursorGrabbing, visibilityType)) {
        return false
    }

    return !when (hideLayerWhen) {
        HideLayerWhen.WhenMouse -> layer.hideWhenMouse
        HideLayerWhen.WhenGamepad -> layer.hideWhenGamepad
        HideLayerWhen.None -> false
    }
}

private fun checkVisibility(
    isCursorGrabbing: Boolean,
    visibilityType: VisibilityType
): Boolean {
    return when (visibilityType) {
        VisibilityType.ALWAYS -> true
        VisibilityType.IN_GAME -> isCursorGrabbing
        VisibilityType.IN_MENU -> !isCursorGrabbing
    }
}
